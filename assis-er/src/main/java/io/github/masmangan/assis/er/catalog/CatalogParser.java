package io.github.masmangan.assis.er.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.masmangan.assis.er.types.Cardinality;

/**
 * Catalog text to entries. Each line is in field form (add relationship |
 * Visit | Pet | many-to-one | petId) or Mermaid form (add "Visit" }o--|| "Pet"
 * : "petId").
 */
final class CatalogParser {

	record Result(List<CatalogEntry> entries, List<String> warnings) {
	}

	private static final Pattern FIELD_FORM = Pattern.compile("(?i)(relationship|entity|attribute)\\s*\\|.*");
	private static final Pattern UNKNOWN_KIND = Pattern.compile("([A-Za-z]+)\\s*\\|(?![|o{}]).*");
	private static final Map<String, Cardinality> CARDINALITIES = Map.of(
			"manytoone", Cardinality.MANY_TO_ONE,
			"onetomany", Cardinality.ONE_TO_MANY,
			"onetoone", Cardinality.ONE_TO_ONE,
			"manytomany", Cardinality.MANY_TO_MANY);

	private CatalogParser() {
	}

	static Result parse(String text) {
		List<CatalogEntry> entries = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		String[] lines = text.split("\\R", -1);
		for (int n = 0; n < lines.length; n++) {
			String line = lines[n].trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			CatalogEntry entry = parseLine(n + 1, line, warnings);
			if (entry != null) {
				entries.add(entry);
			}
		}
		return new Result(entries, warnings);
	}

	private static CatalogEntry parseLine(int line, String text, List<String> warnings) {
		String[] split = text.split("\\s+", 2);
		String operation = split[0].toLowerCase(Locale.ROOT);
		String rest = split.length > 1 ? split[1].trim() : "";
		if (!operation.equals("add") && !operation.equals("remove")) {
			warnings.add("Line " + line + " ignored: unknown operation '" + split[0] + "'");
			return null;
		}
		if (rest.isEmpty()) {
			warnings.add("Line " + line + " ignored: nothing after '" + split[0] + "'");
			return null;
		}
		Matcher field = FIELD_FORM.matcher(rest);
		if (field.matches()) {
			String kind = field.group(1).toLowerCase(Locale.ROOT);
			if (operation.equals("add") && kind.equals("relationship")) {
				return fieldRelationship(line, rest, warnings);
			}
			return notSupported(line, operation + " " + kind, warnings);
		}
		Matcher unknown = UNKNOWN_KIND.matcher(rest);
		if (unknown.matches()) {
			warnings.add("Line " + line + " ignored: unknown kind '" + unknown.group(1) + "'");
			return null;
		}
		if (operation.equals("remove")) {
			return notSupported(line, "remove", warnings);
		}
		if (MermaidLine.looksLikeEntity(rest)) {
			return notSupported(line, "add entity", warnings);
		}
		return MermaidLine.relationship(line, rest, warnings);
	}

	private static CatalogEntry fieldRelationship(int line, String text, List<String> warnings) {
		String[] fields = text.split("\\|", -1);
		if (fields.length < 5 || fields.length > 6) {
			warnings.add("Line " + line
					+ " ignored: expected add relationship | <from> | <to> | <cardinality> | <label> [| <flags>]");
			return null;
		}
		String from = fields[1].trim();
		String to = fields[2].trim();
		String label = fields[4].trim();
		if (from.isEmpty() || to.isEmpty() || label.isEmpty()) {
			warnings.add("Line " + line + " ignored: empty entity name or label");
			return null;
		}
		if ((from + to + label).contains("\"")) {
			warnings.add("Line " + line + " ignored: names and label must not contain '\"'");
			return null;
		}
		String rawCardinality = fields[3].trim();
		Cardinality cardinality = CARDINALITIES
				.get(rawCardinality.replaceAll("[-_\\s]", "").toLowerCase(Locale.ROOT));
		if (cardinality == null) {
			warnings.add("Line " + line + " ignored: unknown cardinality '" + rawCardinality + "'");
			return null;
		}
		boolean required = false;
		boolean dotted = false;
		if (fields.length == 6) {
			for (String raw : fields[5].split(",")) {
				String flag = raw.trim().toLowerCase(Locale.ROOT);
				if (flag.equals("required")) {
					required = true;
				} else if (flag.equals("dotted")) {
					dotted = true;
				} else if (!flag.isEmpty()) {
					warnings.add("Line " + line + ": unknown flag '" + raw.trim() + "' ignored");
				}
			}
		}
		if (required && !AddRelationship.isToOne(cardinality)) {
			warnings.add("Line " + line + ": 'required' only applies to many-to-one and one-to-one, ignored");
			required = false;
		}
		return new AddRelationship(line, from, to, cardinality, label, required, dotted);
	}

	private static CatalogEntry notSupported(int line, String what, List<String> warnings) {
		warnings.add("Line " + line + ": '" + what + "' is not supported yet");
		return null;
	}
}
