package org.springframework.vault.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.vault.VaultException;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds the response from encryption operation and provides helper methods to
 * deal with the data.
 * 
 * @author Praveendra Singh
 *
 */
@Getter
@Setter
public class VaultEncryptionResult extends AbstractVaultCryptoResult {

	public VaultEncryptionResult(VaultResponse vaultResponse) {
		super(vaultResponse);
	}

	public static VaultEncryptionResult of(VaultResponse vaultResponse) {
		return new VaultEncryptionResult(vaultResponse);
	}

	/**
	 * returns the list of ciphertexts or throws VaultException with the first
	 * error encountered.
	 * 
	 * @return ciphertexts
	 */
	public List<String> get() {

		String detailMessage = getErrorMessage();

		if (detailMessage != null) {

			throw new VaultException(detailMessage);
		}

		return fetchField("ciphertext");
	}

	/**
	 * fetches values for a given field from the batch data elements received
	 * from the VaultResponse.
	 * 
	 * @param field
	 *            needed to be fetched
	 * @return
	 */
	private List<String> fetchField(String field) {

		List<String> fields = new ArrayList<String>();

		for (Map<String, String> data : getBatchData()) {
			fields.add(data.get(field));
		}

		return fields;
	}

}
