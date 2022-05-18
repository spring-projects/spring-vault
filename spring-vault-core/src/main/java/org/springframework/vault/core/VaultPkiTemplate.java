/*
 * Copyright 2016-2022 the original author or authors.
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
package org.springframework.vault.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;
import org.springframework.vault.support.VaultSignCertificateRequestResponse;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Default implementation of {@link VaultPkiOperations}.
 *
 * @author Mark Paluch
 * @author Alex Antonov
 */
public class VaultPkiTemplate implements VaultPkiOperations {

	private final VaultOperations vaultOperations;

	private final String path;

	/**
	 * Create a new {@link VaultPkiTemplate} given {@link VaultOperations} and the mount
	 * {@code path}.
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
	public VaultCertificateResponse issueCertificate(String roleName, VaultCertificateRequest certificateRequest)
			throws VaultException {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.notNull(certificateRequest, "Certificate request must not be null");

		return requestCertificate(roleName, "{path}/issue/{roleName}", createIssueRequest(certificateRequest),
				VaultCertificateResponse.class);
	}

	@Override
	public VaultSignCertificateRequestResponse signCertificateRequest(String roleName, String csr,
			VaultCertificateRequest certificateRequest) throws VaultException {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.hasText(csr, "CSR name must not be empty");
		Assert.notNull(certificateRequest, "Certificate request must not be null");

		Map<String, Object> body = createIssueRequest(certificateRequest);
		body.put("csr", csr);

		return requestCertificate(roleName, "{path}/sign/{roleName}", body, VaultSignCertificateRequestResponse.class);
	}

	private <T> T requestCertificate(String roleName, String requestPath, Map<String, Object> request,
			Class<T> responseType) {

		request.putIfAbsent("format", "der");

		T response = this.vaultOperations.doWithSession(restOperations -> {

			try {
				return restOperations.postForObject(requestPath, request, responseType, this.path, roleName);
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e);
			}
		});

		Assert.state(response != null, "VaultCertificateResponse must not be null");

		return response;
	}

	@Override
	public void revoke(String serialNumber) throws VaultException {

		Assert.hasText(serialNumber, "Serial number must not be null or empty");

		this.vaultOperations.doWithSession(restOperations -> {

			try {
				restOperations.postForObject("{path}/revoke", Collections.singletonMap("serial_number", serialNumber),
						Map.class, this.path);

				return null;
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e);
			}
		});
	}

	@Override
	public InputStream getCrl(Encoding encoding) throws VaultException {

		Assert.notNull(encoding, "Encoding must not be null");

		return this.vaultOperations.doWithSession(restOperations -> {

			String requestPath = encoding == Encoding.DER ? "{path}/crl" : "{path}/crl/pem";
			try {
				ResponseEntity<byte[]> response = restOperations.getForEntity(requestPath, byte[].class, this.path);

				if (response.getStatusCode() == HttpStatus.OK) {
					return new ByteArrayInputStream(response.getBody());
				}

				return null;
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e);
			}
		});
	}

	/**
	 * Create a request body stub for {@code pki/issue} and {@code pki/sign} from
	 * {@link VaultCertificateRequest}.
	 * @param certificateRequest must not be {@literal null}.
	 * @return the body as {@link Map}.
	 */
	private static Map<String, Object> createIssueRequest(VaultCertificateRequest certificateRequest) {

		Assert.notNull(certificateRequest, "Certificate request must not be null");

		Map<String, Object> request = new HashMap<>();

		PropertyMapper mapper = PropertyMapper.get();

		mapper.from(certificateRequest::getCommonName).to("common_name", request);
		mapper.from(certificateRequest::getAltNames).whenNotEmpty()
				.as(i -> StringUtils.collectionToDelimitedString(i, ",")).to("alt_names", request);
		mapper.from(certificateRequest::getIpSubjectAltNames).whenNotEmpty()
				.as(i -> StringUtils.collectionToDelimitedString(i, ",")).to("ip_sans", request);
		mapper.from(certificateRequest::getUriSubjectAltNames).whenNotEmpty()
				.as(i -> StringUtils.collectionToDelimitedString(i, ",")).to("uri_sans", request);
		mapper.from(certificateRequest::getOtherSans).whenNotEmpty()
				.as(i -> StringUtils.collectionToDelimitedString(i, ",")).to("other_sans", request);
		mapper.from(certificateRequest::getTtl).whenNonNull().as(i -> i.get(ChronoUnit.SECONDS)).to("ttl", request);
		mapper.from(certificateRequest::isExcludeCommonNameFromSubjectAltNames).whenTrue().to("exclude_cn_from_sans",
				request);
		mapper.from(certificateRequest::getFormat).whenHasText().to("format", request);
		mapper.from(certificateRequest::getPrivateKeyFormat).whenHasText().to("private_key_format", request);

		return request;
	}

}
