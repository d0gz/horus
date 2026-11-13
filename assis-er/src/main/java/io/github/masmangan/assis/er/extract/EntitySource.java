package io.github.masmangan.assis.er.extract;

import java.util.Optional;

import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * @param ownId simple name of the entity's own ID type, when it is a project
 *              class
 */
record EntitySource(TypeDeclaration<?> type, Optional<String> ownId) {

	String name() {
		return type.getNameAsString();
	}

	boolean isOwnId(String typeName) {
		return ownId.filter(typeName::equals).isPresent();
	}
}
