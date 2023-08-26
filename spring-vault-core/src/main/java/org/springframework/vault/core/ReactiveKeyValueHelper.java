/*
 * Copyright 2018-2022 the original author or authors.
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
package org.springframework.vault.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.vault.support.VaultResponseSupport;
import reactor.core.publisher.Mono;

/**
 * Helper for wrapping imperative operations.
 *
 * @author Timothy R. Weiand
 * @since 3.1
 */
class ReactiveKeyValueHelper {

	ReactiveKeyValueHelper() {
	}

	static <T> Mono<T> getRequiredData(VaultResponseSupport<T> support) {
		return Mono.fromCallable(support::getRequiredData);
	}

	static Map<String, Object> makeMetadata(final Map<String, Object> metadata, final Map<String, Object> requiredData,
			Map<String, ?> patch) {
		Map<String, Object> data = new LinkedHashMap<>(requiredData);
		data.putAll(patch);

		Map<String, Object> body = new HashMap<>();
		body.put("data", data);
		body.put("options", Collections.singletonMap("cas", metadata.get("version")));

		return body;
	}

}
