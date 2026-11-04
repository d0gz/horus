# Entity Diagram (Mermaid `erDiagram`) — Plan & Specs

Living document. Tracks the roadmap, the spec of each part, and its required tests.
Each part goes through: spec → approval → implementation plan → implementation → done.

## Goal

Add a new Maven module `assis-er` that reads JPA entity annotations from Java sources and emit a
Mermaid `erDiagram`, following the layered pattern used by `assis-pom`:

```
Extractor  ──►  View (plain records)  ──►  MermaidRenderer (pure, static)  ──►  .mmd file
```

| assis-pom (reference)      | assis-er (new)            |
|----------------------------|---------------------------|
| `PomDependencyExtractor`   | `EntityExtractor`         |
| `PomView`                  | `EntityView`              |
| `PomMermaidRenderer`       | `EntityMermaidRenderer`   |
| `dependencies.mmd`         | `entity-diagram.mmd`      |

## Decisions

| # | Decision | Reason |
|---|----------|--------|
| D1 | Mermaid `erDiagram` (not `classDiagram`) | Native crow's-foot cardinality and PK/FK markers map directly to JPA. |
| D2 | Own module `assis-er` (package `io.github.masmangan.assis.er`), depending on `assis-core`; `assis-core` itself is not modified | Keeps the ER feature isolated like `assis-pom`, while reusing the public parse pipeline (`SmartSourceRootManager`, `DeterministicPathList`, `DeclaredIndex`). |
| D3 | Separate output `entity-diagram.mmd`; `class-diagram.puml` untouched | No regression risk on the existing generator. |
| D4 | Annotations matched by simple name (`Entity`) | Same rule as `DeclaredIndex.stereotypesOf`; covers `jakarta.persistence` and `javax.persistence`, imported or fully qualified. |
| D5 | Start as simple as possible; every part ships with required tests | Incremental, verifiable steps. |
| D6 | Entity names are always double-quoted (`"Owner"`) | Valid Mermaid for any name. |
| D7 | Java names only (class and field names); `@Table`/`@Column` names ignored for now | Simplest. User-selectable naming comes later (see Future). |
| D8 | Generic types render with `~` instead of `<`/`>` (`Map<String, List<Integer>>` → `Map~String,List~Integer~~`) | Mermaid 11 ER lexer accepts `~…~` in attribute types. Verified against `erDiagram.jison` (mermaid@11.4.1), including nested generics, wildcards and arrays. |
| D9 | Package qualifiers are stripped from types (`java.time.LocalDate` → `LocalDate`) | A dot outside `~…~` is a Mermaid parse error (verified). |
| D10 | `EntityView` keeps the Java type exactly as written; Mermaid conversion (D8, D9) happens only in the renderer | The view stays free of Mermaid concerns, as in `assis-pom`. |

## Roadmap

| Part | Scope | Status |
|------|-------|--------|
| 1 | Entity detection: `@Entity` classes → one entity line each | **Done** |
| 2 | Attributes: fields, Java types (generics with `~`), `@Id` → `PK`, transient fields excluded | **Done** |
| 3 | Relationships: `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`, fixed cardinality per annotation, `mappedBy` dedupe | **Done** |
| 4 | Wiring: `--er` / `--class` CLI flags, user docs | **Done** |
| 5 | Inferred relationships from ID-typed fields (dotted `..` lines, `FK` markers) | **Done** |

Out of scope for now (deferred, no part assigned): inheritance and value types —
`@MappedSuperclass`, `@Embeddable`/`@Embedded`, `@Inheritance`.

### Future: user-defined naming

The naming used in the diagram should become a user parameter (e.g. a generator
option or a CLI flag in Part 4), so the user can choose between:

- **Java names** (current default): `"Owner" { String fullName }`.
- **Entity alias from `@Table`**: `"Owner"["owners"]`.
- **Column names from `@Column`**: shown as the attribute comment
  (`String fullName "full_name"`) or as an extra column.

Until then, `@Table` and `@Column` are ignored.

---

## Part 1 — Entity detection

### Scope

In:
- Find every type in the source roots annotated with `@Entity`.
- Emit a Mermaid `erDiagram` with one line per entity, using the class simple name.
- Public entry point that writes `entity-diagram.mmd` into an output directory.

Out (later parts or deferred): attributes, `@Table` names, relationships, CLI
flag, simple-name collisions across packages, nested entity naming. Inheritance
and embeddables are deferred entirely.

### Components

Package: `io.github.masmangan.assis.er`

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
- Roadmap row for Part 1 marked **Done**.

### Part 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GenerateEntityDiagram.generate(sourceRoots, outDir)` writes `entity-diagram.mmd` listing every `@Entity` class.

**Architecture:** Three pure layers in `io.github.masmangan.assis.er` (view record, extractor over `DeclaredIndex`, static renderer) plus one public entry point mirroring `GenerateClassDiagram`.

**Tech Stack:** Java 17, JavaParser via the `assis-core` dependency, JUnit 5, Maven.

**Spec:** Part 1 sections above in this file.

#### Global Constraints

- Java release 17; the only dependency of `assis-er` is `assis-core` (JUnit inherited from the parent). No changes to `assis-core` files.
- Module `assis-er`, registered in the root `pom.xml` `<modules>`; all classes (including the entry point) in package `io.github.masmangan.assis.er`.
- Output file name exactly `entity-diagram.mmd`, UTF-8, `\n` line endings, 4-space indent.
- Every new `.java` file starts with the repo copyright header (copy from `GenerateClassDiagram.java`).
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
- [ ] **Step 5: Commit** — `feat(entity): add EntityView and Mermaid erDiagram renderer`

#### Task 2: `EntityExtractor` + samples

**Files:**
- Create: `assis-er/src/main/java/io/github/masmangan/assis/er/EntityExtractor.java`
- Create samples under `assis-er/src/test/resources/samples/`:
  - `entity/Owner.java` — copy of `assis-core`'s `samples/entity/Owner.java` (test resources are not shared across modules)
  - `entitymixed/Owner.java` — `package samples.entitymixed;` `@Entity` (jakarta import) `class Owner`
  - `entitymixed/OwnerService.java` — plain class
  - `entityjavax/Customer.java` — `import javax.persistence.Entity; @Entity class Customer`
  - `entityfqn/Invoice.java` — no import, `@jakarta.persistence.Entity(name = "invoices") class Invoice`
  - `entitynone/Helper.java` — plain class
  (Same flat layout as the existing `samples/entity/Owner.java`: file directly in the sample dir, `package samples.<dir>;`.)
- Test: `assis-er/src/test/java/io/github/masmangan/assis/er/EntityExtractorTest.java`

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
- [ ] **Step 5: Commit** — `feat(entity): extract @Entity types from DeclaredIndex`

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
- [ ] **Step 6: Housekeeping** — `graphify update .`; set Part 1 roadmap row to **Done**.
- [ ] **Step 7: Commit** — `feat(entity): add GenerateEntityDiagram entry point`

---

## Part 2 — Attributes

### Scope

In:
- Each entity lists its persistent fields as `type name`, in source declaration order.
- `@Id` field → `PK` marker.
- Types rendered per D8 (generics with `~`) and D9 (no package qualifiers); arrays kept (`byte[]`).
- An entity with no persistent fields still renders as a bare `"Owner"` line.

Excluded fields:
- `static` fields.
- `transient` modifier or `@Transient` annotation.
- Fields annotated `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany` (they become relationships in Part 3).

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
       public Entity(String name) { this(name, List.of()); }   // keeps Part 1 callers
   }
   public record Attribute(String type, String name, boolean primaryKey) {}
   ```
   `type` is the Java type as JavaParser prints it (`Map<String,List<Integer>>`, `java.time.LocalDate`).

2. **`EntityExtractor`** — for each entity, walk its `FieldDeclaration`s in order; skip excluded fields;
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
| E9 | Part 1 samples | entities still found; `samples/entity` `Owner` has no attributes |

End-to-end — `GenerateEntityDiagramSampleTest`:

| ID | Case | Expected |
|----|------|----------|
| G7 | `samples/attributes` | file equals the full expected diagram for the sample (all 8 attributes, `Long id PK`, converted types) |

### Done when

- All R/E/G tests pass, plus the full build (`mvn test` from the root).
- `graphify update .` run.
- No commits (user commits).

---

## Part 3 — Relationships

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
| D17 | Inferred cardinality: known collection types give one-to-many; everything else (plain, `Optional`, unknown generics) gives many-to-one | Unknown generics cannot be resolved from source; the dotted line already signals the assumption. |
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
       public EntityView(List<Entity> entities) { this(entities, List.of()); }   // keeps Part 1–2 callers
   }
   public enum Cardinality { MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE, MANY_TO_MANY }
   public record Relationship(String from, String to, Cardinality cardinality, String label) {}
   ```

2. **`EntityExtractor`** — for each entity, for each field with one of the four annotations:
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

## Part 4 — CLI wiring and docs

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

## Part 5 — Inferred relationships (reference by ID)

### Scope

Entities that reference others only by an ID value (DDD / jpearl style) have no
`@OneToMany`/`@ManyToOne`, so Part 3 emits nothing. Example (petclinic):

```java
@Entity
public class Visit extends AbstractEntity<VisitId> {
  private OwnerId ownerId;
  private PetId petId;
}
public class Pet extends AbstractEntity<PetId> { ... }
```

Infer these links from ID types, and render them distinctly from JPA mappings.

### Approach

1. **ID map (first pass, after all entities are collected):** build
   `idType → entity` from, in priority order:
   - the type of the `@Id` / `@EmbeddedId` field, when it is a project class
     that is not an entity;
   - otherwise (no such field, **or its type is a JDK type such as `Long`**) the
     single generic argument of a known ID-style superclass
     (`AbstractEntity<PetId>` → `PetId` ↦ `Pet`). The known bases are a fixed
     list: `AbstractEntity`, `AbstractVersionedEntity`. The argument must be a
     project class that is not itself an entity.
2. **Inference (second pass):** for each field with no relationship annotation,
   if its type (or a collection's element type) is in the map and is not the
   entity's own ID type, emit an inferred relationship to the mapped entity.
3. **Render** inferred relationships with Mermaid's non-identifying (dotted)
   line `..`, labelled with the field name.
4. **Mark foreign keys:** a single-valued field that produced an inferred
   relationship gets the `FK` marker (`OwnerId ownerId FK`). Collection fields
   (`Set<PetId>`) are not marked (no column on the owner's table); neither are the
   entity's own ID type nor `@Id` fields.

### Cardinality

Without annotations cardinality cannot be known; use a fixed assumption:

| Field shape | Assumed | Could also be |
|-------------|---------|---------------|
| `PetId petId` | many-to-one `}o..\|\|` | one-to-one |
| `Set<PetId> petIds`, `Map<String, PetId>` (known collection types) | one-to-many `\|\|..o{` from owner side | many-to-many |
| `Optional<PetId>`, unknown generic `Wrapper<PetId>` | many-to-one `}o..\|\|` | one-to-one |

Known collection types (matched by simple name): `Collection`, `List`, `Set`,
`SortedSet`, `NavigableSet`, `Queue`, `Deque`, `Iterable`, `Map`, `SortedMap`,
`NavigableMap`; the list lives in `er.types.CollectionTypes`. The target is the last type argument. Any other type, generic or
not, is treated as single-valued, because a custom generic cannot be resolved from
source alone (decision D17).

The dotted line is what signals "inferred, cardinality assumed".

### False positives and mitigations

| Risk | Example | Mitigation |
|------|---------|------------|
| Superclass generic is not the ID | `AbstractAggregateRoot<Order>` (self type), `Auditable<User, Long>` | Prefer `@Id` field type; only read generics of known ID-style bases; argument must be a non-entity project class |
| Shared / raw ID type | several entities with `Long`, `UUID` or a shared `EntityId` | Skip JDK types; if one ID type maps to more than one entity, infer nothing for it |
| ID stored for non-link reasons | `createdBy UserId`, `previousPetId` | Accepted: still a real reference; dotted line marks it as inferred |
| IDs inside embeddables / DTOs | `@Embeddable Address { OwnerId ownerId; }` | Only `@Entity` fields are scanned |

Remaining uncertainty is cardinality (one-to-one vs many-to-one), signalled by the dotted line.

### Known limitations

- Raw-typed IDs (`UUID ownerId`, `Long ownerId`) are not matched. A name-based
  fallback (`<entity>Id` → entity) is a possible future opt-in flag, not in scope.
- IDs whose owning entity is outside the scanned sources are ignored.
- ID types are matched by simple name; same-named ID classes in different packages are not distinguished.
- A custom generic wrapper around an ID is assumed single-valued, even if it is really a collection.
- `PK` is not shown for IDs inherited from external bases (e.g. jpearl `AbstractEntity`), and `FK` is not shown for annotated associations (`@ManyToOne`, `@OneToOne`); both deferred.
- Inferred relationships are always on; there is no flag to disable them yet.

### Components (changes)

- `EntityView`: relationship gains `inferred` flag; attribute gains `foreignKey` flag
  (secondary constructors keep earlier callers compiling).
- `EntityExtractor`: collect ID type per entity; add inference pass.
- `EntityMermaidRenderer`: `..` instead of `--` when `inferred`; ` FK` after the name when `foreignKey`.
- Ordering: inferred relationships are emitted per entity right after that entity's
  annotated ones (refines D12: entity order, then annotated, then inferred).
- Samples: `samples/inferred/` with `AbstractEntity<XId>`-style entities.

### Required test cases

| ID | Input | Expected |
|----|-------|----------|
| I1 | `Visit` with `PetId petId`, `Pet extends AbstractEntity<PetId>` | `"Visit" }o..\|\| "Pet" : "petId"` |
| I2 | Entity with `@Id OwnerId id` and another entity with `OwnerId ownerId` | inferred relationship to owner |
| I3 | Entity's own ID field | no self relationship |
| I4 | `Set<PetId> petIds` | collection relationship, dotted |
| I5 | ID type with no owning entity in sources | no relationship |
| I6 | Field already annotated `@ManyToOne` | only the Part 3 relationship, no duplicate |
| I7 | Entity `extends AbstractAggregateRoot<Order>`, another entity with `Order`-typed non-annotated field | no inferred relationship from the generic |
| I8 | Two entities whose `@Id` type is the same `EntityId` class; third entity has `EntityId ref` | no inferred relationship (ambiguous) |
| I9 | Entities with `@Id Long id`; another entity has `Long ownerId` | no inferred relationship (JDK type) |
| I10 | Entity with `@Id PetId id` and superclass `Base<Other>` | `PetId` ↦ entity (`@Id` wins over generic) |
| I13 | `Optional<PetId>` and unknown `Wrapper<PetId>` | many-to-one |
| I14 | `Map<String, PetId>` | one-to-many |
| I12 | `PetId petId` / `Set<PetId> petIds` fields | `petId` gets `FK`; `petIds` does not; own ID type and `@Id` fields get no `FK` |
| I11 | Entity with `@Id Long number` and `extends AbstractEntity<ClinicId>` | `ClinicId` ↦ entity (JDK-typed `@Id` falls back to the generic) |

Existing Part 3 tests keep passing unchanged.

### Done when

- I1–I14 pass (including I12), plus `mvn test` from the root.
- Running against `../petclinic` shows `Visit` linked to `Owner`, `Pet`, `Veterinarian`.
- `graphify update .` run. No commits.
