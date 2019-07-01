/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.vault.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Annotation to guard a {@code @Test} to run only if the required minimum Vault version
 * is available.
 * <p>
 * When applied at the class level, all test methods within that class are automatically
 * disabled as well.
 *
 * <p>
 * When applied at the method level, the presence of this annotation does not prevent the
 * test class from being instantiated. Rather, it prevents the execution of the test
 * method and method-level lifecycle callbacks such as {@code @BeforeEach} methods,
 * {@code @AfterEach} methods, and corresponding extension APIs.
 *
 * @author Mark Paluch
 * @see VaultVersionExtension
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(VaultVersionExtension.class)
public @interface RequiresVaultVersion {

	/**
	 * Minimum version to run a test.
	 */
	String value();
}
