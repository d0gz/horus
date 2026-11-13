package io.github.masmangan.assis.er.extract;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;

import io.github.masmangan.assis.er.EntityView;
import io.github.masmangan.assis.er.types.Cardinality;

final class RelationshipExtractor {

	private RelationshipExtractor() {
	}

	static List<EntityView.Relationship> of(EntitySource entity, ExtractionContext ctx) {
		List<EntityView.Relationship> relationships = new ArrayList<>();
		for (FieldDeclaration fd : entity.type().getFields()) {
			for (AnnotationExpr a : fd.getAnnotations()) {
				Cardinality cardinality = Jpa.RELATIONSHIPS.get(a.getName().getIdentifier());

				if (cardinality == null || Jpa.hasMappedBy(a)) {
					continue;
				}
				boolean required = isToOne(cardinality)
						&& (Jpa.isFalse(a, "optional") || Jpa.hasFalseMember(fd, Jpa.JOIN_COLUMN, "nullable"));
				for (VariableDeclarator v : fd.getVariables()) {
					String target = TypeRefs.targetOf(v.getType());

					if (ctx.entityNames().contains(target)) {
						relationships.add(new EntityView.Relationship(entity.name(), target, cardinality,
								v.getNameAsString(), false, required));
					}
				}
			}
		}
		return relationships;
	}

	private static boolean isToOne(Cardinality cardinality) {
		return cardinality == Cardinality.MANY_TO_ONE || cardinality == Cardinality.ONE_TO_ONE;
	}
}
