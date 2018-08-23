/*
 * Copyright 2018 the original author or authors.
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

import org.springframework.util.Assert;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;

/**
 * Default implementation to obtain a service account id for GCP IAM authentication.
 * Used by {@link GcpIamAuthentication}.
 *
 * @author Magnus Jungsbluth
 * @since 2.1
 * @see GcpIamAuthentication
 */
public class DefaultGcpServiceAccountIdProvider implements GcpServiceAccountIdProvider {

    @Override
    public String getServiceAccountId(GoogleCredential credential) {
        Assert.notNull(credential.getServiceAccountId(), "The configured GoogleCredential does not represent a service account. Configure the service account id manually");

        return credential.getServiceAccountId();
    }

}
