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
package org.springframework.vault.support;

import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.KeySpec;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.asn1.x9.X962NamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;

/**
 * Implements an elliptic curve strategy for private key support.
 *
 * @author Alex Bremora
 * @since 2.4
 */
class EcPrivateKeyStrategy implements PrivateKeyStrategy {

  @Override
  public String getName() {
    return "ec";
  }

  @Override
  public KeySpec getKeySpec(byte[] privateKey) {
    ECPrivateKey ecPrivateKey = ECPrivateKey.getInstance(privateKey);
    ASN1ObjectIdentifier parameters = (ASN1ObjectIdentifier) ecPrivateKey.getParameters();

    X9ECParameters curveParameter = X962NamedCurves.getByOID(parameters);

    EllipticCurve ellipticCurve = new EllipticCurve(
        new ECFieldFp(curveParameter.getCurve().getField().getCharacteristic()),
        curveParameter.getCurve().getA().toBigInteger(), curveParameter.getCurve().getB().toBigInteger());

    ECPoint gPoint = new ECPoint(curveParameter.getG().getXCoord().toBigInteger(),
        curveParameter.getG().getYCoord().toBigInteger());

    ECParameterSpec paramSpec = new ECParameterSpec(ellipticCurve, gPoint, curveParameter.getN(),
        curveParameter.getH().intValue());

    return new ECPrivateKeySpec(ecPrivateKey.getKey(), paramSpec);
  }
}
