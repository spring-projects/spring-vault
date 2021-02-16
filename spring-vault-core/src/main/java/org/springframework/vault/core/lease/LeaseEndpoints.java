/*
 * Copyright 2018-2021 the original author or authors.
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
					lease.getLeaseId());
		}

		@SuppressWarnings("unchecked")
		@Override
		public Lease renew(Lease lease, RestOperations operations) {

			HttpEntity<Object> leaseRenewalEntity = getLeaseRenewalBody(lease);

			ResponseEntity<Map<String, Object>> entity = (ResponseEntity) operations.exchange("sys/renew",
					HttpMethod.PUT, leaseRenewalEntity, Map.class);

			Assert.state(entity != null && entity.getBody() != null, "Renew response must not be null");

			return toLease(entity.getBody());
		}
	},

	/**
	 * Sys/lease endpoints for Vault 0.8 and higher ({@literal /sys/leases/…}).
	 */
	SysLeases {

		@Override
		public void revoke(Lease lease, RestOperations operations) {

			operations.exchange("sys/leases/revoke", HttpMethod.PUT, LeaseEndpoints.getLeaseRevocationBody(lease),
					Map.class, lease.getLeaseId());
		}

		@Override
		@SuppressWarnings("unchecked")
		public Lease renew(Lease lease, RestOperations operations) {

			HttpEntity<Object> leaseRenewalEntity = getLeaseRenewalBody(lease);

			ResponseEntity<Map<String, Object>> entity = (ResponseEntity) operations.exchange("sys/leases/renew",
					HttpMethod.PUT, leaseRenewalEntity, Map.class);

			Assert.state(entity != null && entity.getBody() != null, "Renew response must not be null");

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

	private static Lease toLease(Map<String, Object> body) {

		String leaseId = (String) body.get("lease_id");
		Number leaseDuration = (Number) body.get("lease_duration");
		boolean renewable = (Boolean) body.get("renewable");

		return Lease.of(leaseId, Duration.ofSeconds(leaseDuration != null ? leaseDuration.longValue() : 0), renewable);
	}

	private static HttpEntity<Object> getLeaseRenewalBody(Lease lease) {

		Map<String, String> leaseRenewalData = new HashMap<>();
		leaseRenewalData.put("lease_id", lease.getLeaseId());
		leaseRenewalData.put("increment", Long.toString(lease.getLeaseDuration().getSeconds()));

		return new HttpEntity<>(leaseRenewalData);
	}

	private static HttpEntity<Object> getLeaseRevocationBody(Lease lease) {

		Map<String, String> leaseRenewalData = new HashMap<>();
		leaseRenewalData.put("lease_id", lease.getLeaseId());

		return new HttpEntity<>(leaseRenewalData);
	}

}
