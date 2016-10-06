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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
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
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultInitializationRequest;
import org.springframework.vault.support.VaultInitializationResponse;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultUnsealStatus;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;

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

				VaultResponseEntity<VaultInitializationResponseImpl> response = client.putForEntity("sys/init",
						vaultInitializationRequest, VaultInitializationResponseImpl.class);

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

				VaultResponseEntity<VaultUnsealStatusImpl> response = client.putForEntity("sys/unseal",
						Collections.singletonMap("key", keyShare), VaultUnsealStatusImpl.class);

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

		vaultOperations.write(String.format("sys/mounts/%s", path), vaultMount);
	}

	@Override
	public Map<String, VaultMount> getMounts() {
		return vaultOperations.doWithVault(GET_MOUNTS);
	}

	@Override
	public void unmount(final String path) {

		Assert.hasText(path, "Path must not be empty");

		vaultOperations.delete(String.format("sys/mounts/%s", path));
	}

	@Override
	public void authMount(final String path, final VaultMount vaultMount) throws VaultException {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(vaultMount, "VaultMount must not be null");

		vaultOperations.write(String.format("sys/auth/%s", path), vaultMount);
	}

	@Override
	public Map<String, VaultMount> getAuthMounts() throws VaultException {
		return vaultOperations.doWithVault(GET_AUTH_MOUNTS);
	}

	@Override
	public void authUnmount(final String path) throws VaultException {

		Assert.hasText(path, "Path must not be empty");

		vaultOperations.delete(String.format("sys/auth/%s", path));
	}

	@Override
	public VaultHealth health() {
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

			VaultResponseEntity<VaultUnsealStatusImpl> response = client.getForEntity("sys/seal-status",
					VaultUnsealStatusImpl.class);

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

		private static final ParameterizedTypeReference<VaultMountsResponse> MOUNT_TYPE_REF = new ParameterizedTypeReference<VaultMountsResponse>() {};

		private final String path;

		GetMounts(String path) {
			this.path = path;
		}

		@Override
		public Map<String, VaultMount> doWithVault(VaultOperations.VaultSession session) {

			VaultResponseEntity<VaultMountsResponse> response = session.exchange(path, HttpMethod.GET, null, MOUNT_TYPE_REF,
					Collections.<String, Object> emptyMap());

			if (response.isSuccessful() && response.hasBody()) {

				VaultMountsResponse body = response.getBody();

				if (body.getData() != null) {
					return response.getBody().getData();
				}

				return response.getBody().getTopLevelMounts();
			}

			throw new VaultException(buildExceptionMessage(response));
		}

		private static class VaultMountsResponse extends VaultResponseSupport<Map<String, VaultMount>> {

			private Map<String, VaultMount> topLevelMounts = new HashMap<String, VaultMount>();

			@JsonIgnore
			public Map<String, VaultMount> getTopLevelMounts() {
				return topLevelMounts;
			}

			@SuppressWarnings("unchecked")
			@JsonAnySetter
			public void set(String name, Object value) {

				if (value instanceof Map) {

					Map<String, Object> map = (Map) value;

					if (map.containsKey("type")) {

						VaultMount vaultMount = VaultMount.builder() //
								.type((String) map.get("type")) //
								.description((String) map.get("description")) //
								.config((Map) map.get("config")).build();

						topLevelMounts.put(name, vaultMount);
					}
				}
			}
		}

	}

	private static class Health implements VaultAccessor.RestTemplateCallback<VaultHealth> {

		@Override
		public VaultHealth doWithRestTemplate(URI uri, RestTemplate restTemplate) {

			try {

				ResponseEntity<VaultHealthImpl> healthResponse = restTemplate.exchange(uri, HttpMethod.GET, null,
						VaultHealthImpl.class);
				return healthResponse.getBody();
			} catch (HttpStatusCodeException responseError) {

				try {
					ObjectMapper mapper = new ObjectMapper();
					return mapper.readValue(responseError.getResponseBodyAsString(), VaultHealthImpl.class);
				} catch (Exception jsonError) {
					throw responseError;
				}
			}
		}
	}

	@Data
	static class VaultInitializationResponseImpl implements VaultInitializationResponse {

		private List<String> keys = new ArrayList<String>();

		@JsonProperty("root_token") private String rootToken;

		public VaultToken getRootToken() {
			return VaultToken.of(rootToken);
		}
	}

	@Data
	static class VaultUnsealStatusImpl implements VaultUnsealStatus {

		private boolean sealed;

		@JsonProperty("t") private int secretThreshold;

		@JsonProperty("n") private int secretShares;

		private int progress;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class VaultHealthImpl implements VaultHealth {

		private final boolean initialized;
		private final boolean sealed;
		private final boolean standby;
		private final int serverTimeUtc;
		private final String version;

		private VaultHealthImpl(@JsonProperty("initialized") boolean initialized, @JsonProperty("sealed") boolean sealed,
				@JsonProperty("standby") boolean standby, @JsonProperty("server_time_utc") int serverTimeUtc,
				@JsonProperty("version") String version) {

			this.initialized = initialized;
			this.sealed = sealed;
			this.standby = standby;
			this.serverTimeUtc = serverTimeUtc;
			this.version = version;
		}
	}
}
