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
package org.springframework.vault.core;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.web.client.RestOperations;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VaultTokenTemplate} through {@link VaultTokenOperations}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultTokenTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired
	private VaultOperations vaultOperations;
	private VaultTokenOperations tokenOperations;

	@Before
	public void before() throws Exception {
		tokenOperations = vaultOperations.opsForToken();
	}

	@Test
	public void createTokenShouldCreateANewToken() {

		VaultTokenResponse tokenResponse = tokenOperations.create();
		assertThat(tokenResponse.getAuth()).containsKey("client_token");
	}

	@Test
	public void createTokenShouldCreateACustomizedToken() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.displayName("display") //
				.explicitMaxTtl(TimeUnit.HOURS.toSeconds(10)) //
				.ttl(30 * 60) //
				.policies(Collections.singleton("root")) //
				.numUses(2) //
				.renewable() //
				.noDefaultPolicy() //
				.noParent() //
				.id("HELLO-WORLD") //
				.build();

		VaultTokenResponse tokenResponse = tokenOperations.create(tokenRequest);
		assertThat(tokenResponse.getAuth()).containsEntry("client_token",
				tokenRequest.getId());
	}

	@Test
	public void createOrphanTokenShouldCreateAToken() {

		VaultTokenResponse tokenResponse = tokenOperations.createOrphan();
		assertThat(tokenResponse.getAuth()).containsKey("client_token");
	}

	@Test
	public void createOrphanTokenShouldCreateACustomizedToken() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.displayName("display") //
				.explicitMaxTtl(TimeUnit.HOURS.toSeconds(10)) //
				.ttl(30 * 60) //
				.policies(Collections.singleton("root")) //
				.numUses(2) //
				.renewable() //
				.noDefaultPolicy() //
				.noParent() //
				.id("HELLO-WORLD") //
				.build();

		VaultTokenResponse tokenResponse = tokenOperations.createOrphan(tokenRequest);
		assertThat(tokenResponse.getAuth()).containsEntry("client_token",
				tokenRequest.getId());
	}

	@Test
	public void renewShouldRenewToken() {

		VaultTokenRequest tokenRequest = VaultTokenRequest.builder()
				.explicitMaxTtl(TimeUnit.HOURS.toSeconds(10)) //
				.ttl(30 * 60) //
				.renewable() //
				.build();

		VaultTokenResponse tokenResponse = tokenOperations.create(tokenRequest);
		VaultTokenResponse renew = tokenOperations.renew(tokenResponse.getToken());

		assertThat(renew.getAuth()).containsKey("client_token");
	}

	@Test(expected = VaultException.class)
	public void renewShouldFailForNonRenewableRenewTokens() {

		VaultTokenResponse tokenResponse = tokenOperations.create();
		VaultTokenResponse renew = tokenOperations.renew(tokenResponse.getToken());

		assertThat(renew.getAuth()).containsKey("client_token");
	}

	@Test
	public void revokeShouldRevokeToken() {

		final VaultTokenResponse tokenResponse = tokenOperations.create();
		tokenOperations.revoke(tokenResponse.getToken());

		try {
			lookupSelf(tokenResponse);
		}
		catch (VaultException e) {
			assertThat(e).hasMessageContaining("permission denied");
		}
	}

	@Test
	public void createdTokenShouldBeUsableWithVaultClient() {

		final VaultTokenResponse tokenResponse = tokenOperations.create();

		ResponseEntity<String> response = lookupSelf(tokenResponse);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private ResponseEntity<String> lookupSelf(final VaultTokenResponse tokenResponse) {

		return vaultOperations
				.doWithVault(new RestOperationsCallback<ResponseEntity<String>>() {
					@Override
					public ResponseEntity<String> doWithRestOperations(
							RestOperations restOperations) {
						HttpHeaders headers = new HttpHeaders();
						headers.add(VaultHttpHeaders.VAULT_TOKEN, tokenResponse
								.getToken()
								.getToken());

						return restOperations.exchange("auth/token/lookup-self",
								HttpMethod.GET, new HttpEntity<Object>(headers),
								String.class);
					}
				});

	}
}
