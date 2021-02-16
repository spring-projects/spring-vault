/*
 * Copyright 2019-2021 the original author or authors.
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
package org.springframework.vault.util;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * This is an {@link BeforeAllCallback} and {@link ParameterResolver} that initializes
 * Vault and provides {@link VaultInitializer}.
 *
 * @author Mark Paluch
 * @see VaultInitializer
 */
public class VaultExtension implements BeforeAllCallback, ParameterResolver {

	private static final ExtensionContext.Namespace VAULT = ExtensionContext.Namespace.create("vault.initializer");

	@Override
	public void beforeAll(ExtensionContext extensionContext) throws Exception {

		VaultInitializer initializer = getInitializer(extensionContext);
		initializer.initialize();
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return parameterContext.getParameter().getType().isAssignableFrom(VaultInitializer.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return getInitializer(extensionContext);
	}

	private VaultInitializer getInitializer(ExtensionContext extensionContext) {
		ExtensionContext.Store store = extensionContext.getStore(VAULT);
		return store.getOrComputeIfAbsent(VaultInitializer.class, k -> new VaultInitializer(), VaultInitializer.class);
	}

}
