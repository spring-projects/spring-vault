/*
 * Copyright 2017-2022 the original author or authors.
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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.mapping.model.EntityInstantiators;

/**
 * Base class for {@link VaultConverter} implementations. Sets up a
 * {@link GenericConversionService} and populates basic converters.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public abstract class AbstractVaultConverter implements VaultConverter, InitializingBean {

	protected final GenericConversionService conversionService;

	protected CustomConversions conversions = new VaultCustomConversions();

	protected EntityInstantiators instantiators = new EntityInstantiators();

	/**
	 * Creates a new {@link AbstractVaultConverter} using the given
	 * {@link GenericConversionService}.
	 * @param conversionService must not be {@literal null}.
	 */
	public AbstractVaultConverter(GenericConversionService conversionService) {
		this.conversionService = conversionService;
	}

	/**
	 * Registers the given custom conversions with the converter.
	 * @param conversions
	 */
	public void setCustomConversions(CustomConversions conversions) {
		this.conversions = conversions;
	}

	/**
	 * Registers {@link EntityInstantiators} to customize entity instantiation.
	 * @param instantiators
	 */
	public void setInstantiators(EntityInstantiators instantiators) {
		this.instantiators = instantiators;
	}

	@Override
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void afterPropertiesSet() {
		initializeConverters();
	}

	/**
	 * Registers additional converters that will be available when using the
	 * {@link ConversionService} directly (e.g. for id conversion).
	 */
	private void initializeConverters() {
		this.conversions.registerConvertersIn(this.conversionService);
	}

}
