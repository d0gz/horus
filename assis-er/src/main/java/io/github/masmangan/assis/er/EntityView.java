package io.github.masmangan.assis.er;

import java.util.List;

import io.github.masmangan.assis.er.types.Cardinality;

/**
 * In-memory model of the JPA entities found in a code base, independent of
 * JavaParser and of Mermaid.
 *
 * The extractor fills an  EntityView; the renderer consumes it.
 *
 * @param entities      entities in deterministic order
 * @param relationships  relationships in entity, then field order
 * @param hiddenInferred true when inferred relationships exist but were left out
 *                       (inference not enabled)
 */
public record EntityView(List<Entity> entities, List<Relationship> relationships, boolean hiddenInferred) {

	public EntityView {
		entities = List.copyOf(entities);
		relationships = List.copyOf(relationships);
	}

	public EntityView(List<Entity> entities, List<Relationship> relationships) {
		this(entities, relationships, false);
	}

	public EntityView(List<Entity> entities) {
		this(entities, List.of());
	}

	/**
	 * @param label    owning field name
	 * @param inferred true when deduced from an ID-typed field rather than a JPA
	 *                 relationship annotation; cardinality is then assumed
	 * @param required    true when the target end of a MANY_TO_ONE or ONE_TO_ONE
	 *                    is mandatory
	 * @param fromCatalog true when added by the user catalog
	 * @param dotted      true when the catalog asks for a dotted line
	 */
	public record Relationship(String from, String to, Cardinality cardinality, String label, boolean inferred,
			boolean required, boolean fromCatalog, boolean dotted) {

		public Relationship(String from, String to, Cardinality cardinality, String label, boolean inferred,
				boolean required) {
			this(from, to, cardinality, label, inferred, required, false, false);
		}

		public Relationship(String from, String to, Cardinality cardinality, String label, boolean inferred) {
			this(from, to, cardinality, label, inferred, false);
		}

		public Relationship(String from, String to, Cardinality cardinality, String label) {
			this(from, to, cardinality, label, false);
		}
	}

	/**
	 * @param name       simple name of the entity class
	 * @param attributes persistent fields, in declaration order
	 * @param tableName   Table name when it differs from the class name, else null
	 * @param fromCatalog true when created by the catalog or its table set by it
	 */
	public record Entity(String name, List<Attribute> attributes, String tableName, boolean fromCatalog) {

		public Entity {
			attributes = List.copyOf(attributes);
		}

		public Entity(String name, List<Attribute> attributes, String tableName) {
			this(name, attributes, tableName, false);
		}

		public Entity(String name, List<Attribute> attributes) {
			this(name, attributes, null);
		}

		public Entity(String name) {
			this(name, List.of());
		}
	}

	/**
	 * @param type       Java type as written in source
	 * @param foreignKey true for a single-valued field inferred as an ID reference
	 * @param nullable   true unless an id, a primitive, Column nullable = false or
	 *                   Basic optional = false
	 * @param columnName  Column name when it differs from the field name, else null
	 * @param fromCatalog true when added or replaced by the catalog
	 */
	public record Attribute(String type, String name, boolean primaryKey, boolean foreignKey, boolean nullable,
			String columnName, boolean fromCatalog) {

		public Attribute(String type, String name, boolean primaryKey, boolean foreignKey, boolean nullable,
				String columnName) {
			this(type, name, primaryKey, foreignKey, nullable, columnName, false);
		}

		public Attribute(String type, String name, boolean primaryKey, boolean foreignKey, boolean nullable) {
			this(type, name, primaryKey, foreignKey, nullable, null);
		}

		public Attribute(String type, String name, boolean primaryKey, boolean foreignKey) {
			this(type, name, primaryKey, foreignKey, false);
		}

		public Attribute(String type, String name, boolean primaryKey) {
			this(type, name, primaryKey, false);
		}
	}
}
