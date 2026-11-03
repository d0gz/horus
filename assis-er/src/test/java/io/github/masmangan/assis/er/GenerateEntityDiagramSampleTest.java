package io.github.masmangan.assis.er;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GenerateEntityDiagramSampleTest {

	@TempDir
	Path tempDir;

	private Path sample(String resource) throws Exception {
		return Paths.get(getClass().getClassLoader().getResource(resource).toURI());
	}

	private String generateInto(Set<Path> roots, String outName) throws Exception {
		Path outDir = tempDir.resolve(outName);
		GenerateEntityDiagram.generate(roots, outDir);
		return Files.readString(outDir.resolve("entity-diagram.mmd"), StandardCharsets.UTF_8);
	}

	@Test
	void writesEntityDiagramForSample() throws Exception {
		assertEquals("erDiagram\n    \"Owner\"\n", generateInto(Set.of(sample("samples/entity")), "out"));
	}

	@Test
	void rejectsOutDirThatIsAFile() throws Exception {
		Path file = Files.createFile(tempDir.resolve("not-a-dir"));
		Set<Path> roots = Set.of(sample("samples/entity"));

		assertThrows(IllegalArgumentException.class, () -> GenerateEntityDiagram.generate(roots, file));
	}

	@Test
	void rejectsNullArguments() throws Exception {
		Set<Path> roots = Set.of(sample("samples/entity"));

		assertThrows(NullPointerException.class, () -> GenerateEntityDiagram.generate(null, tempDir));
		assertThrows(NullPointerException.class, () -> GenerateEntityDiagram.generate(roots, null));
	}

	@Test
	void isDeterministic() throws Exception {
		Set<Path> roots = Set.of(sample("samples/entity"));

		assertEquals(generateInto(roots, "a"), generateInto(roots, "b"));
	}

	@Test
	void includesEntitiesFromAllSourceRoots() throws Exception {
		Path entity = sample("samples/entity");
		Path javax = sample("samples/entityjavax");

		String forward = generateInto(new LinkedHashSet<>(List.of(entity, javax)), "forward");
		String reverse = generateInto(new LinkedHashSet<>(List.of(javax, entity)), "reverse");

		assertTrue(forward.contains("    \"Owner\"\n"), forward);
		assertTrue(forward.contains("    \"Customer\"\n"), forward);
		assertEquals(forward, reverse);
	}

	@Test
	void writesHeaderOnlyForEmptySourceRoot() throws Exception {
		Path empty = Files.createDirectory(tempDir.resolve("empty"));

		assertEquals("erDiagram\n", generateInto(Set.of(empty), "out"));
	}
}
