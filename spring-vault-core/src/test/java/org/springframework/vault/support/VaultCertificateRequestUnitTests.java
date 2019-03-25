/*
 * Copyright 2016-2019 the original author or authors.
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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VaultCertificateRequest}.
 *
 * @author Mark Paluch
 */
public class VaultCertificateRequestUnitTests {

	@Test(expected = IllegalArgumentException.class)
	public void shouldRejectUnconfiguredBuilder() throws Exception {
		VaultCertificateRequest.builder().build();
	}

	@Test
	public void shouldBuildRequestWithCommonName() throws Exception {

		VaultCertificateRequest request = VaultCertificateRequest.builder()
				.commonName("hello.com").build();

		assertThat(request.getCommonName()).isEqualTo("hello.com");
	}

	@Test
	public void shouldBuildFullyConfiguredRequest() throws Exception {

		VaultCertificateRequest request = VaultCertificateRequest.builder() //
				.commonName("hello.com") //
				.withAltName("alt") //
				.withIpSubjectAltName("127.0.0.1") //
				.excludeCommonNameFromSubjectAltNames() //
				.build();

		assertThat(request.getCommonName()).isEqualTo("hello.com");
		assertThat(request.getAltNames()).hasSize(1).contains("alt");
		assertThat(request.getIpSubjectAltNames()).hasSize(1).contains("127.0.0.1");
		assertThat(request.isExcludeCommonNameFromSubjectAltNames()).isTrue();
		assertThat(request.getCommonName()).isEqualTo("hello.com");
	}
}
