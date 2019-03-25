/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.vault.annotation;

import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.vault.core.VaultTemplate;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit test for {@link VaultPropertySource}.
 *
 * @author Mark Paluch
 */
public class VaultPropertySourceUnitTests {

	@Configuration
	static class Config {

		@Bean
		VaultTemplate vaultTemplate() {
			return Mockito.mock(VaultTemplate.class);
		}
	}

	@Configuration
	@Profile("demo")
	@VaultPropertySource("foo")
	static class DemoProfile {
	}

	@Configuration
	@Profile("dev")
	@VaultPropertySource("bar")
	static class DevProfile {
	}

	@Test
	public void shouldNotEnablePropertySource() {

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		ctx.register(Config.class);
		ctx.register(DemoProfile.class);
		ctx.register(DevProfile.class);
		ctx.refresh();

		VaultTemplate templateMock = ctx.getBean(VaultTemplate.class);

		verify(templateMock).afterPropertiesSet();
		verifyNoMoreInteractions(templateMock);
	}

	@Test
	public void shouldEnablePropertySourceByProfile() {

		AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

		ctx.getEnvironment().addActiveProfile("demo");

		ctx.register(Config.class);
		ctx.register(DemoProfile.class);
		ctx.register(DevProfile.class);

		ctx.refresh();

		VaultTemplate templateMock = ctx.getBean(VaultTemplate.class);

		verify(templateMock).afterPropertiesSet();
		verify(templateMock).read("foo");
		verify(templateMock, never()).read("bar");
	}
}
