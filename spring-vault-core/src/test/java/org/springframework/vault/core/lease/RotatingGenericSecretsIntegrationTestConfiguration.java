/*
 * Copyright 2017-2022 the original author or authors.
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

import java.util.Map;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.vault.annotation.VaultPropertySource;
import org.springframework.vault.core.env.LeaseAwareVaultPropertySource;

/**
 * Test configuration for integration testing the rotation of generic secrets.
 *
 * @author Steven Swor
 */
@Configuration
@VaultPropertySource(propertyNamePrefix = "generic.rotating.", value = "versioned/rotating",
		renewal = VaultPropertySource.Renewal.ROTATE)
public class RotatingGenericSecretsIntegrationTestConfiguration {

	/**
	 * Utility class which will give our tests a reference to the
	 * {@link LeaseAwareVaultPropertySource} which holds our secrets.
	 */

	public static class PropertySourceHolder implements InitializingBean {

		@Autowired
		ApplicationContext appContext;

		LeaseAwareVaultPropertySource propertySource;

		/**
		 * Searches the {@link ApplicationContext} for the
		 * {@link LeaseAwareVaultPropertySource} corresponding to the secret path we are
		 * testing.
		 * @throws Exception if bad things happen (for example, if the property source
		 * does not exist).
		 */
		@Override
		public void afterPropertiesSet() throws Exception {
			Assert.notNull(this.appContext, "application context must be set");
			Map<String, LeaseAwareVaultPropertySource> leaseAwareVaultPropertySources = this.appContext
					.getBeansOfType(LeaseAwareVaultPropertySource.class);
			for (LeaseAwareVaultPropertySource candidate : leaseAwareVaultPropertySources.values()) {
				if (candidate.getRequestedSecret().getPath().equals("versioned/rotating")) {
					this.propertySource = candidate;
					break;
				}
			}
			Assert.notNull(this.propertySource, "Vault property source for generic secret not found");
		}

		/**
		 * Gets the property source for our tested secrets.
		 * @return the property source for our tested secrets
		 */
		public LeaseAwareVaultPropertySource getPropertySource() {
			return this.propertySource;
		}

	}

	/**
	 * Creates a {@link PropertySourceHolder}.
	 * @return the {@link PropertySourceHolder}
	 */
	@Bean
	public PropertySourceHolder propertySourceHolder() {
		return new PropertySourceHolder();
	}

}
