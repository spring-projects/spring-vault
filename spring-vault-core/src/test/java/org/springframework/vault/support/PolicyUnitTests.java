/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.support;

import org.junit.jupiter.api.Test;

import org.springframework.vault.support.Policy.BuiltinCapabilities;
import org.springframework.vault.support.Policy.Rule;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Policy}.
 *
 * @author Mark Paluch
 */
class PolicyUnitTests {

	@Test
	void withShouldAddRuleToEmptyPolicy() {

		Rule rule = Rule.builder().path("secret/").capabilities(BuiltinCapabilities.READ).build();

		Policy policy = Policy.empty().with(rule);

		assertThat(policy.getRules()).containsExactly(rule);
		assertThat(policy.getRule("secret/")).isSameAs(rule);
	}

	@Test
	void withShouldReplaceExistingRuleAtSamePath() {

		Rule permissive = Rule.builder()
				.path("secret/")
				.capabilities(BuiltinCapabilities.CREATE, BuiltinCapabilities.READ, BuiltinCapabilities.UPDATE,
						BuiltinCapabilities.DELETE, BuiltinCapabilities.LIST, BuiltinCapabilities.SUDO)
				.build();

		Rule restrictive = Rule.builder().path("secret/").capabilities(BuiltinCapabilities.READ).build();

		Policy policy = Policy.of(permissive).with(restrictive);

		assertThat(policy.getRules()).hasSize(1);
		Rule actual = policy.getRule("secret/");
		assertThat(actual).isNotNull();
		assertThat(actual.getCapabilities()).containsExactly(BuiltinCapabilities.READ);
		assertThat(actual.getCapabilities()).doesNotContain(BuiltinCapabilities.SUDO, BuiltinCapabilities.DELETE,
				BuiltinCapabilities.UPDATE, BuiltinCapabilities.CREATE);
	}

	@Test
	void withShouldPreserveOtherRulesWhenReplacingAtExistingPath() {

		Rule admin = Rule.builder()
				.path("secret/admin")
				.capabilities(BuiltinCapabilities.SUDO, BuiltinCapabilities.CREATE)
				.build();

		Rule permissive = Rule.builder()
				.path("secret/data")
				.capabilities(BuiltinCapabilities.SUDO, BuiltinCapabilities.DELETE)
				.build();

		Rule restrictive = Rule.builder().path("secret/data").capabilities(BuiltinCapabilities.READ).build();

		Policy policy = Policy.of(admin, permissive).with(restrictive);

		assertThat(policy.getRules()).hasSize(2);
		assertThat(policy.getRule("secret/admin").getCapabilities())
				.containsExactly(BuiltinCapabilities.SUDO, BuiltinCapabilities.CREATE);
		assertThat(policy.getRule("secret/data").getCapabilities()).containsExactly(BuiltinCapabilities.READ);
	}

	@Test
	void withShouldNotMutateOriginalPolicy() {

		Rule permissive = Rule.builder()
				.path("secret/")
				.capabilities(BuiltinCapabilities.SUDO, BuiltinCapabilities.DELETE)
				.build();

		Policy original = Policy.of(permissive);
		Policy updated = original.with(
				Rule.builder().path("secret/").capabilities(BuiltinCapabilities.READ).build());

		assertThat(original.getRule("secret/").getCapabilities()).contains(BuiltinCapabilities.SUDO);
		assertThat(updated.getRule("secret/").getCapabilities()).containsExactly(BuiltinCapabilities.READ);
	}

}
