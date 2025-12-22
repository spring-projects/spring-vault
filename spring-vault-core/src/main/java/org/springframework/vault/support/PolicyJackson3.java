/*
 * Copyright 2025 the original author or authors.
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

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.util.Converter;

import org.springframework.util.Assert;

/**
 * Jackson 3 serializers and deserializers for {@link Policy}.
 *
 * @author Mark Paluch
 * @since 4.0
 */
class PolicyJackson3 {

	static class PolicySerializer extends ValueSerializer<Policy> {

		@Override
		public void serialize(Policy value, JsonGenerator gen, SerializationContext serializers) {
			gen.writeStartObject();
			gen.writeName("path");
			gen.writeStartObject();
			for (Policy.Rule rule : value.getRules()) {
				gen.writePOJOProperty(rule.getPath(), rule);
			}
			gen.writeEndObject();
			gen.writeEndObject();
		}

	}


	static class PolicyDeserializer extends ValueDeserializer<Policy> {

		@Override
		public Policy deserialize(JsonParser p, DeserializationContext ctxt) {
			Assert.isTrue(p.currentToken() == JsonToken.START_OBJECT,
					"Expected START_OBJECT, got: " + p.currentToken());
			String fieldName = p.nextName();
			Set<Policy.Rule> rules = new LinkedHashSet<>();
			if ("path".equals(fieldName)) {
				p.nextToken();
				Assert.isTrue(p.currentToken() == JsonToken.START_OBJECT,
						"Expected START_OBJECT, got: " + p.currentToken());
				p.nextToken();
				while (p.currentToken() == JsonToken.PROPERTY_NAME) {
					String path = p.currentName();
					p.nextToken();
					Assert.isTrue(p.currentToken() == JsonToken.START_OBJECT,
							"Expected START_OBJECT, got: " + p.currentToken());
					Policy.Rule rule = p.objectReadContext().readValue(p, Policy.Rule.class);
					rules.add(rule.withPath(path));
					JsonToken jsonToken = p.nextToken();
					if (jsonToken == JsonToken.END_OBJECT) {
						break;
					}
				}
				Assert.isTrue(p.currentToken() == JsonToken.END_OBJECT,
						"Expected END_OBJECT, got: " + p.currentToken());
				p.nextToken();
			}
			Assert.isTrue(p.currentToken() == JsonToken.END_OBJECT, "Expected END_OBJECT, got: " + p.currentToken());
			return Policy.of(rules);
		}

	}


	static class CapabilityToStringConverter implements Converter<Policy.Capability, String> {

		@Override
		public String convert(DeserializationContext ctxt, Policy.Capability value) {
			return value.name().toLowerCase();
		}

		@Override
		public String convert(SerializationContext ctxt, Policy.Capability value) {
			return value.name().toLowerCase();
		}

		@Override
		public JavaType getInputType(TypeFactory typeFactory) {
			return typeFactory.constructType(Policy.Capability.class);
		}

		@Override
		public JavaType getOutputType(TypeFactory typeFactory) {
			return typeFactory.constructType(String.class);
		}

	}


	static class StringToCapabilityConverter implements Converter<String, Policy.Capability> {

		@Override
		public Policy.Capability convert(DeserializationContext ctxt, String value) {
			return convert(value);
		}

		@Override
		public Policy.Capability convert(SerializationContext ctxt, String value) {
			return convert(value);
		}

		public Policy.Capability convert(String value) {
			Policy.Capability capability = Policy.BuiltinCapabilities.find(value);
			return capability != null ? capability : () -> value;
		}

		@Override
		public JavaType getInputType(TypeFactory typeFactory) {
			return typeFactory.constructType(String.class);
		}

		@Override
		public JavaType getOutputType(TypeFactory typeFactory) {
			return typeFactory.constructType(Policy.Capability.class);
		}

	}


	static class DurationToStringConverter implements Converter<Duration, String> {

		@Override
		public String convert(DeserializationContext ctxt, Duration value) {
			return convert(value);
		}

		@Override
		public String convert(SerializationContext ctxt, Duration value) {
			return convert(value);
		}

		public String convert(Duration value) {
			return "" + value.getSeconds();
		}

		@Override
		public JavaType getInputType(TypeFactory typeFactory) {
			return typeFactory.constructType(Duration.class);
		}

		@Override
		public JavaType getOutputType(TypeFactory typeFactory) {
			return typeFactory.constructType(String.class);
		}

	}


	static class StringToDurationConverter implements Converter<String, Duration> {

		static Pattern SECONDS = Pattern.compile("(\\d+)s");

		static Pattern MINUTES = Pattern.compile("(\\d+)m");

		static Pattern HOURS = Pattern.compile("(\\d+)h");


		@Override
		public Duration convert(DeserializationContext ctxt, String value) {
			return convert(value);
		}

		@Override
		public Duration convert(SerializationContext ctxt, String value) {
			return convert(value);
		}

		public Duration convert(String value) {

			try {
				return Duration.ofSeconds(Long.parseLong(value));
			} catch (NumberFormatException e) {
				Matcher matcher = SECONDS.matcher(value);
				if (matcher.matches()) {
					return Duration.ofSeconds(Long.parseLong(matcher.group(1)));
				}
				matcher = MINUTES.matcher(value);
				if (matcher.matches()) {
					return Duration.ofMinutes(Long.parseLong(matcher.group(1)));
				}
				matcher = HOURS.matcher(value);
				if (matcher.matches()) {
					return Duration.ofHours(Long.parseLong(matcher.group(1)));
				}
				throw new IllegalArgumentException("Unsupported duration value: " + value);
			}
		}

		@Override
		public JavaType getInputType(TypeFactory typeFactory) {
			return typeFactory.constructType(String.class);
		}

		@Override
		public JavaType getOutputType(TypeFactory typeFactory) {
			return typeFactory.constructType(Policy.Capability.class);
		}

	}

}
