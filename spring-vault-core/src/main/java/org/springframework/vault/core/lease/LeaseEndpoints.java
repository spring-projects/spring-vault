/*
 * Copyright 2018-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.core.lease;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.web.client.RestOperations;

/**
 * Version-specific endpoint implementations that use either legacy or sys/leases
 * endpoints.
 *
 * @author Mark Paluch
 * @author Thomas Kåsene
 * @since 2.1
 * @see SecretLeaseContainer
 */
public enum LeaseEndpoints {

	/**
	 * Legacy endpoints prior to Vault 0.8 ({@literal /sys/renew},{@literal /sys/revoke}).
	 */
	Legacy {

		@Override
		public void revoke(Lease lease, RestOperations operations) {

			operations.exchange("sys/revoke", HttpMethod.PUT, LeaseEndpoints.getLeaseRevocationBody(lease), Map.class,
					lease.getRequiredLeaseId());
		}

		@Override
		public Lease renew(Lease lease, RestOperations operations) {

			HttpEntity<Object> leaseRenewalEntity = getLeaseRenewalBody(lease);
			ResponseEntity<Map<String, Object>> entity = put(operations, leaseRenewalEntity, "sys/renew");

			Assert.state(entity.getBody() != null, "Renew response must not be null");

			return toLease(entity.getBody());
		}
	},

	/**
	 * Sys/lease endpoints for Vault 0.8 and higher ({@literal /sys/leases/…}) that uses
	 * the {@literal /sys/leases/revoke} endpoint when revoking leases.
	 * @since 2.3
	 */
	Leases {

		@Override
		public void revoke(Lease lease, RestOperations operations) {

			operations.exchange("sys/leases/revoke", HttpMethod.PUT, LeaseEndpoints.getLeaseRevocationBody(lease),
					Map.class, lease.getRequiredLeaseId());
		}

		@Override
		public Lease renew(Lease lease, RestOperations operations) {

			HttpEntity<Object> leaseRenewalEntity = getLeaseRenewalBody(lease);
			ResponseEntity<Map<String, Object>> entity = put(operations, leaseRenewalEntity, "sys/leases/renew");

			Assert.state(entity.getBody() != null, "Renew response must not be null");

			return toLease(entity.getBody());
		}
	},

	/**
	 * Sys/lease endpoints for Vault 0.8 and higher ({@literal /sys/leases/…}) that uses
	 * the {@literal /sys/leases/revoke-prefix/…} endpoint when revoking leases.
	 * @since 2.3
	 */
	LeasesRevokedByPrefix {

		@Override
		public void revoke(Lease lease, RestOperations operations) {

			String endpoint = "sys/leases/revoke-prefix/" + lease.getRequiredLeaseId();
			operations.put(endpoint, null);
		}

		@Override

		public Lease renew(Lease lease, RestOperations operations) {

			HttpEntity<Object> leaseRenewalEntity = getLeaseRenewalBody(lease);
			ResponseEntity<Map<String, Object>> entity = put(operations, leaseRenewalEntity, "sys/leases/renew");

			Assert.state(entity.getBody() != null, "Renew response must not be null");

			return toLease(entity.getBody());
		}
	};

	/**
	 * Revoke a {@link Lease}.
	 * @param lease must not be {@literal null}.
	 * @param operations must not be {@literal null}.
	 */
	abstract void revoke(Lease lease, RestOperations operations);

	/**
	 * Renew a {@link Lease} and return the renewed {@link Lease}.
	 * @param lease must not be {@literal null}.
	 * @param operations must not be {@literal null}.
	 * @return the renewed {@link Lease}.
	 */
	abstract Lease renew(Lease lease, RestOperations operations);

	@SuppressWarnings("NullAway")
	private static Lease toLease(Map<String, Object> body) {

		String leaseId = (String) body.get("lease_id");
		Number leaseDuration = (Number) body.get("lease_duration");
		boolean renewable = (Boolean) body.get("renewable");

		return Lease.of(leaseId, Duration.ofSeconds(leaseDuration != null ? leaseDuration.longValue() : 0), renewable);
	}

	private static HttpEntity<Object> getLeaseRenewalBody(Lease lease) {

		Map<String, String> leaseRenewalData = new HashMap<>();
		leaseRenewalData.put("lease_id", lease.getRequiredLeaseId());
		leaseRenewalData.put("increment", Long.toString(lease.getLeaseDuration().getSeconds()));

		return new HttpEntity<>(leaseRenewalData);
	}

	private static HttpEntity<Object> getLeaseRevocationBody(Lease lease) {

		Map<String, String> leaseRenewalData = new HashMap<>();
		leaseRenewalData.put("lease_id", lease.getRequiredLeaseId());

		return new HttpEntity<>(leaseRenewalData);
	}

	@SuppressWarnings({ "unchecked", "RedundantClassCall" })
	private static ResponseEntity<Map<String, Object>> put(RestOperations operations, HttpEntity<Object> entity,
			String url) {
		return ResponseEntity.class.cast(operations.exchange(url, HttpMethod.PUT, entity, Map.class));
	}

}
