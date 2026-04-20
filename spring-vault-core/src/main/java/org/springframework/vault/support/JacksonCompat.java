/*
 * Copyright 2025-present the original author or authors.
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.ClassUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultOperations;
import org.springframework.web.client.RestTemplate;

/**
 * Internal compatibility facade for Jackson 2 and Jackson 3.
 * <p>Detects the available Jackson generation at runtime and prefers Jackson 3
 * when both are present.
 * <p>This class is intened for internal use only and will be removed in future
 * versions.
 *
 * @author Mark Paluch
 * @since 4.0
 */
@SuppressWarnings("ALL")
public abstract class JacksonCompat {

	static final @Nullable Class JACKSON_2_JSON_NODE;

	static final @Nullable Class JACKSON_3_JSON_NODE;

	static final JacksonCompat compat;


	static {

		ClassLoader classLoader = JacksonCompat.class.getClassLoader();
		Class<?> jackson2JsonNode = ClassUtils
				.isPresent("com.fasterxml.jackson.databind.JsonNode", classLoader)
						? ClassUtils.resolveClassName("com.fasterxml.jackson.databind.JsonNode", classLoader)
						: null;
		Class<?> jackson3JsonNode = ClassUtils.isPresent("tools.jackson.databind.JsonNode", classLoader)
				? ClassUtils.resolveClassName("tools.jackson.databind.JsonNode", classLoader)
				: null;

		JACKSON_2_JSON_NODE = jackson2JsonNode;
		JACKSON_3_JSON_NODE = jackson3JsonNode;
		if (JACKSON_3_JSON_NODE != null) {
			compat = Jackson3.INSTANCE;
		} else if (JACKSON_2_JSON_NODE != null) {
			compat = Jackson2.INSTANCE;
		} else {
			throw new IllegalStateException("Either Jackson 2 or Jackson 3 must be available on the classpath");
		}
	}


	/**
	 * Return the active {@link JacksonCompat} strategy for the current classpath.
	 * @return the active {@link JacksonCompat} strategy.
	 */
	public static JacksonCompat instance() {
		return compat;
	}


	/**
	 * Return whether the active strategy is backed by Jackson 3.
	 * @return {@literal true} if Jackson 3 is active; {@literal false} otherwise.
	 */
	public boolean isJackson3() {
		return this instanceof Jackson3;
	}

	/**
	 * Create an HTTP message converter backed by the active Jackson generation.
	 * @return a configured HTTP message converter.
	 */
	public abstract AbstractHttpMessageConverter<Object> createHttpMessageConverter();

	/**
	 * Register JSON encoder and decoder codecs for the active Jackson generation.
	 * @param messageConverters a consumer receiving the codecs to register.
	 */
	public abstract void registerCodecs(Consumer<Object> messageConverters);

	/**
	 * Return the JSON tree model type used by the active Jackson generation.
	 * @return the active JSON node class.
	 */
	public abstract Class<Object> getJsonNodeClass();

	/**
	 * Resolve a JSON pointer path against a JSON tree node.
	 * @param jsonNode the source JSON tree node.
	 * @param path the JSON pointer expression.
	 * @return the value located at the given path.
	 */
	public abstract Object getAt(Object jsonNode, String path);

	/**
	 * Return the default JSON mapper accessor for the active Jackson generation.
	 * @return the default {@link ObjectMapperAccessor}.
	 */
	public abstract ObjectMapperAccessor getObjectMapperAccessor();

	/**
	 * Return a JSON mapper accessor configured for pretty-print output.
	 * @return the pretty-print {@link ObjectMapperAccessor}.
	 */
	public abstract ObjectMapperAccessor getPrettyPrintObjectMapperAccessor();

	/**
	 * Attempt to obtain an {@link ObjectMapperAccessor} from the given message
	 * converters.
	 * @param messageConverters the converters to inspect.
	 * @return the matching {@link ObjectMapperAccessor}, or {@literal null} if none
	 * can be found.
	 */
	public abstract @Nullable ObjectMapperAccessor getObjectMapperAccessor(
			List<HttpMessageConverter<?>> messageConverters);


	/**
	 * Accessor for {@code ObjectMapper} that provides methods to serialize and
	 * deserialize JSON.
	 */
	public interface ObjectMapperAccessor {

		/**
		 * Create an accessor using the default mapper configuration of the active
		 * strategy.
		 * @return a default {@link ObjectMapperAccessor}.
		 */
		static ObjectMapperAccessor create() {
			return compat.getObjectMapperAccessor();
		}

		/**
		 * Create an accessor from the given {@link VaultOperations} context.
		 * <p>If a {@link RestTemplate} with a compatible Jackson converter is
		 * available, that converter's mapper is used. Otherwise, this method falls back
		 * to {@link #create()}.
		 * @param vaultOperations the operations used to access the current Vault
		 * session.
		 * @return an {@link ObjectMapperAccessor} for the current session context.
		 */
		static ObjectMapperAccessor from(VaultOperations vaultOperations) {

			return vaultOperations.doWithSession(operations -> {
				if (operations instanceof RestTemplate template) {
					ObjectMapperAccessor accessor = compat.getObjectMapperAccessor(template.getMessageConverters());
					if (accessor != null) {
						return accessor;
					}
				}
				return ObjectMapperAccessor.create();
			});
		}

		/**
		 * Deserialize JSON content to the requested target type.
		 * @param json must not be {@literal null}.
		 * @param type must not be {@literal null}.
		 * @return the deserialized object.
		 */
		<I> I deserialize(Object json, Class<I> type);

		/**
		 * Serialize the given object to its JSON string representation.
		 * @param object the object to serialize.
		 * @return the JSON string representation.
		 */
		String writeValueAsString(Object object);

	}


	/**
	 * Jackson 2 adapter.
	 */
	private static class Jackson2 extends JacksonCompat {

		private static final Jackson2 INSTANCE = new Jackson2();

		private static final ObjectMapper PRETTY_PRINT_OBJECT_MAPPER = new ObjectMapper()
				.enable(SerializationFeature.INDENT_OUTPUT);

		private static final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();

		private static final Jackson2ObjectMapperAccessor ACCESSOR = new Jackson2ObjectMapperAccessor(
				converter.getObjectMapper());

		private static final Jackson2ObjectMapperAccessor PRETTY_PRINT_MAPPER_ACCESSOR = new Jackson2ObjectMapperAccessor(
				PRETTY_PRINT_OBJECT_MAPPER);


		public static boolean isAvailable() {
			return JACKSON_2_JSON_NODE != null;
		}


		@Override
		public AbstractHttpMessageConverter<Object> createHttpMessageConverter() {
			return converter;
		}

		@Override
		public void registerCodecs(Consumer<Object> messageConverters) {

			messageConverters.accept(new Jackson2JsonDecoder(converter.getObjectMapper()));
			messageConverters.accept(new Jackson2JsonEncoder(converter.getObjectMapper()));
		}

		@Override
		public Class<Object> getJsonNodeClass() {
			return Objects.requireNonNull(JACKSON_2_JSON_NODE);
		}

		@Override
		public Object getAt(Object jsonNode, String path) {
			return ((JsonNode) jsonNode).at(path);
		}

		@Override
		public ObjectMapperAccessor getObjectMapperAccessor() {
			return ACCESSOR;
		}

		@Override
		public ObjectMapperAccessor getPrettyPrintObjectMapperAccessor() {
			return PRETTY_PRINT_MAPPER_ACCESSOR;
		}

		@SuppressWarnings("removal")
		public @Nullable ObjectMapperAccessor getObjectMapperAccessor(List<HttpMessageConverter<?>> converters) {
			Optional<AbstractJackson2HttpMessageConverter> jackson2Converter = converters.stream()
					.filter(AbstractJackson2HttpMessageConverter.class::isInstance) //
					.map(AbstractJackson2HttpMessageConverter.class::cast) //
					.findFirst();
			return jackson2Converter.map(AbstractJackson2HttpMessageConverter::getObjectMapper)
					.map(Jackson2ObjectMapperAccessor::new)
					.orElse(null);
		}


		static class Jackson2ObjectMapperAccessor implements ObjectMapperAccessor {

			private final com.fasterxml.jackson.databind.ObjectMapper mapper;


			Jackson2ObjectMapperAccessor(ObjectMapper mapper) {
				this.mapper = mapper;
			}


			public com.fasterxml.jackson.core.TreeNode getJsonNode(Object jsonNode) {
				return (com.fasterxml.jackson.databind.JsonNode) jsonNode;
			}

			@Override
			public <I> I deserialize(Object json, Class<I> type) {
				try {
					if (json instanceof String s) {
						return this.mapper.reader().readValue(s, type);
					}
					if (json instanceof byte[] bs) {
						return this.mapper.reader().readValue(bs, type);
					}
					return this.mapper.reader().readValue(getJsonNode(json).traverse(), type);
				} catch (IOException e) {
					throw new VaultException("Cannot deserialize response", e);
				}
			}

			@Override
			public String writeValueAsString(Object object) {
				try {
					return mapper.writeValueAsString(object);
				} catch (JsonProcessingException e) {
					throw new IllegalStateException("Cannot serialize headers to JSON", e);
				}
			}

		}

	}


	/**
	 * Jackson 3 adapter.
	 */
	private static class Jackson3 extends JacksonCompat {

		static final Jackson3 INSTANCE = new Jackson3();

		private static final JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();

		private static final tools.jackson.databind.ObjectMapper PRETTY_PRINT_OBJECT_MAPPER = JsonMapper.builder()
				.enable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
				.disable(JsonWriteFeature.ESCAPE_FORWARD_SLASHES)
				.build();

		private static final Jackson3ObjectMapperAccessor ACCESSOR = new Jackson3ObjectMapperAccessor(
				converter.getMapper());

		private static final Jackson3ObjectMapperAccessor PRETTY_PRINT_MAPPER_ACCESSOR = new Jackson3ObjectMapperAccessor(
				PRETTY_PRINT_OBJECT_MAPPER);


		public static boolean isAvailable() {
			return JACKSON_3_JSON_NODE != null;
		}


		@Override
		public AbstractHttpMessageConverter<Object> createHttpMessageConverter() {
			return converter;
		}

		@Override
		public void registerCodecs(Consumer<Object> messageConverters) {

			messageConverters.accept(new JacksonJsonDecoder(converter.getMapper()));
			messageConverters.accept(new JacksonJsonEncoder(converter.getMapper()));
		}

		@Override
		public Class<Object> getJsonNodeClass() {
			return Objects.requireNonNull(JACKSON_3_JSON_NODE);
		}

		@Override
		public Object getAt(Object jsonNode, String path) {
			return ((tools.jackson.databind.JsonNode) jsonNode).at(path);
		}

		@Override
		public ObjectMapperAccessor getObjectMapperAccessor() {
			return ACCESSOR;
		}

		@Override
		public ObjectMapperAccessor getPrettyPrintObjectMapperAccessor() {
			return PRETTY_PRINT_MAPPER_ACCESSOR;
		}

		public @Nullable ObjectMapperAccessor getObjectMapperAccessor(List<HttpMessageConverter<?>> converters) {
			Optional<AbstractJacksonHttpMessageConverter> jackson3Converter = converters.stream()
					.filter(AbstractJacksonHttpMessageConverter.class::isInstance) //
					.map(AbstractJacksonHttpMessageConverter.class::cast) //
					.findFirst();

			return jackson3Converter.map(AbstractJacksonHttpMessageConverter::getMapper)
					.map(Jackson3.Jackson3ObjectMapperAccessor::new)
					.orElse(null);
		}


		static class Jackson3ObjectMapperAccessor implements ObjectMapperAccessor {

			private final tools.jackson.databind.ObjectMapper mapper;


			Jackson3ObjectMapperAccessor(tools.jackson.databind.ObjectMapper mapper) {
				this.mapper = mapper;
			}


			public tools.jackson.databind.JsonNode getJsonNode(Object jsonNode) {
				return (tools.jackson.databind.JsonNode) jsonNode;
			}

			@Override
			public <I> I deserialize(Object json, Class<I> type) {
				ObjectReader reader = this.mapper.readerFor(type);
				if (json instanceof String s) {
					return reader.readValue(s);
				}
				if (json instanceof byte[] bs) {
					return reader.readValue(bs);
				}
				return reader.readValue(getJsonNode(json).traverse(ObjectReadContext.empty()));
			}

			@Override
			public String writeValueAsString(Object object) {
				return mapper.writeValueAsString(object);
			}

		}

	}

}
