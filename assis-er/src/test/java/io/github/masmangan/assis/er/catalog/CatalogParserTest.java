package io.github.masmangan.assis.er.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

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
	void laterPartsAreNotSupportedYet() {
		// CP8
		for (String line : List.of("add entity | AuditLog", "add attribute | Visit | String | notes",
				"remove relationship | Pet | Pet | mother", "remove entity | Helper", "add \"AuditLog\"",
				"add \"Visit\" { String? notes }", "remove \"Pet\" }o--o| \"Pet\" : \"mother\"")) {
			CatalogParser.Result result = CatalogParser.parse(line);
			assertTrue(result.entries().isEmpty(), line);
			assertEquals(1, result.warnings().size(), line);
			assertTrue(result.warnings().get(0).endsWith("is not supported yet"), result.warnings().toString());
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
}
