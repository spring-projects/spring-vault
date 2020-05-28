/*
 * Copyright 2017-2020 the original author or authors.
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

import java.util.Objects;

import org.springframework.util.Assert;

/**
 * Value object representing a Signature.
 *
 * @author Luander Ribeiro
 * @author Mark Paluch
 * @since 2.0
 */
public class Signature {

	private final String signature;

	private Signature(String signature) {
		this.signature = signature;
	}

	/**
	 * Factory method to create a {@link Signature} from the given {@code signature}.
	 * @param signature the signature, must not be {@literal null} or empty.
	 * @return the {@link Signature} encapsulating {@code signature}.
	 */
	public static Signature of(String signature) {

		Assert.hasText(signature, "Signature must not be null or empty");

		return new Signature(signature);
	}

	public String getSignature() {
		return this.signature;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Signature))
			return false;
		Signature that = (Signature) o;
		return this.signature.equals(that.signature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.signature);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [signature='").append(this.signature).append('\'');
		sb.append(']');
		return sb.toString();
	}

}
