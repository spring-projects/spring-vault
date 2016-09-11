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
package org.springframework.vault.core;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.support.VaultTokenRequest;
import org.springframework.vault.support.VaultTokenResponse;
import org.springframework.vault.util.IntegrationTestSupport;

/**
 * Integration tests for {@link VaultTokenTemplate} through {@link VaultTokenOperations}.
 * 
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
public class VaultTokenTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired private VaultOperations vaultOperations;
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

		VaultTokenRequest tokenRequest = new VaultTokenRequest();
		tokenRequest.setDisplayName("display");
		tokenRequest.setExplicitMaxTtl("1h");
		tokenRequest.setTtl("30m");
		tokenRequest.setPolicies(Collections.singletonList("root"));
		tokenRequest.setNumUses(2);
		tokenRequest.setRenewable(true);
		tokenRequest.setId("HELLO-WORLD");

		VaultTokenResponse tokenResponse = tokenOperations.create(tokenRequest);
		assertThat(tokenResponse.getAuth()).containsEntry("client_token", tokenRequest.getId());
	}

	@Test
	public void createOrphanTokenShouldCreateAToken() {

		VaultTokenResponse tokenResponse = tokenOperations.createOrphan();
		assertThat(tokenResponse.getAuth()).containsKey("client_token");
	}

	@Test
	public void createOrphanTokenShouldCreateACustomizedToken() {

		VaultTokenRequest tokenRequest = new VaultTokenRequest();
		tokenRequest.setDisplayName("display");
		tokenRequest.setExplicitMaxTtl("1h");
		tokenRequest.setTtl("30m");
		tokenRequest.setPolicies(Collections.singletonList("root"));
		tokenRequest.setNumUses(2);
		tokenRequest.setRenewable(true);
		tokenRequest.setId("HELLO-WORLD");

		VaultTokenResponse tokenResponse = tokenOperations.createOrphan(tokenRequest);
		assertThat(tokenResponse.getAuth()).containsEntry("client_token", tokenRequest.getId());
	}

	@Test
	public void renewShouldRenewToken() {

		VaultTokenRequest tokenRequest = new VaultTokenRequest();
		tokenRequest.setDisplayName("display");
		tokenRequest.setExplicitMaxTtl("1h");
		tokenRequest.setTtl("30m");

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

		VaultResponseEntity<String> response = lookupSelf(tokenResponse);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getMessage()).isEqualTo("permission denied");
	}

	@Test
	public void createdTokenShouldBeUsableWithVaultClient() {

		final VaultTokenResponse tokenResponse = tokenOperations.create();

		VaultResponseEntity<String> response = lookupSelf(tokenResponse);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private VaultResponseEntity<String> lookupSelf(final VaultTokenResponse tokenResponse) {

		return vaultOperations.doWithVault(new VaultOperations.ClientCallback<VaultResponseEntity<String>>() {
			@Override
			public VaultResponseEntity<String> doWithVault(VaultClient client) {
				return client.getForEntity("/auth/token/lookup-self", tokenResponse.getToken(), String.class);
			}
		});
	}
}
