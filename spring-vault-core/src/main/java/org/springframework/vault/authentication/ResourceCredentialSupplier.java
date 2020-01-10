/*
 * Copyright 2019-2020 the original author or authors.
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
package org.springframework.vault.authentication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.vault.VaultException;

/**
 * Mechanism to retrieve a credential from a {@link Resource}.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see CredentialSupplier
 */
public class ResourceCredentialSupplier implements CredentialSupplier {

	private final Resource resource;

	/**
	 * Create a new {@link ResourceCredentialSupplier} {@link ResourceCredentialSupplier}
	 * from a {@code path}.
	 *
	 * @param path path to the file holding the credential.
	 * @throws IllegalArgumentException if the{@code path} does not exist.
	 */
	public ResourceCredentialSupplier(String path) {
		this(new FileSystemResource(path));
	}

	/**
	 * Create a new {@link ResourceCredentialSupplier} {@link ResourceCredentialSupplier}
	 * from a {@link File} handle.
	 *
	 * @param file path to the file holding the credential.
	 * @throws IllegalArgumentException if the{@code path} does not exist.
	 */
	public ResourceCredentialSupplier(File file) {
		this(new FileSystemResource(file));
	}

	/**
	 * Create a new {@link ResourceCredentialSupplier} {@link ResourceCredentialSupplier}
	 * from a {@link Resource} handle.
	 *
	 * @param resource resource pointing to the resource holding the credential.
	 * @throws IllegalArgumentException if the {@link Resource} does not exist.
	 */
	public ResourceCredentialSupplier(Resource resource) {

		Assert.isTrue(resource.exists(),
				() -> String.format("Resource %s does not exist", resource));

		this.resource = resource;
	}

	@Override
	public String get() {

		try {
			return new String(readToken(this.resource), StandardCharsets.US_ASCII);
		}
		catch (IOException e) {
			throw new VaultException(
					String.format("Credential retrieval from %s failed", this.resource),
					e);
		}
	}

	/**
	 * Read the token from {@link Resource}.
	 *
	 * @param resource the resource to read from, must not be {@literal null}.
	 * @return the new byte array that has been copied to (possibly empty).
	 * @throws IOException in case of I/O errors.
	 */
	private static byte[] readToken(Resource resource) throws IOException {

		Assert.notNull(resource, "Resource must not be null");

		try (InputStream is = resource.getInputStream()) {
			return StreamUtils.copyToByteArray(is);
		}
	}
}
