/*
 * Copyright 2017-2018 the original author or authors.
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
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.vault.support.Policy.Rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Policy} JSON serialization/deserialization.
 *
 * @author Mark Paluch
 */
public class PolicySerializationUnitTests {

	ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void shouldSerialize() throws Exception {

		Rule rule = Rule.builder().path("secret/*")
				.capabilities("create", "read", "update")
				.allowedParameter("ttl", "1h", "2h").deniedParameter("password").build();

		Rule another = Rule.builder().path("secret/foo")
				.capabilities("create", "read", "update", "delete", "list")
				.minWrappingTtl(Duration.ofMinutes(1))
				.maxWrappingTtl(Duration.ofHours(1)).allowedParameter("ttl", "1h", "2h")
				.deniedParameter("password").build();

		Policy policy = Policy.of(rule, another);

		try (InputStream is = new ClassPathResource("policy.json").getInputStream()) {

			String expected = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
			JSONAssert.assertEquals(expected, objectMapper.writeValueAsString(policy),
					JSONCompareMode.STRICT);
		}
	}

	@Test
	public void shouldDeserialize() throws Exception {

		Rule rule = Rule.builder().path("secret/*")
				.capabilities("create", "read", "update", "update")
				.allowedParameter("ttl", "1h", "2h").deniedParameter("password").build();

		Rule another = Rule.builder().path("secret/foo")
				.capabilities("create", "read", "update", "delete", "list")
				.minWrappingTtl(Duration.ofMinutes(1))
				.maxWrappingTtl(Duration.ofHours(1)).allowedParameter("ttl", "1h", "2h")
				.allowedParameter("ttl", "1h", "2h").deniedParameter("password").build();

		Policy expected = Policy.of(rule, another);

		try (InputStream is = new ClassPathResource("policy.json").getInputStream()) {

			Policy actual = objectMapper.readValue(is, Policy.class);

			assertThat(actual.getRules()).hasSameClassAs(expected.getRules());

			Rule secretAll = actual.getRule("secret/*");

			assertThat(secretAll.getPath()).isEqualTo(rule.getPath());
			assertThat(secretAll.getCapabilities()).isEqualTo(rule.getCapabilities());
			assertThat(secretAll.getAllowedParameters()).isEqualTo(
					rule.getAllowedParameters());
			assertThat(secretAll.getDeniedParameters()).isEqualTo(
					rule.getDeniedParameters());

			Rule secretFoo = actual.getRule("secret/foo");

			assertThat(secretFoo.getPath()).isEqualTo(another.getPath());
			assertThat(secretFoo.getCapabilities()).isEqualTo(another.getCapabilities());
			assertThat(secretFoo.getMinWrappingTtl()).isEqualTo(
					another.getMinWrappingTtl());
			assertThat(secretFoo.getMaxWrappingTtl()).isEqualTo(
					another.getMaxWrappingTtl());
			assertThat(secretFoo.getAllowedParameters()).isEqualTo(
					another.getAllowedParameters());
			assertThat(secretFoo.getDeniedParameters()).isEqualTo(
					another.getDeniedParameters());
		}
	}

	@Test
	public void shouldDeserializeEmptyPolicy() throws Exception {

		assertThat(objectMapper.readValue("{}", Policy.class)).isEqualTo(Policy.empty());
	}

	@Test
	public void shouldRejectUnknownFieldNames() throws Exception {

		assertThatThrownBy(
				() -> objectMapper.readValue("{\"foo\":1, \"path\": {} }", Policy.class))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(
				() -> objectMapper.readValue("{\"foo\":\"bar\"}", Policy.class))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void shouldDeserializePolicyWithEmptyRules() throws Exception {

		Policy actual = objectMapper.readValue("{ \"path\": {} }", Policy.class);

		assertThat(actual).isEqualTo(Policy.empty());
	}

	@Test
	public void shouldDeserializeRuleWithHour() throws Exception {

		Policy actual = objectMapper.readValue(
				"{ \"path\": { \"secret\" : {\"min_wrapping_ttl\":\"1h\"} } }",
				Policy.class);

		Rule rule = actual.getRule("secret");
		assertThat(rule.getMinWrappingTtl()).isEqualTo(Duration.ofHours(1));
	}
}
