/*
 * Copyright 2016-2020 the original author or authors.
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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.util.VaultRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link VaultPropertySource}.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
public class VaultPropertySourceIntegrationTests {

	@Import({ Partial1.class, Partial2.class })
	static class Config extends VaultIntegrationTestConfiguration {
	}

	@VaultPropertySource({ "secret/myapp", "secret/myapp/profile" })
	static class Partial1 {
	}

	@VaultPropertySource("secret/generic")
	static class Partial2 {
	}

	@Autowired
	Environment env;
	@Autowired
	ApplicationContext context;
	@Value("${myapp}")
	String myapp;

	@BeforeClass
	public static void beforeClass() {

		VaultRule rule = new VaultRule();
		rule.before();

		VaultOperations vaultOperations = rule.prepare().getVaultOperations();

		vaultOperations.write("secret/myapp",
				Collections.singletonMap("myapp", "myvalue"));
		vaultOperations.write("secret/generic",
				Collections.singletonMap("generic", "generic-value"));
		vaultOperations.write("secret/myapp/profile",
				Collections.singletonMap("myprofile", "myprofilevalue"));
	}

	@Test
	public void environmentShouldResolveProperties() {

		assertThat(env.getProperty("myapp")).isEqualTo("myvalue");
		assertThat(env.getProperty("myprofile")).isEqualTo("myprofilevalue");
		assertThat(env.getProperty("generic")).isEqualTo("generic-value");
	}

	@Test
	public void valueShouldInjectProperty() {
		assertThat(myapp).isEqualTo("myvalue");
	}
}
