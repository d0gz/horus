package io.github.masmangan.assis.er;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

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
	private static final Map<String, EntityView.Cardinality> CARDINALITIES = Map.of(
			"ManyToOne", EntityView.Cardinality.MANY_TO_ONE,
			"OneToMany", EntityView.Cardinality.ONE_TO_MANY,
			"OneToOne", EntityView.Cardinality.ONE_TO_ONE,
			"ManyToMany", EntityView.Cardinality.MANY_TO_MANY);
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
		List<TypeDeclaration<?>> types = new ArrayList<>();
		for (TypeDeclaration<?> td : index.typesInIndexOrder()) {
			if (isEntity(td)) {
				types.add(td);
			}
		}
		Set<String> names = new HashSet<>();
		List<EntityView.Entity> entities = new ArrayList<>();
		for (TypeDeclaration<?> td : types) {
			names.add(td.getNameAsString());
			entities.add(new EntityView.Entity(td.getNameAsString(), attributesOf(td)));
		}
		List<EntityView.Relationship> relationships = new ArrayList<>();
		for (TypeDeclaration<?> td : types) {
			relationships.addAll(relationshipsOf(td, names));
		}
		return new EntityView(entities, relationships);
	}

	private static List<EntityView.Relationship> relationshipsOf(TypeDeclaration<?> td, Set<String> entityNames) {
		List<EntityView.Relationship> relationships = new ArrayList<>();
		for (FieldDeclaration fd : td.getFields()) {
			for (AnnotationExpr a : fd.getAnnotations()) {
				EntityView.Cardinality cardinality = CARDINALITIES.get(a.getName().getIdentifier());
				if (cardinality == null || hasMappedBy(a)) {
					continue;
				}
				for (VariableDeclarator v : fd.getVariables()) {
					String target = targetOf(v.getType());
					if (entityNames.contains(target)) {
						relationships.add(new EntityView.Relationship(td.getNameAsString(), target, cardinality,
								v.getNameAsString()));
					}
				}
			}
		}
		return relationships;
	}

	private static boolean hasMappedBy(AnnotationExpr a) {
		return a instanceof NormalAnnotationExpr n
				&& n.getPairs().stream().anyMatch(p -> "mappedBy".equals(p.getNameAsString()));
	}

	/** Last type argument for generics (List<Pet>, Map<K, Pet>), else the type itself. */
	private static String targetOf(Type type) {
		if (type instanceof ClassOrInterfaceType cit) {
			var args = cit.getTypeArguments();
			if (args.isPresent() && !args.get().isEmpty()) {
				return targetOf(args.get().getLast().orElseThrow());
			}
			return cit.getNameAsString();
		}
		return type.asString();
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
