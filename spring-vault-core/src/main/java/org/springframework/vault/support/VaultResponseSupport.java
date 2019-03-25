/*
 * Copyright 2016 the original author or authors.
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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object to bind generic Vault HTTP API responses.
 *
 * @param <T> type for {@code data} response.
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultResponseSupport<T> {

	private Map<String, Object> auth;

	private T data;

	private Map<String, String> metadata;

	@JsonProperty("wrap_info")
	private Map<String, String> wrapInfo;

	@JsonProperty("lease_duration")
	private long leaseDuration;

	@JsonProperty("lease_id")
	private String leaseId;

	@JsonProperty("request_id")
	private String requestId;

	private boolean renewable;

	private List<String> warnings;

	/**
	 *
	 * @return authentication payload.
	 */
	public Map<String, Object> getAuth() {
		return auth;
	}

	/**
	 *
	 * @param auth the authentication payload.
	 */
	public void setAuth(Map<String, Object> auth) {
		this.auth = auth;
	}

	/**
	 *
	 * @return secret data.
	 */
	public T getData() {
		return data;
	}

	/**
	 *
	 * @param data secret data.
	 */
	public void setData(T data) {
		this.data = data;
	}

	/**
	 *
	 * @return request metadata.
	 */
	public Map<String, String> getMetadata() {
		return metadata;
	}

	/**
	 *
	 * @param metadata request metadata.
	 */
	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}

	/**
	 *
	 * @return the lease duration.
	 */
	public long getLeaseDuration() {
		return leaseDuration;
	}

	/**
	 *
	 * @param leaseDuration the lease duration.
	 */
	public void setLeaseDuration(long leaseDuration) {
		this.leaseDuration = leaseDuration;
	}

	/**
	 *
	 * @return the lease Id.
	 */
	public String getLeaseId() {
		return leaseId;
	}

	/**
	 *
	 * @param leaseId the lease Id.
	 */
	public void setLeaseId(String leaseId) {
		this.leaseId = leaseId;
	}

	/**
	 *
	 * @return {@literal true} if the lease is renewable.
	 */
	public boolean isRenewable() {
		return renewable;
	}

	/**
	 *
	 * @param renewable {@literal true} if the lease is renewable.
	 */
	public void setRenewable(boolean renewable) {
		this.renewable = renewable;
	}

	/**
	 *
	 * @return response wrapping details.
	 */
	public Map<String, String> getWrapInfo() {
		return wrapInfo;
	}

	/**
	 *
	 * @param wrapInfo response wrapping details.
	 */
	public void setWrapInfo(Map<String, String> wrapInfo) {
		this.wrapInfo = wrapInfo;
	}

	/**
	 *
	 * @return the request Id.
	 */
	public String getRequestId() {
		return requestId;
	}

	/**
	 *
	 * @param requestId the request Id.
	 */
	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}

	/**
	 *
	 * @return the warnings.
	 */
	public List<String> getWarnings() {
		return warnings;
	}

	/**
	 *
	 * @param warnings the warnings.
	 */
	public void setWarnings(List<String> warnings) {
		this.warnings = warnings;
	}
}
