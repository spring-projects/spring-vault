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

import org.springframework.vault.client.VaultException;
import org.springframework.vault.support.VaultHealthResponse;
import org.springframework.vault.support.VaultInitializationRequest;
import org.springframework.vault.support.VaultInitializationResponse;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultUnsealStatus;

/**
 * Interface that specified a basic set of Vault operations, implemented by {@link VaultTemplate}. Request errors are
 * wrapped within {@link VaultException}.
 * 
 * @author Mark Paluch
 */
public interface VaultSysOperations {

	/**
	 * @return {@literal true} if Vault is initialized.
	 */
	boolean isInitialized() throws VaultException;

	/**
	 * Initializes Vault with a {@link VaultInitializationRequest}.
	 * 
	 * @param vaultInitializationRequest must not be {@literal null}.
	 * @return the {@link VaultInitializationResponse}.
	 */
	VaultInitializationResponse initialize(VaultInitializationRequest vaultInitializationRequest) throws VaultException;

	/**
	 * Seals vault.
	 */
	void seal() throws VaultException;

	/**
	 * Unseal vault. See {@link VaultUnsealStatus#getProgress()} for progress.
	 * 
	 * @param keyShare must not be empty and not {@literal null}.
	 * @return the {@link VaultUnsealStatus}.
	 */
	VaultUnsealStatus unseal(String keyShare) throws VaultException;

	/**
	 * @return the {@link VaultUnsealStatus}.
	 */
	VaultUnsealStatus getUnsealStatus() throws VaultException;

	/**
	 * Mounts a secret backend {@link VaultMount} to {@code path}.
	 * 
	 * @param path must not be empty or {@literal null}.
	 * @param vaultMount must not be {@literal null}.
	 */
	void mount(String path, VaultMount vaultMount) throws VaultException;

	/**
	 * @return {@link Map} of all secret backend {@link VaultMount mounts}.
	 */
	Map<String, VaultMount> getMounts() throws VaultException;

	/**
	 * Unmounts the secret backend mount at {@code path}.
	 * 
	 * @param path must not be empty or {@literal null}.
	 */
	void unmount(String path) throws VaultException;

	/**
	 * Mounts an auth backend {@link VaultMount} to {@code path}.
	 *
	 * @param path must not be empty or {@literal null}.
	 * @param vaultMount must not be {@literal null}.
	 */
	void authMount(String path, VaultMount vaultMount) throws VaultException;

	/**
	 * @return {@link Map} of all auth backend {@link VaultMount mounts}.
	 */
	Map<String, VaultMount> getAuthMounts() throws VaultException;

	/**
	 * Unmounts the auth backend mount at {@code path}.
	 *
	 * @param path must not be empty or {@literal null}.
	 */
	void authUnmount(String path) throws VaultException;

	/**
	 * @return the {@link VaultHealthResponse}.
	 */
	VaultHealthResponse health() throws VaultException;
}
