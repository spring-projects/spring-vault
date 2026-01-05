/*
 * Copyright 2016-present the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.*;
import org.springframework.vault.support.VaultMount.VaultMountBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

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


	private final VaultTemplate vaultOperations;


	/**
	 * Create a new {@link VaultSysTemplate} with the given {@link VaultOperations}.
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultSysTemplate(VaultOperations vaultOperations) {
		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		this.vaultOperations = VaultTemplate.from(vaultOperations);
	}


	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public boolean isInitialized() {
		return requireResponse(this.vaultOperations.doWithSessionClient(client -> {
			try {
				ResponseEntity<Map<String, Boolean>> body = (ResponseEntity) client.get()
						.uri("sys/init")
						.headers(emptyNamespace())
						.retrieve()
						.toEntity(Map.class);
				Assert.state(body.getBody() != null, "Initialization response must not be null");
				Boolean initialized = body.getBody().get("initialized");
				return initialized != null && initialized.booleanValue();
			} catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e);
			}
		}));
	}

	@Override
	public VaultInitializationResponse initialize(VaultInitializationRequest vaultInitializationRequest) {
		Assert.notNull(vaultInitializationRequest, "VaultInitialization must not be null");
		return requireResponse(this.vaultOperations.doWithVaultClient(client -> {
			try {
				ResponseEntity<VaultInitializationResponseImpl> exchange = client.put()
						.uri("sys/init")
						.headers(emptyNamespace())
						.body(vaultInitializationRequest)
						.retrieve()
						.toEntity(VaultInitializationResponseImpl.class);
				Assert.state(exchange.getBody() != null, "Initialization response must not be null");

				return exchange.getBody();
			} catch (HttpStatusCodeException e) {
				throw VaultResponses.buildException(e);
			}
		}));
	}

	@Override
	public void seal() {
		this.vaultOperations.doWithSessionClient(SEAL);
	}

	@Override
	public VaultUnsealStatus unseal(String keyShare) {
		return requireResponse(this.vaultOperations.doWithVaultClient(client -> {
			ResponseEntity<VaultUnsealStatusImpl> response = client.put()
					.uri("sys/unseal")
					.body(Collections.singletonMap("key", keyShare))
					.retrieve()
					.toEntity(VaultUnsealStatusImpl.class);
			Assert.state(response.getBody() != null, "Unseal response must not be null");
			return response.getBody();
		}));
	}

	@Override
	public VaultUnsealStatus getUnsealStatus() {
		return requireResponse(this.vaultOperations.doWithVaultClient(GET_UNSEAL_STATUS));
	}

	@Override
	public void mount(String path, VaultMount vaultMount) {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(vaultMount, "VaultMount must not be null");
		this.vaultOperations.write("sys/mounts/%s".formatted(path), vaultMount);
	}

	@Override
	public Map<String, VaultMount> getMounts() {
		return requireResponse(this.vaultOperations.doWithSessionClient(GET_MOUNTS));
	}

	@Override
	public void unmount(String path) {
		Assert.hasText(path, "Path must not be empty");
		this.vaultOperations.delete("sys/mounts/%s".formatted(path));
	}

	@Override
	public void authMount(String path, VaultMount vaultMount) throws VaultException {
		Assert.hasText(path, "Path must not be empty");
		Assert.notNull(vaultMount, "VaultMount must not be null");
		this.vaultOperations.write("sys/auth/%s".formatted(path), vaultMount);
	}

	@Override
	public Map<String, VaultMount> getAuthMounts() throws VaultException {
		return requireResponse(this.vaultOperations.doWithSessionClient(GET_AUTH_MOUNTS));
	}

	@Override
	public void authUnmount(String path) throws VaultException {
		Assert.hasText(path, "Path must not be empty");
		this.vaultOperations.delete("sys/auth/%s".formatted(path));
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<String> getPolicyNames() throws VaultException {
		return requireResponse(
				(List<String>) this.vaultOperations.readRequired("sys/policy").getRequiredData().get("policies"));
	}

	@Override
	@SuppressWarnings("NullAway")
	public @Nullable Policy getPolicy(String name) throws VaultException {
		Assert.hasText(name, "Name must not be null or empty");
		return this.vaultOperations.doWithSessionClient((RestClientCallback<@Nullable Policy>) client -> {
			ResponseEntity<VaultResponse> response;
			try {
				response = client.get().uri("sys/policy/{name}", name).retrieve().toEntity(VaultResponse.class);
			} catch (HttpStatusCodeException e) {
				if (HttpStatusUtil.isNotFound(e.getStatusCode())) {
					return null;
				}
				throw e;
			}
			VaultResponse body = response.getBody();
			if (body == null) {
				return null;
			}
			String rules = (String) body.getRequiredData().get("rules");

			if (ObjectUtils.isEmpty(rules)) {
				return Policy.empty();
			}
			if (rules.trim().startsWith("{")) {
				return VaultResponses.unwrap(rules, Policy.class);
			}
			throw new UnsupportedOperationException("Cannot parse policy in HCL format");
		});
	}

	@Override
	@SuppressWarnings("NullAway")
	public void createOrUpdatePolicy(String name, Policy policy) throws VaultException {
		Assert.hasText(name, "Name must not be null or empty");
		Assert.notNull(policy, "Policy must not be null");
		String rules = JacksonCompat.instance().getPrettyPrintObjectMapperAccessor().writeValueAsString(policy);
		this.vaultOperations.doWithSessionClient((RestClientCallback<@Nullable Void>) restOperations -> {
			restOperations.put()
					.uri("sys/policy/{name}", name)
					.body(Collections.singletonMap("rules", rules))
					.retrieve()
					.toEntity(VaultResponse.class);
			return null;
		});
	}

	@Override
	public void deletePolicy(String name) throws VaultException {
		Assert.hasText(name, "Name must not be null or empty");
		this.vaultOperations.delete("sys/policy/%s".formatted(name));
	}

	@Override
	public VaultHealth health() {
		return requireResponse(this.vaultOperations.doWithVaultClient(HEALTH));
	}

	private static <T> T requireResponse(@Nullable T response) {
		Assert.state(response != null, "Response must not be null");
		return response;
	}


	private static class GetUnsealStatus implements RestClientCallback<VaultUnsealStatus> {

		@Override
		public VaultUnsealStatus doWithRestClient(RestClient client) {
			return requireResponse(client.get().uri("sys/seal-status").retrieve().body(VaultUnsealStatusImpl.class));
		}

	}


	private static class Seal implements RestClientCallback<@Nullable Void> {

		@Override
		public Void doWithRestClient(RestClient client) {
			return client.put().uri("sys/seal").retrieve().toBodilessEntity().getBody();
		}

	}


	private record GetMounts(String path) implements RestClientCallback<Map<String, VaultMount>> {

		private static final ParameterizedTypeReference<VaultMountsResponse> MOUNT_TYPE_REF = new ParameterizedTypeReference<VaultMountsResponse>() {
		};

		@Override
		public Map<String, VaultMount> doWithRestClient(RestClient client) {
			ResponseEntity<VaultMountsResponse> exchange = client.get()
					.uri(this.path)
					.retrieve()
					.toEntity(MOUNT_TYPE_REF);
			VaultMountsResponse body = exchange.getBody();
			Assert.state(body != null, "Get mounts response must not be null");
			if (body.getData() != null) {
				return body.getData();
			}
			return body.getTopLevelMounts();
		}


		@JsonIgnoreProperties(ignoreUnknown = true)
		private static class VaultMountsResponse extends VaultResponseSupport<Map<String, VaultMount>> {

			private final Map<String, VaultMount> topLevelMounts = new HashMap<>();

			@JsonIgnore
			public Map<String, VaultMount> getTopLevelMounts() {
				return this.topLevelMounts;
			}

			@SuppressWarnings({"unchecked", "NullAway", "rawtypes"})
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
					this.topLevelMounts.put(name, vaultMount);
				}
			}

		}

	}

	private static <T> HttpEntity<T> emptyNamespace(@Nullable T body) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(VaultHttpHeaders.VAULT_NAMESPACE, "");
		return new HttpEntity<>(body, headers);
	}

	private static Consumer<HttpHeaders> emptyNamespace() {
		return it -> it.add(VaultHttpHeaders.VAULT_NAMESPACE, "");
	}


	private static class Health implements RestClientCallback<VaultHealth> {

		@Override
		public VaultHealth doWithRestClient(RestClient client) {

			try {
				ResponseEntity<VaultHealthImpl> healthResponse = client.get()
						.uri("sys/health")
						.headers(emptyNamespace())
						.retrieve()
						.toEntity(VaultHealthImpl.class);
				return requireResponse(healthResponse.getBody());
			} catch (RestClientResponseException responseError) {
				try {
					return JacksonCompat.instance()
							.getObjectMapperAccessor()
							.deserialize(responseError.getResponseBodyAsString(), VaultHealthImpl.class);
				} catch (Exception jsonError) {
					throw responseError;
				}
			}
		}

	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	static class VaultInitializationResponseImpl implements VaultInitializationResponse {

		private List<String> keys = new ArrayList<>();

		@JsonProperty("root_token")
		private String rootToken = "";


		public VaultToken getRootToken() {
			return VaultToken.of(this.rootToken);
		}

		public List<String> getKeys() {
			return this.keys;
		}

		public void setKeys(List<String> keys) {
			this.keys = keys;
		}

		public void setRootToken(String rootToken) {
			this.rootToken = rootToken;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof VaultInitializationResponseImpl that))
				return false;
			return this.keys.equals(that.keys) && this.rootToken.equals(that.rootToken);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.keys, this.rootToken);
		}

	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	static class VaultUnsealStatusImpl implements VaultUnsealStatus {

		private boolean sealed;

		@JsonProperty("t")
		private int secretThreshold;

		@JsonProperty("n")
		private int secretShares;

		private int progress;


		public boolean isSealed() {
			return this.sealed;
		}

		public int getSecretThreshold() {
			return this.secretThreshold;
		}

		public int getSecretShares() {
			return this.secretShares;
		}

		public int getProgress() {
			return this.progress;
		}

		public void setSealed(boolean sealed) {
			this.sealed = sealed;
		}

		public void setSecretThreshold(int secretThreshold) {
			this.secretThreshold = secretThreshold;
		}

		public void setSecretShares(int secretShares) {
			this.secretShares = secretShares;
		}

		public void setProgress(int progress) {
			this.progress = progress;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof VaultUnsealStatusImpl that))
				return false;
			return this.sealed == that.sealed && this.secretThreshold == that.secretThreshold
					&& this.secretShares == that.secretShares && this.progress == that.progress;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.sealed, this.secretThreshold, this.secretShares, this.progress);
		}

	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	static class VaultHealthImpl implements VaultHealth {

		private final boolean initialized;

		private final boolean sealed;

		private final boolean standby;

		private final boolean performanceStandby;

		private final boolean replicationRecoverySecondary;

		private final int serverTimeUtc;

		private final @Nullable String version;


		VaultHealthImpl(@JsonProperty("initialized") boolean initialized, @JsonProperty("sealed") boolean sealed,
				@JsonProperty("standby") boolean standby,
				@JsonProperty("performance_standby") boolean performanceStandby,
				@Nullable @JsonProperty("replication_dr_mode") String replicationRecoverySecondary,
				@JsonProperty("server_time_utc") int serverTimeUtc, @Nullable @JsonProperty("version") String version) {

			this.initialized = initialized;
			this.sealed = sealed;
			this.standby = standby;
			this.performanceStandby = performanceStandby;
			this.replicationRecoverySecondary = replicationRecoverySecondary != null
					&& !"disabled".equalsIgnoreCase(replicationRecoverySecondary);
			this.serverTimeUtc = serverTimeUtc;
			this.version = version;
		}


		public boolean isInitialized() {
			return this.initialized;
		}

		public boolean isSealed() {
			return this.sealed;
		}

		public boolean isStandby() {
			return this.standby;
		}

		public boolean isPerformanceStandby() {
			return this.performanceStandby;
		}

		public boolean isRecoveryReplicationSecondary() {
			return this.replicationRecoverySecondary;
		}

		public int getServerTimeUtc() {
			return this.serverTimeUtc;
		}

		public @Nullable String getVersion() {
			return this.version;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof VaultHealthImpl that))
				return false;
			return this.initialized == that.initialized && this.sealed == that.sealed && this.standby == that.standby
					&& this.performanceStandby == that.performanceStandby
					&& this.replicationRecoverySecondary == that.replicationRecoverySecondary
					&& this.serverTimeUtc == that.serverTimeUtc && Objects.equals(this.version, that.version);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.initialized, this.sealed, this.standby, this.performanceStandby,
					this.replicationRecoverySecondary, this.serverTimeUtc, this.version);
		}

	}

}
