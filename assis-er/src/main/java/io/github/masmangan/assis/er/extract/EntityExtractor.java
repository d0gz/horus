package io.github.masmangan.assis.er.extract;

import java.util.ArrayList;
import java.util.List;

import io.github.masmangan.assis.er.EntityView;
import io.github.masmangan.assis.internal.DeclaredIndex;

/**
 * Builds an EntityView from the types of a DeclaredIndex.
 *
 * A type is an entity when it carries an annotation whose simple name is
 * Entity. This covers jakarta.persistence.Entity and
 * javax.persistence.Entity, imported or fully qualified.
 */
public final class EntityExtractor {

	private EntityExtractor() {
	}

	/**
	 * Extracts the entities of the index, in index order.
	 *
	 * @param index filled declared-type index
	 * @return view with one entry per @Entity type
	 */
	public static EntityView extract(DeclaredIndex index) {
		return extract(index, false);
	}

	/**
	 * @param index    filled declared-type index
	 * @param inferred true to also infer references by ID type (dotted lines, FK
	 *                 markers)
	 * @return view with one entry per @Entity type
	 */
	public static EntityView extract(DeclaredIndex index, boolean inferred) {
		ExtractionContext ctx = ExtractionContext.of(index, inferred);

		List<EntityView.Entity> entities = new ArrayList<>();
		List<EntityView.Relationship> relationships = new ArrayList<>();
		boolean hiddenInferred = false;

		for (EntitySource entity : ctx.entities()) {
			entities.add(new EntityView.Entity(entity.name(), AttributeExtractor.of(entity, ctx),
					Jpa.nameIfDifferent(entity.type(), Jpa.TABLE, entity.name())));
			relationships.addAll(RelationshipExtractor.of(entity, ctx));

			List<EntityView.Relationship> guessed = InferredRelationshipExtractor.of(entity, ctx);
			if (inferred) {
				relationships.addAll(guessed);
			} else if (!guessed.isEmpty()) {
				hiddenInferred = true;
			}
		}
		return new EntityView(entities, relationships, hiddenInferred);
	}
}
