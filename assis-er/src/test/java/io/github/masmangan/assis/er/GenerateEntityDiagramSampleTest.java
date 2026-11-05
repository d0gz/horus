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
	void writesAttributesForSample() throws Exception {
		String expected = """
				erDiagram
				    "Owner" {
				        Long id PK
				        String? name
				        int a
				        int b
				        List~String~? tags
				        Map~String,List~Integer~~? scores
				        LocalDate? birth
				        byte[]? photo
				    }
				    "Owner" }o--o| "Owner" : "parent"
				""";

		assertEquals(expected, generateInto(Set.of(sample("samples/attributes")), "out"));
	}

	@Test
	void writesRelationshipsForSample() throws Exception {
		String block = "    \"%s\" {\n        Long id PK\n    }\n";
		StringBuilder expected = new StringBuilder("erDiagram\n");
		for (String e : List.of("Owner", "Pet", "Profile", "Specialty", "User", "Vet", "Visit")) {
			expected.append(block.formatted(e));
		}
		expected.append("""
				    "Pet" }o--o| "Owner" : "owner"
				    "Pet" }o--o| "Pet" : "mother"
				    "User" ||--o| "Profile" : "profile"
				    "Vet" }o--o{ "Specialty" : "specialties"
				    "Visit" ||--o{ "Pet" : "pets"
				""");

		assertEquals(expected.toString(), generateInto(Set.of(sample("samples/relationships")), "out"));
	}

	@Test
	void writesOptionalityForSample() throws Exception {
		String block = "    \"%s\" {\n        Long id PK\n    }\n";
		StringBuilder expected = new StringBuilder("erDiagram\n");
		for (String e : List.of("Breeder", "Chip", "Clinic", "Owner", "Passport")) {
			expected.append(block.formatted(e));
		}
		expected.append("""
				    "Pet" {
				        Long id PK
				        String name
				        String code
				        String? nickname
				    }
				""");
		expected.append(block.formatted("Shelter"));
		expected.append("""
				    "Pet" }o--o| "Owner" : "owner"
				    "Pet" }o--|| "Clinic" : "clinic"
				    "Pet" }o--|| "Breeder" : "breeder"
				    "Pet" }o--|| "Shelter" : "shelter"
				    "Pet" ||--o| "Passport" : "passport"
				    "Pet" ||--|| "Chip" : "chip"
				""");

		assertEquals(expected.toString(), generateInto(Set.of(sample("samples/optionality")), "out"));
	}

	@Test
	void writesNamingForSample() throws Exception {
		String expected = """
				erDiagram
				    "Owner"["owners"] {
				        Long id PK
				        %% fullName
				        String? full_name
				        %% email
				        String e_mail
				        String phone
				        String? city
				        %% column: home address
				        String? address
				        String? note
				    }
				    "Pet" {
				        %% id
				        Long pet_id PK
				    }
				    %% table: pet%stats
				    "Stats" {
				        Long id PK
				    }
				    "Tag" {
				        Long id PK
				        String? label
				    }
				    "Vet" {
				        Long id PK
				    }
				    "Visit" {
				        Long id PK
				    }
				    "Pet" }o--o| "Owner" : "owner"
				""";

		assertEquals(expected, generateInto(Set.of(sample("samples/naming")), "out"));
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
