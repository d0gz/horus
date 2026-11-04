package io.github.masmangan.assis.er;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import io.github.masmangan.assis.er.types.CollectionTypes;
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
	private static final Set<String> ID_ANNOTATIONS = Set.of(ID, "EmbeddedId");
	private static final Set<String> ID_STYLE_BASES = Set.of("AbstractEntity", "AbstractVersionedEntity");
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
		for (TypeDeclaration<?> td : types) {
			names.add(td.getNameAsString());
		}
		Set<String> projectTypes = new HashSet<>();
		for (TypeDeclaration<?> td : index.typesInIndexOrder()) {
			projectTypes.add(td.getNameAsString());
		}
		Map<String, String> owners = idOwners(types, projectTypes, names);
		List<EntityView.Entity> entities = new ArrayList<>();
		for (TypeDeclaration<?> td : types) {
			entities.add(new EntityView.Entity(td.getNameAsString(),
					attributesOf(td, owners, idTypeOf(td, projectTypes, names))));
		}
		List<EntityView.Relationship> relationships = new ArrayList<>();
		for (TypeDeclaration<?> td : types) {
			relationships.addAll(relationshipsOf(td, names));
			relationships.addAll(inferredOf(td, owners, idTypeOf(td, projectTypes, names)));
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
				boolean required = isToOne(cardinality)
						&& (isFalse(a, "optional") || hasFalseMember(fd, "JoinColumn", "nullable"));
				for (VariableDeclarator v : fd.getVariables()) {
					String target = targetOf(v.getType());
					if (entityNames.contains(target)) {
						relationships.add(new EntityView.Relationship(td.getNameAsString(), target, cardinality,
								v.getNameAsString(), false, required));
					}
				}
			}
		}
		return relationships;
	}

	/**
	 * ID type simple name to owning entity; ID types claimed by several entities
	 * are dropped.
	 */
	private static Map<String, String> idOwners(List<TypeDeclaration<?>> types, Set<String> projectTypes,
			Set<String> entityNames) {
		Map<String, String> owners = new HashMap<>();
		Set<String> ambiguous = new HashSet<>();
		for (TypeDeclaration<?> td : types) {
			idTypeOf(td, projectTypes, entityNames).ifPresent(id -> {
				if (owners.putIfAbsent(id, td.getNameAsString()) != null) {
					ambiguous.add(id);
				}
			});
		}
		owners.keySet().removeAll(ambiguous);
		return owners;
	}

	/**
	 * @Id/@EmbeddedId field type, else the type argument of a known ID-style base.
	 */
	private static Optional<String> idTypeOf(TypeDeclaration<?> td, Set<String> projectTypes,
			Set<String> entityNames) {
		for (FieldDeclaration fd : td.getFields()) {
			if (hasAnyAnnotation(fd, ID_ANNOTATIONS)) {
				String type = simpleName(fd.getElementType());
				if (isIdCandidate(type, projectTypes, entityNames)) {
					return Optional.of(type);
				}
				break;
			}
		}
		if (td instanceof ClassOrInterfaceDeclaration cd) {
			for (ClassOrInterfaceType base : cd.getExtendedTypes()) {
				var args = base.getTypeArguments();
				if (ID_STYLE_BASES.contains(base.getNameAsString()) && args.isPresent() && args.get().size() == 1) {
					String type = simpleName(args.get().get(0));
					if (isIdCandidate(type, projectTypes, entityNames)) {
						return Optional.of(type);
					}
				}
			}
		}
		return Optional.empty();
	}

	private static boolean isIdCandidate(String type, Set<String> projectTypes, Set<String> entityNames) {
		return projectTypes.contains(type) && !entityNames.contains(type);
	}

	private static List<EntityView.Relationship> inferredOf(TypeDeclaration<?> td, Map<String, String> owners,
			Optional<String> ownId) {
		List<EntityView.Relationship> relationships = new ArrayList<>();
		for (FieldDeclaration fd : td.getFields()) {
			if (fd.isStatic() || fd.isTransient() || hasAnyAnnotation(fd, NOT_ATTRIBUTES)
					|| hasAnyAnnotation(fd, ID_ANNOTATIONS)) {
				continue;
			}
			for (VariableDeclarator v : fd.getVariables()) {
				String idType = targetOf(v.getType());
				String target = owners.get(idType);
				if (target == null || ownId.filter(idType::equals).isPresent()) {
					continue;
				}
				EntityView.Cardinality cardinality = isCollection(v.getType()) ? EntityView.Cardinality.ONE_TO_MANY
						: EntityView.Cardinality.MANY_TO_ONE;
				relationships.add(new EntityView.Relationship(td.getNameAsString(), target, cardinality,
						v.getNameAsString(), true));
			}
		}
		return relationships;
	}

	/**
	 * Known collection types hold many IDs; Optional and unknown generics are
	 * single-valued.
	 */
	private static boolean isCollection(Type type) {
		return type instanceof ClassOrInterfaceType cit && CollectionTypes.isCollection(cit.getNameAsString());
	}

	private static String simpleName(Type type) {
		return type instanceof ClassOrInterfaceType cit ? cit.getNameAsString() : type.asString();
	}

	private static boolean isToOne(EntityView.Cardinality cardinality) {
		return cardinality == EntityView.Cardinality.MANY_TO_ONE || cardinality == EntityView.Cardinality.ONE_TO_ONE;
	}

	private static boolean hasFalseMember(FieldDeclaration fd, String annotation, String member) {
		return fd.getAnnotations().stream()
				.anyMatch(a -> annotation.equals(a.getName().getIdentifier()) && isFalse(a, member));
	}

	private static boolean isFalse(AnnotationExpr a, String member) {
		return a instanceof NormalAnnotationExpr n && n.getPairs().stream()
				.anyMatch(p -> member.equals(p.getNameAsString()) && p.getValue() instanceof BooleanLiteralExpr b
						&& !b.getValue());
	}

	private static boolean hasMappedBy(AnnotationExpr a) {
		return a instanceof NormalAnnotationExpr n
				&& n.getPairs().stream().anyMatch(p -> "mappedBy".equals(p.getNameAsString()));
	}

	/**
	 * Last type argument for generics (List<Pet>, Map<K, Pet>), else the type
	 * itself.
	 */
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

	private static List<EntityView.Attribute> attributesOf(TypeDeclaration<?> td, Map<String, String> owners,
			Optional<String> ownId) {
		List<EntityView.Attribute> attributes = new ArrayList<>();
		for (FieldDeclaration fd : td.getFields()) {
			if (fd.isStatic() || fd.isTransient() || hasAnyAnnotation(fd, NOT_ATTRIBUTES)) {
				continue;
			}
			boolean pk = hasAnyAnnotation(fd, Set.of(ID));
			boolean notNull = !hasAnyAnnotation(fd, ID_ANNOTATIONS)
					&& (hasFalseMember(fd, "Column", "nullable") || hasFalseMember(fd, "Basic", "optional"));

			for (VariableDeclarator v : fd.getVariables()) {
				String ref = targetOf(v.getType());
				boolean fk = !pk && !isCollection(v.getType()) && owners.containsKey(ref)
						&& ownId.filter(ref::equals).isEmpty();

				attributes.add(
						new EntityView.Attribute(v.getType().asString(), v.getNameAsString(), pk, fk, notNull));
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
