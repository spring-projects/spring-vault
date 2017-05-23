/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.core.lease;

import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.env.LeaseAwareVaultPropertySource;
import org.springframework.vault.core.lease.RotatingGenericSecretsIntegrationTestConfiguration.PropertySourceHolder;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.util.PrepareVault;
import org.springframework.vault.util.VaultRule;

/**
 * Integration tests for {@link SecretLeaseContainer} using the {@code generic} backend.
 *
 * @author Steven Swor
 */
@Ignore("Need to fix race conditions")
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { VaultIntegrationTestConfiguration.class,
		RotatingGenericSecretsIntegrationTestConfiguration.class })
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class SecretLeaseContainerIntegrationTests {

	@Autowired
	private VaultOperations vaultOperations;

	@Autowired
	private PropertySourceHolder propertySourceHolder;

	private static PrepareVault prepareVault;

	@BeforeClass
	public static void beforeClass() {

		VaultRule rule = new VaultRule();
		rule.before();

		prepareVault = rule.prepare();

		VaultOperations vaultOperations = prepareVault.getVaultOperations();
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("hello", "world");
		data.put("ttl", "5s");

		vaultOperations.write("secret/rotating", data);

		VaultResponseSupport secretInfo = vaultOperations.read("secret/rotating");
		assertThat(secretInfo).isNotNull();
		assertThat(secretInfo.getLeaseId()).isNotNull().isEmpty();
		assertThat(secretInfo.getLeaseDuration()).isEqualTo(5);
	}

	@AfterClass
	public static void afterClass() {
		if (prepareVault != null) {
			prepareVault.getVaultOperations().delete("secret/rotating");
		}
	}

	@Test
	public void shouldRotateGenericSecrets() throws Exception {

		LeaseAwareVaultPropertySource propertySource = propertySourceHolder.getPropertySource();
		assertThat(propertySource.getProperty("generic.rotating.hello")).isNotNull()
				.isEqualTo("world");

		Map<String, Object> data = new HashMap<String, Object>();
		data.put("hello", "foo");
		data.put("ttl", "5s");
		vaultOperations.write("secret/rotating", data);

		Thread.sleep(10000L);

		assertThat(propertySource.getProperty("generic.rotating.hello")).isNotNull()
				.isEqualTo("foo");

	}
}
