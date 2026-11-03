package io.github.masmangan.assis.er;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.body.TypeDeclaration;

import io.github.masmangan.assis.internal.DeclaredIndex;

/**
 * Builds an EntityView from the types of a DeclaredIndex.
 *
 * A type is an entity when it carries an annotation whose simple name is
 * Entity. This covers jakarta.persistence.Entity and
 * javax.persistence.Entity, imported or fully qualified.
 */
public final class EntityExtractor {

	private static final String ENTITY = "Entity";

	private EntityExtractor() {
	}

	/**
	 * Extracts the entities of the index, in index order.
	 *
	 * @param index filled declared-type index
	 * @return view with one entry per @Entity type
	 */
	public static EntityView extract(DeclaredIndex index) {
		List<EntityView.Entity> entities = new ArrayList<>();
		for (TypeDeclaration<?> td : index.typesInIndexOrder()) {
			if (isEntity(td)) {
				entities.add(new EntityView.Entity(td.getNameAsString()));
			}
		}
		return new EntityView(entities);
	}

	private static boolean isEntity(TypeDeclaration<?> td) {
		return td.getAnnotations().stream().anyMatch(a -> ENTITY.equals(a.getName().getIdentifier()));
	}
}
