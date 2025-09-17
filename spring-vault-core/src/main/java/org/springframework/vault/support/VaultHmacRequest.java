/*
 * Copyright 2017-2025 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Request for a HMAC Digest.
 *
 * @author Luander Ribeiro
 * @author Mark Paluch
 * @since 2.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VaultHmacRequest {

	private final Plaintext plaintext;

	private final @Nullable String algorithm;

	private final @Nullable Integer keyVersion;

	private VaultHmacRequest(Plaintext plaintext, @Nullable String algorithm, @Nullable Integer keyVersion) {

		this.algorithm = algorithm;
		this.plaintext = plaintext;
		this.keyVersion = keyVersion;
	}

	/**
	 * @return a new instance of {@link VaultHmacRequestBuilder}.
	 */
	public static VaultHmacRequestBuilder builder() {
		return new VaultHmacRequestBuilder();
	}

	/**
	 * Create a new {@link VaultHmacRequest} given {@link Plaintext}. Uses the default
	 * signature algorithm.
	 * @return a new {@link VaultHmacRequest} for the given {@link Plaintext input}.
	 */
	public static VaultHmacRequest create(Plaintext input) {
		return builder().plaintext(input).build();
	}

	/**
	 * @return plain text input used as basis to generate the digest.
	 */
	public Plaintext getPlaintext() {
		return this.plaintext;
	}

	/**
	 * @return algorithm used for creating the digest or {@literal null} to use the
	 * default algorithm.
	 */
	public @Nullable String getAlgorithm() {
		return this.algorithm;
	}

	/**
	 * @return version of the key used or {@literal null} to use the the latest version.
	 */
	public @Nullable Integer getKeyVersion() {
		return this.keyVersion;
	}

	/**
	 * Builder to build a {@link VaultHmacRequest}.
	 */
	public static class VaultHmacRequestBuilder {

		private @Nullable Plaintext plaintext;

		private @Nullable String algorithm;

		private @Nullable Integer keyVersion;

		/**
		 * Configure the input to be used to create the digest.
		 * @param input base input to create the digest, must not be {@literal null}.
		 * @return {@code this} {@link VaultHmacRequestBuilder}.
		 */
		public VaultHmacRequestBuilder plaintext(Plaintext input) {

			Assert.notNull(input, "Plaintext must not be null");

			this.plaintext = input;
			return this;
		}

		/**
		 * Configure the algorithm to be used for the operation.
		 * @param algorithm Specify the algorithm to be used for the operation. Supported
		 * algorithms are: {@literal sha2-224}, {@literal sha2-256}, {@literal sha2-384},
		 * {@literal sha2-512}. Defaults to {@literal sha2-256} if not set.
		 * @return {@code this} {@link VaultHmacRequestBuilder}.
		 */
		public VaultHmacRequestBuilder algorithm(String algorithm) {

			Assert.hasText(algorithm, "Algorithm must not be null or empty");

			this.algorithm = algorithm;
			return this;
		}

		/**
		 * Configure the key version to be used for the operation.
		 * @param version key version to be used. If not set, uses the latest version.
		 * @return {@code this} {@link VaultHmacRequestBuilder}.
		 */
		public VaultHmacRequestBuilder keyVersion(int version) {

			this.keyVersion = version;
			return this;
		}

		/**
		 * Build a new {@link VaultHmacRequest} instance. Requires
		 * {@link #plaintext(Plaintext)} to be configured.
		 * @return a new {@link VaultHmacRequest}.
		 */
		public VaultHmacRequest build() {

			Assert.notNull(this.plaintext, "Plaintext input must not be null");

			return new VaultHmacRequest(this.plaintext, this.algorithm, this.keyVersion);
		}

	}

}
