---
name: kotlin-imports
description: Sort Kotlin import blocks, remove unused imports, and normalize blank lines around imports. Use when asked to clean up, sort, or organize imports in .kt files, or to remove unused imports across the myPlanet Android codebase without running ktlint.
---

# Kotlin import cleanup

Sorts imports, removes unused imports, and normalizes blank lines across Kotlin
files — a safe, offline alternative to running ktlint's `import-ordering` +
`no-unused-imports` rules.

## What it does

For every `*.kt` file under the given roots (default `app/src`):

1. **Sorts** the import block alphabetically by import path (case-sensitive
   ASCII order — matches ktlint's default, so it won't fight the linter).
2. **Removes unused imports** — an import is dropped only if its simple name
   never appears anywhere else in the file.
3. **Normalizes blank lines** — exactly one blank line before and after the
   import block, and no blank lines inside it.

## How to run

```bash
# Apply to the whole app module (main + test)
python3 .claude/skills/kotlin-imports/sort_imports.py app/src

# Preview without writing (report only)
python3 .claude/skills/kotlin-imports/sort_imports.py --check app/src

# Limit to a subtree or a single package
python3 .claude/skills/kotlin-imports/sort_imports.py app/src/main/java/org/ole/planet/myplanet/ui
```

The script prints how many files changed, every unused import it removed, and
any wildcard imports it found. It only rewrites files whose content actually
changes.

## Safety model (why removals don't break the build)

Unused-import detection is intentionally conservative — it only ever
**over-keeps**, never over-removes:

- **Backticks are stripped** before matching, so escaped identifiers like the
  Mockito `` `when` `` are detected as used.
- **A dot counts as a word boundary**, so extension-function calls (`.map`,
  `.collect`, `.launchIn`) count as usage of the imported name.
- **Operator / convention functions are never auto-removed** (`plus`, `get`,
  `getValue`, `setValue`, `provideDelegate`, `component1..N`, `contains`, ...)
  since they can be invoked via operator syntax without the name appearing.

## Wildcard imports

This codebase has **no** wildcard (`import x.*`) imports. If any are ever added,
the script **reports** them but does **not** expand them — reliable expansion
needs compiler symbol resolution. Expand those by hand or via the IDE's
"Optimize Imports".

## After running

Do **not** run `./gradlew test` or ktlint as part of this skill (per project
convention). To sanity-check, review `git diff` and confirm each removed name
truly appears nowhere in its file. The build/CI will catch anything that slips
through.
