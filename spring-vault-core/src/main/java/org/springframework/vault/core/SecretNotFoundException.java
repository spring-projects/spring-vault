package org.springframework.vault.core;

import org.springframework.vault.VaultException;

/**
 * An exception which is used in case that no secret is found from Vault server.
 *
 * @author Younghwan Jang
 * @since 2.3
 */
public class SecretNotFoundException extends VaultException {
    /**
     * Create a {@code SecretNotFoundException} with the specified detail message.
     * @param msg the detail message.
     */
    public SecretNotFoundException(String msg) {
        super(msg);
    }

    /**
     * Create a {@code SecretNotFoundException} with the specified detail message and nested
     * exception.
     * @param msg the detail message.
     * @param cause the nested exception.
     */
    public SecretNotFoundException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
