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

package org.springframework.vault.annotation;

import java.util.Map.Entry;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.generate.GenerationContext;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.javapoet.CodeBlock;
import org.springframework.util.ClassUtils;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.domain.RequestedSecret.Mode;
import org.springframework.vault.core.util.PropertyTransformers;
import org.springframework.vault.core.util.PropertyTransformers.KeyPrefixPropertyTransformer;
import org.springframework.vault.core.util.PropertyTransformers.NoOpPropertyTransformer;

/**
 * AOT processor to serialize
 *
 * @author Mark Paluch
 * @since 3.0.1
 */
class PropertySourceAotProcessor implements BeanRegistrationAotProcessor {

	@Override
	public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {

		if (registeredBean.getBeanClass() == org.springframework.vault.core.env.LeaseAwareVaultPropertySource.class) {
			return BeanRegistrationAotContribution.withCustomCodeFragments(AotContribution::new);
		}

		if (registeredBean.getBeanClass() == org.springframework.vault.core.env.VaultPropertySource.class) {
			return BeanRegistrationAotContribution.withCustomCodeFragments(AotContribution::new);
		}

		return null;
	}

	static class AotContribution extends BeanRegistrationCodeFragmentsDecorator {

		protected AotContribution(BeanRegistrationCodeFragments delegate) {
			super(delegate);
		}

		@Override
		public CodeBlock generateSetBeanDefinitionPropertiesCode(GenerationContext generationContext,
				BeanRegistrationCode beanRegistrationCode, RootBeanDefinition beanDefinition,
				Predicate<String> attributeFilter) {

			CodeBlock.Builder code = CodeBlock.builder();

			ConstructorArgumentValues values = beanDefinition.getConstructorArgumentValues();

			for (Entry<Integer, ValueHolder> entry : values.getIndexedArgumentValues().entrySet()) {

				CodeBlock renderedValue = render(entry.getValue().getValue());
				code.addStatement("$N.getConstructorArgumentValues().addIndexedArgumentValue($L, $L)",
						BeanRegistrationCodeFragments.BEAN_DEFINITION_VARIABLE, entry.getKey(), renderedValue);
			}

			return code.build();
		}

		private static CodeBlock render(@Nullable Object value) {

			if (value instanceof RuntimeBeanReference runtimeBeanReference
					&& runtimeBeanReference.getBeanType() != null) {
				return CodeBlock.of("new $T($T.class)", RuntimeBeanReference.class, runtimeBeanReference.getBeanType());
			}

			if (value instanceof BeanReference beanReference) {
				return CodeBlock.of("new $T($S)", RuntimeBeanReference.class, beanReference.getBeanName());
			}

			if (value instanceof String) {
				return CodeBlock.of("$S", value.toString());
			}

			if (value == null || ClassUtils.isPrimitiveOrWrapper(value.getClass())) {
				return CodeBlock.of("$L", value != null ? value.toString() : "null");
			}

			if (value instanceof NoOpPropertyTransformer) {
				return CodeBlock.of("$T.$N()", PropertyTransformers.class, "noop");
			}

			if (value instanceof KeyPrefixPropertyTransformer kpt) {
				return CodeBlock.of("$T.$N($S)", PropertyTransformers.class, "propertyNamePrefix",
						kpt.getPropertyNamePrefix());
			}

			if (value instanceof RequestedSecret rs) {
				return CodeBlock.of("$T.$N($S)", RequestedSecret.class,
						rs.getMode() == Mode.ROTATE ? "rotating" : "renewable", rs.getPath());

			}

			throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
		}

	}

}
