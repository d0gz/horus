package io.github.masmangan.assis.er.catalog;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.masmangan.assis.er.types.Cardinality;

/**
 * Reads catalog lines written as in the generated entity-diagram.mmd.
 */
final class MermaidLine {

	private static final String NAME = "(?:\"([^\"]*)\"|([A-Za-z_][A-Za-z0-9_-]*))";
	private static final Pattern RELATIONSHIP = Pattern
			.compile(NAME + "\\s+(\\S+)\\s+" + NAME + "\\s*:\\s*" + NAME);
	private static final Pattern SYMBOL = Pattern.compile("([}|][o|])(--|\\.\\.)([o|][{|])");
	private static final Pattern ENTITY_LIKE = Pattern
			.compile(NAME + "\\s*(\\[\"[^\"]*\"\\])?\\s*(\\{.*\\})?");

	private MermaidLine() {
	}

	static boolean looksLikeEntity(String text) {
		return ENTITY_LIKE.matcher(text).matches();
	}

	/** Relationship line, or null with a warning added. */
	static AddRelationship relationship(int line, String text, List<String> warnings) {
		Matcher m = RELATIONSHIP.matcher(text);
		if (!m.matches()) {
			warnings.add("Line " + line + " ignored: expected <from> <symbol> <to> : <label>");
			return null;
		}
		String from = name(m, 1);
		String to = name(m, 4);
		String label = name(m, 6);
		if (from.isBlank() || to.isBlank() || label.isBlank()) {
			warnings.add("Line " + line + " ignored: empty entity name or label");
			return null;
		}
		Matcher s = SYMBOL.matcher(m.group(3));
		if (!s.matches()) {
			warnings.add("Line " + line + " ignored: unknown relationship symbol '" + m.group(3) + "'");
			return null;
		}
		String left = s.group(1);
		String right = s.group(3);
		boolean leftMany = left.startsWith("}");
		boolean rightMany = right.endsWith("{");
		Cardinality cardinality = leftMany ? (rightMany ? Cardinality.MANY_TO_MANY : Cardinality.MANY_TO_ONE)
				: (rightMany ? Cardinality.ONE_TO_MANY : Cardinality.ONE_TO_ONE);
		for (String end : List.of(left, right)) {
			if (end.equals("}|") || end.equals("|o") || end.equals("|{")) {
				warnings.add("Line " + line + ": optionality of the '" + end + "' end is not kept");
			}
		}
		boolean required = !rightMany && right.equals("||");
		return new AddRelationship(line, from, to, cardinality, label, required, s.group(2).equals(".."));
	}

	private static String name(Matcher m, int quotedGroup) {
		String quoted = m.group(quotedGroup);
		return quoted != null ? quoted : m.group(quotedGroup + 1);
	}
}
