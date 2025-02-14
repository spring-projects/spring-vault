/*
 * Copyright 2020-2024 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link VaultTransitContext}.
 *
 * @author Lauren Voswinkel
 */
class VaultTransformContextUnitTests {

	@Test
	void rejectsNullTransformation() {
		assertThatIllegalArgumentException().isThrownBy(() -> VaultTransformContext.fromTransformation(null));
	}

	@Test
	void createsFromTransformation() {

		String transformName = "some_transformation";

		VaultTransformContext context = VaultTransformContext.fromTransformation(transformName);

		assertThat(context.getTransformation()).isEqualTo(transformName);
		assertThat(context.getTweak()).isEmpty();
	}

	@Test
	void rejectsNullTweak() {
		assertThatIllegalArgumentException().isThrownBy(() -> VaultTransformContext.fromTweak(null));
	}

	@Test
	void createsFromTweak() {

		byte[] bytes = new byte[] { 1 };

		VaultTransformContext context = VaultTransformContext.fromTweak(bytes);

		assertThat(context.getTweak()).isEqualTo(bytes);
		assertThat(context.getTransformation()).isEmpty();
	}

	@Test
	void createsContextWithReference() {

		String transformName = "some_transformation";
		byte[] tweak = { 1, 2, 3, 4, 5, 6, 7 };
		String referenceValue = "my-reference";

		VaultTransformContext context = VaultTransformContext.builder()
			.transformation(transformName)
			.tweak(tweak)
			.reference(referenceValue)
			.build();

		assertThat(context.getTransformation()).isEqualTo(transformName);
		assertThat(context.getTweak()).isEqualTo(tweak);
		assertThat(context.getReference()).isEqualTo(referenceValue);
	}

}
