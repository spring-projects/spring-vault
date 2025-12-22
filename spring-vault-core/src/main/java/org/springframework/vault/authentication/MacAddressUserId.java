/*
 * Copyright 2016-2025 the original author or authors.
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
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Mechanism to generate a UserId based on the Mac address.
 * {@link MacAddressUserId} creates a hex-encoded representation of the Mac
 * address without any separators (0123456789AB). A network interface hint can
 * be specified optionally to select a network interface (index/name).
 *
 * @author Mark Paluch
 * @see AppIdUserIdMechanism
 */
public class MacAddressUserId implements AppIdUserIdMechanism {

	private final Log logger = LogFactory.getLog(MacAddressUserId.class);

	private final String networkInterfaceHint;

	/**
	 * Create a new {@link MacAddressUserId} using the {@link NetworkInterface} from
	 * the {@link InetAddress#getLocalHost()}.
	 */
	public MacAddressUserId() {
		this("");
	}

	/**
	 * Create a new {@link MacAddressUserId} using a {@code networkInterfaceIndex}.
	 * The index is applied to {@link NetworkInterface#getNetworkInterfaces()} to
	 * obtain the desired network interface.
	 * @param networkInterfaceIndex must be greater or equal to zero.
	 */
	public MacAddressUserId(int networkInterfaceIndex) {

		Assert.isTrue(networkInterfaceIndex >= 0, "NetworkInterfaceIndex must be greater or equal to 0");

		this.networkInterfaceHint = "" + networkInterfaceIndex;
	}

	/**
	 * Create a new {@link MacAddressUserId} using a {@code networkInterfaceName}.
	 * This name is compared with {@link NetworkInterface#getName()} and
	 * {@link NetworkInterface#getDisplayName()} to obtain the desired network
	 * interface.
	 * @param networkInterfaceName must not be {@literal null}.
	 */
	public MacAddressUserId(String networkInterfaceName) {

		Assert.notNull(networkInterfaceName, "NetworkInterfaceName must not be null");

		this.networkInterfaceHint = networkInterfaceName;
	}

	@Override
	public String createUserId() {

		try {

			Optional<NetworkInterface> networkInterface = Optional.empty();
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

			if (StringUtils.hasText(this.networkInterfaceHint)) {

				try {
					networkInterface = getNetworkInterface(Integer.parseInt(this.networkInterfaceHint), interfaces);
				} catch (NumberFormatException e) {
					networkInterface = getNetworkInterface((this.networkInterfaceHint), interfaces);
				}
			}

			if (!networkInterface.isPresent()) {

				if (StringUtils.hasText(this.networkInterfaceHint)) {
					this.logger
							.warn("Did not find a NetworkInterface applying hint %s"
									.formatted(this.networkInterfaceHint));
				}

				InetAddress localHost = InetAddress.getLocalHost();
				networkInterface = Optional.ofNullable(NetworkInterface.getByInetAddress(localHost));

				if (!networkInterface.filter(MacAddressUserId::hasNetworkAddress).isPresent()) {
					networkInterface = getNetworkInterfaceWithHardwareAddress(interfaces);
				}
			}

			return networkInterface.map(MacAddressUserId::getRequiredNetworkAddress) //
					.map(Sha256::toHexString) //
					.map(Sha256::toSha256) //
					.orElseThrow(() -> new IllegalStateException("Cannot determine NetworkInterface"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

	}

	private static Optional<NetworkInterface> getNetworkInterface(Number hint, List<NetworkInterface> interfaces) {

		if (interfaces.size() > hint.intValue() && hint.intValue() >= 0) {
			return Optional.of(interfaces.get(hint.intValue()));
		}

		return Optional.empty();
	}

	private static Optional<NetworkInterface> getNetworkInterface(String hint, List<NetworkInterface> interfaces) {

		return interfaces.stream() //
				.filter(anInterface -> matchesHint(hint, anInterface)) //
				.findFirst();
	}

	private static boolean matchesHint(String hint, NetworkInterface networkInterface) {

		return hint.equals(networkInterface.getDisplayName()) || hint.equals(networkInterface.getName());
	}

	private static Optional<NetworkInterface> getNetworkInterfaceWithHardwareAddress(
			List<NetworkInterface> interfaces) {

		return interfaces.stream() //
				.filter(MacAddressUserId::hasNetworkAddress) //
				.sorted(Comparator.comparingInt(NetworkInterface::getIndex)) //
				.findFirst();
	}

	private static Optional<byte[]> getNetworkAddress(NetworkInterface it) {

		try {
			return Optional.ofNullable(it.getHardwareAddress());
		} catch (SocketException e) {
			throw new IllegalStateException("Cannot determine hardware address for %s".formatted(it.getName()));
		}
	}

	private static byte[] getRequiredNetworkAddress(NetworkInterface it) {

		return getNetworkAddress(it) //
				.orElseThrow(() -> new IllegalStateException(
						"Network interface %s has no hardware address".formatted(it.getName())));
	}

	private static boolean hasNetworkAddress(NetworkInterface it) {
		return getNetworkAddress(it).isPresent();
	}

}
