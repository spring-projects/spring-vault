/*
 * Copyright 2019 the original author or authors.
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
 * Interface to obtain an arbitrary credential that is uses in
 * {@link ClientAuthentication} or {@link AuthenticationSteps} methods. Typically,
 * implementations obtain their credential from a file.
 *
 * @author Mark Paluch
 * @since 2.2
 * @see ResourceCredentialSupplier
 */
@FunctionalInterface
public interface CredentialSupplier extends Supplier<String> {

	/**
	 * Get a credential to be used with an authentication mechanism.
	 *
	 * @return the credential.
	 */
	@Override
	String get();

	/**
	 * Retrieve a cached {@link CredentialSupplier} that obtains the credential early and
	 * reuses the token for each {@link #get()} call. This is useful to prevent I/O
	 * operations in e.g. reactive usage.
	 * <p>
	 * Reusing a cached token can lead to authentication failures if the credential
	 * expires.
	 *
	 * @return a caching {@link CredentialSupplier}.
	 */
	default CredentialSupplier cached() {

		String credential = get();

		return () -> credential;
	}
}
