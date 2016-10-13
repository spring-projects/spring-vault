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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.springframework.vault.core.VaultTemplate;
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
	public void shouldRejectEmptyPath() throws Exception {
		new VaultPropertySource("hello", vaultTemplate, "");

	}

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectPathStartingWithSlash() throws Exception {
		new VaultPropertySource("hello", vaultTemplate, "/secret");
	}

	@Test
	public void shouldLoadProperties() throws Exception {

		prepareResponse();

		VaultPropertySource vaultPropertySource = new VaultPropertySource("hello",
				vaultTemplate, "secret/myapp");

		assertThat(vaultPropertySource.getProperty("key")).isEqualTo("value");
		assertThat(vaultPropertySource.getProperty("integer")).isEqualTo("1");
	}

	@Test
	public void getPropertyNamesShouldReturnNames() throws Exception {

		prepareResponse();

		VaultPropertySource vaultPropertySource = new VaultPropertySource("hello",
				vaultTemplate, "secret/myapp");

		assertThat(vaultPropertySource.getPropertyNames()).contains("key", "integer");
	}

	private void prepareResponse() {

		Map<String, Object> data = new LinkedHashMap<String, Object>();
		data.put("key", "value");
		data.put("integer", 1);

		VaultResponse vaultResponse = new VaultResponse();
		vaultResponse.setData(data);

		when(vaultTemplate.read("secret/myapp")).thenReturn(vaultResponse);
	}
}
