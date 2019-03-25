/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.vault.authentication;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Mechanism to generate a SHA-256 hashed and hex-encoded representation of the IP
 * address. Can be calculated with {@code echo -n 192.168.99.1 | sha256sum}.
 *
 * @author Mark Paluch
 * @see AppIdUserIdMechanism
 */
public class IpAddressUserId implements AppIdUserIdMechanism {

	@Override
	public String createUserId() {
		try {
			return Sha256.toSha256(InetAddress.getLocalHost().getHostAddress());
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
