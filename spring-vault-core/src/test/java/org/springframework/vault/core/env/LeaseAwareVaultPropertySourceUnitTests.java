/*
 * Copyright 2019-2024 the original author or authors.
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
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseErrorListener;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseErrorEvent;
import org.springframework.vault.core.lease.event.SecretLeaseRotatedEvent;
import org.springframework.vault.core.lease.event.SecretNotFoundEvent;
import org.springframework.vault.core.util.PropertyTransformers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LeaseAwareVaultPropertySource}.
 *
 * @author Mark Paluch
 */
@ExtendWith(MockitoExtension.class)
class LeaseAwareVaultPropertySourceUnitTests {

	@Mock
	SecretLeaseContainer leaseContainer;

	@Test
	void shouldLoadProperties() {

		RequestedSecret secret = RequestedSecret.renewable("my-path");

		List<LeaseListener> listeners = new ArrayList<>();
		doAnswer(invocation -> {
			listeners.add(invocation.getArgument(0));
			return null;
		}).when(this.leaseContainer).addLeaseListener(any());
		when(this.leaseContainer.addRequestedSecret(any())).then(invocation -> {

			listeners.forEach(
					leaseListener -> leaseListener.onLeaseEvent(new SecretLeaseCreatedEvent(invocation.getArgument(0),
							Lease.none(), Collections.singletonMap("key", "value"))));
			return invocation.getArgument(0);
		});

		LeaseAwareVaultPropertySource propertySource = new LeaseAwareVaultPropertySource(this.leaseContainer, secret);

		assertThat(propertySource.getPropertyNames()).containsOnly("key");
	}

	@Test
	void shouldRotateSecrets() {

		RequestedSecret secret = RequestedSecret.renewable("my-path");

		List<LeaseListener> listeners = new ArrayList<>();
		doAnswer(invocation -> {
			listeners.add(invocation.getArgument(0));
			return null;
		}).when(this.leaseContainer).addLeaseListener(any());
		when(this.leaseContainer.addRequestedSecret(any())).then(invocation -> {

			listeners.forEach(
					leaseListener -> leaseListener.onLeaseEvent(new SecretLeaseCreatedEvent(invocation.getArgument(0),
							Lease.none(), Collections.singletonMap("key", "value"))));
			return invocation.getArgument(0);
		});

		LeaseAwareVaultPropertySource propertySource = new LeaseAwareVaultPropertySource(this.leaseContainer, secret);

		listeners.forEach(it -> it.onLeaseEvent(new SecretLeaseRotatedEvent(secret, Lease.none(), Lease.none(),
				Collections.singletonMap("new-key", "value"))));

		assertThat(propertySource.getPropertyNames()).containsOnly("new-key");
	}

	@Test
	void ignoresNotFoundByDefault() {

		RequestedSecret secret = RequestedSecret.renewable("my-path");

		List<LeaseListener> listeners = new ArrayList<>();
		doAnswer(invocation -> {
			listeners.add(invocation.getArgument(0));
			return null;
		}).when(this.leaseContainer).addLeaseListener(any());
		when(this.leaseContainer.addRequestedSecret(any())).then(invocation -> {

			listeners.forEach(leaseListener -> leaseListener
				.onLeaseEvent(new SecretNotFoundEvent(invocation.getArgument(0), Lease.none())));
			return invocation.getArgument(0);
		});

		LeaseAwareVaultPropertySource propertySource = new LeaseAwareVaultPropertySource(this.leaseContainer, secret);

		assertThat(propertySource.getPropertyNames()).isEmpty();
	}

	@Test
	void ignoresErrorsByDefault() {

		RequestedSecret secret = RequestedSecret.renewable("my-path");

		List<LeaseErrorListener> errorListeners = new ArrayList<>();
		doAnswer(invocation -> {
			errorListeners.add(invocation.getArgument(0));
			return null;
		}).when(this.leaseContainer).addErrorListener(any());
		when(this.leaseContainer.addRequestedSecret(any())).then(invocation -> {

			errorListeners.forEach(leaseListener -> {
				RuntimeException exception = new RuntimeException("Backend error");
				leaseListener.onLeaseError(new SecretLeaseErrorEvent(secret, Lease.none(), exception), exception);
			});
			return invocation.getArgument(0);
		});

		LeaseAwareVaultPropertySource propertySource = new LeaseAwareVaultPropertySource(this.leaseContainer, secret);

		assertThat(propertySource.getPropertyNames()).isEmpty();
	}

	@Test
	void propagatesErrorIfIgnoreResourceNotFoundIsFalse() {

		RequestedSecret secret = RequestedSecret.renewable("my-path");

		List<LeaseErrorListener> errorListeners = new ArrayList<>();
		doAnswer(invocation -> {
			errorListeners.add(invocation.getArgument(0));
			return null;
		}).when(this.leaseContainer).addErrorListener(any());
		when(this.leaseContainer.addRequestedSecret(any())).then(invocation -> {

			errorListeners.forEach(leaseListener -> {
				RuntimeException exception = new RuntimeException("Backend error");
				leaseListener.onLeaseError(new SecretLeaseErrorEvent(secret, Lease.none(), exception), exception);
			});
			return invocation.getArgument(0);
		});

		assertThatThrownBy(() -> new LeaseAwareVaultPropertySource("name", this.leaseContainer, secret,
				PropertyTransformers.noop(), false))
			.isInstanceOf(VaultPropertySourceNotFoundException.class)
			.hasRootCauseExactlyInstanceOf(RuntimeException.class);
	}

}
