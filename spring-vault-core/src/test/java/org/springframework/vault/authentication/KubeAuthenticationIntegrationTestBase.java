/*
 * Copyright 2017 the original author or authors.
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

import static org.springframework.vault.util.Settings.findWorkDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.util.Files;
import org.junit.Before;
import org.springframework.vault.core.RestOperationsCallback;
import org.springframework.vault.util.IntegrationTestSupport;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration test base class for {@link KubeAuthentication} tests.
 *
 * @author Michal Budzyn
 */
public abstract class KubeAuthenticationIntegrationTestBase
		extends IntegrationTestSupport {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static String getMinikubeIpOrDefault(String defaultHost) {
		final File configFile = new File(new File(System.getProperty("user.home")),
				".minikube/machines/minikube/config.json");

		if (configFile.exists()) {
			try {
				String content = Files.contentOf(configFile, StandardCharsets.UTF_8);
				Map map = OBJECT_MAPPER.readValue(content, Map.class);
				Map driver = (Map) map.getOrDefault("Driver", Collections.emptyMap());
				return (String) driver.getOrDefault("IPAddress", defaultHost);
			}
			catch (IOException o) {
				return defaultHost;
			}
		}
		return defaultHost;
	}

	@Before
	public void before() {

		if (!prepare().hasAuth("kubernetes")) {
			prepare().mountAuth("kubernetes");
		}

		prepare().getVaultOperations()
				.doWithSession((RestOperationsCallback<Object>) restOperations -> {
					File workDir = findWorkDir();

					String certificate = Files.contentOf(
							new File(workDir, "minikube/ca.crt"),
							StandardCharsets.US_ASCII);

					String ip = getMinikubeIpOrDefault("192.168.99.100");
					String host = String.format("https://%s:8443", ip);

					Map<String, String> kubeConfig = new HashMap<>();
					kubeConfig.put("kubernetes_ca_cert", certificate);
					kubeConfig.put("kubernetes_host", host);
					restOperations.postForEntity("auth/kubernetes/config", kubeConfig,
							Map.class);

					Map<String, String> roleData = new HashMap<>();
					roleData.put("bound_service_account_names", "default");
					roleData.put("bound_service_account_namespaces", "default");
					roleData.put("policies", "default");
					roleData.put("ttl", "1h");

					return restOperations.postForEntity("auth/kubernetes/role/my-role",
							roleData, Map.class);

				});
	}
}
