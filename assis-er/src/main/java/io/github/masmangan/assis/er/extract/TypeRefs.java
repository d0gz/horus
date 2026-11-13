package io.github.masmangan.assis.er.extract;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import io.github.masmangan.assis.er.types.CollectionTypes;

final class TypeRefs {

	private TypeRefs() {
	}

	/**
	 * Last type argument for generics (List<Pet>, Map<K, Pet>), else the type
	 * itself.
	 */
	static String targetOf(Type type) {
		if (type instanceof ClassOrInterfaceType cit) {
			var args = cit.getTypeArguments();
			if (args.isPresent() && !args.get().isEmpty()) {
				return targetOf(args.get().getLast().orElseThrow());
			}
			return cit.getNameAsString();
		}
		return type.asString();
	}

	static String simpleName(Type type) {
		return type instanceof ClassOrInterfaceType cit ? cit.getNameAsString() : type.asString();
	}

	/**
	 * Known collection types hold many elements; Optional and unknown generics are
	 * single-valued.
	 */
	static boolean isCollection(Type type) {
		return type instanceof ClassOrInterfaceType cit && CollectionTypes.isCollection(cit.getNameAsString());
	}
}
