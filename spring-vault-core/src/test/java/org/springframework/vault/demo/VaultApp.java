/*
 * Copyright 2016-2019 the original author or authors.
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
package org.springframework.vault.demo;

import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * @author Mark Paluch
 */
public class VaultApp {

	public static void main(String[] args) {

		VaultTemplate vaultTemplate = new VaultTemplate(new VaultEndpoint(),
				new TokenAuthentication("00000000-0000-0000-0000-000000000000"));

		Secrets secrets = new Secrets();
		secrets.username = "hello";
		secrets.password = "world";

		vaultTemplate.write("secret/myapp", secrets);

		VaultResponseSupport<Secrets> response = vaultTemplate.read("secret/myapp",
				Secrets.class);
		System.out.println(response.getData().getUsername());

		vaultTemplate.delete("secret/myapp");
	}

	public static class Secrets {

		String username;
		String password;

		public String getUsername() {
			return username;
		}

		public String getPassword() {
			return password;
		}
	}
}
