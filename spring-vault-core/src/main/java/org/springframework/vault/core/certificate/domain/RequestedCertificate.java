/*
 * Copyright 202-present the original author or authors.
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
package org.springframework.vault.core.certificate.domain;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.support.VaultCertificateRequest;

/**
 * Represents a requested certificate from a Vault PKI.
 *
 * @author Mark Paluch
 * @since 4.1
 */
public abstract class RequestedCertificate {

	private final String name;


	RequestedCertificate(String name) {
		Assert.hasText(name, "Name must not be null or empty");
		this.name = name;
	}


	/**
	 * Create a renewable {@link RequestedSecret} at {@code path}. A lease
	 * associated with this secret will be renewed if the lease is qualified for
	 * renewal. The lease is no longer valid after expiry.
	 * @param name a descriptive name for the {@code RequestedCertificate}, must not
	 * be {@literal null} or empty.
	 * @param role the role to issue the certificate against, must not * be
	 * {@literal null} or empty.
	 * @param certificateRequest the certificate request describing the certificate
	 * to issue.
	 * @return the renewable {@link RequestedSecret}.
	 */
	public static RequestedCertificateBundle issue(String name, String role,
			VaultCertificateRequest certificateRequest) {
		return new RequestedCertificateBundle(name, role, certificateRequest);
	}

	/**
	 * Create a {@code RequestedTrustAnchor} given {@code name} for the default
	 * issuer. The trust anchor requests a CA
	 * {@link org.springframework.vault.support.Certificate}.
	 * @param name a descriptive name for the {@code RequestedCertificate}, must not
	 * be {@literal null} or empty.
	 * @return the renewable {@link RequestedSecret}.
	 */
	public static RequestedTrustAnchor trustAnchor(String name) {
		return new RequestedTrustAnchor(name, RequestedTrustAnchor.DEFAULT_ISSUER);
	}

	/**
	 * Create a {@code RequestedTrustAnchor} given {@code name} and {@code issuer}.
	 * The trust anchor requests a CA
	 * {@link org.springframework.vault.support.Certificate}.
	 * @param name a descriptive name for the {@code RequestedCertificate}, must not
	 * be {@literal null} or empty.
	 * @param issuer reference to an existing issuer, either by Vault-generated
	 * identifier, currently configured default issuer, or the name assigned to an
	 * issuer, must not be {@literal null} or empty.
	 * @return the renewable {@link RequestedSecret}.
	 */
	public static RequestedTrustAnchor trustAnchor(String name, String issuer) {
		return new RequestedTrustAnchor(name, issuer);
	}

	/**
	 * @return name of the requested certificate.
	 */
	public String getName() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RequestedCertificate that)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(name, that.name);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(name);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [name='").append(this.getName()).append('\'');
		sb.append(']');
		return sb.toString();
	}

}
