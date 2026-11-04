package io.github.masmangan.assis.er.types;

import java.util.Set;

/**
 * Simple names of the JDK collection types that hold many elements. Matched by
 * simple name, like annotations.
 */
public final class CollectionTypes {

	private static final Set<String> NAMES = Set.of("Collection", "List", "Set", "SortedSet", "NavigableSet", "Queue",
			"Deque", "Iterable", "Map", "SortedMap", "NavigableMap");

	private CollectionTypes() {
	}

	public static boolean isCollection(String simpleName) {
		return NAMES.contains(simpleName);
	}
}
