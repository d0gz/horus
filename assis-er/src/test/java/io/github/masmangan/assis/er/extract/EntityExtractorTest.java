package io.github.masmangan.assis.er.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.masmangan.assis.er.EntityView;
import io.github.masmangan.assis.internal.DeclaredIndex;
import io.github.masmangan.assis.io.SmartSourceRootManager;
import io.github.masmangan.assis.util.DeterministicPathList;

class EntityExtractorTest {

	private EntityView extractFrom(String resource) throws Exception {
		return extractFrom(resource, false);
	}

	private EntityView extractFrom(String resource, boolean inferred) throws Exception {
		URL url = getClass().getClassLoader().getResource(resource);
		Path root = Paths.get(url.toURI());

		DeclaredIndex index = new DeclaredIndex();
		index.fill(new SmartSourceRootManager().autoscan(DeterministicPathList.fromSourceRoots(Set.of(root))));

		return EntityExtractor.extract(index, inferred);
	}

	private List<String> entityNamesIn(String resource) throws Exception {
		return extractFrom(resource).entities().stream().map(EntityView.Entity::name).toList();
	}

	private List<EntityView.Attribute> ownerAttributes() throws Exception {
		return extractFrom("samples/attributes").entities().get(0).attributes();
	}

	@Test
	void extractsPersistentFieldsInDeclarationOrder() throws Exception {
		assertEquals(List.of("id", "name", "a", "b", "tags", "scores", "birth", "photo"),
				ownerAttributes().stream().map(EntityView.Attribute::name).toList());
	}

	@Test
	void marksOnlyIdAsPrimaryKey() throws Exception {
		assertEquals(List.of("id"),
				ownerAttributes().stream().filter(EntityView.Attribute::primaryKey).map(EntityView.Attribute::name).toList());
	}

	@Test
	void keepsJavaTypesAsWritten() throws Exception {
		Map<String, String> types = ownerAttributes().stream()
				.collect(Collectors.toMap(EntityView.Attribute::name, EntityView.Attribute::type));

		assertEquals("List<String>", types.get("tags"));
		assertEquals("Map<String,List<Integer>>", types.get("scores"));
		assertEquals("java.time.LocalDate", types.get("birth"));
		assertEquals("byte[]", types.get("photo"));
		assertEquals("int", types.get("b"));
	}

	@Test
	void entityWithoutFieldsHasNoAttributes() throws Exception {
		assertEquals(List.of(), extractFrom("samples/entity").entities().get(0).attributes());
	}

	private List<String> relationships() throws Exception {
		return extractFrom("samples/relationships").relationships().stream()
				.map(r -> r.from() + "->" + r.to() + " " + r.cardinality() + " " + r.label()).toList();
	}

	@Test
	void bidirectionalManyToOneDrawnOnceFromOwningSide() throws Exception {
		List<String> rels = relationships();

		assertTrue(rels.contains("Pet->Owner MANY_TO_ONE owner"), rels.toString());
		assertTrue(rels.stream().noneMatch(r -> r.startsWith("Owner->")), rels.toString());
	}

	@Test
	void usesCollectionElementAsTarget() throws Exception {
		assertTrue(relationships().contains("Vet->Specialty MANY_TO_MANY specialties"));
	}

	@Test
	void oneToOneMappedBySideIsSkipped() throws Exception {
		List<String> rels = relationships();

		assertTrue(rels.contains("User->Profile ONE_TO_ONE profile"), rels.toString());
		assertTrue(rels.stream().noneMatch(r -> r.startsWith("Profile->")), rels.toString());
	}

	@Test
	void unidirectionalOneToMany() throws Exception {
		assertTrue(relationships().contains("Visit->Pet ONE_TO_MANY pets"));
	}

	@Test
	void skipsTargetThatIsNotAnEntity() throws Exception {
		assertTrue(relationships().stream().noneMatch(r -> r.contains("Address")));
	}

	@Test
	void selfReference() throws Exception {
		assertTrue(relationships().contains("Pet->Pet MANY_TO_ONE mother"));
	}

	@Test
	void relationshipsInEntityThenFieldOrder() throws Exception {
		assertEquals(List.of("Pet->Owner MANY_TO_ONE owner", "Pet->Pet MANY_TO_ONE mother",
				"User->Profile ONE_TO_ONE profile", "Vet->Specialty MANY_TO_MANY specialties",
				"Visit->Pet ONE_TO_MANY pets"), relationships());
	}

	@Test
	void detectsJakartaEntity() throws Exception {
		assertEquals(List.of("Owner"), entityNamesIn("samples/entity"));
	}

	@Test
	void ignoresNonEntityTypes() throws Exception {
		assertEquals(List.of("Owner"), entityNamesIn("samples/entitymixed"));
	}

	@Test
	void detectsJavaxEntity() throws Exception {
		assertEquals(List.of("Customer"), entityNamesIn("samples/entityjavax"));
	}

	@Test
	void detectsFullyQualifiedEntityWithArguments() throws Exception {
		assertEquals(List.of("Invoice"), entityNamesIn("samples/entityfqn"));
	}

	@Test
	void returnsEmptyViewWhenNoEntities() throws Exception {
		assertEquals(List.of(), entityNamesIn("samples/entitynone"));
	}

	private List<String> inferred() throws Exception {
		return extractFrom("samples/inferred", true).relationships().stream()
				.map(r -> r.from() + "->" + r.to() + " " + r.cardinality() + " " + r.label() + (r.inferred() ? " ~" : ""))
				.toList();
	}

	@Test
	void infersNothingWithoutFlag() throws Exception {
		EntityView view = extractFrom("samples/inferred");

		assertTrue(view.relationships().stream().noneMatch(EntityView.Relationship::inferred),
				view.relationships().toString());
		assertTrue(view.entities().stream().flatMap(e -> e.attributes().stream())
				.noneMatch(EntityView.Attribute::foreignKey));
		assertTrue(view.relationships().stream().anyMatch(r -> r.label().equals("pet")),
				"annotated relationships stay");
		assertTrue(view.hiddenInferred());
	}

	@Test
	void nothingHiddenWhenFlagOnOrNoIdReferences() throws Exception {
		assertFalse(extractFrom("samples/inferred", true).hiddenInferred());
		assertFalse(extractFrom("samples/relationships").hiddenInferred());
	}

	@Test
	void infersManyToOneFromIdTypeOfSuperclassGeneric() throws Exception {
		assertTrue(inferred().contains("Visit->Pet MANY_TO_ONE petId ~"), inferred().toString());
	}

	@Test
	void infersFromIdFieldType() throws Exception {
		assertTrue(inferred().contains("Pet->Owner MANY_TO_ONE ownerId ~"), inferred().toString());
	}

	@Test
	void doesNotInferFromOwnIdType() throws Exception {
		assertTrue(inferred().stream().noneMatch(r -> r.startsWith("Visit->Visit")), inferred().toString());
	}

	@Test
	void infersOneToManyFromIdCollection() throws Exception {
		assertTrue(inferred().contains("Owner->Pet ONE_TO_MANY petIds ~"), inferred().toString());
	}

	@Test
	void annotatedFieldIsNotAlsoInferred() throws Exception {
		List<String> rels = inferred();

		assertTrue(rels.contains("Visit->Pet MANY_TO_ONE pet"), rels.toString());
		assertEquals(1, rels.stream().filter(r -> r.contains(" pet ") || r.endsWith(" pet")).count(), rels.toString());
	}

	@Test
	void ignoresGenericOfNonIdStyleBase() throws Exception {
		assertTrue(inferred().stream().noneMatch(r -> r.contains("Order")), inferred().toString());
	}

	@Test
	void skipsIdTypeOwnedBySeveralEntities() throws Exception {
		assertTrue(inferred().stream().noneMatch(r -> r.contains("sharedId")), inferred().toString());
	}

	@Test
	void skipsJdkTypedIds() throws Exception {
		assertTrue(inferred().stream().noneMatch(r -> r.contains("ownerNumber")), inferred().toString());
	}

	@Test
	void idFieldWinsOverSuperclassGeneric() throws Exception {
		List<String> rels = inferred();

		assertTrue(rels.contains("Visit->Vet MANY_TO_ONE vetId ~"), rels.toString());
		assertTrue(rels.stream().noneMatch(r -> r.contains("otherId")), rels.toString());
	}

	@Test
	void jdkTypedIdFieldFallsBackToSuperclassGeneric() throws Exception {
		assertTrue(inferred().contains("Visit->Clinic MANY_TO_ONE clinicId ~"), inferred().toString());
	}

	@Test
	void optionalAndUnknownGenericsAreManyToOne() throws Exception {
		assertTrue(inferred().contains("Shelter->Pet MANY_TO_ONE mascot ~"), inferred().toString());
		assertTrue(inferred().contains("Shelter->Pet MANY_TO_ONE featured ~"), inferred().toString());
	}

	@Test
	void mapOfIdsIsOneToMany() throws Exception {
		assertTrue(inferred().contains("Shelter->Pet ONE_TO_MANY pets ~"), inferred().toString());
	}

	@Test
	void marksInferredSingleValuedReferencesAsForeignKeys() throws Exception {
		var fks = extractFrom("samples/inferred", true).entities().stream().flatMap(
				e -> e.attributes().stream().filter(EntityView.Attribute::foreignKey).map(a -> e.name() + "." + a.name()))
				.toList();

		assertTrue(fks.contains("Pet.ownerId"), fks.toString());
		assertTrue(fks.contains("Visit.petId"), fks.toString());
		assertTrue(fks.stream().noneMatch(f -> f.equals("Owner.petIds") || f.equals("Shelter.pets")), fks.toString());
		assertTrue(fks.stream().noneMatch(f -> f.equals("Visit.previousVisitId") || f.equals("Clinic.number")),
				fks.toString());
	}

	@Test
	void inferredSampleRelationshipsInOrder() throws Exception {
		assertEquals(List.of("Owner->Pet ONE_TO_MANY petIds ~", "Pet->Owner MANY_TO_ONE ownerId ~",
				"Shelter->Pet MANY_TO_ONE mascot ~", "Shelter->Pet ONE_TO_MANY pets ~",
				"Shelter->Pet MANY_TO_ONE featured ~",
				"Visit->Pet MANY_TO_ONE pet", "Visit->Pet MANY_TO_ONE petId ~", "Visit->Vet MANY_TO_ONE vetId ~",
				"Visit->Clinic MANY_TO_ONE clinicId ~"),
				inferred());
	}

	private Map<String, Boolean> requiredByLabel() throws Exception {
		return extractFrom("samples/optionality").relationships().stream()
				.collect(Collectors.toMap(EntityView.Relationship::label, EntityView.Relationship::required));
	}

	private Map<String, Boolean> petNullable() throws Exception {
		return extractFrom("samples/optionality").entities().stream().filter(e -> e.name().equals("Pet")).findFirst()
				.orElseThrow().attributes().stream()
				.collect(Collectors.toMap(EntityView.Attribute::name, EntityView.Attribute::nullable));
	}

	@Test
	void manyToOneWithoutMembersIsOptional() throws Exception {
		assertEquals(false, requiredByLabel().get("owner"));
	}

	@Test
	void manyToOneOptionalFalseIsRequired() throws Exception {
		assertEquals(true, requiredByLabel().get("clinic"));
	}

	@Test
	void joinColumnNotNullableIsRequired() throws Exception {
		assertEquals(true, requiredByLabel().get("breeder"));
	}

	@Test
	void requiredWinsOverOptionalTrue() throws Exception {
		assertEquals(true, requiredByLabel().get("shelter"));
	}

	@Test
	void oneToOneOptionalAndRequired() throws Exception {
		Map<String, Boolean> required = requiredByLabel();

		assertEquals(false, required.get("passport"));
		assertEquals(true, required.get("chip"));
	}

	@Test
	void columnNotNullableMarksAttribute() throws Exception {
		Map<String, Boolean> nullable = petNullable();

		assertEquals(false, nullable.get("name"));
		assertEquals(true, nullable.get("nickname"));
	}

	@Test
	void basicOptionalFalseMarksAttribute() throws Exception {
		assertEquals(false, petNullable().get("code"));
	}

	@Test
	void idIsNeverNullable() throws Exception {
		assertEquals(false, petNullable().get("id"));
	}

	@Test
	void primitivesAreNeverNullable() throws Exception {
		Map<String, Boolean> nullable = ownerAttributes().stream()
				.collect(Collectors.toMap(EntityView.Attribute::name, EntityView.Attribute::nullable));

		assertEquals(false, nullable.get("a"));
		assertEquals(false, nullable.get("b"));
		assertEquals(true, nullable.get("name"));
		assertEquals(true, nullable.get("photo"));
	}

	@Test
	void explicitNullableWinsOverPrimitive() throws Exception {
		Map<String, Boolean> nullable = extractFrom("samples/primitivenull").entities().get(0).attributes().stream()
				.collect(Collectors.toMap(EntityView.Attribute::name, EntityView.Attribute::nullable));

		assertEquals(false, nullable.get("id"));
		assertEquals(false, nullable.get("plain"));
		assertEquals(true, nullable.get("explicitNull"));
		assertEquals(true, nullable.get("explicitOptional"));
		assertEquals(false, nullable.get("boxedNotNull"));
		assertEquals(true, nullable.get("boxed"));
		assertEquals(false, nullable.get("validated"));
		assertEquals(false, nullable.get("validatedWins"));
	}

	private EntityView naming() throws Exception {
		return extractFrom("samples/naming");
	}

	private EntityView.Entity namingEntity(String name) throws Exception {
		return naming().entities().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
	}

	private Map<String, String> ownerColumns() throws Exception {
		Map<String, String> columns = new LinkedHashMap<>();
		namingEntity("Owner").attributes().forEach(a -> columns.put(a.name(), a.columnName()));
		return columns;
	}

	@Test
	void readsTableName() throws Exception {
		assertEquals("owners", namingEntity("Owner").tableName());
		assertEquals("pet%stats", namingEntity("Stats").tableName());
	}

	@Test
	void tableWithoutNameOrEqualNameIsNull() throws Exception {
		assertEquals(null, namingEntity("Pet").tableName());
		assertEquals(null, namingEntity("Visit").tableName());
		assertEquals(null, namingEntity("Vet").tableName());
		assertEquals(null, extractFrom("samples/entity").entities().get(0).tableName());
	}

	@Test
	void readsColumnNameKeepingJavaName() throws Exception {
		Map<String, String> columns = ownerColumns();

		assertEquals("full_name", columns.get("fullName"));
		assertEquals("e_mail", columns.get("email"));
		assertEquals("home address", columns.get("address"));
	}

	@Test
	void columnWithoutNameEqualNameOrConstantIsNull() throws Exception {
		Map<String, String> columns = ownerColumns();

		assertEquals(null, columns.get("phone"));
		assertEquals(null, columns.get("city"));
		assertEquals(null, columns.get("note"));
		assertTrue(columns.containsKey("id") && columns.get("id") == null);
	}

	@Test
	void blankTableNameIsNull() throws Exception {
		assertEquals(null, namingEntity("Tag").tableName());
	}

	@Test
	void emptyColumnNameIsNull() throws Exception {
		assertEquals(null, namingEntity("Tag").attributes().get(1).columnName());
	}
}
