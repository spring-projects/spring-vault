/*
 * Copyright 2016-2017 the original author or authors.
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

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;

import lombok.EqualsAndHashCode;

import org.springframework.util.Assert;

/**
 * Value object that defines Vault connection coordinates.
 * <p>
 * A {@link VaultEndpoint} defines the hostname, TCP port and the protocol scheme (HTTP or
 * HTTPS).
 *
 * @author Mark Paluch
 */
@EqualsAndHashCode
public class VaultEndpoint implements Serializable {

	public static final String API_VERSION = "v1";

	/**
	 * Vault server host.
	 */
	private String host = "localhost";

	/**
	 * Vault server port.
	 */
	private int port = 8200;

	/**
	 * Protocol scheme. Can be either "http" or "https".
	 */
	private String scheme = "https";

	/**
	 * Create a secure {@link VaultEndpoint} given a {@code host} and {@code port} using
	 * {@code https}.
	 *
	 * @param host must not be empty or {@literal null}.
	 * @param port must be a valid port in the range of 1-65535
	 * @return a new {@link VaultEndpoint}.
	 */
	public static VaultEndpoint create(String host, int port) {

		Assert.hasText(host, "Host must not be empty");

		VaultEndpoint vaultEndpoint = new VaultEndpoint();

		vaultEndpoint.setHost(host);
		vaultEndpoint.setPort(port);

		return vaultEndpoint;
	}

	/**
	 * Create a {@link VaultEndpoint} given a {@link URI}.
	 *
	 * @param uri must contain hostname, port and scheme, must not be empty or
	 * {@literal null}.
	 * @return a new {@link VaultEndpoint}.
	 */
	public static VaultEndpoint from(URI uri) {

		Assert.notNull(uri, "URI must not be null");
		Assert.hasText(uri.getScheme(), "Scheme must not be empty");
		Assert.hasText(uri.getHost(), "Host must not be empty");

		VaultEndpoint vaultEndpoint = new VaultEndpoint();

		vaultEndpoint.setHost(uri.getHost());
		try {
			vaultEndpoint.setPort(uri.getPort() == -1 ? uri.toURL().getDefaultPort()
					: uri.getPort());
		}
		catch (MalformedURLException e) {
			throw new IllegalArgumentException(String.format(
					"Can't retrieve default port from %s", uri), e);
		}
		vaultEndpoint.setScheme(uri.getScheme());

		return vaultEndpoint;
	}

	/**
	 * @return the hostname.
	 */
	public String getHost() {
		return host;
	}

	/**
	 * Sets the hostname.
	 *
	 * @param host must not be empty or {@literal null}.
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * @return the port.
	 */
	public int getPort() {
		return port;
	}

	/**
	 * @param port must be a valid port in the range of 1-65535
	 */
	public void setPort(int port) {

		Assert.isTrue(port >= 1 && port <= 65535,
				"Port must be a valid port in the range between 1 and 65535");

		this.port = port;
	}

	/**
	 * @return the protocol scheme.
	 */
	public String getScheme() {
		return scheme;
	}

	/**
	 * @param scheme must be {@literal http} or {@literal https}.
	 */
	public void setScheme(String scheme) {

		Assert.isTrue("http".equals(scheme) || "https".equals(scheme),
				"Scheme must be http or https");

		this.scheme = scheme;
	}

	/**
	 * Build the Vault {@link URI} based on the given {@code path}.
	 *
	 * @param path must not be empty or {@literal null}.
	 * @return constructed {@link URI}.
	 */
	public URI createUri(String path) {
		return URI.create(createUriString(path));
	}

	/**
	 * Build the Vault URI string based on the given {@code path}.
	 *
	 * @param path must not be empty or {@literal null}.
	 * @return constructed URI String.
	 */
	public String createUriString(String path) {

		Assert.hasText(path, "Path must not be empty");

		return String.format("%s://%s:%s/%s/%s", getScheme(), getHost(), getPort(),
				API_VERSION, path);
	}

	@Override
	public String toString() {
		return String.format("%s://%s:%d", scheme, host, port);
	}
}
