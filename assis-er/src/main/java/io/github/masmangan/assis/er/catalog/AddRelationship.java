package io.github.masmangan.assis.er.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.masmangan.assis.er.EntityView;
import io.github.masmangan.assis.er.types.Cardinality;

/**
 * Adds a relationship between two entities of the view. An extracted or earlier
 * catalog relationship with the same from, to and label is replaced.
 */
record AddRelationship(int line, String from, String to, Cardinality cardinality, String label, boolean required,
		boolean dotted) implements CatalogEntry {

	@Override
	public Phase phase() {
		return Phase.ADD_RELATIONSHIP;
	}

	@Override
	public EntityView applyTo(EntityView view, List<String> warnings) {
		Set<String> names = view.entities().stream().map(EntityView.Entity::name).collect(Collectors.toSet());
		for (String name : List.of(from, to)) {
			if (!names.contains(name)) {
				warnings.add("Line " + line + ": entity '" + name + "' not found, relationship skipped");
				return view;
			}
		}
		List<EntityView.Relationship> relationships = new ArrayList<>();
		for (EntityView.Relationship r : view.relationships()) {
			if (r.from().equals(from) && r.to().equals(to) && r.label().equals(label)) {
				if (r.fromCatalog()) {
					warnings.add("Line " + line + ": relationship " + from + " -> " + to + " '" + label
							+ "' was already in the catalog, the last one wins");
				}
			} else {
				relationships.add(r);
			}
		}
		relationships.add(new EntityView.Relationship(from, to, cardinality, label, false, required, true, dotted));
		return new EntityView(view.entities(), relationships, view.hiddenInferred());
	}

	static boolean isToOne(Cardinality cardinality) {
		return cardinality == Cardinality.MANY_TO_ONE || cardinality == Cardinality.ONE_TO_ONE;
	}
}
