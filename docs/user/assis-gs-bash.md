# Getting Started (bash)

This guide shows how to run **ASSIS CLI** from the command line and generate your first UML diagram from a minimal Java source file.

No IDE is required.

> The instructions below were validated with **ASSIS v0.9.4-beta**.

---

## What you will need

- A **terminal**
- **JDK 17 or newer**
- A **text editor** (any: `vi`, `nano`, VS Code, etc.)

---

## Step 1: Open a terminal

Open your system terminal.

> **Tip**  
> If you are using PowerShell, don’t worry — ASSIS works there too.  
> Examples below assume a Unix-like shell.

---

## Step 2: Create a working directory

```bash
mkdir assis-gs
cd assis-gs
```

This directory will hold:
- a Java source file (`A.java`)
- a PlantUML file (`docs/diagrams/src/class-diagram.puml`)
- the ASSIS CLI JAR file (`assis.jar`)

---

## Step 3: Download ASSIS

Download the ASSIS CLI JAR file from GitHub Releases.

For **v0.9.4-beta**:

```bash
wget -O assis.jar https://github.com/masmangan/assis/releases/download/v0.9.4-beta/assis-cli-0.9.4-beta.jar 
```

If `wget` is not available, you may use:

```bash
curl -L -o assis.jar https://github.com/masmangan/assis/releases/download/v0.9.4-beta/assis-cli-0.9.4-beta.jar 
```

Or download the file manually from:

https://github.com/masmangan/assis/releases

and save it as `assis.jar`.

---

## Step 4: Verify ASSIS is working

Run:

```bash
java -jar assis.jar --version
```

You should see the ASSIS version printed on the terminal.

✔ **Quick check:** if this works, ASSIS is correctly installed.

---

## Step 5: Create a minimal Java file

Create a file named `A.java` with a single class.

Using the command line:

```bash
cat <<EOF > A.java
class A {
}
EOF
```

This creates a simple Java type to analyze.

---

## Step 6: Run ASSIS

Run ASSIS in the current directory:

```bash
java -jar assis.jar
```

ASSIS will:
- Scan Java source files in the current folder
- Generate UML diagrams in PlantUML format under `docs/diagrams/src/`
- Generate an entity diagram of your JPA `@Entity` classes in Mermaid format (`entity-diagram.mmd`) in the same folder

To generate only one of them, pass `--class` (class diagram) or `--er` (entity diagram):

```bash
java -jar assis.jar --er
```

With `--er`, add `--inferred` to also draw links deduced from ID-typed fields
(DDD style, `OwnerId ownerId`). They are drawn dotted because they are guesses:

```bash
java -jar assis.jar --er --inferred
```

If the entity diagram misses a relationship, an entity or an attribute, shows one too many, or draws one wrong, write it in a
**catalog** file and pass it with `--catalog`. The catalog wins over what ASSIS found.
One entry per line; `#` starts a comment. Copy a line from `entity-diagram.mmd` and put
`add` in front of it, or use the field form:

```text
# entity-catalog.txt
add "Visit" }o--|| "Pet" : "petId"
add relationship | Owner | Pet | one-to-many | pets
add relationship | Visit | Vet | many-to-one | vetId | required, dotted
add entity | AuditLog | audit_log
add attribute | Visit | String | notes | nullable
add "Visit" { Long id PK; PetId? petId FK }
remove entity | Helper
remove "Pet" }o--o| "Pet" : "mother"
```

```bash
java -jar assis.jar --er --catalog entity-catalog.txt
```

| Field form | Values |
|------------|--------|
| `add relationship \| <from> \| <to> \| <cardinality> \| <label> [\| <flags>]` | cardinality: `many-to-one`, `one-to-many`, `one-to-one`, `many-to-many`; flags: `required`, `dotted` |
| `add entity \| <name> [\| <table>]` | creates the entity, or sets the table name of an existing one |
| `add attribute \| <entity> \| <type> \| <name> [\| <flags>]` | flags: `pk`, `fk`, `nullable`, `column=<name>`; not nullable unless `nullable` is given |
| `remove relationship \| <from> \| <to> \| <label>` | removes that relationship |
| `remove attribute \| <entity> \| <name>` | removes that attribute (by name or column name) |
| `remove entity \| <name>` | removes the entity and all its relationships |

Both entities of a relationship must exist in the diagram or be added by the catalog;
otherwise the line is skipped with a warning.
`add attribute` creates the entity when it is missing. An attribute with the same name
(or the same column name) as a found one replaces it in place; a new one is added at the end.
In the Mermaid form, several attributes go in one line, separated by `;`.
Entities and attributes added or changed by the catalog get a `%% from catalog` line above them.
Any line of the diagram also works with `remove` in front; removals are applied after all
additions, so they always win.
A catalog line with the same from, to and label as a found relationship replaces it.
Catalog relationships are listed after the comment `%% Relationships below come from the catalog`.
Problems in the file are logged as warnings; the diagram is still generated.

> **Tip**  
> Make your life easier by creating an alias:
> ```bash
> alias assis='java -jar assis.jar'
> ```
> Then simply run:
> ```bash
> assis
> ```
> You may add this alias to your `~/.bashrc` or `~/.zshrc`.

---

## Step 7: Open the generated diagram

Look for the generated `class-diagram.puml` file. Next to it you will find
`entity-diagram.mmd`; without `@Entity` classes it only contains `erDiagram`.

A minimal example looks like:

```text
@startuml
class A {
}
@enduml
```

✔ You are done.

You have successfully generated a PlantUML class diagram from Java source code.

> **Tip**  
> To see available options:
> ```bash
> java -jar assis.jar --help
> ```
>
> In this guide, ASSIS effectively runs:
> ```bash
> java -jar assis.jar -sourcepath . -d docs/diagrams/src/
> ```
> These defaults work because the guide uses a flat directory structure.


At this point, you have a UML diagram in PlantUML format:

docs/diagrams/src/class-diagram.puml

This text file fully describes the diagram.

From here, you may choose how to view it:

- View it locally as SVG (recommended)
- Use the PlantUML Online Server (no installation)

See:
- Getting Started: View diagrams as SVG
- Getting Started: View diagrams via PlantUML Online

---

## Found a problem?

If something did not work as expected, please report it:

👉 https://github.com/masmangan/assis/issues

When reporting an issue, include:
- ASSIS version information (`--version`)
- the command you ran
- any error message or screenshot

---

## Final note

ASSIS is a **static analysis tool**.

It does not:
- execute Java code
- require a build
- depend on frameworks
- infer runtime behavior

It simply reads Java source files and exposes their structure.

---
