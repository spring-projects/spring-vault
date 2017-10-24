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

import lombok.EqualsAndHashCode;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import java.util.Arrays;

/**
 * Value object representing Signature with an optional {@link VaultTransitContext}.
 *
 * @author Luander Ribeiro
 */
@EqualsAndHashCode
public class Signature {

    private final String signature;

    private final VaultTransitContext context;

    private Signature(String signature, VaultTransitContext context) {
        this.signature = signature;
        this.context = context;
    }

    /**
     * Factory method to create {@link Signature} from the given {@code signature}.
     *
     * @param signature the signature, must not be {@literal null} or empty.
     * @return the {@link Signature} for {@code signature}.
     */
    public static Signature of(byte[] signature) {

        Assert.isTrue(!ObjectUtils.isEmpty(signature),
                "Signature must not be null or empty");

        return new Signature(Arrays.toString(signature), VaultTransitContext.empty());
    }

    /**
     * Factory method to create {@link Signature} from the given {@code signature}.
     *
     * @param signature the signature, must not be {@literal null} or empty.
     * @return the {@link Signature} for {@code signature}.
     */
    public static Signature of(String signature) {

        Assert.hasText(signature, "Signature must not be null or empty");

        return new Signature(signature, VaultTransitContext.empty());
    }

    public String getSignature() {
        return signature;
    }

    public VaultTransitContext getContext() {
        return context;
    }

    /**
     * Create a new {@link Signature} object from this signature associated with the
     * given {@link VaultTransitContext}.
     *
     * @param context transit context.
     * @return the new {@link Signature} object.
     */
    public Signature with(VaultTransitContext context) {
        return new Signature(getSignature(), context);
    }
}

