package io.github.masmangan.assis.er;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
