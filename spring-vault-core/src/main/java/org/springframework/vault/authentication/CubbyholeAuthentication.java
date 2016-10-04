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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;

/**
 * Cubbyhole {@link ClientAuthentication} implementation.
 * <p>
 * Cubbyhole authentication uses Vault primitives to provide a secured authentication workflow. Cubbyhole authentication
 * uses {@link VaultToken tokens} as primary login method. An ephemeral token is used to obtain a second, login
 * {@link VaultToken} from Vault's Cubbyhole secret backend. The login token is usually longer-lived and used to
 * interact with Vault. The login token can be retrieved either from a wrapped response or from the {@code data}
 * section.
 * <h2>Wrapped token response usage</h2> <strong>Create a Token</strong>
 * 
 * <pre>
 * <code>
 $ vault token-create -wrap-ttl="10m"
 Key                          	Value
 ---                          	-----
 wrapping_token:              	397ccb93-ff6c-b17b-9389-380b01ca2645
 wrapping_token_ttl:          	0h10m0s
 wrapping_token_creation_time:	2016-09-18 20:29:48.652957077 +0200 CEST
 wrapped_accessor:            	46b6aebb-187f-932a-26d7-4f3d86a68319
 * </code>
 * </pre>
 *
 * <strong>Setup {@link CubbyholeAuthentication}</strong>
 * 
 * <pre>
 * <code>
 CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions
		.builder()
		.initialToken(VaultToken.of("397ccb93-ff6c-b17b-9389-380b01ca2645"))
		.wrapped()
 		.build();
 CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, vaultClient);
 * </code>
 * </pre>
 *
 * <h2>Stored token response usage</h2> <strong>Create a Token</strong>
 *
 * <pre>
 * <code>
 $ vault token-create
 Key            	Value
 ---            	-----
 token          	f9e30681-d46a-cdaf-aaa0-2ae0a9ad0819
 token_accessor 	4eee9bd9-81bb-06d6-af01-723c54a72148
 token_duration 	0s
 token_renewable	false
 token_policies 	[root]

 $ token-create -use-limit=2 -orphan -no-default-policy -policy=none
 Key            	Value
 ---            	-----
 token          	895cb88b-aef4-0e33-ba65-d50007290780
 token_accessor 	e84b661c-8aa8-2286-b788-f258f30c8325
 token_duration 	0s
 token_renewable	false
 token_policies 	[none]

 $ export VAULT_TOKEN=895cb88b-aef4-0e33-ba65-d50007290780
 $ vault write cubbyhole/token token=f9e30681-d46a-cdaf-aaa0-2ae0a9ad0819
 * </code>
 * </pre>
 *
 * <strong>Setup {@link CubbyholeAuthentication}</strong>
 *
 * <pre>
 * <code>
 CubbyholeAuthenticationOptions options = CubbyholeAuthenticationOptions
		.builder()
		.initialToken(VaultToken.of("895cb88b-aef4-0e33-ba65-d50007290780"))
		.path("cubbyhole/token")
		.build();
 CubbyholeAuthentication authentication = new CubbyholeAuthentication(options, vaultClient);
 * </code>
 * </pre>
 *
 * @author Mark Paluch
 * @see CubbyholeAuthenticationOptions
 * @see <a href="https://www.vaultproject.io/docs/auth/token.html">Auth Backend: Token</a>
 * @see <a href="https://www.vaultproject.io/docs/secrets/cubbyhole/index.html">Cubbyhole Secret Backend</a>
 * @see <a href="https://www.vaultproject.io/docs/concepts/response-wrapping.html">Response Wrapping</a>
 */
public class CubbyholeAuthentication implements ClientAuthentication {

	private final static Logger logger = LoggerFactory.getLogger(CubbyholeAuthentication.class);

	private final CubbyholeAuthenticationOptions options;

	private final VaultClient vaultClient;

	/**
	 * Create a new {@link CubbyholeAuthentication} given {@link CubbyholeAuthenticationOptions} and {@link VaultClient}.
	 * 
	 * @param options must not be {@literal null}.
	 * @param vaultClient must not be {@literal null}.
	 */
	public CubbyholeAuthentication(CubbyholeAuthenticationOptions options, VaultClient vaultClient) {

		Assert.notNull(options, "CubbyholeAuthenticationOptions must not be null");
		Assert.notNull(vaultClient, "VaultClient must not be null");

		this.options = options;
		this.vaultClient = vaultClient;
	}

	@Override
	public VaultToken login() throws VaultException {

		VaultResponseEntity<VaultResponse> entity = vaultClient.getForEntity(options.getPath(), options.getInitialToken(),
				VaultResponse.class);

		if (entity.isSuccessful() && entity.hasBody()) {

			VaultResponse body = entity.getBody();
			Map<String, Object> data = body.getData();

			VaultToken token = getToken(entity, data);
			if (token != null) {

				logger.debug("Login successful using Cubbyhole authentication");
				return token;
			}
		}

		throw new VaultException(
				String.format("Cannot retrieve Token from cubbyhole: %s %s", entity.getStatusCode(), entity.getMessage()));
	}

	private VaultToken getToken(VaultResponseEntity<VaultResponse> entity, Map<String, Object> data) {

		if (options.isWrappedToken()) {

			VaultResponse response = vaultClient.unwrap((String) data.get("response"), VaultResponse.class);
			return LoginTokenUtil.from(response.getAuth());
		}

		if (data == null || data.isEmpty()) {
			throw new VaultException(String
					.format("Cannot retrieve Token from cubbyhole: Response at %s does not contain a token", entity.getUri()));
		}

		if (data.size() == 1) {
			String token = (String) data.get(data.keySet().iterator().next());
			return VaultToken.of(token);
		}

		throw new VaultException(String.format(
				"Cannot retrieve Token from cubbyhole: Response at %s does not contain an unique token", entity.getUri()));
	}
}
