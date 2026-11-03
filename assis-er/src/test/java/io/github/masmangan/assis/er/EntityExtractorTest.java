package io.github.masmangan.assis.er;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.masmangan.assis.internal.DeclaredIndex;
import io.github.masmangan.assis.io.SmartSourceRootManager;
import io.github.masmangan.assis.util.DeterministicPathList;

class EntityExtractorTest {

	private EntityView extractFrom(String resource) throws Exception {
		URL url = getClass().getClassLoader().getResource(resource);
		Path root = Paths.get(url.toURI());

		DeclaredIndex index = new DeclaredIndex();
		index.fill(new SmartSourceRootManager().autoscan(DeterministicPathList.fromSourceRoots(Set.of(root))));

		return EntityExtractor.extract(index);
	}

	private List<String> entityNamesIn(String resource) throws Exception {
		return extractFrom(resource).entities().stream().map(EntityView.Entity::name).toList();
	}

	private List<EntityView.Attribute> ownerAttributes() throws Exception {
		return extractFrom("samples/attributes").entities().get(0).attributes();
	}

	@Test
	void extractsPersistentFieldsInDeclarationOrder() throws Exception {
		assertEquals(List.of("id", "name", "a", "b", "tags", "scores", "birth", "photo"),
				ownerAttributes().stream().map(EntityView.Attribute::name).toList());
	}

	@Test
	void marksOnlyIdAsPrimaryKey() throws Exception {
		assertEquals(List.of("id"),
				ownerAttributes().stream().filter(EntityView.Attribute::primaryKey).map(EntityView.Attribute::name).toList());
	}

	@Test
	void keepsJavaTypesAsWritten() throws Exception {
		Map<String, String> types = ownerAttributes().stream()
				.collect(Collectors.toMap(EntityView.Attribute::name, EntityView.Attribute::type));

		assertEquals("List<String>", types.get("tags"));
		assertEquals("Map<String,List<Integer>>", types.get("scores"));
		assertEquals("java.time.LocalDate", types.get("birth"));
		assertEquals("byte[]", types.get("photo"));
		assertEquals("int", types.get("b"));
	}

	@Test
	void entityWithoutFieldsHasNoAttributes() throws Exception {
		assertEquals(List.of(), extractFrom("samples/entity").entities().get(0).attributes());
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
