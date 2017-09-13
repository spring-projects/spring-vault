package org.springframework.vault.support;

import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Holds the response from decryption operation and provides helper methods to
 * deal with the data.
 * 
 * @author Praveendra Singh
 *
 */
@Getter
@Setter
@AllArgsConstructor
public class VaultDecryptionResult {

	private byte[] cipherText;
	private String error;

	/**
	 * returns the list of plaintext or throws VaultException if error
	 * encountered.
	 * 
	 * @return plaintext
	 */
	public byte[] get() {

		if (!StringUtils.isEmpty(error)) {
			throw new VaultException(error);
		}
		return cipherText;
	}

}
