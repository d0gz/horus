package io.github.masmangan.assis.er;

import java.util.List;

/**
 * In-memory model of the JPA entities found in a code base, independent of
 * JavaParser and of Mermaid.
 *
 * The extractor fills an  EntityView; the renderer consumes it.
 *
 * @param entities entities in deterministic order
 */
public record EntityView(List<Entity> entities) {

	public EntityView {
		entities = List.copyOf(entities);
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
