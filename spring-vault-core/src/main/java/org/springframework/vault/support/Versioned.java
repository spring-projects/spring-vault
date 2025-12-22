/*
 * Copyright 2018-2025 the original author or authors.
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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Value object representing versioned secrets along {@link Version} metadata. A
 * versioned object can hold various states to represent:
 * <ul>
 * <li>Initial (not yet versioned) secrets via
 * {@link Versioned#create(Object)}</li>
 * <li>Versioned secrets via {@link Versioned#create(Object, Version)}</li>
 * <li>Versioned secrets with {@link Metadata} attached
 * {@link Versioned#create(Object, Metadata)}</li>
 * </ul>
 * <p>Versioned secrets follow a lifecycle that spans from creation to
 * destruction:
 * <ol>
 * <li>Creation of an unversioned secret: Secret is not yet persisted.</li>
 * <li>Versioned secret: Secret is persisted.</li>
 * <li>Superseded versioned secret: A newer secret version is stored.</li>
 * <li>Deleted versioned secret: Version was deleted. Can be undeleted.</li>
 * <li>Destroyed versioned secret: Version was destroyed.</li>
 * </ol>
 *
 * @author Mark Paluch
 * @author Jeroen Willemsen
 * @see Version
 * @see Metadata
 * @since 2.1
 */
public class Versioned<T> {

	private final @Nullable T data;

	private final Version version;

	private final @Nullable Metadata metadata;


	private Versioned(T data, Version version) {
		this.version = version;
		this.metadata = null;
		this.data = data;
	}

	private Versioned(@Nullable T data, Version version, Metadata metadata) {
		this.version = version;
		this.metadata = metadata;
		this.data = data;
	}


	/**
	 * Create a {@link Version#unversioned() unversioned} given secret.
	 * @param secret must not be {@literal null}.
	 * @return the {@link Versioned} object for {@code secret}
	 */
	public static <T> Versioned<T> create(T secret) {
		Assert.notNull(secret, "Versioned data must not be null");
		return new Versioned<>(secret, Version.unversioned());
	}

	/**
	 * Create a versioned secret object given {@code secret} and {@link Version}.
	 * Versioned secret may contain no actual data as they can be in a
	 * deleted/destroyed state.
	 * @param secret can be {@literal null}.
	 * @param version must not be {@literal null}.
	 * @return the {@link Versioned} object for {@code secret} and {@code Version}.
	 */
	public static <T> Versioned<T> create(@Nullable T secret, Version version) {
		Assert.notNull(version, "Version must not be null");
		return new Versioned<>(secret, version);
	}

	/**
	 * Create a versioned secret object given {@code secret} and {@link Metadata}.
	 * Versioned secret may contain no actual data as they can be in a
	 * deleted/destroyed state.
	 * @param secret can be {@literal null}.
	 * @param metadata must not be {@literal null}.
	 * @return the {@link Versioned} object for {@code secret} and {@link Metadata}.
	 */
	public static <T> Versioned<T> create(@Nullable T secret, Metadata metadata) {
		Assert.notNull(metadata, "Metadata must not be null");
		return new Versioned<>(secret, metadata.getVersion(), metadata);
	}


	/**
	 * @return the {@link Version} associated with this {@link Versioned} object.
	 */
	public Version getVersion() {
		return this.version;
	}

	/**
	 * @return {@literal true} if this versioned object has {@link Metadata}
	 * associated, otherwise {@code false}
	 */
	public boolean hasMetadata() {
		return this.metadata != null;
	}

	@Nullable
	public Metadata getMetadata() {
		return this.metadata;
	}

	/**
	 * Return the required {@link Metadata} for this versioned object. Throws
	 * {@link IllegalStateException} if no metadata is associated.
	 * @return the non-null {@link Metadata} held by this for this versioned object.
	 * @throws IllegalStateException if no metadata is present.
	 */
	public Metadata getRequiredMetadata() {
		Metadata metadata = this.metadata;
		if (metadata == null) {
			throw new IllegalStateException("Required Metadata is not present");
		}
		return metadata;
	}

	/**
	 * @return {@literal true} if this versioned object has data associated, or
	 * {@code false}, of the version is deleted or destroyed.
	 */
	public boolean hasData() {
		return this.data != null;
	}

	/**
	 * @return the actual data for this versioned object. Can be {@literal null} if
	 * the version is deleted or destroyed.
	 */
	@Nullable
	public T getData() {
		return this.data;
	}

	/**
	 * Return the required data for this versioned object. Throws
	 * {@link IllegalStateException} if no data is associated.
	 * @return the non-null value held by this for this versioned object.
	 * @throws IllegalStateException if no data is present.
	 */
	public T getRequiredData() {
		T data = this.data;
		if (data == null) {
			throw new IllegalStateException("Required data is not present");
		}
		return data;
	}

	/**
	 * Convert the data element of this versioned object to an {@link Optional}.
	 * @return {@link Optional#of(Object) Optional} holding the actual value of this
	 * versioned object if {@link #hasData() data is present},
	 * {@link Optional#empty()} if no data is associated.
	 */
	public Optional<T> toOptional() {
		return Optional.ofNullable(this.data);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Versioned<?> versioned))
			return false;
		return Objects.equals(this.data, versioned.data) && Objects.equals(this.version, versioned.version)
				&& Objects.equals(this.metadata, versioned.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.data, this.version, this.metadata);
	}


	/**
	 * Value object representing version metadata such as creation/deletion time.
	 */
	public static class Metadata {

		private final Instant createdAt;

		private final @Nullable Map<String, String> customMetadata;

		private final @Nullable Instant deletedAt;

		private final boolean destroyed;

		private final Version version;


		private Metadata(Instant createdAt, @Nullable Map<String, String> customMetadata, @Nullable Instant deletedAt,
				boolean destroyed, Version version) {
			this.createdAt = createdAt;
			this.customMetadata = customMetadata;
			this.deletedAt = deletedAt;
			this.destroyed = destroyed;
			this.version = version;
		}


		/**
		 * Create a new {@link MetadataBuilder} to build {@code Metadata} objects.
		 * @return a new {@link MetadataBuilder} to build {@code Metadata} objects.
		 */
		public static MetadataBuilder builder() {
			return new MetadataBuilder();
		}

		/**
		 * @return custom metadata, if provided.
		 */
		public Map<String, String> getCustomMetadata() {
			return customMetadata == null ? Collections.emptyMap() : customMetadata;
		}

		/**
		 * @return {@link Instant} at which the version was created.
		 */
		public Instant getCreatedAt() {
			return this.createdAt;
		}

		/**
		 * @return {@literal true} if the version was deleted.
		 */
		public boolean isDeleted() {
			return this.deletedAt != null;
		}

		/**
		 * @return {@link Instant} at which the version was deleted. Can be
		 * {@literal null} if the version is not deleted.
		 */
		@Nullable
		public Instant getDeletedAt() {
			return this.deletedAt;
		}

		/**
		 * @return the version number.
		 */
		public Version getVersion() {
			return this.version;
		}

		/**
		 * @return {@literal true} if the version was destroyed.
		 */
		public boolean isDestroyed() {
			return this.destroyed;
		}

		@Override
		public String toString() {
			StringBuilder customMetadataString = new StringBuilder(", customMetadata=[");
			if (customMetadata != null && customMetadata.keySet().size() > 0) {
				for (String key : customMetadata.keySet()) {
					customMetadataString.append(key).append(":").append(customMetadata.get(key)).append(" ");
				}
			}
			customMetadataString.append("]");
			return getClass().getSimpleName() + " [createdAt=" + this.createdAt + ", deletedAt=" + this.deletedAt
					+ ", destroyed=" + this.destroyed + ", version=" + this.version + customMetadataString + ']';
		}

		/**
		 * Builder for {@link Metadata} objects.
		 */
		public static class MetadataBuilder {

			private @Nullable Instant createdAt;

			private @Nullable Instant deletedAt;

			private boolean destroyed;

			private @Nullable Version version;

			private @Nullable Map<String, String> customMetadata;


			private MetadataBuilder() {
			}


			/**
			 * Configure a created at {@link Instant}.
			 * @param createdAt timestamp at which the version was created, must not be
			 * {@literal null}.
			 * @return this builder.
			 */
			public MetadataBuilder createdAt(Instant createdAt) {
				Assert.notNull(createdAt, "Created at must not be null");
				this.createdAt = createdAt;
				return this;
			}

			/**
			 * Configure a deleted at {@link Instant}.
			 * @param deletedAt timestamp at which the version was deleted, must not be
			 * {@literal null}.
			 * @return this builder.
			 */
			public MetadataBuilder deletedAt(Instant deletedAt) {
				this.deletedAt = deletedAt;
				return this;
			}

			/**
			 * Configure the version was destroyed.
			 * @return this builder.
			 */
			public MetadataBuilder destroyed() {
				return destroyed(true);
			}

			/**
			 * Configure the version was destroyed.
			 * @param destroyed
			 * @return this builder.
			 */
			public MetadataBuilder destroyed(boolean destroyed) {
				this.destroyed = destroyed;
				return this;
			}

			/**
			 * Configure the {@link Version}.
			 * @param version must not be {@literal null}.
			 * @return this builder.
			 */
			public MetadataBuilder version(Version version) {
				Assert.notNull(version, "Version must not be null");
				this.version = version;
				return this;
			}

			/**
			 * Configure the custom metadata map.
			 * @param customMetadata must not be {@literal null} and not empty.
			 * @return this builder.
			 * @since 3.1
			 */
			public MetadataBuilder customMetadata(Map<String, String> customMetadata) {
				this.customMetadata = customMetadata != null && !CollectionUtils.isEmpty(customMetadata)
						? new LinkedHashMap<>(customMetadata)
						: null;
				return this;
			}

			/**
			 * Build the {@link Versioned.Metadata} object. Requires
			 * {@link #createdAt(Instant)} and {@link #version(Versioned.Version)} to be
			 * set.
			 * @return the {@link Versioned.Metadata} object.
			 */
			public Metadata build() {
				Assert.notNull(this.createdAt, "CreatedAt must not be null");
				Assert.notNull(this.version, "Version must not be null");
				return new Metadata(this.createdAt, this.customMetadata, this.deletedAt, this.destroyed, this.version);
			}

		}

	}

	/**
	 * Value object representing a Vault version.
	 * <p>Versions greater zero point to a specific secret version whereas version
	 * number zero points to a placeholder whose meaning is tied to a specific
	 * operation. Version number zero can mean first created version, latest
	 * version.
	 *
	 * @author Mark Paluch
	 */
	public static class Version {

		static final Version UNVERSIONED = new Version(0);


		private final int version;


		private Version(int version) {
			this.version = version;
		}

		/**
		 * @return the unversioned {@link Version} as placeholder for specific
		 * operations that require version number zero.
		 */
		public static Version unversioned() {
			return UNVERSIONED;
		}


		/**
		 * Create a {@link Version} given a {@code versionNumber}.
		 * @param versionNumber the version number.
		 * @return the {@link Version} for {@code versionNumber}.
		 */
		public static Version from(int versionNumber) {
			return versionNumber > 0 ? new Version(versionNumber) : UNVERSIONED;
		}

		/**
		 * @return {@literal true} if this {@link Version} points to a valid version
		 * number, {@literal false} otherwise.
		 * <p>Version numbers that are equal zero are placeholders to denote unversioned
		 * or latest versions in the context of particular versioning operations.
		 */
		public boolean isVersioned() {
			return this.version > 0;
		}

		/**
		 * @return the version number.
		 */
		public int getVersion() {
			return this.version;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Version other))
				return false;
			return this.version == other.version;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.version);
		}

		@Override
		public String toString() {
			return "Version[%d]".formatted(this.version);
		}

	}

}
