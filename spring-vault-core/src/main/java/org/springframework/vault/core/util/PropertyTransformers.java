/*
 * Copyright 2017-2020 the original author or authors.
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
package org.springframework.vault.core.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.util.Assert;

/**
 * Implementations of {@link PropertyTransformer} that provide various useful property
 * transformation operations, prefixing, etc.
 *
 * @author Mark Paluch
 */
public abstract class PropertyTransformers {

	/**
	 * @return "no-operation" transformer which simply returns given name as is. Used
	 * commonly as placeholder or marker.
	 */
	public static PropertyTransformer noop() {
		return NoOpPropertyTransformer.instance();
	}

	/**
	 * @return removes {@literal null} value properties.
	 */
	public static PropertyTransformer removeNullProperties() {
		return RemoveNullProperties.instance();
	}

	/**
	 * @param propertyNamePrefix the prefix to add to each property name.
	 * @return {@link PropertyTransformer} to add {@code propertyNamePrefix} to each
	 * property name.
	 */
	public static PropertyTransformer propertyNamePrefix(String propertyNamePrefix) {
		return KeyPrefixPropertyTransformer.forPrefix(propertyNamePrefix);
	}

	/**
	 * {@link PropertyTransformer} that passes the given properties through without
	 * returning changed properties.
	 */
	static class NoOpPropertyTransformer implements PropertyTransformer {

		static NoOpPropertyTransformer INSTANCE = new NoOpPropertyTransformer();

		private NoOpPropertyTransformer() {
		}

		/**
		 * @return the {@link PropertyTransformer} instance.
		 */
		public static PropertyTransformer instance() {
			return INSTANCE;
		}

		@Override
		public Map<String, Object> transformProperties(Map<String, ? extends Object> input) {
			return (Map) input;
		}
	}

	/**
	 * {@link PropertyTransformer} to remove {@literal null}-value properties.
	 */
	static class RemoveNullProperties implements PropertyTransformer {

		static RemoveNullProperties INSTANCE = new RemoveNullProperties();

		private RemoveNullProperties() {
		}

		/**
		 * @return the {@link PropertyTransformer} instance.
		 */
		public static PropertyTransformer instance() {
			return INSTANCE;
		}

		@Override
		public Map<String, Object> transformProperties(Map<String, ? extends Object> input) {

			Map<String, Object> target = new LinkedHashMap<>(input.size(),
					1);

			for (Entry<String, ? extends Object> entry : input.entrySet()) {

				if (entry.getValue() == null) {
					continue;
				}

				target.put(entry.getKey(), entry.getValue());
			}

			return target;
		}
	}

	/**
	 * {@link PropertyTransformer} that adds a prefix to each key name.
	 */
	static class KeyPrefixPropertyTransformer implements PropertyTransformer {

		private final String propertyNamePrefix;

		private KeyPrefixPropertyTransformer(String propertyNamePrefix) {

			Assert.notNull(propertyNamePrefix, "Property name prefix must not be null");

			this.propertyNamePrefix = propertyNamePrefix;
		}

		/**
		 * Create a new {@link KeyPrefixPropertyTransformer} that adds a prefix to each
		 * key name.
		 * @param propertyNamePrefix the property name prefix to be added in front of each
		 * property name, must not be {@literal null}.
		 * @return a new {@link KeyPrefixPropertyTransformer} that adds a prefix to each
		 * key name.
		 */
		public static PropertyTransformer forPrefix(String propertyNamePrefix) {
			return new KeyPrefixPropertyTransformer(propertyNamePrefix);
		}

		@Override
		public Map<String, Object> transformProperties(Map<String, ? extends Object> input) {

			Map<String, Object> target = new LinkedHashMap<>(input.size(),
					1);

			for (Entry<String, ? extends Object> entry : input.entrySet()) {
				target.put(propertyNamePrefix + entry.getKey(), entry.getValue());
			}

			return target;
		}
	}
}
