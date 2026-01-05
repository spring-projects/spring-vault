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

package org.springframework.vault.support;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Value object to bind generic Vault HTTP API responses.
 *
 * @param <T> type for {@code data} response.
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultResponseSupport<T> {

	private Map<String, Object> auth = Collections.emptyMap();

	private @Nullable T data;

	private Map<String, Object> metadata = Collections.emptyMap();

	@JsonProperty("wrap_info")
	private Map<String, String> wrapInfo = Collections.emptyMap();

	@JsonProperty("lease_duration")
	private long leaseDuration;

	@JsonProperty("lease_id")
	private @Nullable String leaseId;

	@JsonProperty("request_id")
	private @Nullable String requestId;

	private boolean renewable;

	private List<String> warnings = Collections.emptyList();


	/**
	 * Apply metadata such as auth or warnings without copying data.
	 * @param other
	 * @since 3.1
	 */
	public void applyMetadata(VaultResponseSupport<?> other) {
		this.auth = other.auth;
		this.metadata = other.metadata;
		this.wrapInfo = other.wrapInfo;
		this.leaseDuration = other.leaseDuration;
		this.leaseId = other.leaseId;
		this.requestId = other.requestId;
		this.renewable = other.renewable;
		this.warnings = other.warnings;
	}

	/**
	 * @return authentication payload.
	 */
	public Map<String, Object> getAuth() {
		return this.auth;
	}

	/**
	 * @param auth the authentication payload.
	 */
	public void setAuth(Map<String, Object> auth) {
		this.auth = auth;
	}

	/**
	 * @return secret data.
	 */
	public @Nullable T getData() {
		return this.data;
	}

	/**
	 * @return {@literal true} if {@code data} is not null; {@literal false}
	 * otherwise.
	 * @since 4.1
	 */
	public boolean hasData() {
		return this.data != null;
	}

	/**
	 * @return the required secret data.
	 * @throws IllegalStateException if {@code data} is null.
	 */
	public T getRequiredData() {
		if (this.data != null) {
			return this.data;
		}
		throw new IllegalStateException("Data field is empty");
	}

	/**
	 * @param data secret data.
	 */
	public void setData(@Nullable T data) {
		this.data = data;
	}

	/**
	 * @return request metadata.
	 */
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	/**
	 * @param metadata request metadata.
	 */
	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	/**
	 * @return the lease duration.
	 */
	public long getLeaseDuration() {
		return this.leaseDuration;
	}

	/**
	 * @param leaseDuration the lease duration.
	 */
	public void setLeaseDuration(long leaseDuration) {
		this.leaseDuration = leaseDuration;
	}

	/**
	 * @return the lease Id.
	 */
	public @Nullable String getLeaseId() {
		return this.leaseId;
	}

	/**
	 * @param leaseId the lease Id.
	 */
	public void setLeaseId(@Nullable String leaseId) {
		this.leaseId = leaseId;
	}

	/**
	 * @return {@literal true} if the lease is renewable.
	 */
	public boolean isRenewable() {
		return this.renewable;
	}

	/**
	 * @param renewable {@literal true} if the lease is renewable.
	 */
	public void setRenewable(boolean renewable) {
		this.renewable = renewable;
	}

	/**
	 * @return response wrapping details.
	 */
	public Map<String, String> getWrapInfo() {
		return this.wrapInfo;
	}

	/**
	 * @param wrapInfo response wrapping details.
	 */
	public void setWrapInfo(Map<String, String> wrapInfo) {
		this.wrapInfo = wrapInfo;
	}

	/**
	 * @return the request Id.
	 */
	public @Nullable String getRequestId() {
		return this.requestId;
	}

	/**
	 * @param requestId the request Id.
	 */
	public void setRequestId(@Nullable String requestId) {
		this.requestId = requestId;
	}

	/**
	 * @return the warnings.
	 */
	public List<String> getWarnings() {
		return this.warnings;
	}

	/**
	 * @param warnings the warnings.
	 */
	public void setWarnings(List<String> warnings) {
		this.warnings = warnings;
	}

}
