/*
 * Copyright 2017-2025 the original author or authors.
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

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * Mechanism to retrieve a Kubernetes service account token.
 * <p>A file containing a token for a pod's service account is automatically
 * mounted at {@code /var/run/secrets/kubernetes.io/serviceaccount/token}.
 *
 * @author Michal Budzyn
 * @author Mark Paluch
 * @since 2.0
 * @see KubernetesJwtSupplier
 */
public class KubernetesServiceAccountTokenFile extends ResourceCredentialSupplier implements KubernetesJwtSupplier {

	/**
	 * Default path to the service account token file.
	 */
	public static final String DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/token";


	/**
	 * Create a new {@link KubernetesServiceAccountTokenFile} pointing to the
	 * {@link #DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE}. Construction fails
	 * with an exception if the file does not exist.
	 * @throws IllegalArgumentException if the
	 * {@link #DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE} does not exist.
	 */
	public KubernetesServiceAccountTokenFile() {
		this(DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE);
	}

	/**
	 * Create a new {@link KubernetesServiceAccountTokenFile}
	 * {@link KubernetesServiceAccountTokenFile} from a {@code path}.
	 * @param path path to the service account token file.
	 * @throws IllegalArgumentException if the{@code path} does not exist.
	 */
	public KubernetesServiceAccountTokenFile(String path) {
		this(new FileSystemResource(path));
	}

	/**
	 * Create a new {@link KubernetesServiceAccountTokenFile}
	 * {@link KubernetesServiceAccountTokenFile} from a {@link File} handle.
	 * @param file path to the service account token file.
	 * @throws IllegalArgumentException if the{@code path} does not exist.
	 */
	public KubernetesServiceAccountTokenFile(File file) {
		this(new FileSystemResource(file));
	}

	/**
	 * Create a new {@link KubernetesServiceAccountTokenFile}
	 * {@link KubernetesServiceAccountTokenFile} from a {@link Resource} handle.
	 * @param resource resource pointing to the service account token file.
	 * @throws IllegalArgumentException if the{@code path} does not exist.
	 */
	public KubernetesServiceAccountTokenFile(Resource resource) {
		super(resource);
	}

}
