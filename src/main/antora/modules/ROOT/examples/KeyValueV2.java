/*
 * Copyright 2020-present the original author or authors.
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

package org.springframework.vault.documentation;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.Versioned;
import org.springframework.vault.support.Versioned.Version;

/**
 * @author Mark Paluch
 */
//@formatter:off
public class KeyValueV2 {

	void vaultOperations() {

		// tag::vaultOperations[]
		VaultOperations operations = new VaultTemplate(new VaultEndpoint());

		operations.write("secret/data/elvis", Collections.singletonMap("data",
					Collections.singletonMap("social-security-number", "409-52-2002")));

		VaultResponse read = operations.read("secret/data/ykey");
		Map<String,String> data = (Map<String, String>) read.getRequiredData().get("data");
		data.get("social-security-number");
		// end::vaultOperations[]
	}

	void keyValueApi() {

		// tag::keyValueApi[]
		VaultOperations operations = new VaultTemplate(new VaultEndpoint());
		VaultKeyValueOperations keyValueOperations = operations.opsForKeyValue("secret",
									VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);

		keyValueOperations.put("elvis", Collections.singletonMap("social-security-number", "409-52-2002"));

		VaultResponse read = keyValueOperations.get("elvis");
		read.getRequiredData().get("social-security-number");
		// end::keyValueApi[]
	}

	void versionedApi() {

		// tag::versionedApi[]
		VaultOperations operations = new VaultTemplate(new VaultEndpoint());
		VaultVersionedKeyValueOperations versionedOperations = operations.opsForVersionedKeyValue("secret");

		Versioned.Metadata metadata = versionedOperations.put("elvis",							// <1>
							Collections.singletonMap("social-security-number", "409-52-2002"));

		Version version = metadata.getVersion();												// <2>

		Versioned<Object> ssn = versionedOperations.get("elvis", Version.from(42));				// <3>

		Versioned<SocialSecurityNumber> mappedSsn = versionedOperations.get("elvis",			// <4>
													Version.from(42), SocialSecurityNumber.class);

		Versioned<Map<String,String>> versioned = Versioned.create(Collections					// <5>
								.singletonMap("social-security-number", "409-52-2002"),
								Version.from(42));

		versionedOperations.put("elvis", version);
		// end::versionedApi[]
	}

	static class SocialSecurityNumber{

		@JsonProperty("social-security-number")
		String ssn;
	}

}
