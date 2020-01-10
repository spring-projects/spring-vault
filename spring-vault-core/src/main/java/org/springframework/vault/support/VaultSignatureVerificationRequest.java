/*
 * Copyright 2016-2020 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Request for a signature verification.
 *
 * @author Luander Ribeiro
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultSignatureVerificationRequest {

	private final Plaintext plaintext;

	private final @Nullable Signature signature;

	private final @Nullable Hmac hmac;

	private final @Nullable String algorithm;

	private VaultSignatureVerificationRequest(Plaintext plaintext,
			@Nullable Signature signature, @Nullable Hmac hmac, @Nullable String algorithm) {

		this.plaintext = plaintext;
		this.signature = signature;
		this.hmac = hmac;
		this.algorithm = algorithm;
	}

	/**
	 * @return a new instance of {@link VaultSignatureVerificationRequestBuilder}.
	 */
	public static VaultSignatureVerificationRequestBuilder builder() {
		return new VaultSignatureVerificationRequestBuilder();
	}

	/**
	 * Create a new {@link VaultSignatureVerificationRequest} given {@link Plaintext} and
	 * {@link Signature}.
	 *
	 * @param plaintext the plaintext, must not be {@literal null}.
	 * @param signature the signature, must not be {@literal null}.
	 * @return a new {@link VaultSignatureVerificationRequest} for {@link Plaintext} and
	 * {@link Signature}.
	 */
	public static VaultSignatureVerificationRequest create(Plaintext plaintext,
			Signature signature) {
		return builder().plaintext(plaintext).signature(signature).build();
	}

	/**
	 * Create a new {@link VaultSignatureVerificationRequest} given {@link Plaintext} and
	 * {@link Hmac}.
	 *
	 * @param plaintext the plaintext, must not be {@literal null}.
	 * @param hmac the hmac, must not be {@literal null}.
	 * @return a new {@link VaultSignatureVerificationRequest} for {@link Plaintext} and
	 * {@link Hmac}.
	 */
	public static VaultSignatureVerificationRequest create(Plaintext plaintext, Hmac hmac) {
		return builder().plaintext(plaintext).hmac(hmac).build();
	}

	/**
	 * @return plain text input used as basis to verify the signature.
	 */
	public Plaintext getPlaintext() {
		return plaintext;
	}

	/**
	 * @return signature resulting of a sign operation, can be {@literal null} if HMAC is
	 * used.
	 */
	@Nullable
	public Signature getSignature() {
		return signature;
	}

	/**
	 * @return digest resulting of a Hmac operation, can be {@literal null} if Signature
	 * is used.
	 */
	@Nullable
	public Hmac getHmac() {
		return hmac;
	}

	/**
	 * @return algorithm used for verifying the signature or {@literal null} to use the
	 * default algorithm.
	 */
	@Nullable
	public String getAlgorithm() {
		return algorithm;
	}

	/**
	 * Builder to build a {@link VaultSignatureVerificationRequest}.
	 */
	public static class VaultSignatureVerificationRequestBuilder {

		private @Nullable Plaintext input;

		private @Nullable Signature signature;

		private @Nullable Hmac hmac;

		private @Nullable String algorithm;

		/**
		 * Configure the {@link Plaintext} input to be used to verify the signature.
		 *
		 * @param input base input, must not be {@literal null}.
		 * @return {@code this} {@link VaultSignatureVerificationRequestBuilder}.
		 */
		public VaultSignatureVerificationRequestBuilder plaintext(Plaintext input) {

			Assert.notNull(input, "Plaintext must not be null");

			this.input = input;
			return this;
		}

		/**
		 * Configure the {@link Signature} to be verified. Signature verification requires
		 * either a {@link Signature} or a {@link #hmac(Hmac)} to be configured. Clears
		 * any previously configured {@link HMAC}.
		 *
		 * @param signature to be verified, must not be {@literal null}.
		 * @return {@code this} {@link VaultSignatureVerificationRequestBuilder}.
		 */
		public VaultSignatureVerificationRequestBuilder signature(Signature signature) {

			Assert.notNull(signature, "Signature must not be null");

			this.hmac = null;
			this.signature = signature;
			return this;
		}

		/**
		 * Configure the {@link Hmac} to be verified. Signature verification requires
		 * either a {@link Hmac} or a {@link #signature(Signature)} to be configured.
		 * Clears any previously configured {@link Signature}.
		 *
		 * @param hmac to be verified, must not be {@literal null}.
		 * @return {@code this} {@link VaultSignatureVerificationRequestBuilder}.
		 */
		public VaultSignatureVerificationRequestBuilder hmac(Hmac hmac) {

			Assert.notNull(hmac, "HMAC must not be null");

			this.signature = null;
			this.hmac = hmac;
			return this;
		}

		/**
		 * Configure the algorithm to be used for the operation.
		 *
		 * @param algorithm Specify the algorithm to be used for the operation. Supported
		 * algorithms are: {@literal sha2-224}, {@literal sha2-256}, {@literal sha2-384},
		 * {@literal sha2-512}. Defaults to {@literal sha2-256} if not set.
		 * @return {@code this} {@link VaultSignatureVerificationRequestBuilder}.
		 */
		public VaultSignatureVerificationRequestBuilder algorithm(String algorithm) {

			Assert.hasText(algorithm, "Algorithm must not be null or empty");

			this.algorithm = algorithm;
			return this;
		}

		/**
		 * Build a new {@link VaultSignatureVerificationRequest} instance. Requires
		 * {@link #plaintext(Plaintext)} and one of {@link #hmac(Hmac)},
		 * {@link #signature(Signature)} to be configured.
		 *
		 * @return a new {@link VaultHmacRequest}.
		 */
		public VaultSignatureVerificationRequest build() {

			Assert.notNull(input, "Plaintext input must not be null");
			Assert.isTrue(hmac != null || signature != null,
					"Either Signature or Hmac must not be null");

			return new VaultSignatureVerificationRequest(input, signature, hmac,
					algorithm);
		}
	}
}
