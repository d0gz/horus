package io.github.masmangan.assis.er.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.masmangan.assis.er.EntityView;
import io.github.masmangan.assis.er.types.Cardinality;

class CatalogParserTest {

	private static AddRelationship only(CatalogParser.Result result) {
		assertEquals(1, result.entries().size(), result.warnings().toString());
		return (AddRelationship) result.entries().get(0);
	}

	private static void skippedWithWarning(String line) {
		CatalogParser.Result result = CatalogParser.parse(line);
		assertTrue(result.entries().isEmpty(), line);
		assertEquals(1, result.warnings().size(), line + " " + result.warnings());
		assertTrue(result.warnings().get(0).startsWith("Line 1"), result.warnings().toString());
	}

	@Test
	void parsesFieldFormWithAndWithoutFlags() {
		// CP1
		AddRelationship plain = only(CatalogParser.parse("add relationship | Visit | Pet | many-to-one | petId"));
		assertEquals(new AddRelationship(1, "Visit", "Pet", Cardinality.MANY_TO_ONE, "petId", false, false), plain);
		AddRelationship required = only(
				CatalogParser.parse("add relationship | Visit | Pet | many-to-one | petId | required"));
		assertTrue(required.required());
	}

	@Test
	void ignoresBlankLinesCommentsAndSpaces() {
		// CP2
		CatalogParser.Result result = CatalogParser
				.parse("\n# comment\n   \n   add relationship|  Visit |Pet|many-to-one|  petId  \n");
		AddRelationship r = only(result);
		assertEquals(4, r.line());
		assertEquals("Visit", r.from());
		assertEquals("petId", r.label());
		assertTrue(result.warnings().isEmpty());
	}

	@Test
	void operationAndKindAreCaseInsensitive() {
		// CP3
		only(CatalogParser.parse("Add Relationship | Visit | Pet | many-to-one | petId"));
		only(CatalogParser.parse("ADD RELATIONSHIP | Visit | Pet | many-to-one | petId"));
	}

	@Test
	void acceptsCardinalitySpellings() {
		// CP4
		for (String c : List.of("many-to-one", "ManyToOne", "MANY_TO_ONE", "many to one")) {
			assertEquals(Cardinality.MANY_TO_ONE,
					only(CatalogParser.parse("add relationship | A | B | " + c + " | x")).cardinality(), c);
		}
	}

	@Test
	void skipsInvalidFieldLines() {
		// CP5
		skippedWithWarning("add relationship | A | B | some-to-one | x");
		skippedWithWarning("add relationship | A | B | many-to-one");
		skippedWithWarning("add relationship |  | B | many-to-one | x");
		skippedWithWarning("add relationship | A |  | many-to-one | x");
		skippedWithWarning("add relationship | A | B | many-to-one |  ");
		skippedWithWarning("add relationship | A | B | many-to-one | x\"y");
	}

	@Test
	void dropsInvalidFlagsWithWarning() {
		// CP6
		for (String c : List.of("one-to-many", "many-to-many")) {
			CatalogParser.Result result = CatalogParser.parse("add relationship | A | B | " + c + " | x | required");
			assertFalse(only(result).required());
			assertEquals(1, result.warnings().size());
		}
		CatalogParser.Result unknown = CatalogParser.parse("add relationship | A | B | many-to-one | x | maybe");
		assertFalse(only(unknown).required());
		assertEquals(1, unknown.warnings().size());
	}

	@Test
	void skipsUnknownOperationOrKind() {
		// CP7
		skippedWithWarning("update relationship | A | B | many-to-one | x");
		skippedWithWarning("add table | A | B");
	}

	@Test
	void everyOperationAndKindParses() {
		// CP8
		for (String line : List.of("add relationship | Pet | Pet | many-to-one | mother", "add entity | Helper",
				"add attribute | Owner | String | cache", "remove relationship | Pet | Pet | mother",
				"remove entity | Helper", "remove attribute | Owner | cache")) {
			CatalogParser.Result result = CatalogParser.parse(line);
			assertEquals(1, result.entries().size(), line);
			assertTrue(result.warnings().isEmpty(), line + " " + result.warnings());
		}
	}

	@Test
	void dottedFromSymbolOrFlag() {
		// CP11
		assertTrue(only(CatalogParser.parse("add relationship | A | B | many-to-one | x | dotted")).dotted());
		AddRelationship both = only(
				CatalogParser.parse("add relationship | A | B | many-to-one | x | required, dotted"));
		assertTrue(both.required());
		assertTrue(both.dotted());
	}

	private static EntityView.Attribute attr(String type, String name, boolean pk, boolean fk, boolean nullable,
			String column) {
		return new EntityView.Attribute(type, name, pk, fk, nullable, column);
	}

	private static List<CatalogEntry> entries(String text) {
		CatalogParser.Result result = CatalogParser.parse(text);
		assertTrue(result.warnings().isEmpty(), text + " " + result.warnings());
		return result.entries();
	}

	@Test
	void parsesAddEntity() {
		// CP15
		assertEquals(List.of(new AddEntity(1, "AuditLog", null)), entries("add entity | AuditLog"));
		assertEquals(List.of(new AddEntity(1, "AuditLog", "audit_log")), entries("Add Entity | AuditLog | audit_log"));
		assertEquals(List.of(new AddEntity(1, "AuditLog", null)), entries("add entity | AuditLog |  "));
	}

	@Test
	void skipsInvalidAddEntity() {
		// CP16
		skippedWithWarning("add entity | A | a | extra");
		skippedWithWarning("add entity |  ");
		skippedWithWarning("add entity | A\"B");
		skippedWithWarning("add entity | 50%");
	}

	@Test
	void parsesAddAttribute() {
		// CP17
		assertEquals(List.of(new AddAttribute(1, "Visit", attr("String", "notes", false, false, false, null))),
				entries("add attribute | Visit | String | notes"));
		assertEquals(List.of(new AddAttribute(1, "Visit", attr("String", "notes", false, false, true, null))),
				entries("add attribute | Visit | String | notes | nullable"));
		assertEquals(List.of(new AddAttribute(1, "Visit", attr("Map<String, Long>", "scores", false, false, false,
				null))), entries("add attribute | Visit | Map<String, Long> | scores"));
	}

	@Test
	void parsesAttributeFlags() {
		// CP18
		assertEquals(List.of(new AddAttribute(1, "Visit", attr("Long", "id", true, true, false, "visit_id"))),
				entries("add attribute | Visit | Long | id | Column=visit_id, FK ,pk"));
		assertEquals(List.of(new AddAttribute(1, "Visit", attr("String", "notes", false, false, true, null))),
				entries("add attribute | Visit | String | notes | NULLABLE, column=notes"));
	}

	@Test
	void dropsInvalidAttributeFlags() {
		// CP19
		String[][] cases = {
				{ "add attribute | V | Long | id | pk, nullable", "true", "false", "0" },
				{ "add attribute | V | Long | id | column=", "false", "false", "1" },
				{ "add attribute | V | Long | id | unique", "false", "false", "1" } };
		for (String[] c : cases) {
			CatalogParser.Result result = CatalogParser.parse(c[0]);
			assertEquals(1, result.entries().size(), c[0]);
			EntityView.Attribute a = ((AddAttribute) result.entries().get(0)).attribute();
			assertEquals(Boolean.parseBoolean(c[1]), a.primaryKey(), c[0]);
			assertEquals(Boolean.parseBoolean(c[2]), a.nullable(), c[0]);
			assertEquals(null, a.columnName(), c[0]);
			assertEquals(Integer.parseInt(c[3]), result.warnings().size(), c[0] + " " + result.warnings());
		}
	}

	@Test
	void skipsInvalidAddAttribute() {
		// CP20
		skippedWithWarning("add attribute | Visit | String");
		skippedWithWarning("add attribute | Visit |  | notes");
		skippedWithWarning("add attribute | Visit | Str{ing | notes");
		skippedWithWarning("add attribute | Visit | String | PK");
		skippedWithWarning("add attribute | Visit | String | 1x");
		skippedWithWarning("add attribute |  | String | notes");
	}

	@Test
	void parsesRemovals() {
		// CP28
		assertEquals(List.of(new RemoveRelationship(1, "Pet", "Pet", "mother")),
				entries("remove relationship | Pet | Pet | mother"));
		assertEquals(List.of(new RemoveAttribute(1, "Owner", "cache")), entries("Remove Attribute | Owner | cache"));
		assertEquals(List.of(new RemoveEntity(1, "Helper")), entries("REMOVE ENTITY | Helper"));
	}

	@Test
	void skipsInvalidRemovals() {
		// CP29
		skippedWithWarning("remove relationship | Pet | Pet");
		skippedWithWarning("remove relationship | Pet | Pet | mother | extra");
		skippedWithWarning("remove relationship | Pet |  | mother");
		skippedWithWarning("remove relationship | Pet | Pet |  ");
		skippedWithWarning("remove attribute | Owner");
		skippedWithWarning("remove attribute |  | cache");
		skippedWithWarning("remove entity | ");
		skippedWithWarning("remove entity | A | B");
	}

	@Test
	void mermaidLinesAreSkipped() {
		for (String line : List.of("add \"Visit\" }o--|| \"Pet\" : \"petId\"", "add A }o--o| B : x", "add \"Helper\"",
				"add Visit { String notes }", "remove \"Pet\" }o--o| \"Pet\" : \"mother\"", "remove Helper",
				"remove \"Owner\" { String cache }")) {
			skippedWithWarning(line);
		}
	}

}
