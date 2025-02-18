/*
 * Copyright 2016-2025 the original author or authors.
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
package org.springframework.vault.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory.HttpComponents;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory.OkHttp3;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.util.Settings;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.AssertionsForClassTypes.*;

/**
 * Integration tests for {@link ClientHttpRequestFactory}.
 *
 * @author Mark Paluch
 * @author Ryan Gow
 */
class ClientHttpRequestFactoryFactoryIntegrationTests {

	String url = new VaultEndpoint().createUriString("sys/health");

	@Test
	void httpComponentsClientShouldWork() throws Exception {

		ClientHttpRequestFactory factory = HttpComponents.usingHttpComponents(new ClientOptions(),
				Settings.createSslConfiguration());
		RestTemplate template = new RestTemplate(factory);

		String response = request(template);

		assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
		assertThat(response).isNotNull().contains("initialized");

		((DisposableBean) factory).destroy();
	}

	@Test
	void httpComponentsClientUsingPemShouldWork() throws Exception {

		File caCertificate = new File(Settings.findWorkDir(), "ca/certs/ca.cert.pem");
		SslConfiguration sslConfiguration = SslConfiguration
			.forTrustStore(SslConfiguration.KeyStoreConfiguration.of(new FileSystemResource(caCertificate))
				.withStoreType(SslConfiguration.PEM_KEYSTORE_TYPE));

		ClientHttpRequestFactory factory = HttpComponents.usingHttpComponents(new ClientOptions(), sslConfiguration);
		RestTemplate template = new RestTemplate(factory);

		String response = request(template);

		assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
		assertThat(response).isNotNull().contains("initialized");

		((DisposableBean) factory).destroy();
	}

	@Test
	void httpComponentsClientWithExplicitEnabledCipherSuitesShouldWork() throws Exception {

		List<String> enabledCipherSuites = new ArrayList<String>();
		enabledCipherSuites.add("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384");
		enabledCipherSuites.add("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

		ClientHttpRequestFactory factory = HttpComponents.usingHttpComponents(new ClientOptions(),
				Settings.createSslConfiguration().withEnabledCipherSuites(enabledCipherSuites));
		RestTemplate template = new RestTemplate(factory);

		String response = request(template);

		assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
		assertThat(response).isNotNull().contains("initialized");

		((DisposableBean) factory).destroy();
	}

	@Test
	void httpComponentsClientWithExplicitEnabledProtocolsShouldWork() throws Exception {

		List<String> enabledProtocols = new ArrayList<String>();
		enabledProtocols.add("TLSv1.2");

		ClientHttpRequestFactory factory = HttpComponents.usingHttpComponents(new ClientOptions(),
				Settings.createSslConfiguration().withEnabledProtocols(enabledProtocols));
		RestTemplate template = new RestTemplate(factory);

		String response = request(template);

		assertThat(factory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
		assertThat(response).isNotNull().contains("initialized");

		((DisposableBean) factory).destroy();
	}

	@Test
	void okHttp3ClientShouldWork() throws Exception {

		ClientHttpRequestFactory factory = OkHttp3.usingOkHttp3(new ClientOptions(), Settings.createSslConfiguration());
		RestTemplate template = new RestTemplate(factory);

		String response = request(template);

		assertThat(factory).isInstanceOf(OkHttp3ClientHttpRequestFactory.class);
		assertThat(response).isNotNull().contains("initialized");

		((DisposableBean) factory).destroy();
	}

	@Test
	void okHttp3ClientWithExplicitCipherSuitesShouldWork() throws Exception {

		List<String> enabledCipherSuites = new ArrayList<String>();
		enabledCipherSuites.add("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384");
		enabledCipherSuites.add("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");

		ClientHttpRequestFactory factory = OkHttp3.usingOkHttp3(new ClientOptions(),
				Settings.createSslConfiguration().withEnabledCipherSuites(enabledCipherSuites));
		RestTemplate template = new RestTemplate(factory);

		String response = request(template);

		assertThat(factory).isInstanceOf(OkHttp3ClientHttpRequestFactory.class);
		assertThat(response).isNotNull().contains("initialized");

		((DisposableBean) factory).destroy();
	}

	@Test
	void okHttp3ClientWithExplicitProtocolsShouldWork() throws Exception {

		List<String> enabledProtocols = new ArrayList<String>();
		enabledProtocols.add("TLSv1.2");

		ClientHttpRequestFactory factory = OkHttp3.usingOkHttp3(new ClientOptions(),
				Settings.createSslConfiguration().withEnabledProtocols(enabledProtocols));
		RestTemplate template = new RestTemplate(factory);

		String response = request(template);

		assertThat(factory).isInstanceOf(OkHttp3ClientHttpRequestFactory.class);
		assertThat(response).isNotNull().contains("initialized");

		((DisposableBean) factory).destroy();
	}

	private String request(RestTemplate template) {

		// Uninitialized and sealed can cause status 500
		try {
			ResponseEntity<String> responseEntity = template.exchange(this.url, HttpMethod.GET, null, String.class);
			return responseEntity.getBody();
		}
		catch (HttpStatusCodeException e) {
			return e.getResponseBodyAsString();
		}
	}

}
