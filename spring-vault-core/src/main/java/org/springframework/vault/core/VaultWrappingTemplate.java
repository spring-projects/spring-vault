/*
 * Copyright 2018-present the original author or authors.
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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.client.VaultHttpHeaders;
import org.springframework.vault.client.VaultResponses;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.vault.support.WrappedMetadata;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

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
		return getWrappedMetadata(response.getRequiredData(), token);
	}

	@Nullable
	@Override
	public VaultResponse read(VaultToken token) {
		return doUnwrap(token, (client, headers) -> {
			return client.post().uri("sys/wrapping/unwrap").headers(headers).retrieve().body(VaultResponse.class);
		});
	}

	@Nullable
	@Override
	public <T> VaultResponseSupport<T> read(VaultToken token, Class<T> responseType) {
		ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses.getTypeReference(responseType);
		return doUnwrap(token, (client, headers) -> {
			return client.post().uri("sys/wrapping/unwrap").headers(headers).retrieve().body(ref);
		});
	}

	@SuppressWarnings("NullAway")
	private <T extends VaultResponseSupport<?>> @Nullable T doUnwrap(VaultToken token,
			BiFunction<RestClient, Consumer<HttpHeaders>, @Nullable T> requestFunction) {
		return this.vaultOperations.doWithVaultClient((RestClientCallback<@Nullable T>) client -> {
			try {
				return requestFunction.apply(client, httpHeaders -> httpHeaders.putAll(VaultHttpHeaders.from(token)));
			} catch (HttpStatusCodeException e) {

				if (HttpStatusUtil.isNotFound(e.getStatusCode())) {
					return null;
				}
				if (HttpStatusUtil.isBadRequest(e.getStatusCode())
						&& e.getResponseBodyAsString().contains("does not exist")) {
					return null;
				}
				throw VaultResponses.buildException(e, "sys/wrapping/unwrap");
			}
		});
	}

	@Override
	@SuppressWarnings("NullAway")
	public WrappedMetadata rewrap(VaultToken token) {
		Assert.notNull(token, "token VaultToken not be null");
		VaultResponse response = this.vaultOperations.invoke("sys/wrapping/rewrap",
				Collections.singletonMap("token", token.getToken()));
		Map<String, String> wrapInfo = response.getWrapInfo();
		return getWrappedMetadata(wrapInfo, VaultToken.of(wrapInfo.get("token")));
	}

	@Override
	@SuppressWarnings("NullAway")
	public WrappedMetadata wrap(Object body, Duration duration) {
		Assert.notNull(body, "Body must not be null");
		Assert.notNull(duration, "TTL duration must not be null");
		VaultResponse response = this.vaultOperations.doWithSessionClient(client -> {
			return client.post()
					.uri("sys/wrapping/wrap")
					.body(body)
					.header("X-Vault-Wrap-TTL", Long.toString(duration.getSeconds()))
					.retrieve()
					.body(VaultResponse.class);
		});
		Map<String, String> wrapInfo = response.getWrapInfo();
		return getWrappedMetadata(wrapInfo, VaultToken.of(wrapInfo.get("token")));
	}

	private static WrappedMetadata getWrappedMetadata(Map<String, ?> wrapInfo, VaultToken token) {
		TemporalAccessor creation_time = getDate(wrapInfo, "creation_time");
		String path = (String) wrapInfo.get("creation_path");
		Duration ttl = getTtl(wrapInfo);
		return new WrappedMetadata(token, ttl, Instant.from(creation_time), path);
	}

	private static TemporalAccessor getDate(Map<String, ?> responseMetadata, String key) {
		String date = (String) ((Map) responseMetadata).getOrDefault(key, "");
		if (StringUtils.hasText(date)) {
			return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(date);
		}
		throw new IllegalArgumentException("Cannot obtain date");
	}

	private static Duration getTtl(Map<String, ?> wrapInfo) {
		Object creationTtl = wrapInfo.get("ttl");
		if (creationTtl == null) {
			creationTtl = wrapInfo.get("creation_ttl");
		}
		if (creationTtl instanceof String) {
			creationTtl = Integer.parseInt((String) creationTtl);
		}
		if (creationTtl instanceof Integer) {
			return Duration.ofSeconds((Integer) creationTtl);
		}
		throw new IllegalArgumentException("Cannot obtain TTL");
	}

}
