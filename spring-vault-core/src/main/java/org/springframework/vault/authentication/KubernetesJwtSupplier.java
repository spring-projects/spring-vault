/*
 * Copyright 2016-2019 the original author or authors.
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

import java.util.function.Supplier;

/**
 * Interface to obtain a Kubernetes Service Account Token for Kubernetes authentication.
 * Implementations are used by {@link KubernetesAuthentication}.
 *
 * @author Michal Budzyn
 * @author Mark Paluch
 * @since 2.0
 * @see KubernetesAuthentication
 */
@FunctionalInterface
public interface KubernetesJwtSupplier extends Supplier<String> {

	/**
	 * Get a JWT for Kubernetes authentication.
	 *
	 * @return the Kubernetes Service Account JWT.
	 */
	@Override
	String get();

	/**
	 * Retrieve a cached {@link KubernetesJwtSupplier} that obtains the JWT early and
	 * reuses the token for each {@link #get()} call. This is useful to prevent I/O
	 * operations in e.g. reactive usage.
	 * <p>
	 * Reusing a cached token can lead to authentication failures if the token expires.
	 *
	 * @return a caching {@link KubernetesJwtSupplier}.
	 * @since 2.2
	 */
	default KubernetesJwtSupplier cached() {

		String jwt = get();

		return () -> jwt;
	}
}
