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

import java.util.Map;

import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object to bind Vault HTTP Mount API requests/responses.
 * <p>
 * A {@link VaultMount} represents an auth or secret mount with its config details. Instances of this class are
 * immutable once constructed.
 *
 * @author Mark Paluch
 * @see #builder()
 */
public class VaultMount {

	/**
	 * Backend type. Can be an auth or secret backend.
	 */
	private final String type;

	/**
	 * Human readable description of the mount.
	 */
	private final String description;

	/**
	 * Additional configuration.
	 */
	private final Map<String, Object> config;

	private VaultMount(@JsonProperty("type") String type, @JsonProperty("description") String description,
			@JsonProperty("config") Map<String, Object> config) {
		this.type = type;
		this.description = description;
		this.config = config;
	}

	/**
	 * Creates a new {@link VaultMount} given a {@code type}.
	 *
	 * @param type backend type, must not be empty or {@literal null}.
	 * @return the created {@link VaultMount}.
	 */
	public static VaultMount create(String type) {
		return builder().type(type).build();
	}

	/**
	 * @return a new {@link VaultMountBuilder}.
	 */
	public static VaultMountBuilder builder() {
		return new VaultMountBuilder();
	}

	/**
	 * @return the backend type.
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return human readable description of this mount.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @return additional configuration details.
	 */
	public Map<String, Object> getConfig() {
		return config;
	}

	/**
	 * Builder to build a {@link VaultMount}.
	 */
	public static class VaultMountBuilder {

		private String type;

		private String description;

		private Map<String, Object> config;

		VaultMountBuilder() {}

		/**
		 * Configure the backend type.
		 * 
		 * @param type the backend type, must not be empty or {@literal null}.
		 * @return {@literal this} {@link VaultMountBuilder}.
		 */
		public VaultMount.VaultMountBuilder type(String type) {

			Assert.hasText(type, "Type must not be empty or null");

			this.type = type;
			return this;
		}

		/**
		 * Configure a human readable description of this mount.
		 * 
		 * @param description a human readable description of this mount.
		 * @return {@literal this} {@link VaultMountBuilder}.
		 */
		public VaultMount.VaultMountBuilder description(String description) {
			this.description = description;
			return this;
		}

		/**
		 * Set additional configuration details for this mount.
		 * 
		 * @param config additional configuration details for this mount.
		 * @return {@literal this} {@link VaultMountBuilder}.
		 */
		public VaultMount.VaultMountBuilder config(Map<String, Object> config) {
			this.config = config;
			return this;
		}

		/**
		 * Builds a new {@link VaultMount} instance. Requires {@link #type(String)} to be configured.
		 *
		 * @return a new {@link VaultMount}.
		 */
		public VaultMount build() {

			Assert.hasText(type, "Type must not be empty or null");

			return new VaultMount(type, description, config);
		}
	}
}
