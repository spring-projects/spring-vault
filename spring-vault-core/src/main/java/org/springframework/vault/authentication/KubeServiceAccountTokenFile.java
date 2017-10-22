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
package org.springframework.vault.authentication;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.vault.VaultException;

/**
 * Mechanism to retrieve a Kubernetes service account token.
 * <p>
 * A file containing a token for a pod’s service account is automatically mounted at
 * <b>/var/run/secrets/kubernetes.io/serviceaccount/token</b>
 *
 * @author Michal Budzyn
 * @see KubeJwtSupplier
 */
public class KubeServiceAccountTokenFile implements KubeJwtSupplier {

	public static final String DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE = "/var/run/secrets/kubernetes.io/serviceaccount/token";

	private final Resource resource;

	public KubeServiceAccountTokenFile() {
		this(DEFAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE);
	}

	public KubeServiceAccountTokenFile(String fileName) {
		this(new FileSystemResource(fileName));
	}

	public KubeServiceAccountTokenFile(File file) {
		this(new FileSystemResource(file));
	}

	public KubeServiceAccountTokenFile(Resource resource) {
		this.resource = resource;
	}

	@Override
	public String getKubeJwt() {
		try (InputStream is = resource.getInputStream()) {
			return StreamUtils.copyToString(is, US_ASCII);
		}
		catch (IOException e) {
			throw new VaultException(
					String.format("Kube JWT token retrieval from %s failed", resource),
					e);
		}
	}
}
