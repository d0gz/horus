package io.github.masmangan.assis.er;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;

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
	private static final String ID = "Id";
	private static final Set<String> NOT_ATTRIBUTES = Set.of("Transient", "ManyToOne", "OneToMany", "OneToOne",
			"ManyToMany");

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
				entities.add(new EntityView.Entity(td.getNameAsString(), attributesOf(td)));
			}
		}
		return new EntityView(entities);
	}

	private static List<EntityView.Attribute> attributesOf(TypeDeclaration<?> td) {
		List<EntityView.Attribute> attributes = new ArrayList<>();
		for (FieldDeclaration fd : td.getFields()) {
			if (fd.isStatic() || fd.isTransient() || hasAnyAnnotation(fd, NOT_ATTRIBUTES)) {
				continue;
			}
			boolean pk = hasAnyAnnotation(fd, Set.of(ID));
			for (VariableDeclarator v : fd.getVariables()) {
				attributes.add(new EntityView.Attribute(v.getType().asString(), v.getNameAsString(), pk));
			}
		}
		return attributes;
	}

	private static boolean isEntity(TypeDeclaration<?> td) {
		return hasAnyAnnotation(td, Set.of(ENTITY));
	}

	private static boolean hasAnyAnnotation(NodeWithAnnotations<?> n, Set<String> simpleNames) {
		return n.getAnnotations().stream().anyMatch(a -> simpleNames.contains(a.getName().getIdentifier()));
	}
}
