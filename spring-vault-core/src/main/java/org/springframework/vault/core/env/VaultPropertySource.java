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
package org.springframework.vault.core.env;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * {@link PropertySource} that reads keys and values from a {@link VaultTemplate} and
 * {@code path}.
 *
 * @author Mark Paluch
 * @since 3.1
 * @see org.springframework.core.env.PropertiesPropertySource
 */
public class VaultPropertySource extends EnumerablePropertySource<VaultOperations> {

	protected final static Log logger = LogFactory.getLog(VaultPropertySource.class);

	private final String path;
	private final Map<String, String> properties = new LinkedHashMap<String, String>();
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

		super(name, vaultOperations);

		Assert.hasText(path, "Path name must contain at least one character");
		Assert.isTrue(!path.startsWith("/"), "Path name must not start with a slash (/)");

		this.path = path;

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

			Map<String, String> properties = doGetProperties(path);

			if (properties != null) {
				this.properties.putAll(properties);
			}
		}
	}

	/**
	 * Hook method to obtain properties from Vault.
	 *
	 * @param path the path, must not be empty or {@literal null}.
	 * @return the resulting {@link Map} or {@literal null} if properties were not found.
	 * @throws VaultException on problems retrieving properties
	 */
	protected Map<String, String> doGetProperties(String path) throws VaultException {

		VaultResponse vaultResponse = this.source.read(path);

		if (vaultResponse == null || vaultResponse.getData() == null) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("No properties found at %s", path));
			}

			return null;
		}

		return toStringMap(vaultResponse.getData());
	}

	/**
	 * Utility method converting a {@code String/Object} map to a {@code String/String}
	 * map.
	 * 
	 * @param data the map
	 * @return
	 */
	protected Map<String, String> toStringMap(Map<String, Object> data) {

		Map<String, String> result = new LinkedHashMap<String, String>();

		if (data != null) {
			for (String s : data.keySet()) {
				Object value = data.get(s);
				if (value != null) {
					result.put(s, value.toString());
				}
			}
		}

		return result;
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
}
