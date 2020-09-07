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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.TransformCiphertext;
import org.springframework.vault.support.TransformPlaintext;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultTransformContext;
import org.springframework.vault.support.VaultTransformDecodeResult;
import org.springframework.vault.support.VaultTransformEncodeResult;

/**
 * Default implementation of {@link VaultTransformOperations}.
 *
 * @author Lauren Voswinkel
 * @author Mark Paluch
 * @since 2.3
 */
public class VaultTransformTemplate implements VaultTransformOperations {

	private final VaultOperations vaultOperations;

	private final String path;

	/**
	 * Create a new {@link VaultTransformTemplate} given {@link VaultOperations} and the
	 * mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultTransformTemplate(VaultOperations vaultOperations, String path) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations = vaultOperations;
		this.path = path;
	}

	@Override
	public String encode(String roleName, String plaintext) {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("value", plaintext);

		return (String) this.vaultOperations.write(String.format("%s/encode/%s", this.path, roleName), request)
				.getRequiredData().get("encoded_value");
	}

	@Override
	public TransformCiphertext encode(String roleName, TransformPlaintext plaintext) {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("value", plaintext.asString());

		applyTransformOptions(plaintext.getContext(), request);

		Map<String, Object> data = this.vaultOperations
				.write(String.format("%s/encode/%s", this.path, roleName), request).getRequiredData();

		return toCiphertext(data, plaintext.getContext());
	}

	@Override
	public List<VaultTransformEncodeResult> encode(String roleName, List<TransformPlaintext> batchRequest) {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		List<Map<String, String>> batch = new ArrayList<>(batchRequest.size());

		for (TransformPlaintext request : batchRequest) {

			Map<String, String> vaultRequest = new LinkedHashMap<>(2);

			vaultRequest.put("value", request.asString());

			applyTransformOptions(request.getContext(), vaultRequest);

			batch.add(vaultRequest);
		}

		VaultResponse vaultResponse = this.vaultOperations.write(String.format("%s/encode/%s", this.path, roleName),
				Collections.singletonMap("batch_input", batch));

		return toEncodedResults(vaultResponse, batchRequest);
	}

	@Override
	public TransformPlaintext decode(String roleName, TransformCiphertext ciphertext) {

		Assert.hasText(roleName, "Role name must not be null");
		Assert.notNull(ciphertext, "Ciphertext must not be null");

		String plaintext = decode(roleName, ciphertext.getCiphertext(), ciphertext.getContext());

		return TransformPlaintext.of(plaintext).with(ciphertext.getContext());
	}

	@Override
	public String decode(String roleName, String ciphertext, VaultTransformContext transformContext) {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");
		Assert.notNull(transformContext, "VaultTransformContext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("value", ciphertext);

		applyTransformOptions(transformContext, request);

		return (String) this.vaultOperations.write(String.format("%s/decode/%s", this.path, roleName), request)
				.getRequiredData().get("decoded_value");
	}

	@Override
	public List<VaultTransformDecodeResult> decode(String roleName, List<TransformCiphertext> batchRequest) {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		List<Map<String, String>> batch = new ArrayList<>(batchRequest.size());

		for (TransformCiphertext request : batchRequest) {

			Map<String, String> vaultRequest = new LinkedHashMap<>(2);

			vaultRequest.put("value", request.getCiphertext());
			applyTransformOptions(request.getContext(), vaultRequest);

			batch.add(vaultRequest);
		}

		VaultResponse vaultResponse = this.vaultOperations.write(String.format("%s/decode/%s", this.path, roleName),
				Collections.singletonMap("batch_input", batch));

		return toDecryptionResults(vaultResponse, batchRequest);
	}

	private static void applyTransformOptions(VaultTransformContext context, Map<String, String> request) {

		if (!ObjectUtils.isEmpty(context.getTransformation())) {
			request.put("transformation", context.getTransformation());
		}

		if (!ObjectUtils.isEmpty(context.getTweak())) {
			request.put("tweak", Base64Utils.encodeToString(context.getTweak()));
		}
	}

	private static List<VaultTransformEncodeResult> toEncodedResults(VaultResponse vaultResponse,
			List<TransformPlaintext> batchRequest) {

		List<VaultTransformEncodeResult> result = new ArrayList<>(batchRequest.size());
		List<Map<String, String>> batchData = getBatchData(vaultResponse);

		for (int i = 0; i < batchRequest.size(); i++) {

			VaultTransformEncodeResult encoded;
			TransformPlaintext plaintext = batchRequest.get(i);
			if (batchData.size() > i) {

				Map<String, String> data = batchData.get(i);
				if (StringUtils.hasText(data.get("error"))) {
					encoded = new VaultTransformEncodeResult(new VaultException(data.get("error")));
				}
				else {
					encoded = new VaultTransformEncodeResult(toCiphertext(data, plaintext.getContext()));
				}
			}
			else {
				encoded = new VaultTransformEncodeResult(new VaultException("No result for plaintext #" + i));
			}

			result.add(encoded);
		}

		return result;
	}

	private static List<VaultTransformDecodeResult> toDecryptionResults(VaultResponse vaultResponse,
			List<TransformCiphertext> batchRequest) {

		List<VaultTransformDecodeResult> result = new ArrayList<>(batchRequest.size());
		List<Map<String, String>> batchData = getBatchData(vaultResponse);

		for (int i = 0; i < batchRequest.size(); i++) {

			VaultTransformDecodeResult encrypted;
			TransformCiphertext ciphertext = batchRequest.get(i);

			if (batchData.size() > i) {
				encrypted = getDecryptionResult(batchData.get(i), ciphertext);
			}
			else {
				encrypted = new VaultTransformDecodeResult(new VaultException("No result for ciphertext #" + i));
			}

			result.add(encrypted);
		}

		return result;
	}

	private static VaultTransformDecodeResult getDecryptionResult(Map<String, String> data,
			TransformCiphertext ciphertext) {

		if (StringUtils.hasText(data.get("error"))) {
			return new VaultTransformDecodeResult(new VaultException(data.get("error")));
		}

		if (StringUtils.hasText(data.get("decoded_value"))) {

			return new VaultTransformDecodeResult(
					TransformPlaintext.of(data.get("decoded_value")).with(ciphertext.getContext()));
		}

		return new VaultTransformDecodeResult(TransformPlaintext.empty().with(ciphertext.getContext()));
	}

	private static TransformCiphertext toCiphertext(Map<String, ?> data, VaultTransformContext context) {

		String ciphertext = (String) data.get("encoded_value");

		VaultTransformContext contextToUse = context;
		if (data.containsKey("tweak")) {
			byte[] tweak = Base64Utils.decodeFromString((String) data.get("tweak"));
			contextToUse = VaultTransformContext.builder().transformation(context.getTransformation()).tweak(tweak)
					.build();
		}

		return contextToUse.isEmpty() ? TransformCiphertext.of(ciphertext)
				: TransformCiphertext.of(ciphertext).with(contextToUse);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, String>> getBatchData(VaultResponse vaultResponse) {
		return (List<Map<String, String>>) vaultResponse.getRequiredData().get("batch_results");
	}

}
