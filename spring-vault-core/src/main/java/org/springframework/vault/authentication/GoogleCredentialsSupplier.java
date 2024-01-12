/*
 * Copyright 2021-2024 the original author or authors.
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

import java.io.IOException;
import java.util.function.Supplier;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

/**
 * Interface to obtain a {@link ServiceAccountCredentials} for GCP IAM credentials
 * authentication. Implementations are used by {@link GcpIamCredentialsAuthentication}.
 *
 * @author Andreas Gebauer
 * @since 2.3.2
 * @see GcpIamCredentialsAuthentication
 */
@FunctionalInterface
public interface GoogleCredentialsSupplier extends Supplier<GoogleCredentials> {

	/**
	 * Exception-safe helper to get {@link ServiceAccountCredentials} from
	 * {@link #getCredentials}.
	 * @return the ServiceAccountCredentials for JWT signing.
	 */
	@Override
	default GoogleCredentials get() {

		try {
			return getCredentials();
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot obtain GoogleCredentials", e);
		}
	}

	/**
	 * Get a {@link GoogleCredentials} for GCP IAM credentials authentication via JWT
	 * signing.
	 * @return the {@link GoogleCredentials}.
	 * @throws IOException if the credentials lookup fails.
	 */
	GoogleCredentials getCredentials() throws IOException;

}
