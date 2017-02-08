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
package org.springframework.vault.core;

import java.util.Map;

import org.springframework.vault.VaultException;
import org.springframework.vault.support.VaultHealth;
import org.springframework.vault.support.VaultInitializationRequest;
import org.springframework.vault.support.VaultInitializationResponse;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultUnsealStatus;

/**
 * Interface that specifies a basic set of administrative Vault operations.
 * 
 * @author Mark Paluch
 */
public interface VaultSysOperations {

	/**
	 * @return {@literal true} if Vault is initialized.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-init.html">GET
	 * /sys/init</a>
	 */
	boolean isInitialized() throws VaultException;

	/**
	 * Initialize Vault with a {@link VaultInitializationRequest}.
	 * 
	 * @param vaultInitializationRequest must not be {@literal null}.
	 * @return the {@link VaultInitializationResponse}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-init.html">PUT
	 * /sys/init</a>
	 */
	VaultInitializationResponse initialize(
			VaultInitializationRequest vaultInitializationRequest) throws VaultException;

	/**
	 * Seal vault.
	 * 
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-seal.html">PUT
	 * /sys/seal</a>
	 */
	void seal() throws VaultException;

	/**
	 * Unseal vault. See {@link VaultUnsealStatus#getProgress()} for progress.
	 * 
	 * @param keyShare must not be empty and not {@literal null}.
	 * @return the {@link VaultUnsealStatus}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-unseal.html">PUT
	 * /sys/unseal</a>
	 */
	VaultUnsealStatus unseal(String keyShare) throws VaultException;

	/**
	 * @return the {@link VaultUnsealStatus}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-unseal.html">GET
	 * /sys/unseal</a>
	 */
	VaultUnsealStatus getUnsealStatus() throws VaultException;

	/**
	 * Mounts a secret backend {@link VaultMount} to {@code path}.
	 * 
	 * @param path must not be empty or {@literal null}.
	 * @param vaultMount must not be {@literal null}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-mounts.html">POST
	 * /sys/mounts/{mount}</a>
	 */
	void mount(String path, VaultMount vaultMount) throws VaultException;

	/**
	 * @return {@link Map} of all secret backend {@link VaultMount mounts}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-mounts.html">GET
	 * /sys/mounts/</a>
	 */
	Map<String, VaultMount> getMounts() throws VaultException;

	/**
	 * Unmounts the secret backend mount at {@code path}.
	 * 
	 * @param path must not be empty or {@literal null}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-mounts.html">DELETE
	 * /sys/mounts/{mount}</a>
	 */
	void unmount(String path) throws VaultException;

	/**
	 * Mounts an auth backend {@link VaultMount} to {@code path}.
	 *
	 * @param path must not be empty or {@literal null}.
	 * @param vaultMount must not be {@literal null}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-auth.html">POST
	 * /sys/auth/{mount}</a>
	 */
	void authMount(String path, VaultMount vaultMount) throws VaultException;

	/**
	 * @return {@link Map} of all auth backend {@link VaultMount mounts}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-auth.html">GET
	 * /sys/auth/</a>
	 */
	Map<String, VaultMount> getAuthMounts() throws VaultException;

	/**
	 * Unmounts the auth backend mount at {@code path}.
	 *
	 * @param path must not be empty or {@literal null}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-auth.html">DELETE
	 * /sys/auth/{mount}</a>
	 */
	void authUnmount(String path) throws VaultException;

	/**
	 * Returns the health status of Vault.
	 *
	 * @return the {@link VaultHealth}.
	 * @see <a href="https://www.vaultproject.io/docs/http/sys-health.html">GET
	 * /sys/health</a>
	 */
	VaultHealth health() throws VaultException;
}
