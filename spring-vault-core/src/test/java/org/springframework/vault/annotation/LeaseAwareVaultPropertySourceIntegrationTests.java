/*
 * Copyright 2017-2021 the original author or authors.
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
package org.springframework.vault.annotation;

import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;
import org.springframework.vault.annotation.VaultPropertySource.Renewal;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.env.VaultPropertySourceNotFoundException;
import org.springframework.vault.util.VaultExtension;
import org.springframework.vault.util.VaultInitializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Integration test for {@link VaultPropertySource}.
 *
 * @author Mark Paluch
 */
@ExtendWith(VaultExtension.class)
class LeaseAwareVaultPropertySourceIntegrationTests {

	@VaultPropertySource(value = { "secret/myapp", "secret/myapp/profile" }, renewal = Renewal.RENEW)
	static class Config extends VaultIntegrationTestConfiguration {

	}

	@VaultPropertySource(value = { "unknown" }, ignoreSecretNotFound = false)
	static class FailingConfig extends VaultIntegrationTestConfiguration {

	}

	@VaultPropertySource(value = { "unknown" }, ignoreSecretNotFound = false, renewal = Renewal.RENEW)
	static class FailingRenewableConfig extends VaultIntegrationTestConfiguration {

	}

	@BeforeAll
	static void beforeClass(VaultInitializer vaultInitializer) {

		VaultOperations vaultOperations = vaultInitializer.prepare().getVaultOperations();

		vaultOperations.write("secret/myapp", Collections.singletonMap("myapp", "myvalue"));
		vaultOperations.write("secret/myapp/profile", Collections.singletonMap("myprofile", "myprofilevalue"));
	}

	@Test
	void shouldLoadProperties() {

		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class,
				PropertyConsumer.class)) {
			ConfigurableEnvironment env = context.getEnvironment();
			PropertyConsumer consumer = context.getBean(PropertyConsumer.class);

			assertThat(env.getProperty("myapp")).isEqualTo("myvalue");
			assertThat(env.getProperty("myprofile")).isEqualTo("myprofilevalue");
			assertThat(consumer.myapp).isEqualTo("myvalue");
		}
	}

	@Test
	void shouldFailIfPropertiesNotFound() {

		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(FailingConfig.class)) {
			fail("AnnotationConfigApplicationContext startup did not fail");
		}
		catch (Exception e) {
			assertThat(e).hasRootCauseInstanceOf(VaultPropertySourceNotFoundException.class)
					.hasMessageContaining("Vault location [unknown] not resolvable");
		}
	}

	@Test
	void shouldFailIfRenewablePropertiesNotFound() {

		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				FailingRenewableConfig.class)) {

			fail("AnnotationConfigApplicationContext startup did not fail");
		}
		catch (Exception e) {
			assertThat(e).hasRootCauseInstanceOf(VaultPropertySourceNotFoundException.class)
					.hasMessageContaining("Vault location [unknown] not resolvable");
		}
	}

	@Component
	static class PropertyConsumer {

		@Value("${myapp}")
		String myapp;

	}

}
