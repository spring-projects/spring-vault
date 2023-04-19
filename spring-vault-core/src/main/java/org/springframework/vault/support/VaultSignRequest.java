/*
 * Copyright 2017-2023 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Request for a signature creation request.
 *
 * @author Luander Ribeiro
 * @author Mark Paluch
 * @author My-Lan Aragon
 * @since 2.0
 */
public class VaultSignRequest {

	private final Plaintext plaintext;

	private final @Nullable String hashAlgorithm;

	private final @Nullable String signatureAlgorithm;

	private VaultSignRequest(Plaintext plaintext, @Nullable String hashAlgorithm, @Nullable String signatureAlgorithm) {

		this.plaintext = plaintext;
		this.hashAlgorithm = hashAlgorithm;
		this.signatureAlgorithm = signatureAlgorithm;
	}

	/**
	 * @return a new instance of {@link VaultSignRequestBuilder}.
	 */
	public static VaultSignRequestBuilder builder() {
		return new VaultSignRequestBuilder();
	}

	/**
	 * Create a new {@link VaultSignRequest} given {@link Plaintext}. Uses the default
	 * algorithm.
	 * @return a new {@link VaultSignRequest} for the given {@link Plaintext input}.
	 */
	public static VaultSignRequest create(Plaintext input) {
		return builder().plaintext(input).build();
	}

	/**
	 * @return plain text input used as basis to generate the signature.
	 */
	public Plaintext getPlaintext() {
		return this.plaintext;
	}

	/**
	 * @return hashAlgorithm used for creating the signature or {@literal null} to use the
	 * default hash algorithm.
	 * @since 2.4
	 */
	@Nullable
	public String getHashAlgorithm() {
		return this.hashAlgorithm;
	}

	/**
	 * @return signatureAlgorithm used for creating the signature when using an RSA key or
	 * {@literal null} to use the default signature algorithm.
	 * @since 2.4
	 */
	@Nullable
	public String getSignatureAlgorithm() {
		return this.signatureAlgorithm;
	}

	/**
	 * @return algorithm used for creating the signature or {@literal null} to use the
	 * default algorithm.
	 * @deprecated since 2.4, use {@link #getSignatureAlgorithm()} instead.
	 */
	@Deprecated(since = "2.4")
	@Nullable
	public String getAlgorithm() {
		return getSignatureAlgorithm();
	}

	/**
	 * Builder to build a {@link VaultSignRequest}.
	 */
	public static class VaultSignRequestBuilder {

		private @Nullable Plaintext plaintext;

		private @Nullable String hashAlgorithm;

		private @Nullable String signatureAlgorithm;

		/**
		 * Configure the input to be used to create the signature.
		 * @param input base input to create the signature, must not be {@literal null}.
		 * @return {@code this} {@link VaultSignRequestBuilder}.
		 */
		public VaultSignRequestBuilder plaintext(Plaintext input) {

			Assert.notNull(input, "Plaintext must not be null");

			this.plaintext = input;
			return this;
		}

		/**
		 * Configure the hash algorithm to be used for the operation.
		 * @param hashAlgorithm Specify the hash algorithm to be used for the operation.
		 * Supported algorithms are: {@literal sha1}, {@literal sha2-224},
		 * {@literal sha2-256}, {@literal sha2-384}, {@literal sha2-512}. Defaults to
		 * {@literal sha2-256} if not set.
		 * @return {@code this} {@link VaultSignRequestBuilder}.
		 */
		public VaultSignRequestBuilder hashAlgorithm(String hashAlgorithm) {

			Assert.hasText(hashAlgorithm, "Hash algorithm must not be null or empty");

			this.hashAlgorithm = hashAlgorithm;
			return this;
		}

		/**
		 * Configure the signature algorithm to be used for the operation when using an
		 * RSA key.
		 * @param signatureAlgorithm Specify the signature algorithm to be used for the
		 * operation. Supported algorithms are: {@literal pss}, {@literal pkcs1v15}.
		 * Defaults to {@literal pss} if not set.
		 * @return {@code this} {@link VaultSignRequestBuilder}.
		 */
		public VaultSignRequestBuilder signatureAlgorithm(String signatureAlgorithm) {

			Assert.hasText(signatureAlgorithm, "Signature algorithm must not be null or empty");

			this.signatureAlgorithm = signatureAlgorithm;
			return this;
		}

		/**
		 * Configure the algorithm to be used for the operation.
		 * @param algorithm Specify the algorithm to be used for the operation. Supported
		 * algorithms are: {@literal sha2-224}, {@literal sha2-256}, {@literal sha2-384},
		 * {@literal sha2-512}. Defaults to {@literal sha2-256} if not set.
		 * @return {@code this} {@link VaultSignRequestBuilder}.
		 * @deprecated since 2.4, use {@link #signatureAlgorithm(String)} instead.
		 */
		@Deprecated(since = "2.4")
		public VaultSignRequestBuilder algorithm(String algorithm) {
			return signatureAlgorithm(algorithm);
		}

		/**
		 * Build a new {@link VaultSignRequest} instance. Requires
		 * {@link #plaintext(Plaintext)} to be configured.
		 * @return a new {@link VaultSignRequest}.
		 */
		public VaultSignRequest build() {

			Assert.notNull(this.plaintext, "Plaintext input must not be null");

			return new VaultSignRequest(this.plaintext, this.hashAlgorithm, this.signatureAlgorithm);
		}

	}

}
