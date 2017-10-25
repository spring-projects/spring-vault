/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.vault.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Unit tests for {@link KubeServiceAccountTokenFile}.
 *
 * @author Michal Budzyn
 */

public class KubeServiceAccountTokenFileUnitTests {

	private final static String TEST_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNHcydmciLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjllMjQzNWY0LTgxNDctMTFlNy05MGFiLTA4MDAyN2NlZTQwNyIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.asFRZRZ1gRj9sF0lQqbbxrNhW_lOdj9WjqUpH_4TstxqZZ7B36a0xKKXg6XaFWJY1eMsytpwa7uMzvsvf2pYCcklinaSE_F-wc42IOWcpwSLl4PND92Tp7n_JYEAbbSQVfZPzQ2Y7b6cWu6NRzDs638LwVTqYeWMWbcWlOMaTxjMzGTcgDe5RWslkKUPkYsvPOAFtt5ZErwtVcvTUmplJfHzdWwatlpZRQhYkxGgRIJ6LabXfZOd2N_TchJ3tHjAVBzUDTQq3APQssGb9df2RxVTUiyzbhdRGt7129-LCZ8rZYE7E-Mr3SSpExGYcDk-v0It8hky0CKtCLs2UHiABA";

	@Test
	public void shouldGetJwtTokenFromResource() throws Exception {
		final String jwt = new KubeServiceAccountTokenFile(
				new ClassPathResource("kube-jwt-token")).getKubeJwt();

		assertThat(jwt).isEqualTo(TEST_TOKEN);
	}

	@Test
	public void shouldGetJwtTokenFromFile() throws Exception {
		final String fileName = new ClassPathResource("kube-jwt-token").getFile()
				.getAbsolutePath();
		final String jwt = new KubeServiceAccountTokenFile(fileName).getKubeJwt();
		assertThat(jwt).isEqualTo(TEST_TOKEN);
	}
}