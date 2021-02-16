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

import java.util.Optional;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;

/**
 * This is an {@link org.junit.jupiter.api.extension.ExecutionCondition} that supports the
 * {@link RequiresVaultVersion @RequiresVaultVersion} annotation.
 *
 * @author Mark Paluch
 * @see RequiresVaultVersion
 * @see IntegrationTestSupport
 */
class VaultVersionExtension implements ExecutionCondition {

	private static final ExtensionContext.Namespace VAULT = ExtensionContext.Namespace.create("vault.version");

	private static final ConditionEvaluationResult ENABLED_BY_DEFAULT = ConditionEvaluationResult
			.enabled("@VaultVersion is not present");

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {

		Optional<RequiresVaultVersion> optional = AnnotationUtils.findAnnotation(context.getElement(),
				RequiresVaultVersion.class);

		if (!optional.isPresent()) {
			return ENABLED_BY_DEFAULT;
		}

		ExtensionContext.Store store = context.getStore(VAULT);

		Version runningVersion = store.getOrComputeIfAbsent(Version.class, versionClass -> {

			VaultInitializer initializer = new VaultInitializer();
			initializer.initialize();
			return initializer.prepare().getVersion();
		}, Version.class);

		RequiresVaultVersion requiredVersion = optional.get();

		Version required = Version.parse(requiredVersion.value());

		if (runningVersion.isGreaterThanOrEqualTo(required)) {
			return ConditionEvaluationResult
					.enabled(String.format("@VaultVersion check passed current Vault version is %s", runningVersion));
		}

		return ConditionEvaluationResult.disabled(String.format(
				"@VaultVersion requires since version %s, current Vault version is %s", required, runningVersion));
	}

}
