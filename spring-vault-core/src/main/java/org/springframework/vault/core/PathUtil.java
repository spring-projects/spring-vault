package org.springframework.vault.core;

import org.springframework.util.Assert;

/**
 * Utility methods to normalize paths.
 * @author Mark Paluch
 */
class PathUtil {

	static String normalizeListPath(String path) {
		Assert.notNull(path, "Path must not be null");
		return path.equals("/") ? "" : path.endsWith("/") ? path : path + "/";
	}

	static String stripSlashes(String path) {
		Assert.notNull(path, "Path must not be null");
		if (path.startsWith("/")) {
			path = path.substring(1);
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return path.equals("/") ? "" : path;
	}

}
