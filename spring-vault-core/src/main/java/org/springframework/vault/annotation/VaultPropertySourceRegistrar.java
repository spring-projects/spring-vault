/*
 * Copyright 2016-2021 the original author or authors.
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
package org.springframework.vault.annotation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.annotation.VaultPropertySource.Renewal;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.util.PropertyTransformer;
import org.springframework.vault.core.util.PropertyTransformers;

/**
 * Registrar to register {@link org.springframework.vault.core.env.VaultPropertySource}s
 * based on {@link VaultPropertySource}.
 * <p>
 * This class registers potentially multiple property sources based on different Vault
 * paths. {@link org.springframework.vault.core.env.VaultPropertySource}s are resolved and
 * added to {@link ConfigurableEnvironment} once the bean factory is post-processed. This
 * allows injection of Vault properties and and lookup using the
 * {@link org.springframework.core.env.Environment}.
 *
 * @author Mark Paluch
 */
class VaultPropertySourceRegistrar
		implements ImportBeanDefinitionRegistrar, BeanFactoryPostProcessor, EnvironmentAware {

	private @Nullable Environment environment;

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

		ConfigurableEnvironment env = beanFactory.getBean(ConfigurableEnvironment.class);
		MutablePropertySources propertySources = env.getPropertySources();

		registerPropertySources(
				beanFactory.getBeansOfType(org.springframework.vault.core.env.VaultPropertySource.class).values(),
				propertySources);

		registerPropertySources(beanFactory
				.getBeansOfType(org.springframework.vault.core.env.LeaseAwareVaultPropertySource.class).values(),
				propertySources);
	}

	private void registerPropertySources(Collection<? extends PropertySource<?>> propertySources,
			MutablePropertySources mutablePropertySources) {

		for (PropertySource<?> vaultPropertySource : propertySources) {

			if (mutablePropertySources.contains(vaultPropertySource.getName())) {
				continue;
			}

			mutablePropertySources.addLast(vaultPropertySource);
		}
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry registry) {

		Assert.notNull(annotationMetadata, "AnnotationMetadata must not be null");
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");

		if (!registry.isBeanNameInUse("VaultPropertySourceRegistrar")) {
			registry.registerBeanDefinition("VaultPropertySourceRegistrar", BeanDefinitionBuilder //
					.rootBeanDefinition(VaultPropertySourceRegistrar.class) //
					.setRole(BeanDefinition.ROLE_INFRASTRUCTURE) //
					.getBeanDefinition());
		}

		Set<AnnotationAttributes> propertySources = attributesForRepeatable(annotationMetadata,
				VaultPropertySources.class.getName(), VaultPropertySource.class.getName());

		int counter = 0;

		for (AnnotationAttributes propertySource : propertySources) {

			String[] paths = propertySource.getStringArray("value");
			String ref = propertySource.getString("vaultTemplateRef");
			String propertyNamePrefix = propertySource.getString("propertyNamePrefix");
			Renewal renewal = propertySource.getEnum("renewal");
			boolean ignoreSecretNotFound = propertySource.getBoolean("ignoreSecretNotFound");

			Assert.isTrue(paths.length > 0, "At least one @VaultPropertySource(value) location is required");

			Assert.hasText(ref, "'vaultTemplateRef' in @EnableVaultPropertySource must not be empty");

			PropertyTransformer propertyTransformer = StringUtils.hasText(propertyNamePrefix)
					? PropertyTransformers.propertyNamePrefix(propertyNamePrefix) : PropertyTransformers.noop();

			for (String propertyPath : paths) {

				if (!StringUtils.hasText(propertyPath)) {
					continue;
				}

				AbstractBeanDefinition beanDefinition = createBeanDefinition(ref, renewal, propertyTransformer,
						ignoreSecretNotFound, potentiallyResolveRequiredPlaceholders(propertyPath));

				do {
					String beanName = "vaultPropertySource#" + counter;

					if (!registry.isBeanNameInUse(beanName)) {
						registry.registerBeanDefinition(beanName, beanDefinition);
						break;
					}

					counter++;
				}
				while (true);
			}
		}
	}

	private String potentiallyResolveRequiredPlaceholders(String expression) {
		return this.environment != null ? this.environment.resolveRequiredPlaceholders(expression) : expression;
	}

	private AbstractBeanDefinition createBeanDefinition(String ref, Renewal renewal,
			PropertyTransformer propertyTransformer, boolean ignoreResourceNotFound, String propertyPath) {

		BeanDefinitionBuilder builder;

		if (isRenewable(renewal)) {
			builder = BeanDefinitionBuilder
					.rootBeanDefinition(org.springframework.vault.core.env.LeaseAwareVaultPropertySource.class);

			RequestedSecret requestedSecret = renewal == Renewal.ROTATE ? RequestedSecret.rotating(propertyPath)
					: RequestedSecret.renewable(propertyPath);

			builder.addConstructorArgValue(propertyPath);
			builder.addConstructorArgReference("secretLeaseContainer");
			builder.addConstructorArgValue(requestedSecret);
		}
		else {
			builder = BeanDefinitionBuilder
					.rootBeanDefinition(org.springframework.vault.core.env.VaultPropertySource.class);

			builder.addConstructorArgValue(propertyPath);
			builder.addConstructorArgReference(ref);
			builder.addConstructorArgValue(propertyPath);
		}

		builder.addConstructorArgValue(propertyTransformer);
		builder.addConstructorArgValue(ignoreResourceNotFound);
		builder.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);

		return builder.getBeanDefinition();
	}

	private boolean isRenewable(Renewal renewal) {
		return renewal == Renewal.RENEW || renewal == Renewal.ROTATE;
	}

	@SuppressWarnings("unchecked")
	static Set<AnnotationAttributes> attributesForRepeatable(AnnotationMetadata metadata, String containerClassName,
			String annotationClassName) {

		Set<AnnotationAttributes> result = new LinkedHashSet<>();
		addAttributesIfNotNull(result, metadata.getAnnotationAttributes(annotationClassName, false));

		Map<String, Object> container = metadata.getAnnotationAttributes(containerClassName, false);
		if (container != null && container.containsKey("value")) {
			for (Map<String, Object> containedAttributes : (Map<String, Object>[]) container.get("value")) {
				addAttributesIfNotNull(result, containedAttributes);
			}
		}
		return Collections.unmodifiableSet(result);
	}

	private static void addAttributesIfNotNull(Set<AnnotationAttributes> result,
			@Nullable Map<String, Object> attributes) {
		if (attributes != null) {
			result.add(AnnotationAttributes.fromMap(attributes));
		}
	}

}
