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

/**
 * Value object to bind Vault HTTP Mount API requests/responses.
 * 
 * @author Mark Paluch
 */
public class VaultMount {

	private String type;

	private String description;

	private Map<String, Object> config;

	/**
	 * Creates a new {@link VaultMount}.
	 */
	public VaultMount() {}

	/**
	 * Creates a new {@link VaultMount} given a {@code type}.
	 * 
	 * @param type must not be empty or {@literal null}.
	 */
	public VaultMount(String type) {

		Assert.hasText(type, "Type must not be empty");

		this.type = type;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Map<String, Object> getConfig() {
		return config;
	}

	public void setConfig(Map<String, Object> config) {
		this.config = config;
	}
}
