package io.github.masmangan.assis.er;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.masmangan.assis.internal.DeclaredIndex;
import io.github.masmangan.assis.io.SmartSourceRootManager;
import io.github.masmangan.assis.util.DeterministicPathList;

/**
 * Generates a Mermaid erDiagram of the JPA entities found in one or
 * more Java source roots.
 *
 * The output file is always named entity-diagram.mmd inside outDir.
 */
public class GenerateEntityDiagram {

	private static final String ENTITY_DIAGRAM_MMD = "entity-diagram.mmd";

	private static final Logger logger = Logger.getLogger(GenerateEntityDiagram.class.getName());

	private GenerateEntityDiagram() {
	}

	/**
	 * Generates the entity diagram.
	 *
	 * @param sourceRoots one or more Java source roots; must not be null
	 * @param outDir      output directory; must not be null; created if missing; if it exists, it must be a directory
	 * @throws NullPointerException     if an argument is null
	 * @throws IllegalArgumentException if outDir exists and is not a
	 *                                  directory
	 * @throws IOException              if reading sources or writing the output fails
	 */
	public static void generate(final Set<Path> sourceRoots, final Path outDir) throws IOException {
		generate(sourceRoots, outDir, false);
	}

	/**
	 * @param inferred true to also infer references by ID type (dotted lines, FK markers)
	 * @see #generate(Set, Path)
	 */
	public static void generate(final Set<Path> sourceRoots, final Path outDir, final boolean inferred)
			throws IOException {
		Objects.requireNonNull(sourceRoots, "sourceRoots");
		Objects.requireNonNull(outDir, "outDir");

		Path dir = outDir.toAbsolutePath().normalize();
		if (Files.exists(dir) && !Files.isDirectory(dir)) {
			throw new IllegalArgumentException("outDir must be a directory: " + dir);
		}
		Files.createDirectories(dir);
		Path outputFile = dir.resolve(ENTITY_DIAGRAM_MMD);
		logger.log(Level.INFO, () -> "Generating " + outputFile + "...");

		DeclaredIndex index = new DeclaredIndex();
		index.fill(new SmartSourceRootManager().autoscan(DeterministicPathList.fromSourceRoots(sourceRoots)));

		String mermaid = EntityMermaidRenderer.render(EntityExtractor.extract(index, inferred));
		Files.writeString(outputFile, mermaid, StandardCharsets.UTF_8);
		logger.log(Level.INFO, () -> "Writing " + outputFile + " complete.");
	}
}
