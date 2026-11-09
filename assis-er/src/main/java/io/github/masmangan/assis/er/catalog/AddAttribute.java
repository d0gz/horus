package io.github.masmangan.assis.er.catalog;

import java.util.ArrayList;
import java.util.List;

import io.github.masmangan.assis.er.EntityView;

/**
 * Adds an attribute, creating the entity when missing. An attribute with the
 * same name, else the same column name, is replaced in place.
 */
record AddAttribute(int line, String entity, EntityView.Attribute attribute) implements CatalogEntry {

	@Override
	public Phase phase() {
		return Phase.ADD_ENTITY;
	}

	@Override
	public EntityView applyTo(EntityView view, List<String> warnings) {
		List<EntityView.Entity> entities = new ArrayList<>(view.entities());
		for (int i = 0; i < entities.size(); i++) {
			EntityView.Entity e = entities.get(i);
			if (e.name().equals(entity)) {
				entities.set(i, new EntityView.Entity(entity, merge(e.attributes(), warnings), e.tableName(),
						e.fromCatalog()));
				return new EntityView(entities, view.relationships(), view.hiddenInferred());
			}
		}
		entities.add(new EntityView.Entity(entity, List.of(marked(attribute.name(), attribute.columnName())), null,
				true));
		return new EntityView(entities, view.relationships(), view.hiddenInferred());
	}

	private List<EntityView.Attribute> merge(List<EntityView.Attribute> attributes, List<String> warnings) {
		int match = indexOf(attributes, true);
		boolean byColumn = false;
		if (match < 0) {
			match = indexOf(attributes, false);
			byColumn = match >= 0;
		}
		List<EntityView.Attribute> merged = new ArrayList<>(attributes);
		if (match < 0) {
			merged.add(marked(attribute.name(), attribute.columnName()));
			return merged;
		}
		EntityView.Attribute old = merged.get(match);
		if (old.fromCatalog()) {
			warnings.add("Line " + line + ": attribute " + entity + "." + old.name()
					+ " was already in the catalog, the last one wins");
		}
		merged.set(match, byColumn
				? marked(old.name(), attribute.columnName() != null ? attribute.columnName() : attribute.name())
				: marked(attribute.name(), attribute.columnName()));
		return merged;
	}

	private int indexOf(List<EntityView.Attribute> attributes, boolean byName) {
		for (int i = 0; i < attributes.size(); i++) {
			String key = byName ? attributes.get(i).name() : attributes.get(i).columnName();
			if (attribute.name().equals(key)) {
				return i;
			}
		}
		return -1;
	}

	private EntityView.Attribute marked(String name, String column) {
		return new EntityView.Attribute(attribute.type(), name, attribute.primaryKey(), attribute.foreignKey(),
				attribute.nullable(), name.equals(column) ? null : column, true);
	}
}
