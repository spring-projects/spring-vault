/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.vault.core

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.vault.util.MockVaultClient

/**
 * Unit tests to validate [VaultTemplate] can decode into a Kotlin class.
 *
 * @author Braden Rayhorn
 */
class VaultTemplateKotlinJsonDecodingTests {

    @Test
    fun `VaultTemplate read extension should decode JSON into Kotlin class`() {
        val mockVaultClient = MockVaultClient.create()

        mockVaultClient.expect(requestTo(containsString("secret/mykey")))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"data":{"secretString":"abc"}}""",
                    MediaType.APPLICATION_JSON
                )
            )

        val response = VaultTemplate(mockVaultClient).read<SecretData>("secret/mykey")

        assertThat(response?.data?.secretString).isEqualTo("abc")
    }

    class SecretData(val secretString: String)

}
