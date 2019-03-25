/*
 * Copyright 2018-2019 the original author or authors.
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

import org.springframework.util.Assert;

/**
 * Value object representing a VM environment consisting of the subscription Id, the
 * resource group name and the VM name.
 *
 * @author Mark Paluch
 * @since 2.1
 * @see AzureMsiAuthentication
 * @see AzureMsiAuthenticationOptions
 * @link <a href=
 * "https://docs.microsoft.com/en-us/azure/virtual-machines/windows/instance-metadata-service"
 * >Azure Instance Metadata service</a>
 */
public class AzureVmEnvironment {

	private final String subscriptionId;

	private final String resourceGroupName;

	private final String vmName;

	/**
	 * Creates a new {@link AzureVmEnvironment}.
	 *
	 * @param subscriptionId must not be {@literal null}.
	 * @param resourceGroupName must not be {@literal null}.
	 * @param vmName must not be {@literal null}.
	 */
	public AzureVmEnvironment(String subscriptionId, String resourceGroupName,
			String vmName) {

		Assert.notNull(subscriptionId, "SubscriptionId must not be null");
		Assert.notNull(resourceGroupName, "Resource group name must not be null");
		Assert.notNull(vmName, "VM name must not be null");

		this.subscriptionId = subscriptionId;
		this.resourceGroupName = resourceGroupName;
		this.vmName = vmName;
	}

	public String getSubscriptionId() {
		return subscriptionId;
	}

	public String getResourceGroupName() {
		return resourceGroupName;
	}

	public String getVmName() {
		return vmName;
	}
}
