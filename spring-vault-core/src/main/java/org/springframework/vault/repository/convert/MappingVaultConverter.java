/*
 * Copyright 2017-2020 the original author or authors.
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
package org.springframework.vault.repository.convert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.springframework.core.CollectionFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.convert.EntityInstantiator;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.PreferredConstructor.Parameter;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.ConvertingPropertyAccessor;
import org.springframework.data.mapping.model.ParameterValueProvider;
import org.springframework.data.mapping.model.PersistentEntityParameterValueProvider;
import org.springframework.data.mapping.model.PropertyValueProvider;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.vault.repository.mapping.VaultPersistentEntity;
import org.springframework.vault.repository.mapping.VaultPersistentProperty;

/**
 * {@link VaultConverter} that uses a {@link MappingContext} to do sophisticated mapping
 * of domain objects to {@link SecretDocument}. This converter converts between Map-typed
 * representations and domain objects without use of a JSON library.
 * {@link SecretDocument} is the input to JSON mapping to exchange secrets with Vault.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class MappingVaultConverter extends AbstractVaultConverter {

	private final MappingContext<? extends VaultPersistentEntity<?>, VaultPersistentProperty> mappingContext;

	private VaultTypeMapper typeMapper;

	public MappingVaultConverter(
			MappingContext<? extends VaultPersistentEntity<?>, VaultPersistentProperty> mappingContext) {

		super(new DefaultConversionService());

		Assert.notNull(mappingContext, "MappingContext must not be null");

		this.mappingContext = mappingContext;
		this.typeMapper = new DefaultVaultTypeMapper(
				DefaultVaultTypeMapper.DEFAULT_TYPE_KEY, mappingContext);
	}

	/**
	 * Configures the {@link VaultTypeMapper} to be used to add type information to
	 * {@link SecretDocument}s created by the converter and how to lookup type information
	 * from {@link SecretDocument}s when reading them. Uses a
	 * {@link DefaultVaultTypeMapper} by default. Setting this to {@literal null} will
	 * reset the {@link org.springframework.data.convert.TypeMapper} to the default one.
	 *
	 * @param typeMapper the typeMapper to set, must not be {@literal null}.
	 */
	public void setTypeMapper(VaultTypeMapper typeMapper) {

		Assert.notNull(typeMapper, "VaultTypeMapper must not be null");
		this.typeMapper = typeMapper;
	}

	@Override
	public MappingContext<? extends VaultPersistentEntity<?>, VaultPersistentProperty> getMappingContext() {
		return mappingContext;
	}

	@Override
	public <S> S read(Class<S> type, SecretDocument source) {
		return read(ClassTypeInformation.from(type), source);
	}

	@SuppressWarnings("unchecked")
	private <S> S read(TypeInformation<S> type, Object source) {

		SecretDocument secretDocument = getSecretDocument(source);

		TypeInformation<? extends S> typeToUse = secretDocument != null
				? typeMapper.readType(secretDocument.getBody(), type)
				: (TypeInformation) ClassTypeInformation.OBJECT;
		Class<? extends S> rawType = typeToUse.getType();

		if (conversions.hasCustomReadTarget(source.getClass(), rawType)) {
			return conversionService.convert(source, rawType);
		}

		if (SecretDocument.class.isAssignableFrom(rawType)) {
			return (S) source;
		}

		if (Map.class.isAssignableFrom(rawType) && secretDocument != null) {
			return (S) secretDocument.getBody();
		}

		if (typeToUse.isMap() && secretDocument != null) {
			return (S) readMap(typeToUse, secretDocument.getBody());
		}

		if (typeToUse.equals(ClassTypeInformation.OBJECT)) {
			return (S) source;
		}

		return read((VaultPersistentEntity<S>) mappingContext
				.getRequiredPersistentEntity(typeToUse), secretDocument);
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private SecretDocument getSecretDocument(Object source) {

		SecretDocument secretDocument = null;
		if (source instanceof Map) {
			secretDocument = new SecretDocument((Map) source);
		}
		else if (source instanceof SecretDocument) {
			secretDocument = (SecretDocument) source;
		}
		return secretDocument;
	}

	private ParameterValueProvider<VaultPersistentProperty> getParameterProvider(
			VaultPersistentEntity<?> entity, SecretDocument source) {

		VaultPropertyValueProvider provider = new VaultPropertyValueProvider(source);

		PersistentEntityParameterValueProvider<VaultPersistentProperty> parameterProvider = new PersistentEntityParameterValueProvider<>(
				entity, provider, source);

		return new ParameterValueProvider<VaultPersistentProperty>() {

			@Nullable
			@Override
			public <T> T getParameterValue(
					Parameter<T, VaultPersistentProperty> parameter) {

				Object value = parameterProvider.getParameterValue(parameter);
				return value != null ? readValue(value, parameter.getType()) : null;
			}
		};
	}

	private <S> S read(VaultPersistentEntity<S> entity, SecretDocument source) {

		ParameterValueProvider<VaultPersistentProperty> provider = getParameterProvider(
				entity, source);
		EntityInstantiator instantiator = instantiators.getInstantiatorFor(entity);
		S instance = instantiator.createInstance(entity, provider);

		PersistentPropertyAccessor accessor = new ConvertingPropertyAccessor(
				entity.getPropertyAccessor(instance), conversionService);

		VaultPersistentProperty idProperty = entity.getIdProperty();
		SecretDocumentAccessor documentAccessor = new SecretDocumentAccessor(source);

		// make sure id property is set before all other properties
		Object idValue;

		if (entity.requiresPropertyPopulation()) {
			if (idProperty != null && !entity.isConstructorArgument(idProperty)
					&& documentAccessor.hasValue(idProperty)) {

				idValue = readIdValue(idProperty, documentAccessor);
				accessor.setProperty(idProperty, idValue);
			}

			VaultPropertyValueProvider valueProvider = new VaultPropertyValueProvider(
					documentAccessor);

			readProperties(entity, accessor, idProperty, documentAccessor, valueProvider);
		}

		return instance;
	}

	@Nullable
	private Object readIdValue(VaultPersistentProperty idProperty,
			SecretDocumentAccessor documentAccessor) {

		Object resolvedValue = documentAccessor.get(idProperty);

		return resolvedValue != null
				? readValue(resolvedValue, idProperty.getTypeInformation())
				: null;
	}

	private void readProperties(VaultPersistentEntity<?> entity,
			PersistentPropertyAccessor accessor,
			@Nullable VaultPersistentProperty idProperty,
			SecretDocumentAccessor documentAccessor,
			VaultPropertyValueProvider valueProvider) {

		for (VaultPersistentProperty prop : entity) {

			// we skip the id property since it was already set
			if (idProperty != null && idProperty.equals(prop)) {
				continue;
			}

			if (entity.isConstructorArgument(prop) || !documentAccessor.hasValue(prop)) {
				continue;
			}

			accessor.setProperty(prop, valueProvider.getPropertyValue(prop));
		}
	}

	@Nullable
	@SuppressWarnings("unchecked")
	private <T> T readValue(Object value, TypeInformation<?> type) {

		Class<?> rawType = type.getType();

		if (conversions.hasCustomReadTarget(value.getClass(), rawType)) {
			return (T) conversionService.convert(value, rawType);
		}
		else if (value instanceof List) {
			return (T) readCollectionOrArray(type, (List) value);
		}
		else if (value instanceof Map) {
			return (T) read(type, (Map) value);
		}
		else {
			return (T) getPotentiallyConvertedSimpleRead(value, rawType);
		}
	}

	/**
	 * Reads the given {@link List} into a collection of the given {@link TypeInformation}
	 * .
	 *
	 * @param targetType must not be {@literal null}.
	 * @param sourceValue must not be {@literal null}.
	 * @return the converted {@link Collection} or array, will never be {@literal null}.
	 */
	@Nullable
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object readCollectionOrArray(TypeInformation<?> targetType,
			List sourceValue) {

		Assert.notNull(targetType, "Target type must not be null");

		Class<?> collectionType = targetType.getType();

		TypeInformation<?> componentType = targetType.getComponentType() != null
				? targetType.getComponentType()
				: ClassTypeInformation.OBJECT;
		Class<?> rawComponentType = componentType.getType();

		collectionType = Collection.class.isAssignableFrom(collectionType)
				? collectionType
				: List.class;
		Collection<Object> items = targetType.getType().isArray()
				? new ArrayList<>(sourceValue.size())
				: CollectionFactory.createCollection(collectionType, rawComponentType,
						sourceValue.size());

		if (sourceValue.isEmpty()) {
			return getPotentiallyConvertedSimpleRead(items, collectionType);
		}

		for (Object obj : sourceValue) {

			if (obj instanceof Map) {
				items.add(read(componentType, (Map) obj));
			}
			else if (obj instanceof List) {
				items.add(readCollectionOrArray(ClassTypeInformation.OBJECT, (List) obj));
			}
			else {
				items.add(getPotentiallyConvertedSimpleRead(obj, rawComponentType));
			}
		}

		return getPotentiallyConvertedSimpleRead(items, targetType.getType());
	}

	/**
	 * Reads the given {@link Map} into a {@link Map}. will recursively resolve nested
	 * {@link Map}s as well.
	 *
	 * @param type the {@link Map} {@link TypeInformation} to be used to unmarshal this
	 *     {@link Map}.
	 * @param sourceMap must not be {@literal null}
	 * @return the converted {@link Map}.
	 */
	protected Map<Object, Object> readMap(TypeInformation<?> type,
			Map<String, Object> sourceMap) {

		Assert.notNull(sourceMap, "Source map must not be null");

		Class<?> mapType = typeMapper.readType(sourceMap, type).getType();

		TypeInformation<?> keyType = type.getComponentType();
		TypeInformation<?> valueType = type.getMapValueType();

		Class<?> rawKeyType = keyType != null ? keyType.getType() : null;
		Class<?> rawValueType = valueType != null ? valueType.getType() : null;

		Map<Object, Object> map = CollectionFactory.createMap(mapType, rawKeyType,
				sourceMap.keySet().size());

		for (Entry<String, Object> entry : sourceMap.entrySet()) {

			if (typeMapper.isTypeKey(entry.getKey())) {
				continue;
			}

			Object key = entry.getKey();

			if (rawKeyType != null && !rawKeyType.isAssignableFrom(key.getClass())) {
				key = conversionService.convert(key, rawKeyType);
			}

			Object value = entry.getValue();
			TypeInformation<?> defaultedValueType = valueType != null ? valueType
					: ClassTypeInformation.OBJECT;

			if (value instanceof Map) {
				map.put(key, read(defaultedValueType, (Map) value));
			}
			else if (value instanceof List) {
				map.put(key,
						readCollectionOrArray(
								valueType != null ? valueType : ClassTypeInformation.LIST,
								(List) value));
			}
			else {
				map.put(key, getPotentiallyConvertedSimpleRead(value, rawValueType));
			}
		}

		return map;
	}

	/**
	 * Checks whether we have a custom conversion for the given simple object. Converts
	 * the given value if so, applies {@link Enum} handling or returns the value as is.
	 *
	 * @param value
	 * @param target must not be {@literal null}.
	 * @return
	 */
	@Nullable
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object getPotentiallyConvertedSimpleRead(@Nullable Object value,
			@Nullable Class<?> target) {

		if (value == null || target == null
				|| target.isAssignableFrom(value.getClass())) {
			return value;
		}

		if (Enum.class.isAssignableFrom(target)) {
			return Enum.valueOf((Class<Enum>) target, value.toString());
		}

		return conversionService.convert(value, target);
	}

	@Override
	public void write(Object source, SecretDocument sink) {

		Class<?> entityType = ClassUtils.getUserClass(source.getClass());
		TypeInformation<? extends Object> type = ClassTypeInformation.from(entityType);

		SecretDocumentAccessor documentAccessor = new SecretDocumentAccessor(sink);

		writeInternal(source, documentAccessor, type);

		boolean handledByCustomConverter = conversions.hasCustomWriteTarget(entityType,
				SecretDocument.class);
		if (!handledByCustomConverter) {
			typeMapper.writeType(type, sink.getBody());
		}
	}

	/**
	 * Internal write conversion method which should be used for nested invocations.
	 *
	 * @param obj
	 * @param sink
	 * @param typeHint
	 */
	@SuppressWarnings("unchecked")
	protected void writeInternal(Object obj, SecretDocumentAccessor sink,
			@Nullable TypeInformation<?> typeHint) {

		Class<?> entityType = obj.getClass();
		Optional<Class<?>> customTarget = conversions.getCustomWriteTarget(entityType,
				SecretDocument.class);

		if (customTarget.isPresent()) {

			SecretDocument result = conversionService.convert(obj, SecretDocument.class);

			if (result.getId() != null) {
				sink.setId(result.getId());
			}

			sink.getBody().putAll(result.getBody());
			return;
		}

		if (Map.class.isAssignableFrom(entityType)) {
			writeMapInternal((Map<Object, Object>) obj, sink.getBody(),
					ClassTypeInformation.MAP);
			return;
		}

		VaultPersistentEntity<?> entity = mappingContext
				.getRequiredPersistentEntity(entityType);
		writeInternal(obj, sink, entity);
		addCustomTypeKeyIfNecessary(typeHint, obj, sink);
	}

	protected void writeInternal(Object obj, SecretDocumentAccessor sink,
			VaultPersistentEntity<?> entity) {

		PersistentPropertyAccessor accessor = entity.getPropertyAccessor(obj);

		VaultPersistentProperty idProperty = entity.getIdProperty();
		if (idProperty != null && !sink.hasValue(idProperty)) {

			Object value = accessor.getProperty(idProperty);

			if (value != null) {
				sink.put(idProperty, value);
			}
		}
		writeProperties(entity, accessor, sink, idProperty);
	}

	private void writeProperties(VaultPersistentEntity<?> entity,
			PersistentPropertyAccessor accessor, SecretDocumentAccessor sink,
			@Nullable VaultPersistentProperty idProperty) {

		// Write the properties
		for (VaultPersistentProperty prop : entity) {

			if (prop.equals(idProperty) || !prop.isWritable()) {
				continue;
			}

			Object value = accessor.getProperty(prop);

			if (value == null) {
				continue;
			}

			if (!conversions.isSimpleType(value.getClass())) {
				writePropertyInternal(value, sink, prop);
			}
			else {

				sink.put(prop, getPotentiallyConvertedSimpleWrite(value));
			}
		}
	}

	@SuppressWarnings({ "unchecked" })
	protected void writePropertyInternal(@Nullable Object obj,
			SecretDocumentAccessor accessor, VaultPersistentProperty prop) {

		if (obj == null) {
			return;
		}

		TypeInformation<?> valueType = ClassTypeInformation.from(obj.getClass());
		TypeInformation<?> type = prop.getTypeInformation();

		if (valueType.isCollectionLike()) {
			List<Object> collectionInternal = createCollection(asCollection(obj), prop);
			accessor.put(prop, collectionInternal);
			return;
		}

		if (valueType.isMap()) {
			Map<String, Object> mapDbObj = createMap((Map<Object, Object>) obj, prop);
			accessor.put(prop, mapDbObj);
			return;
		}

		// Lookup potential custom target type
		Optional<Class<?>> basicTargetType = conversions
				.getCustomWriteTarget(obj.getClass());

		if (basicTargetType.isPresent()) {

			accessor.put(prop, conversionService.convert(obj, basicTargetType.get()));
			return;
		}

		VaultPersistentEntity<?> entity = isSubtype(prop.getType(), obj.getClass())
				? mappingContext.getRequiredPersistentEntity(obj.getClass())
				: mappingContext.getRequiredPersistentEntity(type);

		SecretDocumentAccessor nested = accessor.writeNested(prop);

		writeInternal(obj, nested, entity);
		addCustomTypeKeyIfNecessary(ClassTypeInformation.from(prop.getRawType()), obj,
				nested);
	}

	private static boolean isSubtype(Class<?> left, Class<?> right) {
		return left.isAssignableFrom(right) && !left.equals(right);
	}

	/**
	 * Writes the given {@link Collection} using the given {@link VaultPersistentProperty}
	 * information.
	 *
	 * @param collection must not be {@literal null}.
	 * @param property must not be {@literal null}.
	 * @return the converted {@link List}.
	 */
	protected List<Object> createCollection(Collection<?> collection,
			VaultPersistentProperty property) {

		return writeCollectionInternal(collection, property.getTypeInformation(),
				new ArrayList<>());
	}

	/**
	 * Populates the given {@link List} with values from the given {@link Collection}.
	 *
	 * @param source the collection to create a {@link List} for, must not be
	 *     {@literal null}.
	 * @param type the {@link TypeInformation} to consider or {@literal null} if unknown.
	 * @param sink the {@link List} to write to.
	 * @return the converted {@link List}.
	 */
	private List<Object> writeCollectionInternal(Collection<?> source,
			@Nullable TypeInformation<?> type, List<Object> sink) {

		TypeInformation<?> componentType = null;

		if (type != null) {
			componentType = type.getComponentType();
		}

		for (Object element : source) {

			Class<?> elementType = element == null ? null : element.getClass();

			if (elementType == null || conversions.isSimpleType(elementType)) {
				sink.add(getPotentiallyConvertedSimpleWrite(element));
			}
			else if (element instanceof Collection || elementType.isArray()) {
				sink.add(writeCollectionInternal(asCollection(element), componentType,
						new ArrayList<>()));
			}
			else {
				SecretDocumentAccessor accessor = new SecretDocumentAccessor(
						new SecretDocument());
				writeInternal(element, accessor, componentType);
				sink.add(accessor.getBody());
			}
		}

		return sink;
	}

	/**
	 * Writes the given {@link Map} using the given {@link VaultPersistentProperty}
	 * information.
	 *
	 * @param map must not {@literal null}.
	 * @param property must not be {@literal null}.
	 * @return the converted {@link Map}.
	 */
	protected Map<String, Object> createMap(Map<Object, Object> map,
			VaultPersistentProperty property) {

		Assert.notNull(map, "Given map must not be null");
		Assert.notNull(property, "PersistentProperty must not be null");

		return writeMapInternal(map, new LinkedHashMap<>(),
				property.getTypeInformation());
	}

	/**
	 * Writes the given {@link Map} to the given {@link Map} considering the given
	 * {@link TypeInformation}.
	 *
	 * @param obj must not be {@literal null}.
	 * @param bson must not be {@literal null}.
	 * @param propertyType must not be {@literal null}.
	 * @return the converted {@link Map}.
	 */
	protected Map<String, Object> writeMapInternal(Map<Object, Object> obj,
			Map<String, Object> bson, TypeInformation<?> propertyType) {

		for (Entry<Object, Object> entry : obj.entrySet()) {

			Object key = entry.getKey();
			Object val = entry.getValue();

			if (conversions.isSimpleType(key.getClass())) {

				String simpleKey = key.toString();
				if (val == null || conversions.isSimpleType(val.getClass())) {
					bson.put(simpleKey, val);
				}
				else if (val instanceof Collection || val.getClass().isArray()) {

					bson.put(simpleKey, writeCollectionInternal(asCollection(val),
							propertyType.getMapValueType(), new ArrayList<>()));
				}
				else {
					SecretDocumentAccessor nested = new SecretDocumentAccessor(
							new SecretDocument());
					TypeInformation<?> valueTypeInfo = propertyType.isMap()
							? propertyType.getMapValueType()
							: ClassTypeInformation.OBJECT;
					writeInternal(val, nested, valueTypeInfo);
					bson.put(simpleKey, nested.getBody());
				}
			}
			else {
				throw new MappingException("Cannot use a complex object as a key value.");
			}
		}

		return bson;
	}

	/**
	 * Adds custom type information to the given {@link SecretDocument} if necessary. That
	 * is if the value is not the same as the one given. This is usually the case if you
	 * store a subtype of the actual declared type of the property.
	 *
	 * @param type type hint.
	 * @param value must not be {@literal null}.
	 * @param accessor must not be {@literal null}.
	 */
	protected void addCustomTypeKeyIfNecessary(@Nullable TypeInformation<?> type,
			Object value, SecretDocumentAccessor accessor) {

		Class<?> reference = type != null ? type.getActualType().getType() : Object.class;
		Class<?> valueType = ClassUtils.getUserClass(value.getClass());

		boolean notTheSameClass = !valueType.equals(reference);
		if (notTheSameClass) {
			typeMapper.writeType(valueType, accessor.getBody());
		}
	}

	/**
	 * Checks whether we have a custom conversion registered for the given value into an
	 * arbitrary simple Vault type. Returns the converted value if so. If not, we perform
	 * special enum handling or simply return the value as is.
	 *
	 * @param value the value to write.
	 * @return the converted value. Can be {@literal null}.
	 */
	@Nullable
	private Object getPotentiallyConvertedSimpleWrite(@Nullable Object value) {

		if (value == null) {
			return null;
		}

		Optional<Class<?>> customTarget = conversions
				.getCustomWriteTarget(value.getClass());

		if (customTarget.isPresent()) {
			return conversionService.convert(value, customTarget.get());
		}

		if (ObjectUtils.isArray(value)) {

			if (value instanceof byte[]) {
				return value;
			}
			return asCollection(value);
		}

		return Enum.class.isAssignableFrom(value.getClass()) ? ((Enum<?>) value).name()
				: value;
	}

	/**
	 * Returns given object as {@link Collection}. Will return the {@link Collection} as
	 * is if the source is a {@link Collection} already, will convert an array into a
	 * {@link Collection} or simply create a single element collection for everything
	 * else.
	 *
	 * @param source the collection object. Can be a {@link Collection}, array or
	 *     singleton object.
	 * @return the {@code source} as {@link Collection}.
	 */
	private static Collection<?> asCollection(Object source) {

		if (source instanceof Collection) {
			return (Collection<?>) source;
		}

		return source.getClass().isArray() ? CollectionUtils.arrayToList(source)
				: Collections.singleton(source);
	}

	/**
	 * {@link PropertyValueProvider} to evaluate a SpEL expression if present on the
	 * property or simply accesses the field of the configured source
	 * {@link SecretDocument}.
	 *
	 */
	class VaultPropertyValueProvider
			implements PropertyValueProvider<VaultPersistentProperty> {

		private final SecretDocumentAccessor source;

		VaultPropertyValueProvider(SecretDocument source) {

			Assert.notNull(source, "Source document must no be null!");

			this.source = new SecretDocumentAccessor(source);
		}

		VaultPropertyValueProvider(SecretDocumentAccessor accessor) {

			Assert.notNull(accessor, "SecretDocumentAccessor must no be null!");

			this.source = accessor;
		}

		@Nullable
		public <T> T getPropertyValue(VaultPersistentProperty property) {

			Object value = source.get(property);

			if (value == null) {
				return null;
			}

			return readValue(value, property.getTypeInformation());
		}
	}
}
