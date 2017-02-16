/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.vault.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;

/**
 * Default implementation of {@link VaultTransitOperations}.
 *
 * @author Mark Paluch
 */
public class VaultTransitTemplate implements VaultTransitOperations {

	private final VaultOperations vaultOperations;

	private final String path;

	public VaultTransitTemplate(VaultOperations vaultOperations, String path) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations = vaultOperations;
		this.path = path;
	}

	@Override
	public void createKey(String keyName) {

		Assert.hasText(keyName, "KeyName must not be empty");

		vaultOperations.write(String.format("%s/keys/%s", path, keyName), null);
	}

	@Override
	public void createKey(String keyName, VaultTransitKeyCreationRequest createKeyRequest) {

		Assert.hasText(keyName, "KeyName must not be empty");
		Assert.notNull(createKeyRequest,
				"VaultTransitKeyCreationRequest must not be empty");

		vaultOperations.write(String.format("%s/keys/%s", path, keyName),
				createKeyRequest);
	}

	@Override
	public List<String> getKeys() {

		VaultResponse response = vaultOperations.read(String.format("%s/keys?list=true",
				path));

		return response == null ? Collections.emptyList() : (List) response.getData()
				.get("keys");
	}

	@Override
	public void configureKey(String keyName, VaultTransitKeyConfiguration keyConfiguration) {

		Assert.hasText(keyName, "KeyName must not be empty");
		Assert.notNull(keyConfiguration, "VaultKeyConfiguration must not be empty");

		vaultOperations.write(String.format("%s/keys/%s/config", path, keyName),
				keyConfiguration);
	}

	@Override
	public VaultTransitKey getKey(String keyName) {

		Assert.hasText(keyName, "KeyName must not be empty");

		VaultResponseSupport<VaultTransitKeyImpl> result = vaultOperations.read(
				String.format("%s/keys/%s", path, keyName), VaultTransitKeyImpl.class);

		if (result != null) {
			return result.getData();
		}

		return null;
	}

	@Override
	public void deleteKey(String keyName) {

		Assert.hasText(keyName, "KeyName must not be empty");

		vaultOperations.delete(String.format("%s/keys/%s", path, keyName));
	}

	@Override
	public void rotate(String keyName) {

		Assert.hasText(keyName, "KeyName must not be empty");

		vaultOperations.write(String.format("%s/keys/%s/rotate", path, keyName), null);
	}

	@Override
	public String encrypt(String keyName, String plaintext) {

		Assert.hasText(keyName, "KeyName must not be empty");
		Assert.notNull(plaintext, "Plain text must not be null");

		Map<String, String> request = new LinkedHashMap<String, String>();

		request.put("plaintext", Base64Utils.encodeToString(plaintext.getBytes()));

		return (String) vaultOperations
				.write(String.format("%s/encrypt/%s", path, keyName), request).getData()
				.get("ciphertext");
	}

	@Override
	public String encrypt(String keyName, byte[] plaintext,
			VaultTransitContext transitRequest) {

		Assert.hasText(keyName, "KeyName must not be empty");
		Assert.notNull(plaintext, "Plain text must not be null");

		Map<String, String> request = new LinkedHashMap<String, String>();

		request.put("plaintext", Base64Utils.encodeToString(plaintext));

		if (transitRequest != null) {
			applyTransitOptions(transitRequest, request);
		}

		return (String) vaultOperations
				.write(String.format("%s/encrypt/%s", path, keyName), request).getData()
				.get("ciphertext");
	}

	@Override
	public String decrypt(String keyName, String ciphertext) {

		Assert.hasText(keyName, "KeyName must not be empty");
		Assert.hasText(keyName, "Cipher text must not be empty");

		Map<String, String> request = new LinkedHashMap<String, String>();

		request.put("ciphertext", ciphertext);

		String plaintext = (String) vaultOperations
				.write(String.format("%s/decrypt/%s", path, keyName), request).getData()
				.get("plaintext");

		return new String(Base64Utils.decodeFromString(plaintext));
	}

	@Override
	public byte[] decrypt(String keyName, String ciphertext,
			VaultTransitContext transitRequest) {

		Assert.hasText(keyName, "KeyName must not be empty");
		Assert.hasText(keyName, "Cipher text must not be empty");

		Map<String, String> request = new LinkedHashMap<String, String>();

		request.put("ciphertext", ciphertext);

		if (transitRequest != null) {
			applyTransitOptions(transitRequest, request);
		}

		String plaintext = (String) vaultOperations
				.write(String.format("%s/decrypt/%s", path, keyName), request).getData()
				.get("plaintext");

		return Base64Utils.decodeFromString(plaintext);
	}

	@Override
	public String rewrap(String keyName, String ciphertext) {

		Assert.hasText(keyName, "KeyName must not be empty");
		Assert.hasText(ciphertext, "Cipher text must not be empty");

		Map<String, String> request = new LinkedHashMap<String, String>();
		request.put("ciphertext", ciphertext);

		return (String) vaultOperations
				.write(String.format("%s/rewrap/%s", path, keyName), request).getData()
				.get("ciphertext");
	}

	@Override
	public String rewrap(String keyName, String ciphertext,
			VaultTransitContext transitRequest) {

		Assert.hasText(keyName, "KeyName must not be empty");
		Assert.hasText(ciphertext, "Cipher text must not be empty");

		Map<String, String> request = new LinkedHashMap<String, String>();

		request.put("ciphertext", ciphertext);

		if (transitRequest != null) {
			applyTransitOptions(transitRequest, request);
		}

		return (String) vaultOperations
				.write(String.format("%s/rewrap/%s", path, keyName), request).getData()
				.get("ciphertext");
	}

	private void applyTransitOptions(VaultTransitContext transitRequest,
			Map<String, String> request) {

		if (transitRequest.getContext() != null) {
			request.put("context",
					Base64Utils.encodeToString(transitRequest.getContext()));
		}

		if (transitRequest.getNonce() != null) {
			request.put("nonce", Base64Utils.encodeToString(transitRequest.getNonce()));
		}
	}

	@Data
	static class VaultTransitKeyImpl implements VaultTransitKey {

		@JsonProperty("cipher_mode")
		private String cipherMode;

		@JsonProperty("type")
		private String type;

		@JsonProperty("deletion_allowed")
		private boolean deletionAllowed;

		private boolean derived;

		private Map<String, Long> keys;

		@JsonProperty("latest_version")
		private boolean latestVersion;

		@JsonProperty("min_decryption_version")
		private int minDecryptionVersion;

		private String name;

		public String getType() {

			if (type != null) {
				return type;
			}

			return cipherMode;
		}

	}
}
