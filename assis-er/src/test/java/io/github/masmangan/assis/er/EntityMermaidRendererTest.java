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
}
