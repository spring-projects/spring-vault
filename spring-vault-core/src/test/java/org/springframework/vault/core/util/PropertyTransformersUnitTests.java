/*
 * Copyright 2017-2019 the original author or authors.
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

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mark Paluch
 */
public class PropertyTransformersUnitTests {

	Map<String, String> properties = Collections.singletonMap("key", "value");

	@Test
	public void propertyNamePrefix() {

		PropertyTransformer propertyTransformer = PropertyTransformers
				.propertyNamePrefix("my-prefix.");

		assertThat(propertyTransformer.transformProperties(properties)).hasSize(1)
				.containsEntry("my-prefix.key", "value");
	}

	@Test
	public void propertyNamePrefixChaining() {

		PropertyTransformer propertyTransformer = PropertyTransformers
				.propertyNamePrefix("my-prefix.").andThen(
						PropertyTransformers.propertyNamePrefix("foo-bar."));

		assertThat(propertyTransformer.transformProperties(properties)).hasSize(1)
				.containsEntry("foo-bar.my-prefix.key", "value");
	}

	@Test
	public void longChaining() {

		PropertyTransformer last = PropertyTransformers.propertyNamePrefix("last.")
				.andThen(PropertyTransformers.noop());

		PropertyTransformer middle = PropertyTransformers.propertyNamePrefix("middle.")
				.andThen(PropertyTransformers.propertyNamePrefix("after-middle."));

		PropertyTransformer propertyTransformer = PropertyTransformers
				.propertyNamePrefix("inner.")
				.andThen(PropertyTransformers.noop().andThen(middle)).andThen(last);

		assertThat(propertyTransformer.transformProperties(properties)).hasSize(1)
				.containsEntry("last.after-middle.middle.inner.key", "value");
	}
}
