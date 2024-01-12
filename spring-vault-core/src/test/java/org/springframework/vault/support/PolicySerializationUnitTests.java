/*
 * Copyright 2017-2024 the original author or authors.
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.vault.support.Policy.Rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link Policy} JSON serialization/deserialization.
 *
 * @author Mark Paluch
 */
class PolicySerializationUnitTests {

	ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.get();

	@Test
	void shouldSerialize() throws Exception {

		Rule rule = Rule.builder()
			.path("secret/*")
			.capabilities("create", "read", "update")
			.allowedParameter("ttl", "1h", "2h")
			.deniedParameter("password")
			.build();

		Rule another = Rule.builder()
			.path("secret/foo")
			.capabilities("create", "read", "update", "delete", "list")
			.minWrappingTtl(Duration.ofMinutes(1))
			.maxWrappingTtl(Duration.ofHours(1))
			.allowedParameter("ttl", "1h", "2h")
			.deniedParameter("password")
			.build();

		Policy policy = Policy.of(rule, another);

		try (InputStream is = new ClassPathResource("policy.json").getInputStream()) {

			String expected = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
			JSONAssert.assertEquals(expected, this.OBJECT_MAPPER.writeValueAsString(policy), JSONCompareMode.STRICT);
		}
	}

	@Test
	void shouldDeserialize() throws Exception {

		Rule rule = Rule.builder()
			.path("secret/*")
			.capabilities("create", "read", "update", "update")
			.allowedParameter("ttl", "1h", "2h")
			.deniedParameter("password")
			.build();

		Rule another = Rule.builder()
			.path("secret/foo")
			.capabilities("create", "read", "update", "delete", "list")
			.minWrappingTtl(Duration.ofMinutes(1))
			.maxWrappingTtl(Duration.ofHours(1))
			.allowedParameter("ttl", "1h", "2h")
			.allowedParameter("ttl", "1h", "2h")
			.deniedParameter("password")
			.build();

		Policy expected = Policy.of(rule, another);

		try (InputStream is = new ClassPathResource("policy.json").getInputStream()) {

			Policy actual = this.OBJECT_MAPPER.readValue(is, Policy.class);

			assertThat(actual.getRules()).hasSameClassAs(expected.getRules());

			Rule secretAll = actual.getRule("secret/*");

			assertThat(secretAll.getPath()).isEqualTo(rule.getPath());
			assertThat(secretAll.getCapabilities()).isEqualTo(rule.getCapabilities());
			assertThat(secretAll.getAllowedParameters()).isEqualTo(rule.getAllowedParameters());
			assertThat(secretAll.getDeniedParameters()).isEqualTo(rule.getDeniedParameters());

			Rule secretFoo = actual.getRule("secret/foo");

			assertThat(secretFoo.getPath()).isEqualTo(another.getPath());
			assertThat(secretFoo.getCapabilities()).isEqualTo(another.getCapabilities());
			assertThat(secretFoo.getMinWrappingTtl()).isEqualTo(another.getMinWrappingTtl());
			assertThat(secretFoo.getMaxWrappingTtl()).isEqualTo(another.getMaxWrappingTtl());
			assertThat(secretFoo.getAllowedParameters()).isEqualTo(another.getAllowedParameters());
			assertThat(secretFoo.getDeniedParameters()).isEqualTo(another.getDeniedParameters());
		}
	}

	@Test
	void shouldDeserializeEmptyPolicy() throws Exception {

		assertThat(this.OBJECT_MAPPER.readValue("{}", Policy.class)).isEqualTo(Policy.empty());
	}

	@Test
	void shouldRejectUnknownFieldNames() throws Exception {

		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.OBJECT_MAPPER.readValue("{\"foo\":1, \"path\": {} }", Policy.class));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.OBJECT_MAPPER.readValue("{\"foo\":\"bar\"}", Policy.class));
	}

	@Test
	void shouldDeserializePolicyWithEmptyRules() throws Exception {

		Policy actual = this.OBJECT_MAPPER.readValue("{ \"path\": {} }", Policy.class);

		assertThat(actual).isEqualTo(Policy.empty());
	}

	@Test
	void shouldDeserializeRuleWithHour() throws Exception {

		Policy actual = this.OBJECT_MAPPER.readValue("{ \"path\": { \"secret\" : {\"min_wrapping_ttl\":\"1h\"} } }",
				Policy.class);

		Rule rule = actual.getRule("secret");
		assertThat(rule.getMinWrappingTtl()).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void crudShouldReturnCrudCapabilities() {

		assertThat(Policy.BuiltinCapabilities.crud()).hasSize(5)
			.contains(Policy.BuiltinCapabilities.CREATE)
			.doesNotContain(Policy.BuiltinCapabilities.SUDO);
	}

	@Test
	void sudoShouldReturnCrudAndSudoCapabilities() {

		assertThat(Policy.BuiltinCapabilities.crudAndSudo()).hasSize(6)
			.contains(Policy.BuiltinCapabilities.CREATE)
			.contains(Policy.BuiltinCapabilities.SUDO);
	}

}
