package io.github.masmangan.assis.er;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Renders an EntityView as a Mermaid erDiagram.
 */
public final class EntityMermaidRenderer {

	static final String INFERRED_NOTE = "Dotted lines below are inferred relationships and are not certain";

	static final String CATALOG_NOTE = "Relationships below come from the catalog";

	static final String FROM_CATALOG_NOTE = "from catalog";

	static final String HIDDEN_INFERRED_NOTE = "%% There could be relationships that were not shown"
			+ " - use '--inferred' to see the inferred relationships";

	private static final String INDENT = "    ";
	/** ATTRIBUTE_WORD of erDiagram.jison, minus the case-insensitive key words. */
	private static final Pattern ATTRIBUTE_WORD = Pattern
			.compile("(?i)(?!(PK|FK|UK)$)[*A-Za-z_][A-Za-z0-9\\-_\\[\\]()]*");
	/** ENTITY_NAME of erDiagram.jison; aliases are not unescaped. */
	private static final Pattern ENTITY_NAME = Pattern.compile("[^\"%\\r\\n\\x0B\\x08\\\\]+");

	private EntityMermaidRenderer() {
	}

	/** True when the name can be written as a quoted entity name or alias. */
	public static boolean isEntityName(String name) {
		return ENTITY_NAME.matcher(name).matches();
	}

	/** True when the name can be written as a bare attribute name. */
	public static boolean isAttributeWord(String name) {
		return ATTRIBUTE_WORD.matcher(name).matches();
	}

	/**
	 * Renders the view as Mermaid text.
	 *
	 * @param view entities to render
	 * @return Mermaid erDiagram source
	 */
	public static String render(EntityView view) {
		StringBuilder sb = new StringBuilder();
		if (view.hiddenInferred()) {
			sb.append(HIDDEN_INFERRED_NOTE).append('\n');
		}
		sb.append("erDiagram\n");
		for (EntityView.Entity entity : view.entities()) {
			boolean alias = entity.tableName() != null && ENTITY_NAME.matcher(entity.tableName()).matches();
			if (entity.fromCatalog()) {
				appendNote(sb, INDENT, FROM_CATALOG_NOTE);
			}
			if (entity.tableName() != null && !alias) {
				appendNote(sb, INDENT, "table: " + entity.tableName());
			}
			sb.append(INDENT).append('"').append(entity.name()).append('"');
			if (alias) {
				sb.append("[\"").append(entity.tableName()).append("\"]");
			}
			if (entity.attributes().isEmpty()) {
				sb.append('\n');
				continue;
			}
			sb.append(" {\n");
			for (EntityView.Attribute a : entity.attributes()) {
				String name = a.name();
				if (a.fromCatalog()) {
					appendNote(sb, INDENT + INDENT, FROM_CATALOG_NOTE);
				}
				if (a.columnName() != null && ATTRIBUTE_WORD.matcher(a.columnName()).matches()) {
					name = a.columnName();
					appendNote(sb, INDENT + INDENT, a.name());
				} else if (a.columnName() != null) {
					appendNote(sb, INDENT + INDENT, "column: " + a.columnName());
				}
				sb.append(INDENT).append(INDENT).append(mermaidType(a.type())).append(a.nullable() ? "?" : "")
						.append(' ').append(name);
				if (a.primaryKey()) {
					sb.append(" PK");
				}
				if (a.foreignKey()) {
					sb.append(" FK");
				}
				sb.append('\n');
			}
			sb.append(INDENT).append("}\n");
		}
		List<EntityView.Relationship> catalog = new ArrayList<>();
		List<EntityView.Relationship> inferred = new ArrayList<>();
		for (EntityView.Relationship r : view.relationships()) {
			if (r.fromCatalog()) {
				catalog.add(r);
			} else if (r.inferred()) {
				inferred.add(r);
			} else {
				appendRelationship(sb, r);
			}
		}
		appendGroup(sb, CATALOG_NOTE, catalog);
		appendGroup(sb, INFERRED_NOTE, inferred);
		return sb.toString();
	}

	private static void appendGroup(StringBuilder sb, String note, List<EntityView.Relationship> relationships) {
		if (relationships.isEmpty()) {
			return;
		}
		sb.append('\n');
		appendNote(sb, INDENT, note);
		for (EntityView.Relationship r : relationships) {
			appendRelationship(sb, r);
		}
	}

	private static void appendRelationship(StringBuilder sb, EntityView.Relationship r) {
		sb.append(INDENT).append('"').append(r.from()).append("\" ").append(symbol(r))
				.append(" \"").append(r.to()).append("\" : \"").append(r.label()).append("\"\n");
	}

	private static String symbol(EntityView.Relationship r) {
		String one = r.required() || r.inferred() ? "||" : "o|";
		String symbol = switch (r.cardinality()) {
			case MANY_TO_ONE -> "}o--" + one;
			case ONE_TO_MANY -> "||--o{";
			case ONE_TO_ONE -> "||--" + one;
			case MANY_TO_MANY -> "}o--o{";
		};
		return r.inferred() || r.dotted() ? symbol.replace("--", "..") : symbol;
	}

	/** Whole-line Mermaid comment, so it never breaks the attribute line. */
	private static void appendNote(StringBuilder sb, String indent, String note) {
		sb.append(indent).append("%% ").append(note.replaceAll("[\\r\\n]+", " ")).append('\n');
	}

	/** Mermaid rejects dots and angle brackets in attribute types outside ~...~. */
	static String mermaidType(String javaType) {
		return javaType.replaceAll("([A-Za-z_$][\\w$]*\\.)+", "")
				.replace('<', '~')
				.replace('>', '~')
				.replaceAll(",\\s+", ",");
	}
}
