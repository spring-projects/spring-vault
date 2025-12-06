/*
 * Copyright 2020-2025 the original author or authors.
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

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Transform backend encode/decode context object.
 *
 * @author Lauren Voswinkel
 * @author Roopesh Chandran
 * @since 2.3
 */
public class VaultTransformContext {

	/**
	 * Empty (default) {@link VaultTransformContext} without a {@literal context}
	 * and {@literal nonce}.
	 */
	private static final VaultTransformContext EMPTY = new VaultTransformContext("", new byte[0], null);


	private final String transformation;

	private final byte[] tweak;

	private final @Nullable String reference;


	private VaultTransformContext(String transformation, byte[] tweak, @Nullable String reference) {
		this.transformation = transformation;
		this.tweak = tweak;
		this.reference = reference;
	}


	/**
	 * @return a new {@link VaultTransformRequestBuilder}.
	 */
	public static VaultTransformRequestBuilder builder() {
		return new VaultTransformRequestBuilder();
	}

	/**
	 * @return an empty {@link VaultTransformContext}.
	 */
	public static VaultTransformContext empty() {
		return EMPTY;
	}

	/**
	 * Create a {@link VaultTransformContext} given {@code transformation} bytes.
	 * @param transformation name as a byte array, must not be {@literal null}.
	 * @return a {@link VaultTransformContext} for {@code transformation}.
	 */
	public static VaultTransformContext fromTransformation(String transformation) {
		return builder().transformation(transformation).build();
	}

	/**
	 * Create a {@link VaultTransformContext} given {@code tweak} String.
	 * @param tweak bytes, must be 7 characters long, must not be {@literal null}.
	 * @return a {@link VaultTransformContext} for {@code tweak}.
	 */
	public static VaultTransformContext fromTweak(byte[] tweak) {
		return builder().tweak(tweak).build();
	}


	/**
	 * Return whether this object is an empty one. That is, transformation and tweak
	 * are both empty.
	 * @return {@code true} if this object is empty.
	 */
	public boolean isEmpty() {
		return ObjectUtils.isEmpty(this.transformation) && ObjectUtils.isEmpty(this.tweak)
				&& ObjectUtils.isEmpty(this.reference);
	}

	/**
	 * @return the transformation name.
	 */
	public String getTransformation() {
		return this.transformation;
	}

	/**
	 * @return the tweak
	 */
	public byte[] getTweak() {
		return this.tweak;
	}

	/**
	 * @return the reference identifier for batch operations.
	 * @since 3.2
	 */
	public @Nullable String getReference() {
		return this.reference;
	}

	/**
	 * Return a builder to create a new {@code VaultTransformContext} whose settings
	 * are replicated from the current {@code VaultTransformContext}.
	 */
	public VaultTransformRequestBuilder mutate() {
		return new VaultTransformRequestBuilder(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof VaultTransformContext that))
			return false;
		return this.transformation.equals(that.transformation) && Arrays.equals(this.tweak, that.tweak)
				&& ObjectUtils.nullSafeEquals(this.reference, that.reference);
	}

	@Override
	public int hashCode() {
		int result = this.transformation.hashCode();
		result = 31 * result + Arrays.hashCode(this.tweak);
		result = 31 * result + ObjectUtils.nullSafeHash(this.reference);
		return result;
	}


	/**
	 * Builder for {@link VaultTransformContext}.
	 */
	public static class VaultTransformRequestBuilder {

		private String transformation = "";

		private byte[] tweak = new byte[0];

		/**
		 * A user-defined identifier that can be used to correlate items in a batch
		 * request with their corresponding results in Vault's {@code batch_results}.
		 * <p>If set, Vault echoes this value in the response so clients can match
		 * inputs to outputs reliably. If Vault does not return the {@code reference},
		 * the original client-supplied reference remains available for correlation.
		 */
		private @Nullable String reference;


		private VaultTransformRequestBuilder() {
		}

		private VaultTransformRequestBuilder(VaultTransformContext context) {
			this.transformation = context.transformation;
			this.tweak = Arrays.copyOf(context.tweak, context.tweak.length);
			this.reference = context.reference;
		}


		/**
		 * Configure a transformation to be used with the {@code transform} operation.
		 * @param transformation name, provided as a String.
		 * @return this builder.
		 */
		public VaultTransformRequestBuilder transformation(String transformation) {
			Assert.notNull(transformation, "Transformation must not be null");
			this.transformation = transformation;
			return this;
		}

		/**
		 * Configure the tweak value for a {@code transform} operation. Must be provided
		 * during decoding for all transformations with a tweak_source of "supplied" or
		 * "generated". Must be provided during encoding for all transformations with a
		 * tweak_source of "supplied".
		 * @param tweak value must be exactly 56 bits (7 bytes), value must be supplied
		 * during any subsequent decoding after an encoding. Failure to do so will
		 * result in a decode that does not return the original value.
		 * @return this builder.
		 */
		public VaultTransformRequestBuilder tweak(byte[] tweak) {
			Assert.notNull(tweak, "Tweak must not be null");
			this.tweak = tweak;
			return this;
		}

		/**
		 * Set a user-defined reference identifier. This reference is placed into each
		 * item of a batch request and, if supported by Vault, echoed in the batch
		 * results.
		 * @param reference the correlation identifier; can be or empty.
		 * @return this builder..
		 * @since 3.2
		 */
		public VaultTransformRequestBuilder reference(String reference) {
			this.reference = reference;
			return this;
		}

		/**
		 * Build a new {@link VaultTransformContext} instance.
		 * @return a new {@link VaultTransformContext}.
		 */
		public VaultTransformContext build() {
			return new VaultTransformContext(this.transformation, this.tweak, this.reference);
		}

	}

}
