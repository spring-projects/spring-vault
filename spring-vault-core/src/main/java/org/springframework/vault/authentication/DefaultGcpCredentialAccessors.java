/*
 * Copyright 2018-2019 the original author or authors.
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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Default implementation of{@link GcpProjectIdAccessor} and
 * {@link GcpServiceAccountIdAccessor}. Used by {@link GcpIamAuthentication}.
 *
 * @author Magnus Jungsbluth
 * @author Mark Paluch
 * @since 2.1
 * @see GcpIamAuthentication
 */
enum DefaultGcpCredentialAccessors implements GcpProjectIdAccessor,
		GcpServiceAccountIdAccessor {

	INSTANCE;

	/**
	 * Get a the service account id (email) to be placed in the signed JWT.
	 *
	 * @param credential credential object to obtain the service account id from.
	 * @return the service account id to use.
	 */
	@Override
	public String getServiceAccountId(GoogleCredential credential) {

		Assert.notNull(credential, "GoogleCredential must not be null");
		Assert.notNull(
				credential.getServiceAccountId(),
				"The configured GoogleCredential does not represent a service account. Configure the service account id with GcpIamAuthenticationOptionsBuilder#serviceAccountId(String).");

		return credential.getServiceAccountId();
	}

	/**
	 * Get a the GCP project id to used in Google Cloud IAM API calls.
	 *
	 * @param credential the credential object to obtain the project id from.
	 * @return the service account id to use.
	 */
	@Override
	public String getProjectId(GoogleCredential credential) {

		Assert.notNull(credential, "GoogleCredential must not be null");

		return StringUtils.isEmpty(credential.getServiceAccountProjectId()) ? "-"
				: credential.getServiceAccountProjectId();
	}
}
