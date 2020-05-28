/*
 * Copyright 2017-2020 the original author or authors.
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
package org.springframework.vault.security;

import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Plaintext;

/**
 * Vault-based {@link BytesEncryptor} using Vault's {@literal transit} backend.
 * Encryption/decryption is bound to a particular key that must support encryption and
 * decryption.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultBytesEncryptor implements BytesEncryptor {

	private final VaultTransitOperations transitOperations;

	private final String keyName;

	/**
	 * Create a new {@link VaultBytesEncryptor} given {@link VaultTransitOperations} and
	 * {@code keyName}.
	 * @param transitOperations must not be {@literal null}.
	 * @param keyName must not be {@literal null} or empty.
	 */
	public VaultBytesEncryptor(VaultTransitOperations transitOperations, String keyName) {

		Assert.notNull(transitOperations, "VaultTransitOperations must not be null");
		Assert.hasText(keyName, "Key name must not be null or empty");

		this.transitOperations = transitOperations;
		this.keyName = keyName;
	}

	@Override
	public byte[] encrypt(byte[] plaintext) {

		Assert.notNull(plaintext, "Plaintext must not be null");
		Assert.isTrue(!ObjectUtils.isEmpty(plaintext), "Plaintext must not be empty");

		Ciphertext ciphertext = this.transitOperations.encrypt(this.keyName, Plaintext.of(plaintext));

		return Utf8.encode(ciphertext.getCiphertext());
	}

	@Override
	public byte[] decrypt(byte[] ciphertext) {

		Assert.notNull(ciphertext, "Ciphertext must not be null");
		Assert.isTrue(!ObjectUtils.isEmpty(ciphertext), "Ciphertext must not be empty");

		Plaintext plaintext = this.transitOperations.decrypt(this.keyName, Ciphertext.of(Utf8.decode(ciphertext)));

		return plaintext.getPlaintext();
	}

}
