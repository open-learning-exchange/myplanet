# OpenHands memory — myPlanet

## Skills (auto-loaded from .agents/skills/)

Skill repos are git submodules. `.openhands/setup.sh` and `.codex/setup.sh`
initialize them at session start, before skill discovery runs. In any other
context (fresh manual clone, other agents), initialize them yourself:

```bash
git submodule update --init --recursive
```

- **agents-summoning** — summon other AI agents on PRs/issues: who answers, how to leash a doer, why a summon went silent; source: https://github.com/dogi/agents-summoning
- **merge-prepping** — rewrite PR titles into house style; source: https://github.com/dogi/merge-prepping
- **kotlin-importing** — sort/clean Kotlin imports; source: https://github.com/dogi/kotlin-importing

Reviewers speak; doers act — an unleashed doer mention (`@openhands`, `@devin`,
`@copilot`) defaults to commits on your branch, so add "comment only" when that
isn't wanted.

## Reference docs

- CLAUDE.md — full codebase guide (architecture, build, conventions)
- docs/DOMAIN_MODEL.md — learning domain: roles, courses, teams, surveys, sync
- docs/CODE_STYLE_GUIDE.md — naming, imports, coroutines, Room, Hilt, UI
- docs/TESTING.md — test patterns per layer
