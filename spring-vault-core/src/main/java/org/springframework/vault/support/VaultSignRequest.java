/*
 * Copyright 2017-2018 the original author or authors.
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
package org.springframework.vault.support;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Request for a signature creation request.
 *
 * @author Luander Ribeiro
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultSignRequest {

	private final Plaintext plaintext;

	private final @Nullable String algorithm;

	private VaultSignRequest(Plaintext plaintext, @Nullable String algorithm) {

		this.plaintext = plaintext;
		this.algorithm = algorithm;
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
	 *
	 * @return a new {@link VaultSignRequest} for the given {@link Plaintext input}.
	 */
	public static VaultSignRequest create(Plaintext input) {
		return builder().plaintext(input).build();
	}

	/**
	 * @return plain text input used as basis to generate the signature.
	 */
	public Plaintext getPlaintext() {
		return plaintext;
	}

	/**
	 * @return algorithm used for creating the signature or {@literal null} to use the
	 * default algorithm.
	 */
	@Nullable
	public String getAlgorithm() {
		return algorithm;
	}

	/**
	 * Builder to build a {@link VaultSignRequest}.
	 */
	public static class VaultSignRequestBuilder {

		private @Nullable Plaintext plaintext;

		private @Nullable String algorithm;

		/**
		 * Configure the input to be used to create the signature.
		 *
		 * @param input base input to create the signature, must not be {@literal null}.
		 * @return {@code this} {@link VaultSignRequestBuilder}.
		 */
		public VaultSignRequestBuilder plaintext(Plaintext input) {

			Assert.notNull(input, "Plaintext must not be null");

			this.plaintext = input;
			return this;
		}

		/**
		 * Configure the algorithm to be used for the operation.
		 *
		 * @param algorithm Specify the algorithm to be used for the operation. Supported
		 * algorithms are: {@literal sha2-224}, {@literal sha2-256}, {@literal sha2-384},
		 * {@literal sha2-512}. Defaults to {@literal sha2-256} if not set.
		 * @return {@code this} {@link VaultSignRequestBuilder}.
		 */
		public VaultSignRequestBuilder algorithm(String algorithm) {

			Assert.hasText(algorithm, "Algorithm must not be null or empty");

			this.algorithm = algorithm;
			return this;
		}

		/**
		 * Build a new {@link VaultSignRequest} instance. Requires
		 * {@link #plaintext(Plaintext)} to be configured.
		 *
		 * @return a new {@link VaultSignRequest}.
		 */
		public VaultSignRequest build() {

			Assert.notNull(plaintext, "Plaintext input must not be null");

			return new VaultSignRequest(plaintext, algorithm);
		}
	}
}
