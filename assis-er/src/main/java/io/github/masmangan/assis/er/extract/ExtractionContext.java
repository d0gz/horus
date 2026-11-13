package io.github.masmangan.assis.er.extract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.body.TypeDeclaration;

import io.github.masmangan.assis.internal.DeclaredIndex;

/**
 * Built once per index and shared by the extractors.
 *
 * @param entities     entities in index order
 * @param projectTypes simple names of all types in the index
 * @param idOwners     ID type simple name to owning entity (inferred)
 * @param inferred     true when inferred output is enabled
 */
record ExtractionContext(List<EntitySource> entities, Set<String> entityNames, Set<String> projectTypes,
		Map<String, String> idOwners, boolean inferred) {

	static ExtractionContext of(DeclaredIndex index, boolean inferred) {
		List<TypeDeclaration<?>> types = new ArrayList<>();
		Set<String> projectTypes = new HashSet<>();
		for (TypeDeclaration<?> td : index.typesInIndexOrder()) {
			projectTypes.add(td.getNameAsString());
			if (Jpa.isEntity(td)) {
				types.add(td);
			}
		}
		Set<String> entityNames = new HashSet<>();
		for (TypeDeclaration<?> td : types) {
			entityNames.add(td.getNameAsString());
		}
		List<EntitySource> entities = new ArrayList<>();
		for (TypeDeclaration<?> td : types) {
			entities.add(new EntitySource(td, IdResolver.idTypeOf(td, projectTypes, entityNames)));
		}
		return new ExtractionContext(List.copyOf(entities), Set.copyOf(entityNames), Set.copyOf(projectTypes),
				Map.copyOf(IdResolver.owners(entities)), inferred);
	}

	/** idOwners when inference is on, else empty: FK markers are inferred output. */
	Map<String, String> fkOwners() {
		return inferred ? idOwners : Map.of();
	}
}
