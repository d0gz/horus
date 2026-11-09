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
| ERC-ADD | **Add entity** and **add attribute**: create the entity when missing, else merge on top of the extracted one | ✅ Done |
| ERC-REMOVE | **Remove** relationship, entity, attribute | ❌ Planned |

Status: ✅ done · ❌ not done · ⚠️ done with a warning or problem · 🛑 blocked · 🟦 fix task

Part 1 = ERC-CORE, part 2 = ERC-ADD (full specs below). ERC-REMOVE is outlined with its
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
| `add` / `remove` | (Mermaid entity / attribute) | see ERC-ADD "Mermaid form" | ERC-ADD / ERC-REMOVE |

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

## ERC-ADD — Entity and attribute addition

### Scope

In:
- `add entity`: create a missing entity, or set the table name of an existing one.
- `add attribute`: add or replace one attribute; creates the entity when missing.
- Mermaid form for both (D53): entity line, alias, one-line attribute block.
- `%% from catalog` notes above catalog-created/changed entities and attributes (D55).
- `add relationship` can target entities created by the catalog (D45, D49).

Out: every `remove` (ERC-REMOVE); re-running inference or FK detection on catalog
attributes (D61); column comments other than `column=`; the `%% table:` note form as
input (use the field form for table names that are not a Mermaid alias).

### `add entity`

```
add entity | <name> [| <table>]
```

- `name`: entity name, case-sensitive. Must be a name the renderer can quote (no `"`, `%`,
  `\` or line break); otherwise the line is skipped with a warning.
- `table`: optional. Rendered by the existing rules (alias `["…"]` when it is a valid
  Mermaid entity name, else a `%% table: …` note). A table equal to `name` is stored as
  `null`, same as the extractor ("only when it differs").
- **Entity missing →** appended after the existing entities (catalog order), no attributes,
  `fromCatalog` true.
- **Entity exists, `table` given →** `tableName` replaced, `fromCatalog` true, attributes
  kept in place (D57).
- **Entity exists, no `table` (or the same one) →** no change, warning
  `Line N: entity 'X' already exists, nothing changed` (D57).

### `add attribute`

```
add attribute | <entity> | <type> | <name> [| <flags>]
```

- `entity`: same rule as `add entity` `name`. **Missing → created** first (as by
  `add entity` without table), then the attribute is added.
- `type`: Java type as written (`List<String>`, `java.time.LocalDate`) or as in the
  diagram (`List~String~`); the renderer converts it as usual (D8, D9). Must not be empty
  or contain `"`, `{`, `}`, `;` or a line break.
- `name`: Java field name. Must be a Mermaid attribute word (the renderer's
  `ATTRIBUTE_WORD`), so the line it writes is valid; otherwise skipped with a warning.
- `flags`: optional fifth field, comma-separated, any order, case-insensitive (D60):
  - `pk` → `primaryKey`; `fk` → `foreignKey`; `nullable` → `nullable`.
  - `column=<name>` → `columnName` (rendered as today, D25). A value equal to `name` is
    stored as `null`. Empty value → ignored with a warning.
  - `pk` with `nullable` → `nullable` ignored with a warning (an id is never nullable,
    same as the extractor).
  - Without `nullable` the attribute is not nullable: the user wrote it on purpose.
  - Unknown flag → ignored with a warning.
- **Match (D58):** an attribute of the entity whose `name` equals the catalog name, else
  one whose `columnName` equals it (the diagram shows the column when it is a valid word,
  so a line copied from the `.mmd` still finds its attribute).
  - **Match → replaced in place** (keeps its position). The replacement is the whole
    attribute: type, `pk`, `fk`, `nullable`, `column` all come from the catalog line;
    the Java name of the matched attribute is kept when the match was by column name, and
    the catalog name becomes its column unless the line gives `column=`.
  - **No match →** appended after the existing attributes.
  - The attribute gets `fromCatalog` true; the entity's own `fromCatalog` is not changed
    by attribute lines on an existing entity.

### Mermaid form

The lines the renderer writes for entities, with `add` in front (D53):

```
add "AuditLog"                               entity, no attributes
add "AuditLog"["audit_log"]                  entity with table
add "Visit" { String? notes }                one attribute
add "Visit" { Long id PK; PetId? petId FK }  several attributes, separated by ;
add "Visit"["visits"] { String? notes }      table and attributes
```

- Name: quoted or bare, as for relationships. Alias: `["<table>"]` directly after the name.
- Block: `{`, attributes separated by `;`, `}` — one catalog line, because the catalog is
  one entry per line (Mermaid itself needs line breaks there). Empty block `{ }` = no
  attributes.
- Attribute: `<type>[?] <name> [PK] [FK]` (also `PK, FK`, case-insensitive keys). `type`
  has no spaces (Mermaid form; use `~` for generics, as the diagram shows). `?` →
  `nullable`. Anything else (third word that is not a key, missing name, a `"comment"`)
  → that attribute is skipped with a warning; the other attributes of the line still apply.
- What one line becomes (D56):
  - an alias → one `AddEntity` with the table;
  - no alias and no block → one `AddEntity` without table;
  - a block → one `AddAttribute` per attribute (the entity is created by them if missing);
    no extra `AddEntity` unless there is an alias.
- A `column=` has no Mermaid form; use the field form.

Field form ↔ Mermaid form:

| Mermaid form | Field form |
|--------------|------------|
| `add "AuditLog"["audit_log"]` | `add entity \| AuditLog \| audit_log` |
| `add "Visit" { String? notes }` | `add attribute \| Visit \| String \| notes \| nullable` |
| `add "Visit" { PetId petId FK }` | `add attribute \| Visit \| PetId \| petId \| fk` |
| `add "AuditLog" { Long id PK }` | `add attribute \| AuditLog \| Long \| id \| pk` |

### Application

- `AddEntity` and `AddAttribute` are phase `ADD_ENTITY` (D49): they run before every
  `add relationship`, in file order among themselves. So `add attribute | X | …` followed
  by `add entity | X | x_table` creates `X` and then sets its table.
- `AddRelationship` then sees catalog-created entities as existing (D45 unchanged in
  code: it already checks the view it is given).
- Same key twice in the catalog (entity name with a table / entity + attribute name):
  the last one wins, with a warning (D59, same rule as D48). Detected by the
  `fromCatalog` flag of the item being replaced, as `AddRelationship` does.
- Catalog items never trigger extraction rules: no inferred relationship from an
  ID-typed catalog attribute, no automatic `FK` marker, no ID type for a created entity
  (D61). `hiddenInferred` is unchanged (D52).

### Rendering (D55)

- `%% from catalog` (4-space indent) above an entity with `fromCatalog`, before its
  `%% table:` note if it has one.
- `%% from catalog` (8-space indent) above an attribute with `fromCatalog`, before its
  column note (D25) if it has one.
- Entities and attributes without `fromCatalog` render exactly as today.

```
    %% from catalog
    "AuditLog"["audit_log"] {
        %% from catalog
        Long id PK
    }
    "Visit" {
        Long id PK
        %% from catalog
        %% notes
        String? visit_notes
    }
```

### Decisions

| # | Decision | Reason |
|---|----------|--------|
| D56 | `CatalogParser` turns one line into a list of entries; a Mermaid entity line gives an `AddEntity` (when it has an alias or no block) plus one `AddAttribute` per block attribute | One line can state several facts; each entry stays one operation with one `applyTo`, so ERC-REMOVE can follow the same shape. |
| D57 | `add entity` on an existing entity only sets the table; with no new table it changes nothing and warns | The catalog never drops extracted attributes by accident; the warning tells the user the line had no effect. |
| D58 | An attribute is matched by Java name, else by column name; a match is replaced whole (keeping its Java name and position; on a column match the catalog name stays the column) | The catalog states the full attribute; matching by column lets users copy lines from the `.mmd`, which shows column names (D25). |
| D59 | Duplicate catalog entity (with table) or attribute (same entity + name): last wins, warning | Same as D48. |
| D60 | Attribute flags `pk`, `fk`, `nullable`, `column=<name>`; not nullable by default; `pk` + `nullable` drops `nullable` with a warning | Explicit facts only; an id is never nullable, as in the extractor. |
| D61 | The catalog does not re-run extraction or inference on what it adds | The catalog is applied to the `EntityView` (D43), which has no JavaParser types; the user states FKs and relationships directly. |
| D62 | Entity names, attribute names and types that would make the renderer write invalid Mermaid are rejected at parse time with a warning. The renderer exposes `isEntityName` / `isAttributeWord` (package `er`, `public static`) so the checks match its own patterns | A bad catalog line must never produce a broken diagram; one source of truth for the patterns. |

### Components

`EntityView` (change):
- `Entity` gains `boolean fromCatalog`, `Attribute` gains `boolean fromCatalog` (last
  components). Secondary constructors keep every existing caller at `false`.

`EntityMermaidRenderer` (change):
- `static final String FROM_CATALOG_NOTE = "from catalog"`; written as above.
- `public static boolean isEntityName(String)`, `public static boolean isAttributeWord(String)`
  over the existing `ENTITY_NAME` / `ATTRIBUTE_WORD` patterns (D62).

`er.catalog` (change):

| File | Change |
|------|--------|
| `CatalogEntry` | `permits AddRelationship, AddEntity, AddAttribute` |
| `AddEntity` (new) | `record AddEntity(int line, String name, String table) implements CatalogEntry`, phase `ADD_ENTITY` |
| `AddAttribute` (new) | `record AddAttribute(int line, String entity, EntityView.Attribute attribute) implements CatalogEntry`, phase `ADD_ENTITY`; creates the entity when missing, then matches (D58) |
| `CatalogParser` | `parseLine` returns `List<CatalogEntry>` (D56); `fieldEntity`, `fieldAttribute`, flag parsing (D60); `add entity` / `add attribute` no longer "not supported" |
| `MermaidLine` | `entity(int line, String text, List<String> warnings)` → `List<CatalogEntry>` (alias, block, attributes split on `;`) |

`GenerateEntityDiagram`, `assis-cli`: no change (the option and usage text already cover
entities and attributes).

User docs (`docs/user/assis-gs-bash.md`, "Catalog" section): `add entity` / `add
attribute` rows, flags, Mermaid form example, the `%% from catalog` note.

### Output

Catalog `samples/catalog-files/entity-catalog-add.txt` on the existing `samples/catalog`
sources (sources unchanged):

```
add relationship | AuditLog | Visit | many-to-one | visit
add entity       | AuditLog | audit_log
add "AuditLog" { Long id PK }
add attribute    | Visit | String | notes | nullable
add "Visit" { PetId petId FK }
add "Pet"["pets"]
add "Owner"
```

```
%% There could be relationships that were not shown - use '--inferred' to see the inferred relationships
erDiagram
    "Owner" {
        Long id PK
    }
    %% from catalog
    "Pet"["pets"]
    "Visit" {
        Long id PK
        %% from catalog
        PetId petId FK
        Set~PetId~? pastPets
        %% from catalog
        String? notes
    }
    %% from catalog
    "AuditLog"["audit_log"] {
        %% from catalog
        Long id PK
    }
    "Pet" }o--o| "Owner" : "owner"

    %% Relationships below come from the catalog
    "AuditLog" }o--o| "Visit" : "visit"
```

- Line 1 refers to `AuditLog` before it is declared; phases (D49) make it valid.
- `petId` was extracted as `PetId? petId`; the catalog replaces it in place, not nullable,
  `FK` (D58).
- `add "Owner"`: exists, no table → unchanged, warning `Line 7: entity 'Owner' already
  exists, nothing changed` (D57).

### Required test cases

Parser — `CatalogParserTest`:

| ID | Case | Expected |
|----|------|----------|
| CP8 | (changed) `remove …` in ERC-ADD | skipped, warning `not supported yet`; `add entity` / `add attribute` now parse |
| CP15 | `add entity \| X` and `add entity \| X \| x_table` | one `AddEntity`, table `null` / `x_table` |
| CP16 | `add entity` with too many fields, empty name, name with `"` or `%` | skipped, warning |
| CP17 | `add attribute` with 4 and 5 fields | one `AddAttribute`, flags as given |
| CP18 | Flags `pk`, `fk`, `nullable`, `column=visit_notes`, mixed case and order | each set; `column=` with the name itself → `null` |
| CP19 | `pk, nullable`; `column=` empty; unknown flag | entry kept, that flag dropped, warning each |
| CP20 | `add attribute` with empty type, type with `{`, name that is not an attribute word (`PK`, `1x`) | skipped, warning |
| CP21 | Mermaid `add "X"`, `add X`, `add "X"["x_table"]`, `add "X" { }` | one `AddEntity` each, table as written |
| CP22 | Mermaid `add "Visit" { String? notes }`, `{ Long id PK; PetId? petId FK }`, `PK, FK` | `AddAttribute` per attribute, same values as the field form (table above) |
| CP23 | Mermaid `add "Visit"["visits"] { String notes }` | `AddEntity` then `AddAttribute`, same line number |
| CP24 | Mermaid generic type `Set~PetId~?` | type `Set~PetId~`, nullable |
| CP25 | Mermaid block with one bad attribute (`String`, `String a b`, `String a "c"`) among good ones | good ones kept, one warning per bad one |
| CP26 | Mermaid unclosed block `add "Visit" {`, text after `}` | skipped, warning |
| CP27 | Entity line copied from a generated `.mmd` with indent, plus `add ` | parsed |

Resolver — `EntityCatalogTest`:

| ID | Case | Expected |
|----|------|----------|
| CA10 | `add entity` for a missing entity | appended last, no attributes, `fromCatalog` true |
| CA11 | `add entity` with table on an existing entity | `tableName` replaced, attributes and position kept, `fromCatalog` true |
| CA12 | `add entity` without table / same table on an existing entity | view unchanged, warning (D57) |
| CA13 | `add entity \| Owner \| Owner` | `tableName` `null` |
| CA14 | `add attribute` on a missing entity | entity created (`fromCatalog` true) with that attribute |
| CA15 | `add attribute`, new name | appended after extracted attributes, `fromCatalog` true; entity `fromCatalog` unchanged |
| CA16 | `add attribute`, same name as extracted | replaced in place, all values from the catalog |
| CA17 | `add attribute` whose name equals an extracted `columnName` | that attribute replaced, Java name kept (D58) |
| CA18 | Same attribute twice; same entity with table twice | last wins, warning (D59) |
| CA19 | `add relationship` to an entity created later in the file | relationship added (D49) |
| CA20 | `add attribute` then `add entity` with table, same entity | created, then table set |
| CA21 | Relationships and `hiddenInferred` | unchanged by entity/attribute entries; no inferred relationship from an ID-typed catalog attribute (D61) |

Renderer — `EntityMermaidRendererTest`:

| ID | Case | Expected |
|----|------|----------|
| CR5 | Entity `fromCatalog`, with and without alias | `%% from catalog` line above the entity line |
| CR6 | Entity `fromCatalog` with a non-alias table | catalog note, then `%% table:` note, then entity |
| CR7 | Attribute `fromCatalog`, with and without column note | catalog note (8 spaces), then column note, then attribute |
| CR8 | No `fromCatalog` anywhere | output identical to today |

End-to-end — `GenerateEntityDiagramSampleTest`:

| ID | Case | Expected |
|----|------|----------|
| CG4 | `samples/catalog` + `entity-catalog-add.txt` | file equals the "Output" example above |
| CG5 | Same with `--inferred` | entity blocks as in CG4 (`petId` is the catalog line), no hidden note, inferred block after the catalog group as without the catalog |

All existing tests keep passing; CP8 is updated (only `remove` stays unsupported).

### Done when

- CP8, CP15–CP27, CA10–CA21, CR5–CR8, CG4–CG5 pass, plus `mvn test` from the root.
- Roadmap row ✅; user docs updated; `assis-er/CLAUDE.md` catalog table lists `AddEntity`,
  `AddAttribute`; `graphify update .` run. No commits.

### ERC-ADD — Implementation Plan

> **For agentic workers:** use superpowers:test-driven-development per task. No commits
> (the user commits); report changed files instead. Java 17, tabs, no copyright header,
> few comments. `graphify query` before reading code, `graphify update .` after.

1. `EntityView.Entity.fromCatalog`, `Attribute.fromCatalog` + secondary constructors;
   renderer `FROM_CATALOG_NOTE`, `isEntityName`, `isAttributeWord` (CR5–CR8).
2. `AddEntity`, `AddAttribute`, `CatalogEntry` permits (CA10–CA21, built directly, no parser).
3. `CatalogParser` field form: `parseLine` → list, `fieldEntity`, `fieldAttribute`, flags
   (CP8, CP15–CP20).
4. `MermaidLine.entity` (CP21–CP27).
5. `samples/catalog-files/entity-catalog-add.txt`, CG4–CG5.
6. User docs, `assis-er/CLAUDE.md`, roadmap ✅, full `mvn test`, `graphify update .`.

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
