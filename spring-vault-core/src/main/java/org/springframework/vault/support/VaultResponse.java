/*
 * Copyright 2016-2025 the original author or authors.
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

import java.util.Map;

/**
 * Value object to bind generic Vault HTTP API responses.
 * <p>This class binds the data element to a {@link Map} for generic response
 * handling.
 *
 * @author Spencer Gibb
 * @author Mark Paluch
 */
public class VaultResponse extends VaultResponseSupport<Map<String, Object>> {

}
