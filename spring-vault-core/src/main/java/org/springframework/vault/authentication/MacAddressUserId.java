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

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Mechanism to generate a UserId based on the Mac address. {@link MacAddressUserId}
 * creates a hex-encoded representation of the Mac address without any separators
 * (0123456789AB). A network interface hint can be specified optionally to select a
 * network interface (index/name).
 *
 * @author Mark Paluch
 * @see AppIdUserIdMechanism
 */
public class MacAddressUserId implements AppIdUserIdMechanism {

	private final Log log = LogFactory.getLog(MacAddressUserId.class);

	private final String networkInterfaceHint;

	/**
	 * Creates a new {@link MacAddressUserId} using the {@link NetworkInterface} from the
	 * {@link InetAddress#getLocalHost()}.
	 */
	public MacAddressUserId() {
		this("");
	}

	/**
	 * Creates a new {@link MacAddressUserId} using a {@code networkInterfaceIndex}. The
	 * index is applied to {@link NetworkInterface#getNetworkInterfaces()} to obtain the
	 * desired network interface.
	 * 
	 * @param networkInterfaceIndex must be greater or equal to zero.
	 */
	public MacAddressUserId(int networkInterfaceIndex) {

		Assert.isTrue(networkInterfaceIndex >= 0,
				"NetworkInterfaceIndex must be greater or equal to 0");

		this.networkInterfaceHint = "" + networkInterfaceIndex;
	}

	/**
	 * Creates a new {@link MacAddressUserId} using a {@code networkInterfaceName}. This
	 * name is compared with {@link NetworkInterface#getName()} and
	 * {@link NetworkInterface#getDisplayName()} to obtain the desired network interface.
	 *
	 * @param networkInterfaceName must not be {@literal null}.
	 */
	public MacAddressUserId(String networkInterfaceName) {

		Assert.notNull(networkInterfaceName, "NetworkInterfaceName must not be null");

		this.networkInterfaceHint = networkInterfaceName;
	}

	@Override
	public String createUserId() {

		try {

			NetworkInterface networkInterface = null;
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface
					.getNetworkInterfaces());

			if (StringUtils.hasText(networkInterfaceHint)) {

				try {
					networkInterface = getNetworkInterface(
							Integer.parseInt(networkInterfaceHint), interfaces);
				}
				catch (NumberFormatException e) {
					networkInterface = getNetworkInterface((networkInterfaceHint),
							interfaces);
				}
			}

			if (networkInterface == null) {

				if (StringUtils.hasText(networkInterfaceHint)) {
					log.warn(String.format(
							"Did not find a NetworkInterface applying hint %s",
							networkInterfaceHint));
				}

				InetAddress localHost = InetAddress.getLocalHost();
				networkInterface = NetworkInterface.getByInetAddress(localHost);

				if (networkInterface == null) {
					throw new IllegalStateException(String.format(
							"Cannot determine NetworkInterface for %s", localHost));
				}
			}

			byte[] mac = networkInterface.getHardwareAddress();
			if (mac == null) {
				throw new IllegalStateException(String.format(
						"Network interface %s has no hardware address",
						networkInterface.getName()));
			}

			return Sha256.toSha256(Sha256.toHexString(mac));
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private NetworkInterface getNetworkInterface(Number hint,
			List<NetworkInterface> interfaces) {

		if (interfaces.size() > hint.intValue() && hint.intValue() >= 0) {
			return interfaces.get(hint.intValue());
		}

		return null;
	}

	private NetworkInterface getNetworkInterface(String hint,
			List<NetworkInterface> interfaces) {

		for (NetworkInterface anInterface : interfaces) {
			if (hint.equals(anInterface.getDisplayName())
					|| hint.equals(anInterface.getName())) {
				return anInterface;
			}
		}

		return null;
	}
}
