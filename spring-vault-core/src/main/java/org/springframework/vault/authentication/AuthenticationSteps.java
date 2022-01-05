/*
 * Copyright 2017-2022 the original author or authors.
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
package org.springframework.vault.authentication;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultToken;

/**
 * Authentication DSL allowing flow composition to create a {@link VaultToken}.
 * <p>
 * Static generators are the main entry point to start with a flow composition. An example
 * authentication using AWS-EC2 authentication:
 *
 * <pre class="code">
 * String nonce = &quot;&quot;;
 * return AuthenticationSteps
 * 		.fromHttpRequest(
 * 				HttpRequestBuilder.get(options.getIdentityDocumentUri().toString()) //
 * 						.as(String.class)) //
 * 		.map(pkcs7 -&gt; pkcs7.replaceAll(&quot;\\r&quot;, &quot;&quot;)) //
 * 		.map(pkcs7 -&gt; {
 *
 * 			Map&lt;String, String&gt; login = new HashMap&lt;&gt;();
 *
 * 			login.put(&quot;nonce&quot;, new String(nonce));
 * 			login.put(&quot;pkcs7&quot;, pkcs7);
 *
 * 			return login;
 * 		}).login(AuthenticationUtil.getLoginPath(options.getPath()));
 * </pre>
 *
 * <p>
 * To perform a computation, authentication steps are composed into a <em>pipeline</em>. A
 * pipeline consists of a source (which might be an object, a supplier function, a HTTP
 * request, etc), zero or more <em>intermediate operations</em> (which transform the
 * authentication state object into another object, such as {@link Node#map(Function)}),
 * and a <em>terminal operation</em> which finishes authentication composition. An
 * authentication flow operates on the authentication state object which is created for
 * each authentication. Step produce an object and some steps can accept the current state
 * object for further transformation.
 *
 * <p>
 * {@link AuthenticationSteps} describes the authentication flow. Computation on the
 * source data is only performed when the flow definition is interpreted by an executor.
 *
 * @author Mark Paluch
 * @since 2.0
 * @see AuthenticationStepsFactory
 */
public class AuthenticationSteps {

	private static final Node<Object> HEAD = new Node<>();

	final List<Node<?>> steps;

	/**
	 * Create a flow definition using a provided {@link VaultToken}.
	 * @param token the token to be used from this {@link AuthenticationSteps}, must not
	 * be {@literal null}.
	 * @return the {@link AuthenticationSteps}.
	 */
	public static AuthenticationSteps just(VaultToken token) {

		Assert.notNull(token, "Vault token must not be null");

		return new AuthenticationSteps(new ScalarValueStep<>(token, AuthenticationSteps.HEAD));
	}

	/**
	 * Create a flow definition from a {@link HttpRequest} returning a
	 * {@link VaultResponse}.
	 * @param request the HTTP request definition, must not be {@literal null}.
	 * @return the {@link AuthenticationSteps}.
	 */
	public static AuthenticationSteps just(HttpRequest<VaultResponse> request) {

		Assert.notNull(request, "HttpRequest must not be null");

		return new AuthenticationSteps(new HttpRequestNode<>(request, AuthenticationSteps.HEAD));
	}

	/**
	 * Start flow composition from a scalar {@code value}.
	 * @param value the value to be used from this {@link Node}, must not be
	 * {@literal null}.
	 * @return the first {@link Node}.
	 * @since 2.3
	 */
	public static <T> Node<T> fromValue(T value) {

		Assert.notNull(value, "Value must not be null");

		return new ScalarValueStep<>(value, AuthenticationSteps.HEAD);
	}

	/**
	 * Start flow composition from a {@link Supplier}.
	 * @param supplier supplier function that will produce the flow value, must not be
	 * {@literal null}. Infrastructure components evaluating authentication steps may
	 * inspect the given {@link java.util.function.Supplier} for an optimized approach to
	 * obtain its value.
	 * @return the first {@link Node}.
	 */
	public static <T> Node<T> fromSupplier(Supplier<T> supplier) {

		Assert.notNull(supplier, "Supplier must not be null");

		return new SupplierStep<>(supplier, AuthenticationSteps.HEAD);
	}

	/**
	 * Start flow composition from a {@link HttpRequest}.
	 * @param request the HTTP request definition, must not be {@literal null}.
	 * @return the first {@link Node}.
	 */
	public static <T> Node<T> fromHttpRequest(HttpRequest<T> request) {

		Assert.notNull(request, "HttpRequest must not be null");

		return new HttpRequestNode<>(request, AuthenticationSteps.HEAD);
	}

	AuthenticationSteps(PathAware pathAware) {
		this.steps = getChain(pathAware);
	}

	/**
	 * Return a {@link List} of node given a {@link PathAware} starting point.
	 * @param pathAware must not be {@literal null}.
	 * @return
	 */
	static List<Node<?>> getChain(PathAware pathAware) {

		List<Node<?>> steps = new ArrayList<>();

		PathAware current = pathAware;
		do {
			if (current instanceof Node<?>) {
				steps.add((Node<?>) current);
			}

			if (current.getPrevious() instanceof PathAware) {
				current = (PathAware) current.getPrevious();
			}
			else {
				break;
			}

		}
		while (!Objects.equals(current, AuthenticationSteps.HEAD));

		Collections.reverse(steps);

		return steps;
	}

	/**
	 * Intermediate authentication step with authentication flow operators represented as
	 * node.
	 *
	 * @param <T> authentication state object type produced by this node.
	 */
	public static class Node<T> {

		/**
		 * Transform the state object into a different object.
		 * @param mappingFunction mapping function to be applied to the state object, must
		 * not be {@literal null}.
		 * @param <R> resulting object type
		 * @return the next {@link Node}.
		 */
		public <R> Node<R> map(Function<? super T, ? extends R> mappingFunction) {

			Assert.notNull(mappingFunction, "Mapping function must not be null");

			return new MapStep<>(mappingFunction, this);
		}

		/**
		 * Combine the result from this {@link Node} and another into a {@link Pair}.
		 * @return the next {@link Node}.
		 * @since 2.1
		 */
		public <R> Node<Pair<T, R>> zipWith(Node<? extends R> other) {

			Assert.notNull(other, "Other node must not be null");
			Assert.isInstanceOf(PathAware.class, other, "Other node must be PathAware");

			return new ZipStep<>(this, (PathAware) other);
		}

		/**
		 * Callback with the current state object.
		 * @param consumerFunction consumer function to be called with the state object,
		 * must not be {@literal null}.
		 * @return the next {@link Node}.
		 */
		public Node<T> onNext(Consumer<? super T> consumerFunction) {

			Assert.notNull(consumerFunction, "Consumer function must not be null");

			return new OnNextStep<>(consumerFunction, this);
		}

		/**
		 * Request data using a {@link HttpRequest}.
		 * @param request the HTTP request definition, must not be {@literal null}.
		 * @return the next {@link Node}.
		 */
		public <R> Node<R> request(HttpRequest<R> request) {

			Assert.notNull(request, "HttpRequest must not be null");

			return new HttpRequestNode<>(request, this);
		}

		/**
		 * Terminal operation requesting a {@link VaultToken token} from Vault by posting
		 * the current state to Vaults {@code uriTemplate}.
		 * @param uriTemplate Vault authentication endpoint, must not be {@literal null}
		 * or empty.
		 * @param uriVariables URI variables for URI template expansion.
		 * @return the {@link AuthenticationSteps}.
		 */
		public AuthenticationSteps login(String uriTemplate, String... uriVariables) {

			Assert.hasText(uriTemplate, "URI template must not be null or empty");

			return login(HttpRequestBuilder.post(uriTemplate, uriVariables).as(VaultResponse.class));
		}

		/**
		 * Terminal operation requesting a {@link VaultToken token} from Vault by issuing
		 * a HTTP request with the current state to Vaults {@code uriTemplate}.
		 * @param request HTTP request definition.
		 * @return the {@link AuthenticationSteps}.
		 */
		public AuthenticationSteps login(HttpRequest<VaultResponse> request) {

			Assert.notNull(request, "HttpRequest must not be null");

			return new AuthenticationSteps(new HttpRequestNode<>(request, this));
		}

		/**
		 * Terminal operation resulting in a {@link VaultToken token} by applying a
		 * mapping {@link Function} to the current state object.
		 * @param mappingFunction mapping function to be applied to the state object, must
		 * not be {@literal null}.
		 * @return the {@link AuthenticationSteps}.
		 */
		public AuthenticationSteps login(Function<? super T, ? extends VaultToken> mappingFunction) {

			Assert.notNull(mappingFunction, "Mapping function must not be null");

			return new AuthenticationSteps(new MapStep<>(mappingFunction, this));
		}

	}

	/**
	 * Builder for {@link HttpRequest}.
	 */
	public static class HttpRequestBuilder {

		HttpMethod method;

		@Nullable
		URI uri;

		@Nullable
		String uriTemplate;

		@Nullable
		String[] urlVariables;

		@Nullable
		HttpEntity<?> entity;

		/**
		 * Builder entry point to {@code GET} from {@code uriTemplate}.
		 * @param uriTemplate must not be {@literal null} or empty.
		 * @param uriVariables the variables to expand the template.
		 * @return a new {@link HttpRequestBuilder}.
		 */
		public static HttpRequestBuilder get(String uriTemplate, String... uriVariables) {
			return new HttpRequestBuilder(HttpMethod.GET, uriTemplate, uriVariables);
		}

		/**
		 * Builder entry point to {@code GET} from {@code uri}.
		 * @param uri must not be {@literal null}.
		 * @return a new {@link HttpRequestBuilder}.
		 */
		public static HttpRequestBuilder get(URI uri) {
			return new HttpRequestBuilder(HttpMethod.GET, uri);
		}

		/**
		 * Builder entry point to {@code POST} to {@code uriTemplate}.
		 * @param uriTemplate must not be {@literal null} or empty.
		 * @param uriVariables the variables to expand the template.
		 * @return a new {@link HttpRequestBuilder}.
		 */
		public static HttpRequestBuilder post(String uriTemplate, String... uriVariables) {
			return new HttpRequestBuilder(HttpMethod.POST, uriTemplate, uriVariables);
		}

		/**
		 * Builder entry point to {@code POST} to {@code uri}.
		 * @param uri must not be {@literal null}.
		 * @return a new {@link HttpRequestBuilder}.
		 */
		public static HttpRequestBuilder post(URI uri) {
			return new HttpRequestBuilder(HttpMethod.POST, uri);
		}

		/**
		 * Builder entry point to use {@link HttpMethod} for {@code uriTemplate}.
		 * @param uriTemplate must not be {@literal null} or empty.
		 * @param uriVariables the variables to expand the template.
		 * @return a new {@link HttpRequestBuilder}.
		 * @since 2.2
		 */
		public static HttpRequestBuilder method(HttpMethod method, String uriTemplate, String... uriVariables) {
			return new HttpRequestBuilder(method, uriTemplate, uriVariables);
		}

		private HttpRequestBuilder(HttpMethod method, URI uri) {
			this.method = method;
			this.uri = uri;
		}

		private HttpRequestBuilder(HttpMethod method, @Nullable String uriTemplate, @Nullable String[] urlVariables) {
			this.method = method;
			this.uriTemplate = uriTemplate;
			this.urlVariables = urlVariables;
		}

		private HttpRequestBuilder(HttpMethod method, @Nullable URI uri, @Nullable String uriTemplate,
				@Nullable String[] urlVariables, @Nullable HttpEntity<?> entity) {
			this.method = method;
			this.uri = uri;
			this.uriTemplate = uriTemplate;
			this.urlVariables = urlVariables;
			this.entity = entity;
		}

		/**
		 * Configure a request {@link HttpEntity entity}.
		 * @param httpEntity must not be {@literal null}.
		 * @return a new {@link HttpRequestBuilder}.
		 */
		public HttpRequestBuilder with(HttpEntity<?> httpEntity) {

			Assert.notNull(httpEntity, "HttpEntity must not be null");

			return new HttpRequestBuilder(this.method, this.uri, this.uriTemplate, this.urlVariables, httpEntity);
		}

		/**
		 * Configure a request {@link HttpHeaders headers}.
		 * @param headers must not be {@literal null}.
		 * @return a new {@link HttpRequestBuilder}.
		 */
		public HttpRequestBuilder with(HttpHeaders headers) {

			Assert.notNull(headers, "HttpHeaders must not be null");

			return new HttpRequestBuilder(this.method, this.uri, this.uriTemplate, this.urlVariables,
					new HttpEntity<>(headers));
		}

		/**
		 * Configure the result type and build the {@link HttpRequest} object.
		 * @param type must not be {@literal null}.
		 * @return the {@link HttpRequest} definition.
		 */
		public <T> HttpRequest<T> as(Class<T> type) {

			Assert.notNull(type, "Result type must not be null");

			return new HttpRequest<>(this, type);
		}

	}

	/**
	 * Value object representing a HTTP request.
	 *
	 * @param <T> authentication state object type produced by this request.
	 */
	public static class HttpRequest<T> {

		final HttpMethod method;

		@Nullable
		final URI uri;

		@Nullable
		final String uriTemplate;

		@Nullable
		final String[] urlVariables;

		@Nullable
		final HttpEntity<?> entity;

		final Class<T> responseType;

		HttpRequest(HttpRequestBuilder builder, Class<T> responseType) {
			this.method = builder.method;
			this.uri = builder.uri;
			this.uriTemplate = builder.uriTemplate;
			this.urlVariables = builder.urlVariables;
			this.entity = builder.entity;
			this.responseType = responseType;
		}

		@Override
		public String toString() {
			return String.format("%s %s AS %s", getMethod(), getUri() != null ? getUri() : getUriTemplate(),
					getResponseType());
		}

		HttpMethod getMethod() {
			return this.method;
		}

		@Nullable
		URI getUri() {
			return this.uri;
		}

		@Nullable
		String getUriTemplate() {
			return this.uriTemplate;
		}

		@Nullable
		String[] getUrlVariables() {
			return this.urlVariables;
		}

		@Nullable
		HttpEntity<?> getEntity() {
			return this.entity;
		}

		Class<T> getResponseType() {
			return this.responseType;
		}

	}

	static final class HttpRequestNode<T> extends Node<T> implements PathAware {

		private final HttpRequest<T> definition;

		private final Node<?> previous;

		HttpRequestNode(HttpRequest<T> definition, Node<?> previous) {
			this.definition = definition;
			this.previous = previous;
		}

		@Override
		public String toString() {
			return this.definition.toString();
		}

		public HttpRequest<T> getDefinition() {
			return this.definition;
		}

		public Node<?> getPrevious() {
			return this.previous;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof HttpRequestNode))
				return false;
			HttpRequestNode<?> that = (HttpRequestNode<?>) o;
			return this.definition.equals(that.definition) && this.previous.equals(that.previous);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.definition, this.previous);
		}

	}

	static final class MapStep<I, O> extends Node<O> implements PathAware {

		private final Function<? super I, ? extends O> mapper;

		private final Node<?> previous;

		MapStep(Function<? super I, ? extends O> mapper, Node<?> previous) {
			this.mapper = mapper;
			this.previous = previous;
		}

		O apply(I in) {
			return this.mapper.apply(in);
		}

		@Override
		public String toString() {
			return "Map: " + this.mapper.toString();
		}

		public Function<? super I, ? extends O> getMapper() {
			return this.mapper;
		}

		public Node<?> getPrevious() {
			return this.previous;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof MapStep))
				return false;
			MapStep<?, ?> mapStep = (MapStep<?, ?>) o;
			return this.mapper.equals(mapStep.mapper) && this.previous.equals(mapStep.previous);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.mapper, this.previous);
		}

	}

	static final class ZipStep<L, R> extends Node<Pair<L, R>> implements PathAware {

		private final Node<?> left;

		private final List<Node<?>> right;

		ZipStep(Node<?> left, PathAware right) {
			this.left = left;
			this.right = getChain(right);
		}

		@Override
		public Node<?> getPrevious() {
			return this.left;
		}

		@Override
		public String toString() {
			return "Zip";
		}

		public AuthenticationSteps.Node<?> getLeft() {
			return this.left;
		}

		public List<Node<?>> getRight() {
			return this.right;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ZipStep))
				return false;
			ZipStep<?, ?> zipStep = (ZipStep<?, ?>) o;
			return this.left.equals(zipStep.left) && this.right.equals(zipStep.right);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.left, this.right);
		}

	}

	static final class OnNextStep<T> extends Node<T> implements PathAware {

		private final Consumer<? super T> consumer;

		private final Node<?> previous;

		OnNextStep(Consumer<? super T> consumer, Node<?> previous) {
			this.consumer = consumer;
			this.previous = previous;
		}

		T apply(T in) {
			this.consumer.accept(in);
			return in;
		}

		@Override
		public String toString() {
			return "Consumer: " + this.consumer.toString();
		}

		public Consumer<? super T> getConsumer() {
			return this.consumer;
		}

		public AuthenticationSteps.Node<?> getPrevious() {
			return this.previous;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof OnNextStep))
				return false;
			OnNextStep<?> that = (OnNextStep<?>) o;
			return this.consumer.equals(that.consumer) && this.previous.equals(that.previous);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.consumer, this.previous);
		}

	}

	static final class ScalarValueStep<T> extends Node<T> implements PathAware {

		private final T value;

		private final Node<?> previous;

		ScalarValueStep(T value, Node<?> previous) {
			this.value = value;
			this.previous = previous;
		}

		@Override
		public String toString() {
			return "Value: " + this.value.toString();
		}

		public T get() {
			return this.value;
		}

		public Node<?> getPrevious() {
			return this.previous;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ScalarValueStep))
				return false;
			ScalarValueStep<?> that = (ScalarValueStep<?>) o;
			return this.value.equals(that.value) && this.previous.equals(that.previous);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.value, this.previous);
		}

	}

	static final class SupplierStep<T> extends Node<T> implements PathAware {

		private final Supplier<T> supplier;

		private final Node<?> previous;

		SupplierStep(Supplier<T> supplier, Node<?> previous) {
			this.supplier = supplier;
			this.previous = previous;
		}

		public T get() {
			return this.supplier.get();
		}

		@Override
		public String toString() {
			return "Supplier: " + this.supplier.toString();
		}

		public Supplier<T> getSupplier() {
			return this.supplier;
		}

		public Node<?> getPrevious() {
			return this.previous;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof SupplierStep))
				return false;
			SupplierStep<?> that = (SupplierStep<?>) o;
			return this.supplier.equals(that.supplier) && this.previous.equals(that.previous);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.supplier, this.previous);
		}

	}

	interface PathAware {

		Node<?> getPrevious();

	}

	/**
	 * A tuple of two things.
	 *
	 * @param <L>
	 * @param <R>
	 * @since 2.1
	 */
	public static class Pair<L, R> {

		private final L left;

		private final R right;

		private Pair(L left, R right) {
			this.left = left;
			this.right = right;
		}

		/**
		 * Create a new {@link Pair} given {@code left} and {@code right} values.
		 * @param left the left value.
		 * @param right the right value.
		 * @return the {@link Pair}.
		 */
		public static <L, R> Pair<L, R> of(L left, R right) {
			return new Pair<>(left, right);
		}

		/**
		 * Type-safe way to get the fist object of this {@link Pair}.
		 * @return The first object
		 */
		public L getLeft() {
			return this.left;
		}

		/**
		 * Type-safe way to get the second object of this {@link Pair}.
		 * @return The second object
		 */
		public R getRight() {
			return this.right;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof Pair))
				return false;
			Pair<?, ?> pair = (Pair<?, ?>) o;
			return this.left.equals(pair.left) && this.right.equals(pair.right);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.left, this.right);
		}

		@Override
		public String toString() {
			StringBuffer sb = new StringBuffer();
			sb.append(getClass().getSimpleName());
			sb.append(" [left=").append(this.left);
			sb.append(", right=").append(this.right);
			sb.append(']');
			return sb.toString();
		}

	}

}
