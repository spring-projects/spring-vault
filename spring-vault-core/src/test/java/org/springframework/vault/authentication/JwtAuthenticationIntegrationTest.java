/*
 * Copyright 2017-2023 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.vault.authentication.JwtAuthentication.DEFAULT_JWT_AUTHENTICATION_PATH;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;

public class JwtAuthenticationIntegrationTest extends IntegrationTestSupport {

	private KeyPair keyPair;

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

	@BeforeEach
	void before() throws Exception {
		keyPair = generateRsaKey();
		if (!prepare().hasAuth("jwt")) {
			prepare().mountAuth("jwt");
		}

		prepare().getVaultOperations().doWithSession(restOperations -> {
			var jwtConfig = Map.of( //
					"jwt_validation_pubkeys", encodePublicKey(), //
					"oidc_client_id", "", //
					"oidc_client_secret", "");
			restOperations.postForEntity("auth/jwt/config", jwtConfig, Map.class);

			var roleData = Map.of("role_type", DEFAULT_JWT_AUTHENTICATION_PATH, //
					"bound_audiences", "", //
					"bound_subject", "admin", //
					"user_claim", "user", //
					"group_claims", "group");
			return restOperations.postForEntity("auth/jwt/role/my-role", roleData, Map.class);
		});
	}

	@Test
	void shouldLoginSuccessfully() throws Exception {
		var jwt = createToken("Administrator");
		var restTemplate = TestRestTemplateFactory.create(Settings.createSslConfiguration());

		var loginToken = new JwtAuthentication(
				JwtAuthenticationOptions.builder().jwt(() -> jwt).role("my-role").build(), restTemplate)
			.login();

		assertThat(loginToken.getToken()).startsWith("hvs.");
	}

	@Test
	void claimChangedInTokenShouldFailSignatureVerification() throws Exception {
		var token1 = createToken("Administrator");
		var token2 = createToken("Administrator2");
		// Different user claim with signature of token1 makes an invalid token
		var jwt = token2.substring(0, token2.lastIndexOf('.') + 1) + token1.substring(token1.lastIndexOf('.') + 1);

		var restTemplate = TestRestTemplateFactory.create(Settings.createSslConfiguration());

		assertThatThrownBy(
				() -> new JwtAuthentication(JwtAuthenticationOptions.builder().jwt(() -> jwt).role("my-role").build(),
						restTemplate)
					.login())
			.isInstanceOf(VaultLoginException.class)
			.hasMessage(
					"Cannot login using JWT: error validating token: error verifying token signature: no known key successfully validated the token signature");
	}

	private String createToken(String user) throws JOSEException {
		var signer = new RSASSASigner(keyPair.getPrivate());
		var header = new JWSHeader(JWSAlgorithm.RS256);
		var body = new JWSObject(header,
				new JWTClaimsSet.Builder().audience("local")
					.subject("admin")
					.claim("user", user)
					.issueTime(new Date())
					.expirationTime(java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(1)))
					.issuer("http://localhost:8000")
					.build()
					.toPayload());
		body.sign(signer);
		return body.serialize();
	}

}
