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
package org.springframework.vault.core.env;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.util.PropertyTransformer;
import org.springframework.vault.core.util.PropertyTransformers;
import org.springframework.vault.support.JsonMapFlattener;
import org.springframework.vault.support.VaultResponse;

/**
 * {@link PropertySource} that reads keys and values from a {@link VaultTemplate} and
 * {@code path}. Transforms properties after retrieving these from Vault using
 * {@link PropertyTransformer}.
 *
 * @author Mark Paluch
 * @see org.springframework.core.env.PropertiesPropertySource
 * @see PropertyTransformer
 * @see PropertyTransformers
 */
public class VaultPropertySource extends EnumerablePropertySource<VaultOperations> {

	private static final Log logger = LogFactory.getLog(VaultPropertySource.class);

	private final String path;

	private final Map<String, Object> properties = new LinkedHashMap<>();

	private final PropertyTransformer propertyTransformer;

	private final Object lock = new Object();

	/**
	 * Create a new {@link VaultPropertySource} given a {@link VaultTemplate} and
	 * {@code path} inside of Vault. This property source loads properties upon
	 * construction.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 * @param path the path inside Vault (e.g. {@code secret/myapp/myproperties}. Must not
	 * be empty or {@literal null}.
	 */
	public VaultPropertySource(VaultOperations vaultOperations, String path) {
		this(path, vaultOperations, path);
	}

	/**
	 * Create a new {@link VaultPropertySource} given a {@code name},
	 * {@link VaultTemplate} and {@code path} inside of Vault. This property source loads
	 * properties upon construction.
	 *
	 * @param name name of the property source, must not be {@literal null}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path the path inside Vault (e.g. {@code secret/myapp/myproperties}. Must not
	 * be empty or {@literal null}.
	 */
	public VaultPropertySource(String name, VaultOperations vaultOperations, String path) {
		this(name, vaultOperations, path, PropertyTransformers.noop());
	}

	/**
	 * Create a new {@link VaultPropertySource} given a {@code name},
	 * {@link VaultTemplate} and {@code path} inside of Vault. This property source loads
	 * properties upon construction and transforms these by applying
	 * {@link PropertyTransformer}.
	 *
	 * @param name name of the property source, must not be {@literal null}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path the path inside Vault (e.g. {@code secret/myapp/myproperties}. Must not
	 * be empty or {@literal null}.
	 * @param propertyTransformer object to transform properties.
	 * @see PropertyTransformers
	 */
	public VaultPropertySource(String name, VaultOperations vaultOperations, String path,
			PropertyTransformer propertyTransformer) {

		super(name, vaultOperations);

		Assert.hasText(path, "Path name must contain at least one character");
		Assert.isTrue(!path.startsWith("/"), "Path name must not start with a slash (/)");
		Assert.notNull(propertyTransformer, "PropertyTransformer must not be null");

		this.path = path;
		this.propertyTransformer = propertyTransformer
				.andThen(PropertyTransformers.removeNullProperties());

		loadProperties();
	}

	/**
	 * Initialize property source and read properties from Vault.
	 */
	protected void loadProperties() {

		synchronized (lock) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Fetching properties from Vault at %s", path));
			}

			Map<String, Object> properties = doGetProperties(path);

			if (properties != null) {
				this.properties.putAll(doTransformProperties(properties));
			}
		}
	}

	@Override
	public Object getProperty(String name) {
		return this.properties.get(name);
	}

	@Override
	public String[] getPropertyNames() {

		Set<String> strings = this.properties.keySet();
		return strings.toArray(new String[strings.size()]);
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	/**
	 * Hook method to obtain properties from Vault.
	 *
	 * @param path the path, must not be empty or {@literal null}.
	 * @return the resulting {@link Map} or {@literal null} if properties were not found.
	 * @throws VaultException on problems retrieving properties
	 */
	@Nullable
	protected Map<String, Object> doGetProperties(String path) throws VaultException {

		VaultResponse vaultResponse = this.source.read(path);

		if (vaultResponse == null || vaultResponse.getData() == null) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("No properties found at %s", path));
			}

			return null;
		}

		return flattenMap(vaultResponse.getData());
	}

	/**
	 * Hook method to transform properties using {@link PropertyTransformer}.
	 *
	 * @param properties must not be {@literal null}.
	 * @return the transformed properties.
	 */
	protected Map<String, Object> doTransformProperties(Map<String, Object> properties) {
		return this.propertyTransformer.transformProperties(properties);
	}

	/**
	 * Utility method converting a {@code String/Object} map to a flat
	 * {@code String/String} map.
	 *
	 * @param data the map
	 * @return the flattened map.
	 * @deprecated since 2.0, use {@link #flattenMap(Map)} to retain JSON data types.
	 */
	@Deprecated
	protected Map<String, String> toStringMap(Map<String, Object> data) {
		return JsonMapFlattener.flattenToStringMap(data);
	}

	/**
	 * Utility method converting a {@code String/Object} map to a flat
	 * {@code String/Object} map. Nested objects are represented with property path keys.
	 *
	 * @param data the map
	 * @return the flattened map.
	 * @since 2.0
	 */
	protected Map<String, Object> flattenMap(Map<String, Object> data) {
		return JsonMapFlattener.flatten(data);
	}
}
