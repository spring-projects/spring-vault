/*
 * Copyright 2016-2020 the original author or authors.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.Policy;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultInitializationRequest;
import org.springframework.vault.support.VaultInitializationResponse;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.VaultUnsealStatus;
import org.springframework.vault.support.VaultMount.VaultMountBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestOperations;

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

	private static final ObjectMapper OBJECT_MAPPER;

	static {

		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		OBJECT_MAPPER = mapper;
	}

	private final VaultOperations vaultOperations;

	/**
	 * Create a new {@link VaultSysTemplate} with the given {@link VaultOperations}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultSysTemplate(VaultOperations vaultOperations) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");

		this.vaultOperations = vaultOperations;

	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean isInitialized() {

		return requireResponse(vaultOperations.doWithVault(restOperations -> {

			try {
				Map<String, Boolean> body = restOperations.getForObject("sys/init",
						Map.class);

				Assert.state(body != null, "Initialization response must not be null");

				return body.get("initialized");
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e);
			}
		}));
	}

	@Override
	public VaultInitializationResponse initialize(
			VaultInitializationRequest vaultInitializationRequest) {

		Assert.notNull(vaultInitializationRequest, "VaultInitialization must not be null");

		return requireResponse(vaultOperations.doWithVault(restOperations -> {

			try {
				ResponseEntity<VaultInitializationResponseImpl> exchange = restOperations
						.exchange("sys/init", HttpMethod.PUT, new HttpEntity<Object>(
								vaultInitializationRequest),
								VaultInitializationResponseImpl.class);

				Assert.state(exchange.getBody() != null,
						"Initialization response must not be null");

				return exchange.getBody();
			}
			catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e);
			}
		}));
	}

	@Override
	public void seal() {
		vaultOperations.doWithSession(SEAL);
	}

	@Override
	public VaultUnsealStatus unseal(String keyShare) {

		return requireResponse(vaultOperations.doWithVault(restOperations -> {

			ResponseEntity<VaultUnsealStatusImpl> response = restOperations.exchange(
					"sys/unseal", HttpMethod.PUT,
					new HttpEntity<Object>(Collections.singletonMap("key", keyShare)),
					VaultUnsealStatusImpl.class);

			Assert.state(response.getBody() != null, "Unseal response must not be null");

			return response.getBody();
		}));
	}

	@Override
	public VaultUnsealStatus getUnsealStatus() {
		return requireResponse(vaultOperations.doWithVault(GET_UNSEAL_STATUS));
	}

	@Override
	public void mount(String path, VaultMount vaultMount) {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(vaultMount, "VaultMount must not be null");

		vaultOperations.write(String.format("sys/mounts/%s", path), vaultMount);
	}

	@Override
	public Map<String, VaultMount> getMounts() {
		return requireResponse(vaultOperations.doWithSession(GET_MOUNTS));
	}

	@Override
	public void unmount(String path) {

		Assert.hasText(path, "Path must not be empty");

		vaultOperations.delete(String.format("sys/mounts/%s", path));
	}

	@Override
	public void authMount(String path, VaultMount vaultMount)
			throws VaultException {

		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(vaultMount, "VaultMount must not be null");

		vaultOperations.write(String.format("sys/auth/%s", path), vaultMount);
	}

	@Override
	public Map<String, VaultMount> getAuthMounts() throws VaultException {
		return requireResponse(vaultOperations.doWithSession(GET_AUTH_MOUNTS));
	}

	@Override
	public void authUnmount(String path) throws VaultException {

		Assert.hasText(path, "Path must not be empty");

		vaultOperations.delete(String.format("sys/auth/%s", path));
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<String> getPolicyNames() throws VaultException {
		return requireResponse((List<String>) vaultOperations.read("sys/policy")
				.getRequiredData().get("policies"));
	}

	@Nullable
	@Override
	public Policy getPolicy(String name) throws VaultException {

		Assert.hasText(name, "Name must not be null or empty");

		return vaultOperations.doWithSession(restOperations -> {

			ResponseEntity<VaultResponse> response;

			try {
				response = restOperations.getForEntity("sys/policy/{name}",
						VaultResponse.class, name);
			}
			catch (HttpStatusCodeException e) {

				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					return null;
				}

				throw e;
			}

			String rules = (String) response.getBody().getRequiredData().get("rules");

			if (StringUtils.isEmpty(rules)) {
				return Policy.empty();
			}

			if (rules.trim().startsWith("{")) {
				return VaultResponses.unwrap(rules, Policy.class);
			}

			throw new UnsupportedOperationException("Cannot parse policy in HCL format");
		});
	}

	@Override
	public void createOrUpdatePolicy(String name, Policy policy) throws VaultException {

		Assert.hasText(name, "Name must not be null or empty");
		Assert.notNull(policy, "Policy must not be null");

		String rules;

		try {
			rules = OBJECT_MAPPER.writeValueAsString(policy);
		}
		catch (IOException e) {
			throw new VaultException("Cannot serialize policy to JSON", e);
		}

		vaultOperations.doWithSession(restOperations -> {

			restOperations.exchange("sys/policy/{name}", HttpMethod.PUT,
					new HttpEntity<>(Collections.singletonMap("rules", rules)),
					VaultResponse.class, name);

			return null;
		});
	}

	@Override
	public void deletePolicy(String name) throws VaultException {

		Assert.hasText(name, "Name must not be null or empty");

		vaultOperations.delete(String.format("sys/policy/%s", name));
	}

	@Override
	public VaultHealth health() {
		return requireResponse(vaultOperations.doWithVault(HEALTH));
	}

	private static <T> T requireResponse(@Nullable T response) {

		Assert.state(response != null, "Response must not be null");

		return response;
	}

	private static class GetUnsealStatus implements
			RestOperationsCallback<VaultUnsealStatus> {

		@Override
		public VaultUnsealStatus doWithRestOperations(RestOperations restOperations) {
			return restOperations.getForObject("sys/seal-status",
					VaultUnsealStatusImpl.class);
		}
	}

	private static class Seal implements RestOperationsCallback<Void> {

		@Override
		public Void doWithRestOperations(RestOperations restOperations) {
			restOperations.put("sys/seal", null);
			return null;
		}

	}

	private static class GetMounts implements
			RestOperationsCallback<Map<String, VaultMount>> {

		private static final ParameterizedTypeReference<VaultMountsResponse> MOUNT_TYPE_REF = new ParameterizedTypeReference<VaultMountsResponse>() {
		};

		private final String path;

		GetMounts(String path) {
			this.path = path;
		}

		@Override
		public Map<String, VaultMount> doWithRestOperations(RestOperations restOperations) {

			ResponseEntity<VaultMountsResponse> exchange = restOperations.exchange(path,
					HttpMethod.GET, null, MOUNT_TYPE_REF, Collections.emptyMap());

			VaultMountsResponse body = exchange.getBody();

			Assert.state(body != null, "Get mounts response must not be null");

			if (body.getData() != null) {
				return body.getData();
			}

			return body.getTopLevelMounts();
		}

		private static class VaultMountsResponse extends
				VaultResponseSupport<Map<String, VaultMount>> {

			private Map<String, VaultMount> topLevelMounts = new HashMap<>();

			@JsonIgnore
			public Map<String, VaultMount> getTopLevelMounts() {
				return topLevelMounts;
			}

			@SuppressWarnings("unchecked")
			@JsonAnySetter
			public void set(String name, Object value) {

				if (!(value instanceof Map)) {
					return;
				}

				Map<String, Object> map = (Map) value;

				if (map.containsKey("type")) {

					VaultMountBuilder builder = VaultMount.builder() //
							.type((String) map.get("type")) //
							.description((String) map.get("description"));// ;

					if (map.containsKey("config")) {
						builder.config((Map) map.get("config"));
					}

					VaultMount vaultMount = builder.build();

					topLevelMounts.put(name, vaultMount);
				}
			}
		}

	}

	private static class Health implements RestOperationsCallback<VaultHealth> {

		@Override
		public VaultHealth doWithRestOperations(RestOperations restOperations) {

			try {
				ResponseEntity<VaultHealthImpl> healthResponse = restOperations.exchange(
						"sys/health", HttpMethod.GET, null, VaultHealthImpl.class);
				return healthResponse.getBody();
			}
			catch (HttpStatusCodeException responseError) {

				try {
					ObjectMapper mapper = new ObjectMapper();
					return mapper.readValue(responseError.getResponseBodyAsString(),
							VaultHealthImpl.class);
				}
				catch (Exception jsonError) {
					throw responseError;
				}
			}
		}
	}

	@Data
	static class VaultInitializationResponseImpl implements VaultInitializationResponse {

		private List<String> keys = new ArrayList<>();

		@JsonProperty("root_token")
		private String rootToken = "";

		public VaultToken getRootToken() {
			return VaultToken.of(rootToken);
		}
	}

	@Data
	static class VaultUnsealStatusImpl implements VaultUnsealStatus {

		private boolean sealed;

		@JsonProperty("t")
		private int secretThreshold;

		@JsonProperty("n")
		private int secretShares;

		private int progress;
	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class VaultHealthImpl implements VaultHealth {

		private final boolean initialized;
		private final boolean sealed;
		private final boolean standby;
		private final int serverTimeUtc;

		@Nullable
		private final String version;

		private VaultHealthImpl(@JsonProperty("initialized") boolean initialized,
				@JsonProperty("sealed") boolean sealed,
				@JsonProperty("standby") boolean standby,
				@JsonProperty("server_time_utc") int serverTimeUtc,
				@Nullable @JsonProperty("version") String version) {

			this.initialized = initialized;
			this.sealed = sealed;
			this.standby = standby;
			this.serverTimeUtc = serverTimeUtc;
			this.version = version;
		}
	}
}
