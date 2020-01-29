package org.springframework.vault.authentication;

/**
 * Utility class to have all the common path templates together.
 * @author alapatsin
 *
 */
abstract class AuthenticationUtil {
	
	static String getAuthRolePath(String path) {
		return String.format("auth/%s/role/", path);
	}
	
	static String getLoginPath(String path) {
		return String.format("auth/%s/login", path);
	} 
}
