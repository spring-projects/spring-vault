package org.springframework.vault.authentication;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.http.ResponseEntity;
import org.springframework.vault.support.VaultResponseSupport;

/**
 * Utility class providing common authentication-related methods.
 *
 * @author Oleg Lopatin
 * @author Mark Paluch
 */
abstract class AuthenticationUtil {

	/**
	 * Return the login path for a {@code authMount}.
	 * @param authMount
	 * @return
	 */
	static String getLoginPath(String authMount) {
		return "auth/%s/login".formatted(authMount);
	}

	public static <T> T getRequiredBody(ResponseEntity<T> entity) {
		T body = entity.getBody();
		if (body == null) {
			throw new IllegalStateException("Expected non-null response body");
		}

		return body;
	}

	public static <T, R extends VaultResponseSupport<T>> T getRequiredData(ResponseEntity<R> entity) {
		return getRequiredBody(entity).getRequiredData();
	}

	public static <T, R extends VaultResponseSupport<T>> T getRequiredData(@Nullable R response) {

		if (response == null) {
			throw new IllegalStateException("Expected non-null response body");
		}

		return response.getRequiredData();
	}

	public static Object getRequiredValue(ResponseEntity<? extends VaultResponseSupport<Map<String, Object>>> response,
			String key) {
		return getRequiredValue(getRequiredBody(response), key);
	}

	public static Object getRequiredValue(@Nullable VaultResponseSupport<Map<String, Object>> response, String key) {

		if (response == null) {
			throw new IllegalStateException("Expected non-null response body");
		}

		Object value = response.getRequiredData().get(key);

		if (value == null) {
			throw new IllegalStateException(String.format("Key '%s' not found in response", key));
		}

		return value;
	}

	private AuthenticationUtil() {
	}

}
