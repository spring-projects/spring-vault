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
package org.springframework.vault.authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * AWS-EC2 login implementation.
 * <p>
 * AWS-EC2 login uses the EC2 identity document and a nonce to login into Vault. AWS-EC2
 * login obtains the PKCS#7 signed EC2 identity document and generates a
 * {@link #createNonce() nonce}. Instances of this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @see AwsEc2AuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/aws-ec2.html">Auth Backend:
 * aws-ec2</a>
 */
public class AwsEc2Authentication implements ClientAuthentication {

	private static final Log logger = LogFactory.getLog(AwsEc2Authentication.class);

	private final AwsEc2AuthenticationOptions options;

	private final RestOperations vaultRestOperations;

	private final RestOperations awsMetadataRestOperations;

	private final AtomicReference<char[]> nonce = new AtomicReference<char[]>();

	/**
	 * Create a new {@link AwsEc2Authentication}.
	 *
	 * @param vaultRestOperations must not be {@literal null}.
	 */
	public AwsEc2Authentication(RestOperations vaultRestOperations) {
		this(AwsEc2AuthenticationOptions.DEFAULT, vaultRestOperations,
				vaultRestOperations);
	}

	/**
	 * Create a new {@link AwsEc2Authentication} specifying
	 * {@link AwsEc2AuthenticationOptions}, a Vault and an AWS-Metadata-specific
	 * {@link RestOperations} .
	 *
	 * @param options must not be {@literal null}.
	 * @param vaultRestOperations must not be {@literal null}.
	 * @param awsMetadataRestOperations must not be {@literal null}.
	 */
	public AwsEc2Authentication(AwsEc2AuthenticationOptions options,
			RestOperations vaultRestOperations, RestOperations awsMetadataRestOperations) {

		Assert.notNull(options, "AwsEc2AuthenticationOptions must not be null");
		Assert.notNull(vaultRestOperations, "Vault RestOperations must not be null");
		Assert.notNull(awsMetadataRestOperations,
				"AWS Metadata RestOperations must not be null");

		this.options = options;
		this.vaultRestOperations = vaultRestOperations;
		this.awsMetadataRestOperations = awsMetadataRestOperations;
	}

	@Override
	public VaultToken login() throws VaultException {
		return createTokenUsingAwsEc2();
	}

	@SuppressWarnings("unchecked")
	private VaultToken createTokenUsingAwsEc2() {

		Map<String, String> login = getEc2Login();

		try {

			VaultResponse response = this.vaultRestOperations.postForObject(
					"/auth/{mount}/login", login, VaultResponse.class, options.getPath());

			if (logger.isDebugEnabled()) {

				if (response.getAuth().get("metadata") instanceof Map) {
					Map<Object, Object> metadata = (Map<Object, Object>) response
							.getAuth().get("metadata");
					logger.debug(String
							.format("Login successful using AWS-EC2 authentication for instance %s, AMI %s",
									metadata.get("instance_id"),
									metadata.get("instance_id")));
				}
				else {
					logger.debug("Login successful using AWS-EC2 authentication");
				}
			}

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (HttpStatusCodeException e) {
			throw new VaultException(String.format("Cannot login using AWS-EC2: %s",
					VaultResponses.getError(e.getResponseBodyAsString())));
		}
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
			String pkcs7 = awsMetadataRestOperations.getForObject(
					options.getIdentityDocumentUri(), String.class);
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
