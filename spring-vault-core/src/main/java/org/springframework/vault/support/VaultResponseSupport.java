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

package org.springframework.vault.support;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object to bind generic Vault HTTP API responses.
 *
 * @param <T>
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultResponseSupport<T> {

	private Map<String, Object> auth;

	private T data;

	private Map<String, String> metadata;

	@JsonProperty("wrap_info") private Map<String, String> wrapInfo;

	@JsonProperty("lease_duration") private long leaseDuration;

	@JsonProperty("lease_id") private String leaseId;

	@JsonProperty("request_id") private String requestId;

	private boolean renewable;

	public Map<String, Object> getAuth() {
		return auth;
	}

	public void setAuth(Map<String, Object> auth) {
		this.auth = auth;
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

	public Map<String, String> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}

	public long getLeaseDuration() {
		return leaseDuration;
	}

	public void setLeaseDuration(long leaseDuration) {
		this.leaseDuration = leaseDuration;
	}

	public String getLeaseId() {
		return leaseId;
	}

	public void setLeaseId(String leaseId) {
		this.leaseId = leaseId;
	}

	public boolean isRenewable() {
		return renewable;
	}

	public void setRenewable(boolean renewable) {
		this.renewable = renewable;
	}

	public Map<String, String> getWrapInfo() {
		return wrapInfo;
	}

	public void setWrapInfo(Map<String, String> wrapInfo) {
		this.wrapInfo = wrapInfo;
	}

	public String getRequestId() {
		return requestId;
	}

	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
}
