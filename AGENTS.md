# OpenHands memory — myPlanet

## Skills (auto-loaded from .agents/skills/)

Skill repos are git submodules — initialize them first on a fresh clone:

```bash
git submodule update --init --recursive
```

- **merge-prepping** — rewrite PR titles into house style; source: https://github.com/dogi/merge-prepping
- **kotlin-importing** — sort/clean Kotlin imports; source: https://github.com/dogi/kotlin-importing

Until each skill repo is restructured with `SKILL.md` at its root (see
`docs/SKILL_SYNC_SETUP.md`, Step 1), the skill files live nested at
`.agents/skills/<name>/plugins/<name>/skills/<skill>/SKILL.md` — read them
from there.

## Reference docs

- CLAUDE.md — full codebase guide (architecture, build, conventions)
- docs/DOMAIN_MODEL.md — learning domain: roles, courses, teams, surveys, sync
- docs/CODE_STYLE_GUIDE.md — naming, imports, coroutines, Room, Hilt, UI
- docs/TESTING.md — test patterns per layer
- docs/AGENT_SPELLBOOK.md — summoning AI agents on PRs
- docs/SKILL_SYNC_SETUP.md — how the shared skills are wired up and maintained
