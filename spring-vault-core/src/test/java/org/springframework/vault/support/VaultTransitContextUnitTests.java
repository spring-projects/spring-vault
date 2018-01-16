/*
 * Copyright 2017-2018 the original author or authors.
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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link VaultTransitContext}.
 *
 * @author Mark Paluch
 */
public class VaultTransitContextUnitTests {

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullContext() {
		VaultTransitContext.fromContext(null);
	}

	@Test
	public void createsFromContext() {

		byte[] bytes = new byte[] { 1 };

		VaultTransitContext context = VaultTransitContext.fromContext(bytes);

		assertThat(context.getContext()).isEqualTo(bytes);
		assertThat(context.getNonce()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullNonce() {
		VaultTransitContext.fromNonce(null);
	}

	@Test
	public void createsFromNonce() {

		byte[] bytes = new byte[] { 1 };

		VaultTransitContext context = VaultTransitContext.fromNonce(bytes);

		assertThat(context.getNonce()).isEqualTo(bytes);
		assertThat(context.getContext()).isEmpty();
	}
}
