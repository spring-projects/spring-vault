/*
 * Copyright 2016-present the original author or authors.
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

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Request for a signature verification.
 *
 * @author Luander Ribeiro
 * @author Mark Paluch
 * @author My-Lan Aragon
 * @author James Luke
 * @author Nanne Baars
 * @since 2.0
 */
public class VaultSignatureVerificationRequest {

	private final Plaintext plaintext;

	private final @Nullable Signature signature;

	private final @Nullable Hmac hmac;

	private final @Nullable String hashAlgorithm;

	private final @Nullable String signatureAlgorithm;

	private final boolean prehashed;


	private VaultSignatureVerificationRequest(Plaintext plaintext, @Nullable Signature signature, @Nullable Hmac hmac,
			@Nullable String hashAlgorithm, @Nullable String signatureAlgorithm, boolean prehashed) {
		this.plaintext = plaintext;
		this.signature = signature;
		this.hmac = hmac;
		this.hashAlgorithm = hashAlgorithm;
		this.signatureAlgorithm = signatureAlgorithm;
		this.prehashed = prehashed;
	}


	/**
	 * @return a new instance of {@link VaultSignatureVerificationRequestBuilder}.
	 */
	public static VaultSignatureVerificationRequestBuilder builder() {
		return new VaultSignatureVerificationRequestBuilder();
	}

	/**
	 * Create a new {@code VaultSignatureVerificationRequest} given
	 * {@link Plaintext} and {@link Signature}.
	 * @param plaintext the plaintext, must not be {@literal null}.
	 * @param signature the signature, must not be {@literal null}.
	 * @return a new {@code VaultSignatureVerificationRequest} for {@link Plaintext}
	 * and {@link Signature}.
	 */
	public static VaultSignatureVerificationRequest create(Plaintext plaintext, Signature signature) {
		return builder().plaintext(plaintext).signature(signature).build();
	}

	/**
	 * Create a new {@code VaultSignatureVerificationRequest} given
	 * {@link Plaintext} and {@link Hmac}.
	 * @param plaintext the plaintext, must not be {@literal null}.
	 * @param hmac the hmac, must not be {@literal null}.
	 * @return a new {@code VaultSignatureVerificationRequest} for {@link Plaintext}
	 * and {@link Hmac}.
	 */
	public static VaultSignatureVerificationRequest create(Plaintext plaintext, Hmac hmac) {
		return builder().plaintext(plaintext).hmac(hmac).build();
	}


	/**
	 * @return plain text input used as basis to verify the signature.
	 */
	public Plaintext getPlaintext() {
		return this.plaintext;
	}

	/**
	 * @return signature resulting of a sign operation, can be {@literal null} if
	 * HMAC is used.
	 */
	public @Nullable Signature getSignature() {
		return this.signature;
	}

	/**
	 * @return digest resulting of a Hmac operation, can be {@literal null} if
	 * Signature is used.
	 */
	public @Nullable Hmac getHmac() {
		return this.hmac;
	}

	/**
	 * @return hash algorithm used for verifying the signature or {@literal null} to
	 * use the default algorithm.
	 * @since 2.4
	 */
	public @Nullable String getHashAlgorithm() {
		return this.hashAlgorithm;
	}

	/**
	 * @return signature algorithm used for verifying the signature when using an
	 * RSA key or {@literal null} to use the default algorithm.
	 * @since 2.4
	 */
	public @Nullable String getSignatureAlgorithm() {
		return this.signatureAlgorithm;
	}

	/**
	 * @return {@literal true} if the input is already hashed.
	 * @since 3.1
	 */
	public boolean isPrehashed() {
		return this.prehashed;
	}


	/**
	 * Builder to build a {@link VaultSignatureVerificationRequest}.
	 */
	public static class VaultSignatureVerificationRequestBuilder {

		private @Nullable Plaintext input;

		private @Nullable Signature signature;

		private @Nullable Hmac hmac;

		private @Nullable String hashAlgorithm;

		private @Nullable String signatureAlgorithm;

		private boolean prehashed;


		VaultSignatureVerificationRequestBuilder() {
		}


		/**
		 * Configure the {@link Plaintext} input to be used to verify the signature.
		 * @param input base input, must not be {@literal null}.
		 * @return this builder.
		 */
		public VaultSignatureVerificationRequestBuilder plaintext(Plaintext input) {
			Assert.notNull(input, "Plaintext must not be null");
			this.input = input;
			return this;
		}

		/**
		 * Configure the {@link Signature} to be verified. Signature verification
		 * requires either a {@link Signature} or a {@link #hmac(Hmac)} to be
		 * configured. Clears any previously configured {@link #hmac(Hmac)}.
		 * @param signature to be verified, must not be {@literal null}.
		 * @return this builder.
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
		 * @param hmac to be verified, must not be {@literal null}.
		 * @return this builder.
		 */
		public VaultSignatureVerificationRequestBuilder hmac(Hmac hmac) {
			Assert.notNull(hmac, "HMAC must not be null");
			this.signature = null;
			this.hmac = hmac;
			return this;
		}

		/**
		 * Configure the hash algorithm to be used for the operation.
		 * @param hashAlgorithm Specify the hash algorithm to be used for the operation.
		 * Supported algorithms are: {@literal sha1}, {@literal sha2-224},
		 * {@literal sha2-256}, {@literal sha2-384}, {@literal sha2-512}. Defaults to
		 * {@literal sha2-256} if not set.
		 * @return this builder.
		 * @since 2.4
		 */
		public VaultSignatureVerificationRequestBuilder hashAlgorithm(String hashAlgorithm) {
			Assert.hasText(hashAlgorithm, "Hash algorithm must not be null or empty");
			this.hashAlgorithm = hashAlgorithm;
			return this;
		}

		/**
		 * Set to {@literal true} when the input is already hashed. If the key type is
		 * {@literal rsa-2048}, {@literal rsa-3072}, or {@literal rsa-4096} then specify
		 * the algorithm used to hash the input through {@link #hashAlgorithm(String)}.
		 * @param prehashed whether the input is already hashed.
		 * @return this builder.
		 * @since 3.1
		 */
		public VaultSignatureVerificationRequestBuilder prehashed(boolean prehashed) {
			this.prehashed = prehashed;
			return this;
		}

		/**
		 * Configure the signature algorithm to be used for the operation when using an
		 * RSA key.
		 * @param signatureAlgorithm Specify the signature algorithm to be used for the
		 * operation. Supported algorithms are: {@literal pss}, {@literal pkcs1v15}.
		 * Defaults to {@literal pss} if not set.
		 * @return this builder.
		 * @since 2.4
		 */
		public VaultSignatureVerificationRequestBuilder signatureAlgorithm(String signatureAlgorithm) {
			Assert.hasText(signatureAlgorithm, "Signature algorithm must not be null or empty");
			this.signatureAlgorithm = signatureAlgorithm;
			return this;
		}

		/**
		 * Build a new {@link VaultSignatureVerificationRequest} instance. Requires
		 * {@link #plaintext(Plaintext)} and one of {@link #hmac(Hmac)},
		 * {@link #signature(Signature)} to be configured.
		 * @return a new {@link VaultHmacRequest}.
		 */
		public VaultSignatureVerificationRequest build() {
			Assert.notNull(this.input, "Plaintext input must not be null");
			Assert.isTrue(this.hmac != null || this.signature != null, "Either Signature or Hmac must not be null");
			return new VaultSignatureVerificationRequest(this.input, this.signature, this.hmac, this.hashAlgorithm,
					this.signatureAlgorithm, this.prehashed);
		}

	}

}
