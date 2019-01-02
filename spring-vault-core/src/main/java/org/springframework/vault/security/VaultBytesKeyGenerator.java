/*
 * Copyright 2017-2019 the original author or authors.
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
package org.springframework.vault.security;

import java.util.Collections;

import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

/**
 * Random byte generator using Vault's {@code transit} backend to generate high-quality
 * random bytes of the configured length.
 * <p>
 * Using Vault ensures to use a high-entropy source preventing to consume entropy of the
 * local machine.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class VaultBytesKeyGenerator implements BytesKeyGenerator {

	private final VaultOperations vaultOperations;

	private final int length;

	private String transitPath;

	/**
	 * Creates a new {@link VaultBytesKeyGenerator} initialized to generate {@link 32}
	 * random bytes using {@code transit} for transit mount path.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 */
	public VaultBytesKeyGenerator(VaultOperations vaultOperations) {
		this(vaultOperations, "transit", 32);
	}

	/**
	 * Creates a new {@link VaultBytesKeyGenerator} initialized to generate {@code length}
	 * random bytes.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 * @param transitPath path of the transit backend, must not be {@literal null} or
	 * empty.
	 * @param length number of random bytes to generate. Must be greater than zero.
	 */
	public VaultBytesKeyGenerator(VaultOperations vaultOperations, String transitPath,
			int length) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(transitPath, "Transit path must not be null or empty");
		Assert.isTrue(length > 0, "Byte count must be greater zero");

		this.vaultOperations = vaultOperations;
		this.transitPath = transitPath;
		this.length = length;
	}

	@Override
	public int getKeyLength() {
		return length;
	}

	@Override
	public byte[] generateKey() {

		VaultResponse response = vaultOperations.write(
				String.format("%s/random/%d", transitPath, getKeyLength()),
				Collections.singletonMap("format", "base64"));

		String randomBytes = (String) response.getRequiredData().get("random_bytes");
		return Base64Utils.decodeFromString(randomBytes);
	}
}
