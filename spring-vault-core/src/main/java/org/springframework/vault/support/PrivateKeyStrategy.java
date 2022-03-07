/*
 * Copyright 2016-2022 the original author or authors.
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

import java.security.spec.KeySpec;

/**
 * Base type for private key support.
 *
 * @author Alex Bremora
 * @since 2.4
 */
interface PrivateKeyStrategy {

	/**
	 * Get name of the implemented strategy.
	 * @return name of the strategy.
	 */
	String getName();

	/**
	 * Get {@link KeySpec} of the implemented strategy.
	 * @return {@link KeySpec} of the strategy.
	 */
	KeySpec getKeySpec(byte[] privateKey);

}
