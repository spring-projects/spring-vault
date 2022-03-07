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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Simple reader for {@literal PEM} files. Unlike {@link PemObject} {@link PemReader} is
 * responsible for reading {@literal PEM} content only. The {@link PemItem} is the
 * representation of a single object within the {@literal PEM} structure. Furthermore
 * {@link PemReader} has no dependencies to cryptographical implementations.
 *
 * @author Alex Bremora
 * @since 2.4
 */
class PemReader {

	public static final String PEM_PREFIX = "-----";

	public static final String PEM_PREFIX_BEGIN = PEM_PREFIX + "BEGIN";

	public static final String PEM_PREFIX_END = PEM_PREFIX + "END";

	private static final String REGEX_PEM_START = "(?=(-{5}BEGIN))";

	public static List<PemItem> parse(String content) {
		if (content == null) {
			return new ArrayList<>(0);
		}

		String[] pemContents = content.trim().split(REGEX_PEM_START);

		return Arrays.stream(pemContents).filter(c -> c != null && !c.isEmpty()).map(c -> PemItem.parse(c))
				.collect(Collectors.toList());
	}

}
