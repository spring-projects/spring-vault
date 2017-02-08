/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.client;

import org.springframework.util.Assert;

/**
 * Encapsulates a typed Vault request body.
 * 
 * <p>
 * Example use:
 * 
 * <pre class="code">
 * VaultRequestBody.just(&quot;{'token': 'example'}&quot;);
 * 
 * VaultRequestBody.empty();
 * </pre>
 *
 * @author Mark Paluch
 */
public abstract class VaultRequestBody<T> {

	/**
	 * Create a new {@link VaultRequestBody} that contains the specified body object.
	 * 
	 * @param body the body object, must not be {@literal null}.
	 * @param <T> type of the body object.
	 * @return the {@link VaultRequestBody} containing the body object.
	 */
	public static <T> VaultRequestBody<T> just(T body) {
		return new ScalarVaultRequestBody<T>(body);
	}

	/**
	 * Create an empty {@link VaultRequestBody} without a body object.
	 * 
	 * @param <T> type of the body object.
	 * @return the empty {@link VaultRequestBody}.
	 */
	public static <T> VaultRequestBody<T> empty() {
		return EmptyVaultRequestBody.instance();
	}

	/**
	 * @return the body object of this {@link VaultRequestBody}.
	 */
	public abstract T getBody();

	/**
	 * Represents a scalar {@link VaultRequestBody} holding a singleton {@code body}
	 * object.
	 */
	static class ScalarVaultRequestBody<T> extends VaultRequestBody<T> {

		private final T body;

		public ScalarVaultRequestBody(T body) {

			Assert.notNull(body, "Body must not be null");

			this.body = body;
		}

		@Override
		public T getBody() {
			return body;
		}
	}

	/**
	 * Represents an empty {@link VaultRequestBody} which returns {@literal null} on
	 * {@link #getBody()}.
	 */
	static class EmptyVaultRequestBody<T> extends VaultRequestBody<T> {

		private final static EmptyVaultRequestBody<Object> INSTANCE = new EmptyVaultRequestBody<Object>();

		/**
		 * Returns a properly parametrized instance of this empty {@link VaultRequestBody}
		 * .
		 *
		 * @param <T> the output type
		 * @return a properly parametrized instance of this empty {@link VaultRequestBody}
		 * .
		 */
		@SuppressWarnings("unchecked")
		public static <T> VaultRequestBody<T> instance() {
			return (VaultRequestBody<T>) INSTANCE;
		}

		@Override
		public T getBody() {
			return null;
		}
	}
}
