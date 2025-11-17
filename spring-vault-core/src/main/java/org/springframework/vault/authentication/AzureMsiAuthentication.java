/*
 * Copyright 2018-2025 the original author or authors.
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

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder;
import org.springframework.vault.authentication.AuthenticationSteps.Node;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestOperations;

/**
 * Azure MSI (Managed Service Identity) authentication using Azure as trusted
 * third party.
 * <p>Azure MSI authentication uses {@link AzureVmEnvironment} and the MSI
 * OAuth2 token (referenced as JWT token in Vault docs) to log into Vault. VM
 * environment and OAuth2 token are fetched from the Azure Instance Metadata
 * service. Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @since 2.1
 * @see AzureMsiAuthenticationOptions
 * @see VaultClient
 * @see <a href="https://www.vaultproject.io/docs/auth/azure.html">Auth Backend:
 * azure</a>
 * @link <a href=
 * "https://docs.microsoft.com/en-us/azure/virtual-machines/windows/instance-metadata-service"
 * >Azure Instance Metadata service</a>
 */
public class AzureMsiAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private static final HttpEntity<Void> METADATA_HEADERS;

	static {

		HttpHeaders headers = new HttpHeaders();
		headers.add("Metadata", "true");
		METADATA_HEADERS = new HttpEntity<>(headers);
	}


	private final AzureMsiAuthenticationOptions options;

	private final VaultLoginClient loginClient;

	private final ClientAdapter azureMetadataAdapter;


	/**
	 * Create a new {@link AzureMsiAuthentication}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 * @deprecated since 4.1, use
	 * {@link #AzureMsiAuthentication(AzureMsiAuthenticationOptions, VaultClient, RestClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public AzureMsiAuthentication(AzureMsiAuthenticationOptions options, RestOperations restOperations) {
		this(options, restOperations, restOperations);
	}

	/**
	 * Create a new {@link AzureMsiAuthentication} specifying
	 * {@link AzureMsiAuthenticationOptions}, a Vault and an Azure-Metadata-specific
	 * {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param vaultRestOperations must not be {@literal null}.
	 * @param azureMetadataRestOperations must not be {@literal null}.
	 * @deprecated since 4.1, use
	 * {@link #AzureMsiAuthentication(AzureMsiAuthenticationOptions, VaultClient, RestClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public AzureMsiAuthentication(AzureMsiAuthenticationOptions options, RestOperations vaultRestOperations,
			RestOperations azureMetadataRestOperations) {
		this(options, ClientAdapter.from(vaultRestOperations).vaultClient(),
				ClientAdapter.from(azureMetadataRestOperations));
	}

	/**
	 * Create a new {@link AzureMsiAuthentication}.
	 * @param options must not be {@literal null}.
	 * @param client must not be {@literal null}.
	 * @since 4.0
	 * @deprecated since 4.1, use
	 * {@link #AzureMsiAuthentication(AzureMsiAuthenticationOptions, VaultClient, RestClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public AzureMsiAuthentication(AzureMsiAuthenticationOptions options, RestClient client) {
		this(options, client, client);
	}

	/**
	 * Create a new {@link AzureMsiAuthentication} specifying
	 * {@link AzureMsiAuthenticationOptions}, a Vault and an Azure-Metadata-specific
	 * {@link RestClient}.
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 * @param azureMetadataClient must not be {@literal null}.
	 * @since 4.0
	 * @deprecated since 4.1, use
	 * {@link #AzureMsiAuthentication(AzureMsiAuthenticationOptions, VaultClient, RestClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public AzureMsiAuthentication(AzureMsiAuthenticationOptions options, RestClient vaultClient,
			RestClient azureMetadataClient) {
		this(options, ClientAdapter.from(vaultClient).vaultClient(), ClientAdapter.from(azureMetadataClient));
	}

	/**
	 * Create a new {@link AzureMsiAuthentication} specifying
	 * {@link AzureMsiAuthenticationOptions} and {@link VaultClient}.
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 * @since 4.1
	 */
	public AzureMsiAuthentication(AzureMsiAuthenticationOptions options, VaultClient vaultClient) {
		this(options, vaultClient, ClientAdapter.from(RestClient.create()));
	}

	/**
	 * Create a new {@link AzureMsiAuthentication} specifying
	 * {@link AzureMsiAuthenticationOptions}, {@link VaultClient} and an
	 * Azure-Metadata-specific {@link RestClient}.
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 * @param azureMetadataClient must not be {@literal null}.
	 * @since 4.1
	 */
	public AzureMsiAuthentication(AzureMsiAuthenticationOptions options, VaultClient vaultClient,
			RestClient azureMetadataClient) {
		this(options, vaultClient, ClientAdapter.from(azureMetadataClient));
	}

	AzureMsiAuthentication(AzureMsiAuthenticationOptions options, VaultClient vaultClient,
			ClientAdapter azureMetadataClient) {

		Assert.notNull(options, "AzureAuthenticationOptions must not be null");
		Assert.notNull(vaultClient, "Vault RestOperations must not be null");
		Assert.notNull(azureMetadataClient, "Azure Instance Metadata RestOperations must not be null");
		this.options = options;
		this.loginClient = VaultLoginClient.create(vaultClient, "Azure");
		this.azureMetadataAdapter = azureMetadataClient;
	}

	/**
	 * Create {@link AuthenticationSteps} for Azure authentication given
	 * {@link AzureMsiAuthenticationOptions}.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for Azure authentication.
	 */
	public static AuthenticationSteps createAuthenticationSteps(AzureMsiAuthenticationOptions options) {
		Assert.notNull(options, "AzureMsiAuthenticationOptions must not be null");
		return createAuthenticationSteps(options, options.getVmEnvironment());
	}

	protected static AuthenticationSteps createAuthenticationSteps(AzureMsiAuthenticationOptions options,
			@Nullable AzureVmEnvironment environment) {
		Node<String> msiToken = AuthenticationSteps
				.fromHttpRequest(
						HttpRequestBuilder.get(options.getIdentityTokenServiceUri()).with(METADATA_HEADERS)
								.as(Map.class)) //
				.map(token -> (String) token.get("access_token"));

		Node<AzureVmEnvironment> environmentSteps;
		if (environment == null) {
			environmentSteps = AuthenticationSteps
					.fromHttpRequest(HttpRequestBuilder.get(options.getInstanceMetadataServiceUri())
							.with(METADATA_HEADERS)
							.as(Map.class)) //
					.map(AzureMsiAuthentication::toAzureVmEnvironment);
		} else {
			environmentSteps = AuthenticationSteps.fromValue(environment);
		}
		return environmentSteps.zipWith(msiToken)
				.map(tuple -> getAzureLogin(options.getRole(), tuple.getLeft(), tuple.getRight())) //
				.loginAt(options.getPath());
	}

	@Override
	public VaultToken login() throws VaultException {
		return createTokenUsingAzureMsiCompute();
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(this.options);
	}

	private VaultToken createTokenUsingAzureMsiCompute() {
		Map<String, String> login = getAzureLogin(this.options.getRole(), getVmEnvironment(), getAccessToken());
		return this.loginClient.loginAt(this.options.getPath()).using(login).retrieve().loginToken();
	}

	private static Map<String, String> getAzureLogin(String role, AzureVmEnvironment vmEnvironment, String jwt) {
		Map<String, String> loginBody = new LinkedHashMap<>();
		loginBody.put("role", role);
		loginBody.put("jwt", jwt);
		loginBody.put("subscription_id", vmEnvironment.getSubscriptionId());
		loginBody.put("resource_group_name", vmEnvironment.getResourceGroupName());
		loginBody.put("vm_name", vmEnvironment.getVmName());
		loginBody.put("vmss_name", vmEnvironment.getVmScaleSetName());
		return loginBody;
	}

	@SuppressWarnings({"NullAway", "rawtypes"})
	private String getAccessToken() {
		ResponseEntity<Map> response = this.azureMetadataAdapter.exchange(this.options.getIdentityTokenServiceUri(),
				HttpMethod.GET, METADATA_HEADERS, Map.class);
		return (String) AuthenticationUtil.getRequiredBody(response).get("access_token");
	}

	private AzureVmEnvironment getVmEnvironment() {
		AzureVmEnvironment vmEnvironment = this.options.getVmEnvironment();
		return vmEnvironment != null ? vmEnvironment : fetchAzureVmEnvironment();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private AzureVmEnvironment fetchAzureVmEnvironment() {
		ResponseEntity<Map> response = this.azureMetadataAdapter.exchange(this.options.getInstanceMetadataServiceUri(),
				HttpMethod.GET, METADATA_HEADERS, Map.class);
		return toAzureVmEnvironment(AuthenticationUtil.getRequiredBody(response));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static AzureVmEnvironment toAzureVmEnvironment(Map<String, Object> instanceMetadata) {
		Map<String, String> compute = (Map) instanceMetadata.get("compute");
		Assert.notNull(compute, "Metadata does not contain compute");
		String subscriptionId = compute.get("subscriptionId");
		String resourceGroupName = compute.get("resourceGroupName");
		String vmName = compute.get("name");
		String vmScaleSetName = compute.get("vmScaleSetName");
		Assert.notNull(subscriptionId, "Metadata does not contain subscriptionId");
		Assert.notNull(resourceGroupName, "Metadata does not contain resourceGroupName");
		Assert.notNull(vmName, "Metadata does not contain name");
		Assert.notNull(vmScaleSetName, "Metadata does not contain vmScaleSetName");
		return new AzureVmEnvironment(subscriptionId, resourceGroupName, vmName, vmScaleSetName);
	}

}
