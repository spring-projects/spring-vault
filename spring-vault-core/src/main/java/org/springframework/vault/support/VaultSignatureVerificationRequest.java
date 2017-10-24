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

import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;

/**
 * Request for a signature verification.
 *
 * @author Luander Ribeiro
 */
public class VaultSignatureVerificationRequest {

    private final String algorithm;

    private final String input;

    private final String signature;

    private final String hmac;

    private VaultSignatureVerificationRequest(String algorithm, String input,
                                              String signature, String hmac) {
        this.algorithm = algorithm;
        this.input = Base64Utils.encodeToString(input.getBytes());
        this.signature = String.valueOf(signature);
        this.hmac = hmac;
    }

    /**
     * @return New instance of
     * {@link VaultSignatureVerificationRequest.VaultSignatureVerificationRequestBuilder}
     */
    public static VaultSignatureVerificationRequestBuilder builder() {
        return new VaultSignatureVerificationRequestBuilder();
    }

    /**
     * Create a new {@link VaultHmacRequest} specifically for a {@code algorithm}.
     *
     * @param algorithm Specify the algorithm to be used for the operation. If not set,
     *                  sha2-256 is used.
     *                  Supported algorithms are:
     *                  sha2-224, sha2-256, sha2-384, sha2-512
     * @return a new {@link VaultHmacRequest} for the given {@code algorithm}.
     */
    public VaultSignatureVerificationRequest ofAlgorithm(String algorithm) {
        return builder().algorithm(algorithm).build();
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
     * @return Signature resulting of a sign operation.
     */
    public String getSignature() {
        return signature;
    }

    /**
     * @return Digest resulting of a Hmac operation.
     */
    public String getHmac() {
        return hmac;
    }

    public static class VaultSignatureVerificationRequestBuilder {

        private String algorithm = "sha2-256";

        private Plaintext input;

        private Signature signature;

        private String hmac;

        /**
         * Configure the algorithm to be used for the operation.
         *
         * @param algorithm Specify the algorithm to be used for the operation. If not set,
         *                  sha2-256 is used.
         *                  Supported algorithms are:
         *                  sha2-224, sha2-256, sha2-384, sha2-512
         * @return {@code this} {@link VaultHmacRequest.VaultHmacRequestBuilder}.
         */
        public VaultSignatureVerificationRequestBuilder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        /**
         * Configure the signature to be verified.
         *
         * @param signature to be verified.
         *                  Either signature or hmac must not be empty of {@literal null}
         * @return {@code this} {@link VaultHmacRequest.VaultHmacRequestBuilder}.
         */
        public VaultSignatureVerificationRequestBuilder signature(Signature signature) {
            this.signature = signature;
            return this;
        }

        /**
         * Configure the hmac to be verified.
         *
         * @param hmac to be verified.
         *                  Either signature or hmac must not be empty of {@literal null}
         * @return {@code this} {@link VaultHmacRequest.VaultHmacRequestBuilder}.
         */
        public VaultSignatureVerificationRequestBuilder hmac(String hmac) {
            this.hmac = hmac;
            return this;
        }

        /**
         * Configure the input to be used to create the digest.
         *
         * @param input base input to create the digest, must not be empty or {@literal null}.
         * @return {@code this} {@link VaultHmacRequest.VaultHmacRequestBuilder}.
         */
        public VaultSignatureVerificationRequestBuilder input(Plaintext input) {
            this.input = input;
            return this;
        }

        /**
         * Configure the input to be used to create the digest.
         *
         * @param input base input to create the digest, must not be empty or {@literal null}.
         * @return {@code this} {@link VaultHmacRequest.VaultHmacRequestBuilder}.
         */
        public VaultSignatureVerificationRequestBuilder input(String input) {
            this.input = Plaintext.of(input);
            return this;
        }

        /**
         * Build a new {@link VaultHmacRequest} instance. Requires
         * {@link #input(String)} or {@link #input(Plaintext)} to be configured.
         *
         * @return a new {@link VaultHmacRequest}.
         */
        public VaultSignatureVerificationRequest build() {

            Assert.hasText(input.asString(), "Input must not be empty");

            return new VaultSignatureVerificationRequest(algorithm,
                    input.asString(), signature.getSignature(), hmac);
        }

    }
}
