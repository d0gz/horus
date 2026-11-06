package io.github.masmangan.assis.er.extract;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import io.github.masmangan.assis.er.EntityView;
import io.github.masmangan.assis.er.types.Cardinality;

/**
 * Heuristic: relationships deduced from fields typed with another entity's ID
 * type. Cardinality is assumed.
 */
final class InferredRelationshipExtractor {

	private InferredRelationshipExtractor() {
	}

	static List<EntityView.Relationship> of(EntitySource entity, ExtractionContext ctx) {
		List<EntityView.Relationship> relationships = new ArrayList<>();
		for (FieldDeclaration fd : entity.type().getFields()) {
			if (!Jpa.isColumnField(fd) || Jpa.isIdField(fd)) {
				continue;
			}
			for (VariableDeclarator v : fd.getVariables()) {
				String idType = TypeRefs.targetOf(v.getType());
				String target = ctx.idOwners().get(idType);
				if (target == null || entity.isOwnId(idType)) {
					continue;
				}
				Cardinality cardinality = TypeRefs.isCollection(v.getType()) ? Cardinality.ONE_TO_MANY
						: Cardinality.MANY_TO_ONE;
				relationships.add(new EntityView.Relationship(entity.name(), target, cardinality,
						v.getNameAsString(), true));
			}
		}
		return relationships;
	}
}
