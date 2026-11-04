package io.github.masmangan.assis.er;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.github.masmangan.assis.internal.DeclaredIndex;
import io.github.masmangan.assis.io.SmartSourceRootManager;
import io.github.masmangan.assis.util.DeterministicPathList;

class EntityExtractorTest {

	private EntityView extractFrom(String resource) throws Exception {
		URL url = getClass().getClassLoader().getResource(resource);
		Path root = Paths.get(url.toURI());

		DeclaredIndex index = new DeclaredIndex();
		index.fill(new SmartSourceRootManager().autoscan(DeterministicPathList.fromSourceRoots(Set.of(root))));

		return EntityExtractor.extract(index);
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
		return extractFrom("samples/inferred").relationships().stream()
				.map(r -> r.from() + "->" + r.to() + " " + r.cardinality() + " " + r.label() + (r.inferred() ? " ~" : ""))
				.toList();
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
		var fks = extractFrom("samples/inferred").entities().stream().flatMap(
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
}
