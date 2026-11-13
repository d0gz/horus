package io.github.masmangan.assis.er.extract;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

/**
 * Heuristic: resolves the ID type of each entity, used only for inferred
 * output.
 */
final class IdResolver {

	private static final Set<String> ID_STYLE_BASES = Set.of("AbstractEntity", "AbstractVersionedEntity");

	private IdResolver() {
	}

	/**
	 * @Id/@EmbeddedId field type, else the type argument of a known ID-style base.
	 */
	static Optional<String> idTypeOf(TypeDeclaration<?> td, Set<String> projectTypes, Set<String> entityNames) {
		for (FieldDeclaration fd : td.getFields()) {
			if (Jpa.isIdField(fd)) {
				String type = TypeRefs.simpleName(fd.getElementType());
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
					String type = TypeRefs.simpleName(args.get().get(0));
					if (isIdCandidate(type, projectTypes, entityNames)) {
						return Optional.of(type);
					}
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * ID type simple name to owning entity; ID types claimed by several entities
	 * are dropped.
	 */
	static Map<String, String> owners(List<EntitySource> entities) {
		Map<String, String> owners = new HashMap<>();
		Set<String> ambiguous = new HashSet<>();
		for (EntitySource entity : entities) {
			entity.ownId().ifPresent(id -> {
				if (owners.putIfAbsent(id, entity.name()) != null) {
					ambiguous.add(id);
				}
			});
		}
		owners.keySet().removeAll(ambiguous);
		return owners;
	}

	private static boolean isIdCandidate(String type, Set<String> projectTypes, Set<String> entityNames) {
		return projectTypes.contains(type) && !entityNames.contains(type);
	}
}
