/*
 * Copyright 2019-2022 the original author or authors.
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

import java.nio.charset.StandardCharsets;
import java.security.spec.RSAPrivateKeySpec;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.PemObject;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

/**
 * PCF implementation of {@link ClientAuthentication}. {@link PcfAuthentication} uses a
 * PCF instance certificate and key to login into Vault.
 * <p>
 * Requires BouncyCastle to generate a RSA PSS signature.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see PcfAuthenticationOptions
 * @see RestOperations
 * @see <a href="https://www.vaultproject.io/docs/auth/pcf.html">Auth Backend: PCF</a>
 */
public class PcfAuthentication implements ClientAuthentication, AuthenticationStepsFactory {

	private static final Log logger = LogFactory.getLog(PcfAuthentication.class);

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

	// SHA256 hash and a salt length of 222
	private static final int SALT_LENGTH = 222;

	private final PcfAuthenticationOptions options;

	private final RestOperations restOperations;

	/**
	 * Create a {@link PcfAuthentication} using {@link PcfAuthenticationOptions} and
	 * {@link RestOperations}.
	 * @param options must not be {@literal null}.
	 * @param restOperations must not be {@literal null}.
	 */
	public PcfAuthentication(PcfAuthenticationOptions options, RestOperations restOperations) {

		Assert.notNull(options, "PcfAuthenticationOptions must not be null");
		Assert.notNull(restOperations, "RestOperations must not be null");

		this.options = options;
		this.restOperations = restOperations;
	}

	/**
	 * Creates a {@link AuthenticationSteps} for pcf authentication given
	 * {@link PcfAuthenticationOptions}.
	 * @param options must not be {@literal null}.
	 * @return {@link AuthenticationSteps} for pcf authentication.
	 */
	public static AuthenticationSteps createAuthenticationSteps(PcfAuthenticationOptions options) {

		Assert.notNull(options, "PcfAuthenticationOptions must not be null");

		AuthenticationSteps.Node<String> cert = AuthenticationSteps.fromSupplier(options.getInstanceCertSupplier());
		AuthenticationSteps.Node<String> key = AuthenticationSteps.fromSupplier(options.getInstanceKeySupplier());

		return cert.zipWith(key)
			.map(credentials -> getPcfLogin(options.getRole(), options.getClock(), credentials.getLeft(),
					credentials.getRight()))
			.login(AuthenticationUtil.getLoginPath(options.getPath()));
	}

	@Override
	public VaultToken login() throws VaultException {

		Map<String, String> login = getPcfLogin(this.options.getRole(), this.options.getClock(),
				this.options.getInstanceCertSupplier().get(), this.options.getInstanceKeySupplier().get());

		try {
			VaultResponse response = this.restOperations
				.postForObject(AuthenticationUtil.getLoginPath(this.options.getPath()), login, VaultResponse.class);

			Assert.state(response != null && response.getAuth() != null, "Auth field must not be null");

			logger.debug("Login successful using PCF authentication");

			return LoginTokenUtil.from(response.getAuth());
		}
		catch (RestClientException e) {
			throw VaultLoginException.create("PCF", e);
		}
	}

	@Override
	public AuthenticationSteps getAuthenticationSteps() {
		return createAuthenticationSteps(this.options);
	}

	private static Map<String, String> getPcfLogin(String role, Clock clock, String instanceCert, String instanceKey) {

		Assert.hasText(role, "Role must not be empty");

		String signingTime = TIME_FORMAT.format(LocalDateTime.now(clock));
		String message = getMessage(role, signingTime, instanceCert);
		String signature = sign(message, instanceKey);
		Map<String, String> login = new HashMap<>();

		login.put("role", role);
		login.put("cf_instance_cert", instanceCert);
		login.put("signing_time", signingTime);
		login.put("signature", signature);

		return login;
	}

	private static String sign(String message, String privateKeyPem) {

		try {
			return doSign(message.getBytes(StandardCharsets.US_ASCII), privateKeyPem);
		}
		catch (CryptoException e) {
			throw new VaultException("Cannot sign PCF login", e);
		}
	}

	private static String getMessage(String role, String signingTime, String instanceCertPem) {
		return signingTime + instanceCertPem + role;
	}

	private static String doSign(byte[] message, String instanceKeyPem) throws CryptoException {

		RSAPrivateKeySpec privateKey = PemObject.fromKey(instanceKeyPem).getRSAPrivateKeySpec();
		PSSSigner signer = new PSSSigner(new RSAEngine(), new SHA256Digest(), SALT_LENGTH);

		signer.init(true, new RSAKeyParameters(true, privateKey.getModulus(), privateKey.getPrivateExponent()));
		signer.update(message, 0, message.length);

		byte[] signature = signer.generateSignature();

		return Base64.getUrlEncoder().encodeToString(signature);
	}

}
