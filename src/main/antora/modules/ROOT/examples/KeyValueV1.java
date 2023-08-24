/*
 * Copyright 2020-2022 the original author or authors.
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

import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * @author Mark Paluch
 */
//@formatter:off
public class KeyValueV1 {

	void vaultOperations() {

		// tag::vaultOperations[]
		VaultOperations operations = new VaultTemplate(new VaultEndpoint());

		operations.write("secret/elvis", Collections.singletonMap("social-security-number", "409-52-2002"));

		VaultResponse read = operations.read("secret/elvis");
		read.getRequiredData().get("social-security-number");
		// end::vaultOperations[]
	}

	void keyValueApi() {

		// tag::keyValueApi[]
		VaultOperations operations = new VaultTemplate(new VaultEndpoint());
		VaultKeyValueOperations keyValueOperations = operations.opsForKeyValue("secret",
									VaultKeyValueOperationsSupport.KeyValueBackend.KV_1);

		keyValueOperations.put("elvis", Collections.singletonMap("password", "409-52-2002"));

		VaultResponse read = keyValueOperations.get("elvis");
		read.getRequiredData().get("social-security-number");
		// end::keyValueApi[]
	}

}
