package org.springframework.vault.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.util.Base64Utils;
import org.springframework.vault.VaultException;

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
public class VaultDecryptionResult extends AbstractVaultCryptoResult {

	public VaultDecryptionResult(VaultResponse vaultResponse) {
		super(vaultResponse);
	}

	public static VaultDecryptionResult of(VaultResponse vaultResponse) {
		return new VaultDecryptionResult(vaultResponse);
	}

	/**
	 * returns the list of plaintexts or throws VaultException with the first
	 * error encountered.
	 * 
	 * @return plaintexts
	 */
	public List<byte[]> get() {

		String detailMessage = getErrorMessage();

		if (detailMessage != null) {

			throw new VaultException(detailMessage);
		}

		return fetchFieldInBytes("plaintext");
	}

	/**
	 * fetches decoded values for a given field from the batch data elements
	 * received from the VaultResponse.
	 * 
	 * @param field
	 *            needed to be fetched
	 * @return
	 */
	private List<byte[]> fetchFieldInBytes(String field) {

		List<byte[]> fields = new ArrayList<byte[]>();

		for (Map<String, String> data : getBatchData()) {
			fields.add(Base64Utils.decodeFromString(data.get(field)));
		}

		return fields;
	}

}
