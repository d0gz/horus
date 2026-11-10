# horus-cli — Unified CLI (class + ER + POM) — Plan & Specs

Living document. Tracks the decisions and the development plan for `horus-cli`, a new
module that bundles every generator of this repository into a single runnable jar.

## Context

Today there are two separate tools with different syntaxes:

| | `assis-cli` | `assis-pom` (`PomDependencyExtractor`) |
|---|---|---|
| Input | project folder (`SourceLocator`: `src/main/java` → `src` → `.`) | path to a `pom.xml` |
| Diagram choice | `--class`, `--er` | `--modo=dependencias\|completo\|reator\|c2` |
| Catalog | `--catalog <file>` (entity catalog) | `--catalogo=<file>` (POM catalog) |
| Output | `-d` (default `docs/diagrams/src`) | current directory |

Goal: **one tool** that complements the original ASSIS instead of two tools. The project
folder is the input, the `pom.xml` is found inside it, and a single `--modo` picks the
diagram.

`horus-cli` is a **new module**, separate from `assis-pom` and `assis-er`, the same way
`assis-cli` is separate from `assis-core`. `assis-cli` is not changed.

## Decisions

- **D1 — New module.** `horus-cli` sits beside `assis-cli`. It depends on `assis-core`,
  `assis-er` and `assis-pom`, and shades them into one jar. Package: `io.github.masmangan.horus.cli`.
  Main class: `HorusApp`.
- **D2 — Project folder as input.** It is an optional positional argument (default `.`).
  The Java source roots and the `pom.xml` are both looked up relative to it.
  Without the positional argument but with `--source-path`, the `pom.xml` is the nearest one
  at or above the first source path (`SourceLocator.findPomAbove`), so
  `--source-path ~/petclinic` or `--source-path ~/petclinic/src/main/java` reads
  `~/petclinic/pom.xml`, not the one in the cwd. The pom used is always logged.
- **D3 — `--modo` takes a single value.** The values are `class | er | dependencias | dependencias-completo | reator | c2`.
  `dependencias-completo` is the horus-cli name of the assis-pom `completo` mode; `HorusArgs`
  translates it before calling `GeneratePomDiagram`.
  There are no `--class` or `--er` flags.
- **D4 — Default run.** Without `--modo`, the tool generates class + er + the three pom
  diagrams (`dependencias`, `dependencias-completo`, `c2`; `reator` only on request),
  **without any catalog**. If `<project>/pom.xml` does not exist, the POM diagrams are skipped
  with an INFO log and the run still succeeds.
- **D5 — Single `--catalogo` option.** The catalog is always an **optional** user addition,
  and what it means depends on the mode:
  - `er` → entity catalog (`EntityCatalog`, assis-er)
  - `dependencias-completo`, `c2` → POM catalog (`CatalogResolver`, assis-pom; future: C2 entities)
  - no mode, `class`, `dependencias` or `reator` → usage error
- **D6 — Mode-specific flags.** `--inferred` is only valid with `--modo=er`.
  `--bancos=compartilhado|por-modulo` is only valid with `--modo=c2`.
- **D7 — Mode-driven lookup.** A POM-only mode does not run Java source discovery, so a
  project without `.java` files does not fail. An explicit POM mode without a `pom.xml`
  is an error (exit 2).
- **D8 — Value syntax.** `--modo`, `--catalogo` and `--bancos` accept both `--opt=value`
  and `--opt value`. `-d`, `--source-path`/`-sourcepath`, `--help` and `--version` behave as
  they do in `assis-cli`.
- **D9 — No change to existing generators.** `assis-pom` gets a thin facade
  (`GeneratePomDiagram`) over the existing `PomDependencyExtractor.run`. That class and its
  tests stay unchanged.

## Interface

```
java -jar horus-cli.jar [project-folder] [options]

  [project-folder]                    default "."; base for Java sources and pom.xml
  --modo=<m>                          class | er | dependencias | dependencias-completo | reator | c2
  --catalogo=<file>                   optional; er -> entity catalog, dependencias-completo/c2 -> POM catalog
  --inferred                          only with --modo=er
  --bancos=compartilhado|por-modulo   only with --modo=c2
  --source-path <p>, -sourcepath <p>  overrides Java source discovery (relative to cwd)
  -d <dir>                            output directory (default docs/diagrams/src)
  --help, --version
```

Outputs in `-d`: `class-diagram.puml`, `entity-diagram.mmd`, `dependencies.mmd`, `pom.mmd`,
`reator.mmd`, `c2.mmd`. The `dependencias-completo` and `c2` modes on an aggregator also write one file
per module (for example `pom-<artifactId>.mmd`).

Exit codes, the same as `assis-cli`: 0 success, 1 usage error, 2 resolve error (no sources / no pom
for an explicit POM mode), 3 generation error.

## Development plan

### H1 — assis-pom: `GeneratePomDiagram` facade
New file `assis-pom/src/main/java/io/github/masmangan/assis/pom/GeneratePomDiagram.java`,
following the pattern of `GenerateClassDiagram` and `GenerateEntityDiagram`:

```java
public static boolean isModo(String modo)
public static void generate(Path pom, Path outDir, String modo, Path catalogo, boolean bancosPorModulo)
        throws IOException
```

- It builds the argument array and calls `PomDependencyExtractor.run(args, out, err)`, which
  is package-private and lives in the same package.
- `out` is `new PrintStream(OutputStream.nullOutputStream())`, so the Mermaid text does not
  go to stdout. `err` is `System.err`, which keeps the existing warnings and C2 notes.
- The output file is `outDir.resolve(<SAIDA_PADRAO* for the mode>)`. Per-module files land in
  the same folder through the existing `gravarPorModulo`.
- A non-zero exit throws: 1 and 3 → `IOException`, 2 → `IllegalArgumentException`.

Tests: `GeneratePomDiagramTest` (the four modes write the expected file in `outDir`; a
missing pom throws; a catalog is applied in `completo`).

### H2 — assis-pom/pom.xml: shaded jar as an attached artifact
In `maven-shade-plugin`, add `<shadedArtifactAttached>true</shadedArtifactAttached>` and
`<shadedClassifierName>all</shadedClassifierName>`, plus `<createDependencyReducedPom>false</createDependencyReducedPom>`
(as in `assis-cli`; the tracked `assis-pom/dependency-reduced-pom.xml` is no longer regenerated). The standalone `assis-pom-<v>-all.jar`
stays available. The main artifact becomes the plain jar with its real dependencies
(`maven-model`, `jackson-databind`), so `horus-cli` shades them without duplicate classes.

### H3 — New module `horus-cli`
- Root `pom.xml`: add `<module>horus-cli</module>`.
- `horus-cli/pom.xml`: copy of `assis-cli/pom.xml` with `artifactId` `horus-cli`, dependencies on
  `assis-core`, `assis-er` and `assis-pom`, and shade `mainClass` `io.github.masmangan.horus.cli.HorusApp`.
- Root `sonar.coverage.jacoco.xmlReportPaths`: add `horus-cli/target/site/jacoco/jacoco.xml`.

### H4 — `horus-cli` sources (`io.github.masmangan.horus.cli`)
The classes in `assis-cli` are package-private, so `horus-cli` has its own versions,
adapted from them:

- **`HorusArgs`**: parsing and validation for D2–D8. Fields: `mode` (RUN/HELP/VERSION),
  `projectDir`, `sourceRoots`, `outDir`, `modo` (null = default run), `catalogo`, `inferred`,
  `bancosPorModulo`. Every error throws `IllegalArgumentException` with `usage`.
  The error cases are: unknown mode, duplicate option or positional argument, `--catalogo`
  with no mode or with an unsupported mode, `--inferred` without `--modo=er`, and `--bancos`
  without `--modo=c2`.
- **`SourceLocator`**:
  - `resolve(Path projectDir, Set<Path> requested)`: the `src/main/java` / `src` / `.`
    candidates resolve against `projectDir`. Explicit `-sourcepath` entries stay relative to
    the cwd, as in javac.
  - `Optional<Path> findPom(Path projectDir)`: returns `projectDir/pom.xml` when it is a regular file.
- **`HorusApp`**: `main` plus a test-friendly `run(String[])`. The flow is:
  1. parse; handle help and version.
  2. Prepare `outDir`, as in `AssisApp`.
  3. `needsJava = modo == null || class || er`; `needsPom = modo == null || GeneratePomDiagram.isModo(modo)`.
  4. If `needsJava`, call `SourceLocator.resolve`; a failure exits with 2.
  5. If `needsPom`, call `findPom`. With an explicit mode and no pom, exit 2. In the default
     run with no pom, log INFO and skip.
  6. Generate: class → `GenerateClassDiagram.generate(roots, outDir)`; er →
     `GenerateEntityDiagram.generate(roots, outDir, inferred, catalogoIfEr)`; pom →
     `GeneratePomDiagram.generate(pom, outDir, m, catalogoIfPom, bancos)` for each `m` in
     `pomModos()` (the chosen mode, or `dependencias`, `dependencias-completo`, `c2` in the default run, translated to the assis-pom names).
  7. `--version` prints the ASSIS, JavaParser, maven-model, Java and OS versions.

### H5 — Tests (`horus-cli/src/test/...`)
Reuse the `JulLogCaptor` approach from `assis-cli`. Every test uses a TempDir project with
`src/main/java` (one `@Entity`) and a minimal `pom.xml`:

- The default run writes `class-diagram.puml`, `entity-diagram.mmd`, `dependencies.mmd`,
  `pom.mmd` and `c2.mmd`.
- The default run without a `pom.xml` exits 0 and writes no `pom.mmd`.
- The positional project folder finds both the sources and the pom inside it.
- `--modo=class` and `--modo=er` each write only their own file.
- `--modo=dependencias`, `reator` and `c2` write their file in `-d`.
- `--modo=c2` on a project without a pom exits non-zero.
- A project with no `.java` files and `--modo=dependencias-completo` exits 0.
- `--modo=er --catalogo=<f>` applies the entity catalog. `--modo=dependencias-completo --catalogo=<f>`
  applies the POM catalog.
- `--modo c2` and `--modo=c2` behave the same.
- Errors: `--catalogo` without a mode, `--modo=class --catalogo=f`, `--inferred` without
  `--modo=er`, `--bancos` without `--modo=c2`, `--modo=xyz`, `--catalogo` without a value,
  two positional arguments.
- `SourceLocator.findPom`, and `resolve` relative to `projectDir`.

### H6 — Docs
- `README.md`: a `horus-cli` section with the interface above. It points to `assis-pom-<v>-all.jar`
  as the standalone POM tool.
- `docs/user/`: a getting-started for `horus-cli`.
- After the code: `graphify update .`

## Verification

1. `mvn -q clean verify` at the root: all modules pass, including the unchanged
   `assis-cli` and `assis-pom` tests.
2. `java -jar horus-cli/target/horus-cli-0.9.5-SNAPSHOT.jar <spring-petclinic>`: `docs/diagrams/src`
   holds `class-diagram.puml`, `entity-diagram.mmd`, `dependencies.mmd`, `pom.mmd`, `c2.mmd`
   (+ per-module files).
3. `java -jar ... <project> --modo=c2 --bancos=por-modulo -d <tmp>`: writes `c2.mmd` and the per-module
   files, and the notes appear on stderr.
4. `java -jar ... <project> --modo=er --catalogo=<cat.txt>`: the catalog is applied.
5. `unzip -l` on the jar contains `categorias.json` and `org/apache/maven/model/*`.
