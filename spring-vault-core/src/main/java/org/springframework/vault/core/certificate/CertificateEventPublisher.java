/*
 * Copyright 2017-present the original author or authors.
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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.core.certificate.domain.RequestedCertificateBundle;
import org.springframework.vault.core.certificate.event.CertificateBundleIssuedEvent;
import org.springframework.vault.core.certificate.event.CertificateBundleRotatedEvent;
import org.springframework.vault.core.certificate.event.CertificateErrorEvent;
import org.springframework.vault.core.certificate.event.CertificateErrorListener;
import org.springframework.vault.core.certificate.event.CertificateEvent;
import org.springframework.vault.core.certificate.event.CertificateExpiredEvent;
import org.springframework.vault.core.certificate.event.CertificateListener;
import org.springframework.vault.core.certificate.event.CertificateObtainedEvent;
import org.springframework.vault.core.certificate.event.CertificateRotatedEvent;
import org.springframework.vault.support.Certificate;
import org.springframework.vault.support.CertificateBundle;

/**
 * Publisher for {@link CertificateEvent}s.
 * <p>This publisher dispatches events to {@link CertificateListener} and
 * {@link CertificateErrorListener}. Instances are thread-safe once
 * {@link #afterPropertiesSet() initialized}.
 *
 * @author Mark Paluch
 * @see CertificateEvent
 * @see CertificateListener
 * @see CertificateErrorListener
 */
public class CertificateEventPublisher implements InitializingBean {

	private final Set<CertificateListener> certificateListeners = new CopyOnWriteArraySet<>();

	private final Set<CertificateErrorListener> certificateErrorListeners = new CopyOnWriteArraySet<>();

	/**
	 * Add a {@link CertificateListener} to the container. The listener starts
	 * receiving events as soon as possible.
	 * @param listener lease listener, must not be {@literal null}.
	 */
	public void addCertificateListener(CertificateListener listener) {
		Assert.notNull(listener, "CertificateListener must not be null");
		this.certificateListeners.add(listener);
	}

	/**
	 * Remove a {@link CertificateListener}.
	 * @param listener must not be {@literal null}.
	 */
	public void removeCertificateListener(CertificateListener listener) {
		this.certificateListeners.remove(listener);
	}

	/**
	 * Add a {@link CertificateErrorListener} to the container. The listener starts
	 * receiving events as soon as possible.
	 * @param listener lease listener, must not be {@literal null}.
	 */
	public void addErrorListener(CertificateErrorListener listener) {
		Assert.notNull(listener, "CertificateListener must not be null");
		this.certificateErrorListeners.add(listener);
	}

	/**
	 * Remove a {@link CertificateErrorListener}.
	 * @param listener must not be {@literal null}.
	 */
	public void removeCertificateErrorListener(CertificateErrorListener listener) {
		this.certificateErrorListeners.remove(listener);
	}

	@Override
	public void afterPropertiesSet() {
	}

	/**
	 * Hook method called when a certificate was obtained. The default
	 * implementation is to notify {@link CertificateListener}. Implementations can
	 * override this method in subclasses.
	 * @param requestedCertificate must not be {@literal null}.
	 * @param certificate must not be {@literal null}.
	 * @see CertificateObtainedEvent
	 */
	protected void onCertificateObtained(RequestedCertificate requestedCertificate, Certificate certificate) {
		if (requestedCertificate instanceof RequestedCertificateBundle
				&& certificate instanceof CertificateBundle bundle) {
			dispatch(new CertificateBundleIssuedEvent(requestedCertificate, bundle));
		} else {
			dispatch(new CertificateObtainedEvent(requestedCertificate, certificate));
		}
	}

	/**
	 * Hook method called when certificates were rotated. The default implementation
	 * is to notify {@link CertificateListener}. Implementations can override this
	 * method in subclasses.
	 * @param requestedCertificate must not be {@literal null}.
	 * @param certificate must not be {@literal null}.
	 * @see CertificateRotatedEvent
	 */
	protected void onCertificateRotated(RequestedCertificate requestedCertificate, Certificate certificate) {
		if (requestedCertificate instanceof RequestedCertificateBundle
				&& certificate instanceof CertificateBundle bundle) {
			dispatch(new CertificateBundleRotatedEvent(requestedCertificate, bundle));
		} else {
			dispatch(new CertificateRotatedEvent(requestedCertificate, certificate));
		}
	}

	/**
	 * Hook method called when a {@link Certificate} expires. The default
	 * implementation is to notify {@link CertificateListener}. Implementations can
	 * override this method in subclasses.
	 * @param requestedCertificate must not be {@literal null}.
	 * @param certificate must not be {@literal null}.
	 * @see CertificateExpiredEvent
	 */
	protected void onCertificateExpired(RequestedCertificate requestedCertificate, Certificate certificate) {
		dispatch(new CertificateExpiredEvent(requestedCertificate, certificate));
	}

	/**
	 * Hook method called when an error occurred during certificate issuance or
	 * retrieval. The default implementation notifies
	 * {@link CertificateErrorListener}. Implementations can override this method in
	 * subclasses.
	 * @param requestedCertificate must not be {@literal null}.
	 * @param e the causing exception.
	 * @see CertificateErrorEvent
	 */
	protected void onError(RequestedCertificate requestedCertificate, Exception e) {
		dispatch(new CertificateErrorEvent(requestedCertificate, e));
	}

	/**
	 * Dispatch the event to all {@link CertificateListener}s.
	 * @param certificateEvent the event to dispatch.
	 */
	void dispatch(CertificateEvent certificateEvent) {
		for (CertificateListener listener : this.certificateListeners) {
			listener.onCertificateEvent(certificateEvent);
		}
	}

	/**
	 * Dispatch the event to all {@link CertificateErrorListener}s.
	 * @param errorEvent the event to dispatch.
	 */
	void dispatch(CertificateErrorEvent errorEvent) {
		if (this.certificateErrorListeners.isEmpty()) {
			LoggingErrorListener.INSTANCE.onCertificateError(errorEvent, (Exception) errorEvent.getException());
		} else {
			for (CertificateErrorListener listener : this.certificateErrorListeners) {
				listener.onCertificateError(errorEvent, (Exception) errorEvent.getException());
			}
		}
	}


	/**
	 * Simple {@link CertificateErrorListener} implementation to log errors.
	 */
	public enum LoggingErrorListener implements CertificateErrorListener {

		INSTANCE;

		@SuppressWarnings("FieldMayBeFinal") // allow setting via reflection.
		private static Log logger = LogFactory.getLog(LoggingErrorListener.class);

		@Override
		public void onCertificateError(CertificateEvent certificateEvent, Exception exception) {
			logger.warn("[%s] %s".formatted(certificateEvent.getSource(), exception.getMessage()),
					exception);
		}

	}

}
