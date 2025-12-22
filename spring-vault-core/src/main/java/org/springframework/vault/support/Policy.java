/*
 * Copyright 2017-2025 the original author or authors.
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
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Value object representing a Vault policy associated with {@link Rule}s.
 * Instances of {@link Policy} support JSON serialization and deserialization
 * using Jackson.
 *
 * @author Mark Paluch
 * @see Rule
 * @see ObjectMapper
 * @since 2.0
 */
@JsonSerialize(using = PolicyJackson3.PolicySerializer.class)
@JsonDeserialize(using = PolicyJackson3.PolicyDeserializer.class)
@com.fasterxml.jackson.databind.annotation.JsonSerialize(using = PolicyJackson2.PolicySerializer.class)
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = PolicyJackson2.PolicyDeserializer.class)
public class Policy {

	private static final Policy EMPTY = new Policy(Collections.emptySet());


	private final Set<Rule> rules;


	private Policy(Set<Rule> rules) {
		this.rules = rules;
	}


	/**
	 * Create an empty {@link Policy} without rules.
	 * @return an empty {@link Policy}.
	 */
	public static Policy empty() {
		return EMPTY;
	}

	/**
	 * Create a {@link Policy} from one or more {@code rules}.
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
	 * @param rules must not be {@literal null}.
	 * @return the {@link Policy} object containing {@code rules}.
	 */
	public static Policy of(Set<Rule> rules) {
		Assert.notNull(rules, "Rules must not be null");
		return new Policy(new LinkedHashSet<>(rules));
	}


	/**
	 * Create a new {@link Policy} object containing all configured rules and add
	 * the given {@link Rule} to the new policy object. If the given {@link Rule}
	 * matches an existing rule path, the exiting rule will be overridden by the new
	 * rule object.
	 * @param rule must not be {@literal null}.
	 * @return the new {@link Policy} object containing all configured rules and the
	 * given {@link Rule}.
	 */
	public Policy with(Rule rule) {
		Assert.notNull(rule, "Rule must not be null");
		Set<Rule> rules = new LinkedHashSet<>(this.rules.size() + 1);
		rules.addAll(this.rules);
		rules.add(rule);
		return new Policy(rules);
	}

	public Set<Rule> getRules() {
		return this.rules;
	}

	/**
	 * Lookup a {@link Rule} by its path. Returns {@literal null} if the rule was
	 * not found.
	 * @param path must not be {@literal null}.
	 * @return the {@link Rule} or {@literal null}, if not found.
	 */
	public @Nullable Rule getRule(String path) {
		Assert.notNull(path, "Path must not be null");
		for (Rule rule : this.rules) {
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
		if (!(o instanceof Policy policy))
			return false;
		return this.rules.equals(policy.rules);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.rules);
	}


	/**
	 * Value object representing a rule for a certain path. Rule equality is
	 * considered by comparing only the path segment to guarantee uniqueness within
	 * a {@link Set}.
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
		 * One or more capabilities which provide fine-grained control over permitted
		 * (or denied) operations.
		 */
		@JsonSerialize(contentConverter = PolicyJackson3.CapabilityToStringConverter.class)
		@JsonDeserialize(contentConverter = PolicyJackson3.StringToCapabilityConverter.class)
		@com.fasterxml.jackson.databind.annotation.JsonSerialize(contentConverter = PolicyJackson2.CapabilityToStringConverter.class)
		@com.fasterxml.jackson.databind.annotation.JsonDeserialize(contentConverter = PolicyJackson2.StringToCapabilityConverter.class)
		private final List<Capability> capabilities;

		/**
		 * The minimum allowed TTL that clients can specify for a wrapped response. In
		 * practice, setting a minimum TTL of one second effectively makes response
		 * wrapping mandatory for a particular path.
		 */
		@JsonProperty("min_wrapping_ttl")
		@JsonSerialize(converter = PolicyJackson3.DurationToStringConverter.class)
		@com.fasterxml.jackson.databind.annotation.JsonSerialize(converter = PolicyJackson2.DurationToStringConverter.class)
		private final @Nullable Duration minWrappingTtl;

		/**
		 * The maximum allowed TTL that clients can specify for a wrapped response.
		 */
		@JsonProperty("max_wrapping_ttl")
		@JsonSerialize(converter = PolicyJackson3.DurationToStringConverter.class)
		@com.fasterxml.jackson.databind.annotation.JsonSerialize(converter = PolicyJackson2.DurationToStringConverter.class)
		private final @Nullable Duration maxWrappingTtl;

		/**
		 * Whitelists a list of keys and values that are permitted on the given path.
		 * Setting a parameter with a value of a populated list allows the parameter to
		 * contain only those values.
		 */
		@JsonProperty("allowed_parameters")
		private final Map<String, List<String>> allowedParameters;

		/**
		 * Blacklists a list of parameter and values. Any values specified here take
		 * precedence over {@link #allowedParameters}. Setting a parameter with a value
		 * of a populated list denies any parameter containing those values. Setting to
		 * {@literal *} will deny any parameter.
		 */
		@JsonProperty("denied_parameters")
		private final Map<String, List<String>> deniedParameters;


		@JsonCreator
		private Rule(@JsonProperty("capabilities") List<Capability> capabilities,
				@JsonProperty("min_wrapping_ttl") @JsonDeserialize(converter = PolicyJackson3.StringToDurationConverter.class) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(converter = PolicyJackson2.StringToDurationConverter.class) Duration minWrappingTtl,
				@JsonProperty("max_wrapping_ttl") @JsonDeserialize(converter = PolicyJackson3.StringToDurationConverter.class) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(converter = PolicyJackson2.StringToDurationConverter.class) Duration maxWrappingTtl,
				@JsonProperty("allowed_parameters") Map<String, List<String>> allowedParameters,
				@JsonProperty("denied_parameters") Map<String, List<String>> deniedParameters) {
			this.path = "";
			this.capabilities = capabilities;
			this.minWrappingTtl = minWrappingTtl;
			this.maxWrappingTtl = maxWrappingTtl;
			this.allowedParameters = allowedParameters;
			this.deniedParameters = deniedParameters;
		}

		private Rule(String path, List<Capability> capabilities, @Nullable Duration minWrappingTtl,
				@Nullable Duration maxWrappingTtl, Map<String, List<String>> allowedParameters,
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
		 * @return a new {@link RuleBuilder}.
		 */
		public static RuleBuilder builder() {
			return new RuleBuilder();
		}


		Rule withPath(String path) {
			return new Rule(path, this.capabilities, this.minWrappingTtl, this.maxWrappingTtl, this.allowedParameters,
					this.deniedParameters);
		}

		public String getPath() {
			return this.path;
		}

		public List<Capability> getCapabilities() {
			return this.capabilities;
		}

		public @Nullable Duration getMinWrappingTtl() {
			return this.minWrappingTtl;
		}

		public @Nullable Duration getMaxWrappingTtl() {
			return this.maxWrappingTtl;
		}

		public Map<String, List<String>> getAllowedParameters() {
			return this.allowedParameters;
		}

		public Map<String, List<String>> getDeniedParameters() {
			return this.deniedParameters;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Rule rule))
				return false;
			return this.path.equals(rule.path);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.path);
		}


		/**
		 * Builder for a {@link Rule}.
		 */
		public static class RuleBuilder {

			private @Nullable String path;

			private final Set<Capability> capabilities = new LinkedHashSet<>();

			private @Nullable Duration minWrappingTtl;

			private @Nullable Duration maxWrappingTtl;

			private final Map<String, List<String>> allowedParameters = new LinkedHashMap<>();

			private final Map<String, List<String>> deniedParameters = new LinkedHashMap<>();


			/**
			 * Associate a {@code path} with the rule.
			 * @param path must not be {@literal null} or empty.
			 * @return this builder.
			 */
			public RuleBuilder path(String path) {
				Assert.hasText(path, "Path must not be empty");
				this.path = path;
				return this;
			}

			/**
			 * Configure a {@link Capability} for the rule. Capabilities are added when
			 * calling this method and do not replace already configured capabilities.
			 * @param capability must not be {@literal null}.
			 * @return this builder.
			 */
			public RuleBuilder capability(Capability capability) {
				Assert.notNull(capability, "Capability must not be null");
				this.capabilities.add(capability);
				return this;
			}

			/**
			 * Configure capabilities. Capabilities are added when calling this method and
			 * do not replace already configured capabilities.
			 * @param capabilities must not be {@literal null}.
			 * @return this builder.
			 */
			public RuleBuilder capabilities(Capability... capabilities) {
				Assert.notNull(capabilities, "Capabilities must not be null");
				Assert.noNullElements(capabilities, "Capabilities must not contain null elements");
				return capabilities(Arrays.asList(capabilities));
			}

			/**
			 * Configure capabilities. Capabilities are added when calling this method and
			 * do not replace already configured capabilities.
			 * @param capabilities must not be {@literal null}.
			 * @return this builder.
			 * @since 3.1
			 */
			public RuleBuilder capabilities(Collection<? extends Capability> capabilities) {
				Assert.notNull(capabilities, "Capabilities must not be null");
				Assert.noNullElements(capabilities, "Capabilities must not contain null elements");
				this.capabilities.addAll(capabilities);
				return this;
			}

			/**
			 * Configure capabilities represented as {@link String} literals. This method
			 * resolves capabilities using {@link BuiltinCapabilities}. Capabilities are
			 * added when calling this method and do not replace already configured
			 * capabilities.
			 * @param capabilities must not be {@literal null}.
			 * @return this builder.
			 * @throws IllegalArgumentException if the capability cannot be resolved to a
			 * built-in {@link Capability}.
			 */
			public RuleBuilder capabilities(String... capabilities) {
				Assert.notNull(capabilities, "Capabilities must not be null");
				Assert.noNullElements(capabilities, "Capabilities must not contain null elements");
				List<Capability> mapped = Arrays.stream(capabilities).map(value -> {
					Capability capability = BuiltinCapabilities.find(value);
					if (capability == null) {
						throw new IllegalArgumentException("Cannot resolve " + value + " to a capability");
					}
					return capability;
				}).collect(Collectors.toList());

				return capabilities(mapped);
			}

			/**
			 * Configure a min TTL for response wrapping.
			 * @param ttl must not be {@literal null}.
			 * @return this builder.
			 */
			public RuleBuilder minWrappingTtl(Duration ttl) {
				Assert.notNull(ttl, "TTL must not be null");
				this.minWrappingTtl = ttl;
				return this;
			}

			/**
			 * Configure a max TTL for response wrapping.
			 * @param ttl must not be {@literal null}.
			 * @return this builder.
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
			 * @param name must not be {@literal null} or empty.
			 * @param values must not be {@literal null}.
			 * @return this builder.
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
			 * @param name must not be {@literal null} or empty.
			 * @param values must not be {@literal null}.
			 * @return this builder.
			 */
			public RuleBuilder deniedParameter(String name, String... values) {
				Assert.hasText(name, "Denied parameter name must not be empty");
				Assert.notNull(values, "Values must not be null");
				this.deniedParameters.put(name, Arrays.asList(values));
				return this;
			}

			/**
			 * Build the {@link Rule} object. Requires a configured {@link #path(String)}
			 * and at least one {@link #capability(Policy.Capability)}.
			 * @return the new {@link Rule} object.
			 */
			public Rule build() {
				Assert.state(StringUtils.hasText(this.path), "Path must not be empty");
				Assert.state(!this.capabilities.isEmpty(), "Rule must define one or more capabilities");
				List<Capability> capabilities = switch (this.capabilities.size()) {
				case 0 -> Collections.emptyList();
				case 1 -> Collections.singletonList(this.capabilities.iterator().next());
				default -> Collections.unmodifiableList(new ArrayList<>(this.capabilities));
				};
				return new Rule(this.path, capabilities, this.minWrappingTtl, this.maxWrappingTtl,
						createMap(this.allowedParameters), createMap(this.deniedParameters));
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
		 * Allows deleting the data at the given path.
		 */
		DELETE,

		/**
		 * Allows listing values at the given path. Note that the keys returned by a
		 * list operation are not filtered by policies. Do not encode sensitive
		 * information in key names. Not all secrets engines support listing.
		 */
		LIST,

		/**
		 * Allows access to paths that are root-protected. Tokens are not permitted to
		 * interact with these paths unless they are have the sudo capability (in
		 * addition to the other necessary capabilities for performing an operation
		 * against that path, such as read or delete).
		 */
		SUDO,

		/**
		 * Disallows access. This always takes precedence regardless of any other
		 * defined capabilities, including {@link #SUDO}.
		 */
		DENY;

		/**
		 * Find a {@link Capability} by its name. The name is compared case-insensitive.
		 * @param value must not be {@literal null}.
		 * @return the {@link Capability} or {@literal null}, if not found.
		 */
		public @Nullable static Capability find(String value) {
			for (BuiltinCapabilities cap : values()) {
				if (cap.name().equalsIgnoreCase(value)) {
					return cap;
				}
			}
			return null;
		}

		/**
		 * Return all capabilities ({@link #CREATE},{@link #READ},{@link #UPDATE},
		 * {@link #DELETE}, {@link #LIST}) for regular CRUD operations.
		 * @return all CRUD operations.
		 * @since 2.3
		 */
		public static List<Capability> crud() {
			return Arrays.asList(CREATE, READ, UPDATE, DELETE, LIST);
		}

		/**
		 * Return all capabilities ({@link #CREATE},{@link #READ},{@link #UPDATE},
		 * {@link #DELETE}, {@link #LIST}) for regular CRUD operations including
		 * {@link #SUDO}.
		 * @return all CRUD operations including SUDO.
		 * @since 2.3
		 */
		public static List<Capability> crudAndSudo() {
			return Arrays.asList(CREATE, READ, UPDATE, DELETE, LIST, SUDO);
		}

	}

}
