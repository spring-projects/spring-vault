/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.config;

import java.net.URI;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.authentication.AppIdAuthentication;
import org.springframework.vault.authentication.AppIdAuthenticationOptions;
import org.springframework.vault.authentication.AppIdUserIdMechanism;
import org.springframework.vault.authentication.AppRoleAuthentication;
import org.springframework.vault.authentication.AppRoleAuthenticationOptions;
import org.springframework.vault.authentication.AwsEc2Authentication;
import org.springframework.vault.authentication.AwsEc2AuthenticationOptions;
import org.springframework.vault.authentication.AwsEc2AuthenticationOptions.AwsEc2AuthenticationOptionsBuilder;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.ClientCertificateAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthentication;
import org.springframework.vault.authentication.CubbyholeAuthenticationOptions;
import org.springframework.vault.authentication.IpAddressUserId;
import org.springframework.vault.authentication.KubeAuthentication;
import org.springframework.vault.authentication.KubeAuthenticationOptions;
import org.springframework.vault.authentication.KubeJwtSupplier;
import org.springframework.vault.authentication.KubeServiceAccountTokenFile;
import org.springframework.vault.authentication.MacAddressUserId;
import org.springframework.vault.authentication.StaticUserId;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.vault.support.SslConfiguration.KeyStoreConfiguration;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestOperations;

/**
 * Configuration using Spring's {@link org.springframework.core.env.Environment} to
 * configure Spring Vault endpoint, SSL options and authentication options. This
 * configuration class uses predefined property keys and is usually imported as part of an
 * existing Java-based configuration. Configuration is obtained from other, existing
 * property sources.
 * <p>
 * Usage:
 *
 * Java-based configuration part:
 *
 * <pre>
 *     <code>
 * &#64;Configuration
 * &#64;Import(EnvironmentVaultConfiguration.class)
 * public class MyConfiguration {
 * }
 *     </code>
 * </pre>
 *
 * Supplied properties:
 *
 * <pre>
 *     <code>
 * vault.uri=https://localhost:8200
 * vault.token=00000000-0000-0000-0000-000000000000
 *     </code>
 * </pre>
 *
 * <h3>Property keys</h3>
 *
 * Authentication-specific properties must be provided depending on the authentication
 * method.
 * <ul>
 * <li>Vault URI: {@code vault.uri}</li>
 * <li>SSL Configuration
 * <ul>
 * <li>Keystore resource: {@code vault.ssl.key-store} (optional)</li>
 * <li>Keystore password: {@code vault.ssl.key-store-password} (optional)</li>
 * <li>Truststore resource: {@code vault.ssl.trust-store} (optional)</li>
 * <li>Truststore password: {@code vault.ssl.trust-store-password} (optional)</li>
 * </ul>
 * </li>
 * <li>Authentication method: {@code vault.authentication} (defaults to {@literal TOKEN},
 * supported authentication methods are:
 * {@literal TOKEN, APPID, APPROLE, AWS_EC2, CERT, CUBBYHOLE})</li>
 * <li>Token authentication
 * <ul>
 * <li>Vault Token: {@code vault.token}</li>
 * </ul>
 * <li>AppId authentication
 * <ul>
 * <li>AppId: {@code vault.app-id.app-id}</li>
 * <li>UserId: {@code vault.app-id.user-id}. {@literal MAC_ADDRESS} and
 * {@literal IP_ADDRESS} use {@link MacAddressUserId}, respective {@link IpAddressUserId}.
 * Any other value is used with {@link StaticUserId}.</li>
 * </ul>
 * <li>AppRole authentication
 * <ul>
 * <li>RoleId: {@code vault.app-role.role-id}</li>
 * <li>SecretId: {@code vault.app-role.secret-id} (optional)</li>
 * </ul>
 * <li>AWS EC2 authentication
 * <ul>
 * <li>RoleId: {@code vault.aws-ec2.role-id}</li>
 * <li>Identity Document URL: {@code vault.aws-ec2.identity-document} (optional)</li>
 * </ul>
 * <li>Client Certificate authentication
 * <ul>
 * <li>(no configuration options)</li>
 * </ul>
 * <li>Cubbyhole authentication
 * <ul>
 * <li>Initial Vault Token: {@code vault.token}</li>
 * </ul>
 * </ul>
 *
 * @author Mark Paluch
 * @author Michal Budzyn
 * @see org.springframework.core.env.Environment
 * @see org.springframework.core.env.PropertySource
 * @see VaultEndpoint
 * @see AppIdAuthentication
 * @see AppRoleAuthentication
 * @see AwsEc2Authentication
 * @see ClientCertificateAuthentication
 * @see CubbyholeAuthentication
 * @see KubeAuthentication
 */
@Configuration
public class EnvironmentVaultConfiguration extends AbstractVaultConfiguration implements
		ApplicationContextAware {

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
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {

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

		KeyStoreConfiguration keyStoreConfiguration = getKeyStoreConfiguration(
				"vault.ssl.key-store", "vault.ssl.key-store-password");

		KeyStoreConfiguration trustStoreConfiguration = getKeyStoreConfiguration(
				"vault.ssl.trust-store", "vault.ssl.trust-store-password");

		return new SslConfiguration(keyStoreConfiguration, trustStoreConfiguration);
	}

	private KeyStoreConfiguration getKeyStoreConfiguration(String resourceProperty,
			String passwordProperty) {

		Resource keyStore = getResource(resourceProperty);
		String keyStorePassword = getProperty(passwordProperty);

		if (keyStore == null) {
			return KeyStoreConfiguration.unconfigured();
		}

		if (StringUtils.hasText(keyStorePassword)) {
			return KeyStoreConfiguration.of(keyStore, keyStorePassword.toCharArray());
		}

		return KeyStoreConfiguration.of(keyStore);
	}

	@Override
	public ClientAuthentication clientAuthentication() {

		String authentication = getEnvironment()
				.getProperty("vault.authentication", AuthenticationMethod.TOKEN.name())
				.toUpperCase().replace('-', '_');

		AuthenticationMethod authenticationMethod = AuthenticationMethod
				.valueOf(authentication);

		switch (authenticationMethod) {

		case TOKEN:
			return tokenAuthentication();
		case APPID:
			return appIdAuthentication();
		case APPROLE:
			return appRoleAuthentication();
		case AWS_EC2:
			return awsEc2Authentication();
		case CERT:
			return new ClientCertificateAuthentication(restOperations());
		case CUBBYHOLE:
			return cubbyholeAuthentication();
		case KUBERNETES:
			return kubeAuthentication();
		default:
			throw new IllegalStateException(String.format(
					"Vault authentication method %s is not supported with %s",
					authenticationMethod, getClass().getSimpleName()));
		}
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	protected ClientAuthentication tokenAuthentication() {

		String token = getProperty("vault.token");
		Assert.hasText(token,
				"Vault Token authentication: Token (vault.token) must not be empty");

		return new TokenAuthentication(token);
	}

	protected ClientAuthentication appIdAuthentication() {

		String appId = getEnvironment().getProperty("vault.app-id.app-id",
				getProperty("spring.application.name"));
		String userId = getProperty("vault.app-id.user-id");

		Assert.hasText(appId,
				"Vault AppId authentication: AppId (vault.app-id.app-id) must not be empty");
		Assert.hasText(userId,
				"Vault AppId authentication: UserId (vault.app-id.user-id) must not be empty");

		AppIdAuthenticationOptions authenticationOptions = AppIdAuthenticationOptions
				.builder().appId(appId) //
				.userIdMechanism(getAppIdUserIdMechanism(userId)).build();

		return new AppIdAuthentication(authenticationOptions, restOperations());
	}

	protected ClientAuthentication appRoleAuthentication() {

		String roleId = getProperty("vault.app-role.role-id");
		String secretId = getProperty("vault.app-role.secret-id");
		Assert.hasText(roleId,
				"Vault AppRole authentication: RoleId (vault.app-role.role-id) must not be empty");

		AppRoleAuthenticationOptions.AppRoleAuthenticationOptionsBuilder builder = AppRoleAuthenticationOptions
				.builder().roleId(roleId);

		if (StringUtils.hasText(secretId)) {
			builder = builder.secretId(secretId);
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

		String roleId = getProperty("vault.aws-ec2.role-id");
		String identityDocument = getProperty("vault.aws-ec2.identity-document");
		Assert.hasText(roleId,
				"Vault AWS EC2 authentication: RoleId (vault.aws-ec2.role-id) must not be empty");

		AwsEc2AuthenticationOptionsBuilder builder = AwsEc2AuthenticationOptions
				.builder().role(roleId);

		if (StringUtils.hasText(identityDocument)) {
			builder.identityDocumentUri(URI.create(identityDocument));
		}

		return new AwsEc2Authentication(builder.build(), restOperations(),
				restOperations());
	}

	protected ClientAuthentication cubbyholeAuthentication() {

		String token = getEnvironment().getProperty("vault.token");
		Assert.hasText(token,
				"Vault Cubbyhole authentication: Initial token (vault.token) must not be empty");

		CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions.builder() //
				.wrapped() //
				.initialToken(VaultToken.of(token)) //
				.build();

		return new CubbyholeAuthentication(options, restOperations());
	}

	protected ClientAuthentication kubeAuthentication() {

		String role = getProperty("vault.kubernetes.role");
		Assert.hasText(role, "Vault Kubernetes authentication: role must not be empty");

		String tokenFile = getProperty("vault.kubernetes.service-account-token-file");
		if (!StringUtils.hasText(tokenFile)) {
			tokenFile = KubeServiceAccountTokenFile.DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE;
		}
		KubeJwtSupplier jwtSupplier = new KubeServiceAccountTokenFile(tokenFile);

		KubeAuthenticationOptions authenticationOptions = KubeAuthenticationOptions
				.builder().role(role).jwtSupplier(jwtSupplier).build();

		return new KubeAuthentication(authenticationOptions, restOperations());
	}

	@Nullable
	private String getProperty(String key) {
		return getEnvironment().getProperty(key);
	}

	@Nullable
	private Resource getResource(String key) {

		String value = getProperty(key);
		return value != null ? applicationContext.getResource(value) : null;
	}

	enum AppIdUserId {
		IP_ADDRESS, MAC_ADDRESS;
	}

	enum AuthenticationMethod {
		TOKEN, APPID, APPROLE, AWS_EC2, CERT, CUBBYHOLE, KUBERNETES;
	}
}
