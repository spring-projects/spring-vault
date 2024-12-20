package org.springframework.vault.authentication;

/**
 * Utility class to have all the common path templates together.
 *
 * @author Oleg Lopatin
 */
abstract class AuthenticationUtil {

	/**
	 * Returns the login path for a {@code authMount}.
	 * @param authMount
	 * @return
	 */
	static String getLoginPath(String authMount) {
		return "/auth/%s/login".formatted(authMount);
	}

	private AuthenticationUtil() {
	}

}
