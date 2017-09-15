package org.springframework.vault.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.util.Base64Utils;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds the response from encryption/decryption operation and provides helper
 * methods to generate list of encryption/decryption objects by fetching the
 * respective fields from VaultResponse.
 * 
 * @author Praveendra Singh
 *
 */
@Getter
@Setter
public class VaultEncryptionDecryptionResultHelper {

	public static List<VaultEncryptionResult> fetchEncryptionResult(VaultResponse vaultResponse) {

		List<VaultEncryptionResult> result = new ArrayList<VaultEncryptionResult>();

		for (Map<String, String> data : getBatchData(vaultResponse)) {

			VaultEncryptionResult res = new VaultEncryptionResult(data.get("ciphertext"), data.get("error"));
			result.add(res);
		}

		return result;
	}

	public static List<VaultDecryptionResult> fetchDecryptionResult(VaultResponse vaultResponse) {

		List<VaultDecryptionResult> result = new ArrayList<VaultDecryptionResult>();

		for (Map<String, String> data : getBatchData(vaultResponse)) {

			VaultDecryptionResult res = new VaultDecryptionResult(Base64Utils.decodeFromString(data.get("plaintext")),
					data.get("error"));
			result.add(res);
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	protected static List<Map<String, String>> getBatchData(VaultResponse vaultResponse) {
		return (List<Map<String, String>>) vaultResponse.getData().get("batch_results");
	}
}
