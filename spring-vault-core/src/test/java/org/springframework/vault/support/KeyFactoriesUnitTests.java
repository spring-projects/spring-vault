/*
 * Copyright 2022 the original author or authors.
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
package org.springframework.vault.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x9.X962NamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link KeyFactories}.
 *
 * @author Mark Paluch
 */
class KeyFactoriesUnitTests {

	@Test
	void shouldCreateEcKey() throws IOException, GeneralSecurityException {

		PemObject key = load("privatekey-ec-256.pem", 1);

		KeySpec keySpec = KeyFactories.EC.getKey(key.getContent());

		assertThat(keySpec).isInstanceOf(ECPrivateKeySpec.class);

		ECPrivateKeySpec ecKeySpec = (ECPrivateKeySpec) keySpec;
		assertThat(ecKeySpec.getS())
			.isEqualTo("80321543313819895612774489145376520718294627432743956845606752593828296924959");

		// Verify against BouncyCastle parser
		ECPrivateKey ecPrivateKey = ECPrivateKey.getInstance(key.getContent());
		ASN1Object parameters = ecPrivateKey.getParametersObject();

		X9ECParameters curveParameter = X962NamedCurves.getByOID((ASN1ObjectIdentifier) parameters);

		assertThat(ecKeySpec.getParams().getCofactor()).isEqualTo(curveParameter.getCurve().getCofactor().intValue());

		assertThat(ecKeySpec.getParams().getOrder()).isEqualTo(curveParameter.getCurve().getOrder());
		assertThat(ecKeySpec.getParams().getCurve().getA()).isEqualTo(curveParameter.getCurve().getA().toBigInteger());
		assertThat(ecKeySpec.getParams().getCurve().getB()).isEqualTo(curveParameter.getCurve().getB().toBigInteger());
	}

	@Test
	void createKeySpecFromPemPrivateKeyRsa2048() throws Exception {

		PemObject key = load("privatekey-rsa-2048.pem", 0);
		KeySpec keySpec = KeyFactories.RSA_PRIVATE.getKey(key.getContent());

		assertThat(keySpec).isInstanceOf(RSAPrivateCrtKeySpec.class);
	}

	private PemObject load(String path, int index) throws IOException {

		ClassPathResource classPathResource = new ClassPathResource(path);

		try (InputStream is = classPathResource.getInputStream()) {

			List<PemObject> objects = PemObject.parse(StreamUtils.copyToString(is, StandardCharsets.US_ASCII));
			return objects.get(index);
		}
	}

}
