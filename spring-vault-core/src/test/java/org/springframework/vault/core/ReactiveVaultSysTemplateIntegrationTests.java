/*
 * Copyright 2016-2024 the original author or authors.
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;

import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link ReactiveVaultSysTemplate} through
 * {@link ReactiveVaultSysOperations}.
 *
 * @author Mark Paluch
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
class ReactiveVaultSysTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired
	ReactiveVaultOperations vaultOperations;

	ReactiveVaultSysOperations adminOperations;

	@BeforeEach
	void before() {
		this.adminOperations = this.vaultOperations.opsForSys();
	}

	@Test
	@RequiresVaultVersion("0.6.1")
	void shouldReportHealth() {

		this.adminOperations.health().as(StepVerifier::create).assertNext(health -> {
			assertThat(health.isInitialized()).isTrue();
			assertThat(health.isSealed()).isFalse();
			assertThat(health.isPerformanceStandby()).isFalse();
			assertThat(health.isRecoveryReplicationSecondary()).isFalse();
			assertThat(health.isStandby()).isFalse();
		}).verifyComplete();
	}

	@Test
	void isInitializedShouldReturnTrue() {
		this.adminOperations.isInitialized().as(StepVerifier::create).expectNext(true).verifyComplete();
	}

}
