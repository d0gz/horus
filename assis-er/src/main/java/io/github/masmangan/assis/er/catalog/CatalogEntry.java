package io.github.masmangan.assis.er.catalog;

import java.util.List;

import io.github.masmangan.assis.er.EntityView;

/**
 * One parsed catalog line. Entries are applied by phase, then in file order.
 */
sealed interface CatalogEntry permits AddRelationship {

	enum Phase {
		ADD_ENTITY, ADD_RELATIONSHIP, REMOVE
	}

	int line();

	Phase phase();

	EntityView applyTo(EntityView view, List<String> warnings);
}
