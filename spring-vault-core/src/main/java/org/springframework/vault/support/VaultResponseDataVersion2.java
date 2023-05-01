package org.springframework.vault.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import org.springframework.lang.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultResponseDataVersion2<T> {

	@Nullable
	private T data;

	@Nullable
	private Map<String, Object> metadata;

	@Nullable
	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

	@Nullable
	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

}
