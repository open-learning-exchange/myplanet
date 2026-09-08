# Copilot instructions — myPlanet

Read `CLAUDE.md` at the repo root first — it is the full codebase guide (architecture, build, conventions, task recipes). The root `AGENTS.md` is a short pointer file for skill discovery, not a replacement for `CLAUDE.md`.

## Shared agent skills

Reusable skills live as git submodules under `.agents/skills/`:

- **agents-summoning** — summon other AI agents on PRs/issues; reviewers speak, doers act (source: https://github.com/dogi/agents-summoning)
- **merge-prepping** — rewrite PR titles into house style (source: https://github.com/dogi/merge-prepping)
- **kotlin-importing** — sort/clean Kotlin imports (source: https://github.com/dogi/kotlin-importing)

Submodules are **not** initialized on a default clone or `actions/checkout`. Before reading anything under `.agents/skills/`, run:

```bash
git submodule update --init --recursive
```

Each skill's entry point is `.agents/skills/<name>/SKILL.md`.
