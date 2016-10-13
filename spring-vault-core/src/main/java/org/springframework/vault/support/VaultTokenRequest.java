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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Value object to bind Vault HTTP Token API requests.
 * 
 * @author Mark Paluch
 */
public class VaultTokenRequest {

	private String id;

	private List<String> policies;

	private Map<String, String> meta;

	@JsonProperty("no_parent")
	private Boolean noParent;

	@JsonProperty("no_default_policy")
	private Boolean noDefaultPolicy;

	private Boolean renewable;

	private String ttl;

	@JsonProperty("explicit_max_ttl")
	private String explicitMaxTtl;

	@JsonProperty("display_name")
	private String displayName;

	@JsonProperty("num_uses")
	private Integer numUses;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<String> getPolicies() {
		return policies;
	}

	public void setPolicies(List<String> policies) {
		this.policies = policies;
	}

	public Map<String, String> getMeta() {
		return meta;
	}

	public void setMeta(Map<String, String> meta) {
		this.meta = meta;
	}

	public Boolean getNoParent() {
		return noParent;
	}

	public void setNoParent(Boolean noParent) {
		this.noParent = noParent;
	}

	public Boolean getNoDefaultPolicy() {
		return noDefaultPolicy;
	}

	public void setNoDefaultPolicy(Boolean noDefaultPolicy) {
		this.noDefaultPolicy = noDefaultPolicy;
	}

	public Boolean getRenewable() {
		return renewable;
	}

	public void setRenewable(Boolean renewable) {
		this.renewable = renewable;
	}

	public String getTtl() {
		return ttl;
	}

	public void setTtl(String ttl) {
		this.ttl = ttl;
	}

	public String getExplicitMaxTtl() {
		return explicitMaxTtl;
	}

	public void setExplicitMaxTtl(String explicitMaxTtl) {
		this.explicitMaxTtl = explicitMaxTtl;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public Integer getNumUses() {
		return numUses;
	}

	public void setNumUses(Integer numUses) {
		this.numUses = numUses;
	}
}
