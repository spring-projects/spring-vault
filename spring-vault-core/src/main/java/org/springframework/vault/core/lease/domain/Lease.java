/*
 * Copyright 2017-2019 the original author or authors.
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

import java.time.Duration;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A lease abstracting the lease Id, duration and its renewability.
 *
 * @author Mark Paluch
 * @author Steven Swor
 */
public class Lease {

	private static final Lease NONE = new Lease(null, Duration.ZERO, false);

	@Nullable
	private final String leaseId;

	private final Duration leaseDuration;

	private final boolean renewable;

	private Lease(@Nullable String leaseId, Duration leaseDuration, boolean renewable) {

		this.leaseId = leaseId;
		this.leaseDuration = leaseDuration;
		this.renewable = renewable;
	}

	/**
	 * Create a new {@link Lease}.
	 *
	 * @param leaseId must not be empty or {@literal null}.
	 * @param leaseDurationSeconds the lease duration in seconds, must not be negative.
	 * @param renewable {@literal true} if this lease is renewable.
	 * @return the created {@link Lease}
	 * @deprecated since 2.0, use {@link #of(String, Duration, boolean)} for time unit
	 * safety.
	 */
	@Deprecated
	public static Lease of(String leaseId, long leaseDurationSeconds, boolean renewable) {

		Assert.isTrue(leaseDurationSeconds >= 0, "Lease duration must not be negative");

		return of(leaseId, Duration.ofSeconds(leaseDurationSeconds), renewable);
	}

	/**
	 * Create a new {@link Lease}.
	 *
	 * @param leaseId must not be empty or {@literal null}.
	 * @param leaseDuration the lease duration, must not be {@literal null} or negative.
	 * @param renewable {@literal true} if this lease is renewable.
	 * @return the created {@link Lease}
	 * @since 2.0
	 */
	public static Lease of(String leaseId, Duration leaseDuration, boolean renewable) {

		Assert.hasText(leaseId, "LeaseId must not be empty");
		Assert.notNull(leaseDuration, "Lease duration must not be null");
		Assert.isTrue(!leaseDuration.isNegative(), "Lease duration must not be negative");

		return new Lease(leaseId, leaseDuration, renewable);
	}

	/**
	 * Create a new non-renewable {@link Lease}, without a {@code leaseId} and specified
	 * duration.
	 *
	 * @param leaseDuration the lease duration in seconds, must not be negative.
	 * @return the created {@link Lease}
	 * @since 1.1
	 * @deprecated since 2.0, use {@link #fromTimeToLive(Duration)} for time unit safety.
	 */
	@Deprecated
	public static Lease fromTimeToLive(long leaseDuration) {

		Assert.isTrue(leaseDuration >= 0, "Lease duration must not be negative");

		return new Lease(null, Duration.ofSeconds(leaseDuration), false);
	}

	/**
	 * Create a new non-renewable {@link Lease}, without a {@code leaseId} and specified
	 * duration.
	 *
	 * @param leaseDuration the lease duration, must not be {@literal null} or negative.
	 * @return the created {@link Lease}
	 * @since 2.0
	 */
	public static Lease fromTimeToLive(Duration leaseDuration) {

		Assert.notNull(leaseDuration, "Lease duration must not be null");
		Assert.isTrue(!leaseDuration.isNegative(), "Lease duration must not be negative");

		return new Lease(null, leaseDuration, false);
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
	 * @return {@literal true} is the lease is associated with a {@code leaseId}.
	 * @since 1.1
	 */
	public boolean hasLeaseId() {
		return leaseId != null;
	}

	/**
	 * @return the lease Id
	 */
	@Nullable
	public String getLeaseId() {
		return leaseId;
	}

	/**
	 * @return the lease duration in seconds.
	 */
	public Duration getLeaseDuration() {
		return leaseDuration;
	}

	/**
	 * @return {@literal true} if the lease is renewable.
	 */
	public boolean isRenewable() {
		return renewable;
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
		result = 31 * result + leaseDuration.hashCode();
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
