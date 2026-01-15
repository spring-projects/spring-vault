/*
 * Copyright 2026 the original author or authors.
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

/**
 * Value object representing a requested trust anchor (CA or intermediate
 * certificate).
 *
 * @author Mark Paluch
 * @since 4.1
 */
public class RequestedTrustAnchor extends RequestedCertificate {

	public static final String DEFAULT_ISSUER = "default";


	private final String issuer;


	RequestedTrustAnchor(String name, String issuer) {
		super(name);
		this.issuer = issuer;
	}


	public String getIssuer() {
		return issuer;
	}

	@Override
	public String toString() {
		return "RequestedTrustAnchor[" +
				"name='" + getName() + "', " +
				"role='" + issuer + '\'' +
				']';
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RequestedTrustAnchor that)) {
			return false;
		}
		if (!super.equals(o)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(issuer, that.issuer);
	}

	@Override
	public int hashCode() {
		return super.hashCode() * 31 + ObjectUtils.nullSafeHash(issuer);
	}

}
