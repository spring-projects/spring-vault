/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.vault.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.Base64Utils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.TransformPlaintext;
import org.springframework.vault.support.TransformCiphertext;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultTransformContext;
import org.springframework.vault.support.VaultTransformDecodeResult;
import org.springframework.vault.support.VaultTransformEncodeResult;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.Version;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link VaultTemplate} using the {@code transform} backend.
 *
 * @author Lauren Voswinkel
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = VaultIntegrationTestConfiguration.class)
@RequiresVaultVersion("1.4.0")
class VaultTransformTemplateIntegrationTests extends IntegrationTestSupport {

	@Autowired
	VaultOperations vaultOperations;

	VaultTransformOperations transformOperations;

	Version vaultVersion;

	@BeforeEach
	void before() {
		Assumptions.assumeTrue(prepare().getVersion().isEnterprise(), "Transform Secrets Engine requires enterprise version");

		VaultSysOperations adminOperations = this.vaultOperations.opsForSys();
		this.transformOperations = this.vaultOperations.opsForTransform();

		this.vaultVersion = prepare().getVersion();

		if (!adminOperations.getMounts().containsKey("transform/")) {
			adminOperations.mount("transform", VaultMount.create("transform"));
		}

		// Write a transformation/role
		this.vaultOperations.write("transform/transformation/myssn", "{" +
				"\"type\": \"fpe\", " +
				"\"template\": \"builtin/socialsecuritynumber\", " +
				"\"allowed_roles\": [\"myrole\"]}"
		);
		this.vaultOperations.write("transform/role/myrole", "{\"transformations\": [\"myssn\", \"internalssn\"]}");

		this.vaultOperations.write("transform/transformation/internalssn", "{" +
				"\"type\": \"fpe\", " +
				"\"tweak_source\": \"internal\", " +
				"\"template\": \"builtin/socialsecuritynumber\", " +
				"\"allowed_roles\": [\"internalrole\"]}"
		);

		this.vaultOperations.write("transform/role/internalrole", "{\"transformations\": [\"internalssn\"]}");

		this.vaultOperations.write("transform/transformation/generatedssn", "{" +
				"\"type\": \"fpe\", " +
				"\"tweak_source\": \"generated\", " +
				"\"template\": \"builtin/socialsecuritynumber\", " +
				"\"allowed_roles\": [\"generatedrole\"]}"
		);

		this.vaultOperations.write("transform/role/generatedrole", "{\"transformations\": [\"generatedssn\"]}");
	}

	@AfterEach
	void tearDown() {
		this.vaultOperations.delete("transform/role/myrole");
		this.vaultOperations.delete("transform/transformation/myssn");
		this.vaultOperations.delete("transform/role/internalrole");
		this.vaultOperations.delete("transform/transformation/internalssn");
		// this.vaultOperations.delete("transform/role/generatedrole");
		// this.vaultOperations.delete("transform/transformation/generatedssn");
	}

	@Test
	void encodeCreatesCiphertextWithTransformationAndTweak() {

		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("myssn")
				.tweak("somenum".getBytes())
				.build();

		TransformCiphertext response = this.transformOperations.encode("myrole", "123-45-6789".getBytes(), transformRequest);
		assertThat(response.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
	}

	@Test
	void encodeThrowsVaultExceptionWithSuppliedTransformationAndNoTweak() {

		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("myssn").build();

		Exception exception = assertThrows(VaultException.class, () -> {
			this.transformOperations.encode("myrole", "123-45-6789".getBytes(), transformRequest);
		});

		String expectedMessage = "incorrect tweak size provided";
		String actualMessage = exception.getMessage();

		assertThat(actualMessage).contains(expectedMessage);
	}

	@Test
	void encodeCreatesCiphertextWithGeneratedTransformationAndNoTweak() {

		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("generatedssn").build();

		TransformCiphertext response = this.transformOperations.encode("generatedrole", "123-45-6789".getBytes(), transformRequest);
		assertThat(response.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
	}

	@Test
	void encodeCreatesCiphertextWithInternalTransformationAndNoTweak() {

		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("internalssn").build();

		TransformCiphertext response = this.transformOperations.encode("internalrole", "123-45-6789".getBytes(), transformRequest);
		assertThat(response.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
	}

	@Test
	void encodeAndDecodeYieldsStartingResultWithSameTweakValueProvided() {
		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("myssn").tweak("somenum".getBytes()).build();
		String targetValue = "123-45-6789";

		TransformCiphertext response = this.transformOperations.encode("myrole", targetValue.getBytes(), transformRequest);
		assertThat(response.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
		assertThat(response.getCiphertext()).isNotEqualTo(targetValue);

		String decodeResponse = this.transformOperations.decode("myrole", response.getCiphertext(), transformRequest);
		assertThat(decodeResponse).isEqualTo(targetValue);
	}

	@Test
	void encodeAndDecodeYieldsStartingResultWithGeneratedTweakValueProvided() {
		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("generatedssn").build();
		String targetValue = "123-45-6789";

		TransformCiphertext response = this.transformOperations.encode("generatedrole", targetValue.getBytes(), transformRequest);
		assertThat(response.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
		assertThat(response.getCiphertext()).isNotEqualTo(targetValue);

		String decodeResponse = this.transformOperations.decode("generatedrole", response.getCiphertext(), response.getContext());
		assertThat(decodeResponse).isEqualTo(targetValue);
	}

	@Test
	void encodeAndDecodeDoesNotYieldStartingResultWithDifferentTweakValueProvided() {
		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("myssn").tweak("somenum".getBytes()).build();
		String targetValue = "123-45-6789";

		TransformCiphertext response = this.transformOperations.encode("myrole", targetValue.getBytes(), transformRequest);
		assertThat(response.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
		assertThat(response.getCiphertext()).isNotEqualTo(targetValue);

		VaultTransformContext decodeRequest = VaultTransformContext.builder().transformation("myssn").tweak("numsome".getBytes()).build();

		String decodeResponse = this.transformOperations.decode("myrole", response.getCiphertext(), decodeRequest);
		assertThat(decodeResponse).isNotEqualTo(targetValue);
	}

	@Test
	void encodeAndDecodeWithoutContextWorksForInternalTweakSource() {
		String targetValue = "123-45-6789";

		String response = this.transformOperations.encode("internalrole", targetValue);
		assertThat(response).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
		assertThat(response).isNotEqualTo(targetValue);

		String decodeResponse = this.transformOperations.decode("internalrole", response);
		assertThat(decodeResponse).isEqualTo(targetValue);
	}

	@Test
	void batchEncodeAndDecodeYieldsStartingResults() {
		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("myssn").tweak("somenum".getBytes()).build();

		List<String> ssns = Arrays.asList(
				"123-01-4567",
				"123-02-4567",
				"123-03-4567",
				"123-04-4567",
				"123-05-4567"
		);

		List<VaultTransformEncodeResult> encoded = this.transformOperations.encode("myrole",
				Arrays.asList(
						TransformPlaintext.of(ssns.get(0)).with(transformRequest),
						TransformPlaintext.of(ssns.get(1)).with(transformRequest),
						TransformPlaintext.of(ssns.get(2)).with(transformRequest),
						TransformPlaintext.of(ssns.get(3)).with(transformRequest),
						TransformPlaintext.of(ssns.get(4)).with(transformRequest)
				)
		);

		List<VaultTransformDecodeResult> decoded = this.transformOperations.decode("myrole",
				Arrays.asList(
						TransformCiphertext.of(encoded.get(0).getAsString()).with(transformRequest),
						TransformCiphertext.of(encoded.get(1).getAsString()).with(transformRequest),
						TransformCiphertext.of(encoded.get(2).getAsString()).with(transformRequest),
						TransformCiphertext.of(encoded.get(3).getAsString()).with(transformRequest),
						TransformCiphertext.of(encoded.get(4).getAsString()).with(transformRequest)
				)
		);

		for (int i = 0; i < decoded.size(); i++) {
			assertThat(decoded.get(i).getAsString()).isEqualTo(ssns.get(i));
		}
	}

	@Test
	void batchEncodeAndDecodeYieldsStartingResultsForInternalWithNoContext() {

		List<String> ssns = Arrays.asList(
				"123-01-4567",
				"123-02-4567",
				"123-03-4567",
				"123-04-4567",
				"123-05-4567"
		);

		List<VaultTransformEncodeResult> encoded = this.transformOperations.encode("internalrole",
				Arrays.asList(
						TransformPlaintext.of(ssns.get(0)),
						TransformPlaintext.of(ssns.get(1)),
						TransformPlaintext.of(ssns.get(2)),
						TransformPlaintext.of(ssns.get(3)),
						TransformPlaintext.of(ssns.get(4))
				)
		);

		List<VaultTransformDecodeResult> decoded = this.transformOperations.decode("internalrole",
				Arrays.asList(
						TransformCiphertext.of(encoded.get(0).getAsString()),
						TransformCiphertext.of(encoded.get(1).getAsString()),
						TransformCiphertext.of(encoded.get(2).getAsString()),
						TransformCiphertext.of(encoded.get(3).getAsString()),
						TransformCiphertext.of(encoded.get(4).getAsString())
				)
		);

		for (int i = 0; i < decoded.size(); i++) {
			assertThat(decoded.get(i).getAsString()).isEqualTo(ssns.get(i));
		}
	}
}
