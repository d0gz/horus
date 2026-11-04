package io.github.masmangan.assis.er;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	private static String renderRelationship(EntityView.Cardinality cardinality) {
		EntityView view = new EntityView(List.of(new EntityView.Entity("Pet"), new EntityView.Entity("Owner")),
				List.of(new EntityView.Relationship("Pet", "Owner", cardinality, "owner")));
		return EntityMermaidRenderer.render(view);
	}

	@Test
	void rendersManyToOne() {
		assertEquals("erDiagram\n    \"Pet\"\n    \"Owner\"\n    \"Pet\" }o--|| \"Owner\" : \"owner\"\n",
				renderRelationship(EntityView.Cardinality.MANY_TO_ONE));
	}

	@Test
	void rendersOneToMany() {
		assertTrue(renderRelationship(EntityView.Cardinality.ONE_TO_MANY).contains("\"Pet\" ||--o{ \"Owner\""));
	}

	@Test
	void rendersOneToOne() {
		assertTrue(renderRelationship(EntityView.Cardinality.ONE_TO_ONE).contains("\"Pet\" ||--|| \"Owner\""));
	}

	@Test
	void rendersManyToMany() {
		assertTrue(renderRelationship(EntityView.Cardinality.MANY_TO_MANY).contains("\"Pet\" }o--o{ \"Owner\""));
	}

	@Test
	void rendersRelationshipsAfterEntityBlocks() {
		EntityView view = new EntityView(List.of(owner(), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Pet", "Owner", EntityView.Cardinality.MANY_TO_ONE, "owner")));

		assertEquals("erDiagram\n    \"Owner\" {\n        Long id PK\n        String name\n    }\n    \"Pet\"\n"
				+ "    \"Pet\" }o--|| \"Owner\" : \"owner\"\n", EntityMermaidRenderer.render(view));
	}

	@Test
	void rendersInferredRelationshipDotted() {
		EntityView view = new EntityView(List.of(new EntityView.Entity("Visit"), new EntityView.Entity("Pet")),
				List.of(new EntityView.Relationship("Visit", "Pet", EntityView.Cardinality.MANY_TO_ONE, "petId", true),
						new EntityView.Relationship("Owner", "Pet", EntityView.Cardinality.ONE_TO_MANY, "petIds", true)));

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
}
