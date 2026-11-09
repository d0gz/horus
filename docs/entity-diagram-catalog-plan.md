# Entity Diagram — Catalog Plan & Specs

Companion to [`entity-diagram-plan.md`](entity-diagram-plan.md) (default output),
[`entity-diagram-inferred-plan.md`](entity-diagram-inferred-plan.md) (opt-in heuristics) and
[`entity-diagram-refactor-plan.md`](entity-diagram-refactor-plan.md) (module layout).
Decision numbers (`D…`) are shared and stay unique across all files.

## Goal

A user-written text file, the **entity catalog**, corrects the diagram where the extractor
is wrong or blind: it adds relationships, entities and attributes the run did not find, and
removes ones it found. Same idea as `assis-pom`'s `CatalogResolver` (`--catalogo=`): a plain
text file, one entry per line, read on every run, never written back, problems reported as
warnings, never as errors.

**The catalog overrules the application.** What the user states wins over what the code
states and over what inference guesses.

```
DeclaredIndex ─► EntityExtractor ─► EntityView ─► EntityCatalog.apply ─► EntityView ─► EntityMermaidRenderer ─► .mmd
```

The catalog works on the `EntityView`, after extraction and before rendering. It never
touches JavaParser types, so it works the same with or without `--inferred`.

## Roadmap

| ID | Scope | Status |
|----|-------|--------|
| ERC-CORE | Catalog file, language, parser, `EntityCatalog` resolver, CLI `--catalog`, renderer group for catalog lines, **add relationship** between existing entities | ✅ Done |
| ERC-ADD | **Add entity** and **add attribute**: create the entity when missing, else merge on top of the extracted one | ❌ Planned |
| ERC-REMOVE | **Remove** relationship, entity, attribute | ❌ Planned |

Status: ✅ done · ❌ not done · ⚠️ done with a warning or problem · 🛑 blocked · 🟦 fix task

Part 1 = ERC-CORE (full spec below). ERC-ADD and ERC-REMOVE are outlined with their
assumptions, so the language and the resolver are designed for them from the start; each
gets a full spec before it is implemented.

---

## Catalog language (all parts)

UTF-8 text, one entry per line. Blank lines and lines starting with `#` are ignored.
Two forms are accepted, and can be mixed in one file:

- **Field form:** fields separated by `|`, whitespace around each field trimmed, first
  field `<operation> <kind>`.
- **Mermaid form:** `<operation>` followed by a line written as in the generated
  `entity-diagram.mmd`. A user can copy a line from the diagram, put `add` or `remove` in
  front of it and edit it (D53).

The parser picks the form per line: if the text after the operation word starts with
`relationship`, `entity` or `attribute` followed by `|`, it is the field form; otherwise
it is the Mermaid form.

```
# entity-catalog.txt
add relationship    | Visit | Pet | many-to-one | petId | required
add relationship    | Owner | Pet | one-to-many | pets

add entity          | AuditLog | audit_log                         (ERC-ADD)
add attribute       | Visit | String | notes | nullable             (ERC-ADD)

remove relationship | Pet | Pet | mother                           (ERC-REMOVE)
remove attribute    | Owner | cache                                 (ERC-REMOVE)
remove entity       | Helper                                        (ERC-REMOVE)

# Mermaid form, same meaning
add "Visit" }o--|| "Pet" : "petId"
add "Owner" ||..o{ "Pet" : "pets"                                     (dotted, D54)
remove "Pet" }o--o| "Pet" : "mother"                                  (ERC-REMOVE)
```

| Operation | Kind | Fields after the first | Part |
|-----------|------|------------------------|------|
| `add` | `relationship` | `from` · `to` · `cardinality` · `label` · [`required`] | ERC-CORE |
| `add` | `entity` | `name` · [`table`] | ERC-ADD |
| `add` | `attribute` | `entity` · `type` · `name` · [flags] | ERC-ADD |
| `remove` | `relationship` | `from` · `to` · [`label`] | ERC-REMOVE |
| `remove` | `attribute` | `entity` · `name` | ERC-REMOVE |
| `remove` | `entity` | `name` | ERC-REMOVE |
| `add` / `remove` | (Mermaid relationship) | `<from> <symbol> <to> : <label>` | ERC-CORE / ERC-REMOVE |
| `add` / `remove` | (Mermaid entity / attribute) | see ERC-ADD outline | ERC-ADD / ERC-REMOVE |

Common rules:
- Entity names are the Java class simple names, as in the diagram identifiers (D23),
  case-sensitive.
- Operation and kind are case-insensitive (`Add Relationship` is fine).
- A line with an unknown operation or kind, too few fields, or an invalid value is
  skipped with a warning naming its line number. The rest of the file still applies.
- A kind that the current part does not support yet (e.g. `add entity` before ERC-ADD) is
  skipped with the warning `Line N: 'add entity' is not supported yet`.

---

## ERC-CORE — Catalog resolver, language and relationship addition

### Scope

In:
- Read the catalog file given by `--catalog <file>`, parse it, keep it in memory.
- `add relationship` between two entities that **exist in the extracted view**.
- Render catalog relationships as solid lines, grouped under a `%%` note.
- Warnings for every skipped line or unreadable file, logged at `WARNING`.

Out: `add entity`, `add attribute` (ERC-ADD); every `remove` (ERC-REMOVE); attributes on
catalog relationships (no `FK` marker is added); `@Table` alias changes.

### `add relationship`

```
add relationship | <from> | <to> | <cardinality> | <label> [| <flags>]
```

- `cardinality`: `many-to-one`, `one-to-many`, `one-to-one`, `many-to-many`. Matching drops
  `-`, `_` and spaces and ignores case, so `ManyToOne`, `MANY_TO_ONE` and `many to one` are
  the same.
- `label`: the relationship label (D13), normally the Java field name. Required: it is the
  key for overruling and, later, for removal. It must not contain `"` or a line break
  (would break the quoted Mermaid label).
- `flags`: optional sixth field, comma-separated, case-insensitive: `required`, `dotted`.
  `required`:
  Allowed only for `many-to-one` and `one-to-one` (same as D18); on other cardinalities it
  is ignored with a warning. Without it the end is optional (`o|`), as for annotated
  relationships.
  `dotted`: draw the line with `..` (D54). Any other flag → ignored with a warning.

### `add relationship` — Mermaid form

```
add <from> <symbol> <to> : <label>
```

- `from` / `to`: an entity name, quoted (`"Visit"`) or bare (`Visit`, Mermaid word
  `[A-Za-z_][A-Za-z0-9_-]*`). Same rule for `label`: quoted or one bare word. Required.
- `symbol` = left end + line + right end, without spaces, as in Mermaid:

  | Left end | Meaning | Right end | Meaning |
  |----------|---------|-----------|---------|
  | `}o`, `}\|` | many | `o{`, `\|{` | many |
  | `\|o`, `\|\|` | one | `o\|` | one, optional |
  | | | `\|\|` | one, required |

  Line `--` = solid, `..` = dotted (D54).
- Cardinality from the ends: many/one → `MANY_TO_ONE`, one/many → `ONE_TO_MANY`,
  one/one → `ONE_TO_ONE`, many/many → `MANY_TO_MANY`. `required` = right end `||` on a
  to-one cardinality.
- The view has no optionality for the left end or for "many" ends, so `}|`, `|o` and `|{`
  are accepted but read as `}o`, `||` and `o{`, with the warning
  `Line N: optionality of the '<end>' end is not kept`. The rendered line then shows what
  the view holds.
- Anything else (unknown symbol, missing `:` or label, extra text) → line skipped, warning.

The two forms produce the same `AddRelationship` entry:

| Mermaid form | Field form |
|--------------|------------|
| `add "Visit" }o--\|\| "Pet" : "petId"` | `add relationship \| Visit \| Pet \| many-to-one \| petId \| required` |
| `add "Visit" }o--o\| "Pet" : "petId"` | `add relationship \| Visit \| Pet \| many-to-one \| petId` |
| `add "Owner" \|\|..o{ "Pet" : "pets"` | `add relationship \| Owner \| Pet \| one-to-many \| pets \| dotted` |

### Decisions

| # | Decision | Reason |
|---|----------|--------|
| D43 | The catalog is applied to the `EntityView`, between extractor and renderer, by a new class in a new package `er.catalog` | Keeps the extractor "code states only" and the renderer pure; the catalog stays independent of JavaParser. |
| D44 | Language: one entry per line, `\|` separated, first field `<operation> <kind>`, `#` comments (same style as the `assis-pom` catalog) | Familiar to users of `--catalogo`; one parser rule covers all parts. |
| D45 | A relationship whose `from` or `to` is not an entity of the view is skipped with a warning; nothing in the diagram changes | User requirement. Mermaid would otherwise invent an empty box. In ERC-ADD, entities added by the catalog count as existing (see D49). |
| D46 | Overrule: a catalog relationship with the same `from`, `to` and `label` as an extracted one (annotated or inferred) **replaces** it; a different label is a new relationship | The catalog wins; the label is the identity, so two links between the same pair stay possible. |
| D47 | Catalog relationships are solid lines (`--`) by default (see D54), and are rendered in their own group after the declared relationships and before the inferred block, under `%% Relationships below come from the catalog` | The user states them as facts; the group shows the reader where they came from. A replaced extracted relationship moves into this group. |
| D48 | Duplicate entries (same `from`, `to`, `label`) in the catalog: the last one wins, with a warning | Same rule as the `assis-pom` catalog. |
| D49 | Fixed application order, independent of line order: entity and attribute additions, then relationship additions, then removals | A relationship can refer to an entity added further down the file (ERC-ADD); removals have the last word (ERC-REMOVE). In ERC-CORE only the middle phase exists. |
| D50 | Missing or unreadable catalog file: warning, diagram generated without the catalog. An empty file or one with no valid lines changes nothing | Same as `assis-pom`: the catalog never breaks a run. |
| D51 | CLI (decided): `--catalog <file>` (value after a space, like `-d`). Valid when the ER diagram is generated (no diagram flag, or `--er`); with only `--class` it is a usage error `Option --catalog requires the entity diagram` | Same reasoning as D34: the option only affects the ER diagram. |
| D52 | The catalog applies with and without `--inferred`. `hiddenInferred` (D36) is not changed by the catalog | The user's facts do not depend on the guess mode. |
| D53 | Two equivalent forms per line: field form and Mermaid form (`add "Visit" }o--\|\| "Pet" : "petId"`). Rule: any line of the generated `.mmd` with `add`/`remove` in front is a valid catalog line (relationships in ERC-CORE, entities and attributes in ERC-ADD) | Users can copy from the diagram they are fixing; the field form stays for readable, explicit edits. Both parse to the same `CatalogEntry`. |
| D55 | Entities and attributes created or changed by the catalog (ERC-ADD) get a `%% from catalog` line above them; catalog relationships are marked by their group note (D47) instead | Same "show where it came from" rule as relationships, without breaking Mermaid (`%%` lines are stripped before parsing, D25). |
| D54 | A catalog relationship is solid unless the user asks for dotted (`..` in the Mermaid form, `dotted` flag in the field form). Dotted catalog lines stay in the catalog group and are not `inferred` | Refines D47: the user decides the line style; "inferred" keeps meaning "guessed by the tool". |

### Components

New package `io.github.masmangan.assis.er.catalog` (folder `er/catalog/`), same rules as
`er.extract`: one public facade, everything else package-private.

| File | Holds |
|------|-------|
| `EntityCatalog` (public) | `static EntityCatalog empty()`, `static EntityCatalog from(Path file)`, `static EntityCatalog fromText(String text)`, `EntityView apply(EntityView view)`, `List<String> warnings()`, `int size()` |
| `CatalogParser` | Text → `List<CatalogEntry>` plus warnings. Splits the operation word, detects the form (D53), then one `switch` on `<kind>` (field form) or a Mermaid-line reader; one small parse method per kind |
| `MermaidLine` | Mermaid-form reader: names (quoted or bare), relationship symbol → cardinality, `required`, `dotted`. ERC-ADD adds entity/attribute lines here |
| `CatalogEntry` | `sealed interface CatalogEntry { int line(); Phase phase(); EntityView applyTo(EntityView view, List<String> warnings); }` with `enum Phase { ADD_ENTITY, ADD_RELATIONSHIP, REMOVE }` (D49) |
| `AddRelationship` | `record AddRelationship(int line, String from, String to, Cardinality cardinality, String label, boolean required, boolean dotted) implements CatalogEntry` |

`EntityCatalog.apply` sorts entries by `phase` (stable, so file order is kept inside a
phase), then folds them over the view: `view = entry.applyTo(view, warnings)`. Warnings
from applying (unknown entity, D45) are added to `warnings()`; `apply` may be called once
per run.

Extending: a new operation is one new `record … implements CatalogEntry` plus one `case` in
`CatalogParser`. Nothing else changes.

`EntityView` (change):
- `Relationship` gains `boolean fromCatalog` and `boolean dotted` (last components).
  Secondary constructors keep every existing caller compiling with `false`.

`EntityMermaidRenderer` (change):
- Relationships are rendered in three groups: declared (`!inferred && !fromCatalog`), then
  a blank line + `%% Relationships below come from the catalog` + catalog ones, then the
  existing inferred block (D37). No note when a group is empty.
- `symbol`: `..` when `inferred || dotted`, else `--`. Catalog relationships are never
  `inferred`, so their ends use `required` like annotated ones (`}o..o|` is possible for a
  dotted, optional catalog line).

`GenerateEntityDiagram` (change):
- New overload `generate(Set<Path> sourceRoots, Path outDir, boolean inferred, Path catalog)`;
  `catalog` may be `null` (no catalog). The existing overloads pass `null`.
- `EntityCatalog.from(catalog).apply(view)` before rendering; each warning logged at
  `Level.WARNING`, plus one `INFO` line `Catalog: N entries read` (valid lines parsed; lines skipped while applying are in the warnings).

`assis-cli` (change):
- `Args`: `--catalog <file>` → `Path catalog` on `CliArgs` (D51); usage text:
  ```
    --catalog <file>
          Entity catalog: add or remove entities, attributes and relationships
          in the entity diagram (overrules what was found in the code)
  ```
- `AssisApp.run` passes it to `GenerateEntityDiagram.generate`.
- User docs: a "Catalog" section in `docs/user/assis-gs-bash.md` with the language table
  and an example.

### Output

Catalog (`samples/catalog`: `Pet extends AbstractEntity<PetId>`, `Visit` has a plain
`PetId petId`, `Owner` has `@OneToMany(mappedBy = "owner") List<Pet> pets`, `Pet` has
`@ManyToOne Owner owner`):

```
add relationship | Visit | Pet | many-to-one | petId | required
add "Pet" }o--|| "Owner" : "owner"
add relationship | Visit | Ghost | many-to-one | ghost
```

```
erDiagram
    ...entity blocks unchanged...

    %% Relationships below come from the catalog
    "Visit" }o--|| "Pet" : "petId"
    "Pet" }o--|| "Owner" : "owner"
```

`Pet → Owner "owner"` was annotated (`}o--o|`); the catalog replaces it (D46) and it moves
into the catalog group (D47). `Visit → Ghost` is skipped: `Ghost` is not an entity (D45),
warning `Line 3: entity 'Ghost' not found, relationship skipped`.

### Required test cases

Parser — `CatalogParserTest` (pure text, package `er.catalog`):

| ID | Case | Expected |
|----|------|----------|
| CP1 | Valid `add relationship` with 5 and with 6 fields | one `AddRelationship`, `required` false / true |
| CP2 | Blank lines, `#` comments, surrounding spaces | ignored / trimmed |
| CP3 | `Add Relationship`, `ADD RELATIONSHIP` | parsed |
| CP4 | Cardinality spellings `many-to-one`, `ManyToOne`, `MANY_TO_ONE`, `many to one` | `MANY_TO_ONE` |
| CP5 | Unknown cardinality, too few fields, empty `from`/`to`/`label`, label with `"` | skipped, one warning each with the line number |
| CP6 | `required` on `one-to-many` / `many-to-many`; unknown flag | entry kept without that flag, warning |
| CP7 | Unknown operation or kind (`update relationship`, `add table`) | skipped, warning |
| CP8 | `add entity`, `add attribute`, `remove …` in ERC-CORE | skipped, warning `not supported yet` |
| CP9 | Mermaid form, each of the four cardinalities, quoted and bare names/label | same `AddRelationship` as the field form (table above) |
| CP10 | Mermaid right end `\|\|` / `o\|` on to-one | `required` true / false |
| CP11 | `..` line; field form `dotted`, `required, dotted` | `dotted` true |
| CP12 | Ends `}\|`, `\|o`, `\|{` | read as `}o`, `\|\|`, `o{`, warning (left-end / many-end optionality not kept) |
| CP13 | Unknown symbol (`}o-->`), missing `:`, missing label, extra text after label | skipped, warning |
| CP14 | A relationship line copied from a generated `.mmd` (with indent) plus `add ` in front | parsed |

Resolver — `EntityCatalogTest`:

| ID | Case | Expected |
|----|------|----------|
| CA1 | `empty()` / empty text | `apply` returns an equal view, no warnings |
| CA2 | Relationship between two existing entities | added, `fromCatalog` true, `inferred` false |
| CA3 | `from` or `to` not in the view | view unchanged, warning (D45) |
| CA4 | Same `from`/`to`/`label` as an annotated relationship | replaced: catalog cardinality and `required`, `fromCatalog` true, only one left (D46) |
| CA5 | Same key as an inferred relationship | replaced, solid (`inferred` false) |
| CA6 | Same pair, different label | both kept |
| CA7 | Same catalog key twice | last wins, warning (D48) |
| CA8 | `from(Path)` with a missing file | `empty()` behaviour, one warning (D50) |
| CA9 | Entities, attributes and `hiddenInferred` | unchanged by relationship entries (D52) |

Renderer — `EntityMermaidRendererTest`:

| ID | Case | Expected |
|----|------|----------|
| CR1 | View with declared, catalog and inferred relationships | declared lines, blank line + catalog note + catalog lines, blank line + inferred note + dotted lines |
| CR2 | Catalog relationship, `required` / not, `MANY_TO_ONE` | `}o--\|\|` / `}o--o\|` |
| CR3 | No catalog relationships | output identical to today (no note) |
| CR4 | Catalog relationship with `dotted`, required / not | `}o..\|\|` / `}o..o\|`, inside the catalog group |

End-to-end — `GenerateEntityDiagramSampleTest`, new sample `samples/catalog/` with a catalog
file `samples/catalog-files/entity-catalog.txt` (outside the source root, so it is not scanned):

| ID | Case | Expected |
|----|------|----------|
| CG1 | `samples/catalog` + catalog | file equals the "Output" example above |
| CG2 | Same with `--inferred` | catalog group before the inferred block; `Visit → Pet "petId"` only once, solid (CA5) |
| CG3 | Catalog path `null` / missing file | output equals the run without catalog |

CLI — `AssisAppTest`:

| ID | Case | Expected |
|----|------|----------|
| CC1 | `--er --catalog <file>` | exit 0, catalog line in `entity-diagram.mmd` |
| CC2 | `--catalog <file>` with no diagram flag | exit 0, both diagrams, catalog applied |
| CC3 | `--class --catalog <file>` | non-zero exit, `Option --catalog requires the entity diagram` |
| CC4 | `--catalog` with no value | usage error |
| CC5 | `CliArgs.usage` | contains `--catalog` |

All existing tests keep passing unchanged (no catalog → no change).

### Done when

- CP1–CP14, CA1–CA9, CR1–CR4, CG1–CG3, CC1–CC5 pass, plus `mvn test` from the root.
- User docs updated; `assis-er/CLAUDE.md` lists `er.catalog`; `graphify update .` run. No commits.

### ERC-CORE — Implementation Plan

> **For agentic workers:** use superpowers:test-driven-development per task. No commits
> (the user commits); report changed files instead. Java 17, tabs, no copyright header,
> few comments. `graphify query` before reading code, `graphify update .` after.

1. `EntityView.Relationship.fromCatalog`, `dotted` + secondary constructors; renderer groups and dotted symbol (CR1–CR4).
2. `CatalogEntry`, `AddRelationship`, `CatalogParser`, `MermaidLine` (CP1–CP14).
3. `EntityCatalog` (`empty`, `from`, `fromText`, `apply`, phases, warnings) (CA1–CA9).
4. `GenerateEntityDiagram` overload + sample `samples/catalog` and its catalog file (CG1–CG3).
5. `assis-cli`: `Args`, `CliArgs`, `AssisApp`, usage, user docs (CC1–CC5).
6. Full `mvn test`, `graphify update .`, roadmap row ✅, `assis-er/CLAUDE.md` updated.

---

## ERC-ADD — Entity and attribute addition (outline)

To be fully specified before implementation. Assumptions:

```
add entity    | <name> [| <table>]
add attribute | <entity> | <type> | <name> [| <flags>]
```

- **Entity missing → created** at the end of the entity list (file order), with no
  attributes unless `add attribute` lines add some. **Entity exists → merged**: a given
  `table` replaces the extracted `tableName`; extracted attributes are kept.
- `add attribute` on a missing entity **creates** the entity (no separate `add entity`
  needed), then adds the attribute.
- Attribute with the same name as an extracted one → **replaced** in place (keeps its
  position); a new name → appended after the extracted attributes.
- `type` is a Java type as written (`List<String>`); the renderer converts it as usual (D8, D9).
- Flags: comma-separated, any order, case-insensitive: `pk`, `fk`, `nullable`,
  `column=<name>`. Without `nullable` the attribute is not nullable (the user wrote it on
  purpose; no `?` guesswork).
- Phase `ADD_ENTITY` (D49), so `add relationship` lines can target catalog-created
  entities anywhere in the file; D45 then counts them as existing.
- Mermaid form (D53), the same lines the renderer writes:
  ```
  add "AuditLog"                         entity, no attributes
  add "AuditLog"["audit_log"]            entity with table alias
  add "Visit" { String? notes }          attribute(s) in one line, Mermaid block syntax
  add "Visit" { Long id PK; PetId? petId FK }
  ```
  `?` after the type = nullable, `PK` / `FK` keys. Several attributes in one block are
  separated by `;` (Mermaid needs line breaks there; the catalog is one entry per line).
  A `%% <name>` column comment cannot be expressed this way; use the field form
  `column=` flag.
- `%% from catalog` note (D55): a whole-line Mermaid comment directly above every entity
  the catalog created or whose table name it set (4-space indent), and above every
  attribute it added or replaced (8-space indent). When an attribute also has a column
  note (D25), the catalog note comes first:
  ```
      %% from catalog
      "AuditLog"["audit_log"] {
          %% from catalog
          String? notes
      }
      "Visit" {
          Long id PK
          %% from catalog
          %% notes
          String? visit_notes
      }
  ```
  `EntityView.Entity` and `EntityView.Attribute` gain `boolean fromCatalog` (secondary
  constructors keep callers at `false`); the renderer writes the note when it is true.
  Extracted entities and attributes untouched by the catalog get no note.

## ERC-REMOVE — Removal (outline)

To be fully specified before implementation. Assumptions:

```
remove relationship | <from> | <to> [| <label>]
remove attribute    | <entity> | <name>
remove entity       | <name>

remove "Pet" }o--o| "Pet" : "mother"     Mermaid form: symbol ignored for matching
remove "Helper"
remove "Owner" { cache }                 attribute by name; a type before it is ignored
```

- `remove relationship` with a label removes that relationship; without a label it removes
  **every** relationship from `from` to `to` (one direction only).
- `remove entity` also removes every relationship that has it as `from` or `to`, so no
  dangling Mermaid boxes appear.
- `remove attribute` removes it by Java field name (not column name).
- Removing something that is not there → warning, no change.
- Phase `REMOVE` runs last (D49): a removal wins over an `add` of the same item in the
  same catalog, with a warning naming both lines.
- Removal applies to extracted, inferred and catalog-added items alike. Removing an
  inferred relationship does not change `hiddenInferred`.
