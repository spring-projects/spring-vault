package org.springframework.vault.support;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Encryption Value Object used for encrypt() operations.
 * 
 * @author Praveendra Singh
 *
 */
@Getter
@Setter
@AllArgsConstructor
public class VaultEncryptionPayload {
	private byte[] plaintext;
	private VaultTransitContext context;

	/**
	 * factory method helps to create encryption value object using plaintext in
	 * bytes
	 * 
	 * @param plaintext
	 *            data to be encrypted
	 * 
	 * @return encryption value object
	 */
	public static VaultEncryptionPayload of(byte[] plaintext) {

		if ((plaintext == null) || (plaintext.length == 0)) {
			throw new IllegalArgumentException("The plaintext must not be null or empty");
		}

		return new VaultEncryptionPayload(plaintext, null);
	}

	/**
	 * factory method helps to create encryption value object using plaintext in
	 * String
	 * 
	 * @param plaintext
	 *            data to be encrypted
	 * 
	 * @return encryption value object
	 */
	public static VaultEncryptionPayload of(String plaintext) {

		if (plaintext == null) {
			throw new IllegalArgumentException("The plaintext must not be null");
		}

		return of(plaintext.getBytes());
	}

	/**
	 * sets the encryption context to the value object.
	 * 
	 * @param context
	 *            transit encryption context
	 * @return encryption value object
	 */
	public VaultEncryptionPayload with(VaultTransitContext context) {
		return new VaultEncryptionPayload(this.getPlaintext(), context);
	}
}
