package org.springframework.vault.support;

import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for EncryptionResult/DecryptionResult classes. Provides common
 * util operations.
 * 
 * @author Praveendra Singh
 *
 */
@Getter
@Setter
@AllArgsConstructor
public abstract class AbstractVaultCryptoResult {
	private VaultResponse vaultResponse;

	/**
	 * Retrieves the first error message from the batch of Data.
	 * 
	 * @return error message
	 */
	protected String getErrorMessage() {

		String detailMessage = null;

		for (Map<String, String> data : getBatchData()) {
			if (!StringUtils.isEmpty(data.get("error"))) {
				// for now just get the first error from the batch.
				detailMessage = data.get("error");
				break;
			}
		}
		return detailMessage;
	}

	/**
	 * checks if operation ended up with any error, then converts it into
	 * VaultException.
	 * 
	 * @return exception object
	 */
	public Throwable getCause() {

		String detailMessage = getErrorMessage();

		if (detailMessage == null) {
			return null;
		}

		return new VaultException(detailMessage);
	}

	/**
	 * status of the batch operation.
	 * 
	 * @return true if success, false if failed.
	 */
	public boolean isSuccess() {
		return getErrorMessage() != null;
	}

	@SuppressWarnings("unchecked")
	protected List<Map<String, String>> getBatchData() {
		return (List<Map<String, String>>) vaultResponse.getData().get("batch_results");
	}
}
