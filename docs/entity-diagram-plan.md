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

## Roadmap

| Part | Scope | Status |
|------|-------|--------|
| 1 | Entity detection: `@Entity` classes → one entity line each | **Spec approved — planning** |
| 2 | Attributes: fields, Java types, `@Id` → `PK`, `@Column`, `@Transient` excluded, `@Table(name)` | Not started |
| 3 | Relationships: `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`, cardinality, `mappedBy` dedupe | Not started |
| 4 | Wiring: CLI flag, user docs | Not started |

Out of scope for now (deferred, no part assigned): inheritance and value types —
`@MappedSuperclass`, `@Embeddable`/`@Embedded`, `@Inheritance`.

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
       Owner
       Pet
   ```
   Empty view → `erDiagram\n`. Indent = 4 spaces, `\n` line endings.

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
| R2 | One entity `Owner` | `"erDiagram\n    Owner\n"` |
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
| G1 | `samples/entity` → `generate(...)` | `entity-diagram.mmd` exists, content `"erDiagram\n    Owner\n"` |
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
  - `rendersSingleEntity` (R2): `Owner` → `"erDiagram\n    Owner\n"`
  - `rendersEntitiesInViewOrder` (R3): `Owner`, `Pet` → `"erDiagram\n    Owner\n    Pet\n"`
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
  - `writesEntityDiagramForSample` (G1): `samples/entity` → `outDir/entity-diagram.mmd` equals `"erDiagram\n    Owner\n"`
  - `rejectsOutDirThatIsAFile` (G2): `outDir` = an existing regular file → `assertThrows(IllegalArgumentException.class, …)`
  - `rejectsNullArguments` (G3): `generate(null, dir)` and `generate(Set.of(root), null)` → `NullPointerException`
  - `isDeterministic` (G4): two runs into two dirs → equal contents
  - `includesEntitiesFromAllSourceRoots` (G5): roots `samples/entity` + `samples/entityjavax` → content contains `"    Owner\n"` and `"    Customer\n"`, and two runs with the `Set` built in different orders produce equal output
  - `writesHeaderOnlyForEmptySourceRoot` (G6): empty temp dir as root → `"erDiagram\n"`
- [ ] **Step 2: Run, expect compile failure** — `mvn -q -pl assis-er -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=GenerateEntityDiagramSampleTest`
- [ ] **Step 3: Implement `generate`** — validate, `createDirectories`, autoscan, fill index, extract, render, `Files.writeString(outDir.resolve("entity-diagram.mmd"), mermaid, UTF_8)`.
- [ ] **Step 4: Run, expect PASS.** If G6 fails because `autoscan` rejects an empty root, report it before changing behavior.
- [ ] **Step 5: Full regression** — `mvn -q test` from the root; expect all green, including `GenerateClassDiagramEntitySampleTest` in `assis-core`.
- [ ] **Step 6: Housekeeping** — `graphify update .`; set Part 1 roadmap row to **Done**.
- [ ] **Step 7: Commit** — `feat(entity): add GenerateEntityDiagram entry point`
