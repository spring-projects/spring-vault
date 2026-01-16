/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.core.lease;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.vault.core.lease.ManagedSecret.SecretAccessor;
import org.springframework.vault.core.lease.ManagedSecret.SimpleSecretAccessor;
import org.springframework.vault.core.lease.ManagedSecret.UsernamePassword;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ManagedSecret}.
 *
 * @author Mark Paluch
 */
@MockitoSettings(strictness = Strictness.LENIENT)
class ManagedSecretUnitTests {

	@Mock
	SecretsRegistry registry;

	@Test
	void shouldRegisterSecret() {
		AtomicReference<SecretAccessor> ref = new AtomicReference<>();

		RequestedSecret requestedSecret = RequestedSecret.rotating("databases/creds/mysql");
		ManagedSecret managed = ManagedSecret.rotating("databases/creds/mysql", accessor -> {
			ref.set(accessor);
		});

		ArgumentCaptor<LeaseListener> listenerCaptor = ArgumentCaptor.forClass(LeaseListener.class);

		managed.registerSecret(registry);
		verify(registry).register(eq(requestedSecret), listenerCaptor.capture());

		LeaseListener listener = listenerCaptor.getValue();
		listener.onLeaseEvent(
				new SecretLeaseCreatedEvent(requestedSecret, Lease.none(),
						Map.of("key", "value", "number", 4)));

		assertThat(ref.get()).isNotNull();
		assertThat(ref.get().getRequiredString("key")).isEqualTo("value");
	}

	@Test
	void accessorShouldReturnValues() {
		SecretAccessor accessor = new SimpleSecretAccessor(Map.of("key", "value",
				"number", 4));

		assertThat(accessor.getString("key")).isEqualTo("value");
		assertThat(accessor.getString("absent")).isNull();
		assertThat(accessor.getString("absent", "default")).isEqualTo("default");
		assertThat(accessor.getString("absent", () -> "default")).isEqualTo("default");
		assertThatExceptionOfType(ClassCastException.class).isThrownBy(() -> accessor.getString("number"));

		assertThat(accessor.getInt("number")).isEqualTo(4);
		assertThat(accessor.getInt("absent", 5)).isEqualTo(5);
		assertThat(accessor.getInt("absent", () -> 5)).isEqualTo(5);
		assertThatExceptionOfType(ClassCastException.class).isThrownBy(() -> accessor.getInt("key"));

		assertThat(accessor.getLong("number")).isEqualTo(4);
		assertThat(accessor.getLong("absent", 5)).isEqualTo(5);
		assertThat(accessor.getLong("absent", () -> 5)).isEqualTo(5);
		assertThatExceptionOfType(ClassCastException.class).isThrownBy(() -> accessor.getLong("key"));
	}

	@Test
	void accessorShouldTransformReturnValues() {
		SecretAccessor accessor = new SimpleSecretAccessor(Map.of("username", "value",
				"password", "psw"));

		accessor.as(UsernamePassword::from).applyTo((username, password) -> {
			assertThat(username).isEqualTo("value");
			assertThat(password).isEqualTo("psw");
		});

		UsernamePassword usernamePassword = accessor.as(UsernamePassword::from);
		assertThat(usernamePassword.getUsername()).isEqualTo("value");
		assertThat(usernamePassword.getPassword()).isEqualTo("psw");
	}

}
