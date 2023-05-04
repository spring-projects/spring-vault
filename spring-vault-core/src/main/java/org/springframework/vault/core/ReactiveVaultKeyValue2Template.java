/*
 * Copyright 2018-2022 the original author or authors.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseDataVersion2;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Default implementation of {@link VaultKeyValueOperations} for the key-value backend
 * version 2.
 *
 * @author Mark Paluch
 * @author Younghwan Jang
 * @since 2.1
 */
class ReactiveVaultKeyValue2Template extends ReactiveVaultKeyValue2Accessor implements ReactiveVaultKeyValueOperations {

	/**
	 * Create a new {@link ReactiveVaultKeyValue2Template} given {@link VaultOperations}
	 * and the mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public ReactiveVaultKeyValue2Template(ReactiveVaultOperations vaultOperations, String path) {
		super(vaultOperations, path);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Mono<VaultResponse> get(String path) {
		var ref = new ParameterizedTypeReference<VaultResponseDataVersion2<Map<String, Object>>>() {
		};

		return doRead(path, ref).onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
			.map(response -> {
				VaultResponse vaultResponse = new VaultResponse();
				vaultResponse.setRenewable(response.isRenewable());
				vaultResponse.setAuth(response.getAuth());
				vaultResponse.setLeaseDuration(response.getLeaseDuration());
				vaultResponse.setLeaseId(response.getLeaseId());
				vaultResponse.setRequestId(response.getRequestId());
				vaultResponse.setWarnings(response.getWarnings());
				vaultResponse.setWrapInfo(response.getWrapInfo());

				var data = response.getData();
				if (null != data) {
					vaultResponse.setData(data.getData());
					vaultResponse.setMetadata(data.getMetadata());
				}
				return vaultResponse;
			});
	}

	@Override
	public <T> Mono<VaultResponseSupport<T>> get(String path, Class<T> responseType) {
		var ref = VaultResponses.getTypeReference(VaultResponses.getDataTypeReference(responseType));
		return doReadRaw(createDataPath(path), ref)
			.onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.empty())
			.map(response -> {
				VaultResponseSupport<T> vaultResponse = new VaultResponseSupport<>();
				vaultResponse.setRenewable(response.isRenewable());
				vaultResponse.setAuth(response.getAuth());
				vaultResponse.setLeaseDuration(response.getLeaseDuration());
				vaultResponse.setLeaseId(response.getLeaseId());
				vaultResponse.setRequestId(response.getRequestId());
				vaultResponse.setWarnings(response.getWarnings());
				vaultResponse.setWrapInfo(response.getWrapInfo());

				var data = response.getData();
				if (null != data) {
					vaultResponse.setData(data.getData());
					vaultResponse.setMetadata(data.getMetadata());
				}
				return vaultResponse;
			});
	}

	@Override
	public Mono<Boolean> patch(String path, Map<String, ?> patch) {
		Assert.notNull(patch, "Patch body must not be null");
		return get(path)
			.onErrorResume(WebClientResponseException.NotFound.class,
					e -> Mono.error(new SecretNotFoundException(String
						.format("No data found at %s; patch only works on existing data", createDataPath(path)),
							String.format("%s/%s", this.path, path))))
			.switchIfEmpty(Mono.error(new SecretNotFoundException(
					String.format("No data found at %s; patch only works on existing data", createDataPath(path)),
					String.format("%s/%s", this.path, path))))
			.flatMap(readResponse -> {
				if (null == readResponse.getData()) {
					return Mono.error(new SecretNotFoundException(String
						.format("No data found at %s; patch only works on existing data", createDataPath(path)),
							String.format("%s/%s", this.path, path)));
				}

				if (readResponse.getMetadata() == null) {
					return Mono.error(new VaultException("Metadata must not be null"));
				}

				Map<String, Object> metadata = readResponse.getMetadata();
				Map<String, Object> data = new LinkedHashMap<>(readResponse.getRequiredData());
				data.putAll(patch);

				Map<String, Object> body = new HashMap<>();
				body.put("data", data);
				body.put("options", Collections.singletonMap("cas", metadata.get("version")));

				return doWrite(createDataPath(path), body).thenReturn(true).onErrorResume(VaultException.class, e -> {
					if (e.getMessage() != null && (e.getMessage().contains("check-and-set")
							|| e.getMessage().contains("did not match the current version"))) {
						return Mono.just(Boolean.FALSE);
					}
					return Mono.error(e);
				});
			});
	}

	@Override
	public Mono<Void> put(String path, Object body) {
		return doWrite(createDataPath(path), Collections.singletonMap("data", body)).then();
	}

}
