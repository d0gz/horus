# Entity Diagram — Extractor Refactor Plan

Companion to [`entity-diagram-plan.md`](entity-diagram-plan.md) (default output) and
[`entity-diagram-inferred-plan.md`](entity-diagram-inferred-plan.md) (opt-in heuristics).
Decision numbers (`D…`) are shared and stay unique across the three files.

**Goal:** split `EntityExtractor` (one 307-line static class) into small modules inside a
new `er.extract` package, so that later work (FIX-NOTNULL, FIX-SIMPLENAME, ERI-ID,
FIX-WRAPPER, FIX-DUPLINK, FIX-SELFREF, embeddables, inheritance) adds or changes **one
module** instead of reshaping the whole extractor.

**Rule: the output does not change.** For every input, the `EntityView` and the `.mmd`
file are identical before and after, with and without `--inferred`. No test expectation
changes. This includes the questionable behaviours listed under "Kept as is".

## Roadmap

| ID | Scope | Status |
|----|-------|--------|
| ER-REFACTOR | Split `EntityExtractor` into `er.extract` modules, same output | ✅ Done |

Status: ✅ done · ❌ not done · ⚠️ done with a warning or problem · 🛑 blocked · 🟦 fix task

## Problems in the current class

1. `extract()` does everything: finds entities, collects project types, resolves ID types,
   and builds attributes, relationships and inferred relationships.
2. `types` and `ownIds` are parallel lists matched by index (`ownIds.get(i)`).
3. Context (`names`, `projectTypes`, `owners`, `ownId`) is passed as separate parameters,
   so each new piece of context changes many signatures.
4. The persistent-field filter (`static` / `transient` / `@Transient` / relationship
   annotation) is copied in `attributesOf` and `inferredOf`.
5. `NOT_ATTRIBUTES` repeats the keys of `CARDINALITIES`.
6. JPA annotation helpers, type helpers, default extraction and inference heuristics all
   live in one file.

## Decisions

| # | Decision | Reason |
|---|----------|--------|
| D38 | All extraction code moves to package `io.github.masmangan.assis.er.extract` (folder `er/extract/`). Only `EntityExtractor` is `public`; every other class there is package-private and `final`, with static methods | One place to look; helpers do not leak into the module API. |
| D39 | One module per concern: JPA annotation knowledge (`Jpa`), type-name helpers (`TypeRefs`), the shared context (`ExtractionContext`, `EntitySource`), ID resolution (`IdResolver`), and one extractor per output (`AttributeExtractor`, `RelationshipExtractor`, `InferredRelationshipExtractor`) | Each roadmap item maps to one module (see "Where future work goes"). |
| D40 | Extractors share the signature `static List<X> of(EntitySource entity, ExtractionContext ctx)` | New context goes into the record, not into every signature. |
| D41 | The default and inferred paths stay separate: `IdResolver`, `InferredRelationshipExtractor` and `ID_STYLE_BASES` hold all heuristics; `Jpa`, `AttributeExtractor` and `RelationshipExtractor` read only what the code states | Keeps the "a wrong line is not acceptable" boundary visible in the file layout. |
| D42 | `EntityView`, `EntityMermaidRenderer`, `GenerateEntityDiagram` and `types/` stay in their packages | Out of scope; only the extractor is reorganized. A later `er.render` package can follow the same rule. |

## Target structure

```
assis-er/src/main/java/io/github/masmangan/assis/er/
├── EntityView.java               (unchanged)
├── EntityMermaidRenderer.java    (unchanged)
├── GenerateEntityDiagram.java    (import changes to er.extract.EntityExtractor)
├── types/
│   ├── Cardinality.java          (unchanged)
│   └── CollectionTypes.java      (unchanged)
└── extract/
    ├── EntityExtractor.java              public facade, orchestration only
    ├── ExtractionContext.java            record: shared context, built once per index
    ├── EntitySource.java                 record: one entity declaration + its own ID type
    ├── Jpa.java                          JPA annotation names and readers
    ├── TypeRefs.java                     type-name helpers
    ├── IdResolver.java                   ID type per entity, ID type → owning entity (heuristic)
    ├── AttributeExtractor.java           EntityView.Attribute list of one entity
    ├── RelationshipExtractor.java        annotated relationships of one entity
    └── InferredRelationshipExtractor.java  ID-type relationships of one entity (heuristic)
```

Test moves with the class:
`assis-er/src/test/java/io/github/masmangan/assis/er/extract/EntityExtractorTest.java`
(only the `package` line and nothing else changes).

## Modules

Each module's code is moved from the current `EntityExtractor`, keeping its logic. The
sketches below show signatures and the parts that move; bodies match the current code
unless a note says otherwise.

### `Jpa` — JPA annotation knowledge

Everything that knows a JPA annotation name. Matched by simple name (D4).

```java
final class Jpa {
	static final String ENTITY = "Entity";
	static final String TABLE = "Table";
	static final String COLUMN = "Column";
	static final String JOIN_COLUMN = "JoinColumn";
	static final String BASIC = "Basic";
	static final String ID = "Id";
	static final String TRANSIENT = "Transient";
	static final Set<String> ID_ANNOTATIONS = Set.of(ID, "EmbeddedId");
	static final Map<String, Cardinality> RELATIONSHIPS = Map.of(
			"ManyToOne", Cardinality.MANY_TO_ONE, "OneToMany", Cardinality.ONE_TO_MANY,
			"OneToOne", Cardinality.ONE_TO_ONE, "ManyToMany", Cardinality.MANY_TO_MANY);

	static boolean isEntity(TypeDeclaration<?> td)
	static boolean has(NodeWithAnnotations<?> n, String simpleName)
	static boolean hasAny(NodeWithAnnotations<?> n, Set<String> simpleNames)   // was hasAnyAnnotation
	static boolean isIdField(FieldDeclaration fd)                               // hasAny(fd, ID_ANNOTATIONS)
	static boolean isRelationshipField(FieldDeclaration fd)                     // hasAny(fd, RELATIONSHIPS.keySet())
	/** Not static, not transient, no @Transient, no relationship annotation. */
	static boolean isColumnField(FieldDeclaration fd)                           // replaces the copied filter
	static boolean hasMappedBy(AnnotationExpr a)
	static boolean isFalse(AnnotationExpr a, String member)
	static boolean hasFalseMember(FieldDeclaration fd, String annotation, String member)
	static String nameIfDifferent(NodeWithAnnotations<?> n, String annotation, String javaName)
}
```

`NOT_ATTRIBUTES` disappears: `isColumnField` = `!fd.isStatic() && !fd.isTransient() &&
!has(fd, TRANSIENT) && !isRelationshipField(fd)`. Same set of names as before.

### `TypeRefs` — type names

```java
final class TypeRefs {
	static String targetOf(Type type)      // last type argument, recursive, else the type
	static String simpleName(Type type)
	static boolean isCollection(Type type) // delegates to types.CollectionTypes
}
```

### `EntitySource` and `ExtractionContext` — shared context

Replaces the parallel lists and the threaded parameters.

```java
record EntitySource(TypeDeclaration<?> type, Optional<String> ownId) {
	String name() { return type.getNameAsString(); }
	boolean isOwnId(String typeName) { return ownId.filter(typeName::equals).isPresent(); }
}

record ExtractionContext(List<EntitySource> entities, Set<String> entityNames,
		Set<String> projectTypes, Map<String, String> idOwners, boolean inferred) {

	static ExtractionContext of(DeclaredIndex index, boolean inferred)
	/** idOwners when inference is on, else empty: FK markers are inferred output (D35). */
	Map<String, String> fkOwners()
}
```

`of` does what the top of `extract()` does today, in the same order: entity types in
index order → `entityNames` → `projectTypes` (all index types) → one `EntitySource` per
entity with `IdResolver.idTypeOf(...)` → `idOwners = IdResolver.owners(entities)`.

### `IdResolver` — ID types (heuristic)

```java
final class IdResolver {
	private static final Set<String> ID_STYLE_BASES = Set.of("AbstractEntity", "AbstractVersionedEntity");

	/** @Id/@EmbeddedId field type, else the type argument of a known ID-style base. */
	static Optional<String> idTypeOf(TypeDeclaration<?> td, Set<String> projectTypes, Set<String> entityNames)
	/** ID type → owning entity; ID types claimed by several entities are dropped. */
	static Map<String, String> owners(List<EntitySource> entities)
}
```

`idTypeOf` keeps the current `break` after the first `@Id`/`@EmbeddedId` field. `owners`
takes `EntitySource`s instead of two parallel lists. `isIdCandidate` moves here as a
private method.

### `AttributeExtractor`

```java
final class AttributeExtractor {
	static List<EntityView.Attribute> of(EntitySource entity, ExtractionContext ctx)
}
```

Body = current `attributesOf`, with `Jpa.isColumnField(fd)` as filter, `ctx.fkOwners()`
in place of `owners`, and `entity.isOwnId(ref)` in place of `ownId.filter(...)`.

### `RelationshipExtractor`

```java
final class RelationshipExtractor {
	static List<EntityView.Relationship> of(EntitySource entity, ExtractionContext ctx)
}
```

Body = current `relationshipsOf`, with `Jpa.RELATIONSHIPS`, `ctx.entityNames()` and
`isToOne` as a private method.

### `InferredRelationshipExtractor` (heuristic)

```java
final class InferredRelationshipExtractor {
	static List<EntityView.Relationship> of(EntitySource entity, ExtractionContext ctx)
}
```

Body = current `inferredOf`. Filter: `!Jpa.isColumnField(fd) || Jpa.isIdField(fd)` →
skip. Uses `ctx.idOwners()` (the full map, regardless of the flag) and
`entity.isOwnId(idType)`.

### `EntityExtractor` — facade

The public API is unchanged: `extract(DeclaredIndex)` and `extract(DeclaredIndex, boolean)`.

```java
public static EntityView extract(DeclaredIndex index, boolean inferred) {
	ExtractionContext ctx = ExtractionContext.of(index, inferred);
	List<EntityView.Entity> entities = new ArrayList<>();
	List<EntityView.Relationship> relationships = new ArrayList<>();
	boolean hiddenInferred = false;

	for (EntitySource entity : ctx.entities()) {
		entities.add(new EntityView.Entity(entity.name(), AttributeExtractor.of(entity, ctx),
				Jpa.nameIfDifferent(entity.type(), Jpa.TABLE, entity.name())));
		relationships.addAll(RelationshipExtractor.of(entity, ctx));

		List<EntityView.Relationship> guessed = InferredRelationshipExtractor.of(entity, ctx);
		if (inferred) {
			relationships.addAll(guessed);
		} else if (!guessed.isEmpty()) {
			hiddenInferred = true;
		}
	}
	return new EntityView(entities, relationships, hiddenInferred);
}
```

Order is unchanged: per entity, annotated relationships, then inferred ones (the renderer
regroups them per D37).

## Kept as is (not fixed here)

These behaviours look wrong or arbitrary but are preserved, because the rule is the same
output. Each one needs its own fix task with tests.

- `primaryKey` is set only by `@Id`, while `nullable` treats both `@Id` and `@EmbeddedId`
  as ids. An `@EmbeddedId` field is shown with no `PK` and no `?`. Candidate fix:
  🟦 FIX-EMBEDDEDID (not planned yet; needs a decision).
- Inference runs even with the flag off, only to set `hiddenInferred` (D36). Intended.
- `idTypeOf` stops at the first `@Id`/`@EmbeddedId` field even when its type is not a
  candidate, then falls back to the base generic (I11). Intended.

## Where future work goes

| Item | Module(s) touched |
|------|-------------------|
| 🟦 FIX-NOTNULL | `Jpa` (not-null names, `isNotNull(fd)`), `AttributeExtractor` (one condition) |
| 🟦 FIX-SIMPLENAME | `ExtractionContext` (replace `entityNames` with a name resolver), `RelationshipExtractor`, `TypeRefs` |
| ERI-ID | `IdResolver` (already knows the base generic), `AttributeExtractor` (prepend synthetic `id PK` when `ctx.inferred()`) |
| 🟦 FIX-WRAPPER | `TypeRefs` (unwrap only collections and `Optional`), `InferredRelationshipExtractor` |
| 🟦 FIX-DUPLINK | `EntityExtractor` (drop guessed links that match an annotated pair) or a new `InferredFilter` |
| 🟦 FIX-SELFREF | `EntitySource` (own ID **field**, not type), `InferredRelationshipExtractor` |
| `@Embedded` / `@MappedSuperclass` / inheritance | new module (`FieldSource` or `EmbeddedExtractor`) that returns the fields to scan; extractors iterate it instead of `td.getFields()` |
| New output element (e.g. unique keys) | new `XExtractor.of(entity, ctx)` + one line in `EntityExtractor` |

## Required checks

No new behaviour means no new test IDs. The proof is that nothing changes:

| ID | Check | Expected |
|----|-------|----------|
| RF1 | `mvn -q test` from the root | all green, including every R/E/G/N/O/T/M/I/F test, with no edits to expected values |
| RF2 | Golden diff: `--er` and `--er --inferred` over every `assis-er/src/test/resources/samples/*` dir and `../petclinic`, before vs after | `diff -r` empty |
| RF3 | `EntityExtractorTest` | only its `package` line changed, plus `import io.github.masmangan.assis.er.EntityView` (no longer same package) |
| RF4 | `grep -rn "class " assis-er/src/main/java/io/github/masmangan/assis/er/extract` | only `EntityExtractor` is `public` |

## ER-REFACTOR — Implementation Plan

> **For agentic workers:** use superpowers:executing-plans. Steps use `- [ ]`. No
> commits (the user commits); report changed files instead.

**Global constraints:** Java 17, tabs, no copyright header, few comments (one-line Javadoc
only where the name does not say it). No `assis-core` changes. Run `graphify query` before
reading code and `graphify update .` after changes (see root `CLAUDE.md`).

### Task 1: Golden snapshot (before any change)

- [ ] `mvn -q -DskipTests package` from the root.
- [ ] For each dir `d` in `assis-er/src/test/resources/samples/*` and `../petclinic`, run
  `java -jar assis-cli/target/assis-cli-*-SNAPSHOT.jar -sourcepath <d> -d <scratch>/before/<name>/plain --er`
  and the same with `--inferred` into `.../inferred`. Use the session scratchpad, not the repo.
  Use the shaded jar, not `original-*.jar`.
- [ ] `mvn -q test` from the root: green baseline.

### Task 2: `Jpa` and `TypeRefs`

- [ ] Create `er/extract/Jpa.java` and `er/extract/TypeRefs.java` by moving the helpers
  and constants listed above. Add `isColumnField`, `isIdField`, `isRelationshipField`.

### Task 3: `EntitySource`, `ExtractionContext`, `IdResolver`

- [ ] Create the two records and `IdResolver` as specified.

### Task 4: The three extractors

- [ ] Create `AttributeExtractor`, `RelationshipExtractor`, `InferredRelationshipExtractor`
  from `attributesOf`, `relationshipsOf`, `inferredOf`.

### Task 5: Facade and move

- [ ] Create `er/extract/EntityExtractor.java` (facade above, with both public methods
  and the class Javadoc kept). Delete `er/EntityExtractor.java`.
- [ ] `GenerateEntityDiagram`: import `io.github.masmangan.assis.er.extract.EntityExtractor`.
- [ ] Move `EntityExtractorTest` to `src/test/java/io/github/masmangan/assis/er/extract/`
  and change its `package` line only.
- [ ] `grep -rn "er.EntityExtractor\|EntityExtractor" --include=*.java .` outside `target/`:
  no stale references.

### Task 6: Verify

- [ ] RF1: `mvn -q test` from the root.
- [ ] RF2: rebuild the jar, rerun Task 1 into `<scratch>/after`, `diff -r before after`.
- [ ] RF3, RF4.
- [ ] `graphify update .`.
- [ ] Set ER-REFACTOR to ✅ in this file. In `assis-er/CLAUDE.md`, remove the "pending"
  note at the top of "Extractor structure".
- [ ] Update the "Components" wording of the other two plan files only when the user asks
  (they still say "`EntityExtractor`: …"; with the new layout that means the matching
  module).

### Done when

- RF1–RF4 pass, `graphify update .` run, roadmap row ✅. No commits.
