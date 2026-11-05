# Entity Diagram — Inferred Plan & Specs

Companion to [`entity-diagram-plan.md`](entity-diagram-plan.md), which holds the default
output (only what the code states). This file holds the **heuristics**: links and keys
the code does not state, deduced from ID types and known base classes.

Principle: the default diagram must never show something wrong; a missing line is
acceptable. Everything here can be wrong in some code structures (see "Known false
results" below), so it is **opt-in** behind a CLI flag (ERI-FLAG). When enabled,
inferred relationships are drawn with dotted `..` lines to mark them as guesses.

Decision numbers (`D…`) are shared with the default plan and stay unique across both files.

## Roadmap

| ID | Scope | Status |
|----|-------|--------|
| ERI-REF | Inferred relationships from ID-typed fields (dotted `..` lines, `FK` markers) | ⚠️ Done, always on until ERI-FLAG |
| ERI-ID | Synthetic `XId id PK` from known ID-style bases (`AbstractEntity<XId>`) | 🛑 Blocked by ER-INHERIT |
| ERI-FLAG | Opt-in flag `--inferred` (default off) for ERI-REF and ERI-ID | ❌ Planned |

Fixes (cheap, only reduce wrong output; not urgent):

| ID | Scope | Status |
|----|-------|--------|
| 🟦 FIX-WRAPPER | Non-reference generics (`Supplier<PetId>`, `Class<PetId>`, ...) are not inferred | ❌ Planned |
| 🟦 FIX-DUPLINK | No dotted line when a solid JPA line already links the same pair from the same entity | ❌ Planned |
| 🟦 FIX-SELFREF | Self-references by own ID type (`parentId`) are drawn; only the `@Id` field is excluded | ❌ Planned |

Status: ✅ done · ❌ not done · ⚠️ done with a warning or problem · 🛑 blocked · 🟦 fix task

---

## ERI-REF — Inferred relationships (reference by ID)

### Scope

Entities that reference others only by an ID value (DDD / jpearl style) have no
`@OneToMany`/`@ManyToOne`, so ER-REL emits nothing. Example (petclinic):

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

### Decisions

| # | Decision | Reason |
|---|----------|--------|
| D17 | Inferred cardinality: known collection types give one-to-many; everything else (plain, `Optional`, unknown generics) gives many-to-one | Unknown generics cannot be resolved from source; the dotted line already signals the assumption. |

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
- `PK` for IDs inherited from bases such as jpearl `AbstractEntity` is ERI-ID; `FK` is not
  shown for annotated associations (`@ManyToOne`, `@OneToOne`), deferred.
- Currently always on; ERI-FLAG makes it opt-in (default off).

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
| I6 | Field already annotated `@ManyToOne` | only the ER-REL relationship, no duplicate |
| I7 | Entity `extends AbstractAggregateRoot<Order>`, another entity with `Order`-typed non-annotated field | no inferred relationship from the generic |
| I8 | Two entities whose `@Id` type is the same `EntityId` class; third entity has `EntityId ref` | no inferred relationship (ambiguous) |
| I9 | Entities with `@Id Long id`; another entity has `Long ownerId` | no inferred relationship (JDK type) |
| I10 | Entity with `@Id PetId id` and superclass `Base<Other>` | `PetId` ↦ entity (`@Id` wins over generic) |
| I13 | `Optional<PetId>` and unknown `Wrapper<PetId>` | many-to-one |
| I14 | `Map<String, PetId>` | one-to-many |
| I12 | `PetId petId` / `Set<PetId> petIds` fields | `petId` gets `FK`; `petIds` does not; own ID type and `@Id` fields get no `FK` |
| I11 | Entity with `@Id Long number` and `extends AbstractEntity<ClinicId>` | `ClinicId` ↦ entity (JDK-typed `@Id` falls back to the generic) |

Existing ER-REL tests keep passing unchanged.

### Done when

- I1–I14 pass (including I12), plus `mvn test` from the root.
- Running against `../petclinic` shows `Visit` linked to `Owner`, `Pet`, `Veterinarian`
  (with the ERI-FLAG flag, once it exists).
- `graphify update .` run. No commits.

---

## ERI-ID — Synthetic ID from ID-style bases

### Scope

Split from ER-INHERIT (inherited attributes) in the default plan. Entities such as petclinic's
`Visit extends AbstractEntity<VisitId>` declare their `@Id` in a base class that is often
external (jpearl), so no `PK` is shown. The ID type is known only from the generic
argument of a base class recognised **by name**, which is an inference.

In:
- **ID-style bases** (the fixed list from ERI-REF: `AbstractEntity`,
  `AbstractVersionedEntity`) with a single type argument `XId`: add a synthetic
  attribute `XId id PK` as the first attribute, whether the base is in the sources or
  external.
- With ER-INHERIT, inherited ID-reference fields from in-source bases feed ERI-REF (inferred
  relationship and `FK` on the subclass).

Out: custom base classes not in the list (`BaseEntity<T>`); a name-based fallback.

### Decisions

| # | Decision | Reason |
|---|----------|--------|
| D27 | Synthetic `XId id PK` only when no `@Id`/`@EmbeddedId` field exists in the chain | `@Id` wins over the generic (same priority as ERI-REF); avoids two `PK`s. |
| D28 | The synthetic attribute is always named `id` | The field name inside the external base is unknown. |
| D33 | An in-source base field named `id` typed by the base's type parameter is replaced by the synthetic PK, not duplicated | One `id` attribute. |

### Output

Petclinic `Visit` (`extends AbstractEntity<VisitId>`, base external), with the flag:

```
"Visit" {
    VisitId id PK
    VeterinarianId? veterinarianId FK
    OwnerId? ownerId FK
    PetId? petId FK
    Instant? appointmentTime
}
```

### Required test cases

| ID | Case | Expected |
|----|------|----------|
| H1 | Entity `extends AbstractEntity<PetId>`, base not in sources | first attribute `PetId id`, `primaryKey` true |
| H2b | Base in sources with `T id` and `Instant createdAt`, entity `extends AbstractEntity<PetId>` | `[PetId id PK, createdAt, own fields...]`; no duplicate `id` |
| H8 | Inherited ID-reference field (`OwnerId ownerId`) in an in-source base | inferred relationship and `FK` on the subclass |
| H9 | `@Id Long number` plus `extends AbstractEntity<ClinicId>` | only `Long number PK`, no synthetic PK |

### Done when

- H1, H2b, H8, H9 pass, plus `mvn test` from the root. Requires default-plan ER-INHERIT.
- Running against `../petclinic` with the flag shows `XId id PK` on every table.
- `graphify update .` run. No commits.

---

## ERI-FLAG — Opt-in flag for inferred output

### Scope

In:
- New value-less CLI flag `--inferred` (name to confirm), same style as `--er`/`--class`.
- **Default (no flag):** no inferred relationships, no `FK` from inferred references, no
  synthetic PK (ERI-ID). Only JPA-declared output from the default plan.
- **With `--inferred`:** ERI-REF and ERI-ID as specified.
- `--help` usage text documents the flag; user docs mention that inferred lines are guesses.

### Behaviour change

ERI-REF is currently always on. After ERI-FLAG, a run without the flag no longer shows the
dotted lines or their `FK` markers (petclinic's `Visit` loses its three links by default).

### Components (changes)

- `EntityExtractor.extract(DeclaredIndex index, boolean inferred)`; the existing
  `extract(index)` keeps the default (off). Inference passes and FK marking run only when
  `inferred` is true.
- `GenerateEntityDiagram.generate(sourceRoots, outDir, boolean inferred)` overload.
- `CliArgs`: `boolean inferred`, set by `--inferred`; `AssisApp.run` passes it through.
- Existing ERI-REF tests call the extractor with `inferred = true`.

### Required test cases

| ID | Case | Expected |
|----|------|----------|
| F1 | `samples/inferred` without the flag | no dotted lines, no `FK` markers |
| F2 | `samples/inferred` with the flag | same output as today |
| F3 | Annotated relationships without the flag | unchanged (solid lines) |
| F4 | CLI `--er --inferred` | `entity-diagram.mmd` contains dotted lines |
| F5 | `CliArgs.usage` | contains `--inferred` |

### Done when

- F1–F5 pass, plus `mvn test` from the root; all ERI-REF tests run with the flag.
- `graphify update .` run. No commits.

---

## 🟦 FIX-WRAPPER — Non-reference generic wrappers

Known false result 2. `targetOf` takes the last type argument of any generic, so
`Supplier<PetId>`, `Event<PetId>`, `Class<PetId>`, `Comparator<PetId>` resolve to `Pet`.

Fix: unwrap only known reference containers: the collection types in
`types.CollectionTypes` and `Optional`. Any other generic yields no inferred link
(missing rather than wrong). This replaces the "unknown generic is many-to-one" row of
D17, so `Wrapper<PetId>` (I13) now gives no link.

| ID | Case | Expected |
|----|------|----------|
| FW1 | `Supplier<PetId>`, `Class<PetId>`, `Event<PetId>` | no inferred link, no `FK` |
| FW2 | `Optional<PetId>`, `Set<PetId>`, `Map<String, PetId>` | unchanged |
| FW3 | I13 `Wrapper<PetId>` | updated: no link |

---

## 🟦 FIX-DUPLINK — Duplicate solid and dotted line

Known false result 4. `@ManyToOne Owner owner` plus a read-only
`@Column(insertable = false, updatable = false) OwnerId ownerId` draws a solid and a
dotted line between the same pair.

Fix: after both passes, drop an inferred relationship when an annotated relationship
already exists with the same `from` and `to` entity. The ID field stays as an attribute,
without `FK` (the annotated side already shows the link).

| ID | Case | Expected |
|----|------|----------|
| FD1 | `@ManyToOne Owner owner` + `OwnerId ownerId` in `Pet` | only the solid `"Pet" }o--o\| "Owner"` line |
| FD2 | Inferred link to an entity not linked by annotation | unchanged |
| FD3 | Annotated link `Pet → Owner` and inferred `Owner → Pet` (other direction) | both kept |

---

## 🟦 FIX-SELFREF — Self-references by own ID type

Known false result 10. Any field typed with the entity's own ID type is skipped, so a
real tree (`Category { CategoryId parentId; }`) loses its self-link.

Fix: exclude only the entity's own ID field (the `@Id`/`@EmbeddedId` field, or the
synthetic/inherited `id` from ERI-ID), not every field of that type. Other fields of the
own ID type draw a dotted self-relationship.

| ID | Case | Expected |
|----|------|----------|
| FS1 | `Category extends AbstractEntity<CategoryId>` with `CategoryId parentId` | `"Category" }o..\|\| "Category" : "parentId"` |
| FS2 | The entity's own `@Id` field | still no self-link |
| FS3 | I3 sample (`Visit.previousVisitId`) | updated: self-link drawn |

Each fix is done when its tests pass, plus `mvn test` from the root and `graphify update .`. No commits.

---

## Known false results (from review, not fixed)

These come from a review of ERI-REF against DDD-style code. They are the reason inference
is opt-in. Cheap fixes planned for 2, 4 and 10: 🟦 FIX-WRAPPER, 🟦 FIX-DUPLINK, 🟦 FIX-SELFREF.

False positives (a line that should not exist):
1. **Value object used as an `@Id`.** Any non-entity project class used as `@Id`/`@EmbeddedId`
   counts (`@Id Email email` makes `Newsletter.contact` draw to `User`; enums and records as keys too).
2. **Non-reference generic wrappers.** `Supplier<PetId>`, `Event<PetId>`, `Class<PetId>`
   resolve to `Pet` because the last type argument is taken.
3. **Non-persistent fields without `@Transient`**, such as `previousPetId` or cached values.
   Property access (annotations on getters) is not read.
4. **Duplicate lines for one link.** `@ManyToOne Owner owner` plus a read-only
   `@Column(insertable=false, updatable=false) OwnerId ownerId` gives a solid and a dotted
   line between the same pair.
5. **Same simple name in different modules.** `OrderId` in two modules is dropped as
   ambiguous; if only one is scanned, every `OrderId` maps to its entity.
6. **Inheritance hierarchies.** With `@Inheritance`, an ID resolves to the root entity.

Wrong semantics (the line is real, its meaning is not):
7. **Optionality not applied.** Inferred links are always drawn `}o..||`; a nullable
   `OwnerId` or `Optional<OwnerId>` is not reflected.
8. **Cardinality is assumed.** Plain ID → many-to-one, collection → one-to-many, even when
   the model is one-to-one or many-to-many.
9. **FK without a constraint.** In DDD the ID across aggregates is often a logical
   reference with no database constraint, but `FK` implies one.

False negatives (a link that should exist, but is not drawn):
10. **Self-references are skipped** for any field whose type equals the entity's own ID type,
    dropping real trees (`parentId`). The rule could be limited to the `@Id` field itself.
11. **`Map<PetId, X>` and `PetId[]`** are not read (map keys ignored, arrays not unwrapped).
12. **Fields in embeddables, `@MappedSuperclass`, and composite keys.**
13. **Custom base classes** (`BaseEntity<T>`) are not in the ID-style list.
14. **A subclass redeclares the ID** (`Dog extends Pet` with its own `PetId`): the type becomes
    ambiguous and the link disappears.
