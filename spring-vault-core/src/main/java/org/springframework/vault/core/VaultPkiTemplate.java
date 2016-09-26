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
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;

/**
 * Default implementation of {@link VaultPkiOperations}.
 * 
 * @author Mark Paluch
 */
public class VaultPkiTemplate implements VaultPkiOperations {

	private final VaultOperations vaultOperations;

	private final String path;

	/**
	 * Create a new {@link VaultPkiTemplate} given {@link VaultPkiOperations} and the mount {@code path}.
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
	public VaultCertificateResponse issueCertificate(final String roleName, VaultCertificateRequest certificateRequest)
			throws VaultException {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.notNull(certificateRequest, "Certificate request must not be null");

		final Map<String, Object> request = new HashMap<String, Object>();
		request.put("common_name", certificateRequest.getCommonName());

		if (!certificateRequest.getAltNames().isEmpty()) {
			request.put("alt_names", StringUtils.collectionToDelimitedString(certificateRequest.getAltNames(), ","));
		}

		if (!certificateRequest.getIpSubjectAltNames().isEmpty()) {
			request.put("ip_sans", StringUtils.collectionToDelimitedString(certificateRequest.getIpSubjectAltNames(), ","));
		}

		if (certificateRequest.getTtl() != null) {
			request.put("ttl", certificateRequest.getTtl());
		}

		request.put("format", "der");

		if (certificateRequest.isExcludeCommonNameFromSubjectAltNames()) {
			request.put("exclude_cn_from_sans", true);
		}

		VaultResponseEntity<VaultCertificateResponse> entity = vaultOperations
				.doWithVault(new VaultOperations.SessionCallback<VaultResponseEntity<VaultCertificateResponse>>() {
					@Override
					public VaultResponseEntity<VaultCertificateResponse> doWithVault(VaultOperations.VaultSession session) {

						return session.postForEntity(String.format("%s/issue/%s", path, roleName), request,
								VaultCertificateResponse.class);
					}
				});

		if (entity.isSuccessful() && entity.hasBody()) {
			return entity.getBody();
		}

		throw new VaultException(buildExceptionMessage(entity));
	}

	private static String buildExceptionMessage(VaultResponseEntity<?> response) {

		if (StringUtils.hasText(response.getMessage())) {
			return String.format("Status %s URI %s: %s", response.getStatusCode(), response.getUri(), response.getMessage());
		}

		return String.format("Status %s URI %s", response.getStatusCode(), response.getUri());
	}
}
