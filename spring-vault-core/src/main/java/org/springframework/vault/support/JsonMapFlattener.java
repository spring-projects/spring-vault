/*
 * Copyright 2016-2018 the original author or authors.
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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Flattens a hierarchical {@link Map} of objects into a property {@link Map}.
 * <p>
 * Flattening is particularly useful when representing a JSON object as
 * {@link java.util.Properties}
 * <p>
 * {@link JsonMapFlattener} flattens {@link Map maps} containing nested
 * {@link java.util.List}, {@link Map} and simple values into a flat representation. The
 * hierarchical structure is reflected in properties using dot-notation. Nested maps are
 * considered as sub-documents.
 * <p>
 * Input:
 *
 * <pre>
 * <code>
 *     {"key": {"nested: "value"}, "another.key": ["one", "two"] }
 * </code>
 * </pre>
 *
 * <br>
 * Result
 *
 * <pre>
 * <code> key.nested=value
 *  another.key[0]=one
 *  another.key[1]=two
 * </code>
 * </pre>
 *
 * @author Mark Paluch
 */
public abstract class JsonMapFlattener {

	private JsonMapFlattener() {
	}

	/**
	 * Flatten a hierarchical {@link Map} into a flat {@link Map} with key names using
	 * property dot notation.
	 *
	 * @param inputMap must not be {@literal null}.
	 * @return the resulting {@link Map}.
	 */
	public static Map<String, String> flatten(Map<String, ? extends Object> inputMap) {

		Assert.notNull(inputMap, "Input Map must not be null");

		Map<String, String> resultMap = new LinkedHashMap<String, String>();

		doFlatten("", inputMap.entrySet().iterator(), resultMap);

		return resultMap;
	}

	private static void doFlatten(String propertyPrefix,
			Iterator<? extends Entry<String, ?>> inputMap, Map<String, String> resultMap) {

		if (StringUtils.hasText(propertyPrefix)) {
			propertyPrefix = propertyPrefix + ".";
		}

		while (inputMap.hasNext()) {

			Entry<String, ? extends Object> entry = inputMap.next();
			flattenElement(propertyPrefix + entry.getKey(), entry.getValue(), resultMap);
		}
	}

	@SuppressWarnings("unchecked")
	private static void flattenElement(String propertyPrefix, Object source,
			Map<String, String> resultMap) {

		if (source instanceof Iterable) {
			flattenCollection(propertyPrefix, (Iterable<Object>) source, resultMap);
			return;
		}

		if (source instanceof Map) {
			doFlatten(propertyPrefix, ((Map<String, ?>) source).entrySet().iterator(),
					resultMap);
			return;
		}

		resultMap.put(propertyPrefix, source == null ? null : source.toString());
	}

	private static void flattenCollection(String propertyPrefix,
			Iterable<Object> iterable, Map<String, String> resultMap) {

		int counter = 0;

		for (Object element : iterable) {
			flattenElement(propertyPrefix + "[" + counter + "]", element, resultMap);
			counter++;
		}
	}
}
