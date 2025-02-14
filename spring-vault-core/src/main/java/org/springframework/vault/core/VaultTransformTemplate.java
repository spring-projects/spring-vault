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
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.Assert;
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

		return (String) this.vaultOperations.write("%s/encode/%s".formatted(this.path, roleName), request)
			.getRequiredData()
			.get("encoded_value");
	}

	@Override
	public TransformCiphertext encode(String roleName, TransformPlaintext plaintext) {

		Assert.hasText(roleName, "Role name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("value", plaintext.asString());

		applyTransformOptions(plaintext.getContext(), request);

		Map<String, Object> data = this.vaultOperations.write("%s/encode/%s".formatted(this.path, roleName), request)
			.getRequiredData();

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

		VaultResponse vaultResponse = this.vaultOperations.write("%s/encode/%s".formatted(this.path, roleName),
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

		return (String) this.vaultOperations.write("%s/decode/%s".formatted(this.path, roleName), request)
			.getRequiredData()
			.get("decoded_value");
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

		VaultResponse vaultResponse = this.vaultOperations.write("%s/decode/%s".formatted(this.path, roleName),
				Collections.singletonMap("batch_input", batch));

		return toDecryptionResults(vaultResponse, batchRequest);
	}

	private static void applyTransformOptions(VaultTransformContext context, Map<String, String> request) {

		if (!ObjectUtils.isEmpty(context.getTransformation())) {
			request.put("transformation", context.getTransformation());
		}

		if (!ObjectUtils.isEmpty(context.getTweak())) {
			request.put("tweak", Base64.getEncoder().encodeToString(context.getTweak()));
		}
		// NEW: pass "reference" in each item, if present
		if (StringUtils.hasText(context.getReference())) {
			request.put("reference", context.getReference());
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

			VaultTransformDecodeResult decodeResult; // Renamed from "encrypted"
			TransformCiphertext ciphertext = batchRequest.get(i);

			if (batchData.size() > i) {
				decodeResult = getDecryptionResult(batchData.get(i), ciphertext);
			}
			else {
				decodeResult = new VaultTransformDecodeResult(new VaultException("No result for ciphertext #" + i));
			}

			result.add(decodeResult);
		}

		return result;
	}

	private static VaultTransformDecodeResult getDecryptionResult(Map<String, String> data,
			TransformCiphertext ciphertext) {

		if (StringUtils.hasText(data.get("error"))) {
			return new VaultTransformDecodeResult(new VaultException(data.get("error")));
		}

		if (StringUtils.hasText(data.get("decoded_value"))) {

			// 1. Read reference from Vault's response (if present).
			String returnedRef = data.get("reference");

			// 2. Build an updated context that merges the existing transformation/tweak
			// with the newly-returned reference. If no reference is returned, keep the
			// old one. Note:- Relying on reference from originalContext is aimed at
			// providing a
			// fallback strategy, if vault does not return the reference, in any
			// circumstance.
			VaultTransformContext originalContext = ciphertext.getContext();
			VaultTransformContext updatedContext = VaultTransformContext.builder()
				.transformation(originalContext.getTransformation())
				.tweak(originalContext.getTweak())
				.reference(returnedRef != null ? returnedRef : originalContext.getReference())
				.build();

			// 3. Attach that updated context to the newly decoded plaintext.
			TransformPlaintext decodedPlaintext = TransformPlaintext.of(data.get("decoded_value")).with(updatedContext);

			return new VaultTransformDecodeResult(decodedPlaintext);

			// return new VaultTransformDecodeResult(
			// TransformPlaintext.of(data.get("decoded_value")).with(ciphertext.getContext()));
		}

		return new VaultTransformDecodeResult(TransformPlaintext.empty().with(ciphertext.getContext()));
	}

	private static TransformCiphertext toCiphertext(Map<String, ?> data, VaultTransformContext context) {

		String ciphertext = (String) data.get("encoded_value");

		// if Vault returns "reference" in batch_results,capturing it for co-relation.
		String returnedRef = (String) data.get("reference");

		VaultTransformContext contextToUse = context;
		if (data.containsKey("tweak")) {
			byte[] tweak = Base64.getDecoder().decode((String) data.get("tweak"));
			contextToUse = VaultTransformContext.builder()
				.transformation(context.getTransformation())
				.tweak(tweak)
				.reference(returnedRef != null ? returnedRef : context.getReference())
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
