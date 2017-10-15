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
 * Value object representing Hmac digest with an optional {@link VaultTransitContext}.
 *
 * @author Luander Ribeiro
 */
@EqualsAndHashCode
public class Hmac {

    private final String hmac;

    private final VaultTransitContext context;

    private Hmac(String hmac, VaultTransitContext context) {
        this.hmac = hmac;
        this.context = context;
    }

    /**
     * Factory method to create {@link Hmac} from a byte sequence.
     *
     * @param hmac the Hmac digest, must not be {@literal null} or empty.
     * @return the {@link Hmac} for {@code plaintext}.
     */
    public static Hmac of(byte[] hmac) {

        Assert.isTrue(!ObjectUtils.isEmpty(hmac),
                "Hmac must not be null or empty");

        return new Hmac(Arrays.toString(hmac), VaultTransitContext.empty());
    }

    /**
     * Factory method to create {@link Hmac} from the given {@code hmac}.
     *
     * @param hmac the Hmac digest, must not be {@literal null} or empty.
     * @return the {@link Hmac} for {@code hmac}.
     */
    public static Hmac of(String hmac) {

        Assert.hasText(hmac, "Hmac digest must not be null or empty");

        return new Hmac(hmac, VaultTransitContext.empty());
    }

    public String getHmac() {
        return hmac;
    }

    public VaultTransitContext getContext() {
        return context;
    }

    /**
     * Create a new {@link Hmac} object from this Hmac digest associated with the
     * given {@link VaultTransitContext}.
     *
     * @param context transit context.
     * @return the new {@link Hmac} object.
     */
    public Hmac with(VaultTransitContext context) {
        return new Hmac(getHmac(), context);
    }
}
