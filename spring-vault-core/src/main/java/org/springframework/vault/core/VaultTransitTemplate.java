/*
 * Copyright 2016-2022 the original author or authors.
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
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.Base64Utils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.vault.VaultException;
import org.springframework.vault.support.Ciphertext;
import org.springframework.vault.support.Hmac;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.RawTransitKey;
import org.springframework.vault.support.Signature;
import org.springframework.vault.support.SignatureValidation;
import org.springframework.vault.support.TransitKeyType;
import org.springframework.vault.support.VaultDecryptionResult;
import org.springframework.vault.support.VaultEncryptionResult;
import org.springframework.vault.support.VaultHmacRequest;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultSignRequest;
import org.springframework.vault.support.VaultSignatureVerificationRequest;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.VaultTransitKey;
import org.springframework.vault.support.VaultTransitKeyConfiguration;
import org.springframework.vault.support.VaultTransitKeyCreationRequest;

/**
 * Default implementation of {@link VaultTransitOperations}.
 *
 * @author Mark Paluch
 * @author Sven Schürmann
 * @author Praveendra Singh
 * @author Luander Ribeiro
 * @author Mikko Koli
 * @author My-Lan Aragon
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

		this.vaultOperations.write(String.format("%s/keys/%s", this.path, keyName), null);
	}

	@Override
	public void createKey(String keyName, VaultTransitKeyCreationRequest createKeyRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(createKeyRequest, "VaultTransitKeyCreationRequest must not be empty");

		this.vaultOperations.write(String.format("%s/keys/%s", this.path, keyName), createKeyRequest);
	}

	@Override
	public List<String> getKeys() {

		VaultResponse response = this.vaultOperations.read(String.format("%s/keys?list=true", this.path));

		return response == null ? Collections.emptyList() : (List) response.getRequiredData().get("keys");
	}

	@Override
	public void configureKey(String keyName, VaultTransitKeyConfiguration keyConfiguration) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(keyConfiguration, "VaultKeyConfiguration must not be empty");

		this.vaultOperations.write(String.format("%s/keys/%s/config", this.path, keyName), keyConfiguration);
	}

	@Override
	@Nullable
	public RawTransitKey exportKey(String keyName, TransitKeyType type) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(type, "Key type must not be null");

		VaultResponseSupport<RawTransitKeyImpl> result = this.vaultOperations
				.read(String.format("%s/export/%s/%s", this.path, type.getValue(), keyName), RawTransitKeyImpl.class);

		return result != null ? result.getRequiredData() : null;
	}

	@Override
	@Nullable
	public VaultTransitKey getKey(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		VaultResponseSupport<VaultTransitKeyImpl> result = this.vaultOperations
				.read(String.format("%s/keys/%s", this.path, keyName), VaultTransitKeyImpl.class);

		if (result != null) {
			return result.getRequiredData();
		}

		return null;
	}

	@Override
	public void deleteKey(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		this.vaultOperations.delete(String.format("%s/keys/%s", this.path, keyName));
	}

	@Override
	public void rotate(String keyName) {

		Assert.hasText(keyName, "Key name must not be empty");

		this.vaultOperations.write(String.format("%s/keys/%s/rotate", this.path, keyName), null);
	}

	@Override
	public String encrypt(String keyName, String plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("plaintext", Base64Utils.encodeToString(plaintext.getBytes()));

		return (String) this.vaultOperations.write(String.format("%s/encrypt/%s", this.path, keyName), request)
				.getRequiredData().get("ciphertext");
	}

	@Override
	public Ciphertext encrypt(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		String ciphertext = encrypt(keyName, plaintext.getPlaintext(), plaintext.getContext());

		return toCiphertext(ciphertext, plaintext.getContext());
	}

	@Override
	public String encrypt(String keyName, byte[] plaintext, VaultTransitContext transitContext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");
		Assert.notNull(transitContext, "VaultTransitContext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("plaintext", Base64Utils.encodeToString(plaintext));

		applyTransitOptions(transitContext, request);

		return (String) this.vaultOperations.write(String.format("%s/encrypt/%s", this.path, keyName), request)
				.getRequiredData().get("ciphertext");
	}

	@Override
	public List<VaultEncryptionResult> encrypt(String keyName, List<Plaintext> batchRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		List<Map<String, String>> batch = new ArrayList<>(batchRequest.size());

		for (Plaintext request : batchRequest) {

			Map<String, String> vaultRequest = new LinkedHashMap<>(2);

			vaultRequest.put("plaintext", Base64Utils.encodeToString(request.getPlaintext()));

			if (request.getContext() != null) {
				applyTransitOptions(request.getContext(), vaultRequest);
			}

			batch.add(vaultRequest);
		}

		VaultResponse vaultResponse = this.vaultOperations.write(String.format("%s/encrypt/%s", this.path, keyName),
				Collections.singletonMap("batch_input", batch));

		return toEncryptionResults(vaultResponse, batchRequest);
	}

	@Override
	public String decrypt(String keyName, String ciphertext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("ciphertext", ciphertext);

		String plaintext = (String) this.vaultOperations
				.write(String.format("%s/decrypt/%s", this.path, keyName), request).getRequiredData().get("plaintext");

		return new String(Base64Utils.decodeFromString(plaintext));
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

		String plaintext = (String) this.vaultOperations
				.write(String.format("%s/decrypt/%s", this.path, keyName), request).getRequiredData().get("plaintext");

		return Base64Utils.decodeFromString(plaintext);
	}

	@Override
	public List<VaultDecryptionResult> decrypt(String keyName, List<Ciphertext> batchRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notEmpty(batchRequest, "BatchRequest must not be null and must have at least one entry");

		List<Map<String, String>> batch = new ArrayList<>(batchRequest.size());

		for (Ciphertext request : batchRequest) {

			Map<String, String> vaultRequest = new LinkedHashMap<>(2);

			vaultRequest.put("ciphertext", request.getCiphertext());

			if (request.getContext() != null) {
				applyTransitOptions(request.getContext(), vaultRequest);
			}

			batch.add(vaultRequest);
		}

		VaultResponse vaultResponse = this.vaultOperations.write(String.format("%s/decrypt/%s", this.path, keyName),
				Collections.singletonMap("batch_input", batch));

		return toDecryptionResults(vaultResponse, batchRequest);
	}

	@Override
	public String rewrap(String keyName, String ciphertext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");

		Map<String, String> request = new LinkedHashMap<>();
		request.put("ciphertext", ciphertext);

		return (String) this.vaultOperations.write(String.format("%s/rewrap/%s", this.path, keyName), request)
				.getRequiredData().get("ciphertext");
	}

	@Override
	public String rewrap(String keyName, String ciphertext, VaultTransitContext transitContext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.hasText(ciphertext, "Ciphertext must not be empty");
		Assert.notNull(transitContext, "VaultTransitContext must not be null");

		Map<String, String> request = new LinkedHashMap<>();

		request.put("ciphertext", ciphertext);

		applyTransitOptions(transitContext, request);

		return (String) this.vaultOperations.write(String.format("%s/rewrap/%s", this.path, keyName), request)
				.getRequiredData().get("ciphertext");
	}

	@Override
	public Hmac getHmac(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		VaultHmacRequest request = VaultHmacRequest.create(plaintext);

		return getHmac(keyName, request);
	}

	@Override
	public Hmac getHmac(String keyName, VaultHmacRequest hmacRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(hmacRequest, "HMAC request must not be null");

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("input", Base64Utils.encodeToString(hmacRequest.getPlaintext().getPlaintext()));

		if (StringUtils.hasText(hmacRequest.getAlgorithm())) {
			request.put("algorithm", hmacRequest.getAlgorithm());
		}

		if (hmacRequest.getKeyVersion() != null) {
			request.put("key_version ", hmacRequest.getKeyVersion());
		}

		String hmac = (String) this.vaultOperations.write(String.format("%s/hmac/%s", this.path, keyName), request)
				.getRequiredData().get("hmac");

		return Hmac.of(hmac);
	}

	@Override
	public Signature sign(String keyName, Plaintext plaintext) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(plaintext, "Plaintext must not be null");

		VaultSignRequest request = VaultSignRequest.create(plaintext);

		return sign(keyName, request);
	}

	@Override
	public Signature sign(String keyName, VaultSignRequest signRequest) {

		Assert.hasText(keyName, "Key name must not be empty");
		Assert.notNull(signRequest, "Sign request must not be null");

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("input", Base64Utils.encodeToString(signRequest.getPlaintext().getPlaintext()));

		if (StringUtils.hasText(signRequest.getHashAlgorithm())) {
			request.put("hash_algorithm", signRequest.getHashAlgorithm());
		}

		if (StringUtils.hasText(signRequest.getSignatureAlgorithm())) {
			request.put("signature_algorithm", signRequest.getSignatureAlgorithm());
		}

		String signature = (String) this.vaultOperations.write(String.format("%s/sign/%s", this.path, keyName), request)
				.getRequiredData().get("signature");

		return Signature.of(signature);
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

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("input", Base64Utils.encodeToString(verificationRequest.getPlaintext().getPlaintext()));

		if (verificationRequest.getHmac() != null) {
			request.put("hmac", verificationRequest.getHmac().getHmac());
		}

		if (verificationRequest.getSignature() != null) {
			request.put("signature", verificationRequest.getSignature().getSignature());
		}

		if (StringUtils.hasText(verificationRequest.getHashAlgorithm())) {
			request.put("hash_algorithm", verificationRequest.getHashAlgorithm());
		}

		if (StringUtils.hasText(verificationRequest.getSignatureAlgorithm())) {
			request.put("signature_algorithm", verificationRequest.getSignatureAlgorithm());
		}

		Map<String, Object> response = this.vaultOperations
				.write(String.format("%s/verify/%s", this.path, keyName), request).getRequiredData();

		if (response.containsKey("valid") && Boolean.valueOf("" + response.get("valid"))) {
			return SignatureValidation.valid();
		}

		return SignatureValidation.invalid();
	}

	private static void applyTransitOptions(VaultTransitContext context, Map<String, String> request) {

		if (!ObjectUtils.isEmpty(context.getContext())) {
			request.put("context", Base64Utils.encodeToString(context.getContext()));
		}

		if (!ObjectUtils.isEmpty(context.getNonce())) {
			request.put("nonce", Base64Utils.encodeToString(context.getNonce()));
		}
	}

	private static List<VaultEncryptionResult> toEncryptionResults(VaultResponse vaultResponse,
			List<Plaintext> batchRequest) {

		List<VaultEncryptionResult> result = new ArrayList<>(batchRequest.size());
		List<Map<String, String>> batchData = getBatchData(vaultResponse);

		for (int i = 0; i < batchRequest.size(); i++) {

			VaultEncryptionResult encrypted;
			Plaintext plaintext = batchRequest.get(i);
			if (batchData.size() > i) {

				Map<String, String> data = batchData.get(i);
				if (StringUtils.hasText(data.get("error"))) {
					encrypted = new VaultEncryptionResult(new VaultException(data.get("error")));
				}
				else {
					encrypted = new VaultEncryptionResult(toCiphertext(data.get("ciphertext"), plaintext.getContext()));
				}
			}
			else {
				encrypted = new VaultEncryptionResult(new VaultException("No result for plaintext #" + i));
			}

			result.add(encrypted);
		}

		return result;
	}

	private static List<VaultDecryptionResult> toDecryptionResults(VaultResponse vaultResponse,
			List<Ciphertext> batchRequest) {

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

			byte[] plaintext = Base64Utils.decodeFromString(data.get("plaintext"));
			return new VaultDecryptionResult(Plaintext.of(plaintext).with(ciphertext.getContext()));
		}

		return new VaultDecryptionResult(Plaintext.empty().with(ciphertext.getContext()));
	}

	private static Ciphertext toCiphertext(String ciphertext, @Nullable VaultTransitContext context) {
		return context != null ? Ciphertext.of(ciphertext).with(context) : Ciphertext.of(ciphertext);
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, String>> getBatchData(VaultResponse vaultResponse) {
		return (List<Map<String, String>>) vaultResponse.getRequiredData().get("batch_results");
	}

	static class VaultTransitKeyImpl implements VaultTransitKey {

		@Nullable
		private String name;

		@JsonProperty("cipher_mode")
		private String cipherMode = "";

		@JsonProperty("type")
		@Nullable
		private String type;

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

		@JsonProperty("supports_decryption")
		private boolean supportsDecryption;

		@JsonProperty("supports_encryption")
		private boolean supportsEncryption;

		@JsonProperty("supports_derivation")
		private boolean supportsDerivation;

		@JsonProperty("supports_signing")
		private boolean supportsSigning;

		public VaultTransitKeyImpl() {
		}

		@Override
		public String getType() {

			if (this.type != null) {
				return this.type;
			}

			return this.cipherMode;
		}

		@Override
		public boolean supportsDecryption() {
			return isSupportsDecryption();
		}

		@Override
		public boolean supportsEncryption() {
			return isSupportsEncryption();
		}

		@Override
		public boolean supportsDerivation() {
			return isSupportsDerivation();
		}

		@Override
		public boolean supportsSigning() {
			return isSupportsSigning();
		}

		@Nullable
		public String getName() {
			return this.name;
		}

		public String getCipherMode() {
			return this.cipherMode;
		}

		public boolean isDeletionAllowed() {
			return this.deletionAllowed;
		}

		public boolean isDerived() {
			return this.derived;
		}

		public boolean isExportable() {
			return this.exportable;
		}

		public Map<String, Object> getKeys() {
			return this.keys;
		}

		public int getLatestVersion() {
			return this.latestVersion;
		}

		public int getMinDecryptionVersion() {
			return this.minDecryptionVersion;
		}

		public int getMinEncryptionVersion() {
			return this.minEncryptionVersion;
		}

		public boolean isSupportsDecryption() {
			return this.supportsDecryption;
		}

		public boolean isSupportsEncryption() {
			return this.supportsEncryption;
		}

		public boolean isSupportsDerivation() {
			return this.supportsDerivation;
		}

		public boolean isSupportsSigning() {
			return this.supportsSigning;
		}

		public void setName(@Nullable String name) {
			this.name = name;
		}

		public void setCipherMode(String cipherMode) {
			this.cipherMode = cipherMode;
		}

		public void setType(@Nullable String type) {
			this.type = type;
		}

		public void setDeletionAllowed(boolean deletionAllowed) {
			this.deletionAllowed = deletionAllowed;
		}

		public void setDerived(boolean derived) {
			this.derived = derived;
		}

		public void setExportable(boolean exportable) {
			this.exportable = exportable;
		}

		public void setKeys(Map<String, Object> keys) {
			this.keys = keys;
		}

		public void setLatestVersion(int latestVersion) {
			this.latestVersion = latestVersion;
		}

		public void setMinDecryptionVersion(int minDecryptionVersion) {
			this.minDecryptionVersion = minDecryptionVersion;
		}

		public void setMinEncryptionVersion(int minEncryptionVersion) {
			this.minEncryptionVersion = minEncryptionVersion;
		}

		public void setSupportsDecryption(boolean supportsDecryption) {
			this.supportsDecryption = supportsDecryption;
		}

		public void setSupportsEncryption(boolean supportsEncryption) {
			this.supportsEncryption = supportsEncryption;
		}

		public void setSupportsDerivation(boolean supportsDerivation) {
			this.supportsDerivation = supportsDerivation;
		}

		public void setSupportsSigning(boolean supportsSigning) {
			this.supportsSigning = supportsSigning;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof VaultTransitKeyImpl))
				return false;
			VaultTransitKeyImpl that = (VaultTransitKeyImpl) o;
			return this.deletionAllowed == that.deletionAllowed && this.derived == that.derived
					&& this.exportable == that.exportable && this.latestVersion == that.latestVersion
					&& this.minDecryptionVersion == that.minDecryptionVersion
					&& this.minEncryptionVersion == that.minEncryptionVersion
					&& this.supportsDecryption == that.supportsDecryption
					&& this.supportsEncryption == that.supportsEncryption
					&& this.supportsDerivation == that.supportsDerivation
					&& this.supportsSigning == that.supportsSigning && Objects.equals(this.name, that.name)
					&& this.cipherMode.equals(that.cipherMode) && Objects.equals(this.type, that.type)
					&& this.keys.equals(that.keys);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.name, this.cipherMode, this.type, this.deletionAllowed, this.derived,
					this.exportable, this.keys, this.latestVersion, this.minDecryptionVersion,
					this.minEncryptionVersion, this.supportsDecryption, this.supportsEncryption,
					this.supportsDerivation, this.supportsSigning);
		}

	}

	static class RawTransitKeyImpl implements RawTransitKey {

		private Map<String, String> keys = Collections.emptyMap();

		@Nullable
		private String name;

		public RawTransitKeyImpl() {
		}

		public Map<String, String> getKeys() {
			return this.keys;
		}

		@Nullable
		public String getName() {
			return this.name;
		}

		public void setKeys(Map<String, String> keys) {
			this.keys = keys;
		}

		public void setName(@Nullable String name) {
			this.name = name;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof RawTransitKeyImpl))
				return false;
			RawTransitKeyImpl that = (RawTransitKeyImpl) o;
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
