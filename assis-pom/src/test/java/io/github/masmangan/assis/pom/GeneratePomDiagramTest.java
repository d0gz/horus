package io.github.masmangan.assis.pom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeneratePomDiagramTest {

	private static final String POM = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project xmlns="http://maven.apache.org/POM/4.0.0">
			  <modelVersion>4.0.0</modelVersion>
			  <groupId>com.exemplo</groupId>
			  <artifactId>demo</artifactId>
			  <version>1.0.0</version>
			  <dependencies>
			    <dependency>
			      <groupId>com.empresa</groupId>
			      <artifactId>core-interno</artifactId>
			      <version>1.0</version>
			    </dependency>
			  </dependencies>
			</project>
			""";

	@TempDir
	Path dir;

	private Path pom() throws IOException {
		Path pom = dir.resolve("pom.xml");
		Files.writeString(pom, POM, StandardCharsets.UTF_8);
		return pom;
	}

	@ParameterizedTest
	@CsvSource({ "dependencias, dependencies.mmd", "completo, pom.mmd", "reator, reator.mmd", "c2, c2.mmd" })
	void writesTheDefaultFileOfEachModeInOutDir(String modo, String file) throws IOException {
		Path out = dir.resolve("out/nested");

		GeneratePomDiagram.generate(pom(), out, modo, null, false);

		assertEquals(file, GeneratePomDiagram.fileName(modo));
		assertTrue(Files.isRegularFile(out.resolve(file)));
		assertFalse(Files.readString(out.resolve(file)).isBlank());
	}

	@Test
	void appliesTheUserCatalog() throws IOException {
		Path catalogo = dir.resolve("catalogo.txt");
		Files.writeString(catalogo, "com.empresa:core-interno | Dominio\n", StandardCharsets.UTF_8);
		Path out = dir.resolve("out");

		GeneratePomDiagram.generate(pom(), out, "completo", null, false);
		String without = Files.readString(out.resolve("pom.mmd"));
		GeneratePomDiagram.generate(pom(), out, "completo", catalogo, false);
		String with = Files.readString(out.resolve("pom.mmd"));

		assertFalse(without.contains("Dominio"));
		assertTrue(with.contains("Dominio"));
	}

	@Test
	void missingPomThrowsIOException() {
		assertThrows(IOException.class,
				() -> GeneratePomDiagram.generate(dir.resolve("nope.xml"), dir, "completo", null, false));
	}

	@Test
	void invalidXmlThrowsIOException() throws IOException {
		Path pom = dir.resolve("pom.xml");
		Files.writeString(pom, "<project><broken>", StandardCharsets.UTF_8);

		assertThrows(IOException.class, () -> GeneratePomDiagram.generate(pom, dir, "completo", null, false));
	}

	@Test
	void unknownModeIsRejected() {
		assertFalse(GeneratePomDiagram.isModo("er"));
		assertFalse(GeneratePomDiagram.isModo(null));
		assertThrows(IllegalArgumentException.class,
				() -> GeneratePomDiagram.generate(dir.resolve("pom.xml"), dir, "er", null, false));
	}
}
