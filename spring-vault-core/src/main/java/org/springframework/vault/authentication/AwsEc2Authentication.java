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

package org.springframework.vault.authentication;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.AuthenticationSteps.HttpRequestBuilder;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * AWS-EC2 login implementation.
 * <p>AWS-EC2 login uses the EC2 identity document and a nonce to login into
 * Vault. AWS-EC2 login obtains the PKCS#7 signed EC2 identity document and
 * generates a {@link #createNonce() nonce}. Instances of this class are
 * immutable once constructed.
 *
 * @author Mark Paluch
 * @see AwsEc2AuthenticationOptions
 * @see VaultClient
 * @see <a href="https://www.vaultproject.io/docs/auth/aws-ec2.html">Auth
 * Backend: aws-ec2</a>
 */
public class AwsEc2Authentication implements ClientAuthentication, AuthenticationStepsFactory {

	private static final Log logger = LogFactory.getLog(AwsEc2Authentication.class);

	private static final char[] EMPTY = new char[0];

	private static final String METADATA_TOKEN_TTL_HEADER = "X-aws-ec2-metadata-token-ttl-seconds";

	private static final String METADATA_TOKEN_HEADER = "X-aws-ec2-metadata-token";


	private final AwsEc2AuthenticationOptions options;

	private final VaultLoginClient loginClient;

	private final ClientAdapter awsMetadataAdapter;

	private final AtomicReference<char[]> nonce = new AtomicReference<>(EMPTY);


	/**
	 * Create a new {@code AwsEc2Authentication}.
	 * @param vaultRestOperations must not be {@literal null}.
	 * @deprecated since 4.1, use
	 * {@link #AwsEc2Authentication(AwsEc2AuthenticationOptions, VaultClient, RestClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public AwsEc2Authentication(RestOperations vaultRestOperations) {
		this(AwsEc2AuthenticationOptions.DEFAULT, vaultRestOperations, vaultRestOperations);
	}

	/**
	 * Create a new {@code AwsEc2Authentication} specifying
	 * {@link AwsEc2AuthenticationOptions}, a Vault and an AWS-Metadata-specific
	 * {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param vaultRestOperations must not be {@literal null}.
	 * @param awsMetadataRestOperations must not be {@literal null}.
	 * {@link #AwsEc2Authentication(AwsEc2AuthenticationOptions, VaultClient, RestClient)}
	 * instead.
	 */
	@Deprecated(since = "4.1")
	public AwsEc2Authentication(AwsEc2AuthenticationOptions options, RestOperations vaultRestOperations,
			RestOperations awsMetadataRestOperations) {
		this(options, ClientAdapter.from(vaultRestOperations).vaultClient(),
				ClientAdapter.from(awsMetadataRestOperations));
	}

	/**
	 * Create a new {@code AwsEc2Authentication}.
	 * @param restClient must not be {@literal null}.
	 * @since 4.0
	 */
	public AwsEc2Authentication(RestClient restClient) {
		this(AwsEc2AuthenticationOptions.DEFAULT, restClient, restClient);
	}

	/**
	 * Create a new {@code AwsEc2Authentication} specifying
	 * {@link AwsEc2AuthenticationOptions}, {@link VaultClient} and a
	 * AWS-Metadata-specific {@link RestClient}.
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 * @param awsMetadataClient must not be {@literal null}.
	 * @since 4.0
	 */
	public AwsEc2Authentication(AwsEc2AuthenticationOptions options, RestClient vaultClient,
			RestClient awsMetadataClient) {
		this(options, ClientAdapter.from(vaultClient).vaultClient(), awsMetadataClient);
	}

	/**
	 * Create a new {@link AwsEc2Authentication} specifying {@link VaultClient}.
	 * @param vaultClient must not be {@literal null}.
	 * @since 4.1
	 */
	public AwsEc2Authentication(VaultClient vaultClient) {
		this(AwsEc2AuthenticationOptions.DEFAULT, vaultClient, ClientAdapter.from(RestClient.create()));
	}

	/**
	 * Create a new {@link AwsEc2Authentication} specifying {@link VaultClient} and
	 * an AWS-Metadata-specific {@link RestClient}.
	 * @param vaultClient must not be {@literal null}.
	 * @param awsMetadataClient must not be {@literal null}.
	 * @since 4.1
	 */
	public AwsEc2Authentication(VaultClient vaultClient, RestClient awsMetadataClient) {
		this(AwsEc2AuthenticationOptions.DEFAULT, vaultClient, ClientAdapter.from(awsMetadataClient));
	}

	/**
	 * Create a new {@link AwsEc2Authentication} specifying
	 * {@link AwsEc2AuthenticationOptions}, a {@link VaultClient} and an
	 * AWS-Metadata-specific {@link RestClient}.
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 * @param awsMetadataClient must not be {@literal null}.
	 * @since 4.1
	 */
	public AwsEc2Authentication(AwsEc2AuthenticationOptions options, VaultClient vaultClient,
			RestClient awsMetadataClient) {
		this(options, vaultClient, ClientAdapter.from(awsMetadataClient));
	}

	AwsEc2Authentication(AwsEc2AuthenticationOptions options, VaultClient vaultClient,
			ClientAdapter awsMetadataClient) {
		Assert.notNull(options, "AwsEc2AuthenticationOptions must not be null");
		Assert.notNull(vaultClient, "VaultClient must not be null");
		Assert.notNull(awsMetadataClient, "AWS Metadata RestClient must not be null");
		this.options = options;
		this.loginClient = VaultLoginClient.create(vaultClient, "AWS-EC2");
		this.awsMetadataAdapter = awsMetadataClient;
	}


	/**
	 * Create {@link AuthenticationSteps} for AWS-EC2 authentication given
	 * {@link AwsEc2AuthenticationOptions}.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for AWS-EC2 authentication.
	 * @since 2.0
	 */
	public static AuthenticationSteps createAuthenticationSteps(AwsEc2AuthenticationOptions options) {
		Assert.notNull(options, "AwsEc2AuthenticationOptions must not be null");
		AtomicReference<char[]> nonce = new AtomicReference<>(EMPTY);
		return createAuthenticationSteps(options, nonce, () -> doCreateNonce(options));
	}

	protected static AuthenticationSteps createAuthenticationSteps(AwsEc2AuthenticationOptions options,
			AtomicReference<char[]> nonce, Supplier<char[]> nonceSupplier) {

		AuthenticationSteps.HttpRequest<String> identityRequest = HttpRequestBuilder
				.get(options.getIdentityDocumentUri().toString())
				.as(String.class);
		AuthenticationSteps.Node<String> identity;

		if (options.getVersion() == AwsEc2AuthenticationOptions.InstanceMetadataServiceVersion.V2) {
			identity = AuthenticationSteps
					.fromHttpRequest(HttpRequestBuilder.put(options.getMetadataTokenRequestUri())
							.with(createTokenRequestHeaders(options))
							.as(String.class))
					.map(it -> {
						return VaultHttpHeaders.singleton(METADATA_TOKEN_HEADER, it);
						})
					.request(identityRequest);
		} else {
			identity = AuthenticationSteps.fromHttpRequest(identityRequest);
		}

		return identity //
				.map(pkcs7 -> pkcs7.replaceAll("\\r", "")) //
				.map(pkcs7 -> pkcs7.replaceAll("\\n", "")) //
				.map(pkcs7 -> {
					Map<String, String> login = new LinkedHashMap<>();
					if (StringUtils.hasText(options.getRole())) {
						login.put("role", options.getRole());
					}
					if (Objects.equals(nonce.get(), EMPTY)) {
						nonce.compareAndSet(EMPTY, nonceSupplier.get());
					}
					login.put("nonce", new String(nonce.get()));
					login.put("pkcs7", pkcs7);
					return login;
				})
				.loginAt(options.getPath());
	}


	@Override
	public VaultToken login() throws VaultException {
		return createTokenUsingAwsEc2();
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(this.options, this.nonce, this::createNonce);
	}

	@SuppressWarnings("unchecked")
	private VaultToken createTokenUsingAwsEc2() {
		Map<String, String> login = getEc2Login();
		VaultResponseSupport<LoginToken> token = this.loginClient.loginAt(this.options.getPath())
				.using(login)
				.retrieve()
				.body();
		if (logger.isDebugEnabled()) {
			if (token.getAuth().get("metadata") instanceof Map) {
				Map<Object, Object> metadata = (Map<Object, Object>) token.getAuth().get("metadata");
				logger.debug("Using AWS-EC2 authentication for instance %s, AMI %s"
						.formatted(metadata.get("instance_id"), metadata.get("instance_id")));
			}
		}
		return token.getRequiredData();
	}

	protected Map<String, String> getEc2Login() {
		Map<String, String> login = new HashMap<>();
		HttpHeaders headers = new HttpHeaders();
		if (options.getVersion() == AwsEc2AuthenticationOptions.InstanceMetadataServiceVersion.V2) {
			headers.add(METADATA_TOKEN_HEADER, createIMDSv2Token());
		}
		if (StringUtils.hasText(this.options.getRole())) {
			login.put("role", this.options.getRole());
		}
		if (Objects.equals(this.nonce.get(), EMPTY)) {
			this.nonce.compareAndSet(EMPTY, createNonce());
		}
		login.put("nonce", new String(this.nonce.get()));

		try {
			HttpEntity<Object> entity = new HttpEntity<>(headers);
			ResponseEntity<String> exchange = this.awsMetadataAdapter.exchange(this.options.getIdentityDocumentUri(),
					HttpMethod.GET, entity, String.class);
			if (!exchange.getStatusCode().is2xxSuccessful()) {
				throw new HttpClientErrorException(exchange.getStatusCode());
			}
			String pkcs7 = exchange.getBody();
			if (StringUtils.hasText(pkcs7)) {
				login.put("pkcs7", pkcs7.replaceAll("\\r", "").replaceAll("\\n", ""));
			}

			return login;
		} catch (RestClientException e) {
			throw new VaultLoginException(
					"Cannot obtain Identity Document from %s".formatted(this.options.getIdentityDocumentUri()), e);
		}
	}

	private String createIMDSv2Token() {
		try {
			HttpEntity<Object> entity = new HttpEntity<>(createTokenRequestHeaders(this.options));
			ResponseEntity<String> exchange = this.awsMetadataAdapter
					.exchange(this.options.getMetadataTokenRequestUri(), HttpMethod.PUT, entity, String.class);
			if (!exchange.getStatusCode().is2xxSuccessful()) {
				throw new HttpClientErrorException(exchange.getStatusCode());
			}
			return AuthenticationUtil.getRequiredBody(exchange);
		} catch (RestClientException e) {
			throw new VaultLoginException(
					"Cannot obtain IMDSv2 Token from %s".formatted(this.options.getMetadataTokenRequestUri()), e);
		}
	}

	protected char[] createNonce() {
		return doCreateNonce(this.options);
	}

	private static char[] doCreateNonce(AwsEc2AuthenticationOptions options) {
		return options.getNonce().getValue();
	}

	private static HttpHeaders createTokenRequestHeaders(AwsEc2AuthenticationOptions options) {
		return VaultHttpHeaders.singleton(METADATA_TOKEN_TTL_HEADER,
				String.valueOf(options.getMetadataTokenTtl().toSeconds()));
	}

}
