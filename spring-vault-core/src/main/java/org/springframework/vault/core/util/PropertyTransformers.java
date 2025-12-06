/*
 * Copyright 2017-2025 the original author or authors.
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

import org.springframework.util.Assert;
import org.springframework.vault.annotation.PropertyMapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

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
     * @param mappings the rules to remap property names.
     * @return {@link PropertyTransformer} to map property names as specified in mappings.
     */
    public static PropertyTransformer propertyMappingBased(PropertyMapping[] mappings) {
        return PropertyMappingBasedPropertyTransformer.forPropertyMappings(mappings);
    }

	/**
	 * {@link PropertyTransformer} that passes the given properties through without
	 * returning changed properties.
	 */
	public static class NoOpPropertyTransformer implements PropertyTransformer {

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
		@SuppressWarnings("unchecked")
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

			Map<String, Object> target = new LinkedHashMap<>(input.size(), 1);

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
	public static class KeyPrefixPropertyTransformer implements PropertyTransformer {

		private final String propertyNamePrefix;

		private KeyPrefixPropertyTransformer(String propertyNamePrefix) {

			Assert.notNull(propertyNamePrefix, "Property name prefix must not be null");

			this.propertyNamePrefix = propertyNamePrefix;
		}

		public String getPropertyNamePrefix() {
			return propertyNamePrefix;
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

			Map<String, Object> target = new LinkedHashMap<>(input.size(), 1);

			for (Entry<String, ? extends Object> entry : input.entrySet()) {
				target.put(this.propertyNamePrefix + entry.getKey(), entry.getValue());
			}

			return target;
		}

	}

    /**
     * {@link PropertyTransformer} based on {@link org.springframework.vault.annotation.PropertyMapping} annotaion rules.
     */
    public static class PropertyMappingBasedPropertyTransformer implements PropertyTransformer {

        private final Map<String, String> mappingRules;

        private PropertyMappingBasedPropertyTransformer(PropertyMapping[] mappings) {

            Map<String, String> mappingRules = new HashMap<>();

            for (PropertyMapping propertyMapping : mappings) {
                mappingRules.put(propertyMapping.from(), propertyMapping.to());
            }

            this.mappingRules = Collections.unmodifiableMap(mappingRules);
        }

        /**
         * Create a new {@link PropertyMappingBasedPropertyTransformer} that maps property names as specified in mappings.
         * @param mappings the property mapping rules.
         * If key does not exist in input properties, property name will not be remapped
         * @return a new {@link PropertyMappingBasedPropertyTransformer}.
         */
        public static PropertyTransformer forPropertyMappings(PropertyMapping[] mappings) {
            return new PropertyMappingBasedPropertyTransformer(mappings);
        }

        @Override
        public Map<String, Object> transformProperties(Map<String, ? extends Object> input) {

            Map<String, Object> target = new LinkedHashMap<>(input.size(), 1);

            for (Entry<String, ? extends Object> entry : input.entrySet()) {
                String newKey = this.mappingRules.getOrDefault(entry.getKey(), entry.getKey());
                target.put(newKey, entry.getValue());
            }

            return target;
        }

    }
}
