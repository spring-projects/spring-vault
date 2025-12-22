/*
 * Copyright 2022-2025 the original author or authors.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.history.Revision;
import org.springframework.data.history.Revisions;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.core.VaultKeyValueMetadataOperations;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.core.util.KeyValueDelegate;
import org.springframework.vault.repository.convert.SecretDocument;
import org.springframework.vault.repository.convert.VaultConverter;
import org.springframework.vault.repository.core.VaultKeyValueTemplate;
import org.springframework.vault.support.VaultMetadataResponse;
import org.springframework.vault.support.Versioned;

/**
 * Vault-based {@link RevisionRepository} providing revision metadata for
 * versioned secrets.
 *
 * @author Mark Paluch
 * @since 2.4
 */
public class VaultRevisionRepository<T> implements RevisionRepository<T, String, Integer> {

	private final EntityInformation<T, String> metadata;

	private final String keyspacePath;

	private final VaultVersionedKeyValueOperations operations;

	private final VaultKeyValueMetadataOperations metadataOperations;

	private final VaultConverter converter;


	public VaultRevisionRepository(EntityInformation<T, String> metadata, String keyspace,
			VaultKeyValueTemplate keyValueTemplate) {

		Assert.notNull(metadata, "EntityInformation must not be null");
		Assert.notNull(keyValueTemplate, "VaultKeyValueTemplate must not be null");

		this.metadata = metadata;
		this.converter = keyValueTemplate.getConverter();

		VaultOperations vaultOperations = keyValueTemplate.getVaultOperations();

		KeyValueDelegate delegate = new KeyValueDelegate(vaultOperations);
		KeyValueDelegate.MountInfo mountInfo = delegate.getMountInfo(keyspace);

		if (!mountInfo.isAvailable()) {
			throw new IllegalStateException("Mount not available under " + keyspace);
		}

		if (!delegate.isVersioned(keyspace)) {
			throw new IllegalStateException("Mount under " + keyspace + " is not versioned");
		}

		this.keyspacePath = keyspace.substring(mountInfo.getPath().length());
		this.operations = vaultOperations.opsForVersionedKeyValue(mountInfo.getPath());
		this.metadataOperations = this.operations.opsForKeyValueMetadata();
	}


	@Override
	public Optional<Revision<Integer, T>> findLastChangeRevision(String id) {

		Assert.notNull(id, "Identifier must not be null");

		return toRevision(operations.get(getPath(id)), id);
	}

	@Override
	public Revisions<Integer, T> findRevisions(String id) {

		VaultMetadataResponse metadata = metadataOperations.get(getPath(id));

		if (metadata == null) {
			return Revisions.none();
		}

		return Revisions.of(collectRevisions(id, metadata.getVersions()));
	}

	private List<Revision<Integer, T>> collectRevisions(String id, List<Versioned.Metadata> versions) {

		List<Revision<Integer, T>> revisions = new ArrayList<>();

		for (Versioned.Metadata version : versions) {

			Versioned<Map<String, Object>> versioned = operations.get(getPath(id), version.getVersion());

			if (versioned == null) {
				continue;
			}

			T entity = versioned.hasData() ? converter.read(this.metadata.getJavaType(), createDocument(id, versioned))
					: null;
			revisions.add(Revision.of(new VaultRevisionMetadata(versioned), entity));
		}
		return revisions;
	}

	@Override
	public Page<Revision<Integer, T>> findRevisions(String id, Pageable pageable) {

		if (pageable.isUnpaged()) {
			return new PageImpl<>(Collections.emptyList());
		}

		VaultMetadataResponse metadata = metadataOperations.get(getPath(id));
		if (metadata == null || pageable.getOffset() > metadata.getVersions().size()) {
			return Page.empty(pageable);
		}

		List<Versioned.Metadata> versions = metadata.getVersions();

		int toIndex = Math.min(versions.size(), Math.toIntExact(pageable.getOffset() + pageable.getPageSize()));
		List<Versioned.Metadata> metadataPage = versions.subList(Math.toIntExact(pageable.getOffset()), toIndex);

		List<Revision<Integer, T>> revisions = collectRevisions(id, metadataPage);

		return new PageImpl<>(revisions, pageable, versions.size());
	}

	@Override
	public Optional<Revision<Integer, T>> findRevision(String id, Integer revisionNumber) {

		Assert.notNull(id, "Identifier must not be null");
		Assert.notNull(revisionNumber, "Revision number must not be null");

		return toRevision(operations.get(getPath(id), Versioned.Version.from(revisionNumber)), id);
	}

	private Optional<Revision<Integer, T>> toRevision(@Nullable Versioned<Map<String, Object>> versioned, String id) {

		if (versioned == null) {
			return Optional.empty();
		}

		T entity = versioned.hasData() ? converter.read(metadata.getJavaType(), createDocument(id, versioned)) : null;
		return Optional.of(Revision.of(new VaultRevisionMetadata(versioned), entity));
	}

	private String getPath(String id) {
		return keyspacePath + "/" + id;
	}

	private SecretDocument createDocument(String id, Versioned<Map<String, Object>> versioned) {
		return new SecretDocument(id, versioned.getVersion().getVersion(), versioned.getRequiredData());
	}

}
