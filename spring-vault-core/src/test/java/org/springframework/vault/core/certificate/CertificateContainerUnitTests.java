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

package org.springframework.vault.core.certificate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.core.certificate.event.CertificateEvent;
import org.springframework.vault.core.certificate.event.CertificateExpiredEvent;
import org.springframework.vault.core.certificate.event.CertificateListener;
import org.springframework.vault.core.certificate.event.CertificateObtainedEvent;
import org.springframework.vault.support.Certificate;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.ObjectMapperSupplier;
import org.springframework.vault.support.VaultCertificateRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CertificateContainer}.
 *
 * @author Mark Paluch
 */
class CertificateContainerUnitTests {

	ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.get();

	Clock clock = Clock.fixed(Instant.now(), ZoneId.of("Z"));

	TaskScheduler taskScheduler = mock(TaskScheduler.class);

	Certificate certificate;

	@BeforeEach
	void before() {
		when(taskScheduler.getClock())
				.thenReturn(Clock.fixed(Instant.ofEpochSecond(10000000L), ZoneId.systemDefault()));
		when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mock(ScheduledFuture.class));
		Map<String, String> data = this.OBJECT_MAPPER.readValue(getClass().getResourceAsStream("/certificate.json"),
				Map.class);
		this.certificate = Certificate.of(data.get("serial_number"), data.get("certificate"),
				data.get("issuing_ca"), List.of(), 0L);
	}

	@Test
	void shouldConsiderJitter() {
		Duration threshold = Duration.ofMinutes(10);
		Duration twiceTheThreshold = threshold.plus(threshold);
		Instant expiry = clock.instant().plus(twiceTheThreshold.plusSeconds(1));

		Duration renewal1 = CertificateContainer.getRenewalDelay(clock, expiry, threshold);
		Duration renewal2 = CertificateContainer.getRenewalDelay(clock, expiry, threshold);

		assertThat(renewal1).isGreaterThan(threshold).isLessThan(twiceTheThreshold);
		assertThat(renewal2).isGreaterThan(threshold).isLessThan(twiceTheThreshold);
		assertThat(renewal1).isNotEqualTo(renewal2);
		assertThat(renewal1).isNotEqualTo(threshold.plusSeconds(1));
	}

	@Test
	void shouldConsiderThresholdTime() {
		Duration threshold = Duration.ofMinutes(10);
		Instant expiry = clock.instant().plus(Duration.ofMinutes(12));

		Duration renewalSeconds = CertificateContainer.getRenewalDelay(clock, expiry, threshold);
		assertThat(renewalSeconds).isEqualTo(Duration.ofMinutes(2));
	}

	@Test
	void shouldReportInstantRenewalTime() {

		Duration threshold = Duration.ofMinutes(10);
		Instant expiry = clock.instant().plus(Duration.ofMinutes(8));

		Duration renewal = CertificateContainer.getRenewalDelay(clock, expiry, threshold);
		assertThat(renewal).isZero();
	}

	@Test
	void shouldConsiderDuplicateRequestsAsTheSame() throws Exception {
		CertificateAuthority ca = getTrustAuthority(certificate);
		CertificateListener listener = mock(CertificateListener.class);

		CertificateContainer container = new CertificateContainer(ca, taskScheduler);
		container.afterPropertiesSet();
		AtomicReference<Certificate> certificateRef = new AtomicReference<>();

		container.addCertificateListener(listener);
		container.register(RequestedCertificate.trustAnchor("my-ca"));
		ManagedCertificate.trust("my-ca", "default", certificateRef::set).registerCertificate(container);

		container.start();
		container.stop();
		container.destroy();

		verify(listener).onCertificateEvent(any(CertificateObtainedEvent.class));
		assertThat(certificateRef).hasValue(certificate);
	}

	@Test
	void shouldNotEmitDuplicateEvents() throws Exception {
		CertificateAuthority ca = getTrustAuthority(certificate);
		CertificateListener listener = mock(CertificateListener.class);

		CertificateContainer container = new CertificateContainer(ca, taskScheduler);
		container.afterPropertiesSet();
		AtomicReference<Certificate> certificateRef = new AtomicReference<>();

		container.addCertificateListener(listener);
		container.start();

		container.register(RequestedCertificate.trustAnchor("my-ca"));
		ManagedCertificate.trust("my-ca", "default", certificateRef::set).registerCertificate(container);

		container.stop();
		container.destroy();

		verify(listener).onCertificateEvent(any(CertificateObtainedEvent.class));
		assertThat(certificateRef).hasNullValue();
	}

	@Test
	void isExpiredReturnsTrueWhenWithinThreshold() {
		CertificateAuthority ca = getTrustAuthority(certificate);
		CertificateContainer container = new CertificateContainer(ca, taskScheduler);
		container.afterPropertiesSet();

		Instant now = taskScheduler.getClock().instant();
		assertThat(container.isExpired(now.plusSeconds(30))).isTrue();
	}

	@Test
	void isExpiredReturnsFalseWhenBeyondThreshold() {
		CertificateAuthority ca = getTrustAuthority(certificate);
		CertificateContainer container = new CertificateContainer(ca, taskScheduler);
		container.afterPropertiesSet();

		Instant now = taskScheduler.getClock().instant();
		assertThat(container.isExpired(now.plusSeconds(120))).isFalse();
	}

	@Test
	void scheduledRotationPublishesCertificateExpiredEvent() throws Exception {
		Instant notAfter = certificate.getX509Certificate().getNotAfter().toInstant();
		Clock nearExpiryClock = Clock.fixed(notAfter.minusSeconds(60), ZoneId.of("Z"));

		TaskScheduler nearExpiryScheduler = mock(TaskScheduler.class);
		when(nearExpiryScheduler.getClock()).thenReturn(nearExpiryClock);
		when(nearExpiryScheduler.schedule(any(Runnable.class), any(Trigger.class)))
				.thenReturn(mock(ScheduledFuture.class));

		CertificateAuthority ca = getTrustAuthority(certificate);
		RequestedCertificate requestedCertificate = RequestedCertificate.trustAnchor("my-ca");

		List<CertificateEvent> events = new ArrayList<>();
		CertificateContainer container = new CertificateContainer(ca, nearExpiryScheduler);
		container.setExpiryThreshold(Duration.ofSeconds(120));
		container.afterPropertiesSet();
		container.addCertificateListener(events::add);
		container.register(requestedCertificate);
		container.start();

		// Simulate the scheduled rotation task firing for the old (near-expiry) cert.
		container.rotate(requestedCertificate);

		assertThat(events).hasAtLeastOneElementOfType(CertificateExpiredEvent.class);

		container.stop();
		container.destroy();
	}

	private static CertificateAuthority getTrustAuthority(Certificate certificate) {
		return new CertificateAuthority() {

			@Override
			public CertificateBundle issueCertificate(String certificateName, String role,
					VaultCertificateRequest request) {
				return null;
			}

			@Override
			public Certificate getIssuerCertificate(String certificateName, String issuer) {
				return certificate;
			}

		};
	}

}
