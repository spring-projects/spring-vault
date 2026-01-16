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

package org.springframework.vault.core.certificate.domain;

import org.springframework.util.ObjectUtils;
import org.springframework.vault.support.VaultCertificateRequest;

/**
 * Value object representing a requested certificate bundle defined by name,
 * role, and request.
 * @author Mark Paluch
 * @since 4.1
 */
public class RequestedCertificateBundle extends RequestedCertificate {

	private final String role;

	private final VaultCertificateRequest request;


	RequestedCertificateBundle(String name, String role, VaultCertificateRequest request) {
		super(name);
		this.role = role;
		this.request = request;
	}


	public String getRole() {
		return role;
	}

	public VaultCertificateRequest getRequest() {
		return request;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RequestedCertificateBundle that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(role, that.role) && ObjectUtils.nullSafeEquals(request.getCommonName(), that.request.getCommonName());
	}

	@Override
	public int hashCode() {
		return super.hashCode() * 31 + ObjectUtils.nullSafeHash(role, request.getCommonName());
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [name='").append(this.getName()).append("',");
		sb.append(" role='").append(this.getRole()).append("',");
		sb.append(" commonName='").append(this.getRequest().getCommonName()).append("'");
		sb.append(']');
		return sb.toString();
	}

}
