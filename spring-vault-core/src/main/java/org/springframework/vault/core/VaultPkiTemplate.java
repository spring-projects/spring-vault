/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.vault.core;

import java.util.HashMap;
import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Default implementation of {@link VaultPkiOperations}.
 *
 * @author Mark Paluch
 */
public class VaultPkiTemplate implements VaultPkiOperations {

	private final VaultOperations vaultOperations;

	private final String path;

	/**
	 * Create a new {@link VaultPkiTemplate} given {@link VaultPkiOperations} and the
	 * mount {@code path}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultPkiTemplate(VaultOperations vaultOperations, String path) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations = vaultOperations;
		this.path = path;
	}

	@Override
	public VaultCertificateResponse issueCertificate(final String roleName,
			VaultCertificateRequest certificateRequest) throws VaultException {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.notNull(certificateRequest, "Certificate request must not be null");

		final Map<String, Object> request = new HashMap<>();
		request.put("common_name", certificateRequest.getCommonName());

		if (!certificateRequest.getAltNames().isEmpty()) {
			request.put(
					"alt_names",
					StringUtils.collectionToDelimitedString(
							certificateRequest.getAltNames(), ","));
		}

		if (!certificateRequest.getIpSubjectAltNames().isEmpty()) {
			request.put(
					"ip_sans",
					StringUtils.collectionToDelimitedString(
							certificateRequest.getIpSubjectAltNames(), ","));
		}

		if (certificateRequest.getTtl() != null) {
			request.put("ttl", certificateRequest.getTtl());
		}

		request.put("format", "der");

		if (certificateRequest.isExcludeCommonNameFromSubjectAltNames()) {
			request.put("exclude_cn_from_sans", true);
		}

		VaultCertificateResponse response = vaultOperations
				.doWithSession(restOperations -> {

					try {
						return restOperations.postForObject("{path}/issue/{roleName}",
								request, VaultCertificateResponse.class, path, roleName);
					}
					catch (HttpStatusCodeException e) {
						throw VaultResponses.buildException(e);
					}
				});

		Assert.state(response != null, "VaultCertificateResponse must not be null");

		return response;
	}
}
