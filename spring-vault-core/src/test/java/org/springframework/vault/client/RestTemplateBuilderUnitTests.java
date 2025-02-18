/*
 * Copyright 2019-2025 the original author or authors.
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

import java.io.IOException;
import java.net.URI;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link RestTemplateBuilder}.
 *
 * @author Mark Paluch
 */
class RestTemplateBuilderUnitTests {

	@Test
	void shouldApplyErrorHandler() {

		ResponseErrorHandler errorHandler = new DefaultResponseErrorHandler();

		RestTemplate restTemplate = RestTemplateBuilder.builder()
			.endpoint(VaultEndpoint.create("localhost", 8200))
			.errorHandler(errorHandler)
			.build();

		assertThat(restTemplate.getErrorHandler()).isSameAs(errorHandler);
	}

	@Test
	void shouldApplyErrorHandlerViaCustomizer() {

		ResponseErrorHandler errorHandler = new DefaultResponseErrorHandler();

		RestTemplate restTemplate = RestTemplateBuilder.builder()
			.endpoint(VaultEndpoint.create("localhost", 8200))
			.customizers(it -> it.setErrorHandler(errorHandler))
			.build();

		assertThat(restTemplate.getErrorHandler()).isSameAs(errorHandler);
	}

	@Test
	void shouldApplyRequestCustomizers() throws IOException {

		RestTemplate restTemplate = RestTemplateBuilder.builder()
			.endpoint(VaultEndpoint.create("localhost", 8200))
			.requestCustomizers(request -> request.getHeaders().add("header", "value"))
			.build();

		restTemplate.getInterceptors().clear();

		ClientHttpRequest request = restTemplate.getRequestFactory().createRequest(URI.create("/"), HttpMethod.GET);

		assertThat(request.getHeaders()).containsEntry("header", Collections.singletonList("value"));
	}

}
