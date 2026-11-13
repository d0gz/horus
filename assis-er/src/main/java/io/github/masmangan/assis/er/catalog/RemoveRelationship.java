package io.github.masmangan.assis.er.catalog;

import java.util.List;

import io.github.masmangan.assis.er.EntityView;

/**
 * Removes the relationship with this from, to and label.
 */
record RemoveRelationship(int line, String from, String to, String label) implements CatalogEntry {

	@Override
	public Phase phase() {
		return Phase.REMOVE;
	}

	@Override
	public EntityView applyTo(EntityView view, List<String> warnings) {
		List<EntityView.Relationship> kept = view.relationships().stream()
				.filter(r -> !(r.from().equals(from) && r.to().equals(to) && r.label().equals(label))).toList();
		if (kept.size() == view.relationships().size()) {
			warnings.add("Line " + line + ": relationship " + from + " -> " + to + " '" + label
					+ "' not found, nothing removed");
			return view;
		}
		return new EntityView(view.entities(), kept, view.hiddenInferred());
	}
}
