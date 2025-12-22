/*
 * Copyright 2020-2025 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.TransformCiphertext;
import org.springframework.vault.support.TransformPlaintext;
import org.springframework.vault.support.VaultMount;
import org.springframework.vault.support.VaultTransformContext;
import org.springframework.vault.support.VaultTransformDecodeResult;
import org.springframework.vault.support.VaultTransformEncodeResult;
import org.springframework.vault.util.IntegrationTestSupport;
import org.springframework.vault.util.RequiresVaultVersion;
import org.springframework.vault.util.Version;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link VaultTemplate} using the {@code transform}
 * backend.
 *
 * @author Lauren Voswinkel
 * @author Mark Paluch
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

		Assumptions.assumeTrue(prepare().getVersion().isEnterprise(),
				"Transform Secrets Engine requires enterprise version");

		VaultSysOperations adminOperations = this.vaultOperations.opsForSys();
		this.transformOperations = this.vaultOperations.opsForTransform();

		this.vaultVersion = prepare().getVersion();

		if (!adminOperations.getMounts().containsKey("transform/")) {
			adminOperations.mount("transform", VaultMount.create("transform"));
		}

		// Write a transformation/role
		this.vaultOperations.write("transform/transformation/myssn", "{" + "\"type\": \"fpe\", "
				+ "\"template\": \"builtin/socialsecuritynumber\", " + "\"allowed_roles\": [\"myrole\"]}");
		this.vaultOperations.write("transform/role/myrole", "{\"transformations\": [\"myssn\", \"internalssn\"]}");

		this.vaultOperations.write("transform/transformation/internalssn",
				"{" + "\"type\": \"fpe\", " + "\"tweak_source\": \"internal\", "
						+ "\"template\": \"builtin/socialsecuritynumber\", "
						+ "\"allowed_roles\": [\"myrole\", \"internalrole\"]}");

		this.vaultOperations.write("transform/transformation/generatedssn",
				"{" + "\"type\": \"fpe\", " + "\"tweak_source\": \"generated\", "
						+ "\"template\": \"builtin/socialsecuritynumber\", "
						+ "\"allowed_roles\": [\"generatedrole\"]}");

		this.vaultOperations.write("transform/role/internalrole", "{\"transformations\": [\"internalssn\"]}");
		this.vaultOperations.write("transform/role/generatedrole", "{\"transformations\": [\"generatedssn\"]}");
	}

	@AfterEach
	void tearDown() {
		this.vaultOperations.delete("transform/role/myrole");
		this.vaultOperations.delete("transform/role/internalrole");
		this.vaultOperations.delete("transform/role/generatedrole");
		this.vaultOperations.delete("transform/transformation/myssn");
		this.vaultOperations.delete("transform/transformation/internalssn");
		this.vaultOperations.delete("transform/transformation/generatedssn");
	}

	@Test
	void encodeCreatesCiphertextWithTransformationAndProvidedTweak() {

		VaultTransformContext transformRequest = VaultTransformContext.builder()
				.transformation("myssn")
				.tweak("somenum".getBytes())
				.build();

		TransformCiphertext ciphertext = this.transformOperations.encode("myrole", "123-45-6789".getBytes(),
				transformRequest);
		assertThat(ciphertext.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
		assertThat(new String(ciphertext.getContext().getTweak())).isEqualTo("somenum");
	}

	@Test
	void encodeThrowsVaultExceptionWithSuppliedTransformationAndNoTweak() {

		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("myssn").build();

		assertThatExceptionOfType(VaultException.class).isThrownBy(() -> {
			this.transformOperations.encode("myrole", "123-45-6789".getBytes(), transformRequest);
		}).withMessageContaining("incorrect tweak size provided");
	}

	@Test
	void encodeCreatesCiphertextWithInternalTransformationAndNoTweak() {

		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("internalssn").build();

		TransformCiphertext ciphertext = this.transformOperations.encode("myrole", "123-45-6789".getBytes(),
				transformRequest);
		assertThat(ciphertext.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}");
	}

	@Test
	void encodeAndDecodeYieldsStartingResultWithSameTweakValueProvided() {

		VaultTransformContext transformRequest = VaultTransformContext.builder()
				.transformation("myssn")
				.tweak("somenum".getBytes())
				.build();
		String targetValue = "123-45-6789";

		TransformCiphertext ciphertext = this.transformOperations.encode("myrole", targetValue.getBytes(),
				transformRequest);
		assertThat(ciphertext.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}").isNotEqualTo(targetValue);

		String decodeResponse = this.transformOperations.decode("myrole", ciphertext.getCiphertext(), transformRequest);
		assertThat(decodeResponse).isEqualTo(targetValue);
	}

	@Test
	void encodeAndDecodeDoesNotYieldStartingResultWithDifferentTweakValueProvided() {

		VaultTransformContext transformRequest = VaultTransformContext.builder()
				.transformation("myssn")
				.tweak("somenum".getBytes())
				.build();
		String targetValue = "123-45-6789";

		TransformCiphertext ciphertext = this.transformOperations.encode("myrole", targetValue.getBytes(),
				transformRequest);
		assertThat(ciphertext.getCiphertext()).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}").isNotEqualTo(targetValue);

		VaultTransformContext otherDecodeRequest = VaultTransformContext.builder()
				.transformation("myssn")
				.tweak("numsome".getBytes())
				.build();

		String decodeResponse = this.transformOperations.decode("myrole", ciphertext.getCiphertext(),
				otherDecodeRequest);
		assertThat(decodeResponse).isNotEqualTo(targetValue);
	}

	@Test
	void encodeAndDecodeWithoutContextWorksForGeneratedTweakSource() {

		VaultTransformContext transformRequest = VaultTransformContext.builder().transformation("generatedssn").build();

		TransformCiphertext ciphertext = this.transformOperations.encode("generatedrole", "123-45-6789".getBytes(),
				transformRequest);

		TransformPlaintext plaintext = this.transformOperations.decode("generatedrole", ciphertext);
		assertThat(plaintext.asString()).isEqualTo("123-45-6789");
	}

	@Test
	void encodeAndDecodeWithoutContextWorksForInternalTweakSource() {

		String targetValue = "123-45-6789";

		String response = this.transformOperations.encode("internalrole", targetValue);
		assertThat(response).matches("[0-9]{3}-[0-9]{2}-[0-9]{4}").isNotEqualTo(targetValue);

		String decodeResponse = this.transformOperations.decode("internalrole", response);
		assertThat(decodeResponse).isEqualTo(targetValue);
	}

	@Test
	void batchEncodeAndDecodeYieldsStartingResults() {

		VaultTransformContext transformRequest = VaultTransformContext.builder()
				.transformation("myssn")
				.tweak("somenum".getBytes())
				.build();

		List<String> ssns = Arrays.asList("123-01-4567", "123-02-4567", "123-03-4567", "123-04-4567", "123-05-4567");

		List<VaultTransformEncodeResult> encoded = this.transformOperations.encode("myrole",
				ssns.stream()
						.map(TransformPlaintext::of)
						.map(plaintext -> plaintext.with(transformRequest))
						.collect(Collectors.toList()));

		List<VaultTransformDecodeResult> decoded = this.transformOperations.decode("myrole",
				encoded.stream()
						.map(VaultTransformEncodeResult::getAsString)
						.map(TransformCiphertext::of)
						.map(ciphertext -> ciphertext.with(transformRequest))
						.collect(Collectors.toList()));

		for (int i = 0; i < decoded.size(); i++) {
			assertThat(decoded.get(i).getAsString()).isEqualTo(ssns.get(i));
		}
	}

	@Test
	void batchEncodeAndDecodeYieldsStartingResultsForInternalWithNoContext() {

		List<String> ssns = Arrays.asList("123-01-4567", "123-02-4567", "123-03-4567", "123-04-4567", "123-05-4567");

		List<VaultTransformEncodeResult> encoded = this.transformOperations.encode("internalrole",
				ssns.stream().map(TransformPlaintext::of).collect(Collectors.toList()));

		List<VaultTransformDecodeResult> decoded = this.transformOperations.decode("internalrole",
				encoded.stream()
						.map(VaultTransformEncodeResult::getAsString)
						.map(TransformCiphertext::of)
						.collect(Collectors.toList()));

		for (int i = 0; i < decoded.size(); i++) {
			assertThat(decoded.get(i).getAsString()).isEqualTo(ssns.get(i));
		}
	}

	@Test
	void batchEncodeAndDecodeWithReference() {

		// Prepare test data
		List<TransformPlaintext> batch = new ArrayList<>();
		batch.add(TransformPlaintext.of("123-45-6789")
				.with(VaultTransformContext.builder().transformation("myssn").reference("ref-1").build()));
		batch.add(TransformPlaintext.of("234-56-7890")
				.with(VaultTransformContext.builder().transformation("myssn").reference("ref-2").build()));

		// Encode
		List<VaultTransformEncodeResult> encodeResults = transformOperations.encode("myrole", batch);

		// Verify encode results
		assertThat(encodeResults).hasSize(2);
		assertThat(encodeResults.get(0).isSuccessful()).isTrue();
		assertThat(encodeResults.get(1).isSuccessful()).isTrue();

		// Prepare decode batch
		List<TransformCiphertext> ciphertexts = new ArrayList<>();
		ciphertexts.add(encodeResults.get(0).get());
		ciphertexts.add(encodeResults.get(1).get());

		// Decode
		List<VaultTransformDecodeResult> decodeResults = transformOperations.decode("myrole", ciphertexts);

		// Verify decode results
		assertThat(decodeResults).hasSize(2);
		assertThat(decodeResults.get(0).get().asString()).isEqualTo("123-45-6789");
		assertThat(decodeResults.get(1).get().asString()).isEqualTo("234-56-7890");
		assertThat(decodeResults.get(0).get().getContext().getReference()).isEqualTo("ref-1");
		assertThat(decodeResults.get(1).get().getContext().getReference()).isEqualTo("ref-2");
	}

}
