/*
 * Copyright 2018-2024 the original author or authors.
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

/**
 * Interface to obtain a GCP project id for GCP IAM authentication. Implementations are
 * used by {@link GcpIamAuthentication}.
 *
 * @author Magnus Jungsbluth
 * @author Mark Paluch
 * @since 2.1
 * @see GcpIamAuthentication
 */
@FunctionalInterface
public interface GcpProjectIdAccessor {

	/**
	 * Get a the GCP project id to used in Google Cloud IAM API calls.
	 * @param credential the credential object to obtain the project id from.
	 * @return the service account id to use.
	 */
	String getProjectId(GoogleCredential credential);

}
