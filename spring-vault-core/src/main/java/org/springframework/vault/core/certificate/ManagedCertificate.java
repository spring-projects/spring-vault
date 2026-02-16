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

import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.vault.core.certificate.domain.RequestedCertificate;
import org.springframework.vault.core.certificate.domain.RequestedCertificateBundle;
import org.springframework.vault.core.certificate.domain.RequestedTrustAnchor;
import org.springframework.vault.core.certificate.event.CertificateBundleIssuedEvent;
import org.springframework.vault.core.certificate.event.CertificateEvent;
import org.springframework.vault.core.certificate.event.CertificateListener;
import org.springframework.vault.core.certificate.event.CertificateListenerAdapter;
import org.springframework.vault.core.certificate.event.CertificateObtainedEvent;
import org.springframework.vault.support.Certificate;
import org.springframework.vault.support.CertificateBundle;
import org.springframework.vault.support.VaultCertificateRequest;

/**
 * Value object to simplify management of a certificated issued by (or obtained
 * from) Vault using functional callbacks. A managed {@link RequestedCertificate
 * certificate} registers with {@link CertificateRegistry} and subscribes to
 * certificate events of certificate retrieval, issuance, and
 * reissuance/rotation for propagation to a consumer.
 *
 * <p>Example usage:
 *
 * <pre class="code">
 * ManagedCertificate managed = ManagedCertificate.issue("my-bundle", "my-role", certificateRequest, bundle -> {
 *   bundle.createKeyStore("my-alias");
 * });
 * </pre>
 *
 * <pre class="code">
 * ManagedCertificate managed = ManagedCertificate.trust("my-bundle", bundle -> {
 *   bundle.createTrustStore();
 * });
 * </pre>
 *
 * <p>A {@code ManagedCertificate} object is activated through
 * {@link #registerCertificate(CertificateRegistry) registration} with a running
 * {@link CertificateRegistry} and can be subject to container lifecycle
 * management.
 *
 * @author Mark Paluch
 * @since 4.1
 * @see RequestedCertificateBundle
 */
public class ManagedCertificate implements CertificateRegistrar {

	private static final Log logger = LogFactory.getLog(ManagedCertificate.class);


	private final RequestedCertificate requestedCertificate;

	private final CertificateListener listener;


	private ManagedCertificate(RequestedCertificate requestedCertificate, CertificateListenerAdapter listener) {
		this.requestedCertificate = requestedCertificate;
		this.listener = listener;
	}


	/**
	 * Create an issued {@code ManagedCertificate} bundle consisting of an X.509
	 * Certificate and its private key. The {@code bundleConsumer} will be invoked
	 * with the issued (or obtained) {@link CertificateBundle} on certificate
	 * container startup and each time the certificate is rotated.
	 *
	 * @param name name of the certificate bundle
	 * @param role Vault role name to issue the certificate against.
	 * @param certificateRequest the certificate request describing the certificate
	 * to issue.
	 * @param bundleConsumer consumer for certificate bundle access.
	 * @return the managed certificate bundle object.
	 */
	public static ManagedCertificate issue(String name, String role,
			VaultCertificateRequest certificateRequest,
			Consumer<CertificateBundle> bundleConsumer) {
		return issue(name, role, certificateRequest, bundleConsumer,
				throwable -> onError(name, throwable));
	}

	/**
	 * Create an issued {@code ManagedCertificate} bundle consisting of an X.509
	 * Certificate and its private key. The {@code bundleConsumer} will be invoked
	 * with the issued (or obtained) {@link CertificateBundle} on certificate
	 * container startup and each time the certificate is rotated.
	 *
	 * @param name name of the certificate bundle.
	 * @param role Vault role name to issue the certificate against.
	 * @param certificateRequest the certificate request describing the certificate
	 * to issue.
	 * @param bundleConsumer consumer for certificate bundle access.
	 * @param errorConsumer consumer for errors.
	 * @return the managed certificate bundle object.
	 */
	public static ManagedCertificate issue(String name, String role,
			VaultCertificateRequest certificateRequest,
			Consumer<CertificateBundle> bundleConsumer,
			Consumer<Throwable> errorConsumer) {
		return issue(RequestedCertificate.issue(name, role, certificateRequest), bundleConsumer, errorConsumer);
	}

	/**
	 * Create an issued {@code ManagedCertificate} bundle consisting of an X.509
	 * Certificate and its private key. The {@code bundleConsumer} will be invoked
	 * with the issued (or obtained) {@link CertificateBundle} on certificate
	 * container startup and each time the certificate is rotated (re-issued).
	 *
	 * @param certificateBundle requested certificate definition.
	 * @param bundleConsumer consumer for certificate bundle access.
	 * @param errorConsumer consumer for errors.
	 * @return the managed certificate bundle object.
	 */
	public static ManagedCertificate issue(RequestedCertificateBundle certificateBundle,
			Consumer<CertificateBundle> bundleConsumer,
			Consumer<Throwable> errorConsumer) {
		Assert.notNull(bundleConsumer, "Bundle consumer must not be null");
		Assert.notNull(errorConsumer, "Error consumer must not be null");

		CertificateListenerAdapter listener = new CertificateListenerAdapter() {

			@Override
			public void onCertificateEvent(CertificateEvent certificateEvent) {
				if (certificateEvent instanceof CertificateBundleIssuedEvent event) {
					try {
						bundleConsumer.accept(event.getCertificate());
					} catch (Exception e) {
						errorConsumer.accept(e);
					}
				}
			}

			@Override
			public void onCertificateError(CertificateEvent leaseEvent, Exception exception) {
				errorConsumer.accept(exception);
			}

		};

		return new ManagedCertificate(certificateBundle, listener);
	}

	/**
	 * Create a {@code ManagedTrustAnchor}. The {@code certificateConsumer} will be
	 * invoked with the issued (or obtained) {@link Certificate} on certificate
	 * container startup and each time the certificate is rotated.
	 *
	 * @param name name of the certificate bundle
	 * @param issuer certificate issuer name.
	 * @param certificateConsumer consumer for certificate access.
	 * @return the managed certificate object.
	 */
	public static ManagedCertificate trust(String name, String issuer,
			Consumer<Certificate> certificateConsumer) {
		return trust(RequestedCertificate.trustAnchor(name, issuer), certificateConsumer, throwable -> {
			onError(name, throwable);
		});
	}

	/**
	 * Create a {@code ManagedCertificateBundle}.The {@code certificateConsumer}
	 * will be invoked with the issued (or obtained) {@link Certificate} on
	 * certificate container startup and each time the certificate is rotated.
	 *
	 * @param trustAnchor requested certificate definition.
	 * @param certificateConsumer consumer for certificate access.
	 * @param errorConsumer consumer for errors.
	 * @return the managed certificate object.
	 */
	public static ManagedCertificate trust(RequestedTrustAnchor trustAnchor,
			Consumer<Certificate> certificateConsumer,
			Consumer<Throwable> errorConsumer) {
		return from(trustAnchor, certificateConsumer, errorConsumer);
	}

	/**
	 * Create a {@code ManagedCertificateBundle}. The {@code certificateConsumer}
	 * will be invoked with the issued (or obtained) {@link Certificate} on
	 * certificate container startup and each time the certificate is rotated.
	 *
	 * @param requestedCertificate requested certificate definition.
	 * @param certificateConsumer consumer for certificate access.
	 * @return the managed certificate object.
	 */
	public static ManagedCertificate from(RequestedCertificate requestedCertificate,
			Consumer<Certificate> certificateConsumer) {
		return from(requestedCertificate, certificateConsumer, throwable -> {
			onError(requestedCertificate.getName(), throwable);
		});
	}

	/**
	 * Create a {@code ManagedCertificateBundle}. The {@code certificateConsumer}
	 * will be invoked with the issued (or obtained) {@link Certificate} on
	 * certificate container startup and each time the certificate is rotated.
	 *
	 * @param requestedCertificate requested certificate definition.
	 * @param certificateConsumer consumer for certificate access.
	 * @param errorConsumer consumer for errors.
	 * @return the managed certificate object.
	 */
	public static ManagedCertificate from(RequestedCertificate requestedCertificate,
			Consumer<Certificate> certificateConsumer,
			Consumer<Throwable> errorConsumer) {
		Assert.notNull(certificateConsumer, "Certificate consumer must not be null");
		Assert.notNull(errorConsumer, "Error consumer must not be null");

		CertificateListenerAdapter listener = new CertificateListenerAdapter() {

			@Override
			public void onCertificateEvent(CertificateEvent certificateEvent) {
				if (certificateEvent instanceof CertificateObtainedEvent event) {
					try {
						certificateConsumer.accept(event.getCertificate());
					} catch (Exception e) {
						errorConsumer.accept(e);
					}
				}
			}

			@Override
			public void onCertificateError(CertificateEvent leaseEvent, Exception exception) {
				errorConsumer.accept(exception);
			}

		};

		return new ManagedCertificate(requestedCertificate, listener);
	}

	private static void onError(String name, Throwable throwable) {
		if (logger.isErrorEnabled()) {
			logger.error("Error occurred while processing certificate: " + name, throwable);
		}
	}


	public String getName() {
		return requestedCertificate.getName();
	}

	@Override
	public void registerCertificate(CertificateRegistry registry) {
		registry.register(this.requestedCertificate, this.listener);
	}

	@Override
	public String toString() {
		return "ManagedCertificate [" + getName() + "]";
	}

}
