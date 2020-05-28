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

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Value object to bind Vault HTTP Mount API requests/responses.
 * <p>
 * A {@link VaultMount} represents an auth or secret mount with its config details.
 * Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @author Maciej Drozdzowski
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
	@Nullable
	private final String description;

	/**
	 * Additional configuration.
	 */
	private final Map<String, Object> config;

	/**
	 * Mount type specific options.
	 */
	private final Map<String, String> options;

	VaultMount(@JsonProperty("type") String type, @Nullable @JsonProperty("description") String description,
			@Nullable @JsonProperty("config") Map<String, Object> config,
			@Nullable @JsonProperty("options") Map<String, String> options) {

		this.type = type;
		this.description = description;
		this.config = config != null ? config : Collections.emptyMap();
		this.options = options != null ? options : Collections.emptyMap();
	}

	/**
	 * Create a new {@link VaultMount} given a {@code type}.
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
		return this.type;
	}

	/**
	 * @return human readable description of this mount.
	 */
	@Nullable
	public String getDescription() {
		return this.description;
	}

	/**
	 * @return additional configuration details.
	 */
	@Nullable
	public Map<String, Object> getConfig() {
		return this.config;
	}

	/**
	 * @return mount type specific options.
	 * @since 2.2
	 */
	@Nullable
	public Map<String, String> getOptions() {
		return this.options;
	}

	/**
	 * Builder to build a {@link VaultMount}.
	 */
	public static class VaultMountBuilder {

		@Nullable
		private String type;

		@Nullable
		private String description;

		private Map<String, Object> config = Collections.emptyMap();

		private Map<String, String> options = Collections.emptyMap();

		VaultMountBuilder() {
		}

		/**
		 * Configure the backend type.
		 * @param type the backend type, must not be empty or {@literal null}.
		 * @return {@literal this} {@link VaultMountBuilder}.
		 */
		public VaultMountBuilder type(String type) {

			Assert.hasText(type, "Type must not be empty or null");

			this.type = type;
			return this;
		}

		/**
		 * Configure a human readable description of this mount.
		 * @param description a human readable description of this mount.
		 * @return {@literal this} {@link VaultMountBuilder}.
		 */
		public VaultMountBuilder description(String description) {

			this.description = description;
			return this;
		}

		/**
		 * Set additional configuration details for this mount.
		 * @param config additional configuration details for this mount.
		 * @return {@literal this} {@link VaultMountBuilder}.
		 */
		public VaultMountBuilder config(Map<String, Object> config) {

			Assert.notNull(config, "Configuration map must not be null");

			this.config = config;
			return this;
		}

		/**
		 * Set mount type specific options for this mount.
		 * @param options mount type specific options for this mount.
		 * @return {@literal this} {@link VaultMountBuilder}.
		 * @since 2.2
		 */
		public VaultMountBuilder options(Map<String, String> options) {

			Assert.notNull(options, "Options map must not be null");

			this.options = options;
			return this;
		}

		/**
		 * Build a new {@link VaultMount} instance. Requires {@link #type(String)} to be
		 * configured.
		 * @return a new {@link VaultMount}.
		 */
		public VaultMount build() {

			Assert.notNull(this.type, "Type must not be null");
			Assert.hasText(this.type, "Type must not be empty or null");

			return new VaultMount(this.type, this.description, this.config, this.options);
		}

	}

}
