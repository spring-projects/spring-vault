/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.vault.authentication;

import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.vault.client.VaultClients.PrefixAwareUriTemplateHandler;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.iam.credentials.v1.SignJwtRequest;
import com.google.cloud.iam.credentials.v1.SignJwtResponse;

import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.lite.ProtoLiteUtils;
import io.grpc.stub.ServerCalls;

/**
 * Unit tests for {@link GcpIamCredentialsAuthentication}.
 *
 * @author Andreas Gebauer
 */
class GcpIamCredentialsAuthenticationUnitTests {

	RestTemplate restTemplate;

	MockRestServiceServer mockRest;

	Server server;

	ManagedChannel managedChannel;

	ServerCalls.UnaryMethod<SignJwtRequest, SignJwtResponse> serverCall;

	@BeforeEach
	void before() throws IOException {

		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setUriTemplateHandler(new PrefixAwareUriTemplateHandler());

		this.mockRest = MockRestServiceServer.createServer(restTemplate);
		this.restTemplate = restTemplate;

		String serverName = InProcessServerBuilder.generateName();
		this.server = InProcessServerBuilder.forName(serverName).directExecutor()
				.addService(ServerServiceDefinition.builder("google.iam.credentials.v1.IAMCredentials")
						.addMethod(
								MethodDescriptor
										.newBuilder(ProtoLiteUtils.marshaller(SignJwtRequest.getDefaultInstance()),
												ProtoLiteUtils.marshaller(SignJwtResponse.getDefaultInstance()))
										.setType(MethodDescriptor.MethodType.UNARY)
										.setFullMethodName("google.iam.credentials.v1.IAMCredentials/SignJwt").build(),
								asyncUnaryCall((request, responseObserver) -> {
									this.serverCall.invoke(request, responseObserver);
								}))
						.build())
				.build().start();
		this.managedChannel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
	}

	@AfterEach
	void after() {
		this.server.shutdown();
	}

	@Test
	void shouldLogin() {
		this.serverCall = ((request, responseObserver) -> {
			SignJwtResponse signJwtResponse = SignJwtResponse.newBuilder().setSignedJwt("my-jwt").setKeyId("key-id")
					.build();
			responseObserver.onNext(signJwtResponse);
			responseObserver.onCompleted();
		});

		this.mockRest.expect(requestTo("/auth/gcp/login")).andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.role").value("dev-role")).andExpect(jsonPath("$.jwt").value("my-jwt"))
				.andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON).body(
						"{" + "\"auth\":{\"client_token\":\"my-token\", \"renewable\": true, \"lease_duration\": 10}"
								+ "}"));

		PrivateKey privateKeyMock = mock(PrivateKey.class);
		ServiceAccountCredentials credential = (ServiceAccountCredentials) ServiceAccountCredentials.newBuilder()
				.setClientEmail("hello@world").setProjectId("foobar").setPrivateKey(privateKeyMock)
				.setPrivateKeyId("key-id")
				.setAccessToken(new AccessToken("foobar", Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))).build();

		GcpIamCredentialsAuthenticationOptions options = GcpIamCredentialsAuthenticationOptions.builder()
				.role("dev-role").credentials(credential).build();
		GcpIamCredentialsAuthentication authentication = new GcpIamCredentialsAuthentication(options, this.restTemplate,
				FixedTransportChannelProvider.create(GrpcTransportChannel.create(managedChannel)));

		VaultToken login = authentication.login();

		assertThat(login).isInstanceOf(LoginToken.class);
		assertThat(login.getToken()).isEqualTo("my-token");

		LoginToken loginToken = (LoginToken) login;
		assertThat(loginToken.isRenewable()).isTrue();
		assertThat(loginToken.getLeaseDuration()).isEqualTo(Duration.ofSeconds(10));
	}

	@Test
	void shouldCreateNewGcpIamObjectInstance() {

		PrivateKey privateKeyMock = mock(PrivateKey.class);
		ServiceAccountCredentials credential = (ServiceAccountCredentials) ServiceAccountCredentials.newBuilder()
				.setClientEmail("hello@world").setProjectId("foobar").setPrivateKey(privateKeyMock)
				.setPrivateKeyId("key-id")
				.setAccessToken(new AccessToken("foobar", Date.from(Instant.now().plus(1, ChronoUnit.DAYS)))).build();

		GcpIamCredentialsAuthenticationOptions options = GcpIamCredentialsAuthenticationOptions.builder()
				.role("dev-role").credentials(credential).build();

		new GcpIamCredentialsAuthentication(options, this.restTemplate);
	}

}
