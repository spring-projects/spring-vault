/*
 * Copyright 2018-2019 the original author or authors.
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

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
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
import org.springframework.web.client.RestOperations;

/**
 * @author Mark Paluch
 */
public class VaultWrappingTemplate implements VaultWrappingOperations {

	private final VaultOperations vaultOperations;

	/**
	 * Create a new {@link VaultWrappingTemplate} given {@link VaultOperations}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultWrappingTemplate(VaultOperations vaultOperations) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");

		this.vaultOperations = vaultOperations;
	}

	@Nullable
	@Override
	public WrappedMetadata lookup(VaultToken token) {

		Assert.notNull(token, "token VaultToken not be null");

		VaultResponse response = null;
		try {
			response = vaultOperations.write("sys/wrapping/lookup",
					Collections.singletonMap("token", token.getToken()));
		}
		catch (VaultException e) {

			if (e.getMessage() != null && e.getMessage().contains("does not exist")) {
				return null;
			}

			throw e;
		}

		if (response == null) {
			return null;
		}

		return getWrappedMetadata(response.getData(), token);
	}

	@Nullable
	@Override
	public VaultResponse read(VaultToken token) {

		return doUnwrap(
				token,
				(restOperations, entity) -> {
					return restOperations.exchange("sys/wrapping/unwrap",
							HttpMethod.POST, entity, VaultResponse.class).getBody();
				});
	}

	@Nullable
	@Override
	public <T> VaultResponseSupport<T> read(VaultToken token, Class<T> responseType) {

		ParameterizedTypeReference<VaultResponseSupport<T>> ref = VaultResponses
				.getTypeReference(responseType);

		return doUnwrap(
				token,
				(restOperations, entity) -> {
					return restOperations.exchange("sys/wrapping/unwrap",
							HttpMethod.POST, entity, ref).getBody();
				});
	}

	@Nullable
	private <T extends VaultResponseSupport<?>> T doUnwrap(VaultToken token,
			BiFunction<RestOperations, HttpEntity<?>, T> requestFunction) {

		return vaultOperations.doWithVault(restOperations -> {

			try {
				return requestFunction.apply(restOperations, new HttpEntity<>(
						VaultHttpHeaders.from(token)));
			}
			catch (HttpStatusCodeException e) {

				if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
					return null;
				}

				if (e.getStatusCode() == HttpStatus.BAD_REQUEST
						&& e.getResponseBodyAsString().contains("does not exist")) {
					return null;
				}

				throw VaultResponses.buildException(e, "sys/wrapping/unwrap");
			}
		});
	}

	@Override
	public WrappedMetadata rewrap(VaultToken token) {

		Assert.notNull(token, "token VaultToken not be null");

		VaultResponse response = vaultOperations.write("sys/wrapping/rewrap",
				Collections.singletonMap("token", token.getToken()));

		Map<String, String> wrapInfo = response.getWrapInfo();

		return getWrappedMetadata(wrapInfo, VaultToken.of(wrapInfo.get("token")));
	}

	@Override
	public WrappedMetadata wrap(Object body, Duration duration) {

		Assert.notNull(body, "Body must not be null");
		Assert.notNull(duration, "TTL duration must not be null");

		VaultResponse response = vaultOperations.doWithSession(restOperations -> {

			HttpHeaders headers = new HttpHeaders();
			headers.add("X-Vault-Wrap-TTL", Long.toString(duration.getSeconds()));

			return restOperations.exchange("sys/wrapping/wrap", HttpMethod.POST,
					new HttpEntity<>(body, headers), VaultResponse.class).getBody();
		});

		Map<String, String> wrapInfo = response.getWrapInfo();

		return getWrappedMetadata(wrapInfo, VaultToken.of(wrapInfo.get("token")));
	}

	private static WrappedMetadata getWrappedMetadata(Map<String, ?> wrapInfo,
			VaultToken token) {

		TemporalAccessor creation_time = getDate(wrapInfo, "creation_time");
		String path = (String) wrapInfo.get("creation_path");
		Duration ttl = getTtl(wrapInfo);

		return new WrappedMetadata(token, ttl, Instant.from(creation_time), path);
	}

	@Nullable
	private static TemporalAccessor getDate(Map<String, ?> responseMetadata, String key) {

		String date = (String) ((Map) responseMetadata).getOrDefault(key, "");

		return StringUtils.hasText(date) ? DateTimeFormatter.ISO_OFFSET_DATE_TIME
				.parse(date) : null;
	}

	@Nullable
	private static Duration getTtl(Map<String, ?> wrapInfo) {

		Object creationTtl = wrapInfo.get("ttl");

		if (creationTtl == null) {
			creationTtl = wrapInfo.get("creation_ttl");
		}

		if (creationTtl instanceof String) {
			creationTtl = Integer.parseInt((String) creationTtl);
		}

		Duration ttl = null;

		if (creationTtl instanceof Integer) {
			ttl = Duration.ofSeconds((Integer) creationTtl);

		}
		return ttl;
	}

}
