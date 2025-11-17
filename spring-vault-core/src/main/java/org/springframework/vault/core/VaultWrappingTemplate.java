/*
 * Copyright 2018-2025 the original author or authors.
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

import java.time.Duration;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.WrappedMetadata;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * @author Mark Paluch
 */
public class VaultWrappingTemplate implements VaultWrappingOperations {

	private final VaultTemplate vaultOperations;


	/**
	 * Create a new {@link VaultWrappingTemplate} given {@link VaultOperations}.
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultWrappingTemplate(VaultOperations vaultOperations) {
		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		this.vaultOperations = VaultTemplate.from(vaultOperations);
	}

	@Nullable
	@Override
	public WrappedMetadata lookup(VaultToken token) {
		Assert.notNull(token, "VaultToken not be null");
		VaultResponse response = null;
		try {
			response = this.vaultOperations.write("sys/wrapping/lookup",
					Collections.singletonMap("token", token.getToken()));
		} catch (VaultException e) {
			if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
				return null;
			}
			throw e;
		}
		if (response == null) {
			return null;
		}
		return WrappedMetadata.from(response.getRequiredData(), token);
	}

	@Nullable
	@Override
	public VaultResponse read(VaultToken token) {
		return doUnwrap(token, (client, headers) -> {
			return client.post().path("sys/wrapping/unwrap").headers(headers).retrieve().body();
		});
	}

	@Nullable
	@Override
	public <T> VaultResponseSupport<T> read(VaultToken token, Class<T> responseType) {
		ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses.getTypeReference(responseType);
		return doUnwrap(token, (client, headers) -> {
			return client.post().path("sys/wrapping/unwrap").headers(headers).retrieve().body(ref);
		});
	}

	@SuppressWarnings("NullAway")
	private <T extends VaultResponseSupport<?>> @Nullable T doUnwrap(VaultToken token,
			BiFunction<VaultClient, Consumer<HttpHeaders>, @Nullable T> requestFunction) {
		return this.vaultOperations.doWithVaultClient((VaultClientCallback<@Nullable T>) client -> {
			try {
				return requestFunction.apply(client, httpHeaders -> httpHeaders.putAll(VaultHttpHeaders.from(token)));
			} catch (VaultException e) {

				if (e.getCause() instanceof HttpStatusCodeException hse) {

					if (HttpStatusUtil.isNotFound(hse.getStatusCode())) {
						return null;
					}

					if (HttpStatusUtil.isBadRequest(hse.getStatusCode())
							&& hse.getResponseBodyAsString().contains("does not exist")) {
						return null;

				}
					throw VaultResponses.buildException(hse, "sys/wrapping/unwrap");
				}

				throw e;
			}
		});
	}

	@Override
	@SuppressWarnings("NullAway")
	public WrappedMetadata rewrap(VaultToken token) {
		Assert.notNull(token, "token VaultToken not be null");
		VaultResponse response = this.vaultOperations.invoke("sys/wrapping/rewrap",
				Collections.singletonMap("token", token.getToken()));

		return WrappedMetadata.from(response);
	}

	@Override
	@SuppressWarnings("NullAway")
	public WrappedMetadata wrap(Object body, Duration duration) {
		Assert.notNull(body, "Body must not be null");
		Assert.notNull(duration, "TTL duration must not be null");
		return this.vaultOperations.doWithSessionClient(client -> {

			return client.post().path("sys/wrapping/wrap").body(body).retrieve().wrap(duration);
		});
	}

}
