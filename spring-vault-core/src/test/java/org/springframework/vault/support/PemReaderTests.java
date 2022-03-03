/*
 * Copyright 2019-2022 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PemReader}.
 *
 * @author Alex Bremora
 */
public class PemReaderTests {

  @Test
  void parseNullReturnsEmptyList() {
    String input = null;

    List<PemItem> items = PemReader.parse(input);

    assertThat(items).hasSize(0);
  }

  @Test
  void parseEmptyStringReturnsEmptyList() {
    String input = "";

    List<PemItem> items = PemReader.parse(input);

    assertThat(items).hasSize(0);
  }

  @Test
  void parseValidPemReturnsOneItem() {
    String input = "-----BEGIN CERTIFICATE-----\nF00\n-----END CERTIFICATE-----";

    List<PemItem> items = PemReader.parse(input);

    assertThat(items).hasSize(1);
  }

  @Test
  void parseValidPemBundleCertificatePrivateKeyReturnsTwoItems() {
    String input = "-----BEGIN CERTIFICATE-----\nF00\n-----END CERTIFICATE-----\n-----BEGIN RSA PRIVATE KEY-----\nF00\n-----END RSA PRIVATE KEY-----";

    List<PemItem> items = PemReader.parse(input);

    assertThat(items).hasSize(2);
  }

  @Test
  void parseValidPemBundlePrivateKeyCertificateReturnsTwoItems() {
    String input = "-----BEGIN RSA PRIVATE KEY-----\nF00\n-----END RSA PRIVATE KEY-----\n-----BEGIN CERTIFICATE-----\nF00\n-----END CERTIFICATE-----";

    List<PemItem> items = PemReader.parse(input);

    assertThat(items).hasSize(2);
  }
}
