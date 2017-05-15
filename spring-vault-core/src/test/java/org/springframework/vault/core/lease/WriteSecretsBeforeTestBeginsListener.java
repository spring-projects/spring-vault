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
package org.springframework.vault.core.lease;

import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * An {@link AbstractTestExecutionListener} which will write the sample rotating secret
 * with the initial value we will test against.
 *
 * @author Steven Swor
 */
public class WriteSecretsBeforeTestBeginsListener extends AbstractTestExecutionListener {

	/**
	 * Writes the sample rotating secret before the test begins.
	 *
	 * @param testContext the {@link TestContext}. May not be {@literal null}.
	 * @throws Exception if bad things happen
	 */
	@Override
	public void beforeTestClass(TestContext testContext) throws Exception {

		VaultOperations vaultOperations = testContext.getApplicationContext()
				.getBean(VaultOperations.class);

		Map<String, Object> data = new HashMap<String, Object>();
		data.put("hello", "world");
		data.put("ttl", "5");

		vaultOperations.write("secret/rotating", data);

		VaultResponseSupport secretInfo = vaultOperations.read("secret/rotating");
		assertThat(secretInfo).isNotNull();
		assertThat(secretInfo.getLeaseId()).isNotNull().isEmpty();
		assertThat(secretInfo.getLeaseDuration()).isEqualTo(5);
	}

}
