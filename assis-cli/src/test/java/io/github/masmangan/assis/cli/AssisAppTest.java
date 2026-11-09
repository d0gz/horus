/*
 * Copyright (c) 2025-2026, Marco Mangan.
 * All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package io.github.masmangan.assis.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 *
 */
class AssisAppTest {

	private static final String PUML_FILE = "class-diagram.puml";

	@TempDir
	Path tempDir;

	@Test
	void generatesDiagramFromHelloUsingSourcepathAndOutputDirectory() throws Exception {
		Path srcMainJava = tempDir.resolve("src/somewhere");
		Files.createDirectories(srcMainJava);

		Files.writeString(srcMainJava.resolve("Hello.java"), """
				public class Hello {
				}
				""", UTF_8);

		Path outputDirectory = tempDir.resolve("anotherdoc");

		int code = AssisApp
				.run(new String[] { "-sourcepath", srcMainJava.toString(), "-d", outputDirectory.toString() });

		assertEquals(0, code);
		assertTrue(Files.exists(outputDirectory), "Expected output directory to exist: " + outputDirectory + "\n");

		Path pumlPath = outputDirectory.resolve(PUML_FILE);
		assertTrue(Files.exists(pumlPath), "Expected output PUML file to exist: " + pumlPath + "\n");

		String puml = Files.readString(pumlPath, UTF_8);
		assertTrue(puml.contains("Hello") || puml.contains("\"Hello\""),
				"Expected diagram to mention Hello. Content:\n" + puml);
	}

	@ResourceLock("ASSIS_DEFAULT_OUTPUT")
	@Test
	void generatesDiagramFromHelloUsingSourcepathOnly() throws Exception {
		Path sourcePath = tempDir.resolve("src");
		Files.createDirectories(sourcePath);

		Files.writeString(sourcePath.resolve("Hello.java"), """
				public class Hello {
				}
				""", UTF_8);

		Path expectedOut = Path.of("docs/diagrams/src").toAbsolutePath().normalize();
		Path pumlPath = expectedOut.resolve(PUML_FILE);

		Files.deleteIfExists(pumlPath);

		try (var outBuf = new ByteArrayOutputStream(); var errBuf = new ByteArrayOutputStream();

		) {
			int code = AssisApp.run(new String[] { "-sourcepath", sourcePath.toString() });

			assertEquals(0, code);
			assertTrue(Files.exists(expectedOut), "Expected default output directory to exist: " + expectedOut + "\n");
			assertTrue(Files.exists(pumlPath), "Expected output PUML file to exist: " + pumlPath + "\n");

			String puml = Files.readString(pumlPath, UTF_8);
			assertTrue(puml.contains("Hello"), "Expected diagram to mention Hello. Content:\n" + puml);
		} finally {
			try {
				Files.deleteIfExists(pumlPath);
				Files.deleteIfExists(expectedOut.resolve(ER_FILE));
			} catch (Exception ignored) {
				throw new IllegalStateException("Clean up failed.");
			}
		}
	}

	@ResourceLock("ASSIS_DEFAULT_OUTPUT")
	@Test
	void generatesDiagramFromDefaults() throws Exception {
		Path expectedOut = Path.of("docs/diagrams/src").toAbsolutePath().normalize();
		Path pumlPath = expectedOut.resolve(PUML_FILE);

		Files.deleteIfExists(pumlPath);

		try {
			int code = AssisApp.run(new String[] {});

			assertEquals(0, code);
			assertTrue(Files.exists(expectedOut), "Expected default output directory to exist: " + expectedOut + "\n");
			assertTrue(Files.exists(pumlPath), "Expected output PUML file to exist: " + pumlPath + "\n");

			String puml = Files.readString(pumlPath, UTF_8);
			assertTrue(puml.contains("AssisApp"), "Expected diagram to mention Hello. Content:\n" + puml);
		} finally {
			try {
				Files.deleteIfExists(pumlPath);
				Files.deleteIfExists(expectedOut.resolve(ER_FILE));
			} catch (Exception ignored) {
				throw new IllegalStateException("Clean up failed.");
			}
		}
	}

	@Test
	void generatesDiagramFromCompositeSourcepathAndOutputDirectory() throws Exception {
		Path sp1 = tempDir.resolve("p1");
		Path sp2 = tempDir.resolve("p2");
		Files.createDirectories(sp1);
		Files.createDirectories(sp2);

		Files.writeString(sp1.resolve("Hello.java"), """
				public class Hello {
				}
				""", UTF_8);

		Files.writeString(sp2.resolve("World.java"), """
				public class World {
				}
				""", UTF_8);

		Path out = tempDir.resolve("anotherdoc");

		var outBuf = new ByteArrayOutputStream();
		var errBuf = new ByteArrayOutputStream();

		String compositeSourcepath = sp1.toString() + File.pathSeparator + sp2.toString();
		int code = AssisApp.run(new String[] { "-sourcepath", compositeSourcepath, "-d", out.toString() });

		assertEquals(0, code, debugBuffers(outBuf, errBuf));
		assertTrue(Files.exists(out),
				"Expected output directory to exist: " + out + "\n" + debugBuffers(outBuf, errBuf));

		Path pumlPath = out.resolve(PUML_FILE);
		assertTrue(Files.exists(pumlPath),
				"Expected output PUML file to exist: " + pumlPath + "\n" + debugBuffers(outBuf, errBuf));

		String puml = Files.readString(pumlPath, UTF_8);
		assertTrue(puml.contains("Hello") || puml.contains("\"Hello\""),
				"Expected diagram to mention Hello. Content:\n" + puml);
		assertTrue(puml.contains("World") || puml.contains("\"World\""),
				"Expected diagram to mention World (composite sourcepath). Content:\n" + puml);
	}

	@Test
	void invalidArgumentReturnsNonZero() {
		try (var logs = new JulLogCaptor(AssisApp.class)) {
			int code = AssisApp.run(new String[] { "-nope" });

			assertNotEquals(0, code, "Expected non-zero exit code for invalid arg.\nLogs:\n" + logs.dump());

			assertTrue(logs.any(Level.SEVERE, "unknown") || logs.any(Level.SEVERE, "option"),
					"Expected a SEVERE message mentioning unknown/invalid option.\nLogs:\n" + logs.dump());
		}
	}

	@Test
	void checkHelpKnobExists() {
		try (var logs = new JulLogCaptor(AssisApp.class)) {
			int code = AssisApp.run(new String[] { "--help" });

			assertEquals(0, code, "Expected zero exit code for --help.\nLogs:\n" + logs.dump());

			assertTrue(logs.any(Level.INFO, "usage"),
					"Expected help text to contain 'usage' (logged at CONFIG).\nLogs:\n" + logs.dump());
		}
	}

	@Test
	void checkVersionExists() {
		try (var logs = new JulLogCaptor(AssisApp.class)) {
			int code = AssisApp.run(new String[] { "--version" });

			assertEquals(0, code, "Expected zero exit code for --version.\nLogs:\n" + logs.dump());

			assertTrue(logs.any(Level.INFO, "ASSIS"),
					"Expected version output to mention ASSIS (logged at CONFIG).\nLogs:\n" + logs.dump());
		}
	}

	private static final String ER_FILE = "entity-diagram.mmd";

	private Path runOnHello(String... flags) throws Exception {
		Path src = tempDir.resolve("src");
		Files.createDirectories(src);
		Files.writeString(src.resolve("Hello.java"), "public class Hello {\n}\n", UTF_8);
		Path out = tempDir.resolve("out");

		String[] base = { "-sourcepath", src.toString(), "-d", out.toString() };
		String[] args = java.util.stream.Stream.concat(java.util.Arrays.stream(base), java.util.Arrays.stream(flags))
				.toArray(String[]::new);
		assertEquals(0, AssisApp.run(args));
		return out;
	}

	@Test
	void generatesBothDiagramsWithoutFlags() throws Exception {
		Path out = runOnHello();

		assertTrue(Files.exists(out.resolve(PUML_FILE)));
		assertTrue(Files.exists(out.resolve(ER_FILE)));
	}

	@Test
	void erFlagGeneratesOnlyEntityDiagram() throws Exception {
		Path out = runOnHello("--er");

		assertTrue(Files.readString(out.resolve(ER_FILE), UTF_8).startsWith("erDiagram"));
		assertFalse(Files.exists(out.resolve(PUML_FILE)));
	}

	@Test
	void classFlagGeneratesOnlyClassDiagram() throws Exception {
		Path out = runOnHello("--class");

		assertTrue(Files.exists(out.resolve(PUML_FILE)));
		assertFalse(Files.exists(out.resolve(ER_FILE)));
	}

	@Test
	void bothFlagsGenerateBothDiagrams() throws Exception {
		Path out = runOnHello("--class", "--er");

		assertTrue(Files.exists(out.resolve(PUML_FILE)));
		assertTrue(Files.exists(out.resolve(ER_FILE)));
	}

	@Test
	void usageDocumentsDiagramFlags() {
		assertTrue(CliArgs.usage.contains("--class"));
		assertTrue(CliArgs.usage.contains("--er"));
	}

	private Path writeIdReferenceSample() throws Exception {
		Path src = tempDir.resolve("idsrc");
		Files.createDirectories(src);
		Files.writeString(src.resolve("OwnerId.java"), "public class OwnerId {\n}\n", UTF_8);
		Files.writeString(src.resolve("Owner.java"), "@Entity\npublic class Owner {\n\t@Id\n\tOwnerId id;\n}\n", UTF_8);
		Files.writeString(src.resolve("Pet.java"),
				"@Entity\npublic class Pet {\n\t@Id\n\tLong id;\n\tOwnerId ownerId;\n}\n", UTF_8);
		return src;
	}

	private String runEr(Path src, String... flags) throws Exception {
		Path out = tempDir.resolve("er-out");
		String[] base = { "-sourcepath", src.toString(), "-d", out.toString(), "--er" };
		String[] args = java.util.stream.Stream.concat(java.util.Arrays.stream(base), java.util.Arrays.stream(flags))
				.toArray(String[]::new);
		assertEquals(0, AssisApp.run(args));
		return Files.readString(out.resolve(ER_FILE), UTF_8);
	}

	@Test
	void erWithoutInferredHasNoDottedLines() throws Exception {
		String er = runEr(writeIdReferenceSample());

		assertTrue(er.startsWith("%% There could be relationships that were not shown"), er);
		assertFalse(er.contains("}o.."), er);
	}

	@Test
	void erWithInferredDrawsDottedLines() throws Exception {
		String er = runEr(writeIdReferenceSample(), "--inferred");

		assertFalse(er.contains("There could be relationships"), er);
		assertTrue(er.contains("\"Pet\" }o..|| \"Owner\" : \"ownerId\""), er);
	}

	@Test
	void inferredWithoutErIsRejected() {
		try (var logs = new JulLogCaptor(AssisApp.class)) {
			assertNotEquals(0, AssisApp.run(new String[] { "--inferred" }));
			assertNotEquals(0, AssisApp.run(new String[] { "--class", "--inferred" }));
			assertTrue(logs.any(Level.SEVERE, "--inferred requires --er"), logs.dump());
		}
	}

	private Path writeCatalog() throws Exception {
		Path catalog = tempDir.resolve("entity-catalog.txt");
		Files.writeString(catalog, "add relationship | Pet | Owner | many-to-one | ownerId | required\n", UTF_8);
		return catalog;
	}

	@Test
	void erWithCatalogDrawsCatalogLine() throws Exception {
		// CC1
		String er = runEr(writeIdReferenceSample(), "--catalog", writeCatalog().toString());

		assertTrue(er.contains("\"Pet\" }o--|| \"Owner\" : \"ownerId\""), er);
	}

	@Test
	void catalogWithoutDiagramFlagAppliesToEntityDiagram() throws Exception {
		// CC2
		Path out = tempDir.resolve("cc2-out");
		String[] args = { "-sourcepath", writeIdReferenceSample().toString(), "-d", out.toString(), "--catalog",
				writeCatalog().toString() };
		assertEquals(0, AssisApp.run(args));

		assertTrue(Files.exists(out.resolve(PUML_FILE)));
		assertTrue(Files.readString(out.resolve(ER_FILE), UTF_8).contains("\"Pet\" }o--|| \"Owner\" : \"ownerId\""));
	}

	@Test
	void catalogWithOnlyClassIsRejected() throws Exception {
		// CC3
		String catalog = writeCatalog().toString();
		try (var logs = new JulLogCaptor(AssisApp.class)) {
			assertNotEquals(0, AssisApp.run(new String[] { "--class", "--catalog", catalog }));
			assertTrue(logs.any(Level.SEVERE, "--catalog requires the entity diagram"), logs.dump());
		}
	}

	@Test
	void catalogWithoutValueIsRejected() {
		// CC4
		try (var logs = new JulLogCaptor(AssisApp.class)) {
			assertNotEquals(0, AssisApp.run(new String[] { "--er", "--catalog" }));
			assertTrue(logs.any(Level.SEVERE, "Missing value for --catalog"), logs.dump());
		}
	}

	@Test
	void usageDocumentsCatalog() {
		// CC5
		assertTrue(CliArgs.usage.contains("--catalog <file>"));
	}

	@Test
	void usageDocumentsInferredFlag() {
		assertTrue(CliArgs.usage.contains("--inferred"));
	}

	private static String debugBuffers(ByteArrayOutputStream outBuf, ByteArrayOutputStream errBuf) {
		return "stdout:\n" + outBuf.toString(UTF_8) + "\n\nstderr:\n" + errBuf.toString(UTF_8);
	}
}