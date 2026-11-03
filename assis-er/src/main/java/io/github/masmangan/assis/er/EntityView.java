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
	 * A single entity.
	 *
	 * @param name simple name of the entity class
	 */
	public record Entity(String name) {
	}
}
