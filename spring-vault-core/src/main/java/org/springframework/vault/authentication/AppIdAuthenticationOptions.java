/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.vault.authentication;

import org.springframework.util.Assert;

/**
 * Authentication options for {@link AppIdAuthentication}.
 * <p>
 * Authentication options provide the path, appId and a {@link AppIdUserIdMechanism}.
 * {@link AppIdAuthentication} can be constructed using {@link #builder()}. Instances of
 * this class are immutable once constructed.
 *
 * @author Mark Paluch
 * @see AppIdAuthentication
 * @see AppIdUserIdMechanism
 * @see #builder()
 */
public class AppIdAuthenticationOptions {

	public static final String DEFAULT_APPID_AUTHENTICATION_PATH = "app-id";

	/**
	 * Path of the appid authentication backend mount.
	 */
	private final String path;

	/**
	 * The AppId
	 */
	private final String appId;

	/**
	 * {@link AppIdUserIdMechanism} instance to obtain a userId.
	 */
	private final AppIdUserIdMechanism userIdMechanism;

	private AppIdAuthenticationOptions(String path, String appId,
			AppIdUserIdMechanism userIdMechanism) {

		this.path = path;
		this.appId = appId;
		this.userIdMechanism = userIdMechanism;
	}

	/**
	 * @return a new {@link AppIdAuthenticationOptionsBuilder}.
	 */
	public static AppIdAuthenticationOptionsBuilder builder() {
		return new AppIdAuthenticationOptionsBuilder();
	}

	/**
	 * @return the mount path.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the AppId.
	 */
	public String getAppId() {
		return appId;
	}

	/**
	 * @return the {@link AppIdUserIdMechanism}.
	 */
	public AppIdUserIdMechanism getUserIdMechanism() {
		return userIdMechanism;
	}

	/**
	 * Builder for {@link AppIdAuthenticationOptions}.
	 */
	public static class AppIdAuthenticationOptionsBuilder {

		private String path = DEFAULT_APPID_AUTHENTICATION_PATH;

		private String appId;

		private AppIdUserIdMechanism userIdMechanism;

		AppIdAuthenticationOptionsBuilder() {
		}

		/**
		 * Configure the mount path.
		 *
		 * @param path must not be empty or {@literal null}.
		 * @return {@code this} {@link AppIdAuthenticationOptionsBuilder}.
		 * @see #DEFAULT_APPID_AUTHENTICATION_PATH
		 */
		public AppIdAuthenticationOptionsBuilder path(String path) {

			Assert.hasText(path, "Path must not be empty");

			this.path = path;
			return this;
		}

		/**
		 * Configure the AppId.
		 *
		 * @param appId must not be empty or {@literal null}.
		 * @return {@code this} {@link AppIdAuthenticationOptionsBuilder}.
		 */
		public AppIdAuthenticationOptionsBuilder appId(String appId) {

			Assert.hasText(appId, "AppId must not be empty");

			this.appId = appId;
			return this;
		}

		/**
		 * Configure the {@link AppIdUserIdMechanism}.
		 *
		 * @param userIdMechanism must not be {@literal null}.
		 * @return {@code this} {@link AppIdAuthenticationOptionsBuilder}.
		 */
		public AppIdAuthenticationOptionsBuilder userIdMechanism(
				AppIdUserIdMechanism userIdMechanism) {

			Assert.notNull(userIdMechanism, "AppIdUserIdMechanism must not be null");

			this.userIdMechanism = userIdMechanism;
			return this;
		}

		/**
		 * Build a new {@link AppIdAuthenticationOptions} instance. Requires
		 * {@link #userIdMechanism(AppIdUserIdMechanism)} to be configured.
		 *
		 * @return a new {@link AppIdAuthenticationOptions}.
		 */
		public AppIdAuthenticationOptions build() {

			Assert.hasText(appId, "AppId must not be empty");
			Assert.notNull(userIdMechanism, "AppIdUserIdMechanism must not be null");

			return new AppIdAuthenticationOptions(path, appId, userIdMechanism);
		}
	}
}
