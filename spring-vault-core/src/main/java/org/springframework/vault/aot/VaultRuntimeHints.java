/*
 * Copyright 2023-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.vault.aot;

import java.io.IOException;
import java.util.stream.Stream;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

/**
 * Runtime hints for Spring Vault.
 *
 * @author Mark Paluch
 * @since 3.0.1
 */
class VaultRuntimeHints implements RuntimeHintsRegistrar {

	private final CachingMetadataReaderFactory factory = new CachingMetadataReaderFactory();


	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);

		ReflectionHints reflection = hints.reflection();
		MemberCategory[] dataObjectCategories = new MemberCategory[] {MemberCategory.DECLARED_FIELDS,
				MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
				MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS, MemberCategory.INTROSPECT_DECLARED_METHODS};
		try {
			Resource[] resources = resolver.getResources(ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
					+ ClassUtils.convertClassNameToResourcePath("org.springframework.vault.support") + "/*.class");

			for (Resource resource : resources) {

				MetadataReader metadataReader = factory.getMetadataReader(resource);
				String className = metadataReader.getClassMetadata().getClassName();

				if (className.contains("-")) {
					continue;
				}

				reflection.registerType(TypeReference.of(className), dataObjectCategories);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		Stream
				.of("org.springframework.vault.core.VaultSysTemplate$GetMounts$VaultMountsResponse",
						"org.springframework.vault.core.VaultVersionedKeyValueTemplate$VersionedResponse",
						"org.springframework.vault.core.ReactiveVaultTemplate$VaultListResponse",
						"org.springframework.vault.core.VaultListResponse",

						"org.springframework.vault.core.VaultTransitTemplate$RawTransitKeyImpl",
						"org.springframework.vault.core.VaultTransitTemplate$VaultTransitKeyImpl",

						"org.springframework.vault.core.VaultSysTemplate$GetMounts",
						"org.springframework.vault.core.VaultSysTemplate$GetUnsealStatus",
						"org.springframework.vault.core.VaultSysTemplate$Health",
						"org.springframework.vault.core.VaultSysTemplate$Seal",
						"org.springframework.vault.core.VaultSysTemplate$VaultHealthImpl",
						"org.springframework.vault.core.VaultSysTemplate$VaultInitializationResponseImpl",
						"org.springframework.vault.core.VaultSysTemplate$VaultUnsealStatusImpl",

						"org.springframework.vault.core.VaultVersionedKeyValueTemplate$VersionedResponse")
				.forEach(cls -> reflection.registerType(TypeReference.of(cls), dataObjectCategories));

		reflection.registerTypeIfPresent(classLoader, "com.google.api.client.json.jackson2.JacksonFactory",
				MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);

		reflection.registerTypeIfPresent(classLoader, "com.google.api.client.json.gson.GsonFactory",
				MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);

	}

}
