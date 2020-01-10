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
package org.springframework.vault.demo;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.stereotype.Component;
import org.springframework.vault.annotation.VaultPropertySource;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.core.VaultIntegrationTestConfiguration;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.util.PrepareVault;
import org.springframework.vault.util.VaultInitializer;

/**
 * This application uses {@link PropertySources} to define static config files and
 * {@link VaultPropertySource} to retrieve properties from Vault.
 * <p>
 * {@code secure-introduction.properties} externalizes Vault login credentials to keep
 * authentication details outside the code.
 * <p>
 * {@code other.properties} references a Vault property to illustrate possible integration
 * with Spring Vault's property source support.
 *
 * @author Mark Paluch
 */
public class SecurePropertyUsage {

	public static void main(String[] args) {

		VaultInitializer initializer = new VaultInitializer();
		initializer.initialize();

		PrepareVault prepareVault = initializer.prepare();
		VaultOperations vaultOperations = prepareVault.getVaultOperations();

		Map<String, String> data = new HashMap<String, String>();
		data.put("encrypted", "Much secret. Very confidential. Wow.");

		vaultOperations.write("secret/secure-introduction", data);

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				Config.class);

		System.out.println(context.getEnvironment()
				.getProperty("my-property-that-references-vault"));
		System.out.println(context.getEnvironment().getProperty("encrypted"));

		System.out.println(context.getBean(Client.class).myValue);

		context.stop();
	}

	@PropertySources({
			@PropertySource("classpath:/org/springframework/vault/demo/secure-introduction.properties"),
			@PropertySource("classpath:/org/springframework/vault/demo/other.properties") })
	@VaultPropertySource({ "secret/secure-introduction" })
	@Configuration
	@ComponentScan
	static class Config extends VaultIntegrationTestConfiguration {

		@Override
		public ClientAuthentication clientAuthentication() {
			return new TokenAuthentication(getEnvironment().getProperty("vault.token"));
		}
	}

	@Component
	static class Client {

		@Value("${encrypted}")
		String myValue;

	}
}
