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

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.client.VaultAccessor;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.core.VaultOperations.ClientCallback;
import org.springframework.vault.core.VaultOperations.SessionCallback;
import org.springframework.vault.support.VaultHealthResponse;
import org.springframework.vault.support.VaultInitializationRequest;
import org.springframework.vault.support.VaultInitializationResponse;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultUnsealStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Default implementation of {@link VaultSysOperations}.
 * 
 * @author Mark Paluch
 */
public class VaultSysTemplate implements VaultSysOperations {

	private static final GetUnsealStatus GET_UNSEAL_STATUS = new GetUnsealStatus();

	private static final Seal SEAL = new Seal();

	private static final GetMounts GET_MOUNTS = new GetMounts("sys/mounts");

	private static final GetMounts GET_AUTH_MOUNTS = new GetMounts("sys/auth");

	private static final Health HEALTH = new Health();

	private final VaultOperations vaultOperations;

	/**
	 * Creates a new {@link VaultSysTemplate} with the given {@link VaultOperations}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultSysTemplate(VaultOperations vaultOperations) {

		Assert.notNull(vaultOperations);

		this.vaultOperations = vaultOperations;
	}

	@Override
	public boolean isInitialized() {

		return vaultOperations.doWithVault(new ClientCallback<Boolean>() {

			@Override
			public Boolean doWithVault(VaultClient client) {

				VaultResponseEntity<Map<String, Boolean>> response = client.getForEntity("sys/init", Map.class);

				if (response.isSuccessful() && response.hasBody()) {
					return response.getBody().get("initialized");
				}

				throw new VaultException(buildExceptionMessage(response));
			}
		});
	}

	@Override
	public VaultInitializationResponse initialize(final VaultInitializationRequest vaultInitializationRequest) {

		Assert.notNull(vaultInitializationRequest, "VaultInitialization must not be null");

		return vaultOperations.doWithVault(new ClientCallback<VaultInitializationResponse>() {

			@Override
			public VaultInitializationResponse doWithVault(VaultClient client) {

				VaultResponseEntity<VaultInitializationResponse> response = client.putForEntity("sys/init",
						vaultInitializationRequest, VaultInitializationResponse.class);

				if (response.isSuccessful() && response.hasBody()) {
					return response.getBody();
				}

				throw new VaultException(buildExceptionMessage(response));
			}
		});
	}

	@Override
	public void seal() {
		vaultOperations.doWithVault(SEAL);
	}

	@Override
	public VaultUnsealStatus unseal(final String keyShare) {

		return vaultOperations.doWithVault(new ClientCallback<VaultUnsealStatus>() {

			@Override
			public VaultUnsealStatus doWithVault(VaultClient client) {

				VaultResponseEntity<VaultUnsealStatus> response = client.putForEntity("sys/unseal",
						Collections.singletonMap("key", keyShare), VaultUnsealStatus.class);

				if (response.isSuccessful() && response.hasBody()) {
					return response.getBody();
				}

				throw new VaultException(buildExceptionMessage(response));
			}
		});
	}

	@Override
	public VaultUnsealStatus getUnsealStatus() {
		return vaultOperations.doWithVault(GET_UNSEAL_STATUS);
	}

	@Override
	public void mount(final String path, final VaultMount vaultMount) {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(vaultMount, "VaultMount must not be null");

		vaultOperations.doWithVault(new SessionCallback<Void>() {
			@Override
			public Void doWithVault(VaultOperations.VaultSession session) {

				VaultResponseEntity<Map<?, ?>> response = session.exchange("sys/mounts/{mount}", HttpMethod.PUT,
						new HttpEntity<VaultMount>(vaultMount), Map.class, Collections.singletonMap("mount", path));

				if (response.isSuccessful()) {
					return null;
				}

				throw new VaultException(buildExceptionMessage(response));
			}
		});
	}

	@Override
	public Map<String, VaultMount> getMounts() {
		return vaultOperations.doWithVault(GET_MOUNTS);
	}

	@Override
	public void unmount(final String path) {

		Assert.hasText(path, "Path must not be empty");

		vaultOperations.doWithVault(new SessionCallback<Void>() {

			@Override
			public Void doWithVault(VaultOperations.VaultSession session) {

				VaultResponseEntity<Map<?, ?>> response = session.exchange("sys/mounts/{mount}", HttpMethod.DELETE, null,
						Map.class, Collections.singletonMap("mount", path));

				if (response.isSuccessful()) {
					return null;
				}

				throw new VaultException(buildExceptionMessage(response));
			}
		});
	}

	@Override
	public void authMount(final String path, final VaultMount vaultMount) throws VaultException {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(vaultMount, "VaultMount must not be null");

		vaultOperations.doWithVault(new SessionCallback<Void>() {
			@Override
			public Void doWithVault(VaultOperations.VaultSession session) {

				VaultResponseEntity<Map<?, ?>> response = session.exchange("sys/auth/{mount}", HttpMethod.PUT,
						new HttpEntity<VaultMount>(vaultMount), Map.class, Collections.singletonMap("mount", path));

				if (response.isSuccessful()) {
					return null;
				}

				throw new VaultException(buildExceptionMessage(response));
			}
		});
	}

	@Override
	public Map<String, VaultMount> getAuthMounts() throws VaultException {
		return vaultOperations.doWithVault(GET_AUTH_MOUNTS);
	}

	@Override
	public void authUnmount(final String path) throws VaultException {

		Assert.hasText(path, "Path must not be empty");

		vaultOperations.doWithVault(new SessionCallback<Void>() {

			@Override
			public Void doWithVault(VaultOperations.VaultSession session) {

				VaultResponseEntity<Map<?, ?>> response = session.exchange("sys/auth/{mount}", HttpMethod.DELETE, null,
						Map.class, Collections.singletonMap("mount", path));

				if (response.isSuccessful()) {
					return null;
				}

				throw new VaultException(buildExceptionMessage(response));
			}
		});
	}

	@Override
	public VaultHealthResponse health() {
		return vaultOperations.doWithRestTemplate("sys/health", Collections.<String, Object> emptyMap(), HEALTH);
	}

	private static String buildExceptionMessage(VaultResponseEntity<?> response) {

		if (StringUtils.hasText(response.getMessage())) {
			return String.format("Status %s URI %s: %s", response.getStatusCode(), response.getUri(), response.getMessage());
		}

		return String.format("Status %s URI %s", response.getStatusCode(), response.getUri());
	}

	private static class GetUnsealStatus implements ClientCallback<VaultUnsealStatus> {

		@Override
		public VaultUnsealStatus doWithVault(VaultClient client) {

			VaultResponseEntity<VaultUnsealStatus> response = client.getForEntity("sys/seal-status", VaultUnsealStatus.class);

			if (response.isSuccessful() && response.hasBody()) {
				return response.getBody();
			}

			throw new VaultException(buildExceptionMessage(response));
		}
	}

	private static class Seal implements SessionCallback<Void> {

		@Override
		public Void doWithVault(VaultOperations.VaultSession session) {

			VaultResponseEntity<Map> response = session.putForEntity("sys/seal", null, Map.class);

			if (!response.isSuccessful()) {
				throw new VaultException(buildExceptionMessage(response));
			}

			return null;
		}
	}

	private static class GetMounts implements SessionCallback<Map<String, VaultMount>> {

		private static final ParameterizedTypeReference<VaultResponseSupport<Map<String, VaultMount>>> MOUNT_TYPE_REF = new ParameterizedTypeReference<VaultResponseSupport<Map<String, VaultMount>>>() {
		};

		private final String path;

		public GetMounts(String path) {
			this.path = path;
		}

		@Override
		public Map<String, VaultMount> doWithVault(VaultOperations.VaultSession session) {

			VaultResponseEntity<VaultResponseSupport<Map<String, VaultMount>>> response = session.exchange(path, HttpMethod.GET, null,
					MOUNT_TYPE_REF, Collections.<String, Object>emptyMap());

			if (response.isSuccessful() && response.hasBody()) {
				return response.getBody().getData();
			}

			throw new VaultException(buildExceptionMessage(response));
		}
	}

	private static class Health implements VaultAccessor.RestTemplateCallback<VaultHealthResponse> {

		@Override
		public VaultHealthResponse doWithRestTemplate(URI uri, RestTemplate restTemplate) {

			try {

				ResponseEntity<VaultHealthResponse> healthResponse = restTemplate.exchange(uri, HttpMethod.GET, null,
						VaultHealthResponse.class);
				return healthResponse.getBody();
			} catch (HttpStatusCodeException responseError) {

				try {
					ObjectMapper mapper = new ObjectMapper();
					return mapper.readValue(responseError.getResponseBodyAsString(), VaultHealthResponse.class);
				} catch (Exception jsonError) {
					throw responseError;
				}
			}
		}
	}
}
