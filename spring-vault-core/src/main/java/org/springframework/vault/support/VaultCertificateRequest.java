/*
 * Copyright 2016-2019 the original author or authors.
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
package org.springframework.vault.support;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Request for a Certificate.
 *
 * @author Mark Paluch
 * @author Alex Antonov
 */
public class VaultCertificateRequest {

	/**
	 * The CN of the certificate. Should match the host name.
	 */
	private final String commonName;

	/**
	 * Alternate CN names for additional host names.
	 */
	private final List<String> altNames;

	/**
	 * Requested IP Subject Alternative Names.
	 */
	private final List<String> ipSubjectAltNames;

	/**
	 * Requested URI Subject Alternative Names.
	 */
	private final List<String> uriSubjectAltNames;

	/**
	 * Requested Time to Live
	 */
	@Nullable
	private final Duration ttl;

	/**
	 * If {@literal true}, the given common name will not be included in DNS or Email
	 * Subject Alternate Names (as appropriate). Useful if the CN is not a hostname or
	 * email address, but is instead some human-readable identifier.
	 */
	private final boolean excludeCommonNameFromSubjectAltNames;

	private VaultCertificateRequest(String commonName, List<String> altNames,
			List<String> ipSubjectAltNames, List<String> uriSubjectAltNames,
			@Nullable Duration ttl, boolean excludeCommonNameFromSubjectAltNames) {

		this.commonName = commonName;
		this.altNames = altNames;
		this.ipSubjectAltNames = ipSubjectAltNames;
		this.uriSubjectAltNames = uriSubjectAltNames;
		this.ttl = ttl;
		this.excludeCommonNameFromSubjectAltNames = excludeCommonNameFromSubjectAltNames;
	}

	/**
	 * @return a new {@link VaultCertificateRequestBuilder}.
	 */
	public static VaultCertificateRequestBuilder builder() {
		return new VaultCertificateRequestBuilder();
	}

	/**
	 * Create a new {@link VaultCertificateRequest} given a {@code commonName}.
	 *
	 * @param commonName must not be empty or {@literal null}.
	 * @return the created {@link VaultCertificateRequest}.
	 */
	public static VaultCertificateRequest create(String commonName) {
		return builder().commonName(commonName).build();
	}

	public String getCommonName() {
		return commonName;
	}

	public List<String> getAltNames() {
		return altNames;
	}

	public List<String> getIpSubjectAltNames() {
		return ipSubjectAltNames;
	}

	public List<String> getUriSubjectAltNames() {
		return uriSubjectAltNames;
	}

	@Nullable
	public Duration getTtl() {
		return ttl;
	}

	public boolean isExcludeCommonNameFromSubjectAltNames() {
		return excludeCommonNameFromSubjectAltNames;
	}

	public static class VaultCertificateRequestBuilder {

		@Nullable
		private String commonName;

		private List<String> altNames = new ArrayList<>();

		private List<String> ipSubjectAltNames = new ArrayList<>();

		private List<String> uriSubjectAltNames = new ArrayList<>();

		@Nullable
		private Duration ttl;

		private boolean excludeCommonNameFromSubjectAltNames;

		VaultCertificateRequestBuilder() {
		}

		/**
		 * Configure the common name.
		 *
		 * @param commonName must not be empty or {@literal null}.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 */
		public VaultCertificateRequestBuilder commonName(String commonName) {

			Assert.hasText(commonName, "Common name must not be empty");

			this.commonName = commonName;
			return this;
		}

		/**
		 * Configure alternative names. Replaces previously configured alt names.
		 *
		 * @param altNames must not be {@literal null}.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 */
		public VaultCertificateRequestBuilder altNames(Iterable<String> altNames) {

			Assert.notNull(altNames, "Alt names must not be null");

			this.altNames = toList(altNames);
			return this;
		}

		/**
		 * Add an alternative name.
		 *
		 * @param altName must not be empty or {@literal null}.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 */
		public VaultCertificateRequestBuilder withAltName(String altName) {

			Assert.hasText(altName, "Alt name must not be empty");

			this.altNames.add(altName);
			return this;
		}

		/**
		 * Configure IP subject alternative names. Replaces previously configured IP
		 * subject alt names.
		 *
		 * @param ipSubjectAltNames must not be {@literal null}.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 */
		public VaultCertificateRequestBuilder ipSubjectAltNames(
				Iterable<String> ipSubjectAltNames) {

			Assert.notNull(ipSubjectAltNames, "IP subject alt names must not be null");

			this.ipSubjectAltNames = toList(ipSubjectAltNames);
			return this;
		}

		/**
		 * Add an IP subject alternative name.
		 *
		 * @param ipSubjectAltName must not be empty or {@literal null}.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 */
		public VaultCertificateRequestBuilder withIpSubjectAltName(
				String ipSubjectAltName) {

			Assert.hasText(ipSubjectAltName, "IP subject alt name must not be empty");

			this.ipSubjectAltNames.add(ipSubjectAltName);
			return this;
		}

		/**
		 * Configure URI subject alternative names. Replaces previously configured URI
		 * subject alt names.
		 *
		 * @param uriSubjectAltNames must not be {@literal null}.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 * @since 2.2
		 */
		public VaultCertificateRequestBuilder uriSubjectAltNames(
				Iterable<String> uriSubjectAltNames) {

			Assert.notNull(uriSubjectAltNames, "URI subject alt names must not be null");

			this.uriSubjectAltNames = toList(uriSubjectAltNames);
			return this;
		}

		/**
		 * Add an URI subject alternative name.
		 *
		 * @param uriSubjectAltName must not be empty or {@literal null}.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 * @since 2.2
		 */
		public VaultCertificateRequestBuilder withUriSubjectAltName(
				String uriSubjectAltName) {

			Assert.hasText(uriSubjectAltName, "URI subject alt name must not be empty");

			this.uriSubjectAltNames.add(uriSubjectAltName);
			return this;
		}

		/**
		 * Configure a TTL.
		 *
		 * @param ttl the time to live, in seconds, must not be negative.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 * @deprecated since 2.0, use {@link #ttl(Duration)} for time unit safety.
		 */
		@Deprecated
		public VaultCertificateRequestBuilder ttl(int ttl) {

			Assert.isTrue(ttl > 0, "TTL must not be negative");

			this.ttl = Duration.ofSeconds(ttl);
			return this;
		}

		/**
		 * Configure a TTL.
		 *
		 * @param ttl the time to live, must not be negative.
		 * @param timeUnit must not be {@literal null}
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 */
		public VaultCertificateRequestBuilder ttl(long ttl, TimeUnit timeUnit) {

			Assert.isTrue(ttl > 0, "TTL must not be negative");
			Assert.notNull(timeUnit, "TimeUnit must be greater 0");

			this.ttl = Duration.ofSeconds(timeUnit.toSeconds(ttl));
			return this;
		}

		/**
		 * Configure a TTL.
		 *
		 * @param ttl the time to live, must not be {@literal null} or negative.
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 * @since 2.0
		 */
		public VaultCertificateRequestBuilder ttl(Duration ttl) {

			Assert.notNull(ttl, "TTL must not be null");
			Assert.isTrue(!ttl.isNegative(), "TTL must not be negative");

			this.ttl = ttl;
			return this;
		}

		/**
		 * Exclude the given common name from DNS or Email Subject Alternate Names (as
		 * appropriate). Useful if the CN is not a hostname or email address, but is
		 * instead some human-readable identifier.
		 *
		 * @return {@code this} {@link VaultCertificateRequestBuilder}.
		 */
		public VaultCertificateRequestBuilder excludeCommonNameFromSubjectAltNames() {

			this.excludeCommonNameFromSubjectAltNames = true;
			return this;
		}

		/**
		 * Build a new {@link VaultCertificateRequest} instance. Requires
		 * {@link #commonName(String)} to be configured.
		 *
		 * @return a new {@link VaultCertificateRequest}.
		 */
		public VaultCertificateRequest build() {

			Assert.notNull(commonName, "Common name must not be null");
			Assert.hasText(commonName, "Common name must not be empty");

			List<String> altNames;
			switch (this.altNames.size()) {
			case 0:
				altNames = java.util.Collections.emptyList();
				break;
			case 1:
				altNames = java.util.Collections.singletonList(this.altNames.get(0));
				break;
			default:
				altNames = java.util.Collections
						.unmodifiableList(new ArrayList<>(this.altNames));
			}

			List<String> ipSubjectAltNames;
			switch (this.ipSubjectAltNames.size()) {
			case 0:
				ipSubjectAltNames = java.util.Collections.emptyList();
				break;
			case 1:
				ipSubjectAltNames = java.util.Collections
						.singletonList(this.ipSubjectAltNames.get(0));
				break;
			default:
				ipSubjectAltNames = java.util.Collections
						.unmodifiableList(new ArrayList<>(this.ipSubjectAltNames));
			}

			List<String> uriSubjectAltNames;
			switch (this.uriSubjectAltNames.size()) {
			case 0:
				uriSubjectAltNames = java.util.Collections.emptyList();
				break;
			case 1:
				uriSubjectAltNames = java.util.Collections
						.singletonList(this.uriSubjectAltNames.get(0));
				break;
			default:
				uriSubjectAltNames = java.util.Collections
						.unmodifiableList(new ArrayList<>(this.uriSubjectAltNames));
			}

			return new VaultCertificateRequest(commonName, altNames, ipSubjectAltNames,
					uriSubjectAltNames, ttl, excludeCommonNameFromSubjectAltNames);
		}

		private static <E> List<E> toList(Iterable<E> iter) {

			List<E> list = new ArrayList<>();
			for (E item : iter) {
				list.add(item);
			}

			return list;
		}
	}
}
