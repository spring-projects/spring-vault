/*
 * Copyright 2017-2024 the original author or authors.
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.vault.support.VaultToken;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.vault.authentication.JwtAuthentication.DEFAULT_JWT_AUTHENTICATION_PATH;

/**
 * Integration tests for {@link JwtAuthentication} using
 * {@link AuthenticationStepsExecutor}.
 *
 * @author Nanne Baars
 * @author Mark Paluch
 */
class JwtAuthenticationIntegrationTest extends IntegrationTestSupport {

	private KeyPair keyPair;

	@BeforeEach
	void before() throws Exception {

		keyPair = generateRsaKey();

		if (!prepare().hasAuth("jwt")) {
			prepare().mountAuth("jwt");
		}

		prepare().getVaultOperations().doWithSession(restOperations -> {

			Map<String, String> jwtConfig = Map.of("jwt_validation_pubkeys", encodePublicKey(), "oidc_client_id", "",
					"oidc_client_secret", "");
			restOperations.postForEntity("auth/jwt/config", jwtConfig, Map.class);

			Map<String, String> roleData = Map.of("role_type", DEFAULT_JWT_AUTHENTICATION_PATH, //
					"bound_audiences", "", "bound_subject", "admin", "user_claim", "user", "group_claims", "group");
			return restOperations.postForEntity("auth/jwt/role/my-role", roleData, Map.class);
		});
	}

	private KeyPair generateRsaKey() throws Exception {

		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		return keyPairGenerator.generateKeyPair();
	}

	private String encodePublicKey() {
		return String.format("""
				-----BEGIN PUBLIC KEY-----
				%s
				-----END PUBLIC KEY-----
				""", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
	}

	private String createToken(String user) throws JOSEException {

		RSASSASigner signer = new RSASSASigner(keyPair.getPrivate());
		JWSHeader header = new JWSHeader(JWSAlgorithm.RS256);
		JWSObject body = new JWSObject(header,
				new JWTClaimsSet.Builder().audience("local")
					.subject("admin")
					.claim("user", user)
					.issueTime(new Date())
					.expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))
					.issuer("http://localhost:8000")
					.build()
					.toPayload());
		body.sign(signer);
		return body.serialize();
	}

	@Test
	void shouldLoginSuccessfully() throws Exception {

		String jwt = createToken("Administrator");
		RestTemplate restTemplate = TestRestTemplateFactory.create(Settings.createSslConfiguration());

		JwtAuthentication authentication = new JwtAuthentication(
				JwtAuthenticationOptions.builder().jwtSupplier(() -> jwt).role("my-role").build(), restTemplate);
		VaultToken loginToken = authentication.login();

		assertThat(loginToken.getToken()).isNotNull();
	}

	@Test
	void claimChangedInTokenShouldFailSignatureVerification() throws Exception {

		String token1 = createToken("Administrator");
		String token2 = createToken("Administrator2");

		// Different user claim with signature of token1 makes an invalid token
		String jwt = token2.substring(0, token2.lastIndexOf('.') + 1) + token1.substring(token1.lastIndexOf('.') + 1);

		RestTemplate restTemplate = TestRestTemplateFactory.create(Settings.createSslConfiguration());
		JwtAuthentication authentication = new JwtAuthentication(
				JwtAuthenticationOptions.builder().jwtSupplier(() -> jwt).role("my-role").build(), restTemplate);

		assertThatThrownBy(authentication::login).isInstanceOf(VaultLoginException.class)
			.hasMessageContaining("Cannot login using JWT", "error validating token",
					"error verifying token signature");
	}

}
