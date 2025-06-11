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
 * Compatibility layer for Jackson 2 and Jackson 3. This class auto-detects whether
 * Jackson 3 or Jackson 2 are available prefering Jackson 3. Note that Jackson 2 support
 * will be removed in future versions.
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

		Class<?> jackson2JsonNode = null;
		Class<?> jackson3JsonNode = null;
		try {
			jackson2JsonNode = ClassUtils
				.isPresent("com.fasterxml.jackson.databind.JsonNode", Jackson2.class.getClassLoader())
						? ClassUtils.forName("com.fasterxml.jackson.databind.JsonNode", Jackson2.class.getClassLoader())
						: null;
		}
		catch (ClassNotFoundException e) {
		}

		try {
			jackson3JsonNode = ClassUtils.isPresent("tools.jackson.databind.JsonNode", Jackson2.class.getClassLoader())
					? ClassUtils.forName("tools.jackson.databind.JsonNode", Jackson2.class.getClassLoader()) : null;

		}
		catch (ClassNotFoundException e) {
		}

		JACKSON_2_JSON_NODE = jackson2JsonNode;
		JACKSON_3_JSON_NODE = jackson3JsonNode;

		if (JACKSON_3_JSON_NODE != null) {
			compat = Jackson3.INSTANCE;
		}
		else if (JACKSON_2_JSON_NODE != null) {
			compat = Jackson2.INSTANCE;
		}
		else {
			throw new IllegalStateException("Either Jackson 2 or Jackson 3 must be available on the classpath");
		}
	}

	/**
	 * Obtain the {@link JacksonCompat} instance.
	 * @return
	 */
	public static JacksonCompat instance() {
		return compat;
	}

	public boolean isJackson3() {
		return this instanceof Jackson3;
	}

	public abstract AbstractHttpMessageConverter<Object> createHttpMessageConverter();

	public abstract void registerCodecs(Consumer<Object> messageConverters);

	public abstract Class<Object> getJsonNodeClass();

	public abstract Object getAt(Object jsonNode, String path);

	public abstract ObjectMapperAccessor getObjectMapperAccessor();

	public abstract ObjectMapperAccessor getPrettyPrintObjectMapperAccessor();

	public abstract @Nullable ObjectMapperAccessor getObjectMapperAccessor(
			List<HttpMessageConverter<?>> messageConverters);

	/**
	 * Accessor for {@code ObjectMapper} that provides methods to serialize and
	 * deserialize JSON.
	 */
	public interface ObjectMapperAccessor {

		static ObjectMapperAccessor create() {
			return compat.getObjectMapperAccessor();
		}

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
		 * Deserialize a {@code JsonNode} to the requested {@link Class type}.
		 * @param json must not be {@literal null}.
		 * @param type must not be {@literal null}.
		 * @return the deserialized object.
		 */
		<I> I deserialize(Object json, Class<I> type);

		String writeValueAsString(Object object);

	}

	static class Jackson2 extends JacksonCompat {

		static final Jackson2 INSTANCE = new Jackson2();
		static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
		static final ObjectMapper PRETTY_PRINT_OBJECT_MAPPER = new ObjectMapper()
			.enable(SerializationFeature.INDENT_OUTPUT);
		static final Jackson2ObjectMapperAccessor MAPPER_ACCESSOR = new Jackson2ObjectMapperAccessor(OBJECT_MAPPER);

		static final MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(
				OBJECT_MAPPER);

		public static boolean isAvailable() {
			return JACKSON_2_JSON_NODE != null;
		}

		@Override
		public AbstractHttpMessageConverter<Object> createHttpMessageConverter() {
			return converter;
		}

		@Override
		public void registerCodecs(Consumer<Object> messageConverters) {

			messageConverters.accept(new Jackson2JsonDecoder(OBJECT_MAPPER));
			messageConverters.accept(new Jackson2JsonEncoder(OBJECT_MAPPER));
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
			return MAPPER_ACCESSOR;
		}

		@Override
		public ObjectMapperAccessor getPrettyPrintObjectMapperAccessor() {
			return new Jackson2ObjectMapperAccessor(PRETTY_PRINT_OBJECT_MAPPER);
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
				}
				catch (IOException e) {
					throw new VaultException("Cannot deserialize response", e);
				}
			}

			@Override
			public String writeValueAsString(Object object) {
				try {
					return mapper.writeValueAsString(object);
				}
				catch (JsonProcessingException e) {
					throw new IllegalStateException("Cannot serialize headers to JSON", e);
				}
			}

		}

	}

	static class Jackson3 extends JacksonCompat {

		static final Jackson3 INSTANCE = new Jackson3();

		static final tools.jackson.databind.ObjectMapper OBJECT_MAPPER = new tools.jackson.databind.ObjectMapper();
		static final tools.jackson.databind.ObjectMapper PRETTY_PRINT_OBJECT_MAPPER = JsonMapper.builder()
			.enable(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
			.disable(JsonWriteFeature.ESCAPE_FORWARD_SLASHES)
			.build();
		static final Jackson3ObjectMapperAccessor MAPPER_ACCESSOR = new Jackson3ObjectMapperAccessor(
				PRETTY_PRINT_OBJECT_MAPPER);

		static final JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter(OBJECT_MAPPER);

		public static boolean isAvailable() {
			return JACKSON_3_JSON_NODE != null;
		}

		@Override
		public AbstractHttpMessageConverter<Object> createHttpMessageConverter() {
			return converter;
		}

		@Override
		public void registerCodecs(Consumer<Object> messageConverters) {

			messageConverters.accept(new JacksonJsonDecoder(OBJECT_MAPPER));
			messageConverters.accept(new JacksonJsonEncoder(OBJECT_MAPPER));
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
			return new Jackson3.Jackson3ObjectMapperAccessor(OBJECT_MAPPER);
		}

		@Override
		public ObjectMapperAccessor getPrettyPrintObjectMapperAccessor() {
			return MAPPER_ACCESSOR;
		}

		public @Nullable ObjectMapperAccessor getObjectMapperAccessor(List<HttpMessageConverter<?>> converters) {

			Optional<AbstractJacksonHttpMessageConverter> jackson3Converter = converters.stream()
				.filter(AbstractJacksonHttpMessageConverter.class::isInstance) //
				.map(AbstractJacksonHttpMessageConverter.class::cast) //
				.findFirst();

			return jackson3Converter.map(AbstractJacksonHttpMessageConverter::getObjectMapper)
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
