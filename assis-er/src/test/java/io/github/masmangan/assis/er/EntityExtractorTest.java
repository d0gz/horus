package io.github.masmangan.assis.er;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.masmangan.assis.internal.DeclaredIndex;
import io.github.masmangan.assis.io.SmartSourceRootManager;
import io.github.masmangan.assis.util.DeterministicPathList;

class EntityExtractorTest {

	private List<String> entityNamesIn(String resource) throws Exception {
		URL url = getClass().getClassLoader().getResource(resource);
		Path root = Paths.get(url.toURI());

		DeclaredIndex index = new DeclaredIndex();
		index.fill(new SmartSourceRootManager().autoscan(DeterministicPathList.fromSourceRoots(Set.of(root))));

		return EntityExtractor.extract(index).entities().stream().map(EntityView.Entity::name).toList();
	}

	@Test
	void detectsJakartaEntity() throws Exception {
		assertEquals(List.of("Owner"), entityNamesIn("samples/entity"));
	}

	@Test
	void ignoresNonEntityTypes() throws Exception {
		assertEquals(List.of("Owner"), entityNamesIn("samples/entitymixed"));
	}

	@Test
	void detectsJavaxEntity() throws Exception {
		assertEquals(List.of("Customer"), entityNamesIn("samples/entityjavax"));
	}

	@Test
	void detectsFullyQualifiedEntityWithArguments() throws Exception {
		assertEquals(List.of("Invoice"), entityNamesIn("samples/entityfqn"));
	}

	@Test
	void returnsEmptyViewWhenNoEntities() throws Exception {
		assertEquals(List.of(), entityNamesIn("samples/entitynone"));
	}
}
