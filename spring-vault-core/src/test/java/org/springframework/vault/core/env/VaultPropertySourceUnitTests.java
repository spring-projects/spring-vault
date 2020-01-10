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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.util.PropertyTransformers;
import org.springframework.vault.support.VaultResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VaultPropertySource}.
 *
 * @author Mark Paluch
 */
@RunWith(MockitoJUnitRunner.class)
public class VaultPropertySourceUnitTests {

	@Mock
	VaultTemplate vaultTemplate;

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectEmptyPath() {
		new VaultPropertySource("hello", vaultTemplate, "", PropertyTransformers.noop());

	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectPathStartingWithSlash() {
		new VaultPropertySource("hello", vaultTemplate, "/secret",
				PropertyTransformers.noop());
	}

	@Test
	public void shouldLoadProperties() {

		prepareResponse();

		VaultPropertySource vaultPropertySource = new VaultPropertySource("hello",
				vaultTemplate, "secret/myapp", PropertyTransformers.noop());

		assertThat(vaultPropertySource.getProperty("key")).isEqualTo("value");
		assertThat(vaultPropertySource.getProperty("integer")).isEqualTo(1);
		assertThat(vaultPropertySource.getProperty("complex.key")).isEqualTo("value");
		assertThat(vaultPropertySource.getProperty("empty")).isNull();
		assertThat(vaultPropertySource.containsProperty("empty")).isFalse();
	}

	@Test
	public void shouldLoadAndTransformProperties() {

		prepareResponse();

		VaultPropertySource vaultPropertySource = new VaultPropertySource("hello",
				vaultTemplate, "secret/myapp",
				PropertyTransformers.propertyNamePrefix("database."));

		assertThat(vaultPropertySource.containsProperty("database.key")).isTrue();
		assertThat(vaultPropertySource.containsProperty("key")).isFalse();
		assertThat(vaultPropertySource.getProperty("database.key")).isEqualTo("value");
		assertThat(vaultPropertySource.getProperty("key")).isNull();
		assertThat(vaultPropertySource.getProperty("database.integer")).isEqualTo(1);
		assertThat(vaultPropertySource.getProperty("database.complex.key")).isEqualTo(
				"value");
	}

	@Test
	public void getPropertyNamesShouldReturnNames() {

		prepareResponse();

		VaultPropertySource vaultPropertySource = new VaultPropertySource("hello",
				vaultTemplate, "secret/myapp", PropertyTransformers.noop());

		assertThat(vaultPropertySource.getPropertyNames()).contains("key", "integer",
				"complex.key");
	}

	private void prepareResponse() {

		Map<String, Object> data = new LinkedHashMap<String, Object>();
		data.put("key", "value");
		data.put("integer", 1);
		data.put("empty", null);
		data.put("complex", Collections.singletonMap("key", "value"));

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(data);

		when(vaultTemplate.read("secret/myapp")).thenReturn(vaultResponse);
	}
}
