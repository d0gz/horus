package io.github.masmangan.assis.er;

import java.util.List;

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

	public enum Cardinality {
		MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE, MANY_TO_MANY
	}

	/**
	 * @param label owning field name
	 */
	public record Relationship(String from, String to, Cardinality cardinality, String label) {
	}

	/**
	 * @param name       simple name of the entity class
	 * @param attributes persistent fields, in declaration order
	 */
	public record Entity(String name, List<Attribute> attributes) {

		public Entity {
			attributes = List.copyOf(attributes);
		}

		public Entity(String name) {
			this(name, List.of());
		}
	}

	/**
	 * @param type Java type as written in source
	 */
	public record Attribute(String type, String name, boolean primaryKey) {
	}
}
