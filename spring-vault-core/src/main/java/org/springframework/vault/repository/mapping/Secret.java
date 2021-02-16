/*
 * Copyright 2017-2021 the original author or authors.
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
package org.springframework.vault.repository.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.keyvalue.annotation.KeySpace;

/**
 * {@link Secret} marks objects as aggregate roots to be stored in Vault.
 *
 * @author Mark Paluch
 */
@Persistent
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(value = { ElementType.TYPE })
@KeySpace
public @interface Secret {

	/**
	 * The prefix to distinguish between domain types. The attribute supports SpEL
	 * expressions to dynamically calculate the keyspace based on a per-operation basis.
	 *
	 * @see KeySpace
	 */
	@AliasFor(annotation = KeySpace.class, attribute = "value")
	String value() default "";

	/**
	 * Secret backend mount, defaults to {@literal secret}. The attribute supports SpEL
	 * expressions to dynamically calculate the backend based on a per-operation basis.
	 */
	String backend() default "secret";

}
