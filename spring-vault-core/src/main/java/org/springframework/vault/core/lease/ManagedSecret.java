/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.vault.core.lease;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseListenerAdapter;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;

/**
 * Value object to simplify management of a secret obtained from Vault using
 * functional callbacks. A managed {@link RequestedSecret secret} registers with
 * {@link SecretsRegistry} and subscribes to lease events, typically used for
 * secrets that can be rotated and propagation to a consumer.
 *
 * <p>The {@code SecretsAccessor} interface provides typed access to secrets and
 * extension points to simplify access to well-known secret structures such as
 * username/password pairs, for example:
 *
 * <pre class="code">
 * ManagedSecret managed = ManagedSecret.rotating("databases/creds/mysql", secrets -> {
 *   secrets.as(UsernamePassword::from).applyTo((username, password) -> {
 *     connectionPool.setUsername(username);
 *     connectionPool.setPassword(password);
 *   });
 * });
 * </pre>
 *
 * <p>A {@code ManagedSecret} object is activated through
 * {@link #registerSecret(SecretsRegistry) registration} with a running
 * {@link SecretsRegistry} and can be subject to container lifecycle management.
 *
 * @author Mark Paluch
 * @since 4.1
 * @see RequestedSecret
 * @see SecretAccessor
 * @see UsernamePassword
 */
public class ManagedSecret implements SecretRegistrar {

	private static final Log logger = LogFactory.getLog(ManagedSecret.class);


	private final RequestedSecret secret;

	private final LeaseListenerAdapter adapter;


	private ManagedSecret(RequestedSecret secret, Consumer<SecretAccessor> secretsConsumer,
			Consumer<Throwable> errorConsumer) {
		this.secret = secret;
		this.adapter = new LeaseListenerAdapter() {

			@Override
			public void onLeaseEvent(SecretLeaseEvent leaseEvent) {
				if (leaseEvent instanceof SecretLeaseCreatedEvent created) {
					SimpleSecretAccessor accessor = new SimpleSecretAccessor(created.getSecrets());
					try {
						secretsConsumer.accept(accessor);
					} catch (Exception e) {
						errorConsumer.accept(e);
					}
				}
			}

			@Override
			public void onLeaseError(SecretLeaseEvent leaseEvent, Exception exception) {
				errorConsumer.accept(exception);
			}

		};
	}


	/**
	 * Create a rotating {@code ManagedSecret} at {@code path}. The
	 * {@code secretsConsumer} is invoked with the new secrets are obtained from
	 * Vault upon initial request and each time the secret is rotated.
	 *
	 * @param path secret path.
	 * @param secretsConsumer consumer for secrets access.
	 * @return the managed secret object.
	 */
	public static ManagedSecret rotating(String path, Consumer<SecretAccessor> secretsConsumer) {
		return from(RequestedSecret.rotating(path), secretsConsumer, throwable -> {
			if (logger.isErrorEnabled()) {
				logger.error("Error occurred while processing secret at path: " + path, throwable);
			}
		});
	}

	/**
	 * Create a rotating {@code ManagedSecret} at {@code path}. The
	 * {@code secretsConsumer} is invoked with the new secrets are obtained from
	 * Vault upon initial request and each time the secret is rotated.
	 *
	 * @param path secret path.
	 * @param secretsConsumer consumer for secrets access.
	 * @param errorConsumer consumer for errors.
	 * @return the managed secret object.
	 */
	public static ManagedSecret rotating(String path, Consumer<SecretAccessor> secretsConsumer,
			Consumer<Throwable> errorConsumer) {
		return from(RequestedSecret.rotating(path), secretsConsumer, errorConsumer);
	}

	/**
	 * Create a {@code ManagedSecret} from {@link RequestedSecret}. The
	 * {@code secretsConsumer} is invoked with the new secrets are obtained from
	 * Vault upon initial request and each time the secret is rotated.
	 *
	 * @param secret the requested secret.
	 * @param secretsConsumer consumer for secrets access.
	 * @param errorConsumer consumer for errors.
	 * @return the managed secret object.
	 */
	public static ManagedSecret from(RequestedSecret secret, Consumer<SecretAccessor> secretsConsumer,
			Consumer<Throwable> errorConsumer) {
		return new ManagedSecret(secret, secretsConsumer, errorConsumer);
	}


	@Override
	public void registerSecret(SecretsRegistry registry) {
		registry.register(this.secret, this.adapter);
	}

	@Override
	public String toString() {
		return "ManagedSecret [" + secret + "]";
	}

	static <T> @Nullable T get(Map<String, Object> secrets, String key, Class<T> expectedType) {
		Object o = secrets.get(key);
		if (o == null) {
			return null;
		}
		if (expectedType.isInstance(o)) {
			return expectedType.cast(o);
		}
		throw new ClassCastException(
				"Value for key: %s (type: %s) is not of type %s".formatted(key, o.getClass().getName(),
						expectedType.getName()));
	}

	/**
	 * Interface to access secrets obtained from Vault.
	 */
	public interface SecretAccessor {

		/**
		 * Return the required {@code int} value for the given key or throw
		 * {@link NoSuchElementException}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped.
		 * @throws NoSuchElementException if the key is not present or its value was
		 * {@literal null}.
		 */
		default int getInt(String key) throws NoSuchElementException {
			return getRequiredNumber(key).intValue();
		}

		/**
		 * Return the {@code int} value for the given key or {@code defaultValue} if the
		 * key is not present or its value was {@code null}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped or
		 * {@code defaultValue}.
		 */
		default int getInt(String key, int defaultValue) {
			Number number = getNumber(key);
			return number != null ? number.intValue() : defaultValue;
		}

		/**
		 * Return the {@code int} value for the given key or the value of
		 * {@link IntSupplier#getAsInt()} if the key is not present or its value was
		 * {@code null}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped or
		 * {@link IntSupplier#getAsInt()}.
		 */
		default int getInt(String key, IntSupplier defaultValue) {
			Number number = getNumber(key);
			return number != null ? number.intValue() : defaultValue.getAsInt();
		}

		/**
		 * Return the required {@code long} value for the given key or throw
		 * {@link NoSuchElementException}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped.
		 * @throws NoSuchElementException if the key is not present or its value was
		 * {@literal null}.
		 */
		default long getLong(String key) throws NoSuchElementException {
			return getRequiredNumber(key).intValue();
		}

		/**
		 * Return the {@code long} value for the given key or {@code defaultValue} if
		 * the key is not present or its value was {@code null}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped or
		 * {@code defaultValue}.
		 */
		default long getLong(String key, long defaultValue) {
			Number number = getNumber(key);
			return number != null ? number.longValue() : defaultValue;
		}

		/**
		 * Return the {@code long} value for the given key or the value of
		 * {@link LongSupplier#getAsLong()} if the key is not present or its value was
		 * {@code null}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped or
		 * {@link LongSupplier#getAsLong()}.
		 */
		default long getLong(String key, LongSupplier defaultValue) {
			Number number = getNumber(key);
			return number != null ? number.longValue() : defaultValue.getAsLong();
		}

		/**
		 * Return the {@code String} value for the given key.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped, can be
		 * {@literal null}.
		 */
		@Nullable
		default String getString(String key) {
			return get(key, String.class);
		}

		/**
		 * Return the required {@code String} value for the given key or throw
		 * {@link NoSuchElementException}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped.
		 * @throws NoSuchElementException if the key is not present or its value was
		 * {@literal null}.
		 */
		default String getRequiredString(String key) throws NoSuchElementException {
			return getRequired(key, String.class);
		}

		/**
		 * Return the {@code String} value for the given key or {@code defaultValue} if
		 * the key is not present or its value was {@code null}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped or
		 * {@code defaultValue}.
		 */
		default String getString(String key, String defaultValue) {
			String string = get(key, String.class);
			return string != null ? string : defaultValue;
		}

		/**
		 * Return the {@code String} value for the given key or the value of
		 * {@link Supplier#get()} if the key is not present or its value was
		 * {@code null}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped or
		 * {@link Supplier#get()}.
		 */
		default String getString(String key, Supplier<String> defaultValue) {
			String string = get(key, String.class);
			return string != null ? string : defaultValue.get();
		}

		/**
		 * Return the required value for the given key or throw
		 * {@link NoSuchElementException}.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped.
		 * @throws NoSuchElementException if the key is not present or its value was
		 * {@literal null}.
		 */
		default <T> T getRequired(String key, Class<T> expectedType) throws NoSuchElementException {
			Object o = get(key, expectedType);
			if (o == null) {
				throw new NoSuchElementException("No value present for key: " + key);
			}
			return expectedType.cast(o);
		}

		private Number getRequiredNumber(String key) {
			return getRequired(key, Number.class);
		}

		private @Nullable Number getNumber(String key) {
			return get(key, Number.class);
		}

		/**
		 * Return the value for the given key.
		 * @param key the key whose associated value is to be returned.
		 * @return the value to which the specified key is mapped, can be
		 * {@literal null}.
		 */
		<T> @Nullable T get(String key, Class<T> expectedType);

		/**
		 * Transform this {@link SecretAccessor} into a target type. <pre class="code">
		 * {@code accessor.as(UsernamePassword::from) }
		 * </pre>
		 *
		 * @param transformer the {@link Function} to map this {@code SecretsAccessor}
		 * into a target type instance.
		 * @param <P> the returned instance type
		 *
		 * @return the {@code SecretsAccessor} transformed to an instance of {@code P}
		 */
		default <P> P as(Function<? super SecretAccessor, P> transformer) {
			return transformer.apply(this);
		}

	}

	record SimpleSecretAccessor(Map<String, Object> secrets) implements SecretAccessor {

		@Override
		public <T> @Nullable T get(String key, Class<T> expectedType) {
			return ManagedSecret.get(secrets, key, expectedType);
		}

	}


	/**
	 * Extension of {@link SecretAccessor} to access username and password values.
	 */
	public interface UsernamePassword extends SecretAccessor {

		/**
		 * Return the username value.
		 * @return the username value.
		 */
		String getUsername();

		/**
		 * Return the password value.
		 * @return the password value.
		 */
		CharSequence getPassword();

		/**
		 * Apply username and password to the given consumer.
		 * @param consumer the username-password consumer.
		 */
		default void applyTo(UsernamePasswordConsumer consumer) {
			consumer.accept(getUsername(), getPassword());
		}


		/**
		 * Factory method to create {@code UsernamePassword} from a
		 * {@link SecretAccessor}.
		 * @param accessor the secret accessor.
		 * @return a {@code UsernamePassword} instance.
		 * @throws IllegalStateException if username or password are missing.
		 */
		static UsernamePassword from(SecretAccessor accessor) {
			return new SimpleUsernamePasswordAccessor(accessor.getRequiredString("username"),
					accessor.getRequiredString("password"), accessor);
		}


		/**
		 * Represents an operation that accepts {@code username} and {@code passsword}
		 * arguments and returns no result.
		 */
		@FunctionalInterface
		interface UsernamePasswordConsumer {

			/**
			 * Performs this operation on the given arguments.
			 *
			 * @param username the username input argument.
			 * @param password the password input argument.
			 */
			void accept(String username, CharSequence password);

		}

	}

	record SimpleUsernamePasswordAccessor(String username, CharSequence password, SecretAccessor accessor)
			implements UsernamePassword {

		@Override
		public String getUsername() {
			return username();
		}

		@Override
		public CharSequence getPassword() {
			return password();
		}

		@Override
		public <T> @Nullable T get(String key, Class<T> expectedType) {
			return accessor.get(key, expectedType);
		}

	}

}
