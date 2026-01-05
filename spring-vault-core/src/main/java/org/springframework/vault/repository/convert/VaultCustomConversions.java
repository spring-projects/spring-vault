/*
 * Copyright 2017-present the original author or authors.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.vault.repository.mapping.VaultSimpleTypes;

/**
 * Value object to capture custom conversion. {@link VaultCustomConversions}
 * also act as factory for
 * {@link org.springframework.data.mapping.model.SimpleTypeHolder}
 *
 * @author Mark Paluch
 * @since 2.0
 * @see org.springframework.data.convert.CustomConversions
 * @see org.springframework.data.mapping.model.SimpleTypeHolder
 * @see VaultSimpleTypes
 */
public class VaultCustomConversions extends org.springframework.data.convert.CustomConversions {

	private static final StoreConversions STORE_CONVERSIONS;

	private static final List<Object> STORE_CONVERTERS;

	static {

		List<Object> converters = new ArrayList<>();

		converters.add(CustomToStringConverter.INSTANCE);

		STORE_CONVERTERS = Collections.unmodifiableList(converters);
		STORE_CONVERSIONS = StoreConversions.of(VaultSimpleTypes.HOLDER, STORE_CONVERTERS);
	}


	/**
	 * Create an empty {@code VaultCustomConversions} object.
	 */
	VaultCustomConversions() {
		this(Collections.emptyList());
	}

	/**
	 * Create a new {@link VaultCustomConversions} instance registering the given
	 * converters.
	 * @param converters must not be {@literal null}.
	 */
	public VaultCustomConversions(List<?> converters) {
		super(STORE_CONVERSIONS, converters);
	}


	@WritingConverter
	private enum CustomToStringConverter implements GenericConverter {

		INSTANCE;

		public Set<ConvertiblePair> getConvertibleTypes() {
			ConvertiblePair localeToString = new ConvertiblePair(Locale.class, String.class);
			ConvertiblePair booleanToString = new ConvertiblePair(Character.class, String.class);
			return new HashSet<>(Arrays.asList(localeToString, booleanToString));
		}

		public @Nullable Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			return source != null ? source.toString() : null;
		}

	}

}
