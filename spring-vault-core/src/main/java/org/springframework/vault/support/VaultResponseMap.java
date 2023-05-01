package org.springframework.vault.support;

import java.util.HashMap;
import java.util.Map;

public class VaultResponseMap extends HashMap<String, Object> {

	public Map<String, Object> asMap() {
		return this;
	}

}
