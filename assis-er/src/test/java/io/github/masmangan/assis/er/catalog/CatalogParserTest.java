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
				"add attribute | Owner | String | cache", "add \"Pet\" }o--o| \"Pet\" : \"mother\"",
				"add \"Helper\"", "add \"Owner\" { String cache }",
				"remove relationship | Pet | Pet | mother", "remove entity | Helper",
				"remove attribute | Owner | cache", "remove \"Helper\"",
				"remove \"Pet\" }o--o| \"Pet\" : \"mother\"", "remove \"Owner\" { String cache }")) {
			CatalogParser.Result result = CatalogParser.parse(line);
			assertEquals(1, result.entries().size(), line);
			assertTrue(result.warnings().isEmpty(), line + " " + result.warnings());
		}
	}

	@Test
	void mermaidFormEqualsFieldForm() {
		// CP9
		String[][] pairs = {
				{ "add \"Visit\" }o--|| \"Pet\" : \"petId\"", "add relationship | Visit | Pet | many-to-one | petId | required" },
				{ "add Visit }o--o| Pet : petId", "add relationship | Visit | Pet | many-to-one | petId" },
				{ "add \"Owner\" ||--o{ \"Pet\" : \"pets\"", "add relationship | Owner | Pet | one-to-many | pets" },
				{ "add \"User\" ||--|| Profile : \"profile\"", "add relationship | User | Profile | one-to-one | profile | required" },
				{ "add \"Vet\" }o--o{ \"Specialty\" : specialties", "add relationship | Vet | Specialty | many-to-many | specialties" },
				{ "add \"Owner\" ||..o{ \"Pet\" : \"pets\"", "add relationship | Owner | Pet | one-to-many | pets | dotted" } };
		for (String[] pair : pairs) {
			assertEquals(only(CatalogParser.parse(pair[1])), only(CatalogParser.parse(pair[0])), pair[0]);
		}
	}

	@Test
	void mermaidRightEndGivesRequired() {
		// CP10
		assertTrue(only(CatalogParser.parse("add A }o--|| B : x")).required());
		assertFalse(only(CatalogParser.parse("add A }o--o| B : x")).required());
		assertTrue(only(CatalogParser.parse("add A ||--|| B : x")).required());
		assertFalse(only(CatalogParser.parse("add A ||--o| B : x")).required());
	}

	@Test
	void dottedFromSymbolOrFlag() {
		// CP11
		assertTrue(only(CatalogParser.parse("add A }o..|| B : x")).dotted());
		assertTrue(only(CatalogParser.parse("add relationship | A | B | many-to-one | x | dotted")).dotted());
		AddRelationship both = only(
				CatalogParser.parse("add relationship | A | B | many-to-one | x | required, dotted"));
		assertTrue(both.required());
		assertTrue(both.dotted());
	}

	@Test
	void unkeptOptionalityIsWarned() {
		// CP12
		for (String line : List.of("add A }|--o| B : x", "add A |o--o{ B : x", "add A ||--|{ B : x")) {
			CatalogParser.Result result = CatalogParser.parse(line);
			only(result);
			assertEquals(1, result.warnings().size(), line);
			assertTrue(result.warnings().get(0).contains("is not kept"), result.warnings().toString());
		}
		assertEquals(Cardinality.MANY_TO_ONE, only(CatalogParser.parse("add A }|--o| B : x")).cardinality());
		assertEquals(Cardinality.ONE_TO_MANY, only(CatalogParser.parse("add A ||--|{ B : x")).cardinality());
	}

	@Test
	void skipsInvalidMermaidLines() {
		// CP13
		skippedWithWarning("add A }o--> B : x");
		skippedWithWarning("add A }o--|| B");
		skippedWithWarning("add A }o--|| B :");
		skippedWithWarning("add A }o--|| B : x y");
		skippedWithWarning("add \"\" }o--|| B : x");
	}

	@Test
	void acceptsLineCopiedFromDiagram() {
		// CP14
		AddRelationship r = only(CatalogParser.parse("add     \"Pet\" }o--o| \"Owner\" : \"owner\""));
		assertEquals(new AddRelationship(1, "Pet", "Owner", Cardinality.MANY_TO_ONE, "owner", false, false), r);
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
	void parsesMermaidEntity() {
		// CP21
		assertEquals(List.of(new AddEntity(1, "X", null)), entries("add \"X\""));
		assertEquals(List.of(new AddEntity(1, "X", null)), entries("add X"));
		assertEquals(List.of(new AddEntity(1, "X", "x_table")), entries("add \"X\"[\"x_table\"]"));
		assertEquals(List.of(new AddEntity(1, "X", null)), entries("add \"X\" { }"));
	}

	@Test
	void mermaidAttributesEqualFieldForm() {
		// CP22
		String[][] pairs = {
				{ "add \"Visit\" { String? notes }", "add attribute | Visit | String | notes | nullable" },
				{ "add \"Visit\" { PetId petId FK }", "add attribute | Visit | PetId | petId | fk" },
				{ "add \"AuditLog\" { Long id PK }", "add attribute | AuditLog | Long | id | pk" },
				{ "add \"Visit\" { Long id PK, FK }", "add attribute | Visit | Long | id | pk, fk" },
				{ "add \"Visit\" { Long id pk fk }", "add attribute | Visit | Long | id | pk, fk" } };
		for (String[] pair : pairs) {
			assertEquals(entries(pair[1]), entries(pair[0]), pair[0]);
		}
		assertEquals(List.of(new AddAttribute(1, "Visit", attr("Long", "id", true, false, false, null)),
				new AddAttribute(1, "Visit", attr("PetId", "petId", false, true, true, null))),
				entries("add \"Visit\" { Long id PK; PetId? petId FK; }"));
	}

	@Test
	void mermaidAliasAndBlock() {
		// CP23
		assertEquals(List.of(new AddEntity(1, "Visit", "visits"),
				new AddAttribute(1, "Visit", attr("String", "notes", false, false, false, null))),
				entries("add \"Visit\"[\"visits\"] { String notes }"));
	}

	@Test
	void mermaidGenericType() {
		// CP24
		assertEquals(List.of(new AddAttribute(1, "Visit", attr("Set~PetId~", "pastPets", false, false, true, null))),
				entries("add Visit { Set~PetId~? pastPets }"));
	}

	@Test
	void mermaidBadAttributeSkippedOthersKept() {
		// CP25
		for (String bad : List.of("String", "String a b", "String a \"c\"", "String PK")) {
			CatalogParser.Result result = CatalogParser.parse("add \"Visit\" { Long id PK; " + bad + "; String? x }");
			assertEquals(2, result.entries().size(), bad + " " + result.entries());
			assertEquals(1, result.warnings().size(), bad + " " + result.warnings());
			assertTrue(result.warnings().get(0).startsWith("Line 1"), result.warnings().toString());
		}
	}

	@Test
	void mermaidMalformedEntitySkipped() {
		// CP26
		skippedWithWarning("add \"Visit\" {");
		skippedWithWarning("add \"Visit\" { String x } extra");
		skippedWithWarning("add \"Visit\"[\"v\" { String x }");
		skippedWithWarning("add \"A%\"");
	}

	@Test
	void parsesEntityLineCopiedFromDiagram() {
		// CP27
		assertEquals(List.of(new AddEntity(1, "AuditLog", "audit_log")),
				entries("    add \"AuditLog\"[\"audit_log\"]"));
		assertEquals(List.of(new AddAttribute(1, "Visit", attr("PetId", "petId", false, true, true, null))),
				entries("add         \"Visit\" { PetId? petId FK }"));
	}

	@Test
	void parsesRemovalsInBothForms() {
		// CP28
		String[][] pairs = {
				{ "remove relationship | Pet | Pet | mother", "remove \"Pet\" }o--o| \"Pet\" : \"mother\"" },
				{ "Remove Attribute | Owner | cache", "remove \"Owner\" { String? cache }" },
				{ "REMOVE ENTITY | Helper", "remove Helper" } };
		CatalogEntry[] expected = { new RemoveRelationship(1, "Pet", "Pet", "mother"),
				new RemoveAttribute(1, "Owner", "cache"), new RemoveEntity(1, "Helper") };
		for (int i = 0; i < pairs.length; i++) {
			assertEquals(List.of(expected[i]), entries(pairs[i][0]), pairs[i][0]);
			assertEquals(List.of(expected[i]), entries(pairs[i][1]), pairs[i][1]);
		}
		assertEquals(List.of(new RemoveEntity(1, "Helper")), entries("    remove \"Helper\"[\"helpers\"]"));
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
		skippedWithWarning("remove \"Pet\" }o--> \"Pet\" : \"mother\"");
		skippedWithWarning("remove \"Pet\" }o--o| \"Pet\"");
		skippedWithWarning("remove \"Owner\" { }");
	}

	@Test
	void blockRemovesOnlyAttributes() {
		// CP30
		assertEquals(List.of(new RemoveAttribute(1, "Owner", "id"), new RemoveAttribute(1, "Owner", "cache")),
				entries("remove \"Owner\"[\"owners\"] { Long id PK; String cache }"));
		CatalogParser.Result bad = CatalogParser.parse("remove \"Owner\"[\"owners\"] { String }");
		assertTrue(bad.entries().isEmpty(), bad.entries().toString());
	}
}
