/*
 * Copyright 2017 the original author or authors.
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
package org.springframework.vault.core.env;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.BeforeSecretLeaseRevocationEvent;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.LeaseListenerAdapter;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;
import org.springframework.vault.core.util.PropertyTransformer;
import org.springframework.vault.core.util.PropertyTransformers;
import org.springframework.vault.support.JsonMapFlattener;

/**
 * {@link PropertySource} that requests renewable secrets from
 * {@link SecretLeaseContainer}. Leases are renewed or rotated, depeding on
 * {@link RequestedSecret#getMode()}. Contents of this {@link PropertySource} is updated
 * from background threads and the content is mutable. Expiration and revocation removes
 * properties.
 *
 * @author Mark Paluch
 * @see org.springframework.core.env.PropertiesPropertySource
 * @see PropertyTransformer
 * @see PropertyTransformers
 */
public class LeaseAwareVaultPropertySource
		extends EnumerablePropertySource<VaultOperations> {

	private final static Log logger = LogFactory
			.getLog(LeaseAwareVaultPropertySource.class);

	private final SecretLeaseContainer secretLeaseContainer;

	private final RequestedSecret requestedSecret;

	private final Map<String, String> properties = new ConcurrentHashMap<String, String>();

	private final PropertyTransformer propertyTransformer;

	private final LeaseListener leaseListener;

	/**
	 * Create a new {@link LeaseAwareVaultPropertySource} given a
	 * {@link SecretLeaseContainer} and {@link RequestedSecret}. This property source
	 * requests the secret upon initialization and receives secrets once they are emitted
	 * through events published by {@link SecretLeaseContainer}.
	 *
	 * @param secretLeaseContainer must not be {@literal null}.
	 * @param requestedSecret must not be {@literal null}.
	 */
	public LeaseAwareVaultPropertySource(SecretLeaseContainer secretLeaseContainer,
			RequestedSecret requestedSecret) {
		this(requestedSecret.getPath(), secretLeaseContainer, requestedSecret);
	}

	/**
	 * Create a new {@link LeaseAwareVaultPropertySource} given a {@code name},
	 * {@link SecretLeaseContainer} and {@link RequestedSecret}. This property source
	 * requests the secret upon initialization and receives secrets once they are emitted
	 * through events published by {@link SecretLeaseContainer}.
	 *
	 * @param name name of the property source, must not be {@literal null}.
	 * @param secretLeaseContainer must not be {@literal null}.
	 * @param requestedSecret must not be {@literal null}.
	 */
	public LeaseAwareVaultPropertySource(String name,
			SecretLeaseContainer secretLeaseContainer, RequestedSecret requestedSecret) {
		this(name, secretLeaseContainer, requestedSecret, PropertyTransformers.noop());
	}

	/**
	 * Create a new {@link LeaseAwareVaultPropertySource} given a {@code name},
	 * {@link SecretLeaseContainer} and {@link RequestedSecret}. This property source
	 * requests the secret upon initialization and receives secrets once they are emitted
	 * through events published by {@link SecretLeaseContainer}.
	 *
	 * @param name name of the property source, must not be {@literal null}.
	 * @param secretLeaseContainer must not be {@literal null}.
	 * @param requestedSecret must not be {@literal null}.
	 * @param propertyTransformer object to transform properties.
	 * @see PropertyTransformers
	 */
	public LeaseAwareVaultPropertySource(String name,
			SecretLeaseContainer secretLeaseContainer, RequestedSecret requestedSecret,
			PropertyTransformer propertyTransformer) {

		super(name);

		Assert.notNull(secretLeaseContainer,
				"Path name must contain at least one character");
		Assert.notNull(requestedSecret, "SecretLeaseContainer must not be null");
		Assert.notNull(propertyTransformer, "PropertyTransformer must not be null");

		this.secretLeaseContainer = secretLeaseContainer;
		this.requestedSecret = requestedSecret;
		this.propertyTransformer = propertyTransformer;
		this.leaseListener = new LeaseListenerAdapter() {
			@Override
			public void onLeaseEvent(SecretLeaseEvent leaseEvent) {
				handleLeaseEvent(leaseEvent,
						LeaseAwareVaultPropertySource.this.properties);
			}
		};

		loadProperties();
	}

	/**
	 * Initialize property source and read properties from Vault.
	 */
	private void loadProperties() {

		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Requesting secrets from Vault at %s using %s",
					requestedSecret.getPath(), requestedSecret.getMode()));
		}

		secretLeaseContainer.addLeaseListener(leaseListener);
		secretLeaseContainer.addRequestedSecret(requestedSecret);
	}

	public RequestedSecret getRequestedSecret() {
		return requestedSecret;
	}

	@Override
	public Object getProperty(String name) {
		return this.properties.get(name);
	}

	@Override
	public String[] getPropertyNames() {

		Set<String> strings = this.properties.keySet();
		return strings.toArray(new String[strings.size()]);
	}

	// -------------------------------------------------------------------------
	// Implementation hooks and helper methods
	// -------------------------------------------------------------------------

	/**
	 * Hook method to handle a {@link SecretLeaseEvent}.
	 *
	 * @param leaseEvent must not be {@literal null}.
	 * @param properties reference to property storage of this property source.
	 */
	protected void handleLeaseEvent(SecretLeaseEvent leaseEvent,
			Map<String, String> properties) {

		if (leaseEvent.getSource() != getRequestedSecret()) {
			return;
		}

		if (leaseEvent instanceof SecretLeaseExpiredEvent
				|| leaseEvent instanceof BeforeSecretLeaseRevocationEvent
				|| leaseEvent instanceof SecretLeaseCreatedEvent) {
			properties.clear();
		}

		if (leaseEvent instanceof SecretLeaseCreatedEvent) {

			SecretLeaseCreatedEvent created = (SecretLeaseCreatedEvent) leaseEvent;
			properties.putAll(doTransformProperties(toStringMap(created.getSecrets())));
		}
	}

	/**
	 * Hook method to transform properties using {@link PropertyTransformer}.
	 *
	 * @param properties must not be {@literal null}.
	 * @return the transformed properties.
	 */
	protected Map<String, String> doTransformProperties(Map<String, String> properties) {
		return this.propertyTransformer.transformProperties(properties);
	}

	/**
	 * Utility method converting a {@code String/Object} map to a {@code String/String}
	 * map.
	 *
	 * @param data the map
	 * @return
	 */
	protected Map<String, String> toStringMap(Map<String, Object> data) {
		return JsonMapFlattener.flatten(data);
	}
}
