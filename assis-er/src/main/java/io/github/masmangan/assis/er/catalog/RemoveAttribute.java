package io.github.masmangan.assis.er.catalog;

import java.util.ArrayList;
import java.util.List;

import io.github.masmangan.assis.er.EntityView;

/**
 * Removes the attribute with this name, else with this column name.
 */
record RemoveAttribute(int line, String entity, String name) implements CatalogEntry {

	@Override
	public Phase phase() {
		return Phase.REMOVE;
	}

	@Override
	public EntityView applyTo(EntityView view, List<String> warnings) {
		List<EntityView.Entity> entities = new ArrayList<>(view.entities());
		for (int i = 0; i < entities.size(); i++) {
			EntityView.Entity e = entities.get(i);
			if (!e.name().equals(entity)) {
				continue;
			}
			List<EntityView.Attribute> attributes = new ArrayList<>(e.attributes());
			if (!attributes.removeIf(a -> a.name().equals(name))
					&& !attributes.removeIf(a -> name.equals(a.columnName()))) {
				warnings.add("Line " + line + ": attribute " + entity + "." + name + " not found, nothing removed");
				return view;
			}
			entities.set(i, new EntityView.Entity(entity, attributes, e.tableName(), e.fromCatalog()));
			return new EntityView(entities, view.relationships(), view.hiddenInferred());
		}
		warnings.add("Line " + line + ": entity '" + entity + "' not found, nothing removed");
		return view;
	}
}
