package io.github.masmangan.assis.er.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.masmangan.assis.er.EntityView;
import io.github.masmangan.assis.er.types.Cardinality;

class EntityCatalogTest {

	@TempDir
	Path tempDir;

	private static final EntityView.Relationship OWNER = new EntityView.Relationship("Pet", "Owner",
			Cardinality.MANY_TO_ONE, "owner");
	private static final EntityView.Relationship INFERRED = new EntityView.Relationship("Visit", "Pet",
			Cardinality.MANY_TO_ONE, "petId", true);

	private static EntityView view() {
		return new EntityView(
				List.of(new EntityView.Entity("Owner",
						List.of(new EntityView.Attribute("Long", "id", true))),
						new EntityView.Entity("Pet"), new EntityView.Entity("Visit")),
				List.of(OWNER, INFERRED), true);
	}

	private static EntityView.Relationship catalog(String from, String to, Cardinality c, String label,
			boolean required) {
		return new EntityView.Relationship(from, to, c, label, false, required, true, false);
	}

	@Test
	void emptyCatalogChangesNothing() {
		// CA1
		assertEquals(view(), EntityCatalog.empty().apply(view()));
		EntityCatalog blank = EntityCatalog.fromText("");
		assertEquals(view(), blank.apply(view()));
		assertTrue(blank.warnings().isEmpty());
	}

	@Test
	void addsRelationshipBetweenExistingEntities() {
		// CA2
		EntityView out = EntityCatalog.fromText("add relationship | Visit | Owner | many-to-one | ownerId").apply(view());
		assertEquals(List.of(OWNER, INFERRED, catalog("Visit", "Owner", Cardinality.MANY_TO_ONE, "ownerId", false)),
				out.relationships());
	}

	@Test
	void skipsRelationshipWithUnknownEntity() {
		// CA3
		for (String line : List.of("add relationship | Visit | Ghost | many-to-one | ghost",
				"add relationship | Ghost | Visit | many-to-one | ghost")) {
			EntityCatalog c = EntityCatalog.fromText(line);
			assertEquals(view(), c.apply(view()));
			assertEquals(List.of("Line 1: entity 'Ghost' not found, relationship skipped"), c.warnings());
		}
	}

	@Test
	void replacesAnnotatedRelationshipWithSameKey() {
		// CA4
		EntityView out = EntityCatalog.fromText("add relationship | Pet | Owner | one-to-one | owner | required")
				.apply(view());
		assertEquals(List.of(INFERRED, catalog("Pet", "Owner", Cardinality.ONE_TO_ONE, "owner", true)),
				out.relationships());
	}

	@Test
	void replacesInferredRelationshipWithSolidOne() {
		// CA5
		EntityView out = EntityCatalog.fromText("add \"Visit\" }o--|| \"Pet\" : \"petId\"").apply(view());
		assertEquals(List.of(OWNER, catalog("Visit", "Pet", Cardinality.MANY_TO_ONE, "petId", true)),
				out.relationships());
		assertFalse(out.relationships().get(1).inferred());
	}

	@Test
	void keepsRelationshipWithOtherLabel() {
		// CA6
		EntityView out = EntityCatalog.fromText("add relationship | Pet | Owner | many-to-one | breeder").apply(view());
		assertEquals(3, out.relationships().size());
		assertTrue(out.relationships().contains(OWNER));
	}

	@Test
	void lastDuplicateWins() {
		// CA7
		EntityCatalog c = EntityCatalog.fromText("add relationship | Visit | Owner | many-to-one | ownerId\n"
				+ "add relationship | Visit | Owner | one-to-one | ownerId");
		EntityView out = c.apply(view());
		assertEquals(List.of(OWNER, INFERRED, catalog("Visit", "Owner", Cardinality.ONE_TO_ONE, "ownerId", false)),
				out.relationships());
		assertEquals(1, c.warnings().size());
		assertTrue(c.warnings().get(0).startsWith("Line 2"), c.warnings().toString());
	}

	@Test
	void missingFileIsAWarning() {
		// CA8
		EntityCatalog c = EntityCatalog.from(tempDir.resolve("missing.txt"));
		assertEquals(view(), c.apply(view()));
		assertEquals(0, c.size());
		assertEquals(1, c.warnings().size());
		assertTrue(c.warnings().get(0).startsWith("Catalog could not be read"), c.warnings().toString());
	}

	@Test
	void keepsEntitiesAndHiddenInferred() {
		// CA9
		EntityView out = EntityCatalog.fromText("add relationship | Visit | Owner | many-to-one | ownerId\n"
				+ "add relationship | Pet | Owner | many-to-one | owner").apply(view());
		assertEquals(view().entities(), out.entities());
		assertTrue(out.hiddenInferred());
	}
}
