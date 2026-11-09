package io.github.masmangan.assis.er.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.masmangan.assis.er.EntityView;

/**
 * Creates a missing entity, or sets the table name of an existing one. A null
 * table means none was given; a table equal to the name clears it.
 */
record AddEntity(int line, String name, String table) implements CatalogEntry {

	@Override
	public Phase phase() {
		return Phase.ADD_ENTITY;
	}

	@Override
	public EntityView applyTo(EntityView view, List<String> warnings) {
		String newTable = name.equals(table) ? null : table;
		List<EntityView.Entity> entities = new ArrayList<>(view.entities());
		for (int i = 0; i < entities.size(); i++) {
			EntityView.Entity e = entities.get(i);
			if (!e.name().equals(name)) {
				continue;
			}
			if (table == null || Objects.equals(newTable, e.tableName())) {
				warnings.add("Line " + line + ": entity '" + name + "' already exists, nothing changed");
				return view;
			}
			if (e.fromCatalog() && e.tableName() != null) {
				warnings.add("Line " + line + ": table of entity '" + name
						+ "' was already set in the catalog, the last one wins");
			}
			entities.set(i, new EntityView.Entity(name, e.attributes(), newTable, true));
			return new EntityView(entities, view.relationships(), view.hiddenInferred());
		}
		entities.add(new EntityView.Entity(name, List.of(), newTable, true));
		return new EntityView(entities, view.relationships(), view.hiddenInferred());
	}
}
