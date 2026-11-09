package io.github.masmangan.assis.er;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void inferredOutputOnlyWithFlag() throws Exception {
		Set<Path> roots = Set.of(sample("samples/inferred"));
		String plain = generateInto(roots, "plain");
		Path outDir = tempDir.resolve("inferred");
		GenerateEntityDiagram.generate(roots, outDir, true);
		String inferred = Files.readString(outDir.resolve("entity-diagram.mmd"), StandardCharsets.UTF_8);

		assertTrue(plain.startsWith(EntityMermaidRenderer.HIDDEN_INFERRED_NOTE + "\nerDiagram\n"), plain);
		assertFalse(inferred.contains(EntityMermaidRenderer.HIDDEN_INFERRED_NOTE), inferred);
		assertFalse(plain.replace(EntityMermaidRenderer.HIDDEN_INFERRED_NOTE, "").contains(".."), plain);
		assertFalse(plain.contains(" FK"), plain);
		assertTrue(inferred.contains("\"Visit\" }o..|| \"Pet\" : \"petId\""), inferred);
		assertTrue(inferred.contains(" FK"), inferred);
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

	private String generateWithCatalog(boolean inferred, Path catalog, String outName) throws Exception {
		Path outDir = tempDir.resolve(outName);
		GenerateEntityDiagram.generate(Set.of(sample("samples/catalog")), outDir, inferred, catalog);
		return Files.readString(outDir.resolve("entity-diagram.mmd"), StandardCharsets.UTF_8);
	}

	private static final String CATALOG_ENTITIES = "erDiagram\n"
			+ "    \"Owner\" {\n        Long id PK\n    }\n"
			+ "    \"Pet\"\n";

	@Test
	void appliesCatalogToSample() throws Exception {
		// CG1
		String expected = EntityMermaidRenderer.HIDDEN_INFERRED_NOTE + "\n" + CATALOG_ENTITIES
				+ "    \"Visit\" {\n        Long id PK\n        PetId? petId\n        Set~PetId~? pastPets\n    }\n"
				+ "\n"
				+ "    %% " + EntityMermaidRenderer.CATALOG_NOTE + "\n"
				+ "    \"Visit\" }o--|| \"Pet\" : \"petId\"\n"
				+ "    \"Pet\" }o--|| \"Owner\" : \"owner\"\n";
		assertEquals(expected, generateWithCatalog(false, sample("samples/catalog-files/entity-catalog.txt"), "out"));
	}

	@Test
	void catalogComesBeforeInferredBlock() throws Exception {
		// CG2
		String expected = CATALOG_ENTITIES
				+ "    \"Visit\" {\n        Long id PK\n        PetId? petId FK\n        Set~PetId~? pastPets\n    }\n"
				+ "\n"
				+ "    %% " + EntityMermaidRenderer.CATALOG_NOTE + "\n"
				+ "    \"Visit\" }o--|| \"Pet\" : \"petId\"\n"
				+ "    \"Pet\" }o--|| \"Owner\" : \"owner\"\n"
				+ "\n"
				+ "    %% " + EntityMermaidRenderer.INFERRED_NOTE + "\n"
				+ "    \"Visit\" ||..o{ \"Pet\" : \"pastPets\"\n";
		assertEquals(expected, generateWithCatalog(true, sample("samples/catalog-files/entity-catalog.txt"), "out"));
	}

	@Test
	void noOrMissingCatalogChangesNothing() throws Exception {
		// CG3
		String plain = generateWithCatalog(false, null, "none");
		assertEquals(plain, generateWithCatalog(false, tempDir.resolve("missing.txt"), "missing"));
		assertFalse(plain.contains(EntityMermaidRenderer.CATALOG_NOTE), plain);
	}

	private static final String FROM_CATALOG = "%% " + EntityMermaidRenderer.FROM_CATALOG_NOTE + "\n";

	private static final String CATALOG_ADD_ENTITIES = "erDiagram\n"
			+ "    \"Owner\" {\n        Long id PK\n    }\n"
			+ "    " + FROM_CATALOG + "    \"Pet\"[\"pets\"]\n"
			+ "    \"Visit\" {\n        Long id PK\n        " + FROM_CATALOG + "        PetId petId FK\n"
			+ "        Set~PetId~? pastPets\n        " + FROM_CATALOG + "        String? notes\n    }\n"
			+ "    " + FROM_CATALOG + "    \"AuditLog\"[\"audit_log\"] {\n        " + FROM_CATALOG
			+ "        Long id PK\n    }\n"
			+ "    \"Pet\" }o--o| \"Owner\" : \"owner\"\n"
			+ "\n"
			+ "    %% " + EntityMermaidRenderer.CATALOG_NOTE + "\n"
			+ "    \"AuditLog\" }o--o| \"Visit\" : \"visit\"\n";

	@Test
	void addsEntitiesAndAttributesFromCatalog() throws Exception {
		// CG4
		assertEquals(EntityMermaidRenderer.HIDDEN_INFERRED_NOTE + "\n" + CATALOG_ADD_ENTITIES,
				generateWithCatalog(false, sample("samples/catalog-files/entity-catalog-add.txt"), "out"));
	}

	@Test
	void addedItemsWithInferred() throws Exception {
		// CG5
		String expected = CATALOG_ADD_ENTITIES
				+ "\n"
				+ "    %% " + EntityMermaidRenderer.INFERRED_NOTE + "\n"
				+ "    \"Visit\" }o..|| \"Pet\" : \"petId\"\n"
				+ "    \"Visit\" ||..o{ \"Pet\" : \"pastPets\"\n";
		assertEquals(expected,
				generateWithCatalog(true, sample("samples/catalog-files/entity-catalog-add.txt"), "out"));
	}
}
