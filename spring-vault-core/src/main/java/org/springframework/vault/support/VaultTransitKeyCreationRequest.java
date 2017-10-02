/*
 * Copyright 2016 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.util.Assert;

/**
 * Transit backend key creation request options.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 */
public class VaultTransitKeyCreationRequest {

	private final boolean derived;

	@JsonProperty("type")
	private final String type;

	@JsonProperty("convergent_encryption")
	private final boolean convergentEncryption;

	private final boolean exportable;

	private VaultTransitKeyCreationRequest(boolean derived, String type,
			boolean convergentEncryption, boolean exportable) {
		this.derived = derived;
		this.type = type;
		this.convergentEncryption = convergentEncryption;
		this.exportable = exportable;
	}

	/**
	 * Create a new {@link VaultTransitKeyCreationRequest} specifically for a {@code type}
	 * .
	 * @param the key type to use, must not be {@literal null} or empty.
	 * @return a new {@link VaultTransitKeyCreationRequest} for the given key {@code type}
	 * .
	 * @since 2.0
	 */
	public static VaultTransitKeyCreationRequest ofKeyType(String type) {
		return builder().type(type).build();
	}

	/**
	 * @return a new {@link VaultTransitKeyCreationRequestBuilder}.
	 */
	public static VaultTransitKeyCreationRequestBuilder builder() {
		return new VaultTransitKeyCreationRequestBuilder();
	}

	/**
	 *
	 * @return {@literal true} if key derivation MUST be used.
	 */
	public boolean getDerived() {
		return derived;
	}

	/**
	 *
	 * @return {@literal true} if convergent encryption should be used (where the same
	 * plaintext creates the same cipher text).
	 */
	public boolean getConvergentEncryption() {
		return convergentEncryption;
	}

	/**
	 *
	 * @return the key type.
	 */
	public String getType() {
		return type;
	}

	/**
	 *
	 * @return {@literal true} if key MUST be exportable.
	 */
	public boolean getExportable() {
		return this.exportable;
	}

	/**
	 * Builder for {@link VaultTransitKeyCreationRequest}.
	 */
	public static class VaultTransitKeyCreationRequestBuilder {

		private boolean derived;
		private String type = "aes256-gcm96";
		private boolean convergentEncryption;
		private boolean exportable;

		VaultTransitKeyCreationRequestBuilder() {
		}

		/**
		 * Configure the key type.
		 *
		 * @param type the type of key to create, must not be empty or {@literal null}.
		 * @return {@code this} {@link VaultTransitKeyCreationRequestBuilder}.
		 */
		public VaultTransitKeyCreationRequestBuilder type(String type) {

			Assert.hasText(type, "Type must not be null or empty");

			this.type = type;
			return this;
		}

		/**
		 * Configure key derivation.
		 *
		 * @param derived {@literal true} if key derivation MUST be used. If enabled, all
		 * encrypt/decrypt requests to this named key must provide a context which is used
		 * for key derivation. Defaults to {@literal false}.
		 * @return {@code this} {@link VaultTransitKeyCreationRequestBuilder}.
		 */
		public VaultTransitKeyCreationRequestBuilder derived(boolean derived) {

			this.derived = derived;
			return this;
		}

		/**
		 * Configure convergent encryption where the same plaintext creates the same
		 * ciphertext. Requires {@link #derived(boolean)} to be {@literal true}.
		 *
		 * @param convergentEncryption {@literal true} the same plaintext creates the same
		 * ciphertext. Defaults to {@literal false}.
		 * @return {@code this} {@link VaultTransitKeyCreationRequestBuilder}.
		 */
		public VaultTransitKeyCreationRequestBuilder convergentEncryption(
				boolean convergentEncryption) {

			this.convergentEncryption = convergentEncryption;
			return this;
		}

		/**
		 * Configure if the raw key is exportable.
		 *
		 * @param exportable {@literal true} the raw key is exportable. Defaults to
		 * {@literal false}.
		 * @return {@code this} {@link VaultTransitKeyCreationRequestBuilder}.
		 */
		public VaultTransitKeyCreationRequestBuilder exportable(boolean exportable) {

			this.exportable = exportable;
			return this;
		}

		/**
		 * Build a new {@link VaultTransitKeyCreationRequest} instance. Requires
		 * {@link #type(String)} to be configured.
		 *
		 * @return a new {@link VaultTransitKeyCreationRequest}.
		 */
		public VaultTransitKeyCreationRequest build() {

			Assert.hasText(type, "Type must not be empty");

			return new VaultTransitKeyCreationRequest(derived, type,
					convergentEncryption, exportable);
		}
	}
}
