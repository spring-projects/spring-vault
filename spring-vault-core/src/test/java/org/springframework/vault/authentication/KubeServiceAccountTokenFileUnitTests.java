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
package org.springframework.vault.authentication;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KubernetesServiceAccountTokenFile}.
 *
 * @author Michal Budzyn
 * @author Mark Paluch
 */
class KubeServiceAccountTokenFileUnitTests {

	final static String TEST_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9."
			+ "eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy"
			+ "5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJu"
			+ "ZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG"
			+ "9rZW4tNHcydmciLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZp"
			+ "Y2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2Vydm"
			+ "ljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjllMjQzNWY0LTgxNDctMT"
			+ "FlNy05MGFiLTA4MDAyN2NlZTQwNyIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3" + "VudDpkZWZhdWx0OmRlZmF1bHQifQ."
			+ "asFRZRZ1gRj9sF0lQqbbxrNhW_lOdj9WjqUpH_4TstxqZZ7B36a0xKKXg6XaFW"
			+ "JY1eMsytpwa7uMzvsvf2pYCcklinaSE_F-wc42IOWcpwSLl4PND92Tp7n_JYEAbb"
			+ "SQVfZPzQ2Y7b6cWu6NRzDs638LwVTqYeWMWbcWlOMaTxjMzGTcgDe5RWslkKUPkY"
			+ "svPOAFtt5ZErwtVcvTUmplJfHzdWwatlpZRQhYkxGgRIJ6LabXfZOd2N_TchJ3tH"
			+ "jAVBzUDTQq3APQssGb9df2RxVTUiyzbhdRGt7129-LCZ8rZYE7E-Mr3SSpExGYcD" + "k-v0It8hky0CKtCLs2UHiABA";

	@Test
	void shouldGetJwtTokenFromResource() {

		String jwt = new KubernetesServiceAccountTokenFile(new ClassPathResource("kube-jwt-token")).get();

		assertThat(jwt).isEqualTo(TEST_TOKEN);
	}

	@Test
	void shouldGetJwtTokenFromFile() throws Exception {

		String fileName = new ClassPathResource("kube-jwt-token").getFile().getAbsolutePath();
		String jwt = new KubernetesServiceAccountTokenFile(fileName).get();
		assertThat(jwt).isEqualTo(TEST_TOKEN);
	}

}
