/*
 * Copyright 2017-2025 the original author or authors.
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
package org.springframework.vault.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VaultTransitContext}.
 *
 * @author Mark Paluch
 */
class VaultTransitContextUnitTests {

	@Test
	void rejectsNullContext() {
		assertThatIllegalArgumentException().isThrownBy(() -> VaultTransitContext.fromContext(null));
	}

	@Test
	void createsFromContext() {

		byte[] bytes = new byte[] { 1 };

		VaultTransitContext context = VaultTransitContext.fromContext(bytes);

		assertThat(context.getContext()).isEqualTo(bytes);
		assertThat(context.getNonce()).isEmpty();
	}

	@Test
	void rejectsNullNonce() {
		assertThatIllegalArgumentException().isThrownBy(() -> VaultTransitContext.fromNonce(null));
	}

	@Test
	void createsFromNonce() {

		byte[] bytes = new byte[] { 1 };

		VaultTransitContext context = VaultTransitContext.fromNonce(bytes);

		assertThat(context.getNonce()).isEqualTo(bytes);
		assertThat(context.getContext()).isEmpty();
	}

}
