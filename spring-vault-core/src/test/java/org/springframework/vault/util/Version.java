/*
 * Copyright 2017-2022 the original author or authors.
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
package org.springframework.vault.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Value object representing Version consisting of major, minor and bugfix part.
 *
 * @author Mark Paluch
 */
public class Version implements Comparable<Version> {

	private static final String VERSION_PARSE_ERROR = "Invalid version string! Could not parse segment %s within %s.";

	final int major;

	final int minor;

	final int bugfix;

	final int build;

	final boolean enterprise;

	/**
	 * Creates a new {@link Version} from the given integer values. At least one value has
	 * to be given but a maximum of 4.
	 * @param parts must not be {@literal null} or empty.
	 */
	private Version(boolean enterprise, int... parts) {

		Assert.notNull(parts, "Parts must not be null");
		Assert.isTrue(parts.length > 0 && parts.length < 5, "Parts must contain one to four segments");

		this.major = parts[0];
		this.minor = parts.length > 1 ? parts[1] : 0;
		this.bugfix = parts.length > 2 ? parts[2] : 0;
		this.build = parts.length > 3 ? parts[3] : 0;
		this.enterprise = enterprise;

		Assert.isTrue(this.major >= 0, "Major version must be greater or equal zero!");
		Assert.isTrue(this.minor >= 0, "Minor version must be greater or equal zero!");
		Assert.isTrue(this.bugfix >= 0, "Bugfix version must be greater or equal zero!");
		Assert.isTrue(this.build >= 0, "Build version must be greater or equal zero!");
	}

	/**
	 * Parses the given string representation of a version into a {@link Version} object.
	 * @param version must not be {@literal null} or empty.
	 * @return
	 */
	public static Version parse(String version) {

		Assert.hasText(version, "Version must not be empty!");

		String[] parts = version.trim().split("\\.");
		int[] intParts = new int[parts.length];
		boolean enterprise = version.endsWith("+ent");

		for (int i = 0; i < parts.length; i++) {

			String input = i == parts.length - 1 ? parts[i].replaceAll("\\D.*", "") : parts[i];

			if (StringUtils.hasText(input)) {
				try {
					intParts[i] = Integer.parseInt(input);
				}
				catch (IllegalArgumentException o_O) {
					throw new IllegalArgumentException(String.format(VERSION_PARSE_ERROR, input, version), o_O);
				}
			}
		}

		return new Version(enterprise, intParts);
	}

	/**
	 * Returns whether the current {@link Version} is greater (newer) than the given one.
	 * @param version
	 * @return
	 */
	public boolean isGreaterThan(Version version) {
		return compareTo(version) > 0;
	}

	/**
	 * Returns whether the current {@link Version} is greater (newer) or the same as the
	 * given one.
	 * @param version
	 * @return
	 */
	public boolean isGreaterThanOrEqualTo(Version version) {
		return compareTo(version) >= 0;
	}

	/**
	 * Returns whether the current {@link Version} is the same as the given one.
	 * @param version
	 * @return
	 */
	public boolean is(Version version) {
		return equals(version);
	}

	/**
	 * Returns whether the current {@link Version} is less (older) than the given one.
	 * @param version
	 * @return
	 */
	public boolean isLessThan(Version version) {
		return compareTo(version) < 0;
	}

	/**
	 * Returns whether the current {@link Version} is less (older) or equal to the current
	 * one.
	 * @param version
	 * @return
	 */
	public boolean isLessThanOrEqualTo(Version version) {
		return compareTo(version) <= 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Version that) {

		if (that == null) {
			return 1;
		}

		if (this.major != that.major) {
			return this.major - that.major;
		}

		if (this.minor != that.minor) {
			return this.minor - that.minor;
		}

		if (this.bugfix != that.bugfix) {
			return this.bugfix - that.bugfix;
		}

		if (this.build != that.build) {
			return this.build - that.build;
		}

		return 0;
	}

	public boolean isEnterprise() {
		return this.enterprise;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		List<Integer> digits = new ArrayList<Integer>();
		digits.add(this.major);
		digits.add(this.minor);

		if (this.build != 0 || this.bugfix != 0) {
			digits.add(this.bugfix);
		}

		if (this.build != 0) {
			digits.add(this.build);
		}

		return StringUtils.collectionToDelimitedString(digits, ".") + (isEnterprise() ? "+ent" : "");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Version))
			return false;
		Version version = (Version) o;
		return this.major == version.major && this.minor == version.minor && this.bugfix == version.bugfix
				&& this.build == version.build && this.enterprise == version.enterprise;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.major, this.minor, this.bugfix, this.build, this.enterprise);
	}

}
