/*
 * Copyright 2017-2018 the original author or authors.
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
 * Mechanism to retrieve a Kubernetes service account token.
 * <p>
 * A file containing a token for a pod's service account is automatically mounted at
 * {@code /var/run/secrets/kubernetes.io/serviceaccount/token}.
 *
 * @author Michal Budzyn
 * @author Mark Paluch
 * @since 2.0
 * @see KubernetesJwtSupplier
 */
public class KubernetesServiceAccountTokenFile implements KubernetesJwtSupplier {

	/**
	 * Default path to the service account token file.
	 */
	public static final String DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/token";

	private byte[] token;

	/**
	 * Create a new {@link KubernetesServiceAccountTokenFile} pointing to the
	 * {@link #DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE}. Construction fails with an
	 * exception if the file does not exist.
	 *
	 * @throws IllegalArgumentException if the
	 * {@link #DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE} does not exist.
	 */
	public KubernetesServiceAccountTokenFile() {
		this(DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE);
	}

	/**
	 * Create a new {@link KubernetesServiceAccountTokenFile}
	 * {@link KubernetesServiceAccountTokenFile} from a {@code path}.
	 *
	 * @param path path to the service account token file.
	 * @throws IllegalArgumentException if the{@code path} does not exist.
	 */
	public KubernetesServiceAccountTokenFile(String path) {
		this(new FileSystemResource(path));
	}

	/**
	 * Create a new {@link KubernetesServiceAccountTokenFile}
	 * {@link KubernetesServiceAccountTokenFile} from a {@link File} handle.
	 *
	 * @param file path to the service account token file.
	 * @throws IllegalArgumentException if the{@code path} does not exist.
	 */
	public KubernetesServiceAccountTokenFile(File file) {
		this(new FileSystemResource(file));
	}

	/**
	 * Create a new {@link KubernetesServiceAccountTokenFile}
	 * {@link KubernetesServiceAccountTokenFile} from a {@link Resource} handle.
	 *
	 * @param resource resource pointing to the service account token file.
	 * @throws IllegalArgumentException if the{@code path} does not exist.
	 */
	public KubernetesServiceAccountTokenFile(Resource resource) {

		Assert.isTrue(resource.exists(),
				() -> String.format("Resource %s does not exist", resource));

		try {
			this.token = readToken(resource);
		}
		catch (IOException e) {
			throw new VaultException(String.format(
					"Kube JWT token retrieval from %s failed", resource), e);
		}
	}

	@Override
	public String get() {
		return new String(token, StandardCharsets.US_ASCII);
	}

	/**
	 * Read the token from {@link Resource}.
	 *
	 * @param resource the resource to read from, must not be {@literal null}.
	 * @return the new byte array that has been copied to (possibly empty).
	 * @throws IOException in case of I/O errors.
	 */
	protected static byte[] readToken(Resource resource) throws IOException {

		Assert.notNull(resource, "Resource must not be null");

		try (InputStream is = resource.getInputStream()) {
			return StreamUtils.copyToByteArray(is);
		}
	}
}
