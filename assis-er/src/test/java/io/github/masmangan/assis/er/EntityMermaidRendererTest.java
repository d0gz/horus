package io.github.masmangan.assis.er;

import io.github.masmangan.assis.er.types.Cardinality;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EntityMermaidRendererTest {

	@Test
	void rendersEmptyView() {
		assertEquals("erDiagram\n", EntityMermaidRenderer.render(new EntityView(List.of())));
	}

	@Test
	void rendersSingleEntity() {
		EntityView view = new EntityView(List.of(new EntityView.Entity("Owner")));

		assertEquals("erDiagram\n    \"Owner\"\n", EntityMermaidRenderer.render(view));
	}

	@Test
	void rendersEntitiesInViewOrder() {
		EntityView view = new EntityView(List.of(new EntityView.Entity("Owner"), new EntityView.Entity("Pet")));

		assertEquals("erDiagram\n    \"Owner\"\n    \"Pet\"\n", EntityMermaidRenderer.render(view));
	}

	private static EntityView.Entity owner() {
		return new EntityView.Entity("Owner", List.of(new EntityView.Attribute("Long", "id", true),
				new EntityView.Attribute("String", "name", false)));
	}

	@Test
	void rendersAttributesBlockWithPrimaryKey() {
		assertEquals("erDiagram\n    \"Owner\" {\n        Long id PK\n        String name\n    }\n",
				EntityMermaidRenderer.render(new EntityView(List.of(owner()))));
	}

	@Test
	void rendersEntityWithoutAttributesAfterBlock() {
		EntityView view = new EntityView(List.of(owner(), new EntityView.Entity("Tag")));

		assertEquals("erDiagram\n    \"Owner\" {\n        Long id PK\n        String name\n    }\n    \"Tag\"\n",
				EntityMermaidRenderer.render(view));
	}

	@Test
	void convertsGenericToTilde() {
		assertEquals("List~String~", EntityMermaidRenderer.mermaidType("List<String>"));
	}

	@Test
	void convertsNestedGenericsAndDropsSpaceAfterComma() {
		assertEquals("Map~String,List~Integer~~", EntityMermaidRenderer.mermaidType("Map<String, List<Integer>>"));
	}

	@Test
	void stripsPackageQualifier() {
		assertEquals("LocalDate", EntityMermaidRenderer.mermaidType("java.time.LocalDate"));
	}

	@Test
	void stripsPackageQualifierInsideGeneric() {
		assertEquals("List~LocalDate~", EntityMermaidRenderer.mermaidType("List<java.time.LocalDate>"));
	}

	@Test
	void keepsArrays() {
		assertEquals("byte[]", EntityMermaidRenderer.mermaidType("byte[]"));
	}

	private static String renderRelationship(Cardinality cardinality) {
		EntityView view = new EntityView(List.of(new EntityView.Entity("Pet"), new EntityView.Entity("Owner")),
				List.of(new EntityView.Relationship("Pet", "Owner", cardinality, "owner")));
		return EntityMermaidRenderer.render(view);
	}

	private static String renderRequired(Cardinality cardinality, boolean required) {
		EntityView view = new EntityView(List.of(new EntityView.Entity("Pet"), new EntityView.Entity("Owner")),
				List.of(new EntityView.Relationship("Pet", "Owner", cardinality, "owner", false, required)));
		return EntityMermaidRenderer.render(view);
	}

	@Test
	void rendersManyToOne() {
		assertEquals("erDiagram\n    \"Pet\"\n    \"Owner\"\n    \"Pet\" }o--o| \"Owner\" : \"owner\"\n",
				renderRelationship(Cardinality.MANY_TO_ONE));
	}

	@Test
	void rendersOptionalManyToOne() {
		assertTrue(renderRequired(Cardinality.MANY_TO_ONE, false).contains("\"Pet\" }o--o| \"Owner\""));
	}

	@Test
	void rendersRequiredManyToOne() {
		assertTrue(renderRequired(Cardinality.MANY_TO_ONE, true).contains("\"Pet\" }o--|| \"Owner\""));
	}

	@Test
	void rendersOptionalAndRequiredOneToOne() {
		assertTrue(renderRequired(Cardinality.ONE_TO_ONE, false).contains("\"Pet\" ||--o| \"Owner\""));
		assertTrue(renderRequired(Cardinality.ONE_TO_ONE, true).contains("\"Pet\" ||--|| \"Owner\""));
	}

	@Test
	void requiredDoesNotChangeToManyEnds() {
		for (boolean required : List.of(false, true)) {
			assertTrue(renderRequired(Cardinality.ONE_TO_MANY, required)
					.contains("\"Pet\" ||--o{ \"Owner\""));
			assertTrue(renderRequired(Cardinality.MANY_TO_MANY, required)
					.contains("\"Pet\" }o--o{ \"Owner\""));
		}
	}

	@Test
	void inferredManyToOneStaysOnlyOne() {
		EntityView view = new EntityView(List.of(new EntityView.Entity("Visit"), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Visit", "Pet", Cardinality.MANY_TO_ONE, "petId", true,
						false)));

		assertTrue(EntityMermaidRenderer.render(view).contains("\"Visit\" }o..|| \"Pet\""));
	}

	@Test
	void rendersOneToMany() {
		assertTrue(renderRelationship(Cardinality.ONE_TO_MANY).contains("\"Pet\" ||--o{ \"Owner\""));
	}

	@Test
	void rendersOneToOne() {
		assertTrue(renderRelationship(Cardinality.ONE_TO_ONE).contains("\"Pet\" ||--o| \"Owner\""));
	}

	@Test
	void rendersManyToMany() {
		assertTrue(renderRelationship(Cardinality.MANY_TO_MANY).contains("\"Pet\" }o--o{ \"Owner\""));
	}

	@Test
	void rendersRelationshipsAfterEntityBlocks() {
		EntityView view = new EntityView(List.of(owner(), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Pet", "Owner", Cardinality.MANY_TO_ONE, "owner")));

		assertEquals("erDiagram\n    \"Owner\" {\n        Long id PK\n        String name\n    }\n    \"Pet\"\n"
				+ "    \"Pet\" }o--o| \"Owner\" : \"owner\"\n", EntityMermaidRenderer.render(view));
	}

	@Test
	void rendersInferredRelationshipDotted() {
		EntityView view = new EntityView(List.of(new EntityView.Entity("Visit"), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Visit", "Pet", Cardinality.MANY_TO_ONE, "petId", true),
						new EntityView.Relationship("Owner", "Pet", Cardinality.ONE_TO_MANY, "petIds", true)));

		String out = EntityMermaidRenderer.render(view);

		assertTrue(out.contains("    \"Visit\" }o..|| \"Pet\" : \"petId\"\n"), out);
		assertTrue(out.contains("    \"Owner\" ||..o{ \"Pet\" : \"petIds\"\n"), out);
	}

	@Test
	void rendersForeignKeyMarker() {
		var view = new EntityView(List.of(new EntityView.Entity("Pet",
				List.of(new EntityView.Attribute("OwnerId", "ownerId", false, true)))));

		assertEquals("erDiagram\n    \"Pet\" {\n        OwnerId ownerId FK\n    }\n",
				EntityMermaidRenderer.render(view));
	}

	@Test
	void rendersNullableTypeWithQuestionMark() {
		var view = new EntityView(List.of(new EntityView.Entity("Pet",
				List.of(new EntityView.Attribute("Long", "id", true, false, false),
						new EntityView.Attribute("String", "name", false, false, true),
						new EntityView.Attribute("String", "code", false, false, false),
						new EntityView.Attribute("List<String>", "tags", false, false, true),
						new EntityView.Attribute("OwnerId", "ownerId", false, true, true),
						new EntityView.Attribute("String", "legacy", false)))));

		assertEquals("erDiagram\n    \"Pet\" {\n        Long id PK\n        String? name\n        String code\n"
				+ "        List~String~? tags\n        OwnerId? ownerId FK\n        String legacy\n    }\n",
				EntityMermaidRenderer.render(view));
	}

	private static String renderAttribute(EntityView.Attribute a) {
		return EntityMermaidRenderer.render(new EntityView(List.of(new EntityView.Entity("Owner", List.of(a)))));
	}

	@Test
	void rendersTableNameAsAlias() {
		var view = new EntityView(List.of(new EntityView.Entity("Owner", owner().attributes(), "owners"),
				new EntityView.Entity("Tag", List.of(), "tags")));

		String out = EntityMermaidRenderer.render(view);

		assertTrue(out.startsWith("erDiagram\n    \"Owner\"[\"owners\"] {\n"), out);
		assertTrue(out.contains("    \"Tag\"[\"tags\"]\n"), out);
	}

	@Test
	void entityWithoutTableNameIsUnchanged() {
		var view = new EntityView(List.of(new EntityView.Entity("Owner", owner().attributes(), null)));

		assertEquals("erDiagram\n    \"Owner\" {\n        Long id PK\n        String name\n    }\n",
				EntityMermaidRenderer.render(view));
	}

	@Test
	void relationshipUsesEntityNameNotAlias() {
		var view = new EntityView(
				List.of(new EntityView.Entity("Owner", List.of(), "owners"),
						new EntityView.Entity("Pet", List.of(), "pets")),
				List.of(new EntityView.Relationship("Pet", "Owner", Cardinality.MANY_TO_ONE, "owner")));

		assertTrue(EntityMermaidRenderer.render(view).endsWith("    \"Pet\" }o--o| \"Owner\" : \"owner\"\n"));
	}

	@Test
	void rendersColumnNameWithJavaNameCommentLine() {
		assertEquals("erDiagram\n    \"Owner\" {\n        %% fullName\n        String full_name\n    }\n",
				renderAttribute(new EntityView.Attribute("String", "fullName", false, false, false, "full_name")));
	}

	@Test
	void columnNameKeepsNullabilityAndKeys() {
		assertTrue(renderAttribute(new EntityView.Attribute("String", "fullName", false, false, true, "full_name"))
				.contains("        %% fullName\n        String? full_name\n"));
		assertTrue(renderAttribute(new EntityView.Attribute("Long", "id", true, false, false, "owner_id"))
				.contains("        %% id\n        Long owner_id PK\n"));
	}

	@Test
	void invalidColumnIdentifierGoesToCommentLine() {
		assertTrue(renderAttribute(new EntityView.Attribute("String", "fullName", false, false, true, "full name"))
				.contains("        %% column: full name\n        String? fullName\n"));
		assertTrue(renderAttribute(new EntityView.Attribute("String", "key", false, false, false, "pk"))
				.contains("        %% column: pk\n        String key\n"));
	}

	@Test
	void quotesInCommentLines() {
		assertTrue(renderAttribute(new EntityView.Attribute("String", "nick", false, false, false, "say \"hi\""))
				.contains("        %% column: say \"hi\"\n        String nick\n"));
	}

	@Test
	void columnNameWithNewlineStaysOnOneCommentLine() {
		assertTrue(renderAttribute(new EntityView.Attribute("String", "nick", false, false, false, "say \"hi\"\r\nnow"))
				.contains("        %% column: say \"hi\" now\n        String nick\n"));
	}

	@Test
	void tableNameWithNewlineStaysOnOneCommentLine() {
		var view = new EntityView(List.of(new EntityView.Entity("Owner", List.of(), "pet\nstats")));

		assertEquals("erDiagram\n    %% table: pet stats\n    \"Owner\"\n", EntityMermaidRenderer.render(view));
	}

	@Test
	void tableNameMermaidRejectsGoesToCommentLine() {
		for (String table : List.of("a\"b", "50%", "a\\b")) {
			var view = new EntityView(List.of(new EntityView.Entity("Owner", List.of(), table)));

			assertEquals("erDiagram\n    %% table: " + table + "\n    \"Owner\"\n", EntityMermaidRenderer.render(view));
		}
	}

	@Test
	void hiddenInferredNoteComesFirst() {
		var view = new EntityView(List.of(new EntityView.Entity("Owner")), List.of(), true);

		assertEquals(EntityMermaidRenderer.HIDDEN_INFERRED_NOTE + "\nerDiagram\n    \"Owner\"\n",
				EntityMermaidRenderer.render(view));
		assertEquals("erDiagram\n    \"Owner\"\n",
				EntityMermaidRenderer.render(new EntityView(List.of(new EntityView.Entity("Owner")))));
	}

	@Test
	void inferredRelationshipsComeAfterNote() {
		var view = new EntityView(List.of(new EntityView.Entity("Owner"), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Pet", "Owner", Cardinality.MANY_TO_ONE, "ownerId", true),
						new EntityView.Relationship("Pet", "Owner", Cardinality.MANY_TO_ONE, "owner")));

		assertEquals("erDiagram\n    \"Owner\"\n    \"Pet\"\n"
				+ "    \"Pet\" }o--o| \"Owner\" : \"owner\"\n"
				+ "\n"
				+ "    %% " + EntityMermaidRenderer.INFERRED_NOTE + "\n"
				+ "    \"Pet\" }o..|| \"Owner\" : \"ownerId\"\n", EntityMermaidRenderer.render(view));
	}

	@Test
	void noInferredNoteWithoutInferredRelationships() {
		var view = new EntityView(List.of(new EntityView.Entity("Owner"), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Pet", "Owner", Cardinality.MANY_TO_ONE, "owner")));

		assertFalse(EntityMermaidRenderer.render(view).contains("%%"));
	}

	private static EntityView.Relationship catalog(String from, String to, String label, boolean required,
			boolean dotted) {
		return new EntityView.Relationship(from, to, Cardinality.MANY_TO_ONE, label, false, required, true, dotted);
	}

	@Test
	void groupsDeclaredCatalogAndInferred() {
		// CR1
		var view = new EntityView(List.of(new EntityView.Entity("Owner"), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Pet", "Owner", Cardinality.MANY_TO_ONE, "ownerId", true),
						catalog("Pet", "Owner", "breeder", true, false),
						new EntityView.Relationship("Pet", "Owner", Cardinality.MANY_TO_ONE, "owner")));

		assertEquals("erDiagram\n    \"Owner\"\n    \"Pet\"\n"
				+ "    \"Pet\" }o--o| \"Owner\" : \"owner\"\n"
				+ "\n"
				+ "    %% " + EntityMermaidRenderer.CATALOG_NOTE + "\n"
				+ "    \"Pet\" }o--|| \"Owner\" : \"breeder\"\n"
				+ "\n"
				+ "    %% " + EntityMermaidRenderer.INFERRED_NOTE + "\n"
				+ "    \"Pet\" }o..|| \"Owner\" : \"ownerId\"\n", EntityMermaidRenderer.render(view));
	}

	@Test
	void catalogRelationshipUsesRequired() {
		// CR2
		var entities = List.of(new EntityView.Entity("Owner"), new EntityView.Entity("Pet"));
		assertTrue(EntityMermaidRenderer.render(new EntityView(entities, List.of(catalog("Pet", "Owner", "o", true, false))))
				.contains("\"Pet\" }o--|| \"Owner\" : \"o\""));
		assertTrue(EntityMermaidRenderer.render(new EntityView(entities, List.of(catalog("Pet", "Owner", "o", false, false))))
				.contains("\"Pet\" }o--o| \"Owner\" : \"o\""));
	}

	@Test
	void noCatalogNoteWithoutCatalogRelationships() {
		// CR3
		var view = new EntityView(List.of(new EntityView.Entity("Owner"), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Pet", "Owner", Cardinality.MANY_TO_ONE, "owner")));

		assertFalse(EntityMermaidRenderer.render(view).contains(EntityMermaidRenderer.CATALOG_NOTE));
	}

	@Test
	void dottedCatalogRelationshipStaysInCatalogGroup() {
		// CR4
		var view = new EntityView(List.of(new EntityView.Entity("Owner"), new EntityView.Entity("Pet")),
				List.of(catalog("Pet", "Owner", "a", true, true), catalog("Pet", "Owner", "b", false, true)));

		assertEquals("erDiagram\n    \"Owner\"\n    \"Pet\"\n"
				+ "\n"
				+ "    %% " + EntityMermaidRenderer.CATALOG_NOTE + "\n"
				+ "    \"Pet\" }o..|| \"Owner\" : \"a\"\n"
				+ "    \"Pet\" }o..o| \"Owner\" : \"b\"\n", EntityMermaidRenderer.render(view));
	}
}
