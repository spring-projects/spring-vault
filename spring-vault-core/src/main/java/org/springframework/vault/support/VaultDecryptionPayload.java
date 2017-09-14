package org.springframework.vault.support;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Decryption Value Object used for encrypt() operations.
 * 
 * @author Praveendra Singh
 *
 */
@Getter
@Setter
@AllArgsConstructor
public class VaultDecryptionPayload {
	private String ciphertext;
	private VaultTransitContext context;

	/**
	 * factory method helps to create decryption value object using ciphertext
	 * in String
	 * 
	 * @param ciphertext
	 *            to be decrypted
	 * @return decryption value object
	 */
	public static VaultDecryptionPayload of(String ciphertext) {

		if (ciphertext == null) {
			throw new IllegalArgumentException("The ciphertext must not be null");
		}

		return new VaultDecryptionPayload(ciphertext, null);
	}

	/**
	 * sets the decryption context to the value object.
	 * 
	 * @param context
	 *            transit decryption context
	 * @return decryption value object
	 */
	public VaultDecryptionPayload with(VaultTransitContext context) {
		return new VaultDecryptionPayload(this.getCiphertext(), context);
	}
}
