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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Factory for {@link PrivateKeyStrategy}. Supports {@link RsaPrivateKeyStrategy} and {@link EcPrivateKeyStrategy}.
 *
 * @author Alex Bremora
 * @since 2.4
 */
class PrivateKeyFactory {
  private static final List<PrivateKeyStrategy> privateKeyStrategies = Arrays.asList(
      new RsaPrivateKeyStrategy(),
      new EcPrivateKeyStrategy());

  public static PrivateKeyStrategy create(String privateKeyType) {
    Optional<PrivateKeyStrategy> optionalPrivateKeyStrategy = privateKeyStrategies.stream().filter(f -> f.getName().equalsIgnoreCase(privateKeyType)).findFirst();
    return optionalPrivateKeyStrategy.isPresent() ? optionalPrivateKeyStrategy.get() : null;
  }

  public static boolean isTypeSupported(String privateKeyType){
    return privateKeyStrategies.stream().filter(f -> f.getName().equalsIgnoreCase(privateKeyType)).findFirst().isPresent();
  }
}
