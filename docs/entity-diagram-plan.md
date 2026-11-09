# Entity Diagram (Mermaid `erDiagram`) — Plan & Specs

Living document. Tracks the roadmap, the spec of each part, and its required tests.
Each part goes through: spec → approval → implementation plan → implementation → done.

This file covers the **default output**: only what the code states (JPA annotations,
fields, types). Heuristics that infer what the code does not state (references by ID
type, synthetic IDs from known base classes) live in
[`entity-diagram-inferred-plan.md`](entity-diagram-inferred-plan.md) and are opt-in.
Principle: a missing line is acceptable, a wrong line is not.

Code layout: since ER-REFACTOR ([`entity-diagram-refactor-plan.md`](entity-diagram-refactor-plan.md),
D38–D42) the extractor is split into modules in package `io.github.masmangan.assis.er.extract`.
"Components" sections written before that say `EntityExtractor` for what now lives in:
`Jpa` (annotation names and readers), `TypeRefs` (type helpers), `ExtractionContext` /
`EntitySource` (shared context), `AttributeExtractor`, `RelationshipExtractor`,
`IdResolver` and `InferredRelationshipExtractor` (heuristics). `EntityExtractor` is only
the public facade. New sections name the module they change. Agent rules: `assis-er/CLAUDE.md`.

## Goal

Add a new Maven module `assis-er` that reads JPA entity annotations from Java sources and emit a
Mermaid `erDiagram`, following the layered pattern used by `assis-pom`:

```
Extractor  ──►  View (plain records)  ──►  MermaidRenderer (pure, static)  ──►  .mmd file
```

| assis-pom (reference)      | assis-er (new)            |
|----------------------------|---------------------------|
| `PomDependencyExtractor`   | `er.extract.EntityExtractor` (facade over the `er.extract` modules) |
| `PomView`                  | `EntityView`              |
| `PomMermaidRenderer`       | `EntityMermaidRenderer`   |
| `dependencies.mmd`         | `entity-diagram.mmd`      |

## Decisions

| # | Decision | Reason |
|---|----------|--------|
| D1 | Mermaid `erDiagram` (not `classDiagram`) | Native crow's-foot cardinality and PK/FK markers map directly to JPA. |
| D2 | Own module `assis-er` (package `io.github.masmangan.assis.er`), depending on `assis-core`; `assis-core` itself is not modified. Extraction code lives in sub-package `er.extract` (D38) | Keeps the ER feature isolated like `assis-pom`, while reusing the public parse pipeline (`SmartSourceRootManager`, `DeterministicPathList`, `DeclaredIndex`). |
| D3 | Separate output `entity-diagram.mmd`; `class-diagram.puml` untouched | No regression risk on the existing generator. |
| D4 | Annotations matched by simple name (`Entity`) | Same rule as `DeclaredIndex.stereotypesOf`; covers `jakarta.persistence` and `javax.persistence`, imported or fully qualified. |
| D5 | Start as simple as possible; every part ships with required tests | Incremental, verifiable steps. |
| D6 | Entity names are always double-quoted (`"Owner"`) | Valid Mermaid for any name. |
| D7 | Java names only (class and field names); `@Table`/`@Column` names ignored for now (**superseded by D22, ER-NAME**) | Simplest. |
| D8 | Generic types render with `~` instead of `<`/`>` (`Map<String, List<Integer>>` → `Map~String,List~Integer~~`) | Mermaid 11 ER lexer accepts `~…~` in attribute types. Verified against `erDiagram.jison` (mermaid@11.4.1), including nested generics, wildcards and arrays. |
| D9 | Package qualifiers are stripped from types (`java.time.LocalDate` → `LocalDate`) | A dot outside `~…~` is a Mermaid parse error (verified). |
| D10 | `EntityView` keeps the Java type exactly as written; Mermaid conversion (D8, D9) happens only in the renderer | The view stays free of Mermaid concerns, as in `assis-pom`. |

## Roadmap

| ID | Scope | Status |
|----|-------|--------|
| ER-ENTITY | Entity detection: `@Entity` classes → one entity line each | ✅ Done |
| ER-ATTR | Attributes: fields, Java types (generics with `~`), `@Id` → `PK`, transient fields excluded | ✅ Done |
| ER-REL | Relationships: `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`, fixed cardinality per annotation, `mappedBy` dedupe | ✅ Done |
| ER-CLI | Wiring: `--er` / `--class` CLI flags, user docs | ✅ Done |
| ER-NULL | Optionality and nullability: required/optional relationship ends, `?` on nullable column types | ✅ Done |
| ER-NAME | `@Table` / `@Column` names (entity alias, column name with Java name as comment) | ✅ Done |
| ER-REFACTOR | Extractor split into `er.extract` modules, same output ([refactor plan](entity-diagram-refactor-plan.md)) | ✅ Done |

Fixes (problems found in the default output, against the "never wrong" rule):

| ID | Scope | Status |
|----|-------|--------|
| 🟦 FIX-NOTNULL | Bean Validation / Lombok not-null annotations suppress `?` | ❌ Planned |
| 🟦 FIX-SIMPLENAME | Entities and relationship targets resolved by qualified name, not simple name | ❌ Planned |

Status: ✅ done · ❌ not done · ⚠️ done with a warning or problem · 🛑 blocked · 🟦 fix task

ERI-REF, ERI-ID and ERI-FLAG (inferred references, synthetic IDs, the opt-in flag) are in
[`entity-diagram-inferred-plan.md`](entity-diagram-inferred-plan.md).

ERC-CORE, ERC-ADD and ERC-REMOVE (user catalog `--catalog <file>` that adds or removes
relationships, entities and attributes, overruling the extraction) are in
[`entity-diagram-catalog-plan.md`](entity-diagram-catalog-plan.md).

Out of scope (deferred, no part assigned): value types and inheritance —
`@Embeddable`/`@Embedded`, `@MappedSuperclass` and fields of superclasses, `@Inheritance`.
An entity shows only the fields it declares itself. When picked up, these get their own
module that supplies the fields to scan (see "Where future work goes" in the refactor plan).

### Future: naming option

Naming from `@Table` / `@Column` is specified in ER-NAME and applied automatically when
the annotations are present. A CLI flag to switch back to pure Java names is a
possible follow-up, not in scope.

---

## ER-ENTITY — Entity detection

### Scope

In:
- Find every type in the source roots annotated with `@Entity`.
- Emit a Mermaid `erDiagram` with one line per entity, using the class simple name.
- Public entry point that writes `entity-diagram.mmd` into an output directory.

Out (later parts or deferred): attributes, `@Table` names, relationships, CLI
flag, simple-name collisions across packages, nested entity naming. Inheritance
and embeddables are deferred entirely.

### Components

Package: `io.github.masmangan.assis.er` (the extractor moved to `er.extract` in ER-REFACTOR)

1. **`EntityView`** — the model. No JavaParser or Mermaid types.
   ```java
   public record EntityView(List<Entity> entities) {
       public record Entity(String name) {}
   }
   ```

2. **`EntityExtractor`** — `static EntityView extract(DeclaredIndex index)`.
   Iterates `index.typesInIndexOrder()`, keeps types whose annotations contain
   simple name `Entity`, maps each to `Entity(td.getNameAsString())`.
   Order = index order (already deterministic).

3. **`EntityMermaidRenderer`** — `static String render(EntityView view)`.
   Pure function. Output:
   ```
   erDiagram
       "Owner"
       "Pet"
   ```
   Entity names are always double-quoted. Empty view → `erDiagram\n`. Indent = 4 spaces, `\n` line endings.

4. **`GenerateEntityDiagram`** (same package) —
   `public static void generate(Set<Path> sourceRoots, Path outDir)`.
   Mirrors `GenerateClassDiagram.generate`: same argument validation, same
   `SmartSourceRootManager.autoscan` + `DeclaredIndex.fill`, then
   extract → render → write `outDir/entity-diagram.mmd` (UTF-8).

### Data flow

```
sourceRoots → SmartSourceRootManager.autoscan → DeclaredIndex.fill
            → EntityExtractor.extract → EntityView
            → EntityMermaidRenderer.render → Files.writeString(entity-diagram.mmd)
```

### Error handling

Same contract as `GenerateClassDiagram`:
- `null` `sourceRoots` / `outDir` → `NullPointerException`.
- `outDir` exists and is not a directory → `IllegalArgumentException`.
- `outDir` missing → created.
- No entities found → valid file containing only `erDiagram`.

### Required test cases

Renderer — `EntityMermaidRendererTest` (pure, no I/O):

| ID | Case | Expected |
|----|------|----------|
| R1 | Empty view | `"erDiagram\n"` |
| R2 | One entity `Owner` | `"erDiagram\n    \"Owner\"\n"` |
| R3 | Entities `Owner`, `Pet` | Both lines, in view order |

Extractor — `EntityExtractorTest` (parses sample sources):

| ID | Sample | Expected |
|----|--------|----------|
| E1 | `samples/entity` (existing `Owner`, `jakarta.persistence.Entity`) | `[Owner]` |
| E2 | `samples/entitymixed`: `@Entity Owner` + plain `OwnerService` | `[Owner]` — non-entity ignored |
| E3 | `samples/entityjavax`: `@javax.persistence.Entity` import | entity detected |
| E4 | `samples/entityfqn`: `@jakarta.persistence.Entity` written fully qualified, no import | entity detected |
| E5 | `samples/entitynone`: only plain classes | empty view |

End-to-end — `GenerateEntityDiagramSampleTest`:

| ID | Case | Expected |
|----|------|----------|
| G1 | `samples/entity` → `generate(...)` | `entity-diagram.mmd` exists, content `"erDiagram\n    \"Owner\"\n"` |
| G2 | `outDir` is an existing file | `IllegalArgumentException` |
| G3 | `null` `sourceRoots` or `outDir` | `NullPointerException` |
| G4 | Run twice on same input | Identical output (determinism) |

Regression: existing `GenerateClassDiagramEntitySampleTest` keeps passing unchanged.

### Done when

- All R/E/G tests pass, plus the full build (`mvn test` from the root: `assis-core` unchanged and green).
- `graphify update .` run after the code changes.
- Roadmap row for ER-ENTITY marked **Done**.

### ER-ENTITY — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GenerateEntityDiagram.generate(sourceRoots, outDir)` writes `entity-diagram.mmd` listing every `@Entity` class.

**Architecture:** Three pure layers in `io.github.masmangan.assis.er` (view record, extractor over `DeclaredIndex`, static renderer) plus one public entry point mirroring `GenerateClassDiagram`.

**Tech Stack:** Java 17, JavaParser via the `assis-core` dependency, JUnit 5, Maven.

**Spec:** ER-ENTITY sections above in this file.

#### Global Constraints

- Java release 17; the only dependency of `assis-er` is `assis-core` (JUnit inherited from the parent). No changes to `assis-core` files.
- Module `assis-er`, registered in the root `pom.xml` `<modules>`; all classes (including the entry point) in package `io.github.masmangan.assis.er`.
- Output file name exactly `entity-diagram.mmd`, UTF-8, `\n` line endings, 4-space indent.
- No copyright header on new files; few comments. No commits (the user commits); report changed files instead.
- Indentation: tabs, as in `assis-core`.
- Before any code change: run `graphify query "<question>"` for codebase questions (see `CLAUDE.md`); after code changes: `graphify update .`.

#### Review Focus

1. `@Entity` with arguments (`@Entity(name = "invoices")`) must still be detected → covered in E4 sample.
2. Several source roots, each with entities → all appear, deterministic order → G5 in Task 3.
3. A source root with no `.java` files at all → valid `erDiagram\n` file, no exception → covered by E5/G-level behavior; E5 sample contains only a plain class, so add G6 (empty dir) in Task 3.

---

#### Task 1: `assis-er` module + `EntityView` + `EntityMermaidRenderer`

**Files:**
- Create: `assis-er/pom.xml` — parent `assis`, artifact `assis-er`, dependency `assis-core` `${project.version}`, jacoco plugin (as `assis-pom`)
- Modify: `pom.xml` — add `<module>assis-er</module>`
- Create: `assis-er/src/main/java/io/github/masmangan/assis/er/EntityView.java`
- Create: `assis-er/src/main/java/io/github/masmangan/assis/er/EntityMermaidRenderer.java`
- Test: `assis-er/src/test/java/io/github/masmangan/assis/er/EntityMermaidRendererTest.java`

**Interfaces:**
- Produces: `public record EntityView(List<EntityView.Entity> entities)` with nested `public record Entity(String name)`; compact constructor copies with `List.copyOf`.
- Produces: `public final class EntityMermaidRenderer { public static String render(EntityView view) }` (private constructor).

- [ ] **Step 1: Write failing tests**
  - `rendersEmptyView` (R1): `assertEquals("erDiagram\n", render(new EntityView(List.of())))`
  - `rendersSingleEntity` (R2): `Owner` → `"erDiagram\n    \"Owner\"\n"`
  - `rendersEntitiesInViewOrder` (R3): `Owner`, `Pet` → `"erDiagram\n    \"Owner\"\n    \"Pet\"\n"`
- [ ] **Step 2: Run, expect compile failure** — `mvn -q -pl assis-er -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=EntityMermaidRendererTest`
- [ ] **Step 3: Implement** `EntityView` and `render` (StringBuilder: header line, then one indented line per entity).
- [ ] **Step 4: Run, expect PASS** — same command.
- [ ] **Step 5: Report changed files** (no commit)

#### Task 2: `EntityExtractor` + samples

**Files:**
- Create: `assis-er/src/main/java/io/github/masmangan/assis/er/EntityExtractor.java` (now `er/extract/EntityExtractor.java`, ER-REFACTOR)
- Create samples under `assis-er/src/test/resources/samples/`:
  - `entity/Owner.java` — copy of `assis-core`'s `samples/entity/Owner.java` (test resources are not shared across modules)
  - `entitymixed/Owner.java` — `package samples.entitymixed;` `@Entity` (jakarta import) `class Owner`
  - `entitymixed/OwnerService.java` — plain class
  - `entityjavax/Customer.java` — `import javax.persistence.Entity; @Entity class Customer`
  - `entityfqn/Invoice.java` — no import, `@jakarta.persistence.Entity(name = "invoices") class Invoice`
  - `entitynone/Helper.java` — plain class
  (Same flat layout as the existing `samples/entity/Owner.java`: file directly in the sample dir, `package samples.<dir>;`.)
- Test: `assis-er/src/test/java/io/github/masmangan/assis/er/EntityExtractorTest.java` (now under `er/extract/`)

**Interfaces:**
- Consumes: `EntityView` (Task 1); `DeclaredIndex.typesInIndexOrder()`; `SmartSourceRootManager.autoscan(DeterministicPathList)`; `DeterministicPathList.fromSourceRoots(Set<Path>)`.
- Produces: `public final class EntityExtractor { public static EntityView extract(DeclaredIndex index) }`; matching rule = any annotation whose `getName().getIdentifier()` equals `"Entity"`.

- [ ] **Step 1: Write failing tests.** Private helper `EntityView extractFrom(String resource)`: resolve the resource dir via `getClass().getClassLoader().getResource(resource).toURI()`, autoscan it, fill a `DeclaredIndex`, call `extract`. Assert on `view.entities().stream().map(Entity::name).toList()`:
  - `detectsJakartaEntity` (E1): `samples/entity` → `List.of("Owner")`
  - `ignoresNonEntityTypes` (E2): `samples/entitymixed` → `List.of("Owner")`
  - `detectsJavaxEntity` (E3): `samples/entityjavax` → `List.of("Customer")`
  - `detectsFullyQualifiedEntityWithArguments` (E4): `samples/entityfqn` → `List.of("Invoice")`
  - `returnsEmptyViewWhenNoEntities` (E5): `samples/entitynone` → `List.of()`
- [ ] **Step 2: Run, expect compile failure** — `mvn -q -pl assis-er -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=EntityExtractorTest`
- [ ] **Step 3: Implement `extract`** — iterate index order, filter by the matching rule, map to `Entity(td.getNameAsString())`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Report changed files** (no commit)

#### Task 3: `GenerateEntityDiagram` entry point

**Files:**
- Create: `assis-er/src/main/java/io/github/masmangan/assis/er/GenerateEntityDiagram.java`
- Test: `assis-er/src/test/java/io/github/masmangan/assis/er/GenerateEntityDiagramSampleTest.java`

**Interfaces:**
- Consumes: `EntityExtractor.extract`, `EntityMermaidRenderer.render`; test-only: sample roots resolved from the test classpath (no `TestWorkbench`, which is not visible across modules).
- Produces: `public class GenerateEntityDiagram { public static void generate(Set<Path> sourceRoots, Path outDir) throws IOException }` (private constructor). Same validation/logging as `GenerateClassDiagram.generate`; no `Dashboard` printing.

- [ ] **Step 1: Write failing tests** (use `@TempDir` for output; resolve sample roots from the classpath as in `EntityExtractorTest`):
  - `writesEntityDiagramForSample` (G1): `samples/entity` → `outDir/entity-diagram.mmd` equals `"erDiagram\n    \"Owner\"\n"`
  - `rejectsOutDirThatIsAFile` (G2): `outDir` = an existing regular file → `assertThrows(IllegalArgumentException.class, …)`
  - `rejectsNullArguments` (G3): `generate(null, dir)` and `generate(Set.of(root), null)` → `NullPointerException`
  - `isDeterministic` (G4): two runs into two dirs → equal contents
  - `includesEntitiesFromAllSourceRoots` (G5): roots `samples/entity` + `samples/entityjavax` → content contains `"    \"Owner\"\n"` and `"    \"Customer\"\n"`, and two runs with the `Set` built in different orders produce equal output
  - `writesHeaderOnlyForEmptySourceRoot` (G6): empty temp dir as root → `"erDiagram\n"`
- [ ] **Step 2: Run, expect compile failure** — `mvn -q -pl assis-er -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=GenerateEntityDiagramSampleTest`
- [ ] **Step 3: Implement `generate`** — validate, `createDirectories`, autoscan, fill index, extract, render, `Files.writeString(outDir.resolve("entity-diagram.mmd"), mermaid, UTF_8)`.
- [ ] **Step 4: Run, expect PASS.** If G6 fails because `autoscan` rejects an empty root, report it before changing behavior.
- [ ] **Step 5: Full regression** — `mvn -q test` from the root; expect all green, including `GenerateClassDiagramEntitySampleTest` in `assis-core`.
- [ ] **Step 6: Housekeeping** — `graphify update .`; set ER-ENTITY roadmap row to **Done**.
- [ ] **Step 7: Report changed files** (no commit)

---

## ER-ATTR — Attributes

### Scope

In:
- Each entity lists its persistent fields as `type name`, in source declaration order.
- `@Id` field → `PK` marker.
- Types rendered per D8 (generics with `~`) and D9 (no package qualifiers); arrays kept (`byte[]`).
- An entity with no persistent fields still renders as a bare `"Owner"` line.

Excluded fields:
- `static` fields.
- `transient` modifier or `@Transient` annotation.
- Fields annotated `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany` (they become relationships in ER-REL).

Out: `@Table`/`@Column` names (D7, Future), `@EmbeddedId`/`@Embedded` (deferred with embeddables), inherited fields (deferred with inheritance), `UK`/`FK` markers.

### Output

```
erDiagram
    "Owner" {
        Long id PK
        String name
        List~String~ tags
    }
    "Tag"
```

Attribute lines are indented 8 spaces; the block opens with `"Name" {` and closes with `    }`.

### Components (changes)

1. **`EntityView`**
   ```java
   public record Entity(String name, List<Attribute> attributes) {
       public Entity(String name) { this(name, List.of()); }   // keeps ER-ENTITY callers
   }
   public record Attribute(String type, String name, boolean primaryKey) {}
   ```
   `type` is the Java type as JavaParser prints it (`Map<String,List<Integer>>`, `java.time.LocalDate`).

2. **`AttributeExtractor`** (was `EntityExtractor`; exclusion rule = `Jpa.isColumnField`) — for each entity, walk its `FieldDeclaration`s in order; skip excluded fields;
   one `Attribute` per variable (`int a, b;` → two attributes); `primaryKey` = field has `@Id`.
   Annotations matched by simple name (D4).

3. **`EntityMermaidRenderer`** — renders blocks; converts the type with
   `static String mermaidType(String javaType)` (package-private):
   strip qualifiers (`([A-Za-z_$][\w$]*\.)+` → ``), `<`/`>` → `~`, remove spaces after commas.

### Required test cases

Renderer — `EntityMermaidRendererTest`:

| ID | Case | Expected |
|----|------|----------|
| R4 | `Owner` with `Long id` (PK), `String name` | `"erDiagram\n    \"Owner\" {\n        Long id PK\n        String name\n    }\n"` |
| R5 | `Owner` with attributes + `Tag` without | Owner block, then `    "Tag"\n` |
| R6 | `mermaidType("List<String>")` | `List~String~` |
| R7 | `mermaidType("Map<String, List<Integer>>")` | `Map~String,List~Integer~~` |
| R8 | `mermaidType("java.time.LocalDate")` | `LocalDate` |
| R9 | `mermaidType("List<java.time.LocalDate>")` | `List~LocalDate~` |
| R10 | `mermaidType("byte[]")` | `byte[]` |

R1–R3 keep passing (entities without attributes render as before).

Extractor — `EntityExtractorTest`, new sample `samples/attributes/Owner.java`:

```java
@Entity
public class Owner {
    @Id Long id;
    String name;
    int a, b;
    List<String> tags;
    Map<String, List<Integer>> scores;
    java.time.LocalDate birth;
    byte[] photo;
    static int counter;
    transient String cache;
    @Transient String temp;
    @ManyToOne Owner parent;
}
```

| ID | Case | Expected |
|----|------|----------|
| E6 | Attribute names in order | `[id, name, a, b, tags, scores, birth, photo]` |
| E7 | Only `id` is PK | `primaryKey` true for `id`, false for the rest |
| E8 | Raw types kept | `tags` → `List<String>`, `scores` → `Map<String,List<Integer>>` (JavaParser `asString()` canonical form), `birth` → `java.time.LocalDate`, `photo` → `byte[]` |
| E9 | ER-ENTITY samples | entities still found; `samples/entity` `Owner` has no attributes |

End-to-end — `GenerateEntityDiagramSampleTest`:

| ID | Case | Expected |
|----|------|----------|
| G7 | `samples/attributes` | file equals the full expected diagram for the sample (all 8 attributes, `Long id PK`, converted types) |

### Done when

- All R/E/G tests pass, plus the full build (`mvn test` from the root).
- `graphify update .` run.
- No commits (user commits).

---

## ER-REL — Relationships

### Scope

In:
- One relationship line per JPA association field of an entity.
- Fixed cardinality per annotation (decision D11).
- Bidirectional associations drawn once: the side whose annotation has `mappedBy` is skipped; the owning side draws it, labelled with its field name.
- Target = the field type; for generic types, the **last** type argument (`List<Pet>` → `Pet`, `Map<String, Pet>` → `Pet`), simple name.
- A line is drawn only if the target is an entity of the diagram (otherwise Mermaid would invent an empty box).

Out: optionality and constraints (see Known limitations), `@JoinTable`/`@JoinColumn` details, cascade, fetch, identifying vs non-identifying (always solid `--`).

### Known limitations

- **Relational constraints are ignored.** Non-null foreign keys (`optional = false`,
  `@JoinColumn(nullable = false)`), unique constraints and similar are not read; the
  cardinality comes only from the annotation kind (D11). E.g. a mandatory `@ManyToOne`
  still renders as `}o--||`, not as `}|--||`.

### Decisions

| # | Decision | Reason |
|---|----------|--------|
| D11 | Cardinality fixed per annotation: `@ManyToOne` → `}o--\|\|`, `@OneToMany` → `\|\|--o{`, `@OneToOne` → `\|\|--\|\|`, `@ManyToMany` → `}o--o{` | Simplest, predictable; optionality deferred. |
| D12 | Relationship lines come after all entity lines/blocks, ordered by entity (index order) then field declaration order | Deterministic output. |
| D13 | Label is the owning field name, quoted (`: "owner"`) | Java names (D7). |

Syntax verified against `erDiagram.jison` (mermaid@11.4.1): quoted names on both sides, all four cardinalities, self-reference, lines after attribute blocks.

### Output

```
erDiagram
    "Owner" {
        Long id PK
    }
    "Pet" {
        Long id PK
    }
    "Pet" }o--|| "Owner" : "owner"
```

### Components (changes)

1. **`EntityView`**
   ```java
   public record EntityView(List<Entity> entities, List<Relationship> relationships) {
       public EntityView(List<Entity> entities) { this(entities, List.of()); }   // keeps ER-ENTITY and ER-ATTR callers
   }
   // Cardinality lives in types/Cardinality.java (enum: MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE, MANY_TO_MANY)
   public record Relationship(String from, String to, Cardinality cardinality, String label) {}
   ```

2. **`RelationshipExtractor`** (was `EntityExtractor`; annotations in `Jpa.RELATIONSHIPS`, target via `TypeRefs.targetOf`) — for each entity, for each field with one of the four annotations:
   skip if the annotation has a `mappedBy` member; resolve the target (last type argument or the
   type itself, simple name); keep it only if the target is an extracted entity; one `Relationship`
   per variable. Two passes: entity names first, then relationships.

3. **`EntityMermaidRenderer`** — after the entities, one line per relationship:
   `    "<from>" <card> "<to>" : "<label>"`.

### Required test cases

Renderer — `EntityMermaidRendererTest`:

| ID | Case | Expected |
|----|------|----------|
| R11 | `Pet → Owner MANY_TO_ONE "owner"` | line `    "Pet" }o--\|\| "Owner" : "owner"` |
| R12 | `ONE_TO_MANY` | `\|\|--o{` |
| R13 | `ONE_TO_ONE` | `\|\|--\|\|` |
| R14 | `MANY_TO_MANY` | `}o--o{` |
| R15 | Entities with blocks + relationships | relationship lines come after all entities |

R1–R10 keep passing (no relationships → unchanged output).

Extractor — `EntityExtractorTest`, new sample `samples/relationships/` (one class per file, package `samples.relationships`):

```java
@Entity class Owner   { @Id Long id; @OneToMany(mappedBy = "owner") List<Pet> pets; }
@Entity class Pet     { @Id Long id; @ManyToOne Owner owner; @ManyToOne Pet mother; }
@Entity class Vet     { @Id Long id; @ManyToMany Set<Specialty> specialties; }
@Entity class Specialty { @Id Long id; }
@Entity class User    { @Id Long id; @OneToOne Profile profile; }
@Entity class Profile { @Id Long id; @OneToOne(mappedBy = "profile") User user; }
@Entity class Visit   { @Id Long id; @OneToMany List<Pet> pets; @ManyToOne Address address; }
class Address { }     // not an entity
```

| ID | Case | Expected |
|----|------|----------|
| E10 | Bidirectional `@OneToMany(mappedBy)`/`@ManyToOne` | only `Pet → Owner MANY_TO_ONE "owner"`; nothing from `Owner.pets` |
| E11 | Collection element as target | `Vet → Specialty MANY_TO_MANY "specialties"` |
| E12 | `@OneToOne` with `mappedBy` on the other side | only `User → Profile ONE_TO_ONE "profile"` |
| E13 | Unidirectional `@OneToMany` | `Visit → Pet ONE_TO_MANY "pets"` |
| E14 | Target is not an entity | no relationship for `Visit.address` |
| E15 | Self-reference | `Pet → Pet MANY_TO_ONE "mother"` |
| E16 | Full list, order (D12) | exactly `[Pet→Owner owner, Pet→Pet mother, User→Profile profile, Vet→Specialty specialties, Visit→Pet pets]` |

End-to-end — `GenerateEntityDiagramSampleTest`:

| ID | Case | Expected |
|----|------|----------|
| G7 (updated) | `samples/attributes` now also ends with `    "Owner" }o--\|\| "Owner" : "parent"` (its `@ManyToOne Owner parent` is a self-reference) | |
| G8 | `samples/relationships` | file equals the full expected diagram: 7 entity blocks (`Long id PK` each), then the 5 lines of E16 |

### Done when

- All R/E/G tests pass, plus the full build (`mvn test` from the root).
- `graphify update .` run.
- No commits (user commits).

---

## ER-CLI — CLI wiring and docs

### Scope

In:
- `assis-cli` depends on `assis-er`; the shaded `assis.jar` bundles it.
- Two new value-less flags in `CliArgs`, existing CLI style (not `assis-pom`'s `--key=value`):

  | Flags | Output in `-d` directory |
  |-------|--------------------------|
  | none | `class-diagram.puml` **and** `entity-diagram.mmd` |
  | `--class` | `class-diagram.puml` only |
  | `--er` | `entity-diagram.mmd` only |
  | `--class --er` | both |

- `--help` usage text documents both flags.
- User docs: `docs/user/assis-gs-bash.md` (run step, outputs, tip) and a short `assis-er` section in `README.md`.

Out: the naming parameter (see "Future: user-defined naming"), a standalone `assis-er` jar/`main`.

### Behaviour change

Running with no flags now also writes `entity-diagram.mmd` (previously only
`class-diagram.puml`). For code without `@Entity` classes the file contains just `erDiagram`.

### Decisions

| # | Decision | Reason |
|---|----------|--------|
| D14 | ER generation exposed through `assis-cli` flags, not a separate jar | One tool, one command (option A). |
| D15 | No flag = both diagrams; `--class` / `--er` select; both flags = both | User choice. |
| D16 | `-d`, `--source-path` and source auto-discovery apply to both diagrams unchanged | Same inputs, same output directory. |

### Components (changes)

1. **`assis-cli/pom.xml`** — add dependency `io.github.masmangan:assis-er:${project.version}`.
2. **`CliArgs`** — new fields `boolean classDiagram`, `boolean erDiagram`; `--class` / `--er` set them;
   if neither is given, both are `true`. Usage text gains:
   ```
     --class
           Generate only the class diagram (class-diagram.puml)
     --er
           Generate only the entity diagram (entity-diagram.mmd)
   ```
   and under Defaults: `Without --class or --er, both diagrams are generated.`
3. **`AssisApp.run`** — calls `GenerateClassDiagram.generate` if `classDiagram`,
   `GenerateEntityDiagram.generate` if `erDiagram`. Exit codes unchanged.

### Required test cases

`AssisAppTest` (writes `Hello.java` into `@TempDir`, like the existing tests; no new sample needed):

| ID | Args | Expected |
|----|------|----------|
| C1 | `-sourcepath <hello> -d <out>` | exit 0; `class-diagram.puml` and `entity-diagram.mmd` both exist |
| C2 | `... --er` | exit 0; `entity-diagram.mmd` exists and starts with `erDiagram`; no `class-diagram.puml` |
| C3 | `... --class` | exit 0; `class-diagram.puml` exists; no `entity-diagram.mmd` |
| C4 | `... --class --er` | exit 0; both exist |
| C5 | `CliArgs.usage` | contains `--class` and `--er` |

Existing `AssisAppTest` cases keep passing unchanged.

### Done when

- C1–C5 pass, plus the full build (`mvn test` from the root).
- Docs updated; `graphify update .` run.
- No commits (user commits).

Implementation note: the existing `generatesDiagramFromDefaults` and
`generatesDiagramFromHelloUsingSourcepathOnly` tests write into the real
`docs/diagrams/src/`; their cleanup now also deletes `entity-diagram.mmd`.

---

## ER-NULL — Optionality and nullability

### Scope

Today the cardinality only comes from the annotation kind (D11) and no constraint is
read (see ER-REL known limitations).

In:
- **Relationship ends.** Read optionality of the "one" side of `@ManyToOne` and owning
  `@OneToOne`:
  - required (`optional = false`, or `@JoinColumn(nullable = false)`) → `||`;
  - otherwise (JPA default is `optional = true`) → `o|` (zero or one).
- **Columns.** A nullable attribute gets `?` after its (converted) type: `String? name`,
  `List~String~? tags`. An attribute is nullable unless it is an `@Id`/`@EmbeddedId`
  field, has a Java primitive type (`int`, `long`, `boolean`, ...), or carries
  `@Column(nullable = false)` / `@Basic(optional = false)`. No comment is emitted; the
  line is `<type>[?] <name> [PK|FK]`.

Out:
- `@NotNull`/`@NonNull` (Bean Validation, Lombok): not JPA constraints; possible opt-in later.
- `@OneToMany` / `@ManyToMany` ends and `@JoinTable` constraints.
- Inferred relationships (inferred plan, ERI-REF): no annotation, so no constraint; they keep
  the assumed `}o..||`.
- Unique constraints (`UK`).

### Decisions

| # | Decision | Reason |
|---|----------|--------|
| D18 | Annotated `@ManyToOne` defaults to `}o--o\|` (optional), required becomes `}o--\|\|`; `@OneToOne` defaults to `\|\|--o\|`, required stays `\|\|--\|\|` | Follows the JPA default (`optional = true`). **This changes the default output of every annotated `@ManyToOne`/`@OneToOne`** (R11, R13 and related tests are updated). |
| D19 | When sources disagree (`optional = true` but `@JoinColumn(nullable = false)`), required wins | The column constraint is what the database enforces. |
| D20 | Nullability of a column is shown as `?` after the type (`String? name`), not as a comment; not-null columns print the type unchanged | User's choice: reads like Kotlin/C# nullable types and keeps the comment free. **Not valid Mermaid** (see Known limitations). This changes the output of every nullable attribute (ER-ATTR expectations updated). |
| D21 | `@Id`/`@EmbeddedId` fields and Java primitives never get `?` | An id is implied not null by `PK`; a primitive cannot hold null. |

Syntax verified against `erDiagram.jison` (mermaid@11.4.1): `o|` is accepted on the
right-hand side. `?` is **not** accepted in the attribute type token (11.4.1 and
12.3.0).

### Output

```
erDiagram
    "Pet" {
        Long id PK
        String name
        String? nickname
    }
    "Pet" }o--o| "Owner" : "owner"
    "Pet" }o--|| "Clinic" : "clinic"
```

### Components (changes)

- `EntityView`: `Relationship` gains `boolean required` (default constructor value
  `false`); `Attribute` gains `boolean nullable`. Secondary constructors keep earlier callers
  compiling and default `nullable` to `false`, so their output has no `?`.
- `RelationshipExtractor` reads the annotation members (`optional`) and an
  accompanying `@JoinColumn(nullable)`; `AttributeExtractor` reads `@Column(nullable)` and
  `@Basic(optional)`, and skips ids and primitive types.
- `EntityMermaidRenderer`: `symbol` picks `o|` or `||` for the target end of annotated
  `MANY_TO_ONE`/`ONE_TO_ONE` (inferred ones unchanged); attributes with `nullable` get
  `?` after the type.

### Required test cases

Renderer — `EntityMermaidRendererTest`:

| ID | Case | Expected |
|----|------|----------|
| N1 | `MANY_TO_ONE`, not required | `}o--o\|` |
| N2 | `MANY_TO_ONE`, required | `}o--\|\|` |
| N3 | `ONE_TO_ONE`, not required / required | `\|\|--o\|` / `\|\|--\|\|` |
| N4 | `ONE_TO_MANY`, `MANY_TO_MANY` | unchanged |
| N5 | Inferred `MANY_TO_ONE` | `}o..\|\|` unchanged |
| N6 | Attribute `nullable` / not nullable, with `PK`/`FK` keys, generic type, old constructor | `String? name`, `List~String~? tags`, `OwnerId? ownerId FK`, `String code`; old constructor has no `?` |

Extractor — `EntityExtractorTest`, new sample `samples/optionality/`:

| ID | Case | Expected |
|----|------|----------|
| O1 | `@ManyToOne` with no members | not required |
| O2 | `@ManyToOne(optional = false)` | required |
| O3 | `@ManyToOne @JoinColumn(nullable = false)` | required |
| O4 | `@ManyToOne(optional = true) @JoinColumn(nullable = false)` | required (D19) |
| O5 | `@OneToOne` optional / `@OneToOne(optional = false)` | not required / required |
| O6 | `@Column(nullable = false) String name` / plain `String nickname` | not `nullable` / `nullable` |
| O7 | `@Basic(optional = false) String code` | not `nullable` |
| O8 | `@Id` field (even with `@Column(nullable = false)`) | never `nullable` |
| O9 | Primitive fields (`int a, b`) | never `nullable` |

End-to-end — `GenerateEntityDiagramSampleTest`:

| ID | Case | Expected |
|----|------|----------|
| G9 | `samples/optionality` | file equals the full expected diagram |

### Done when

- N1–N6, O1–O9 and G9 pass, plus `mvn test` from the root; ER-REL expected strings updated per D18.
- `graphify update .` run. No commits.

### Known limitations

- `?` after the attribute type is **not accepted by Mermaid's ER grammar** (mermaid
  11.4.1 and 12.3.0): any diagram with a nullable column fails to render until Mermaid
  supports it. The user chose this knowingly (D20).

---

## ER-NAME — `@Table` and `@Column` names

### Scope

In:
- **Entity.** `@Table(name = "owners")` → the table name is displayed, the Java class
  name stays as the Mermaid entity identifier: `"Owner"["owners"]`. Relationship lines
  keep using `"Owner"`. A table name containing `"`, `%`, `\` or a line break (rejected
  in a quoted Mermaid name) gets no alias: the entity is printed as `"Owner"` with a
  `%% table: <tableName>` line directly above it (D26).
- **Attribute.** `@Column(name = "full_name") String fullName` → the attribute is
  shown with the **column name**, and the Java field name goes in a `%%` comment line
  directly above it (same indent): `%% fullName` then `String? full_name`.
- Names are used only when the annotation gives a `name` that differs from the Java
  name; otherwise output is unchanged.

Out: `schema`/`catalog` of `@Table`, `@Entity(name = ...)` (a JPQL name), `@JoinColumn`
names, `@Column` length/precision, naming strategies (Spring `SpringPhysicalNamingStrategy`
etc.), relationship labels (remain Java field names, D13).

### Decisions

| # | Decision | Reason |
|---|----------|--------|
| D22 | Replaces D7: names from `@Table`/`@Column` are shown when present | Diagram matches the database. Replaces the earlier "Future" idea of putting the column name in the comment. |
| D23 | The Mermaid entity identifier stays the Java class name; the table name is the alias (when valid, D26) | Relationships and tooling keep stable identifiers. |
| D24 | If the column name is not a valid Mermaid attribute identifier (spaces, quotes, `PK`/`FK`/`UK`), keep the Java field name as attribute and put `column: <columnName>` in the comment line | Mermaid would reject the line. |
| D25 | The original name goes in a whole-line `%% <text>` comment directly above the attribute, never in the quoted attribute comment; nullability is shown separately (D20). Newlines in the text become spaces; the space after `%%` keeps it from being read as a `%%{` directive | `%%` comments are whole-line only (a trailing `%%` breaks the line) and are stripped before parsing, so they never affect the attribute line. |
| D26 | No escaping. A table name is used as alias only when it has no `"`, `%`, `\` or line break (the `ENTITY_NAME` token); otherwise the entity has no alias and a `%% table: <tableName>` line (4-space indent) goes directly above it | Quoted entity names reject these characters, and entity codes such as `#quot;` are not decoded inside ER aliases (verified in Mermaid: they render as literal text). `%%` lines need no escaping. |

Syntax verified against `erDiagram.jison` (mermaid@11.4.1): entity alias
`"Owner"["owners"]` (supported from Mermaid 10.5) and the accepted attribute-name
characters `[*A-Za-z_][A-Za-z0-9\-_\[\]()]*` (D24).

### Output

```
erDiagram
    "Owner"["owners"] {
        Long id PK
        %% fullName
        String? full_name
        String email
    }
    %% table: pet%stats
    "Stats" {
        Long id PK
    }
    "Pet" }o--o| "Owner" : "owner"
```

### Components (changes)

- `EntityView`: `Entity` gains `String tableName` (nullable); `Attribute` gains
  `String columnName` (nullable). Secondary constructors keep earlier callers compiling;
  the view keeps the Java names in `name` (D10).
- `EntityExtractor` (facade, `Jpa.nameIfDifferent`) reads `@Table(name)` on the entity; `AttributeExtractor` reads `@Column(name)` on fields
  (annotations matched by simple name, D4).
- `EntityMermaidRenderer`: emit the alias after the identifier when the table name is
  valid, else a `%% table:` line above the entity (D26); the column name as the
  attribute name when valid (D24) and the `%%` comment line per D25.

### Required test cases

Renderer — `EntityMermaidRendererTest`:

| ID | Case | Expected |
|----|------|----------|
| T1 | Entity with `tableName` | `"Owner"["owners"] {` |
| T2 | Entity without `tableName` | unchanged |
| T3 | Relationship involving an aliased entity | line still uses `"Owner"` |
| T4 | Attribute with `columnName` | `%% fullName` line, then `String full_name` |
| T5 | `columnName` plus `nullable` / `PK` | `%% fullName` + `String? full_name`; `%% id` + `Long owner_id PK` |
| T6 | Invalid column identifier | `%% column: full name` + `String? fullName` |
| T7 | Quote or newline in a column name; table name with `"`, `%` or `\` | comment line kept on one line, unescaped; no alias, `%% table: <tableName>` line above `"Owner"` |

Extractor — `EntityExtractorTest`, new sample `samples/naming/`:

| ID | Case | Expected |
|----|------|----------|
| M1 | `@Table(name = "owners")` | `tableName` = `owners` |
| M2 | `@Table` with only `schema`, or no `@Table` | `tableName` null |
| M3 | `@Column(name = "full_name")` | `columnName` = `full_name`, `name` still `fullName` |
| M4 | `@Column` without name, blank name, constant, or name equal to the field; blank `@Table` name | `columnName` / `tableName` null |

End-to-end — `GenerateEntityDiagramSampleTest`:

| ID | Case | Expected |
|----|------|----------|
| G10 | `samples/naming` | file equals the full expected diagram |

### Done when

- T1–T7, M1–M4 and G10 pass, plus `mvn test` from the root .
- `graphify update .` run. No commits.

### Known limitations

- `%%` comment lines are not rendered in the final diagram; the original Java name, an
  invalid column name and a table name that cannot be an alias are visible only in the
  `.mmd` source.

---

## 🟦 FIX-NOTNULL — Not-null annotations outside JPA

### Problem

ER-NULL marks a column nullable (`?`) unless `@Column(nullable = false)`,
`@Basic(optional = false)`, `@Id` or a primitive type says otherwise. A field with
`@NotNull String name` (Bean Validation) still prints `String? name`, which claims
something the code contradicts.

### Fix

- Also treat these as not null (matched by simple name, D4): `NotNull`, `NotBlank`,
  `NotEmpty` (`jakarta`/`javax.validation`), `NonNull` (Lombok), `Nonnull` (JSR-305).
- Modules: `Jpa` (the not-null names and an `isNotNull(fd)` reader) and one condition in
  `AttributeExtractor`'s `nullable`.
- Relationship ends are unchanged: a `@NotNull @ManyToOne` field stays optional unless
  `optional = false` or `@JoinColumn(nullable = false)` (D18). Open: whether `@NotNull`
  should also make a `@ManyToOne` end required.

### Required test cases

| ID | Case | Expected |
|----|------|----------|
| NN1 | `@NotNull String name` | `String name` (no `?`) |
| NN2 | `@NotBlank`, `@NotEmpty`, Lombok `@NonNull`, `@Nonnull` | no `?` |
| NN3 | `@jakarta.validation.constraints.NotNull` fully qualified | no `?` |
| NN4 | Plain `String name` | `String? name` (unchanged) |

### Done when

- NN1–NN4 pass, plus `mvn test` from the root; ER-NULL known limitation updated.
- `graphify update .` run. No commits.

---

## 🟦 FIX-SIMPLENAME — Same simple name in different packages

### Problem

Entities and relationship targets are keyed by simple name. Two `@Entity class Order`
in different packages are merged into one entity, and a field `Order order` can be
drawn to the wrong one. This affects ER-REL relationships and ER-ATTR attribute blocks.

### Fix

- Key entities internally by fully qualified name (package + nested type path).
- Resolve a field's target type with the compilation unit's information, in order:
  fully qualified in the source, single-type import, same package, nested type of the
  declaring class. If it resolves to exactly one entity, draw the line.
- If it cannot be resolved for sure (wildcard imports matching several candidates,
  unknown types), **draw nothing**: a missing line is acceptable, a wrong one is not.
- Modules: `ExtractionContext` (replace `entityNames` with a resolver keyed by qualified
  name), `RelationshipExtractor` and `TypeRefs` (resolve the target through it). The
  renderer handles identifiers; `IdResolver` keeps simple names (inferred limitation).
- Rendering: entity identifiers stay the simple name when it is unique; on a collision
  every colliding entity uses its qualified name (`"billing.Order"`, valid inside a
  quoted Mermaid name). Deterministic for the same input.

### Required test cases

| ID | Case | Expected |
|----|------|----------|
| SN1 | Two `@Entity Order` in packages `billing` and `shop` | two entities, `"billing.Order"` and `"shop.Order"` |
| SN2 | `Invoice` in `billing` with `@ManyToOne Order order` (same package) | line to `"billing.Order"` only |
| SN3 | Field typed `shop.Order` (fully qualified) or with `import shop.Order` | line to `"shop.Order"` |
| SN4 | Ambiguous: `import billing.*; import shop.*;` with `Order order` | no line |
| SN5 | Unique simple names | output unchanged (`"Order"`) |

### Done when

- SN1–SN5 pass, plus `mvn test` from the root; existing samples keep their output.
- `graphify update .` run. No commits.

