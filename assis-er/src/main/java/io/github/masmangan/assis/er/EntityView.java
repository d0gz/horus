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
 * @param relationships relationships in entity, then field order
 */
public record EntityView(List<Entity> entities, List<Relationship> relationships) {

	public EntityView {
		entities = List.copyOf(entities);
		relationships = List.copyOf(relationships);
	}

	public EntityView(List<Entity> entities) {
		this(entities, List.of());
	}

	/**
	 * @param label    owning field name
	 * @param inferred true when deduced from an ID-typed field rather than a JPA
	 *                 relationship annotation; cardinality is then assumed
	 * @param required true when the target end of an annotated MANY_TO_ONE or
	 *                 ONE_TO_ONE is mandatory (optional = false or
	 *                 JoinColumn nullable = false)
	 */
	public record Relationship(String from, String to, Cardinality cardinality, String label, boolean inferred,
			boolean required) {

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
	 * @param tableName  Table name when it differs from the class name, else null
	 */
	public record Entity(String name, List<Attribute> attributes, String tableName) {

		public Entity {
			attributes = List.copyOf(attributes);
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
	 * @param columnName Column name when it differs from the field name, else null
	 */
	public record Attribute(String type, String name, boolean primaryKey, boolean foreignKey, boolean nullable,
			String columnName) {

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
