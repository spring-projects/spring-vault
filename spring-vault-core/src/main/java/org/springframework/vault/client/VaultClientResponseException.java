package org.springframework.vault.client;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.vault.VaultException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Abstract base class for exceptions thrown by {@link VaultClient} and
 * {@link ReactiveVaultClient} in case a request fails because of a server error
 * response, a failure to decode the response, or a low level I/O error.
 *
 * <p>Server error responses are determined by
 * {@link RestClient.ResponseSpec#onStatus status handlers} for
 * {@code RestClient}, and by {@link ResponseErrorHandler} for
 * {@code RestTemplate}.
 *
 * @author Mark Paluch
 * @since 4.1
 */
public abstract class VaultClientResponseException extends VaultException {

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;


	/**
	 * Create a {@code VaultClientResponseException} with the specified detail
	 * message.
	 * @param msg the detail message.
	 */
	public VaultClientResponseException(String msg) {
		super(msg);
	}

	/**
	 * Create a {@code VaultClientResponseException} with the specified detail
	 * message and nested exception.
	 * @param msg the detail message.
	 * @param cause the nested exception.
	 */
	public VaultClientResponseException(String msg, @Nullable Throwable cause) {
		super(msg, cause);
	}


	/**
	 * Return the HTTP status code.
	 */
	public abstract HttpStatusCode getStatusCode();

	/**
	 * Return the HTTP status text.
	 */
	public abstract String getStatusText();

	/**
	 * Return the HTTP response headers.
	 */
	public @Nullable HttpHeaders getResponseHeaders() {
		return null;
	}

	/**
	 * Return the response body as a byte array.
	 */
	public abstract byte[] getResponseBodyAsByteArray();

	/**
	 * Return the response body converted to String. The charset used is that of the
	 * response "Content-Type" or otherwise {@code "UTF-8"}.
	 */
	public String getResponseBodyAsString() {
		return getResponseBodyAsString(DEFAULT_CHARSET);
	}

	/**
	 * Return the response body converted to String. The charset used is that of the
	 * response "Content-Type" or otherwise the one given.
	 * @param fallbackCharset the charset to use on if the response doesn't specify.
	 */
	public abstract String getResponseBodyAsString(Charset fallbackCharset);

	/**
	 * Convert the error response content to the specified type.
	 * @param targetType the type to convert to.
	 * @param <E> the expected target type.
	 * @return the converted object, or {@code null} if there is no content.
	 */
	public abstract <E> @Nullable E getResponseBodyAs(Class<E> targetType);

	/**
	 * Variant of {@link #getResponseBodyAs(Class)} with
	 * {@link ParameterizedTypeReference}.
	 */
	public abstract <E> @Nullable E getResponseBodyAs(ParameterizedTypeReference<E> targetType);

	@Override
	public String toString() {
		String s = VaultClientResponseException.class.getName();
		String message = getLocalizedMessage();
		return (message != null) ? (s + ": " + message) : s;
	}

}
