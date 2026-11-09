package io.github.masmangan.assis.er.catalog;

import java.util.List;

import io.github.masmangan.assis.er.EntityView;

/**
 * Removes an entity and every relationship from or to it.
 */
record RemoveEntity(int line, String name) implements CatalogEntry {

	@Override
	public Phase phase() {
		return Phase.REMOVE;
	}

	@Override
	public EntityView applyTo(EntityView view, List<String> warnings) {
		List<EntityView.Entity> kept = view.entities().stream().filter(e -> !e.name().equals(name)).toList();
		if (kept.size() == view.entities().size()) {
			warnings.add("Line " + line + ": entity '" + name + "' not found, nothing removed");
			return view;
		}
		List<EntityView.Relationship> relationships = view.relationships().stream()
				.filter(r -> !r.from().equals(name) && !r.to().equals(name)).toList();
		return new EntityView(kept, relationships, view.hiddenInferred());
	}
}
