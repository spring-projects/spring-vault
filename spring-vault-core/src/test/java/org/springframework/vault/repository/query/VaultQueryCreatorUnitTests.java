/*
 * Copyright 2017-2018 the original author or authors.
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
package org.springframework.vault.repository.query;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.repository.query.DefaultParameters;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.util.ReflectionUtils;
import org.springframework.vault.repository.mapping.VaultMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link VaultQueryCreator}.
 *
 * @author Mark Paluch
 */
public class VaultQueryCreatorUnitTests {

	private VaultMappingContext mappingContext = new VaultMappingContext();

	@Test
	public void greaterThan() {

		VaultQuery query = createQuery("findByIdGreaterThan", "5");

		assertThat(query.getPredicate()).accepts("6", "7").rejects("4", "5");
	}

	@Test
	public void greaterThanOrEqual() {

		VaultQuery query = createQuery("findByIdGreaterThanEqual", "5");

		assertThat(query.getPredicate()).accepts("5", "6", "7").rejects("4");
	}

	@Test
	public void lessThan() {

		VaultQuery query = createQuery("findByIdLessThan", "5");

		assertThat(query.getPredicate()).accepts("4").rejects("5", "6", "7");
	}

	@Test
	public void lessThanOrEqual() {

		VaultQuery query = createQuery("findByIdLessThanEqual", "5");

		assertThat(query.getPredicate()).accepts("4", "5").rejects("6", "7");
	}

	@Test
	public void between() {

		VaultQuery query = createQuery("findByIdIsBetween", "5", "7");

		assertThat(query.getPredicate()).accepts("5", "6", "7").rejects("4", "8");
	}

	@Test
	public void in() {

		VaultQuery query = createQuery("findByIdIn", Arrays.asList("2", "3"));

		assertThat(query.getPredicate()).accepts("2", "3").rejects("4", "8");
	}

	@Test
	public void negateIn() {

		VaultQuery query = createQuery("findByIdNotIn", Arrays.asList("2", "3"));

		assertThat(query.getPredicate()).accepts("4", "8").rejects("2", "3");
	}

	@Test
	public void startingWith() {

		VaultQuery query = createQuery("findByIdStartsWith", "Walter");

		assertThat(query.getPredicate()).accepts("Walter White").rejects("Skyler");
	}

	@Test
	public void endingWith() {

		VaultQuery query = createQuery("findByIdEndsWith", "White");

		assertThat(query.getPredicate()).accepts("Walter White").rejects("Skyler");
	}

	@Test
	public void containing() {

		VaultQuery query = createQuery("findByIdContaining", "er Wh");

		assertThat(query.getPredicate()).accepts("Walter White").rejects("Skyler");
	}

	@Test
	public void negateContaining() {

		VaultQuery query = createQuery("findByIdNotContaining", "er Wh");

		assertThat(query.getPredicate()).accepts("Skyler").rejects("Walter White");
	}

	@Test
	public void regex() {

		VaultQuery query = createQuery("findByIdMatches", "Wa(.*)r");

		assertThat(query.getPredicate()).accepts("Walter", "Water").rejects("Skyler");
	}

	@Test
	public void isTrue() {

		VaultQuery query = createQuery("findByIdIsTrue", "");

		assertThat(query.getPredicate()).accepts("true", "True").rejects("false");
	}

	@Test
	public void isFalse() {

		VaultQuery query = createQuery("findByIdIsFalse", "");

		assertThat(query.getPredicate()).accepts("false", "False").rejects("true");
	}

	@Test
	public void simpleProperty() {

		VaultQuery query = createQuery("findById", "Walter");

		assertThat(query.getPredicate()).accepts("Walter").rejects("Skyler");
	}

	@Test
	public void negateSimpleProperty() {

		VaultQuery query = createQuery("findByIdNot", "Walter");

		assertThat(query.getPredicate()).accepts("Skyler").rejects("Walter");
	}

	@Test
	public void greaterThanOrEquals() {

		VaultQuery query = createQuery("findByIdGreaterThanOrIdIs", "5", "2");

		assertThat(query.getPredicate()).accepts("6", "7", "2").rejects("3", "4", "5");
	}

	@Test
	public void greaterThanAndLessThan() {

		VaultQuery query = createQuery("findByIdGreaterThanAndIdLessThan", "2", "5");

		assertThat(query.getPredicate()).accepts("3", "4").rejects("2", "5", "6");
	}

	@Test(expected = InvalidDataAccessApiUsageException.class)
	public void failsForNonIdProperties() {
		createQuery("findByName", "");
	}

	VaultQuery createQuery(String methodName, String value) {

		DefaultParameters defaultParameters = new DefaultParameters(
				ReflectionUtils.findMethod(dummy.class, "someUnrelatedMethod",
						String.class));

		PartTree partTree = new PartTree(methodName, Credentials.class);
		VaultQueryCreator queryCreator = new VaultQueryCreator(
				partTree,
				new ParametersParameterAccessor(defaultParameters, new Object[] { value }),
				mappingContext);

		return queryCreator.createQuery().getCriteria();
	}

	VaultQuery createQuery(String methodName, List<String> value) {

		DefaultParameters defaultParameters = new DefaultParameters(
				ReflectionUtils
						.findMethod(dummy.class, "someUnrelatedMethod", List.class));

		PartTree partTree = new PartTree(methodName, Credentials.class);
		VaultQueryCreator queryCreator = new VaultQueryCreator(
				partTree,
				new ParametersParameterAccessor(defaultParameters, new Object[] { value }),
				mappingContext);

		return queryCreator.createQuery().getCriteria();
	}

	VaultQuery createQuery(String methodName, String value, String anotherValue) {

		DefaultParameters defaultParameters = new DefaultParameters(
				ReflectionUtils.findMethod(dummy.class, "someUnrelatedMethod",
						String.class, String.class));

		PartTree partTree = new PartTree(methodName, Credentials.class);
		VaultQueryCreator queryCreator = new VaultQueryCreator(partTree,
				new ParametersParameterAccessor(defaultParameters, new Object[] { value,
						anotherValue }), mappingContext);

		return queryCreator.createQuery().getCriteria();
	}

	static interface dummy {

		Object someUnrelatedMethod(String arg);

		Object someUnrelatedMethod(List<String> arg);

		Object someUnrelatedMethod(String from, String to);
	}

	static class Credentials {

		String id, name;
	}
}
