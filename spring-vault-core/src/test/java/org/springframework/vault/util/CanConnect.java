/*
 * Copyright 2016-2020 the original author or authors.
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

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Helper to check whether a TCP connection can be established.
 *
 * @author Mark Paluch
 */
public class CanConnect {

	/**
	 * Performs a check whether a connection can be established to the
	 * {@code socketAddress}.
	 *
	 * @param socketAddress the address to test, must not be {@literal null}.
	 * @return {@literal true}, if a connection can be established
	 */
	public static boolean to(SocketAddress socketAddress) {

		Socket socket = new Socket();
		try {

			socket.connect(socketAddress, (int) TimeUnit.SECONDS.toMillis(1));
			return true;
		}
		catch (IOException e) {
			return false;
		}
		finally {
			try {
				socket.close();
			}
			catch (IOException o_O) {
			}
		}
	}
}
