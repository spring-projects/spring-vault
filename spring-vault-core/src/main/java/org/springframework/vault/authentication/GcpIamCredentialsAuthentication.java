/*
 * Copyright 2021-2022 the original author or authors.
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

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.api.client.http.HttpTransport;
import com.google.api.gax.grpc.InstantiatingGrpcChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.iam.credentials.v1.IamCredentialsClient;
import com.google.cloud.iam.credentials.v1.IamCredentialsSettings;
import com.google.cloud.iam.credentials.v1.ServiceAccountName;
import com.google.cloud.iam.credentials.v1.SignJwtResponse;
import com.google.cloud.iam.credentials.v1.stub.IamCredentialsStubSettings;

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestOperations;

/**
 * Google Cloud IAM credentials login implementation using GCP IAM service accounts to
 * legitimate its authenticity via JSON Web Token using the IAM Credentials
 * {@code projects.serviceAccounts.signJwt} method.
 * <p/>
 * This authentication method uses Googles IAM Credentials API to obtain a signed token
 * for a specific {@link com.google.api.client.auth.oauth2.Credential}. Service account
 * details are obtained from a {@link GoogleCredentials} that can be retrieved either from
 * a JSON file or the runtime environment (GAE, GCE).
 * <p/>
 * {@link GcpIamCredentialsAuthentication} uses Google Java API that uses synchronous API.
 *
 * @author Andreas Gebauer
 * @author Mark Paluch
 * @since 2.3.2
 * @see GcpIamCredentialsAuthenticationOptions
 * @see HttpTransport
 * @see GoogleCredentials
 * @see GoogleCredentials#getApplicationDefault()
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/gcp.html">Auth Backend: gcp
 * (IAM)</a>
 * @see <a href=
 * "https://cloud.google.com/iam/docs/reference/credentials/rest/v1/projects.serviceAccounts/signJwt">GCP:
 * projects.serviceAccounts.signJwt</a>
 */
public class GcpIamCredentialsAuthentication extends GcpJwtAuthenticationSupport implements ClientAuthentication {

	private final GcpIamCredentialsAuthenticationOptions options;

	private final TransportChannelProvider transportChannelProvider;

	private final GoogleCredentials credentials;

	/**
	 * Create a new instance of {@link GcpIamCredentialsAuthentication} given
	 * {@link GcpIamCredentialsAuthenticationOptions} and {@link RestOperations}. This
	 * constructor initializes {@link InstantiatingGrpcChannelProvider} for Google API
	 * usage.
	 * @param options must not be {@literal null}.
	 * @param restOperations HTTP client for Vault login, must not be {@literal null}.
	 */
	public GcpIamCredentialsAuthentication(GcpIamCredentialsAuthenticationOptions options,
			RestOperations restOperations) {
		this(options, restOperations, IamCredentialsStubSettings.defaultGrpcTransportProviderBuilder().build());
	}

	/**
	 * Create a new instance of {@link GcpIamCredentialsAuthentication} given
	 * {@link GcpIamCredentialsAuthenticationOptions}, {@link RestOperations} and
	 * {@link TransportChannelProvider}.
	 * @param options must not be {@literal null}.
	 * @param restOperations HTTP client for Vault login, must not be {@literal null}.
	 * @param transportChannelProvider Provider for transport channel Google API use, must
	 * not be {@literal null}.
	 */
	public GcpIamCredentialsAuthentication(GcpIamCredentialsAuthenticationOptions options,
			RestOperations restOperations, TransportChannelProvider transportChannelProvider) {

		super(restOperations);

		Assert.notNull(options, "GcpAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");
		Assert.notNull(transportChannelProvider, "TransportChannelProvider must not be null");

		this.options = options;
		this.transportChannelProvider = transportChannelProvider;
		this.credentials = options.getCredentialSupplier().get();
	}

	@Override
	public VaultToken login() throws VaultException {

		String signedJwt = signJwt();

		return doLogin("GCP-IAM", signedJwt, this.options.getPath(), this.options.getRole());
	}

	protected String signJwt() {

		String serviceAccount = getServiceAccountId();
		Map<String, Object> jwtPayload = getJwtPayload(this.options, serviceAccount);

		try {
			IamCredentialsSettings credentialsSettings = IamCredentialsSettings.newBuilder()
				.setCredentialsProvider(() -> this.credentials)
				.setTransportChannelProvider(this.transportChannelProvider)
				.build();
			try (IamCredentialsClient iamCredentialsClient = IamCredentialsClient.create(credentialsSettings)) {
				String payload = GoogleJsonUtil.JSON_FACTORY.toString(jwtPayload);
				ServiceAccountName serviceAccountName = ServiceAccountName.of("-", serviceAccount);
				SignJwtResponse response = iamCredentialsClient.signJwt(serviceAccountName, Collections.emptyList(),
						payload);
				return response.getSignedJwt();
			}
		}
		catch (IOException e) {
			throw new VaultLoginException("Cannot sign JWT", e);
		}
	}

	private String getServiceAccountId() {
		return this.options.getServiceAccountIdAccessor().getServiceAccountId(this.credentials);
	}

	private static Map<String, Object> getJwtPayload(GcpIamCredentialsAuthenticationOptions options,
			String serviceAccount) {

		Instant validUntil = options.getClock().instant().plus(options.getJwtValidity());

		Map<String, Object> payload = new LinkedHashMap<>();

		payload.put("sub", serviceAccount);
		payload.put("aud", "vault/" + options.getRole());
		payload.put("exp", validUntil.getEpochSecond());

		return payload;
	}

}
