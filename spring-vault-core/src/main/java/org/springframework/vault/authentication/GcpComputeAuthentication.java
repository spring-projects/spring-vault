/*
 * Copyright 2018-2019 the original author or authors.
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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequest;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

import static org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder.get;

/**
 * GCP GCE (Google Compute Engine)-based login implementation using GCE's metadata service
 * to create signed JSON Web Token.
 * <p/>
 * This authentication method uses Googles GCE's metadata service in combination with the
 * default/specified service account to obtain an identity document as JWT using a HTTP
 * client. Credentials and authenticity are implied from the runtime itself and are not
 * required to be configured.
 *
 * @author Mark Paluch
 * @since 2.1
 * @see GcpComputeAuthenticationOptions
 * @see <a href="https://www.vaultproject.io/docs/auth/gcp.html">Auth Backend: gcp
 * (IAM)</a>
 * @see <a href=
 * "https://cloud.google.com/compute/docs/instances/verifying-instance-identity">Google
 * Compute Engine: Verifying the Identity of Instances</a>
 */
public class GcpComputeAuthentication extends GcpJwtAuthenticationSupport
		implements ClientAuthentication, AuthenticationStepsFactory {

	public static final String COMPUTE_METADATA_URL_TEMPLATE = "http://metadata/computeMetadata/v1/instance/service-accounts/{serviceAccount}/identity"
			+ "?audience={audience}&format={format}";

	private final GcpComputeAuthenticationOptions options;

	private final RestOperations googleMetadataRestOperations;

	/**
	 * Create a new {@link GcpComputeAuthentication} instance given
	 * {@link GcpComputeAuthenticationOptions} and {@link RestOperations} for Vault and
	 * Google API use.
	 *
	 * @param options must not be {@literal null}.
	 * @param vaultRestOperations must not be {@literal null}.
	 */
	public GcpComputeAuthentication(GcpComputeAuthenticationOptions options,
			RestOperations vaultRestOperations) {
		this(options, vaultRestOperations, vaultRestOperations);
	}

	/**
	 * Create a new {@link GcpComputeAuthentication} instance given
	 * {@link GcpComputeAuthenticationOptions} and {@link RestOperations} for Vault and
	 * Google API use.
	 *
	 * @param options must not be {@literal null}.
	 * @param vaultRestOperations must not be {@literal null}.
	 * @param googleMetadataRestOperations must not be {@literal null}.
	 */
	public GcpComputeAuthentication(GcpComputeAuthenticationOptions options,
			RestOperations vaultRestOperations,
			RestOperations googleMetadataRestOperations) {

		super(vaultRestOperations);

		Assert.notNull(options, "GcpGceAuthenticationOptions must not be null");
		Assert.notNull(googleMetadataRestOperations,
				"Google Metadata RestOperations must not be null");

		this.options = options;
		this.googleMetadataRestOperations = googleMetadataRestOperations;
	}

	/**
	 * Creates a {@link AuthenticationSteps} for GCE authentication given
	 * {@link GcpComputeAuthenticationOptions}.
	 *
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for cubbyhole authentication.
	 */
	public static AuthenticationSteps createAuthenticationSteps(
			GcpComputeAuthenticationOptions options) {

		Assert.notNull(options, "CubbyholeAuthenticationOptions must not be null");

		String serviceAccount = options.getServiceAccount();
		String audience = getAudience(options.getRole());

		HttpRequest<String> jwtRequest = get(COMPUTE_METADATA_URL_TEMPLATE,
				serviceAccount, audience, "full") //
						.with(getMetadataHttpHeaders()) //
						.as(String.class);

		return AuthenticationSteps.fromHttpRequest(jwtRequest)
				//
				.map(jwt -> createRequestBody(options.getRole(), jwt))
				.login("auth/{mount}/login", options.getPath());
	}

	@Override
	public VaultToken login() throws VaultException {

		String signedJwt = signJwt();

		return doLogin("GCP-GCE", signedJwt, this.options.getPath(),
				this.options.getRole());
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(options);
	}

	protected String signJwt() {

		try {
			Map<String, String> urlParameters = new LinkedHashMap<>();
			urlParameters.put("serviceAccount", this.options.getServiceAccount());
			urlParameters.put("audience", getAudience(this.options.getRole()));
			urlParameters.put("format", "full");

			HttpHeaders headers = getMetadataHttpHeaders();
			HttpEntity<Object> entity = new HttpEntity<>(headers);

			ResponseEntity<String> response = googleMetadataRestOperations.exchange(
					COMPUTE_METADATA_URL_TEMPLATE, HttpMethod.GET, entity, String.class,
					urlParameters);

			return response.getBody();
		}
		catch (HttpStatusCodeException e) {
			throw new VaultLoginException("Cannot obtain signed identity", e);
		}
	}

	private static HttpHeaders getMetadataHttpHeaders() {

		HttpHeaders headers = new HttpHeaders();

		headers.set("Metadata-Flavor", "Google");

		return headers;
	}

	private static String getAudience(String role) {
		return String.format("https://localhost:8200/vault/%s", role);
	}
}
