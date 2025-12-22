/*
 * Copyright 2017-2025 the original author or authors.
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

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.*;
import org.springframework.vault.authentication.AppIdAuthenticationOptions.AppIdAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.AppRoleAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.RoleId;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions.SecretId;
import org.springframework.vault.authentication.AwsEc2AuthenticationOptions.AwsEc2AuthenticationOptionsBuilder;
import org.springframework.vault.authentication.AzureMsiAuthenticationOptions.AzureMsiAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions.CubbyholeAuthenticationOptionsBuilder;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions.KubernetesAuthenticationOptionsBuilder;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestOperations;

/**
 * Configuration using Spring's {@link org.springframework.core.env.Environment}
 * to configure Spring Vault endpoint, SSL options and authentication options.
 * This configuration class uses predefined property keys and is usually
 * imported as part of an existing Java-based configuration. Configuration is
 * obtained from other, existing property sources.
 * <p>Usage:
 *
 * Java-based configuration part:
 *
 * <pre>
 *     <code>
 * &#64;Configuration
 * &#64;Import(EnvironmentVaultConfiguration.class)
 * public class MyConfiguration {
 * }
 *     </code> </pre>
 *
 * Supplied properties:
 *
 * <pre>
 *     <code>
 * vault.uri=https://localhost:8200
 * vault.token=00000000-0000-0000-0000-000000000000
 *     </code> </pre>
 *
 * <h3>Property keys</h3>
 *
 * Authentication-specific properties must be provided depending on the
 * authentication method.
 * <ul>
 * <li>Vault URI: {@code vault.uri}</li>
 * <li>SSL Configuration
 * <ul>
 * <li>Keystore resource: {@code vault.ssl.key-store} (optional)</li>
 * <li>Keystore password: {@code vault.ssl.key-store-password} (optional)</li>
 * <li>Keystore type: {@code vault.ssl.key-store-type} (since 2.3,
 * optional)</li>
 * <li>Truststore resource: {@code vault.ssl.trust-store} (optional)</li>
 * <li>Truststore password: {@code vault.ssl.trust-store-password}
 * (optional)</li>
 * <li>Truststore type: {@code vault.ssl.trust-store-password} (since 2.3,
 * optional)</li>
 * <li>Enabled SSL/TLS protocols: {@code vault.ssl.enabled-protocols} (since
 * 2.3.2, optional, protocols separated with comma)</li>
 * <li>Enabled SSL/TLS cipher suites: {@code vault.ssl.enabled-cipher-suites}
 * (since 2.3.2, optional, cipher suites separated with comma)</li>
 * </ul>
 * </li>
 * <li>Authentication method: {@code vault.authentication} (defaults to
 * {@literal TOKEN}, supported authentication methods are:
 * {@literal TOKEN, APPID, APPROLE, AWS_EC2, AWS_IAM, AZURE, CERT, CUBBYHOLE, KUBERNETES},
 * see {@link AuthenticationMethod})</li>
 * <li>Token authentication
 * <ul>
 * <li>Vault Token: {@code vault.token}</li>
 * </ul>
 * <li>AppId authentication
 * <ul>
 * <li>AppId path: {@code vault.app-id.app-id-path} (since 2.2.1, defaults to
 * {@link AppIdAuthenticationOptions#DEFAULT_APPID_AUTHENTICATION_PATH})</li>
 * <li>AppId: {@code vault.app-id.app-id}</li>
 * <li>UserId: {@code vault.app-id.user-id}. {@literal MAC_ADDRESS} and
 * {@literal IP_ADDRESS} use {@link MacAddressUserId}, respective
 * {@link IpAddressUserId}. Any other value is used with
 * {@link StaticUserId}.</li>
 * </ul>
 * <li>AppRole authentication
 * <ul>
 * <li>AppRole path: {@code vault.app-role.app-role-path} (since 2.2.1, defaults
 * to
 * {@link AppRoleAuthenticationOptions#DEFAULT_APPROLE_AUTHENTICATION_PATH})</li>
 * <li>RoleId: {@code vault.app-role.role-id}</li>
 * <li>SecretId: {@code vault.app-role.secret-id} (optional)</li>
 * </ul>
 * <li>AWS EC2 authentication
 * <ul>
 * <li>AWS EC2 path: {@code vault.aws-ec2.aws-ec2-path} (since 2.2.1, defaults
 * to {@link AwsEc2AuthenticationOptions#DEFAULT_AWS_AUTHENTICATION_PATH})</li>
 * <li>Role: {@code vault.aws-ec2.role} (since 2.2.1)</li>
 * <li>RoleId: {@code vault.aws-ec2.role-id} (<strong>deprecated since
 * 2.2.1:</strong> use {@code vault.aws-ec2.role} instead)</li>
 * <li>Identity Document URL: {@code vault.aws-ec2.identity-document} (defaults
 * to
 * {@link AwsEc2AuthenticationOptions#DEFAULT_PKCS7_IDENTITY_DOCUMENT_URI})</li>
 * </ul>
 * <li>AWS IAM authentication
 * <ul>
 * <li>Role: {@code vault.aws-iam.role} (since 3.0.2)</li>
 * </ul>
 * <li>Azure MSI authentication
 * <ul>
 * <li>Azure MSI path: {@code vault.azure-msi.azure-path} (since 2.2.1, defaults
 * to
 * {@link AzureMsiAuthenticationOptions#DEFAULT_AZURE_AUTHENTICATION_PATH})</li>
 * <li>Role: {@code vault.azure-msi.role}</li>
 * <li>MetadataServiceUri: {@code vault.azure-msi.metadata-service} (defaults to
 * {@link AzureMsiAuthenticationOptions#DEFAULT_INSTANCE_METADATA_SERVICE_URI})</li>
 * <li>IdentityTokenServiceUri: {@code vault.azure-msi.identity-token-service}
 * (defaults to
 * {@link AzureMsiAuthenticationOptions#DEFAULT_IDENTITY_TOKEN_SERVICE_URI})</li>
 * </ul>
 * <li>Client Certificate authentication
 * <ul>
 * <li>(no configuration options)</li>
 * </ul>
 * <li>Cubbyhole authentication
 * <ul>
 * <li>Initial Vault Token: {@code vault.token}</li>
 * </ul>
 * <li>Kubernetes authentication
 * <ul>
 * <li>Kubernetes path: {@code vault.kubernetes.kubernetes-path} (since 2.2.1,
 * defaults to
 * {@link KubernetesAuthenticationOptions#DEFAULT_KUBERNETES_AUTHENTICATION_PATH})</li>
 * <li>Role: {@code vault.kubernetes.role}</li>
 * <li>Path to service account token file:
 * {@code vault.kubernetes.service-account-token-file} (defaults to
 * {@link KubernetesServiceAccountTokenFile#DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE})</li>
 * </ul>
 * </ul>
 *
 * @author Mark Paluch
 * @author Michal Budzyn
 * @author Raoof Mohammed
 * @author Justin Bertrand
 * @author Ryan Gow
 * @author Nick Tan
 * @see org.springframework.core.env.Environment
 * @see org.springframework.core.env.PropertySource
 * @see VaultEndpoint
 * @see AppIdAuthentication
 * @see AppRoleAuthentication
 * @see AwsEc2Authentication
 * @see AwsIamAuthentication
 * @see AzureMsiAuthentication
 * @see ClientCertificateAuthentication
 * @see CubbyholeAuthentication
 * @see KubernetesAuthentication
 */
@Configuration
public class EnvironmentVaultConfiguration extends AbstractVaultConfiguration implements ApplicationContextAware {

	private static final Log logger = LogFactory.getLog(EnvironmentVaultConfiguration.class);


	private @Nullable RestOperations cachedRestOperations;

	private @Nullable ApplicationContext applicationContext;


	@Override
	public RestOperations restOperations() {
		if (this.cachedRestOperations != null) {
			return this.cachedRestOperations;
		}
		this.cachedRestOperations = super.restOperations();
		return this.cachedRestOperations;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
		super.setApplicationContext(applicationContext);
	}

	@Override
	public VaultEndpoint vaultEndpoint() {
		String uri = getProperty("vault.uri");
		if (uri != null) {
			return VaultEndpoint.from(URI.create(uri));
		}
		throw new IllegalStateException("Vault URI (vault.uri) is null");
	}

	@Override
	public SslConfiguration sslConfiguration() {
		KeyStoreConfiguration keyStoreConfiguration = getKeyStoreConfiguration("vault.ssl.key-store",
				"vault.ssl.key-store-password", "vault.ssl.key-store-type");
		KeyStoreConfiguration trustStoreConfiguration = getKeyStoreConfiguration("vault.ssl.trust-store",
				"vault.ssl.trust-store-password", "vault.ssl.trust-store-type");
		List<String> enabledProtocols = getPropertyAsList("vault.ssl.enabled-protocols");
		List<String> enabledCipherSuites = getPropertyAsList("vault.ssl.enabled-cipher-suites");
		return new SslConfiguration(keyStoreConfiguration, trustStoreConfiguration, enabledProtocols,
				enabledCipherSuites);
	}

	private KeyStoreConfiguration getKeyStoreConfiguration(String resourceProperty, String passwordProperty,
			String keystoreTypeProperty) {
		Resource keyStore = getResource(resourceProperty);
		String keyStorePassword = getProperty(passwordProperty);
		String keystoreType = getProperty(keystoreTypeProperty, SslConfiguration.PEM_KEYSTORE_TYPE);
		if (keyStore == null) {
			return KeyStoreConfiguration.unconfigured();
		}
		if (StringUtils.hasText(keyStorePassword)) {
			return KeyStoreConfiguration.of(keyStore, keyStorePassword.toCharArray(), keystoreType);
		}
		return KeyStoreConfiguration.of(keyStore).withStoreType(keystoreType);
	}

	@Override
	public ClientAuthentication clientAuthentication() {
		String authentication = getProperty("vault.authentication", AuthenticationMethod.TOKEN.name()).toUpperCase()
				.replace('-', '_');
		AuthenticationMethod authenticationMethod = AuthenticationMethod.valueOf(authentication);

		return switch (authenticationMethod) {
		case TOKEN -> tokenAuthentication();
		case APPID -> appIdAuthentication();
		case APPROLE -> appRoleAuthentication();
		case AWS_EC2 -> awsEc2Authentication();
		case AWS_IAM -> awsIamAuthentication();
		case AZURE -> azureMsiAuthentication();
		case CERT -> new ClientCertificateAuthentication(restOperations());
		case CUBBYHOLE -> cubbyholeAuthentication();
		case KUBERNETES -> kubeAuthentication();
		default -> throw new IllegalStateException("Vault authentication method %s is not supported with %s"
				.formatted(authenticationMethod, getClass().getSimpleName()));
		};
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	protected ClientAuthentication tokenAuthentication() {
		String token = getProperty("vault.token");
		Assert.hasText(token, "Vault Token authentication: Token (vault.token) must not be empty");
		return new TokenAuthentication(token);
	}

	protected ClientAuthentication appIdAuthentication() {

		String appId = getProperty("vault.app-id.app-id", getProperty("spring.application.name"));
		String userId = getProperty("vault.app-id.user-id");
		String path = getProperty("vault.app-id.app-id-path",
				AppIdAuthenticationOptions.DEFAULT_APPID_AUTHENTICATION_PATH);

		Assert.hasText(appId, "Vault AppId authentication: AppId (vault.app-id.app-id) must not be empty");
		Assert.hasText(userId, "Vault AppId authentication: UserId (vault.app-id.user-id) must not be empty");

		AppIdAuthenticationOptionsBuilder builder = AppIdAuthenticationOptions.builder()
				.appId(appId)
				.userIdMechanism(getAppIdUserIdMechanism(userId))
				.path(path);

		return new AppIdAuthentication(builder.build(), restOperations());
	}

	protected ClientAuthentication appRoleAuthentication() {
		String roleId = getProperty("vault.app-role.role-id");
		String secretId = getProperty("vault.app-role.secret-id");
		String path = getProperty("vault.app-role.app-role-path",
				AppRoleAuthenticationOptions.DEFAULT_APPROLE_AUTHENTICATION_PATH);
		Assert.hasText(roleId, "Vault AppRole authentication: RoleId (vault.app-role.role-id) must not be empty");
		AppRoleAuthenticationOptionsBuilder builder = AppRoleAuthenticationOptions.builder()
				.roleId(RoleId.provided(roleId))
				.path(path);
		if (StringUtils.hasText(secretId)) {
			builder = builder.secretId(SecretId.provided(secretId));
		}
		return new AppRoleAuthentication(builder.build(), restOperations());
	}

	protected AppIdUserIdMechanism getAppIdUserIdMechanism(String userId) {

		if (userId.equalsIgnoreCase(AppIdUserId.IP_ADDRESS.name())) {
			return new IpAddressUserId();
		}

		if (userId.equalsIgnoreCase(AppIdUserId.MAC_ADDRESS.name())) {
			return new MacAddressUserId();
		}

		return new StaticUserId(userId);
	}

	protected ClientAuthentication awsEc2Authentication() {
		String role = getProperty("vault.aws-ec2.role");
		String roleId = getProperty("vault.aws-ec2.role-id");
		String identityDocument = getProperty("vault.aws-ec2.identity-document");
		String path = getProperty("vault.aws-ec2.aws-ec2-path",
				AwsEc2AuthenticationOptions.DEFAULT_AWS_AUTHENTICATION_PATH);
		Assert.isTrue(StringUtils.hasText(roleId) || StringUtils.hasText(role),
				"Vault AWS-EC2 authentication: Role (vault.aws-ec2.role) must not be empty");
		if (StringUtils.hasText(roleId) && StringUtils.hasText(role)) {
			throw new IllegalStateException("AWS-EC2 Authentication: Only one of Role (vault.aws-ec2.role) or"
					+ " RoleId (deprecated, vault.aws-ec2.roleId) must be provided");
		}
		if (StringUtils.hasText(roleId)) {
			logger.warn(
					"AWS-EC2 Authentication: vault.aws-ec2.roleId is deprecated. Please use vault.aws-ec2.role instead.");
		}
		AwsEc2AuthenticationOptionsBuilder builder = AwsEc2AuthenticationOptions.builder()
				.role(StringUtils.hasText(role) ? role : roleId)
				.path(path);
		if (StringUtils.hasText(identityDocument)) {
			builder.identityDocumentUri(URI.create(identityDocument));
		}
		return new AwsEc2Authentication(builder.build(), restOperations(), restOperations());
	}

	protected ClientAuthentication awsIamAuthentication() {
		String role = getProperty("vault.aws-iam.role");
		Assert.isTrue(StringUtils.hasText(role),
				"Vault AWS-IAM authentication: Role (vault.aws-iam.role) must not be empty");
		return AwsIam.doCreateIamAuthentication(role, restOperations());
	}

	protected ClientAuthentication azureMsiAuthentication() {
		String role = getProperty("vault.azure-msi.role");
		String path = getProperty("vault.azure-msi.azure-path",
				AzureMsiAuthenticationOptions.DEFAULT_AZURE_AUTHENTICATION_PATH);
		URI metadataServiceUri = getUri("vault.azure-msi.metadata-service",
				AzureMsiAuthenticationOptions.DEFAULT_INSTANCE_METADATA_SERVICE_URI);
		URI identityTokenServiceUri = getUri("vault.azure-msi.identity-token-service",
				AzureMsiAuthenticationOptions.DEFAULT_IDENTITY_TOKEN_SERVICE_URI);
		Assert.hasText(role, "Vault Azure MSI authentication: Role (vault.azure-msi.role) must not be empty");
		AzureMsiAuthenticationOptionsBuilder builder = AzureMsiAuthenticationOptions.builder()
				.role(role)
				.path(path)
				.instanceMetadataUri(metadataServiceUri)
				.identityTokenServiceUri(identityTokenServiceUri);
		return new AzureMsiAuthentication(builder.build(), restOperations());
	}

	protected ClientAuthentication cubbyholeAuthentication() {
		String token = getProperty("vault.token");
		Assert.hasText(token, "Vault Cubbyhole authentication: Initial token (vault.token) must not be empty");
		CubbyholeAuthenticationOptionsBuilder builder = CubbyholeAuthenticationOptions.builder()
				.wrapped()
				.initialToken(VaultToken.of(token));
		return new CubbyholeAuthentication(builder.build(), restOperations());
	}

	protected ClientAuthentication kubeAuthentication() {
		String role = getProperty("vault.kubernetes.role");
		String tokenFile = getProperty("vault.kubernetes.service-account-token-file",
				KubernetesServiceAccountTokenFile.DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE);
		String path = getProperty("vault.kubernetes.kubernetes-path",
				KubernetesAuthenticationOptions.DEFAULT_KUBERNETES_AUTHENTICATION_PATH);
		Assert.hasText(role, "Vault Kubernetes authentication: role must not be empty");
		KubernetesJwtSupplier jwtSupplier = new KubernetesServiceAccountTokenFile(tokenFile);
		KubernetesAuthenticationOptionsBuilder builder = KubernetesAuthenticationOptions.builder()
				.role(role)
				.jwtSupplier(jwtSupplier)
				.path(path);
		return new KubernetesAuthentication(builder.build(), restOperations());
	}

	private List<String> getPropertyAsList(String key) {
		String val = getEnvironment().getProperty(key);
		if (val == null) {
			return Collections.emptyList();
		}
		return Arrays.stream(val.split(",")).map(String::trim).collect(Collectors.toList());
	}

	@Nullable
	private String getProperty(String key) {
		return getEnvironment().getProperty(key);
	}

	private String getProperty(String key, String defaultValue) {
		return getEnvironment().getProperty(key, defaultValue);
	}

	private URI getUri(String key, URI defaultValue) {
		return getEnvironment().getProperty(key, URI.class, defaultValue);
	}

	@Nullable
	private Resource getResource(String key) {
		String value = getProperty(key);
		return value != null ? this.applicationContext.getResource(value) : null;
	}

	enum AppIdUserId {

		IP_ADDRESS, MAC_ADDRESS;

	}


	enum AuthenticationMethod {

		TOKEN, APPID, APPROLE, AWS_EC2, AWS_IAM, AZURE, CERT, CUBBYHOLE, KUBERNETES;

	}


	static class AwsIam {

		static ClientAuthentication doCreateIamAuthentication(String role, RestOperations restOperations) {
			AwsIamAuthenticationOptions.AwsIamAuthenticationOptionsBuilder builder = AwsIamAuthenticationOptions
					.builder()
					.role(role)
					.credentialsProvider(DefaultCredentialsProvider.create());
			return new AwsIamAuthentication(builder.build(), restOperations);
		}

	}

}
