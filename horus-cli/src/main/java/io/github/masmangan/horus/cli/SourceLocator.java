package io.github.masmangan.horus.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Finds the Java source roots and the pom.xml of a project folder.
 */
final class SourceLocator {

	private static final Logger LOG = Logger.getLogger(SourceLocator.class.getName());

	static final Path MAVEN = Path.of("src/main/java");
	static final Path SRC = Path.of("src");
	static final Path DOT = Path.of(".");
	static final String POM_XML = "pom.xml";

	private static final List<Path> CANDIDATES = List.of(MAVEN, SRC, DOT);

	private SourceLocator() {
	}

	/**
	 * Explicit roots (relative to the cwd, like javac) must exist and contain
	 * .java files. Otherwise the first of src/main/java, src, . under projectDir
	 * that contains .java files is used. Returned paths are absolute and
	 * normalized.
	 */
	static Set<Path> resolve(Path projectDir, Set<Path> requested) throws IOException {
		if (!requested.isEmpty()) {
			return extractRequested(requested);
		}
		return extractFirstDefault(projectDir);
	}

	static Optional<Path> findPom(Path projectDir) {
		Path pom = projectDir.resolve(POM_XML).toAbsolutePath().normalize();
		return Files.isRegularFile(pom) ? Optional.of(pom) : Optional.empty();
	}

	/** Nearest pom.xml in dir or in one of its parents. */
	static Optional<Path> findPomAbove(Path dir) {
		for (Path d = dir.toAbsolutePath().normalize(); d != null; d = d.getParent()) {
			Optional<Path> pom = findPom(d);
			if (pom.isPresent()) {
				return pom;
			}
		}
		return Optional.empty();
	}

	private static Set<Path> extractFirstDefault(Path projectDir) throws IOException {
		Path base = projectDir.toAbsolutePath().normalize();
		if (!Files.isDirectory(base)) {
			throw new IllegalArgumentException("Project folder is not a directory: " + base);
		}

		for (Path candidate : CANDIDATES) {
			Path abs = base.resolve(candidate).normalize();
			LOG.info(() -> "Trying source directory: " + abs);

			if (!Files.isDirectory(abs) || !containsJava(abs)) {
				LOG.info(() -> "No source code inside. Skipping: " + abs);
				continue;
			}

			LOG.info(() -> "Using source directory: " + abs);
			if (candidate.equals(DOT)) {
				LOG.warning(() -> "Falling back to the project folder as source root. "
						+ "Use -sourcepath/--source-path to point to the desired folder.");
			}
			return Set.of(abs);
		}

		throw new IllegalStateException("No Java source directory found under " + base
				+ " (tried: src/main/java, src, .)");
	}

	private static Set<Path> extractRequested(Set<Path> requested) throws IOException {
		LinkedHashSet<Path> out = new LinkedHashSet<>();
		for (Path dir : requested) {
			Path abs = dir.toAbsolutePath().normalize();
			LOG.info(() -> "Using explicit source path (javac-like): " + abs);

			if (!Files.isDirectory(abs)) {
				throw new IllegalArgumentException("Source path is not a directory: " + abs);
			}
			if (!containsJava(abs)) {
				throw new IllegalArgumentException("No .java files found under -sourcepath/--source-path: " + abs);
			}
			out.add(abs);
		}
		return out;
	}

	private static boolean containsJava(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			return walk.anyMatch(p -> p.toString().endsWith(".java"));
		}
	}
}
