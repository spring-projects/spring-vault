/*
 * Copyright 2022-2024 the original author or authors.
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
package org.springframework.vault.repository.support;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.history.RevisionMetadata;
import org.springframework.vault.support.Versioned;

/**
 * @author Mark Paluch
 */
public class VaultRevisionMetadata implements RevisionMetadata<Integer> {

	private final Versioned.Metadata metadata;

	public VaultRevisionMetadata(Versioned<?> versioned) {
		this(versioned.getRequiredMetadata());
	}

	public VaultRevisionMetadata(Versioned.Metadata metadata) {
		this.metadata = metadata;
	}

	@Override
	public Optional<Integer> getRevisionNumber() {
		return Optional.of(metadata.getVersion().getVersion());
	}

	@Override
	public Optional<Instant> getRevisionInstant() {
		return Optional.of(metadata.getCreatedAt());
	}

	@Override
	public <T> T getDelegate() {
		return (T) metadata;
	}

}
