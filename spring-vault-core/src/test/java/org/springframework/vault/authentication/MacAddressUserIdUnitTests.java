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

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.regex.Pattern;

import org.junit.Test;

import org.springframework.util.CollectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * Unit tests for {@link MacAddressUserId}.
 * 
 * @author Mark Paluch
 */
public class MacAddressUserIdUnitTests {

	@Test
	public void shouldGenerateUppercaseSha256HexString() throws Exception {

		String userId = new MacAddressUserId().createUserId();

		assertThat(userId).matches(Pattern.compile("[0-9A-F]+")).doesNotMatch(
				Pattern.compile("[a-f]"));
	}

	@Test
	public void shouldGenerateUserIdFromNetworkInterfaceIndex() throws Exception {

		int index = getValidNetworkInterfaceIndex();
		assumeTrue(index != -1);

		String userId = new MacAddressUserId(index).createUserId();

		assertThat(userId).matches(Pattern.compile("[0-9A-F]+")).doesNotMatch(
				Pattern.compile("[a-f]"));
	}

	/**
	 * Obtain index for {@link NetworkInterface} with a HardwareAddress.
	 * 
	 * @return -1 if none, otherwise index.
	 * @throws SocketException
	 */
	private int getValidNetworkInterfaceIndex() throws SocketException {

		NetworkInterface[] networkInterfaces = CollectionUtils.toArray(
				NetworkInterface.getNetworkInterfaces(), new NetworkInterface[0]);

		int index = -1;

		for (int i = 0; i < networkInterfaces.length; i++) {
			if (networkInterfaces[i].getHardwareAddress() != null) {
				index = i;
				break;
			}
		}
		return index;
	}
}
