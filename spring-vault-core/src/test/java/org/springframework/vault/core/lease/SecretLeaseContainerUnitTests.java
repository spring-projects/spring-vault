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
package org.springframework.vault.core.lease;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.AfterSecretLeaseRevocationEvent;
import org.springframework.vault.core.lease.event.BeforeSecretLeaseRevocationEvent;
import org.springframework.vault.core.lease.event.LeaseListenerAdapter;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseErrorEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;
import org.springframework.vault.core.lease.event.SecretLeaseRotatedEvent;
import org.springframework.vault.core.lease.event.SecretNotFoundEvent;
import org.springframework.vault.support.LeaseStrategy;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.HttpClientErrorException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SecretLeaseContainer}.
 *
 * @author Mark Paluch
 * @author Steven Swor
 * @author Thomas Kåsene
 */
@ExtendWith(MockitoExtension.class)
class SecretLeaseContainerUnitTests {

	@Mock
	VaultOperations vaultOperations;

	@Mock
	TaskScheduler taskScheduler;

	@Mock
	ScheduledFuture scheduledFuture;

	@Mock
	LeaseListenerAdapter leaseListenerAdapter;

	@Captor
	ArgumentCaptor<SecretLeaseEvent> captor;

	RequestedSecret requestedSecret = RequestedSecret.renewable("my-secret");

	RequestedSecret rotatingGenericSecret = RequestedSecret.rotating("rotating-generic");

	SecretLeaseContainer secretLeaseContainer;

	@BeforeEach
	void before() throws Exception {

		this.secretLeaseContainer = new SecretLeaseContainer(this.vaultOperations, this.taskScheduler);
		this.secretLeaseContainer.addLeaseListener(this.leaseListenerAdapter);
		this.secretLeaseContainer.addErrorListener(this.leaseListenerAdapter);
		this.secretLeaseContainer.afterPropertiesSet();
	}

	@Test
	void shouldSetProperties() {

		this.secretLeaseContainer.setMinRenewal(Duration.ofMinutes(2));
		this.secretLeaseContainer.setExpiryThreshold(Duration.ofMinutes(3));

		assertThat(this.secretLeaseContainer.getMinRenewal().getSeconds()).isEqualTo(120);
		assertThat(this.secretLeaseContainer.getMinRenewalSeconds()).isEqualTo(120);
		assertThat(this.secretLeaseContainer.getExpiryThreshold().getSeconds()).isEqualTo(180);
		assertThat(this.secretLeaseContainer.getExpiryThresholdSeconds()).isEqualTo(180);
	}

	@Test
	void shouldWorkIfNoSecretsRequested() {

		this.secretLeaseContainer.start();

		verifyNoInteractions(this.leaseListenerAdapter);
	}

	@Test
	void shouldWorkIfNoSecretsFound() {

		this.secretLeaseContainer.start();

		this.secretLeaseContainer.requestRenewableSecret(this.requestedSecret.getPath());

		verify(this.leaseListenerAdapter).onLeaseEvent(any(SecretNotFoundEvent.class));
		verifyNoMoreInteractions(this.leaseListenerAdapter);
	}

	@Test
	void shouldAcceptSecretsWithoutLease() {

		VaultResponse secrets = new VaultResponse();
		secrets.setData(Collections.singletonMap("key", (Object) "value"));

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(secrets);

		this.secretLeaseContainer.addRequestedSecret(this.requestedSecret);
		this.secretLeaseContainer.start();

		verifyNoInteractions(this.taskScheduler);
		verify(this.leaseListenerAdapter).onLeaseEvent(this.captor.capture());

		SecretLeaseCreatedEvent leaseCreatedEvent = (SecretLeaseCreatedEvent) this.captor.getValue();

		assertThat(leaseCreatedEvent.getSource()).isEqualTo(this.requestedSecret);
		assertThat(leaseCreatedEvent.getLease()).isNotNull();
		assertThat(leaseCreatedEvent.getSecrets()).containsKey("key");
	}

	@Test
	void shouldAcceptSecretsWithStaticLease() {

		VaultResponse secrets = new VaultResponse();
		secrets.setLeaseId("lease");
		secrets.setRenewable(false);
		secrets.setData(Collections.singletonMap("key", "value"));

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(secrets);

		this.secretLeaseContainer.addRequestedSecret(this.requestedSecret);
		this.secretLeaseContainer.start();

		verifyNoInteractions(this.taskScheduler);
		verify(this.leaseListenerAdapter).onLeaseEvent(this.captor.capture());

		SecretLeaseCreatedEvent leaseCreatedEvent = (SecretLeaseCreatedEvent) this.captor.getValue();

		assertThat(leaseCreatedEvent.getSource()).isEqualTo(this.requestedSecret);
		assertThat(leaseCreatedEvent.getLease()).isNotNull();
		assertThat(leaseCreatedEvent.getSecrets()).containsKey("key");
	}

	@Test
	void shouldPropagateErrorsToListenerOnInitialRetrieval() {

		VaultException e = new VaultException("error");
		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenThrow(e);

		this.secretLeaseContainer.addRequestedSecret(this.requestedSecret);

		this.secretLeaseContainer.start();

		verify(this.leaseListenerAdapter).onLeaseError(this.captor.capture(), eq(e));
		verifyNoMoreInteractions(this.leaseListenerAdapter);

		SecretLeaseEvent leaseEvent = this.captor.getValue();

		assertThat(leaseEvent.getSource()).isEqualTo(this.requestedSecret);
		assertThat(leaseEvent.getLease()).isEqualTo(Lease.none());
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldAcceptSecretsWithRenewableLease() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(createSecrets());

		this.secretLeaseContainer.addRequestedSecret(this.requestedSecret);
		this.secretLeaseContainer.start();

		verify(this.taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRenewLease() {

		prepareRenewal();

		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
			.thenReturn(Lease.of("new_lease", Duration.ofSeconds(70), true));

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));

		captor.getValue().run();
		verifyNoInteractions(this.scheduledFuture);
		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRenewLeaseNow() {

		prepareRenewal();

		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
			.thenReturn(Lease.of("new_lease", Duration.ofSeconds(70), true));

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));

		this.secretLeaseContainer.renew(this.requestedSecret);

		verify(this.vaultOperations).doWithSession(any(RestOperationsCallback.class));
		verify(this.scheduledFuture).cancel(false);
		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRenewLeaseAfterFailure() {

		prepareRenewal();
		AtomicInteger attempts = new AtomicInteger();
		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class))).then(invocation -> {

			int attempt = attempts.incrementAndGet();
			if (attempt == 1) {
				throw new VaultException("Renewal failure");
			}

			return Lease.of("new_lease", Duration.ofSeconds(70), true);
		});

		this.secretLeaseContainer.setLeaseStrategy(LeaseStrategy.retainOnError());
		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));
		captor.getValue().run();

		boolean renewed = this.secretLeaseContainer.renew(this.requestedSecret);
		assertThat(renewed).isTrue();

		verify(this.vaultOperations, times(2)).doWithSession(any(RestOperationsCallback.class));
		verify(this.scheduledFuture).cancel(false);
		verify(this.taskScheduler, times(3)).schedule(captor.capture(), any(Trigger.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	void shouldRetainLeaseAfterRenewalFailure() {

		prepareRenewal();
		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
			.thenThrow(new VaultException("Renewal failure"));

		this.secretLeaseContainer.setLeaseStrategy(LeaseStrategy.retainOnError());
		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));
		captor.getValue().run();

		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));
		captor.getValue().run();

		verify(this.vaultOperations, times(2)).doWithSession(any(RestOperationsCallback.class));
	}

	@Test
	void shouldRotateNonRenewableLease() {

		final List<SecretLeaseEvent> events = new ArrayList<SecretLeaseEvent>();
		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(createSecrets("key", "value", false),
				createSecrets("key", "value2", false));

		this.secretLeaseContainer.addRequestedSecret(RequestedSecret.rotating(this.requestedSecret.getPath()));
		this.secretLeaseContainer.addLeaseListener(new LeaseListenerAdapter() {
			@Override
			public void onLeaseEvent(SecretLeaseEvent leaseEvent) {
				events.add(leaseEvent);
			}
		});

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));

		captor.getValue().run();
		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));

		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(SecretLeaseCreatedEvent.class);
		assertThat(events.get(1)).isInstanceOf(SecretLeaseRotatedEvent.class);

		SecretLeaseRotatedEvent rotated = (SecretLeaseRotatedEvent) events.get(1);
		assertThat(rotated.getSecrets()).containsEntry("key", "value2");
	}

	@Test
	void shouldRotateGenericSecret() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		when(this.vaultOperations.read(this.rotatingGenericSecret.getPath())).thenReturn(
				createGenericSecrets(Collections.singletonMap("key", (Object) "value")),
				createGenericSecrets(Collections.singletonMap("foo", (Object) "bar")));

		this.secretLeaseContainer.addRequestedSecret(this.rotatingGenericSecret);

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));

		captor.getValue().run();
		verifyNoInteractions(this.scheduledFuture);
		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));

		ArgumentCaptor<SecretLeaseEvent> createdEvents = ArgumentCaptor.forClass(SecretLeaseEvent.class);
		verify(this.leaseListenerAdapter, times(2)).onLeaseEvent(createdEvents.capture());

		List<SecretLeaseEvent> events = createdEvents.getAllValues();

		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(SecretLeaseCreatedEvent.class);
		assertThat(((SecretLeaseCreatedEvent) events.get(0)).getSecrets()).containsOnlyKeys("key");

		assertThat(events.get(1)).isInstanceOf(SecretLeaseRotatedEvent.class);
		assertThat(((SecretLeaseCreatedEvent) events.get(1)).getSecrets()).containsOnlyKeys("foo");
	}

	@Test
	void failedRotateShouldEmitExpiredAndErrorEvents() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		when(this.vaultOperations.read(this.rotatingGenericSecret.getPath()))
				.thenReturn(createGenericSecrets(Collections.singletonMap("key", "value")))
				.thenThrow(new HttpClientErrorException(HttpStatus.I_AM_A_TEAPOT));

		this.secretLeaseContainer.addRequestedSecret(this.rotatingGenericSecret);

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));

		captor.getValue().run();
		verifyNoInteractions(this.scheduledFuture);

		ArgumentCaptor<SecretLeaseEvent> createdEvents = ArgumentCaptor.forClass(SecretLeaseEvent.class);
		verify(this.leaseListenerAdapter, times(2)).onLeaseEvent(createdEvents.capture());

		ArgumentCaptor<SecretLeaseErrorEvent> errorEvents = ArgumentCaptor.forClass(SecretLeaseErrorEvent.class);
		verify(this.leaseListenerAdapter).onLeaseError(errorEvents.capture(), any());

		List<SecretLeaseEvent> events = createdEvents.getAllValues();

		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(SecretLeaseCreatedEvent.class);
		assertThat(((SecretLeaseCreatedEvent) events.get(0)).getSecrets()).containsOnlyKeys("key");

		assertThat(events.get(1)).isInstanceOf(SecretLeaseExpiredEvent.class);

		assertThat(errorEvents.getAllValues()).hasSize(1);
	}

	@Test
	void shouldRotateGenericSecretNow() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		when(this.vaultOperations.read(this.rotatingGenericSecret.getPath())).thenReturn(
				createGenericSecrets(Collections.singletonMap("key", "value")),
				createGenericSecrets(Collections.singletonMap("foo", "bar")));

		this.secretLeaseContainer.addRequestedSecret(this.rotatingGenericSecret);
		this.secretLeaseContainer.start();

		this.secretLeaseContainer.rotate(this.rotatingGenericSecret);

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));
		verify(this.scheduledFuture).cancel(false);
		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));

		ArgumentCaptor<SecretLeaseEvent> createdEvents = ArgumentCaptor.forClass(SecretLeaseEvent.class);
		verify(this.leaseListenerAdapter, times(2)).onLeaseEvent(createdEvents.capture());
	}

	@Test
	void shouldNotRenewExpiringLease() {

		prepareRenewal();
		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
			.thenReturn(Lease.of("new_lease", Duration.ofSeconds(5), true));

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));

		captor.getValue().run();
		verifyNoInteractions(this.scheduledFuture);
		verify(this.taskScheduler, times(1)).schedule(captor.capture(), any(Trigger.class));
		verify(this.leaseListenerAdapter).onLeaseEvent(any(SecretLeaseCreatedEvent.class));
		verify(this.leaseListenerAdapter).onLeaseEvent(any(SecretLeaseExpiredEvent.class));
	}

	@Test
	void shouldNotRotateExpiringLease() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		VaultResponse first = createSecrets();
		VaultResponse second = createSecrets();
		second.setData(Collections.singletonMap("foo", (Object) "bar"));

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(first, second);
		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
				.thenReturn(Lease.of("new_lease", Duration.ofSeconds(5), true));

		this.secretLeaseContainer.requestRotatingSecret("my-secret");

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));

		captor.getValue().run();
		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));

		ArgumentCaptor<SecretLeaseEvent> createdEvents = ArgumentCaptor.forClass(SecretLeaseEvent.class);
		verify(this.leaseListenerAdapter, times(2)).onLeaseEvent(createdEvents.capture());

		List<SecretLeaseEvent> events = createdEvents.getAllValues();

		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(SecretLeaseCreatedEvent.class);
		assertThat(((SecretLeaseCreatedEvent) events.get(0)).getSecrets()).containsOnlyKeys("key");

		assertThat(events.get(1)).isInstanceOf(SecretLeaseRotatedEvent.class);
		assertThat(((SecretLeaseCreatedEvent) events.get(1)).getSecrets()).containsOnlyKeys("foo");
	}

	@SuppressWarnings("unchecked")
	@Test
	void secretShouldContinueOnRestartSecrets() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		VaultResponse first = createSecrets();
		VaultResponse second = createSecrets();
		second.setData(Collections.singletonMap("foo", (Object) "bar"));

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(first, second);
		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
				.thenReturn(Lease.of("after_restart", Duration.ofSeconds(1), true));

		RequestedSecret secret = RequestedSecret.rotating("my-secret");

		this.secretLeaseContainer.setExpiryThreshold(Duration.ofSeconds(1));
		this.secretLeaseContainer.addRequestedSecret(secret);
		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		this.secretLeaseContainer.restartSecrets();

		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));
		captor.getAllValues().get(0).run(); // old lease run
		captor.getAllValues().get(1).run(); // new lease run

		// one from restartSecrets, the second from the scheduler detecting expiry
		verify(this.leaseListenerAdapter, times(2)).onLeaseEvent(any(SecretLeaseRotatedEvent.class));
		verify(this.leaseListenerAdapter, never()).onLeaseEvent(any(SecretLeaseExpiredEvent.class));
		verify(this.leaseListenerAdapter, never()).onLeaseEvent(any(SecretLeaseErrorEvent.class));
	}

	@SuppressWarnings("unchecked")
	@Test
	void concurrentRenewalAfterRestartSecretShouldContinueOnRestartSecrets() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		VaultResponse first = createSecrets();
		VaultResponse second = createSecrets();
		second.setData(Collections.singletonMap("foo", (Object) "bar"));

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(first, second);
		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
				.thenReturn(Lease.of("after_restart", Duration.ofSeconds(1), true));

		RequestedSecret secret = RequestedSecret.rotating("my-secret");

		this.secretLeaseContainer.setExpiryThreshold(Duration.ofSeconds(1));
		this.secretLeaseContainer.addRequestedSecret(secret);
		this.secretLeaseContainer.start();

		Map<RequestedSecret, SecretLeaseContainer.LeaseRenewalScheduler> renewals = (Map<RequestedSecret, SecretLeaseContainer.LeaseRenewalScheduler>) ReflectionTestUtils.getField(this.secretLeaseContainer, "renewals");

		SecretLeaseContainer.LeaseRenewalScheduler scheduler = renewals.get(secret);
		Lease lease = scheduler.getLease();
		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		this.secretLeaseContainer.restartSecrets();

		scheduler.associateLease(lease);

		verify(this.taskScheduler, times(2)).schedule(captor.capture(), any(Trigger.class));
		captor.getAllValues().get(1).run(); // new lease run
		captor.getAllValues().get(0).run(); // old lease run

		// one from restartSecrets, the second from the scheduler detecting expiry
		verify(this.leaseListenerAdapter, times(2)).onLeaseEvent(any(SecretLeaseRotatedEvent.class));
		verify(this.leaseListenerAdapter).onLeaseEvent(any(SecretLeaseExpiredEvent.class));
		verify(this.leaseListenerAdapter, never()).onLeaseEvent(any(SecretLeaseErrorEvent.class));
	}

	@Test
	void scheduleRenewalShouldApplyExpiryThreshold() {

		prepareRenewal();

		this.secretLeaseContainer.start();

		ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
		verify(this.taskScheduler).schedule(any(Runnable.class), captor.capture());

		Instant nextExecutionTime = captor.getValue().nextExecution(null);
		assertThat(nextExecutionTime).isBetween(Instant.now().plusSeconds(35), Instant.now().plusSeconds(41));
	}

	@Test
	void shouldPublishRenewalErrors() {

		prepareRenewal();
		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
			.thenThrow(new HttpClientErrorException(HttpStatus.I_AM_A_TEAPOT));

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		verify(this.leaseListenerAdapter).onLeaseEvent(any(SecretLeaseCreatedEvent.class));
		verify(this.leaseListenerAdapter).onLeaseError(this.captor.capture(), any(VaultException.class));
		verifyNoMoreInteractions(this.leaseListenerAdapter);

		SecretLeaseEvent leaseEvent = this.captor.getValue();

		assertThat(leaseEvent.getSource()).isEqualTo(this.requestedSecret);
		assertThat(leaseEvent.getLease()).isNotNull();
	}

	@Test
	@SuppressWarnings("unchecked")
	void subsequentScheduleRenewalShouldApplyExpiryThreshold() {

		prepareRenewal();

		when(this.vaultOperations.doWithSession(any(RestOperationsCallback.class)))
			.thenReturn(Lease.of("new_lease", Duration.ofSeconds(70), true));

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));

		runnableCaptor.getValue().run();

		ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
		verify(this.taskScheduler, times(2)).schedule(any(Runnable.class), captor.capture());

		assertThat(captor.getAllValues().get(0).nextExecution(null)).isBetween(Instant.now().plusSeconds(31),
				Instant.now().plusSeconds(41));

		assertThat(captor.getAllValues().get(1).nextExecution(null)).isBetween(Instant.now().plusSeconds(9),
				Instant.now().plusSeconds(11));
	}

	@Test
	void scheduleRenewalShouldTriggerOnlyOnce() {

		prepareRenewal();

		this.secretLeaseContainer.start();

		ArgumentCaptor<Trigger> captor = ArgumentCaptor.forClass(Trigger.class);
		verify(this.taskScheduler).schedule(any(Runnable.class), captor.capture());

		Trigger trigger = captor.getValue();

		assertThat(trigger.nextExecution(null)).isNotNull();
		assertThat(trigger.nextExecution(null)).isNull();
	}

	@Test
	void subsequentStartShouldNoOp() {

		prepareRenewal();

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));

		this.secretLeaseContainer.start();

		verifyNoMoreInteractions(this.scheduledFuture);
		verifyNoMoreInteractions(this.taskScheduler);
	}

	@Test
	void canceledRenewalShouldSkipRenewal() {

		prepareRenewal();

		this.secretLeaseContainer.start();

		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
		verify(this.taskScheduler).schedule(captor.capture(), any(Trigger.class));
		verify(this.vaultOperations).read(eq("sys/internal/ui/mounts/my-secret"));
		verify(this.vaultOperations).read(eq("my-secret"));

		this.secretLeaseContainer.stop();

		verify(this.scheduledFuture).cancel(false);

		captor.getValue().run();

		verifyNoMoreInteractions(this.vaultOperations);
	}

	@Test
	void shouldDisableRenewalOnDisposal() throws Exception {

		prepareRenewal();

		this.secretLeaseContainer.start();
		this.secretLeaseContainer.destroy();

		verify(this.vaultOperations).doWithSession(any(RestOperationsCallback.class));
		verify(this.scheduledFuture).cancel(false);
		verify(this.leaseListenerAdapter).onLeaseEvent(any(SecretLeaseCreatedEvent.class));
		verify(this.leaseListenerAdapter).onLeaseEvent(any(BeforeSecretLeaseRevocationEvent.class));
		verify(this.leaseListenerAdapter).onLeaseEvent(any(AfterSecretLeaseRevocationEvent.class));
	}

	@Test
	void shouldNotRevokeSecretsWithoutLease() throws Exception {

		VaultResponse secrets = new VaultResponse();
		secrets.setData(Collections.singletonMap("key", (Object) "value"));

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(secrets);

		this.secretLeaseContainer.addRequestedSecret(this.requestedSecret);
		this.secretLeaseContainer.start();

		this.secretLeaseContainer.destroy();

		verifyNoInteractions(this.taskScheduler);

		verify(this.leaseListenerAdapter, never()).onLeaseEvent(any(BeforeSecretLeaseRevocationEvent.class));
		verify(this.leaseListenerAdapter, never()).onLeaseEvent(any(AfterSecretLeaseRevocationEvent.class));
	}

	@Test
	void shouldRevokeSecretsOnDestroy() throws Exception {

		VaultResponse secrets = new VaultResponse();
		secrets.setData(Collections.singletonMap("key", (Object) "value"));
		secrets.setLeaseId("1234");
		secrets.setLeaseDuration(1000);

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(secrets);

		this.secretLeaseContainer.addRequestedSecret(this.requestedSecret);
		this.secretLeaseContainer.start();
		this.secretLeaseContainer.stop();

		this.secretLeaseContainer.destroy();

		verify(this.leaseListenerAdapter).onLeaseEvent(any(SecretLeaseCreatedEvent.class));
		verify(this.leaseListenerAdapter).onLeaseEvent(any(BeforeSecretLeaseRevocationEvent.class));
		verify(this.leaseListenerAdapter).onLeaseEvent(any(AfterSecretLeaseRevocationEvent.class));
	}

	@Test
	void shouldRequestRotatingGenericSecrets() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		when(this.vaultOperations.read(this.rotatingGenericSecret.getPath())).thenReturn(createGenericSecrets());

		this.secretLeaseContainer.addRequestedSecret(this.rotatingGenericSecret);
		this.secretLeaseContainer.start();

		verify(this.leaseListenerAdapter).onLeaseEvent(this.captor.capture());

		SecretLeaseCreatedEvent leaseCreatedEvent = (SecretLeaseCreatedEvent) this.captor.getValue();

		assertThat(leaseCreatedEvent.getSource()).isEqualTo(this.rotatingGenericSecret);
		assertThat(leaseCreatedEvent.getLease()).isNotNull();
		assertThat(leaseCreatedEvent.getSecrets()).containsKey("key");
	}

	@SuppressWarnings("unchecked")
	private void prepareRenewal() {

		when(this.taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(this.scheduledFuture);

		when(this.vaultOperations.read(this.requestedSecret.getPath())).thenReturn(createSecrets());

		this.secretLeaseContainer.addRequestedSecret(this.requestedSecret);
	}

	private VaultResponse createSecrets() {
		return createSecrets("key", "value", true);
	}

	private VaultResponse createSecrets(String key, String value, boolean renewable) {

		VaultResponse secrets = new VaultResponse();

		secrets.setLeaseId("lease");
		secrets.setRenewable(renewable);
		secrets.setLeaseDuration(100);
		secrets.setData(Collections.singletonMap(key, value));

		return secrets;
	}

	private VaultResponse createGenericSecrets() {
		return createGenericSecrets(Collections.singletonMap("key", "value"));
	}

	private VaultResponse createGenericSecrets(Map<String, Object> data) {

		VaultResponse secrets = new VaultResponse();

		secrets.setRenewable(false);
		secrets.setLeaseDuration(100);
		secrets.setData(data);

		return secrets;
	}

}
