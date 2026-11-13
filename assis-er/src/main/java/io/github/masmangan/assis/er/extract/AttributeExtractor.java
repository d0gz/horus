package io.github.masmangan.assis.er.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import io.github.masmangan.assis.er.EntityView;

final class AttributeExtractor {

	private AttributeExtractor() {
	}

	static List<EntityView.Attribute> of(EntitySource entity, ExtractionContext ctx) {
		Map<String, String> fkOwners = ctx.fkOwners();
		List<EntityView.Attribute> attributes = new ArrayList<>();
		for (FieldDeclaration fd : entity.type().getFields()) {
			if (!Jpa.isColumnField(fd)) {
				continue;
			}
			boolean pk = Jpa.has(fd, Jpa.ID);
			boolean notNull = Jpa.isIdField(fd) || Jpa.has(fd, Jpa.NOT_NULL) || Jpa.hasFalseMember(fd, Jpa.COLUMN, "nullable")
					|| Jpa.hasFalseMember(fd, Jpa.BASIC, "optional");
					
			boolean explicitNull = Jpa.hasTrueMember(fd, Jpa.COLUMN, "nullable")
					|| Jpa.hasTrueMember(fd, Jpa.BASIC, "optional");

			for (VariableDeclarator v : fd.getVariables()) {
				String ref = TypeRefs.targetOf(v.getType());
				boolean fk = !pk && !TypeRefs.isCollection(v.getType()) && fkOwners.containsKey(ref)
						&& !entity.isOwnId(ref);

				attributes.add(new EntityView.Attribute(v.getType().asString(), v.getNameAsString(), pk, fk,
						!notNull && (explicitNull || !v.getType().isPrimitiveType()),
						Jpa.nameIfDifferent(fd, Jpa.COLUMN, v.getNameAsString())));
			}
		}
		return attributes;
	}
}
