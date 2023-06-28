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
import static org.springframework.vault.authentication.JwtAuthentication.DEFAULT_JWT_AUTHENTICATION_PATH;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.Settings;
import org.springframework.vault.util.TestRestTemplateFactory;

public class JwtAuthenticationIntegrationTest extends IntegrationTestSupport {

	private static MockWebServer mockServer = new MockWebServer();

	private static KeyPair keyPair;

	@BeforeAll
	static void setupJWKSMockServer() throws Exception {
		mockServer.start(8000);
		keyPair = generateRsaKey();
		var rsaPublicKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).build();
		var jwkSet = new JWKSet(rsaPublicKey).toPublicJWKSet();
		Dispatcher dispatcher = new Dispatcher() {
			@Override
			public MockResponse dispatch(RecordedRequest request) {
				return switch (request.getPath()) {
					case "/jwks" -> new MockResponse().addHeader("Content-Type", MediaType.APPLICATION_JSON)
						.setBody(jwkSet.toString());
					default -> new MockResponse().setResponseCode(404);
				};
			}
		};
		mockServer.setDispatcher(dispatcher);
	}

	@AfterAll
	static void stop() throws IOException {
		mockServer.close();
	}

	private static KeyPair generateRsaKey() throws Exception {
		KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
		keyPairGenerator.initialize(2048);
		return keyPairGenerator.generateKeyPair();
	}

	private String createToken() throws Exception {
		var signer = new RSASSASigner(keyPair.getPrivate());
		var header = new JWSHeader(JWSAlgorithm.RS256);
		var body = new JWSObject(header,
				new JWTClaimsSet.Builder().audience("local")
					.subject("admin")
					.claim("user", "Administrator")
					.issueTime(new Date())
					.expirationTime(java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(1)))
					.issuer("http://localhost:8000")
					.build()
					.toPayload());
		body.sign(signer);
		return body.serialize();
	}

	@BeforeEach
	void before() {
		if (!prepare().hasAuth("jwt")) {
			prepare().mountAuth("jwt");
		}

		prepare().getVaultOperations().doWithSession(restOperations -> {
			var jwtConfig = Map.of( //
					"jwks_url", "http://localhost:8000/jwks", //
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
		var restTemplate = TestRestTemplateFactory.create(Settings.createSslConfiguration());
		var loginToken = new JwtAuthentication(
				JwtAuthenticationOptions.builder().jwt(createToken()).role("my-role").build(), restTemplate)
			.login();
		assertThat(loginToken.getToken()).startsWith("hvs.");
	}

	@Test
	void claimChangedInTokenShouldFailSignatureVerification() {
		var token = "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiJsb2NhbCIsInN1YiI6InRlc3QiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwMDAiLCJleHAiOjE2ODc5ODI4MjcsInVzZXIiOiJBZG1pbmlzdHJhdG9yIiwiaWF0IjoxNjg3ODk2NDI3fQ.x_4cO5-GGbkDqts6-uRz8XTJbY50oCRqpkFyKmbcAt5Fq6IP4HryMJCd_DNxbZnfhXNxNdAEp4qXQFt6MEpi6YfQ3cc8Tg99pjvB6Hr6DKxao_UwEbWMrc7IIu-ZTjb1Nz91hXnYPpNGQQk4nNQN3_Ahdba6Ptt4i44_DiVyueR5pwE4NQDUoqlB1ETFjWl8O7t4px1No_LEJ5BEE2PboiPCxVU2zlyNL0-pmnWw8b1bRKvRPd2wXXMrYjcXNkL_GlqE4H18WOqwuNIJMbwXcwxE_sjk-6QTFxaeEDBiGKyGTFaIHlpN0OUiSBu2fpVHdaLRjzVBXGUBVsPgXqO2Nw";
		var restTemplate = TestRestTemplateFactory.create(Settings.createSslConfiguration());
		Assertions
			.assertThatThrownBy(
					() -> new JwtAuthentication(JwtAuthenticationOptions.builder().jwt(token).role("my-role").build(),
							restTemplate)
						.login())
			.isInstanceOf(VaultLoginException.class)
			.hasMessage(
					"Cannot login using JWT: error validating token: error verifying token signature: failed to verify id token signature");
	}

}
