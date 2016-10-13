/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.vault.authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * AWS-EC2 login implementation.
 * <p>
 * AWS-EC2 login uses the EC2 identity document and a nonce to login into Vault. AWS-EC2
 * login obtains the PKCS#7 signed EC2 identity document and generates a
 * {@link #createNonce() nonce}. Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @see AwsEc2AuthenticationOptions
 * @see <a href="https://www.vaultproject.io/docs/auth/aws-ec2.html">Auth Backend:
 * aws-ec2</a>
 */
public class AwsEc2Authentication implements ClientAuthentication {

	private final static Logger logger = LoggerFactory
			.getLogger(AwsEc2Authentication.class);

	private final AwsEc2AuthenticationOptions options;

	private final VaultClient vaultClient;

	private final RestTemplate restTemplate;

	private final AtomicReference<char[]> nonce = new AtomicReference<char[]>();

	/**
	 * Creates a new {@link AwsEc2Authentication}.
	 * 
	 * @param vaultClient must not be {@literal null}.
	 */
	public AwsEc2Authentication(VaultClient vaultClient) {
		this(AwsEc2AuthenticationOptions.DEFAULT, vaultClient, vaultClient
				.getRestTemplate());
	}

	/**
	 * Creates a new {@link AwsEc2Authentication} specifying
	 * {@link AwsEc2AuthenticationOptions}, {@link VaultClient} and a {@link RestTemplate}
	 * .
	 * 
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 * @param restTemplate must not be {@literal null}.
	 */
	public AwsEc2Authentication(AwsEc2AuthenticationOptions options,
			VaultClient vaultClient, RestTemplate restTemplate) {

		Assert.notNull(options, "AwsEc2AuthenticationOptions must not be null");
		Assert.notNull(vaultClient, "VaultEndpoint must not be null");
		Assert.notNull(restTemplate, "RestTemplate must not be null");

		this.options = options;
		this.vaultClient = vaultClient;
		this.restTemplate = restTemplate;
	}

	@Override
	public VaultToken login() throws VaultException {
		return createTokenUsingAwsEc2();
	}

	@SuppressWarnings("unchecked")
	private VaultToken createTokenUsingAwsEc2() {

		String path = String.format("auth/%s/login", options.getPath());

		Map<String, String> login = getEc2Login();

		VaultResponseEntity<VaultResponse> entity = this.vaultClient.postForEntity(path,
				login, VaultResponse.class);

		if (!entity.isSuccessful()) {
			throw new VaultException(String.format("Cannot login using AWS-EC2: %s",
					entity.getMessage()));
		}

		VaultResponse body = entity.getBody();

		if (logger.isDebugEnabled()) {

			if (body.getAuth().get("metadata") instanceof Map) {
				Map<Object, Object> metadata = (Map<Object, Object>) body.getAuth().get(
						"metadata");
				logger.debug(String
						.format("Login successful using AWS-EC2 authentication for instance %s, AMI %s",
								metadata.get("instance_id"), metadata.get("instance_id")));
			}
			else {
				logger.debug("Login successful using AWS-EC2 authentication");
			}
		}

		return LoginTokenUtil.from(entity.getBody().getAuth());
	}

	protected Map<String, String> getEc2Login() {

		Map<String, String> login = new HashMap<String, String>();

		if (StringUtils.hasText(options.getRole())) {
			login.put("role", options.getRole());
		}

		if (this.nonce.get() == null) {
			this.nonce.compareAndSet(null, createNonce());
		}

		login.put("nonce", new String(this.nonce.get()));

		try {
			String pkcs7 = restTemplate.getForObject(options.getIdentityDocumentUri(),
					String.class);
			if (StringUtils.hasText(pkcs7)) {
				login.put("pkcs7", pkcs7.replaceAll("\\r", "").replace("\\n", ""));
			}

			return login;
		}
		catch (RestClientException e) {
			throw new VaultException(String.format(
					"Cannot obtain Identity Document from %s",
					options.getIdentityDocumentUri()), e);
		}
	}

	protected char[] createNonce() {
		return UUID.randomUUID().toString().toCharArray();
	}
}
