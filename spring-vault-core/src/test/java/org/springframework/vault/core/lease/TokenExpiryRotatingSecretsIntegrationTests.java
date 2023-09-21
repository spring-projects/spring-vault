/*
 * Copyright 2020-2022 the original author or authors.
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
package org.springframework.vault.core.lease;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.AuthenticationEventPublisher;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.authentication.UsernamePasswordAuthentication;
import org.springframework.vault.authentication.UsernamePasswordAuthenticationOptions;
import org.springframework.vault.authentication.event.AuthenticationEventMulticaster;
import org.springframework.vault.authentication.event.LoginTokenExpiredEvent;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.lease.TokenExpiryRotatingSecretsIntegrationTests.UserPassConfiguration;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseRotatedEvent;
import org.springframework.vault.support.Policy;
import org.springframework.vault.support.Policy.BuiltinCapabilities;
import org.springframework.vault.support.Policy.Capability;
import org.springframework.vault.support.Policy.Rule;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.PrepareVault;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Integration tests for rotating generic secrets.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(classes = { UserPassConfiguration.class, RotatingGenericSecretsIntegrationTestConfiguration.class })
class TokenExpiryRotatingSecretsIntegrationTests extends IntegrationTestSupport {

	@BeforeAll
	static void beforeAll() {

		VaultInitializer initializer = new VaultInitializer();

		initializer.initialize();
		PrepareVault prepare = initializer.prepare();

		assumeThat(prepare.getVersion()).isGreaterThanOrEqualTo(VaultInitializer.VERSIONING_INTRODUCED_WITH);

		if (!prepare.hasAuth("userpass")) {
			prepare.mountAuth("userpass");
		}

		VaultOperations vaultOperations = prepare.getVaultOperations();

		Policy policy = Policy.of(Rule.builder().capabilities(BuiltinCapabilities.crud()).path("/*").build());
		vaultOperations.opsForSys().createOrUpdatePolicy("TokenExpiryRotatingSecretsIntegrationTests", policy);

		vaultOperations.write("auth/userpass/users/token-expiry", Map.of("password", "token-expiry", "token_ttl", 8,
				"token_max_ttl", 8, "token_policies", "TokenExpiryRotatingSecretsIntegrationTests"));

		VaultKeyValueOperations versioned = prepare.getVaultOperations()
			.opsForKeyValue("versioned", VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);

		versioned.put("rotating", Collections.singletonMap("key", "value"));
	}

	@Test
	void shouldRenewSecretsOnTokenRenewalFailure(
			@Autowired RotatingGenericSecretsIntegrationTestConfiguration.PropertySourceHolder holder,
			@Autowired SessionManager sessionManager, @Autowired SecretLeaseContainer container)
			throws InterruptedException {

		assertThat(holder.propertySource.getProperty("generic.rotating.key")).isEqualTo("value");

		CountDownLatch latch = new CountDownLatch(1);
		BlockingQueue<SecretLeaseEvent> events = new LinkedBlockingQueue<>();

		((AuthenticationEventMulticaster) sessionManager).addAuthenticationListener(leaseEvent -> {
			if (leaseEvent instanceof LoginTokenExpiredEvent) {
				latch.countDown();
			}
		});

		// for some reason, "failed to renew entry: policies have changed, not renewing"
		// happens.
		((AuthenticationEventMulticaster) sessionManager).addErrorListener(leaseEvent -> {
			latch.countDown();
		});

		VaultKeyValueOperations versioned = prepare().getVaultOperations()
			.opsForKeyValue("versioned", VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);

		versioned.put("rotating", Collections.singletonMap("key", "updated-value"));

		container.addLeaseListener(events::add);

		Assert.isTrue(latch.await(5, TimeUnit.SECONDS), "Timeout waiting for AuthenticationEvent");

		assertThat(events.poll(2, TimeUnit.SECONDS)).isInstanceOf(SecretLeaseRotatedEvent.class);
		assertThat(holder.propertySource.getProperty("generic.rotating.key")).isEqualTo("updated-value");

		versioned.put("rotating", Collections.singletonMap("key", "another-updated-value"));

		assertThat(events.poll(5, TimeUnit.SECONDS)).isInstanceOf(SecretLeaseRotatedEvent.class);
		assertThat(holder.propertySource.getProperty("generic.rotating.key")).isEqualTo("another-updated-value");
	}

	@Configuration
	static class UserPassConfiguration extends VaultIntegrationTestConfiguration {

		@Override
		public ClientAuthentication clientAuthentication() {
			return new UsernamePasswordAuthentication(UsernamePasswordAuthenticationOptions.builder()
				.username("token-expiry")
				.password("token-expiry")
				.build(), getRestTemplateFactory().create());
		}

	}

}
