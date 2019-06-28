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
package org.springframework.vault.config;

import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;

/**
 * Factory for {@link ClientHttpConnector} that supports
 * {@link ReactorClientHttpConnector}.
 *
 * @author Mark Paluch
 * @since 2.0
 * @deprecated since 2.2, use
 * {@link org.springframework.vault.client.ClientHttpConnectorFactory} as the
 * functionality was moved to the {@code org.springframework.vault.client} package.
 */
@Deprecated
public class ClientHttpConnectorFactory {

	/**
	 * Create a {@link ClientHttpConnector} for the given {@link ClientOptions} and
	 * {@link SslConfiguration}.
	 *
	 * @param options must not be {@literal null}
	 * @param sslConfiguration must not be {@literal null}
	 * @return a new {@link ClientHttpConnector}.
	 */
	public static ClientHttpConnector create(ClientOptions options,
			SslConfiguration sslConfiguration) {
		return org.springframework.vault.client.ClientHttpConnectorFactory.create(
				options, sslConfiguration);
	}
}
