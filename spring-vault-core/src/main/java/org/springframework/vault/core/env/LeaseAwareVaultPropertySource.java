/*
 * Copyright 2017-2025 the original author or authors.
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

package org.springframework.vault.core.env;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.BeforeSecretLeaseRevocationEvent;
import org.springframework.vault.core.lease.event.LeaseListenerAdapter;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;
import org.springframework.vault.core.lease.event.SecretLeaseRotatedEvent;
import org.springframework.vault.core.lease.event.SecretNotFoundEvent;
import org.springframework.vault.core.util.PropertyTransformer;
import org.springframework.vault.core.util.PropertyTransformers;
import org.springframework.vault.support.JsonMapFlattener;

/**
 * {@link PropertySource} that requests renewable secrets from
 * {@link SecretLeaseContainer}. Leases are renewed or rotated, depeding on
 * {@link RequestedSecret#getMode()}. Contents of this {@link PropertySource} is
 * updated from background threads and the content is mutable. Expiration and
 * revocation removes properties.
 *
 * @author Mark Paluch
 * @see org.springframework.core.env.PropertiesPropertySource
 * @see PropertyTransformer
 * @see PropertyTransformers
 */
public class LeaseAwareVaultPropertySource extends EnumerablePropertySource<VaultOperations> {

	@SuppressWarnings("FieldMayBeFinal") // allow setting via reflection.
	private static Log logger = LogFactory.getLog(LeaseAwareVaultPropertySource.class);


	private final SecretLeaseContainer secretLeaseContainer;

	private final RequestedSecret requestedSecret;

	private final Map<String, Object> properties = new ConcurrentHashMap<>();

	private final PropertyTransformer propertyTransformer;

	private final boolean ignoreSecretNotFound;

	private final LeaseListenerAdapter leaseListener;

	private volatile boolean notFound = false;

	private @Nullable volatile Exception loadError;


	/**
	 * Create a new {@link LeaseAwareVaultPropertySource} given a
	 * {@link SecretLeaseContainer} and {@link RequestedSecret}. This property
	 * source requests the secret upon initialization and receives secrets once they
	 * are emitted through events published by {@link SecretLeaseContainer}.
	 * @param secretLeaseContainer must not be {@literal null}.
	 * @param requestedSecret must not be {@literal null}.
	 */
	public LeaseAwareVaultPropertySource(SecretLeaseContainer secretLeaseContainer, RequestedSecret requestedSecret) {
		this(requestedSecret.getPath(), secretLeaseContainer, requestedSecret);
	}

	/**
	 * Create a new {@link LeaseAwareVaultPropertySource} given a {@code name},
	 * {@link SecretLeaseContainer} and {@link RequestedSecret}. This property
	 * source requests the secret upon initialization and receives secrets once they
	 * are emitted through events published by {@link SecretLeaseContainer}.
	 * @param name name of the property source, must not be {@literal null}.
	 * @param secretLeaseContainer must not be {@literal null}.
	 * @param requestedSecret must not be {@literal null}.
	 */
	public LeaseAwareVaultPropertySource(String name, SecretLeaseContainer secretLeaseContainer,
			RequestedSecret requestedSecret) {
		this(name, secretLeaseContainer, requestedSecret, PropertyTransformers.noop());
	}

	/**
	 * Create a new {@link LeaseAwareVaultPropertySource} given a {@code name},
	 * {@link SecretLeaseContainer} and {@link RequestedSecret}. This property
	 * source requests the secret upon initialization and receives secrets once they
	 * are emitted through events published by {@link SecretLeaseContainer}.
	 * @param name name of the property source, must not be {@literal null}.
	 * @param secretLeaseContainer must not be {@literal null}.
	 * @param requestedSecret must not be {@literal null}.
	 * @param propertyTransformer object to transform properties.
	 * @see PropertyTransformers
	 */
	public LeaseAwareVaultPropertySource(String name, SecretLeaseContainer secretLeaseContainer,
			RequestedSecret requestedSecret, PropertyTransformer propertyTransformer) {
		this(name, secretLeaseContainer, requestedSecret, propertyTransformer, true);
	}

	/**
	 * Create a new {@link LeaseAwareVaultPropertySource} given a {@code name},
	 * {@link SecretLeaseContainer} and {@link RequestedSecret}. This property
	 * source requests the secret upon initialization and receives secrets once they
	 * are emitted through events published by {@link SecretLeaseContainer}.
	 * @param name name of the property source, must not be {@literal null}.
	 * @param secretLeaseContainer must not be {@literal null}.
	 * @param requestedSecret must not be {@literal null}.
	 * @param propertyTransformer object to transform properties.
	 * @param ignoreSecretNotFound indicate if failure to find a secret at
	 * {@code path} should be ignored.
	 * @since 2.2
	 * @see PropertyTransformers
	 */
	public LeaseAwareVaultPropertySource(String name, SecretLeaseContainer secretLeaseContainer,
			RequestedSecret requestedSecret, PropertyTransformer propertyTransformer, boolean ignoreSecretNotFound) {

		super(name);

		Assert.notNull(secretLeaseContainer, "SecretLeaseContainer must not be null");
		Assert.notNull(requestedSecret, "RequestedSecret must not be null");
		Assert.notNull(propertyTransformer, "PropertyTransformer must not be null");

		this.secretLeaseContainer = secretLeaseContainer;
		this.requestedSecret = requestedSecret;
		this.propertyTransformer = propertyTransformer.andThen(PropertyTransformers.removeNullProperties());
		this.ignoreSecretNotFound = ignoreSecretNotFound;
		this.leaseListener = new LeaseListenerAdapter() {

			@Override
			public void onLeaseEvent(SecretLeaseEvent leaseEvent) {
				handleLeaseEvent(leaseEvent, LeaseAwareVaultPropertySource.this.properties);
			}

			@Override
			public void onLeaseError(SecretLeaseEvent leaseEvent, Exception exception) {
				handleLeaseErrorEvent(leaseEvent, exception);
			}

		};

		loadProperties();
	}

	/**
	 * Initialize property source and read properties from Vault.
	 */
	private void loadProperties() {

		if (logger.isDebugEnabled()) {
			logger.debug("Requesting secrets from Vault at %s using %s".formatted(this.requestedSecret.getPath(),
					this.requestedSecret.getMode()));
		}

		this.secretLeaseContainer.addLeaseListener(this.leaseListener);
		this.secretLeaseContainer.addErrorListener(this.leaseListener);
		this.secretLeaseContainer.addRequestedSecret(this.requestedSecret);

		Exception loadError = this.loadError;
		if (this.notFound || loadError != null) {

			String msg = "Vault location [%s] not resolvable".formatted(this.requestedSecret.getPath());

			if (this.ignoreSecretNotFound) {
				if (logger.isInfoEnabled()) {
					logger.info("%s: %s".formatted(msg, loadError != null ? loadError.getMessage() : "Not found"));
				}
			} else {
				if (loadError != null) {
					throw new VaultPropertySourceNotFoundException(msg, loadError);
				}
				throw new VaultPropertySourceNotFoundException(msg);
			}
		}
	}


	public RequestedSecret getRequestedSecret() {
		return this.requestedSecret;
	}

	@Override
	public @Nullable Object getProperty(String name) {
		return this.properties.get(name);
	}

	@Override
	public String[] getPropertyNames() {

		Set<String> strings = this.properties.keySet();
		return strings.toArray(new String[0]);
	}


	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	/**
	 * Hook method to handle a {@link SecretLeaseEvent}.
	 * @param leaseEvent must not be {@literal null}.
	 * @param properties reference to property storage of this property source.
	 */
	protected void handleLeaseEvent(SecretLeaseEvent leaseEvent, Map<String, Object> properties) {
		if (leaseEvent.getSource() != getRequestedSecret()) {
			return;
		}
		if (leaseEvent instanceof SecretNotFoundEvent) {
			this.notFound = true;
		}
		if (leaseEvent instanceof SecretLeaseExpiredEvent || leaseEvent instanceof BeforeSecretLeaseRevocationEvent
				|| leaseEvent instanceof SecretLeaseCreatedEvent) {
			properties.clear();
		}
		if (leaseEvent instanceof SecretLeaseCreatedEvent created) {
			Map<String, Object> secrets = doTransformProperties(flattenMap(created.getSecrets()));
			if (leaseEvent instanceof SecretLeaseRotatedEvent) {
				List<String> removedKeys = new ArrayList<>(properties.keySet());
				removedKeys.removeAll(secrets.keySet());
				removedKeys.forEach(properties::remove);
			}
			properties.putAll(secrets);
		}
	}

	/**
	 * Hook method to handle a {@link SecretLeaseEvent} errors.
	 * @param leaseEvent must not be {@literal null}.
	 * @param exception offending exception.
	 */
	protected void handleLeaseErrorEvent(SecretLeaseEvent leaseEvent, Exception exception) {
		if (leaseEvent.getSource() != getRequestedSecret()) {
			return;
		}
		this.loadError = exception;
	}

	/**
	 * Hook method to transform properties using {@link PropertyTransformer}.
	 * @param properties must not be {@literal null}.
	 * @return the transformed properties.
	 */
	protected Map<String, Object> doTransformProperties(Map<String, Object> properties) {
		return this.propertyTransformer.transformProperties(properties);
	}

	/**
	 * Utility method converting a {@code String/Object} map to a flat
	 * {@code String/Object} map. Nested objects are represented with property path
	 * keys.
	 * @param data the map
	 * @return the flattened map.
	 * @since 2.0
	 */
	protected Map<String, Object> flattenMap(Map<String, Object> data) {
		return JsonMapFlattener.flatten(data);
	}

}
