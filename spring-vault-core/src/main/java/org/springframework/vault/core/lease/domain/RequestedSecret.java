/*
 * Copyright 2017-2025 the original author or authors.
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
package org.springframework.vault.core.lease.domain;

import org.springframework.util.Assert;

/**
 * Represents a requested secret from a specific Vault path associated with a lease
 * {@link Mode}.
 * <p>
 * A {@link RequestedSecret} can be renewing or rotating.
 *
 * @author Mark Paluch
 * @author Pierre-Jean Vardanega
 * @see Mode
 * @see Lease#isRenewable()
 */
public class RequestedSecret {

	private final String path;

	private final Mode mode;

	private RequestedSecret(String path, Mode mode) {

		Assert.hasText(path, "Path must not be null or empty");
		Assert.isTrue(!path.startsWith("/"), "Path name must not start with a slash (/)");

		this.path = path;
		this.mode = mode;
	}

	/**
	 * Create a renewable {@link RequestedSecret} at {@code path}. A lease associated with
	 * this secret will be renewed if the lease is qualified for renewal. The lease is no
	 * longer valid after expiry.
	 * @param path must not be {@literal null} or empty, must not start with a slash.
	 * @return the renewable {@link RequestedSecret}.
	 */
	public static RequestedSecret renewable(String path) {
		return new RequestedSecret(path, Mode.RENEW);
	}

	/**
	 * Create a rotating {@link RequestedSecret} at {@code path}. A lease associated with
	 * this secret will be renewed if the lease is qualified for renewal. Once the lease
	 * expires, a new secret with a new lease is obtained.
	 * @param path must not be {@literal null} or empty, must not start with a slash.
	 * @return the rotating {@link RequestedSecret}.
	 */
	public static RequestedSecret rotating(String path) {
		return new RequestedSecret(path, Mode.ROTATE);
	}

	/**
	 * Create a {@link RequestedSecret} given {@link Mode} at {@code path}.
	 * @param mode must not be {@literal null}.
	 * @param path must not be {@literal null} or empty, must not start with a slash.
	 * @see #rotating(String)
	 * @see #renewable(String)
	 * @return the rotating {@link RequestedSecret}.
	 */
	public static RequestedSecret from(Mode mode, String path) {

		Assert.notNull(mode, "Mode must not be null");

		return mode == Mode.ROTATE ? rotating(path) : renewable(path);
	}

	/**
	 * @return the Vault path of the requested secret.
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * @return lease mode.
	 * @see Mode
	 */
	public Mode getMode() {
		return this.mode;
	}

	@Override
	public boolean equals(Object o) {

		if (this == o)
			return true;
		if (!(o instanceof RequestedSecret that))
			return false;

		if (!this.path.equals(that.path))
			return false;
		return this.mode == that.mode;
	}

	@Override
	public int hashCode() {

		int result = this.path.hashCode();
		result = 31 * result + this.mode.hashCode();
		return result;
	}

	@Override
	public String toString() {

		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [path='").append(this.path).append('\'');
		sb.append(", mode=").append(this.mode);
		sb.append(']');
		return sb.toString();
	}

	public enum Mode {

		/**
		 * Renew lease of the requested secret until secret expires its max lease time.
		 */
		RENEW,

		/**
		 * Renew lease of the requested secret. Obtains new secret along a new lease once
		 * the previous lease expires its max lease time.
		 */
		ROTATE;

	}

}
