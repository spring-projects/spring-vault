/*
 * Copyright 2020-2021 the original author or authors.
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

import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.Signature;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;

/**
 * @author Mark Paluch
 */
//@formatter:off
public class Transit {

	void encryptSimple() {

		// tag::encryptSimple[]
		VaultOperations operations = new VaultTemplate(new VaultEndpoint());
		VaultTransitOperations transitOperations = operations.opsForTransit("transit");

		transitOperations.createKey("my-aes-key", VaultTransitKeyCreationRequest.ofKeyType("aes128-gcm96"));	// <1>

		String ciphertext = transitOperations.encrypt("my-aes-key", "plaintext to encrypt");					// <2>

		String plaintext = transitOperations.decrypt("my-aes-key", ciphertext);									// <3>

		// end::encryptSimple[]
	}

	void encryptPlaintext(VaultTransitOperations transitOperations) {

		// tag::encryptPlaintext[]
		byte [] plaintext = "plaintext to encrypt".getBytes();

		Ciphertext ciphertext = transitOperations.encrypt("my-aes-key", Plaintext.of(plaintext));			// <1>

		Plaintext decrypttedPlaintext = transitOperations.decrypt("my-aes-key", ciphertext);				// <2>

		// end::encryptPlaintext[]
	}

	void signVerify(VaultTransitOperations transitOperations) {

		// tag::signVerify[]
		byte [] plaintext = "plaintext to sign".getBytes();

		transitOperations.createKey("my-ed25519-key", VaultTransitKeyCreationRequest.ofKeyType("ed25519"));	// <1>

		Signature signature = transitOperations.sign("my-ed25519-key", Plaintext.of(plaintext));			// <2>

		boolean valid = transitOperations.verify("my-ed25519-key", Plaintext.of(plaintext), signature);		// <3>

		// end::signVerify[]
	}

}
