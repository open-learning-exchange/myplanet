# The Skill Sync — one skill repo, every agent

The agent skills this repo uses aren't stored here. Each one (`agents-summoning`,
`merge-prepping`, `kotlin-importing`, `branch-overtaking`) is maintained **once**
in its own repo under [`dogi`](https://github.com/dogi), and every agent loads it
from that single source of truth. This page is the plumbing: how each loader
finds a skill, and what to do after a skill repo merges.

## The skills

| Skill | What it does | Source repo | Loaded by |
|---|---|---|---|
| **agents-summoning** (`summoning`) | Summoning other AI agents on PRs and issues — the grid of who answers how, the Laws of Summoning, per-agent connection checklists, and the dated field notes behind every claim (harvested from this repo's multi-agent experiments) | `dogi/agents-summoning` | Claude Code (plugin), OpenHands + Copilot (submodule) |
| **merge-prepping** | Rewrite PR titles into house style | `dogi/merge-prepping` | Claude Code (plugin), OpenHands + Copilot (submodule) |
| **kotlin-importing** | Sort/clean Kotlin imports | `dogi/kotlin-importing` | Claude Code (plugin), OpenHands + Copilot (submodule) |
| **branch-overtaking** | Bind a session to someone else's PR branch and subscribe to its events | `dogi/branch-overtaking` | Claude Code (plugin) only — no submodule |

`agents-summoning` replaced this repo's old `docs/AGENT_SPELLBOOK.md`: the grid,
the laws and the vendor links became the skill's `SKILL.md`, the dated receipts
its `NOTES.md`, and the "is this agent even connected?" procedure its
`references/connecting.md`. Read it there — in a Claude Code session ask for it
by name (`/agents-summoning:summoning`) or just ask to "get codex to review
this"; from any clone it is `.agents/skills/agents-summoning/SKILL.md`.

## Why this works

- **Claude Code** reads `.claude/settings.json` → fetches the plugin
  marketplaces from GitHub → follows the internal symlinks under
  `plugins/<name>/skills/` to find `SKILL.md`. It does not read
  `.agents/skills/`. Marketplaces + `enabledPlugins` are committed to this
  repo, so **web/cloud sessions install the plugins at session start** —
  user-scoped plugin settings don't travel to a cloud VM.
- **OpenHands** reads `.agents/skills/<name>/SKILL.md` and auto-loads it on
  every session. It does **not** read `.claude/settings.json` or fetch
  marketplaces. `.openhands/microagents/repo.md` is also supported (unused
  here); the root `AGENTS.md` is auto-loaded memory and points at the skills.
- **Copilot's coding agent** discovers project skills from `.agents/skills/`
  (alongside `.github/skills` and `.claude/skills`), so the same submodules
  serve it — provided its checkout initializes submodules, which a plain clone
  does not.
- A **git submodule** at `.agents/skills/<name>/` makes the files physically
  present after `git submodule update --init`, on any machine or VM. Each skill
  repo's root doubles as a skill directory for exactly this reason.

## Maintaining it

**Bump the pin after every skill-repo merge.** OpenHands and Copilot see the
**pinned submodule commit**, while Claude Code's marketplace fetch tracks the
skill repo's **main tip** — the two match only while the pin is current:

```bash
git submodule update --remote --checkout -- .agents/skills/<name>
```

then commit the gitlink. `--checkout` is load-bearing: it forces the pin even
where the target repo configures `merge`/`rebase` update modes, and the
submodule sits on a detached HEAD.

**The load is best-effort on fresh sessions.** `.openhands/setup.sh` runs
`git submodule update --init --recursive` before skill discovery and is
deliberately non-fatal, so an uninitialized submodule means that skill simply
doesn't load that session — run the init once connectivity is back. Wire the
same command into any other agent's setup steps (Copilot's, Jules's Initial
Setup script) that needs to read `.agents/skills/`.

**Symlinks inside a skill repo** normally resolve on every checkout, since
target and link travel together. If one doesn't, check the stored target with
`readlink <link> | od -c` — a trailing `\n` (from generating the link via
echo/printf instead of `ln -s`) makes it point at a filename ending in an
invisible newline. Recreate with `ln -s`. Field-tested 2026-08-09:
`kotlin-importing.py`'s plugin symlink shipped with exactly this defect while
its sibling `SKILL.md` link was clean. Windows checkouts outside Developer Mode
need `core.symlinks=true`, or Git writes the links as plain files containing the
target path and a loader reads that string as the skill body.
