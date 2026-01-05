/*
 * Copyright 2021-present the original author or authors.
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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import org.springframework.util.Assert;

/**
 * Default implementation of {@link GoogleCredentialsAccountIdAccessor}. Used by
 * {@link GcpIamCredentialsAuthentication}.
 *
 * @author Andreas Gebauer
 * @since 2.3.2
 * @see GcpIamCredentialsAuthentication
 */
enum DefaultGoogleCredentialsAccessors implements GoogleCredentialsAccountIdAccessor {

	INSTANCE;

	/**
	 * Get the service account id (email) to be placed in the signed JWT.
	 * @param credentials credentials object to obtain the service account id from.
	 * @return the service account id to use.
	 */
	@Override
	public String getServiceAccountId(GoogleCredentials credentials) {
		Assert.notNull(credentials, "GoogleCredentials must not be null");
		Assert.isInstanceOf(ServiceAccountCredentials.class, credentials,
				"The configured GoogleCredentials does not represent a service account. Configure the service account id with GcpIamCredentialsAuthenticationOptionsBuilder#serviceAccountId(String).");
		return ((ServiceAccountCredentials) credentials).getAccount();
	}

}
