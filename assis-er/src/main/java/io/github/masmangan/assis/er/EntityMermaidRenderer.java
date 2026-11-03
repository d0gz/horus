package io.github.masmangan.assis.er;

/**
 * Renders an EntityView as a Mermaid erDiagram.
 */
public final class EntityMermaidRenderer {

	private static final String INDENT = "    ";

	private EntityMermaidRenderer() {
	}

	/**
	 * Renders the view as Mermaid text.
	 *
	 * @param view entities to render
	 * @return Mermaid erDiagram source
	 */
	public static String render(EntityView view) {
		StringBuilder sb = new StringBuilder("erDiagram\n");
		for (EntityView.Entity entity : view.entities()) {
			sb.append(INDENT).append('"').append(entity.name()).append("\"\n");
		}
		return sb.toString();
	}
}
