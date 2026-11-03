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
			sb.append(INDENT).append('"').append(entity.name()).append('"');
			if (entity.attributes().isEmpty()) {
				sb.append('\n');
				continue;
			}
			sb.append(" {\n");
			for (EntityView.Attribute a : entity.attributes()) {
				sb.append(INDENT).append(INDENT).append(mermaidType(a.type())).append(' ').append(a.name());
				if (a.primaryKey()) {
					sb.append(" PK");
				}
				sb.append('\n');
			}
			sb.append(INDENT).append("}\n");
		}
		return sb.toString();
	}

	/** Mermaid rejects dots and angle brackets in attribute types outside ~...~. */
	static String mermaidType(String javaType) {
		return javaType.replaceAll("([A-Za-z_$][\\w$]*\\.)+", "")
				.replace('<', '~')
				.replace('>', '~')
				.replaceAll(",\\s+", ",");
	}
}
