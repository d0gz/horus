package io.github.masmangan.assis.er.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.masmangan.assis.er.EntityMermaidRenderer;
import io.github.masmangan.assis.er.EntityView;
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
	private static final Pattern INVALID_TYPE = Pattern.compile("[\"{};\\r\\n]");
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
			entries.addAll(parseLine(n + 1, line, warnings));
		}
		return new Result(entries, warnings);
	}

	private static List<CatalogEntry> parseLine(int line, String text, List<String> warnings) {
		String[] split = text.split("\\s+", 2);
		String operation = split[0].toLowerCase(Locale.ROOT);
		String rest = split.length > 1 ? split[1].trim() : "";
		if (!operation.equals("add") && !operation.equals("remove")) {
			warnings.add("Line " + line + " ignored: unknown operation '" + split[0] + "'");
			return List.of();
		}
		if (rest.isEmpty()) {
			warnings.add("Line " + line + " ignored: nothing after '" + split[0] + "'");
			return List.of();
		}
		Matcher field = FIELD_FORM.matcher(rest);
		if (field.matches()) {
			String kind = field.group(1).toLowerCase(Locale.ROOT);
			if (operation.equals("remove")) {
				return notSupported(line, operation + " " + kind, warnings);
			}
			return switch (kind) {
				case "relationship" -> one(fieldRelationship(line, rest, warnings));
				case "entity" -> one(fieldEntity(line, rest, warnings));
				default -> one(fieldAttribute(line, rest, warnings));
			};
		}
		Matcher unknown = UNKNOWN_KIND.matcher(rest);
		if (unknown.matches()) {
			warnings.add("Line " + line + " ignored: unknown kind '" + unknown.group(1) + "'");
			return List.of();
		}
		if (operation.equals("remove")) {
			return notSupported(line, "remove", warnings);
		}
		if (MermaidLine.looksLikeEntity(rest)) {
			return MermaidLine.entity(line, rest, warnings);
		}
		return one(MermaidLine.relationship(line, rest, warnings));
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

	private static AddEntity fieldEntity(int line, String text, List<String> warnings) {
		String[] fields = text.split("\\|", -1);
		if (fields.length < 2 || fields.length > 3) {
			warnings.add("Line " + line + " ignored: expected add entity | <name> [| <table>]");
			return null;
		}
		String name = fields[1].trim();
		if (!validEntityName(line, name, warnings)) {
			return null;
		}
		String table = fields.length == 3 ? fields[2].trim() : "";
		return new AddEntity(line, name, table.isEmpty() ? null : table);
	}

	private static AddAttribute fieldAttribute(int line, String text, List<String> warnings) {
		String[] fields = text.split("\\|", -1);
		if (fields.length < 4 || fields.length > 5) {
			warnings.add("Line " + line
					+ " ignored: expected add attribute | <entity> | <type> | <name> [| <flags>]");
			return null;
		}
		String entity = fields[1].trim();
		if (!validEntityName(line, entity, warnings)) {
			return null;
		}
		boolean pk = false;
		boolean fk = false;
		boolean nullable = false;
		String column = null;
		if (fields.length == 5) {
			for (String raw : fields[4].split(",")) {
				String flag = raw.trim();
				String lower = flag.toLowerCase(Locale.ROOT);
				if (lower.equals("pk")) {
					pk = true;
				} else if (lower.equals("fk")) {
					fk = true;
				} else if (lower.equals("nullable")) {
					nullable = true;
				} else if (lower.startsWith("column=")) {
					String value = flag.substring("column=".length()).trim();
					if (value.isEmpty()) {
						warnings.add("Line " + line + ": empty column name ignored");
					} else {
						column = value;
					}
				} else if (!flag.isEmpty()) {
					warnings.add("Line " + line + ": unknown flag '" + flag + "' ignored");
				}
			}
		}
		EntityView.Attribute attribute = attribute(line, fields[2].trim(), fields[3].trim(), pk, fk, nullable,
				column, warnings);
		return attribute == null ? null : new AddAttribute(line, entity, attribute);
	}

	static boolean validEntityName(int line, String name, List<String> warnings) {
		if (name.isEmpty() || !EntityMermaidRenderer.isEntityName(name)) {
			warnings.add("Line " + line + " ignored: invalid entity name '" + name + "'");
			return false;
		}
		return true;
	}

	/** Checked attribute, or null with a warning; a trailing ? on the type means nullable. */
	static EntityView.Attribute attribute(int line, String type, String name, boolean pk, boolean fk,
			boolean nullable, String column, List<String> warnings) {
		if (type.endsWith("?")) {
			type = type.substring(0, type.length() - 1).trim();
			nullable = true;
		}
		if (type.isEmpty() || INVALID_TYPE.matcher(type).find()) {
			warnings.add("Line " + line + " ignored: invalid attribute type '" + type + "'");
			return null;
		}
		if (!EntityMermaidRenderer.isAttributeWord(name)) {
			warnings.add("Line " + line + " ignored: invalid attribute name '" + name + "'");
			return null;
		}
		if (pk && nullable) {
			warnings.add("Line " + line + ": 'nullable' does not apply to a primary key, ignored");
			nullable = false;
		}
		return new EntityView.Attribute(type, name, pk, fk, nullable, name.equals(column) ? null : column);
	}

	private static List<CatalogEntry> one(CatalogEntry entry) {
		return entry == null ? List.of() : List.of(entry);
	}

	private static List<CatalogEntry> notSupported(int line, String what, List<String> warnings) {
		warnings.add("Line " + line + ": '" + what + "' is not supported yet");
		return List.of();
	}
}
