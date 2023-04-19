/*
 * Copyright 2016-2022 the original author or authors.
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
package org.springframework.vault.core;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;
import org.springframework.vault.util.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Integration tests for {@link VaultTokenTemplate} through {@link VaultTokenOperations}.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class VaultTokenTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired
	VaultOperations vaultOperations;

	VaultTokenOperations tokenOperations;

	@BeforeEach
	void before() {
		this.tokenOperations = this.vaultOperations.opsForToken();
	}

	@Test
	void createTokenShouldCreateANewToken() {

		VaultTokenResponse tokenResponse = this.tokenOperations.create();
		assertThat(tokenResponse.getAuth()).containsKey("client_token");
	}

	@Test
	void createTokenShouldCreateACustomizedToken() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
			.displayName("display") //
			.explicitMaxTtl(Duration.ofHours(5)) //
			.ttl(Duration.ofMinutes(30 * 60)) //
			.policies(Collections.singleton("root")) //
			.numUses(2) //
			.renewable() //
			.noDefaultPolicy() //
			.noParent() //
			.id(UUID.randomUUID().toString()) //
			.build();

		VaultTokenResponse tokenResponse = this.tokenOperations.create(tokenRequest);
		assertThat(tokenResponse.getAuth()).containsEntry("client_token", tokenRequest.getId());
	}

	@Test
	void createOrphanTokenShouldCreateAToken() {

		VaultTokenResponse tokenResponse = this.tokenOperations.createOrphan();
		assertThat(tokenResponse.getAuth()).containsKey("client_token");
	}

	@Test
	void createOrphanTokenShouldCreateACustomizedToken() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
			.displayName("display") //
			.explicitMaxTtl(Duration.ofHours(5)) //
			.ttl(Duration.ofMinutes(30 * 60)) //
			.policies(Collections.singleton("root")) //
			.numUses(2) //
			.renewable() //
			.noDefaultPolicy() //
			.noParent() //
			.id(UUID.randomUUID().toString()) //
			.build();

		VaultTokenResponse tokenResponse = this.tokenOperations.createOrphan(tokenRequest);
		assertThat(tokenResponse.getAuth()).containsEntry("client_token", tokenRequest.getId());
	}

	@Test
	void renewShouldRenewToken() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
			.explicitMaxTtl(Duration.ofHours(5)) //
			.ttl(Duration.ofMinutes(30 * 60)) //
			.renewable() //
			.build();

		VaultTokenResponse tokenResponse = this.tokenOperations.create(tokenRequest);
		VaultTokenResponse renew = this.tokenOperations.renew(tokenResponse.getToken());

		assertThat(renew.getAuth()).containsKey("client_token");
	}

	@Test
	void renewShouldFailForNonRenewableRenewTokens() {

		VaultTokenResponse tokenResponse = this.tokenOperations.create();

		assertThatExceptionOfType(VaultException.class)
			.isThrownBy(() -> this.tokenOperations.renew(tokenResponse.getToken()));
	}

	@Test
	void revokeShouldRevokeToken() {

		final VaultTokenResponse tokenResponse = this.tokenOperations.create();
		this.tokenOperations.revoke(tokenResponse.getToken());

		try {
			lookupSelf(tokenResponse);
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("permission denied");
		}
	}

	@Test
	void createdTokenShouldBeUsableWithVaultClient() {

		final VaultTokenResponse tokenResponse = this.tokenOperations.create();

		ResponseEntity<String> response = lookupSelf(tokenResponse);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@SuppressWarnings("ConstantConditions")
	private ResponseEntity<String> lookupSelf(final VaultTokenResponse tokenResponse) {

		return this.vaultOperations.doWithVault(restOperations -> {
			HttpHeaders headers = new HttpHeaders();
			headers.add(VaultHttpHeaders.VAULT_TOKEN, tokenResponse.getToken().getToken());

			return restOperations.exchange("auth/token/lookup-self", HttpMethod.GET, new HttpEntity<>(headers),
					String.class);
		});

	}

}
