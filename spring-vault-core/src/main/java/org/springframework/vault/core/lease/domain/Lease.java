/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.core.lease.domain;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A lease abstracting the lease Id, duration and its renewability.
 *
 * @author Mark Paluch
 * @author Steven Swor
 */
public class Lease {

	private static final Lease NONE = new Lease(null, 0, false);

	private final String leaseId;

	private final long leaseDuration;

	private final boolean renewable;

	private Lease(String leaseId, long leaseDuration, boolean renewable) {

		this.leaseId = leaseId;
		this.leaseDuration = leaseDuration;
		this.renewable = renewable;
	}

	/**
	 * Create a new {@link Lease}.
	 *
	 * @param leaseId must not {@literal null}. Empty string implies a generic secret.
	 * @param leaseDuration the lease duration in seconds
	 * @param renewable {@literal true} if this lease is renewable.
	 * @return the created {@link Lease}
	 */
	public static Lease of(String leaseId, long leaseDuration, boolean renewable) {

		Assert.notNull(leaseId, "LeaseId must not be null");
		return new Lease(leaseId, leaseDuration, renewable);
	}

	/**
	 * Factory method to return a non-renewable, zero-duration {@link Lease}.
	 *
	 * @return a non-renewable, zero-duration {@link Lease}.
	 */
	public static Lease none() {
		return NONE;
	}

	/**
	 * @return the lease Id
	 */
	public String getLeaseId() {
		return leaseId;
	}

	/**
	 * @return the lease duration in seconds.
	 */
	public long getLeaseDuration() {
		return leaseDuration;
	}

	/**
	 *
	 * @return {@literal true} if the lease is renewable.
	 */
	public boolean isRenewable() {
		return renewable;
	}

	/**
	 *
	 * @return {@literal true} if the lease represents a rotating generic secret.
	 */
	public boolean isRotatingGenericLease() {
		return !renewable && leaseDuration > 0 && StringUtils.isEmpty(leaseId);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Lease))
			return false;

		Lease lease = (Lease) o;

		if (leaseDuration != lease.leaseDuration)
			return false;
		if (renewable != lease.renewable)
			return false;
		return leaseId != null ? leaseId.equals(lease.leaseId) : lease.leaseId == null;
	}

	@Override
	public int hashCode() {

		int result = leaseId != null ? leaseId.hashCode() : 0;
		result = 31 * result + (int) (leaseDuration ^ (leaseDuration >>> 32));
		result = 31 * result + (renewable ? 1 : 0);
		return result;
	}

	@Override
	public String toString() {

		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [leaseId='").append(leaseId).append('\'');
		sb.append(", leaseDuration=").append(leaseDuration);
		sb.append(", renewable=").append(renewable);
		sb.append(']');
		return sb.toString();
	}
}
