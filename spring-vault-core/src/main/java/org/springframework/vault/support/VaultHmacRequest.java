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
package org.springframework.vault.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;

/**
 * Request for a HMAC Digest.
 *
 * @author Luander Ribeiro
 */
public class VaultHmacRequest {

    @JsonProperty("key_version")
    private final int keyVersion;

    private final String algorithm;

    private final String input;

    @JsonIgnore
    private final VaultTransitContext context;

    private VaultHmacRequest(int keyVersion, String algorithm,
                             String input, VaultTransitContext context) {
        this.algorithm = algorithm;
        this.input = Base64Utils.encodeToString(input.getBytes());
        this.keyVersion = keyVersion;
        this.context = context;
    }

    /**
     * @return New instance of {@link VaultHmacRequest.VaultHmacRequestBuilder}
     */
    public static VaultHmacRequestBuilder builder() {
        return new VaultHmacRequestBuilder();
    }

    /**
     * Create a new {@link VaultHmacRequest} specifically for an {@code input}.
     * Uses {@code sha2-256} algorithm.
     *
     * @return a new {@link VaultHmacRequest} for the given {@code input}.
     */
    public static VaultHmacRequest ofInput(Plaintext input) {
        return builder().input(input).build();
    }

    /**
     * @return Algorithm used for creating the digest.
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * @return plain text input used as basis to generate the digest.
     */
    public String getInput() {
        return input;
    }

    /**
     * @return Version of the key used. If not set the latest version is used.
     */
    public int getKeyVersion() {
        return keyVersion;
    }

    public VaultTransitContext getContext() {
        return context;
    }

    public static class VaultHmacRequestBuilder {

        private int keyVersion;

        private String algorithm = "sha2-256";

        private Plaintext input;

        private VaultTransitContext context;

        /**
         * Configure the algorithm to be used for the operation.
         *
         * @param algorithm Specify the algorithm to be used for the operation. If not set,
         *                  sha2-256 is used.
         *                  Supported algorithms are:
         *                  sha2-224, sha2-256, sha2-384, sha2-512
         * @return {@code this}
         */
        public VaultHmacRequestBuilder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /**
         * Configure the input to be used to create the digest.
         *
         * @param input base input to create the digest, must not be empty or {@literal null}.
         * @return {@code this}.
         */
        public VaultHmacRequestBuilder input(Plaintext input) {
            this.input = input;
            this.context = input.getContext();
            return this;
        }

        /**
         * Configure the input to be used to create the digest.
         *
         * @param input base input to create the digest, must not be empty or {@literal null}.
         * @return {@code this}
         */
        public VaultHmacRequestBuilder input(String input) {
            this.input = Plaintext.of(input);
            this.context = VaultTransitContext.empty();
            return this;
        }

        /**
         * Configure the version to be used for the operation.
         *
         * @param version key version to be used. If not set, uses the latest version.
         * @return {@code this} {@link VaultHmacRequest.VaultHmacRequestBuilder}.
         */
        public VaultHmacRequestBuilder keyVersion(int version) {
            this.keyVersion = version;
            return this;
        }

        /**
         * Build a new {@link VaultHmacRequest} instance. Requires
         * {@link #input(String)} or {@link #input(Plaintext)} to be configured.
         *
         * @return a new {@link VaultHmacRequest}.
         */
        public VaultHmacRequest build() {

            Assert.notNull(input, "Input must not be empty");

            return new VaultHmacRequest(keyVersion, algorithm, input.asString(), context);
        }



    }
}
