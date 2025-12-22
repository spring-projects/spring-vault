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

import java.net.URI;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Authentication options for {@link AzureMsiAuthentication}.
 * <p>Authentication options provide the path, role, an optional
 * {@link AzureVmEnvironment}, and instance metadata/OAuth2 token URIs.
 * {@link AzureMsiAuthenticationOptions} can be constructed using
 * {@link #builder()}. Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @since 2.1
 * @see AzureMsiAuthenticationOptions
 * @see #builder()
 */
public class AzureMsiAuthenticationOptions {

	public static final String DEFAULT_AZURE_AUTHENTICATION_PATH = "azure";

	public static final URI DEFAULT_INSTANCE_METADATA_SERVICE_URI = URI
			.create("http://169.254.169.254/metadata/instance?api-version=2017-12-01");

	public static final URI DEFAULT_IDENTITY_TOKEN_SERVICE_URI = URI.create(
			"http://169.254.169.254/metadata/identity/oauth2/token?resource=https://vault.hashicorp.com&api-version=2018-02-01");


	/**
	 * Path of the azure authentication backend mount.
	 */
	private final String path;

	/**
	 * Name of the role against which the login is being attempted.
	 */
	private final String role;

	/*
	 * {@link URI} to the instance metadata endpoint.
	 */
	private final URI instanceMetadataServiceUri;

	/*
	 * {@link URI} to the token service for the managed identity.
	 */
	private final URI identityTokenServiceUri;

	/**
	 * Optional {@link AzureVmEnvironment}.
	 */
	@Nullable
	private final AzureVmEnvironment vmEnvironment;


	private AzureMsiAuthenticationOptions(String path, String role, URI instanceMetadataServiceUri,
			URI identityTokenServiceUri, @Nullable AzureVmEnvironment vmEnvironment) {
		this.path = path;
		this.role = role;
		this.instanceMetadataServiceUri = instanceMetadataServiceUri;
		this.identityTokenServiceUri = identityTokenServiceUri;
		this.vmEnvironment = vmEnvironment;
	}


	/**
	 * @return a new {@link AzureMsiAuthenticationOptionsBuilder}.
	 */
	public static AzureMsiAuthenticationOptionsBuilder builder() {
		return new AzureMsiAuthenticationOptionsBuilder();
	}


	/**
	 * @return the path of the azure authentication backend mount.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return the role against which the login is being attempted.
	 */
	public String getRole() {
		return this.role;
	}

	/**
	 * @return the {@link AzureVmEnvironment}. If {@literal null}, the environment
	 * is retrieved from the {@link #getInstanceMetadataServiceUri() VM instance
	 * metadata service}.
	 */
	@Nullable
	public AzureVmEnvironment getVmEnvironment() {
		return this.vmEnvironment;
	}

	/**
	 * @return {@link URI} to the instance metadata endpoint.
	 */
	public URI getInstanceMetadataServiceUri() {
		return this.instanceMetadataServiceUri;
	}

	/**
	 * @return {@link URI} to the token service for the managed identity.
	 */
	public URI getIdentityTokenServiceUri() {
		return this.identityTokenServiceUri;
	}


	/**
	 * Builder for {@link AzureMsiAuthenticationOptions}.
	 */
	public static class AzureMsiAuthenticationOptionsBuilder {

		private String path = DEFAULT_AZURE_AUTHENTICATION_PATH;

		@Nullable
		private String role;

		@Nullable
		private AzureVmEnvironment vmEnvironment;

		private URI instanceMetadataServiceUri = DEFAULT_INSTANCE_METADATA_SERVICE_URI;

		private URI identityTokenServiceUri = DEFAULT_IDENTITY_TOKEN_SERVICE_URI;


		AzureMsiAuthenticationOptionsBuilder() {
		}


		/**
		 * Configure the mount path, defaults to {@literal azure}.
		 * @param path must not be empty or {@literal null}.
		 * @return this builder.
		 */
		public AzureMsiAuthenticationOptionsBuilder path(String path) {
			Assert.hasText(path, "Path must not be empty");
			this.path = path;
			return this;
		}

		/**
		 * Configure the name of the role against which the login is being attempted.
		 * @param role must not be empty or {@literal null}.
		 * @return this builder.
		 */
		public AzureMsiAuthenticationOptionsBuilder role(String role) {
			Assert.hasText(role, "Role must not be null or empty");
			this.role = role;
			return this;
		}

		/**
		 * Configure a VM environment (subscriptionId, resource group name, VM name).
		 * Environment details are passed to Vault as login body. If left unconfigured,
		 * {@link AzureMsiAuthentication} looks up the details from the instance
		 * metadata service.
		 * @param vmEnvironment must not be {@literal null}.
		 * @return this builder.
		 */
		public AzureMsiAuthenticationOptionsBuilder vmEnvironment(AzureVmEnvironment vmEnvironment) {
			Assert.notNull(vmEnvironment, "AzureVmEnvironment must not be null");
			this.vmEnvironment = vmEnvironment;
			return this;
		}

		/**
		 * Configure the instance metadata {@link URI}.
		 * @param instanceMetadataServiceUri must not be {@literal null}.
		 * @return this builder.
		 * @see #DEFAULT_IDENTITY_TOKEN_SERVICE_URI
		 */
		public AzureMsiAuthenticationOptionsBuilder instanceMetadataUri(URI instanceMetadataServiceUri) {
			Assert.notNull(instanceMetadataServiceUri, "Instance metadata service URI must not be null");
			this.instanceMetadataServiceUri = instanceMetadataServiceUri;
			return this;
		}

		/**
		 * Configure the managed identity service token {@link URI}.
		 * @param identityTokenServiceUri must not be {@literal null}.
		 * @return this builder.
		 * @see #DEFAULT_IDENTITY_TOKEN_SERVICE_URI
		 */
		public AzureMsiAuthenticationOptionsBuilder identityTokenServiceUri(URI identityTokenServiceUri) {
			Assert.notNull(identityTokenServiceUri, "Identity token service URI must not be null");
			this.identityTokenServiceUri = identityTokenServiceUri;
			return this;
		}

		/**
		 * Build a new {@link AzureMsiAuthenticationOptions} instance.
		 * @return a new {@link AzureMsiAuthenticationOptions}.
		 */
		public AzureMsiAuthenticationOptions build() {
			Assert.hasText(this.role, "Role must not be null or empty");
			return new AzureMsiAuthenticationOptions(this.path, this.role, this.instanceMetadataServiceUri,
					this.identityTokenServiceUri, this.vmEnvironment);
		}

	}

}
