/*
 * Copyright 2016-2025 the original author or authors.
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Request for a Certificate.
 *
 * @author Mark Paluch
 * @author Alex Antonov
 */
public class VaultCertificateRequest {

	private static String DEFAULT_FORMAT = "der";


	/**
	 * The CN of the certificate. Should match the host name.
	 */
	private final String commonName;

	/**
	 * If {@literal true}, the given common name will not be included in DNS or
	 * Email Subject Alternate Names (as appropriate). Useful if the CN is not a
	 * hostname or email address, but is instead some human-readable identifier.
	 */
	private final boolean excludeCommonNameFromSubjectAltNames;

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
	 * Specifies custom OID/UTF8-string Subject Alternative Names. These must match
	 * values specified on the role in {@literal allowed_other_sans}. The format is
	 * the same as OpenSSL: {@literal <oid>;<type>:<value>} where the only current
	 * valid type is UTF8.
	 *
	 * @since 2.4
	 */
	private final List<String> otherSans;

	/**
	 * Requested Time to Live
	 */
	@Nullable
	private final Duration ttl;

	/**
	 * Set the Not After field of the certificate with specified date value. The
	 * value format should be given in UTC format YYYY-MM-ddTHH:MM:SSZ. Supports the
	 * Y10K end date for IEEE 802.1AR-2018 standard devices, 9999-12-31T23:59:59Z.
	 */
	@Nullable
	private final Instant notAfter;

	/**
	 * Specifies the format for returned data. Can be {@literal pem},
	 * {@literal der}, or {@literal pem_bundle}; defaults to {@literal der} (in
	 * vault api the default is {@literal pem}). If der, the output is base64
	 * encoded. If {@literal pem_bundle}, the certificate field will contain the
	 * private key and certificate, concatenated; if the issuing CA is not a
	 * Vault-derived self-signed root, this will be included as well.
	 *
	 * @since 2.4
	 */
	private final String format;

	/**
	 * Specifies the format for marshaling the private key. Defaults to
	 * {@literal der} which will return either base64-encoded DER or PEM-encoded
	 * DER, depending on the value of {@literal format}. The other option is
	 * {@literal pkcs8} which will return the key marshalled as PEM-encoded PKCS8.
	 *
	 * @since 2.4
	 */
	@Nullable
	private final String privateKeyFormat;

	/**
	 * Specifies the comma-separated list of requested User ID (OID
	 * 0.9.2342.19200300.100.1.1) Subject values to be placed on the signed
	 * certificate. This field is validated against allowed_user_ids on the role.
	 */
	@Nullable
	private final String userIds;


	private VaultCertificateRequest(String commonName, boolean excludeCommonNameFromSubjectAltNames,
			List<String> altNames, List<String> ipSubjectAltNames, List<String> uriSubjectAltNames,
			List<String> otherSans, @Nullable Duration ttl, @Nullable Instant notAfter, String format,
			@Nullable String privateKeyFormat, @Nullable String userIds) {
		this.commonName = commonName;
		this.excludeCommonNameFromSubjectAltNames = excludeCommonNameFromSubjectAltNames;
		this.altNames = altNames;
		this.ipSubjectAltNames = ipSubjectAltNames;
		this.uriSubjectAltNames = uriSubjectAltNames;
		this.otherSans = otherSans;
		this.ttl = ttl;
		this.notAfter = notAfter;
		this.format = format;
		this.privateKeyFormat = privateKeyFormat;
		this.userIds = userIds;
	}


	/**
	 * @return a new {@link VaultCertificateRequestBuilder}.
	 */
	public static VaultCertificateRequestBuilder builder() {
		return new VaultCertificateRequestBuilder();
	}

	/**
	 * Create a new {@code VaultCertificateRequest} given a {@code commonName}.
	 * @param commonName must not be empty or {@literal null}.
	 * @return the created {@code VaultCertificateRequest}.
	 */
	public static VaultCertificateRequest create(String commonName) {
		return builder().commonName(commonName).build();
	}


	public String getCommonName() {
		return this.commonName;
	}

	public List<String> getAltNames() {
		return this.altNames;
	}

	public List<String> getIpSubjectAltNames() {
		return this.ipSubjectAltNames;
	}

	public List<String> getUriSubjectAltNames() {
		return this.uriSubjectAltNames;
	}

	public List<String> getOtherSans() {
		return this.otherSans;
	}

	@Nullable
	public Duration getTtl() {
		return this.ttl;
	}

	public String getFormat() {
		return format;
	}

	@Nullable
	public String getPrivateKeyFormat() {
		return privateKeyFormat;
	}

	public boolean isExcludeCommonNameFromSubjectAltNames() {
		return this.excludeCommonNameFromSubjectAltNames;
	}

	@Nullable
	public Instant getNotAfter() {
		return this.notAfter;
	}

	@Nullable
	public String getUserIds() {
		return this.userIds;
	}


	public static class VaultCertificateRequestBuilder {

		@Nullable
		private String commonName;

		private List<String> altNames = new ArrayList<>();

		private List<String> ipSubjectAltNames = new ArrayList<>();

		private List<String> uriSubjectAltNames = new ArrayList<>();

		private List<String> otherSans = new ArrayList<>();

		@Nullable
		private Duration ttl;

		private String format = DEFAULT_FORMAT;

		@Nullable
		private String privateKeyFormat;

		private boolean excludeCommonNameFromSubjectAltNames;

		@Nullable
		private Instant notAfter;

		@Nullable
		private String userIds;


		VaultCertificateRequestBuilder() {
		}


		/**
		 * Configure the common name.
		 * @param commonName must not be empty or {@literal null}.
		 * @return this builder.
		 */
		public VaultCertificateRequestBuilder commonName(String commonName) {
			Assert.hasText(commonName, "Common name must not be empty");
			this.commonName = commonName;
			return this;
		}

		/**
		 * Exclude the given common name from DNS or Email Subject Alternate Names (as
		 * appropriate). Useful if the CN is not a hostname or email address, but is
		 * instead some human-readable identifier.
		 * @return this builder.
		 */
		public VaultCertificateRequestBuilder excludeCommonNameFromSubjectAltNames() {
			this.excludeCommonNameFromSubjectAltNames = true;
			return this;
		}

		/**
		 * Configure alternative names. Replaces previously configured alt names.
		 * @param altNames must not be {@literal null}.
		 * @return this builder.
		 */
		public VaultCertificateRequestBuilder altNames(Iterable<String> altNames) {
			Assert.notNull(altNames, "Alt names must not be null");
			this.altNames = toList(altNames);
			return this;
		}

		/**
		 * Add an alternative name.
		 * @param altName must not be empty or {@literal null}.
		 * @return this builder.
		 */
		public VaultCertificateRequestBuilder withAltName(String altName) {
			Assert.hasText(altName, "Alt name must not be empty");
			this.altNames.add(altName);
			return this;
		}

		/**
		 * Configure IP subject alternative names. Replaces previously configured IP
		 * subject alt names.
		 * @param ipSubjectAltNames must not be {@literal null}.
		 * @return this builder.
		 */
		public VaultCertificateRequestBuilder ipSubjectAltNames(Iterable<String> ipSubjectAltNames) {
			Assert.notNull(ipSubjectAltNames, "IP subject alt names must not be null");
			this.ipSubjectAltNames = toList(ipSubjectAltNames);
			return this;
		}

		/**
		 * Add an IP subject alternative name.
		 * @param ipSubjectAltName must not be empty or {@literal null}.
		 * @return this builder.
		 */
		public VaultCertificateRequestBuilder withIpSubjectAltName(String ipSubjectAltName) {
			Assert.hasText(ipSubjectAltName, "IP subject alt name must not be empty");
			this.ipSubjectAltNames.add(ipSubjectAltName);
			return this;
		}

		/**
		 * Configure URI subject alternative names. Replaces previously configured URI
		 * subject alt names.
		 * @param uriSubjectAltNames must not be {@literal null}.
		 * @return this builder.
		 * @since 2.2
		 */
		public VaultCertificateRequestBuilder uriSubjectAltNames(Iterable<String> uriSubjectAltNames) {
			Assert.notNull(uriSubjectAltNames, "URI subject alt names must not be null");
			this.uriSubjectAltNames = toList(uriSubjectAltNames);
			return this;
		}

		/**
		 * Add an URI subject alternative name.
		 * @param uriSubjectAltName must not be empty or {@literal null}.
		 * @return this builder.
		 * @since 2.2
		 */
		public VaultCertificateRequestBuilder withUriSubjectAltName(String uriSubjectAltName) {
			Assert.hasText(uriSubjectAltName, "URI subject alt name must not be empty");
			this.uriSubjectAltNames.add(uriSubjectAltName);
			return this;
		}

		/**
		 * Configure custom OID/UTF8-string subject alternative names. Replaces
		 * previously configured other subject alt names.
		 * @param otherSans must not be {@literal null}.
		 * @return this builder.
		 * @since 2.4
		 */
		public VaultCertificateRequestBuilder otherSans(Iterable<String> otherSans) {
			Assert.notNull(otherSans, "Other subject alt names must not be null");
			this.otherSans = toList(otherSans);
			return this;
		}

		/**
		 * Add custom OID/UTF8-string subject alternative name.
		 * @param otherSans must not be empty or {@literal null}.
		 * @return this builder.
		 * @since 2.4
		 */
		public VaultCertificateRequestBuilder withOtherSans(String otherSans) {
			Assert.hasText(otherSans, "Other subject alt name must not be empty");
			this.otherSans.add(otherSans);
			return this;
		}

		/**
		 * Configure a TTL.
		 * @param ttl the time to live, must not be negative.
		 * @param timeUnit must not be {@literal null}
		 * @return this builder.
		 */
		public VaultCertificateRequestBuilder ttl(long ttl, TimeUnit timeUnit) {
			Assert.isTrue(ttl > 0, "TTL must not be negative");
			Assert.notNull(timeUnit, "TimeUnit must be greater 0");
			this.ttl = Duration.ofSeconds(timeUnit.toSeconds(ttl));
			return this;
		}

		/**
		 * Configure a TTL.
		 * @param ttl the time to live, must not be {@literal null} or negative.
		 * @return this builder.
		 * @since 2.0
		 */
		public VaultCertificateRequestBuilder ttl(Duration ttl) {
			Assert.notNull(ttl, "TTL must not be null");
			Assert.isTrue(!ttl.isNegative(), "TTL must not be negative");
			this.ttl = ttl;
			return this;
		}

		/**
		 * Set the {@code Not After} field of the certificate with specified date value.
		 * Supports the Y10K end date for IEEE 802.1AR-2018 standard devices,
		 * 9999-12-31T23:59:59Z.
		 * @return this builder.
		 * @since 3.1
		 */
		public VaultCertificateRequestBuilder notAfter(Instant notAfter) {
			Assert.notNull(notAfter, "Not after must not be null");
			this.notAfter = Instant.from(notAfter).truncatedTo(ChronoUnit.SECONDS);
			return this;
		}

		/**
		 * Configure the certificate format.
		 * @param format the certificate format to use. Can be {@code pem}, {@code der},
		 * or {@code pem_bundle}
		 * @return this builder.
		 * @since 2.4
		 */
		public VaultCertificateRequestBuilder format(String format) {
			Assert.hasText(format, "Format must not be empty or null");
			this.format = format;
			return this;
		}

		/**
		 * Configure the key format.
		 * @param privateKeyFormat the key format to use. Can be {@code pem},
		 * {@code der}, or {@code pkcs8}
		 * @return this builder.
		 * @since 2.4
		 */
		public VaultCertificateRequestBuilder privateKeyFormat(String privateKeyFormat) {
			Assert.hasText(privateKeyFormat, "Private key format must not be empty or null");
			this.privateKeyFormat = privateKeyFormat;
			return this;
		}

		/**
		 * Specifies the comma-separated list of requested User ID (OID
		 * 0.9.2342.19200300.100.1.1) Subject values to be placed on the signed
		 * certificate. This field is validated against allowed_user_ids on the role.
		 * @return this builder.
		 * @since 3.1
		 */
		public VaultCertificateRequestBuilder userId(String userId) {
			Assert.hasText(userId, "User ID must not be empty or null");
			this.userIds = userId;
			return this;
		}

		/**
		 * Specifies the comma-separated list of requested User ID (OID
		 * 0.9.2342.19200300.100.1.1) Subject values to be placed on the signed
		 * certificate. This field is validated against allowed_user_ids on the role.
		 * @return this builder.
		 * @since 3.1
		 */
		public VaultCertificateRequestBuilder userIds(Collection<String> userIds) {
			Assert.notNull(userIds, "User IDs must not be null");
			this.userIds = StringUtils.collectionToCommaDelimitedString(userIds);
			return this;
		}

		/**
		 * Build a new {@link VaultCertificateRequest} instance. Requires
		 * {@link #commonName(String)} to be configured.
		 * @return a new {@link VaultCertificateRequest}.
		 */
		public VaultCertificateRequest build() {
			Assert.notNull(this.commonName, "Common name must not be null");
			Assert.hasText(this.commonName, "Common name must not be empty");
			List<String> altNames = switch (this.altNames.size()) {
			case 0 -> java.util.Collections.emptyList();
			case 1 -> java.util.Collections.singletonList(this.altNames.get(0));
			default -> java.util.Collections.unmodifiableList(new ArrayList<>(this.altNames));
			};

			List<String> ipSubjectAltNames = switch (this.ipSubjectAltNames.size()) {
			case 0 -> java.util.Collections.emptyList();
			case 1 -> java.util.Collections.singletonList(this.ipSubjectAltNames.get(0));
			default -> java.util.Collections.unmodifiableList(new ArrayList<>(this.ipSubjectAltNames));
			};

			List<String> uriSubjectAltNames = switch (this.uriSubjectAltNames.size()) {
			case 0 -> java.util.Collections.emptyList();
			case 1 -> java.util.Collections.singletonList(this.uriSubjectAltNames.get(0));
			default -> java.util.Collections.unmodifiableList(new ArrayList<>(this.uriSubjectAltNames));
			};

			List<String> otherSans = switch (this.otherSans.size()) {
			case 0 -> java.util.Collections.emptyList();
			case 1 -> java.util.Collections.singletonList(this.otherSans.get(0));
			default -> java.util.Collections.unmodifiableList(new ArrayList<>(this.otherSans));
			};
			return new VaultCertificateRequest(this.commonName, this.excludeCommonNameFromSubjectAltNames, altNames,
					ipSubjectAltNames, uriSubjectAltNames, otherSans, this.ttl, notAfter, this.format,
					this.privateKeyFormat, userIds);
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
