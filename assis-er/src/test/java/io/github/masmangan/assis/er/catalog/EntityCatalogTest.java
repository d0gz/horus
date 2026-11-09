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

	private static EntityView.Attribute attr(String type, String name, boolean pk, boolean fk, boolean nullable,
			String column) {
		return new EntityView.Attribute(type, name, pk, fk, nullable, column);
	}

	private static EntityView.Attribute fromCatalog(EntityView.Attribute a) {
		return new EntityView.Attribute(a.type(), a.name(), a.primaryKey(), a.foreignKey(), a.nullable(),
				a.columnName(), true);
	}

	private static final EntityView.Attribute ID = attr("Long", "id", true, false, false, null);
	private static final EntityView.Attribute NOTES = attr("String", "notes", false, false, true, null);

	private static EntityView visitView() {
		return new EntityView(List.of(new EntityView.Entity("Owner", List.of(ID), "owners"),
				new EntityView.Entity("Visit", List.of(ID, attr("PetId", "petId", false, false, true, null),
						attr("String", "comment", false, false, true, "visit_notes")))),
				List.of(), true);
	}

	private static EntityView.Entity entity(EntityView view, String name) {
		return view.entities().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
	}

	@Test
	void addEntityCreatesMissingEntity() {
		// CA10
		EntityView out = EntityCatalog.of(new AddEntity(1, "AuditLog", "audit_log")).apply(visitView());
		assertEquals(3, out.entities().size());
		assertEquals(new EntityView.Entity("AuditLog", List.of(), "audit_log", true), out.entities().get(2));
	}

	@Test
	void addEntitySetsTableOfExistingEntity() {
		// CA11
		EntityView out = EntityCatalog.of(new AddEntity(1, "Visit", "visits")).apply(visitView());
		EntityView.Entity visit = out.entities().get(1);
		assertEquals("Visit", visit.name());
		assertEquals("visits", visit.tableName());
		assertTrue(visit.fromCatalog());
		assertEquals(entity(visitView(), "Visit").attributes(), visit.attributes());
	}

	@Test
	void addEntityWithoutChangeWarns() {
		// CA12
		for (CatalogEntry e : List.of(new AddEntity(3, "Visit", null), new AddEntity(3, "Owner", "owners"))) {
			EntityCatalog c = EntityCatalog.of(e);
			assertEquals(visitView(), c.apply(visitView()));
			assertEquals(1, c.warnings().size(), c.warnings().toString());
			assertTrue(c.warnings().get(0).startsWith("Line 3: entity '"), c.warnings().toString());
		}
	}

	@Test
	void tableEqualToNameClearsTable() {
		// CA13
		EntityView out = EntityCatalog.of(new AddEntity(1, "Owner", "Owner")).apply(visitView());
		assertEquals(null, entity(out, "Owner").tableName());
	}

	@Test
	void addAttributeCreatesMissingEntity() {
		// CA14
		EntityView out = EntityCatalog.of(new AddAttribute(1, "AuditLog", ID)).apply(visitView());
		assertEquals(new EntityView.Entity("AuditLog", List.of(fromCatalog(ID)), null, true), out.entities().get(2));
	}

	@Test
	void addAttributeAppendsNewName() {
		// CA15
		EntityView out = EntityCatalog.of(new AddAttribute(1, "Owner", NOTES)).apply(visitView());
		EntityView.Entity owner = entity(out, "Owner");
		assertEquals(List.of(ID, fromCatalog(NOTES)), owner.attributes());
		assertFalse(owner.fromCatalog());
		assertEquals("owners", owner.tableName());
	}

	@Test
	void addAttributeReplacesSameNameInPlace() {
		// CA16
		EntityView.Attribute petId = attr("PetId", "petId", false, true, false, null);
		EntityView out = EntityCatalog.of(new AddAttribute(1, "Visit", petId)).apply(visitView());
		List<EntityView.Attribute> attributes = entity(out, "Visit").attributes();
		assertEquals(3, attributes.size());
		assertEquals(fromCatalog(petId), attributes.get(1));
	}

	@Test
	void addAttributeMatchesColumnName() {
		// CA17
		EntityView out = EntityCatalog.of(new AddAttribute(1, "Visit", attr("String", "visit_notes", false, false,
				false, null))).apply(visitView());
		assertEquals(fromCatalog(attr("String", "comment", false, false, false, "visit_notes")),
				entity(out, "Visit").attributes().get(2));
	}

	@Test
	void lastCatalogAttributeAndTableWin() {
		// CA18
		EntityCatalog attributes = EntityCatalog.of(new AddAttribute(1, "Owner", NOTES),
				new AddAttribute(2, "Owner", attr("Text", "notes", false, false, false, null)));
		EntityView out = attributes.apply(visitView());
		assertEquals(List.of(ID, fromCatalog(attr("Text", "notes", false, false, false, null))),
				entity(out, "Owner").attributes());
		assertEquals(1, attributes.warnings().size());
		assertTrue(attributes.warnings().get(0).startsWith("Line 2"), attributes.warnings().toString());

		EntityCatalog tables = EntityCatalog.of(new AddEntity(1, "Visit", "a"), new AddEntity(2, "Visit", "b"));
		assertEquals("b", entity(tables.apply(visitView()), "Visit").tableName());
		assertEquals(1, tables.warnings().size());
		assertTrue(tables.warnings().get(0).startsWith("Line 2"), tables.warnings().toString());
	}

	@Test
	void relationshipCanTargetEntityAddedLater() {
		// CA19
		EntityCatalog c = EntityCatalog.fromText("add relationship | AuditLog | Visit | many-to-one | visit");
		EntityView created = EntityCatalog.of(new AddEntity(2, "AuditLog", null)).apply(visitView());
		assertEquals(1, c.apply(created).relationships().size());

		EntityCatalog mixed = EntityCatalog.of(
				new AddRelationship(1, "AuditLog", "Visit", Cardinality.MANY_TO_ONE, "visit", false, false),
				new AddEntity(2, "AuditLog", null));
		assertEquals(List.of(catalog("AuditLog", "Visit", Cardinality.MANY_TO_ONE, "visit", false)),
				mixed.apply(visitView()).relationships());
		assertTrue(mixed.warnings().isEmpty(), mixed.warnings().toString());
	}

	@Test
	void attributeThenTableOnSameEntity() {
		// CA20
		EntityCatalog c = EntityCatalog.of(new AddAttribute(1, "AuditLog", ID), new AddEntity(2, "AuditLog", "logs"));
		assertEquals(new EntityView.Entity("AuditLog", List.of(fromCatalog(ID)), "logs", true),
				entity(c.apply(visitView()), "AuditLog"));
		assertTrue(c.warnings().isEmpty(), c.warnings().toString());
	}

	@Test
	void entityEntriesKeepRelationshipsAndHiddenInferred() {
		// CA21
		EntityView out = EntityCatalog.of(new AddEntity(1, "Owner", "o"), new AddAttribute(2, "Visit",
				attr("OwnerId", "ownerId", false, false, true, null))).apply(view());
		assertEquals(view().relationships(), out.relationships());
		assertTrue(out.hiddenInferred());
	}
}
