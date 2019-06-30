/*
 * Copyright 2017-2019 the original author or authors.
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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.support.Policy.PolicyDeserializer;
import org.springframework.vault.support.Policy.PolicySerializer;

/**
 * Value object representing a Vault policy associated with {@link Rule}s. Instances of
 * {@link Policy} support JSON serialization and deserialization using Jackson.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see Rule
 * @see com.fasterxml.jackson.databind.ObjectMapper
 */
@JsonSerialize(using = PolicySerializer.class)
@JsonDeserialize(using = PolicyDeserializer.class)
public class Policy {

	private static final Policy EMPTY = new Policy(Collections.emptySet());

	private final Set<Rule> rules;

	private Policy(Set<Rule> rules) {
		this.rules = rules;
	}

	/**
	 * Create an empty {@link Policy} without rules.
	 *
	 * @return an empty {@link Policy}.
	 */
	public static Policy empty() {
		return EMPTY;
	}

	/**
	 * Create a {@link Policy} from one or more {@code rules}.
	 *
	 * @param rules must not be {@literal null}.
	 * @return the {@link Policy} object containing {@code rules}.
	 */
	public static Policy of(Rule... rules) {

		Assert.notNull(rules, "Rules must not be null");
		Assert.noNullElements(rules, "Rules must not contain null elements");

		return new Policy(new LinkedHashSet<>(Arrays.asList(rules)));
	}

	/**
	 * Create a {@link Policy} from one or more {@code rules}.
	 *
	 * @param rules must not be {@literal null}.
	 * @return the {@link Policy} object containing {@code rules}.
	 */
	public static Policy of(Set<Rule> rules) {

		Assert.notNull(rules, "Rules must not be null");

		return new Policy(new LinkedHashSet<>(rules));
	}

	/**
	 * Create a new {@link Policy} object containing all configured rules and add the
	 * given {@link Rule} to the new policy object. If the given {@link Rule} matches an
	 * existing rule path, the exiting rule will be overridden by the new rule object.
	 *
	 * @param rule must not be {@literal null}.
	 * @return the new {@link Policy} object containing all configured rules and the given
	 * {@link Rule}.
	 */
	public Policy with(Rule rule) {

		Assert.notNull(rule, "Rule must not be null");

		Set<Rule> rules = new LinkedHashSet<>(this.rules.size() + 1);
		rules.addAll(this.rules);
		rules.add(rule);

		return new Policy(rules);
	}

	public Set<Rule> getRules() {
		return rules;
	}

	/**
	 * Lookup a {@link Rule} by its path. Returns {@literal null} if the rule was not
	 * found.
	 *
	 * @param path must not be {@literal null}.
	 * @return the {@link Rule} or {@literal null}, if not found.
	 */
	@Nullable
	public Rule getRule(String path) {

		Assert.notNull(path, "Path must not be null");

		for (Rule rule : rules) {
			if (rule.getPath().equals(path)) {
				return rule;
			}
		}

		return null;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Policy))
			return false;
		Policy policy = (Policy) o;
		return rules.equals(policy.rules);
	}

	@Override
	public int hashCode() {
		return Objects.hash(rules);
	}

	/**
	 * Value object representing a rule for a certain path. Rule equality is considered by
	 * comparing only the path segment to guarante uniqueness within a {@link Set}.
	 *
	 * @author Mark Paluch
	 */
	@JsonInclude(Include.NON_EMPTY)
	public static class Rule {

		/**
		 * Path or path with asterisk to which this rule applies to.
		 */
		@JsonIgnore
		private final String path;

		/**
		 * One or more capabilities which provide fine-grained control over permitted (or
		 * denied) operations.
		 */
		@JsonSerialize(contentConverter = CapabilityToStringConverter.class)
		@JsonDeserialize(contentConverter = StringToCapabilityConverter.class)
		private final List<Capability> capabilities;

		/**
		 * The minimum allowed TTL that clients can specify for a wrapped response. In
		 * practice, setting a minimum TTL of one second effectively makes response
		 * wrapping mandatory for a particular path.
		 */
		@JsonProperty("min_wrapping_ttl")
		@JsonSerialize(converter = DurationToStringConverter.class)
		@Nullable
		private final Duration minWrappingTtl;

		/**
		 * The maximum allowed TTL that clients can specify for a wrapped response.
		 */
		@JsonProperty("max_wrapping_ttl")
		@JsonSerialize(converter = DurationToStringConverter.class)
		@Nullable
		private final Duration maxWrappingTtl;

		/**
		 * Whitelists a list of keys and values that are permitted on the given path.
		 * Setting a parameter with a value of a populated list allows the parameter to
		 * contain only those values.
		 */
		@JsonProperty("allowed_parameters")
		private final Map<String, List<String>> allowedParameters;

		/**
		 * Blacklists a list of parameter and values. Any values specified here take
		 * precedence over {@link #allowedParameters}. Setting a parameter with a value of
		 * a populated list denies any parameter containing those values. Setting to
		 * {@literal *} will deny any parameter.
		 */
		@JsonProperty("denied_parameters")
		private final Map<String, List<String>> deniedParameters;

		@JsonCreator
		private Rule(
				@JsonProperty("capabilities") List<Capability> capabilities,
				@JsonProperty("min_wrapping_ttl") @JsonDeserialize(converter = StringToDurationConverter.class) Duration minWrappingTtl,
				@JsonProperty("max_wrapping_ttl") @JsonDeserialize(converter = StringToDurationConverter.class) Duration maxWrappingTtl,
				@JsonProperty("allowed_parameters") Map<String, List<String>> allowedParameters,
				@JsonProperty("denied_parameters") Map<String, List<String>> deniedParameters) {

			this.path = "";
			this.capabilities = capabilities;
			this.minWrappingTtl = minWrappingTtl;
			this.maxWrappingTtl = maxWrappingTtl;
			this.allowedParameters = allowedParameters;
			this.deniedParameters = deniedParameters;
		}

		private Rule(String path, List<Capability> capabilities,
				@Nullable Duration minWrappingTtl, @Nullable Duration maxWrappingTtl,
				Map<String, List<String>> allowedParameters,
				Map<String, List<String>> deniedParameters) {

			this.path = path;
			this.capabilities = capabilities;
			this.minWrappingTtl = minWrappingTtl;
			this.maxWrappingTtl = maxWrappingTtl;
			this.allowedParameters = allowedParameters;
			this.deniedParameters = deniedParameters;
		}

		/**
		 * Create a new builder for {@link Rule}.
		 *
		 * @return a new {@link RuleBuilder}.
		 */
		public static RuleBuilder builder() {
			return new RuleBuilder();
		}

		private Rule withPath(String path) {
			return new Rule(path, capabilities, minWrappingTtl, maxWrappingTtl,
					allowedParameters, deniedParameters);
		}

		public String getPath() {
			return path;
		}

		public List<Capability> getCapabilities() {
			return capabilities;
		}

		@Nullable
		public Duration getMinWrappingTtl() {
			return minWrappingTtl;
		}

		@Nullable
		public Duration getMaxWrappingTtl() {
			return maxWrappingTtl;
		}

		public Map<String, List<String>> getAllowedParameters() {
			return allowedParameters;
		}

		public Map<String, List<String>> getDeniedParameters() {
			return deniedParameters;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Rule))
				return false;
			Rule rule = (Rule) o;
			return path.equals(rule.path);
		}

		@Override
		public int hashCode() {
			return Objects.hash(path);
		}

		/**
		 * Builder for a {@link Rule}.
		 */
		public static class RuleBuilder {

			private @Nullable String path;

			private Set<Capability> capabilities = new LinkedHashSet<>();

			@Nullable
			private Duration minWrappingTtl;

			@Nullable
			private Duration maxWrappingTtl;

			private Map<String, List<String>> allowedParameters = new LinkedHashMap<String, List<String>>();

			private Map<String, List<String>> deniedParameters = new LinkedHashMap<String, List<String>>();;

			/**
			 * Associate a {@code path} with the rule.
			 *
			 * @param path must not be {@literal null} or empty.
			 * @return {@code this} {@link RuleBuilder}.
			 */
			public RuleBuilder path(String path) {

				Assert.hasText(path, "Path must not be empty");

				this.path = path;
				return this;
			}

			/**
			 * Configure a {@link Capability} for the rule. Capabilities are added when
			 * calling this method and do not replace already configured capabilities.
			 *
			 * @param capability must not be {@literal null}.
			 * @return {@code this} {@link RuleBuilder}.
			 */
			public RuleBuilder capability(Capability capability) {

				Assert.notNull(capability, "Capability must not be null");

				this.capabilities.add(capability);
				return this;
			}

			/**
			 * Configure capabilities. apabilities are added when calling this method and
			 * do not replace already configured capabilities.
			 *
			 * @param capabilities must not be {@literal null}.
			 * @return {@code this} {@link RuleBuilder}.
			 */
			public RuleBuilder capabilities(Capability... capabilities) {

				Assert.notNull(capabilities, "Capabilities must not be null");
				Assert.noNullElements(capabilities,
						"Capabilities must not contain null elements");

				return capabilities(Arrays.asList(capabilities));
			}

			/**
			 * Configure capabilities represented as {@link String} literals. This method
			 * resolves capabilities using {@link BuiltinCapabilities}. Capabilities are
			 * added when calling this method and do not replace already configured
			 * capabilities.
			 *
			 * @param capabilities must not be {@literal null}.
			 * @return {@code this} {@link RuleBuilder}.
			 * @throws IllegalArgumentException if the capability cannot be resolved to a
			 * built-in {@link Capability}.
			 */
			public RuleBuilder capabilities(String... capabilities) {

				Assert.notNull(capabilities, "Capabilities must not be null");
				Assert.noNullElements(capabilities,
						"Capabilities must not contain null elements");

				List<Capability> mapped = Arrays
						.stream(capabilities)
						.map(value -> {

							Capability capability = BuiltinCapabilities.find(value);

							if (capability == null) {
								throw new IllegalArgumentException("Cannot resolve "
										+ value + " to a capability");
							}
							return capability;
						}).collect(Collectors.toList());

				return capabilities(mapped);
			}

			private RuleBuilder capabilities(Iterable<Capability> capabilities) {

				for (Capability capability : capabilities) {
					this.capabilities.add(capability);
				}

				return this;
			}

			/**
			 * Configure a min TTL for response wrapping.
			 *
			 * @param ttl must not be {@literal null}.
			 * @return {@code this} {@link RuleBuilder}.
			 */
			public RuleBuilder minWrappingTtl(Duration ttl) {

				Assert.notNull(ttl, "TTL must not be null");

				this.minWrappingTtl = ttl;
				return this;
			}

			/**
			 * Configure a max TTL for response wrapping.
			 *
			 * @param ttl must not be {@literal null}.
			 * @return {@code this} {@link RuleBuilder}.
			 */
			public RuleBuilder maxWrappingTtl(Duration ttl) {

				Assert.notNull(ttl, "TTL must not be null");

				this.maxWrappingTtl = ttl;
				return this;
			}

			/**
			 * Configure allowed parameter values given {@code name} and {@code values}.
			 * Allowing parameter values replaces previously configured allowed parameter
			 * values. Empty {@code values} allow all values for the given parameter
			 * {@code name}.
			 *
			 * @param name must not be {@literal null} or empty.
			 * @param values must not be {@literal null}.
			 * @return {@code this} {@link RuleBuilder}.
			 */
			public RuleBuilder allowedParameter(String name, String... values) {

				Assert.hasText(name, "Allowed parameter name must not be empty");
				Assert.notNull(values, "Values must not be null");

				this.allowedParameters.put(name, Arrays.asList(values));

				return this;
			}

			/**
			 * Configure denied parameter values given {@code name} and {@code values}.
			 * Denying parameter values replaces previously configured denied parameter
			 * values. Empty {@code values} deny parameter usage.
			 *
			 * @param name must not be {@literal null} or empty.
			 * @param values must not be {@literal null}.
			 * @return {@code this} {@link RuleBuilder}.
			 */
			public RuleBuilder deniedParameter(String name, String... values) {

				Assert.hasText(name, "Denied parameter name must not be empty");
				Assert.notNull(values, "Values must not be null");

				this.deniedParameters.put(name, Arrays.asList(values));

				return this;
			}

			/**
			 * Build the {@link Rule} object. Requires a configured {@link #path(String)}
			 * and at least one {@link #capability(Capability)}.
			 *
			 * @return the new {@link Rule} object.
			 */
			public Rule build() {

				Assert.state(StringUtils.hasText(path), "Path must not be empty");
				Assert.state(!capabilities.isEmpty(),
						"Rule must define one or more capabilities");

				List<Capability> capabilities;
				switch (this.capabilities.size()) {
				case 0:
					capabilities = Collections.emptyList();
					break;
				case 1:
					capabilities = Collections.singletonList(this.capabilities.iterator()
							.next());
					break;
				default:
					capabilities = Collections.unmodifiableList(new ArrayList<>(
							this.capabilities));
				}

				return new Rule(path, capabilities, minWrappingTtl, maxWrappingTtl,
						createMap(this.allowedParameters),
						createMap(this.deniedParameters));
			}

			private Map<String, List<String>> createMap(Map<String, List<String>> source) {

				if (source.isEmpty()) {
					return Collections.emptyMap();
				}

				return Collections.unmodifiableMap(new LinkedHashMap<>(source));
			}
		}
	}

	/**
	 * Capability interface representing capability literals.
	 */
	public interface Capability {

		/**
		 * @return the capability literal.
		 */
		String name();
	}

	/**
	 * Built-in Vault capabilities.
	 */
	public enum BuiltinCapabilities implements Capability {

		/**
		 * Allows creating data at the given path. Very few parts of Vault distinguish
		 * between create and update, so most operations require both create and update
		 * capabilities.
		 */
		CREATE,

		/**
		 * Allows reading the data at the given path.
		 */
		READ,

		/**
		 * Allows change the data at the given path. In most parts of Vault, this
		 * implicitly includes the ability to create the initial value at the path.
		 */
		UPDATE,

		/**
		 * Deprecated: Previous capability literal before it was split into
		 * {@link #CREATE} and {@link #UPDATE}.
		 */
		WRITE,

		/**
		 * Allows deleting the data at the given path.
		 */
		DELETE,

		/**
		 * Allows listing values at the given path. Note that the keys returned by a list
		 * operation are not filtered by policies. Do not encode sensitive information in
		 * key names. Not all backends support listing.
		 */
		LIST,

		/**
		 * Allows access to paths that are root-protected. Tokens are not permitted to
		 * interact with these paths unless they are have the sudo capability (in addition
		 * to the other necessary capabilities for performing an operation against that
		 * path, such as read or delete).
		 */
		SUDO,

		/**
		 * Disallows access. This always takes precedence regardless of any other defined
		 * capabilities, including {@link #SUDO}.
		 */
		DENY;

		/**
		 * Find a {@link Capability} by its name. The name is compared case-insensitive.
		 *
		 * @param value must not be {@literal null}.
		 * @return the {@link Capability} or {@literal null}, if not found.
		 */
		@Nullable
		public static Capability find(String value) {

			for (BuiltinCapabilities cap : values()) {
				if (cap.name().equalsIgnoreCase(value)) {
					return cap;
				}
			}

			return null;
		}
	}

	static class PolicySerializer extends JsonSerializer<Policy> {

		@Override
		public void serialize(Policy value, JsonGenerator gen,
				SerializerProvider serializers) throws IOException {

			gen.writeStartObject();

			gen.writeFieldName("path");
			gen.writeStartObject();

			for (Rule rule : value.getRules()) {
				gen.writeObjectField(rule.path, rule);
			}

			gen.writeEndObject();
			gen.writeEndObject();

		}
	}

	static class PolicyDeserializer extends JsonDeserializer<Policy> {

		@Override
		public Policy deserialize(JsonParser p, DeserializationContext ctxt)
				throws IOException {

			Assert.isTrue(p.getCurrentToken() == JsonToken.START_OBJECT,
					"Expected START_OBJECT, got: " + p.getCurrentToken());

			String fieldName = p.nextFieldName();

			Set<Rule> rules = new LinkedHashSet<>();

			if ("path".equals(fieldName)) {

				p.nextToken();
				Assert.isTrue(p.getCurrentToken() == JsonToken.START_OBJECT,
						"Expected START_OBJECT, got: " + p.getCurrentToken());

				p.nextToken();

				while (p.currentToken() == JsonToken.FIELD_NAME) {

					String path = p.getCurrentName();
					p.nextToken();

					Assert.isTrue(p.getCurrentToken() == JsonToken.START_OBJECT,
							"Expected START_OBJECT, got: " + p.getCurrentToken());

					Rule rule = p.getCodec().readValue(p, Rule.class);
					rules.add(rule.withPath(path));

					JsonToken jsonToken = p.nextToken();
					if (jsonToken == JsonToken.END_OBJECT) {
						break;
					}
				}

				Assert.isTrue(p.getCurrentToken() == JsonToken.END_OBJECT,
						"Expected END_OBJECT, got: " + p.getCurrentToken());
				p.nextToken();
			}

			Assert.isTrue(p.getCurrentToken() == JsonToken.END_OBJECT,
					"Expected END_OBJECT, got: " + p.getCurrentToken());
			return Policy.of(rules);
		}
	}

	static class CapabilityToStringConverter implements Converter<Capability, String> {

		@Override
		public String convert(Capability value) {
			return value.name().toLowerCase();
		}

		@Override
		public JavaType getInputType(TypeFactory typeFactory) {
			return typeFactory.constructType(Capability.class);
		}

		@Override
		public JavaType getOutputType(TypeFactory typeFactory) {
			return typeFactory.constructType(String.class);
		}
	}

	static class StringToCapabilityConverter implements Converter<String, Capability> {

		@Override
		public Capability convert(String value) {

			Capability capability = BuiltinCapabilities.find(value);

			return capability != null ? capability : () -> value;
		}

		@Override
		public JavaType getInputType(TypeFactory typeFactory) {
			return typeFactory.constructType(String.class);
		}

		@Override
		public JavaType getOutputType(TypeFactory typeFactory) {
			return typeFactory.constructType(Capability.class);
		}
	}

	static class DurationToStringConverter implements Converter<Duration, String> {

		@Override
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
		public Duration convert(String value) {

			try {
				return Duration.ofSeconds(Long.parseLong(value));
			}
			catch (NumberFormatException e) {

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
			return typeFactory.constructType(Capability.class);
		}
	}
}
