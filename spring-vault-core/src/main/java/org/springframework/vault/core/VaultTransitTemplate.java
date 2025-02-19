/*
 * Copyright 2016-2025 the original author or authors.
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
import java.util.Objects;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.*;

/**
 * Default implementation of {@link VaultTransitOperations}.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 * @author Praveendra Singh
 * @author Luander Ribeiro
 * @author Mikko Koli
 * @author My-Lan Aragon
 * @author Nanne Baars
 */
public class VaultTransitTemplate implements VaultTransitOperations {

	private final VaultOperations vaultOperations;

	private final String path;

	/**
	 * Create a new {@link VaultTransitTemplate} given {@link VaultOperations} and the
	 * mount {@code path}.
	 * @param vaultOperations must not be {@literal null}.
	 * @param path must not be empty or {@literal null}.
	 */
	public VaultTransitTemplate(VaultOperations vaultOperations, String path) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null");
		Assert.hasText(path, "Path must not be empty");

		this.vaultOperations = vaultOperations;
		this.path = path;
	}

	@Override
	public void createKey(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		writeForData("%s/keys/%s".formatted(this.path, keyName), null);
	}

	@Override
	public void createKey(String keyName, VaultTransitKeyCreationRequest createKeyRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(createKeyRequest, "VaultTransitKeyCreationRequest must not be empty");

		writeForData("%s/keys/%s".formatted(this.path, keyName), createKeyRequest);
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public List<String> getKeys() {

		VaultResponse response = this.vaultOperations.read("%s/keys?list=true".formatted(this.path));

		return response == null ? Collections.emptyList()
				: (List) response.getRequiredData().getOrDefault("keys", Collections.emptyList());
	}

	@Override
	public void configureKey(String keyName, VaultTransitKeyConfiguration keyConfiguration) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(keyConfiguration, "VaultKeyConfiguration must not be empty");

		writeForData("%s/keys/%s/config".formatted(this.path, keyName), keyConfiguration);
	}

	@Override
	@Nullable
	public RawTransitKey exportKey(String keyName, TransitKeyType type) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(type, "Key type must not be null");

		VaultResponseSupport<RawTransitKeyImpl> result = this.vaultOperations
			.read("%s/export/%s/%s".formatted(this.path, type.getValue(), keyName), RawTransitKeyImpl.class);

		return result != null ? result.getRequiredData() : null;
	}

	@Override
	@Nullable
	public VaultTransitKey getKey(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		VaultResponseSupport<VaultTransitKeyImpl> result = this.vaultOperations
			.read("%s/keys/%s".formatted(this.path, keyName), VaultTransitKeyImpl.class);

		if (result != null) {
			return result.getRequiredData();
		}

		return null;
	}

	@Override
	public void deleteKey(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		this.vaultOperations.delete("%s/keys/%s".formatted(this.path, keyName));
	}

	@Override
	public void rotate(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		writeForData("%s/keys/%s/rotate".formatted(this.path, keyName), null);
	}

	@Override
	@SuppressWarnings("NullAway")
	public String encrypt(String keyName, String plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("plaintext", Base64.getEncoder().encodeToString(plaintext.getBytes()));

		return (String) writeForData("%s/encrypt/%s".formatted(this.path, keyName), request).get("ciphertext");
	}

	@Override
	public Ciphertext encrypt(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		String ciphertext = encrypt(keyName, plaintext.getPlaintext(), plaintext.getContext());

		return toCiphertext(ciphertext, plaintext.getContext());
	}

	@Override
	@SuppressWarnings("NullAway")
	public String encrypt(String keyName, byte[] plaintext, VaultTransitContext transitContext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");
		Assert.notNull(transitContext, "VaultTransitContext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("plaintext", Base64.getEncoder().encodeToString(plaintext));

		applyTransitOptions(transitContext, request);

		return (String) writeForData("%s/encrypt/%s".formatted(this.path, keyName), request).get("ciphertext");
	}

	@Override
	public List<VaultEncryptionResult> encrypt(String keyName, List<Plaintext> batchRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		List<Map<String, String>> batch = new ArrayList<>(batchRequest.size());

		for (Plaintext request : batchRequest) {

			Map<String, String> vaultRequest = new LinkedHashMap<>(2);

			vaultRequest.put("plaintext", Base64.getEncoder().encodeToString(request.getPlaintext()));
			applyTransitOptions(request.getContext(), vaultRequest);

			batch.add(vaultRequest);
		}

		VaultResponse vaultResponse = writeForResponse("%s/encrypt/%s".formatted(this.path, keyName),
				Collections.singletonMap("batch_input", batch));

		return toBatchResults(vaultResponse, batchRequest, Plaintext::getContext);
	}

	@Override
	public String decrypt(String keyName, String ciphertext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("ciphertext", ciphertext);

		String plaintext = (String) writeForData("%s/decrypt/%s".formatted(this.path, keyName), request)
			.get("plaintext");

		return new String(Base64.getDecoder().decode(plaintext));
	}

	@Override
	public Plaintext decrypt(String keyName, Ciphertext ciphertext) {

		Assert.hasText(keyName, "Key name must not be null");
		Assert.notNull(ciphertext, "Ciphertext must not be null");

		byte[] plaintext = decrypt(keyName, ciphertext.getCiphertext(), ciphertext.getContext());

		return Plaintext.of(plaintext).with(ciphertext.getContext());
	}

	@Override
	public byte[] decrypt(String keyName, String ciphertext, VaultTransitContext transitContext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");
		Assert.notNull(transitContext, "VaultTransitContext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("ciphertext", ciphertext);

		applyTransitOptions(transitContext, request);

		String plaintext = (String) writeForData("%s/decrypt/%s".formatted(this.path, keyName), request)
			.get("plaintext");

		return Base64.getDecoder().decode(plaintext);
	}

	@Override
	public List<VaultDecryptionResult> decrypt(String keyName, List<Ciphertext> batchRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		List<Map<String, String>> batch = new ArrayList<>(batchRequest.size());

		for (Ciphertext request : batchRequest) {

			Map<String, String> vaultRequest = new LinkedHashMap<>(2);

			vaultRequest.put("ciphertext", request.getCiphertext());
			applyTransitOptions(request.getContext(), vaultRequest);

			batch.add(vaultRequest);
		}

		VaultResponse vaultResponse = writeForResponse("%s/decrypt/%s".formatted(this.path, keyName),
				Collections.singletonMap("batch_input", batch));

		return toDecryptionResults(vaultResponse, batchRequest);
	}

	@Override
	@SuppressWarnings("NullAway")
	public String rewrap(String keyName, String ciphertext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");

		Map<String, String> request = new LinkedHashMap<>();
		request.put("ciphertext", ciphertext);

		return (String) writeForData("%s/rewrap/%s".formatted(this.path, keyName), request).get("ciphertext");
	}

	@Override
	@SuppressWarnings("NullAway")
	public String rewrap(String keyName, String ciphertext, VaultTransitContext transitContext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");
		Assert.notNull(transitContext, "VaultTransitContext must not be null");

		Map<String, String> request = createRewrapRequest(toCiphertext(ciphertext, transitContext));

		return (String) writeForData("%s/rewrap/%s".formatted(this.path, keyName), request).get("ciphertext");
	}

	@Override
	public List<VaultEncryptionResult> rewrap(String keyName, List<Ciphertext> batchRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		List<Map<String, String>> batch = new ArrayList<>(batchRequest.size());

		for (Ciphertext request : batchRequest) {

			Map<String, String> vaultRequest = createRewrapRequest(request);
			batch.add(vaultRequest);
		}

		VaultResponse vaultResponse = writeForResponse("%s/rewrap/%s".formatted(this.path, keyName),
				Collections.singletonMap("batch_input", batch));

		return toBatchResults(vaultResponse, batchRequest, Ciphertext::getContext);
	}

	@Override
	public Hmac getHmac(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		VaultHmacRequest request = VaultHmacRequest.create(plaintext);

		return getHmac(keyName, request);
	}

	@Override
	@SuppressWarnings("NullAway")
	public Hmac getHmac(String keyName, VaultHmacRequest hmacRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(hmacRequest, "HMAC request must not be null");

		Map<String, Object> request = toRequestBody(hmacRequest);

		String hmac = (String) writeForData("%s/hmac/%s".formatted(this.path, keyName), request).get("hmac");

		return Hmac.of(hmac);
	}

	static Map<String, Object> toRequestBody(VaultHmacRequest hmacRequest) {

		Map<String, Object> request = new LinkedHashMap<>(3);
		PropertyMapper mapper = PropertyMapper.get();

		mapper.from(hmacRequest.getPlaintext()::getPlaintext)
			.as(Base64.getEncoder()::encodeToString)
			.to("input", request);
		mapper.from(hmacRequest::getAlgorithm).whenHasText().to("algorithm", request);
		mapper.from(hmacRequest::getKeyVersion).whenNonNull().to("key_version", request);

		return request;
	}

	@Override
	public Signature sign(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		VaultSignRequest request = VaultSignRequest.create(plaintext);

		return sign(keyName, request);
	}

	@Override
	@SuppressWarnings("NullAway")
	public Signature sign(String keyName, VaultSignRequest signRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(signRequest, "Sign request must not be null");

		Map<String, Object> request = toRequestBody(signRequest);

		String signature = (String) writeForData("%s/sign/%s".formatted(this.path, keyName), request).get("signature");

		return Signature.of(signature);
	}

	static Map<String, Object> toRequestBody(VaultSignRequest signRequest) {

		Map<String, Object> request = new LinkedHashMap<>(4);
		PropertyMapper mapper = PropertyMapper.get();

		mapper.from(signRequest.getPlaintext()::getPlaintext)
			.as(Base64.getEncoder()::encodeToString)
			.to("input", request);
		mapper.from(signRequest::getHashAlgorithm).whenHasText().to("hash_algorithm", request);
		mapper.from(signRequest::getSignatureAlgorithm).whenHasText().to("signature_algorithm", request);
		mapper.from(signRequest::isPrehashed).to("prehashed", request);

		return request;
	}

	@Override
	public boolean verify(String keyName, Plaintext plainText, Signature signature) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plainText, "Plaintext must not be null");
		Assert.notNull(signature, "Signature must not be null");

		VaultSignatureVerificationRequest request = VaultSignatureVerificationRequest.create(plainText, signature);
		return verify(keyName, request).isValid();
	}

	@Override
	public SignatureValidation verify(String keyName, VaultSignatureVerificationRequest verificationRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(verificationRequest, "Signature verification request must not be null");

		Map<String, Object> request = toRequestBody(verificationRequest);

		Map<String, Object> response = writeForData("%s/verify/%s".formatted(this.path, keyName), request);

		if (response.containsKey("valid") && Boolean.valueOf("" + response.get("valid"))) {
			return SignatureValidation.valid();
		}

		return SignatureValidation.invalid();
	}

	private Map<String, Object> writeForData(String path, @Nullable Object request) {
		return writeForResponse(path, request).getRequiredData();
	}

	private VaultResponse writeForResponse(String path, @Nullable Object request) {

		VaultResponse response = this.vaultOperations.write(path, request);

		if (response == null) {
			throw new IllegalStateException("Write to '%s' did not return a response".formatted(path));
		}

		return response;
	}

	static Map<String, Object> toRequestBody(VaultSignatureVerificationRequest verificationRequest) {

		Map<String, Object> request = new LinkedHashMap<>(5);
		PropertyMapper mapper = PropertyMapper.get();

		mapper.from(verificationRequest.getPlaintext()::getPlaintext)
			.as(Base64.getEncoder()::encodeToString)
			.to("input", request);
		mapper.from(verificationRequest::getHmac).whenNonNull().as(Hmac::getHmac).to("hmac", request);
		mapper.from(verificationRequest::getSignature)
			.whenNonNull()
			.as(Signature::getSignature)
			.to("signature", request);
		mapper.from(verificationRequest::getHashAlgorithm).whenHasText().to("hash_algorithm", request);
		mapper.from(verificationRequest::getSignatureAlgorithm).whenHasText().to("signature_algorithm", request);
		mapper.from(verificationRequest::isPrehashed).to("prehashed", request);

		return request;
	}

	static void applyTransitOptions(VaultTransitContext context, Map<String, String> request) {

		if (!ObjectUtils.isEmpty(context.getContext())) {
			request.put("context", Base64.getEncoder().encodeToString(context.getContext()));
		}

		if (!ObjectUtils.isEmpty(context.getNonce())) {
			request.put("nonce", Base64.getEncoder().encodeToString(context.getNonce()));
		}

		if (context.getKeyVersion() != 0) {
			request.put("key_version", "" + context.getKeyVersion());
		}
	}

	@SuppressWarnings("NullAway")
	static <T> List<VaultEncryptionResult> toBatchResults(VaultResponse vaultResponse, List<T> batchRequests,
			Function<T, VaultTransitContext> contextExtractor) {

		List<VaultEncryptionResult> result = new ArrayList<>(batchRequests.size());
		List<Map<String, String>> batchData = getBatchData(vaultResponse);

		for (int i = 0; i < batchRequests.size(); i++) {

			VaultEncryptionResult encrypted;
			T request = batchRequests.get(i);
			if (batchData.size() > i) {

				Map<String, String> data = batchData.get(i);
				if (StringUtils.hasText(data.get("error"))) {
					encrypted = new VaultEncryptionResult(new VaultException(data.get("error")));
				}
				else {
					encrypted = new VaultEncryptionResult(
							toCiphertext(data.get("ciphertext"), contextExtractor.apply(request)));
				}
			}
			else {
				encrypted = new VaultEncryptionResult(new VaultException("No result for request #" + i));
			}

			result.add(encrypted);
		}

		return result;
	}

	static List<VaultDecryptionResult> toDecryptionResults(VaultResponse vaultResponse, List<Ciphertext> batchRequest) {

		List<VaultDecryptionResult> result = new ArrayList<>(batchRequest.size());
		List<Map<String, String>> batchData = getBatchData(vaultResponse);

		for (int i = 0; i < batchRequest.size(); i++) {

			VaultDecryptionResult encrypted;
			Ciphertext ciphertext = batchRequest.get(i);

			if (batchData.size() > i) {
				encrypted = getDecryptionResult(batchData.get(i), ciphertext);
			}
			else {
				encrypted = new VaultDecryptionResult(new VaultException("No result for ciphertext #" + i));
			}

			result.add(encrypted);
		}

		return result;
	}

	private static VaultDecryptionResult getDecryptionResult(Map<String, String> data, Ciphertext ciphertext) {

		if (StringUtils.hasText(data.get("error"))) {
			return new VaultDecryptionResult(new VaultException(data.get("error")));
		}

		if (StringUtils.hasText(data.get("plaintext"))) {

			byte[] plaintext = Base64.getDecoder().decode(data.get("plaintext"));
			return new VaultDecryptionResult(Plaintext.of(plaintext).with(ciphertext.getContext()));
		}

		return new VaultDecryptionResult(Plaintext.empty().with(ciphertext.getContext()));
	}

	static Map<String, String> createRewrapRequest(Ciphertext request) {

		Map<String, String> vaultRequest = new LinkedHashMap<>(2);
		vaultRequest.put("ciphertext", request.getCiphertext());
		applyTransitOptions(request.getContext(), vaultRequest);

		return vaultRequest;
	}

	static Ciphertext toCiphertext(String ciphertext, @Nullable VaultTransitContext context) {
		return context != null ? Ciphertext.of(ciphertext).with(context) : Ciphertext.of(ciphertext);
	}

	@SuppressWarnings({ "NullAway", "unchecked" })
	static List<Map<String, String>> getBatchData(VaultResponse vaultResponse) {
		return (List<Map<String, String>>) vaultResponse.getRequiredData().get("batch_results");
	}

	@SuppressWarnings("NullAway")
	static class VaultTransitKeyImpl implements VaultTransitKey {

		@Nullable
		private String name;

		@JsonProperty("type")
		@Nullable
		private String type;

		@JsonProperty("allow_plaintext_backup")
		private boolean allowPlaintextBackup;

		@JsonProperty("cipher_mode")
		private String cipherMode = "";

		@JsonProperty("convergent_encryption_version")
		private int convergentVersion;

		@JsonProperty("deletion_allowed")
		private boolean deletionAllowed;

		private boolean derived;

		private boolean exportable;

		private Map<String, Object> keys = Collections.emptyMap();

		@JsonProperty("latest_version")
		private int latestVersion;

		@JsonProperty("min_decryption_version")
		private int minDecryptionVersion;

		@JsonProperty("min_encryption_version")
		private int minEncryptionVersion;

		@JsonProperty("convergent_encryption")
		private boolean supportsConvergentEncryption;

		@JsonProperty("supports_decryption")
		private boolean supportsDecryption;

		@JsonProperty("supports_derivation")
		private boolean supportsDerivation;

		@JsonProperty("supports_encryption")
		private boolean supportsEncryption;

		@JsonProperty("supports_signing")
		private boolean supportsSigning;

		public VaultTransitKeyImpl() {
		}

		@Override
		public String getName() {
			return this.name;
		}

		public void setName(@Nullable String name) {
			this.name = name;
		}

		@Override
		public String getType() {

			if (this.type != null) {
				return this.type;
			}

			return this.cipherMode;
		}

		public void setType(@Nullable String type) {
			this.type = type;
		}

		@Override
		public boolean allowPlaintextBackup() {
			return isAllowPlaintextBackup();
		}

		public boolean isAllowPlaintextBackup() {
			return this.allowPlaintextBackup;
		}

		public String getCipherMode() {
			return this.cipherMode;
		}

		public void setCipherMode(String cipherMode) {
			this.cipherMode = cipherMode;
		}

		@Override
		public int getConvergentVersion() {
			return this.convergentVersion;
		}

		@Override
		public boolean isDeletionAllowed() {
			return this.deletionAllowed;
		}

		public void setDeletionAllowed(boolean deletionAllowed) {
			this.deletionAllowed = deletionAllowed;
		}

		@Override
		public boolean isDerived() {
			return this.derived;
		}

		public void setDerived(boolean derived) {
			this.derived = derived;
		}

		@Override
		public boolean isExportable() {
			return this.exportable;
		}

		public void setExportable(boolean exportable) {
			this.exportable = exportable;
		}

		@Override
		public Map<String, Object> getKeys() {
			return this.keys;
		}

		public void setKeys(Map<String, Object> keys) {
			this.keys = keys;
		}

		@Override
		public int getLatestVersion() {
			return this.latestVersion;
		}

		public void setLatestVersion(int latestVersion) {
			this.latestVersion = latestVersion;
		}

		@Override
		public int getMinDecryptionVersion() {
			return this.minDecryptionVersion;
		}

		public void setMinDecryptionVersion(int minDecryptionVersion) {
			this.minDecryptionVersion = minDecryptionVersion;
		}

		public void setSupportsEncryption(boolean supportsEncryption) {
			this.supportsEncryption = supportsEncryption;
		}

		@Override
		public int getMinEncryptionVersion() {
			return this.minEncryptionVersion;
		}

		public void setMinEncryptionVersion(int minEncryptionVersion) {
			this.minEncryptionVersion = minEncryptionVersion;
		}

		public boolean isSupportsConvergentEncryption() {
			return this.supportsConvergentEncryption;
		}

		@Override
		public boolean supportsConvergentEncryption() {
			return isSupportsConvergentEncryption();
		}

		public boolean isSupportsDecryption() {
			return this.supportsDecryption;
		}

		@Override
		public boolean supportsDecryption() {
			return isSupportsDecryption();
		}

		public void setSupportsDecryption(boolean supportsDecryption) {
			this.supportsDecryption = supportsDecryption;
		}

		public boolean isSupportsEncryption() {
			return this.supportsEncryption;
		}

		@Override
		public boolean supportsEncryption() {
			return isSupportsEncryption();
		}

		@Override
		public boolean supportsDerivation() {
			return isSupportsDerivation();
		}

		public boolean isSupportsDerivation() {
			return this.supportsDerivation;
		}

		public void setSupportsDerivation(boolean supportsDerivation) {
			this.supportsDerivation = supportsDerivation;
		}

		public boolean isSupportsSigning() {
			return this.supportsSigning;
		}

		public void setSupportsSigning(boolean supportsSigning) {
			this.supportsSigning = supportsSigning;
		}

		@Override
		public boolean supportsSigning() {
			return isSupportsSigning();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof VaultTransitKeyImpl that))
				return false;
			return this.allowPlaintextBackup == that.allowPlaintextBackup
					&& this.deletionAllowed == that.deletionAllowed && this.derived == that.derived
					&& this.exportable == that.exportable && this.latestVersion == that.latestVersion
					&& this.minDecryptionVersion == that.minDecryptionVersion
					&& this.minEncryptionVersion == that.minEncryptionVersion
					&& this.supportsDecryption == that.supportsDecryption
					&& this.supportsEncryption == that.supportsEncryption
					&& this.supportsDerivation == that.supportsDerivation
					&& this.supportsSigning == that.supportsSigning && Objects.equals(this.name, that.name)
					&& this.cipherMode.equals(that.cipherMode) && Objects.equals(this.type, that.type)
					&& this.supportsConvergentEncryption == that.supportsConvergentEncryption;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.allowPlaintextBackup, this.name, this.cipherMode, this.type, this.deletionAllowed,
					this.derived, this.exportable, this.keys, this.latestVersion, this.minDecryptionVersion,
					this.minEncryptionVersion, this.supportsDecryption, this.supportsEncryption,
					this.supportsDerivation, this.supportsSigning, this.supportsConvergentEncryption);
		}

	}

	static class RawTransitKeyImpl implements RawTransitKey {

		private Map<String, String> keys = Collections.emptyMap();

		private final String name;

		public RawTransitKeyImpl(@JsonProperty("name") String name) {
			this.name = name;
		}

		@Override
		public Map<String, String> getKeys() {
			return this.keys;
		}

		@Override
		public String getName() {
			return this.name;
		}

		public void setKeys(Map<String, String> keys) {
			this.keys = keys;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof RawTransitKeyImpl that))
				return false;
			return this.keys.equals(that.keys) && Objects.equals(this.name, that.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.keys, this.name);
		}

	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(getClass().getSimpleName());
		sb.append(" [vaultOperations=").append(this.vaultOperations);
		sb.append(", path='").append(this.path).append('\'');
		sb.append(']');
		return sb.toString();
	}

}
