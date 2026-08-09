# Skill sync setup — Claude Code + OpenHands

Goal: maintain `merge-prepping` (and `kotlin-importing`) **once** in their own repos, and have both Claude Code and OpenHands auto-load the same skill on every session in this repo.

## Rollout status

- [x] **Step 1** — `dogi/merge-prepping` and `dogi/kotlin-importing` restructured with root `SKILL.md` (plugin paths symlinked back), skills renamed to discovery-safe names (`prepping`, `importing`)
- [x] **Step 2** — submodules added at `.agents/skills/` (pinned at `merge-prepping@24744f8`, `kotlin-importing@a4b4ca2`)
- [x] **Step 3** — `AGENTS.md` written at the repo root
- [x] **Step 4** — `.claude/settings.json` already correct, no change needed
- [x] **Step 5** — `.github/copilot-instructions.md` points Copilot at the skills and the submodule init
- [x] **Step 6** — `.openhands/setup.sh` initializes the submodules before OpenHands skill discovery

All steps are live: OpenHands auto-loads from `.agents/skills/<name>/SKILL.md` (submodules bootstrapped by `.openhands/setup.sh` before discovery), Claude Code keeps loading the plugins through the marketplaces, and Copilot picks the skills up via its instruction file.

## Why this works

- **Claude Code** reads `.claude/settings.json` → fetches plugin marketplaces from GitHub → follows internal symlinks to find `SKILL.md`.
- **OpenHands** reads `.agents/skills/<name>/SKILL.md` → auto-loads on every session. It does **not** read `.claude/settings.json` or fetch marketplaces.
- A **git submodule** at `.agents/skills/<name>/` makes the files physically present after `git submodule update --init`, on any machine or VM.
- **Internal symlinks** (inside the skill repo) resolve on every checkout — no broken-link problem.

## Alternatives considered (and why the submodule won)

Brainstormed with OpenHands; two simpler options were on the table:

- **Option A — copy the skill into this repo** as plain files under `.agents/skills/merge-prepping/`. OpenHands would auto-load it, and the trigger comment shrinks to `@openhands prep this PR` (or even `@openhands fix the title` — the skill description lists the trigger phrases). But the copy forks the source of truth: every edit to `dogi/merge-prepping` must be manually re-copied here, and the two versions silently drift.
- **Option B — no install, explicit comment each time**: `@openhands clone https://github.com/dogi/merge-prepping, read plugins/merge-prepping/skills/prepping/SKILL.md, and apply the title procedure to this PR`. Works today with zero setup, but it's verbose and relies on the model finding and following the skill each run — not guaranteed.

The **submodule** approach keeps Option A's auto-load and short trigger while preserving a single source of truth: the skill repo stays canonical, and this repo pins a specific commit of it that's bumped deliberately (see Maintenance).

## Step 1 — restructure each skill repo

Make `SKILL.md` + `references/` canonical at the repo root. Keep the Claude plugin path, but symlink it back up to the root files.

Example for `dogi/merge-prepping`:

```text
dogi/merge-prepping/
├── SKILL.md                                    ← canonical (source of truth)
├── references/
│   └── title-corpus.md                         ← canonical
├── .claude-plugin/marketplace.json
└── plugins/merge-prepping/
    ├── .claude-plugin/plugin.json
    └── skills/prepping/
        ├── SKILL.md          → symlink → ../../../../SKILL.md
        └── references/
            └── title-corpus.md → symlink → ../../../../../references/title-corpus.md
```

Commands (run inside the `dogi/merge-prepping` checkout):

```bash
# move the skill files to the repo root
mkdir references
git mv plugins/merge-prepping/skills/prepping/SKILL.md SKILL.md
git mv plugins/merge-prepping/skills/prepping/references/title-corpus.md references/title-corpus.md

# recreate the plugin paths as symlinks pointing back up
ln -s ../../../../SKILL.md plugins/merge-prepping/skills/prepping/SKILL.md
mkdir -p plugins/merge-prepping/skills/prepping/references
ln -s ../../../../../references/title-corpus.md plugins/merge-prepping/skills/prepping/references/title-corpus.md

git add -A && git commit -m "restructure: canonical SKILL.md at root, plugin path symlinked"
git push
```

Symlink depth check: the `SKILL.md` link lives in `plugins/merge-prepping/skills/prepping/`, four directories below the root, so its target climbs four levels (`../../../../SKILL.md`); the corpus link lives one directory deeper (`…/prepping/references/`), so it climbs five (`../../../../../references/…`). Verify with `ls -L` after creating them (see Troubleshooting).

Repeat the same pattern for `dogi/kotlin-importing`, adjusting for its actual layout: the skill lives at `plugins/kotlin-importing/skills/importing/` and ships a script (`kotlin-importing.py`) instead of a `references/` directory — move both `SKILL.md` and the script to the repo root and symlink both back from the plugin path (same four-level climb: `../../../../SKILL.md`, `../../../../kotlin-importing.py`).

## Step 2 — add submodules to myPlanet

```bash
cd myplanet/
mkdir -p .agents/skills
git submodule add https://github.com/dogi/merge-prepping.git .agents/skills/merge-prepping
git submodule add https://github.com/dogi/kotlin-importing.git .agents/skills/kotlin-importing
git add .gitmodules .agents/skills
git commit -m "add merge-prepping and kotlin-importing skill submodules"
```

After cloning this repo, anyone gets the skills with:

```bash
git clone --recurse-submodules <myplanet-url>
# or, on an existing clone:
git submodule update --init --recursive
```

## Step 3 — write AGENTS.md (OpenHands memory)

OpenHands auto-loads `AGENTS.md` on every session. Put cross-references there so the model knows where everything lives.

```markdown
# OpenHands memory — myPlanet

## Skills (auto-loaded from .agents/skills/)
- **merge-prepping** — rewrite PR titles into house style; source: https://github.com/dogi/merge-prepping
- **kotlin-importing** — sort/clean Kotlin imports; source: https://github.com/dogi/kotlin-importing

## Reference docs
- CLAUDE.md — full codebase guide (architecture, build, conventions)
- docs/DOMAIN_MODEL.md — learning domain: roles, courses, teams, surveys, sync
- docs/CODE_STYLE_GUIDE.md — naming, imports, coroutines, Room, Hilt, UI
- docs/TESTING.md — test patterns per layer
- docs/AGENT_SPELLBOOK.md — summoning AI agents on PRs
```

## Step 4 — .claude/settings.json (already correct, no change)

The existing file already fetches both plugins:

```json
{
  "extraKnownMarketplaces": {
    "importing": { "source": { "source": "github", "repo": "dogi/kotlin-importing" } },
    "prepping": { "source": { "source": "github", "repo": "dogi/merge-prepping" } }
  },
  "enabledPlugins": {
    "kotlin-importing@importing": true,
    "merge-prepping@prepping": true
  }
}
```

Claude Code fetches the marketplaces at session start and follows the internal symlinks (now pointing to the root `SKILL.md`). No edit needed after Step 1.

## Step 5 — point Copilot at the skills

Copilot's coding agent reads `.github/copilot-instructions.md` plus the nearest `AGENTS.md` — and a real `AGENTS.md` at the root **replaces** `CLAUDE.md` for it (see `docs/AGENT_SPELLBOOK.md` → Cross-cutting facts). Since this setup makes `AGENTS.md` a real file (OpenHands memory), `.github/copilot-instructions.md` restores the pointer: read `CLAUDE.md` first, then lists the skills and the submodule-init command.

## Step 6 — bootstrap submodules before OpenHands discovery

Skill discovery runs at session start — before the agent reads `AGENTS.md` — so an init command documented there comes too late: on a fresh checkout the gitlinks are empty directories and no `SKILL.md` is found. OpenHands runs `.openhands/setup.sh` at workspace initialization, ahead of discovery, so that script does the init:

```bash
#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")/.." || {
  echo "setup.sh: cannot locate workspace root" >&2
  exit 1
}
git submodule update --init --recursive ||
  echo "setup.sh: submodule init failed (offline?) — continuing without uninitialized skills" >&2
exit 0
```

Without this file the short trigger still fails on fresh sessions even though everything else is wired correctly; the `AGENTS.md` command only helps mid-session or in other contexts. The init failure is deliberately non-fatal: with no network at workspace start, already-initialized submodules keep working and uninitialized ones simply don't load that session, instead of the whole setup script failing the workspace.

## Result — what each system sees

| System | Reads | Loads skill from | Trigger |
|---|---|---|---|
| Claude Code | `.claude/settings.json` | GitHub fetch + plugin symlinks | `/merge-prepping:prepping` |
| OpenHands | `.agents/skills/<name>/SKILL.md` + `AGENTS.md` | submodule, initialized by `.openhands/setup.sh` before discovery | `@openhands prep this PR` (skill description handles trigger phrases) |
| Copilot (coding agent) | `.github/copilot-instructions.md` + `AGENTS.md` | submodule, after `git submodule update --init` | `@copilot <ask>` |

## CI and default-checkout implications

Submodules are **not** fetched by default: a plain `git clone`, and `actions/checkout` without a `submodules:` setting, leave `.agents/skills/*` as empty directories (`git submodule status` shows a `-` prefix on the commit). None of the current workflows (`build.yml`, `test.yml`, `release.yml`, `automerge.yml`) need the skills, so CI is intentionally left untouched — the app build must not pay for a skill fetch.

If a future workflow or agent bootstrap step expects `.agents/skills/**` to exist, it must opt in explicitly, either:

```yaml
- uses: actions/checkout@v7
  with:
    submodules: recursive
```

or by running `git submodule update --init --recursive` as a step. Fresh human clones should use `git clone --recurse-submodules` (documented in the README's Development section).

## Maintenance

- **Edit the skill once** — change `SKILL.md` or `references/title-corpus.md` in `dogi/merge-prepping`.
- **Bump the submodule** in myPlanet when you want to pick up the change. Note the submodule checkout is on a detached HEAD, so a plain `git pull` inside it has no branch to update; use the submodule-safe form:

  ```bash
  git submodule update --remote --merge -- .agents/skills/merge-prepping
  git add .agents/skills/merge-prepping
  git commit -m "bump merge-prepping skill submodule"
  ```

- **Same for `kotlin-importing`** — edit `SKILL.md` or `kotlin-importing.py` in `dogi/kotlin-importing`, then:

  ```bash
  git submodule update --remote --merge -- .agents/skills/kotlin-importing
  git add .agents/skills/kotlin-importing
  git commit -m "bump kotlin-importing skill submodule"
  ```

- **Regenerate the corpus** periodically from the myPlanet log:

  ```bash
  git log --format=%s master > /tmp/titles.txt
  # drop into references/title-corpus.md in dogi/merge-prepping, commit, bump submodule
  ```

## Troubleshooting

**OpenHands doesn't find the skill** — confirm the submodule is initialized and `SKILL.md` is at `.agents/skills/merge-prepping/SKILL.md` (not nested deeper):

```bash
ls .agents/skills/merge-prepping/SKILL.md
git submodule status
```

If the path is nested (e.g. `.agents/skills/merge-prepping/plugins/.../SKILL.md`), Step 1 wasn't applied — the root restructure is required.

**File exists but the skill is still "unknown"** — suspect the frontmatter, not the path, and check it in this order:

1. **Is the YAML valid at all?** One-line repro: `python3 -c "import yaml; yaml.safe_load(open('SKILL.md').read().split('---')[1])"`. An **unquoted `description:` containing `: ` (colon-space)** — e.g. an inline example like `` `scope: smoother thing doing` `` — is a YAML mapping indicator and kills the whole parse (`mapping values are not allowed here`), silently dropping the skill. Quote the value (single quotes; double any internal apostrophes). Field-tested 2026-08-09: this was the real reason `prepping` failed to load even after its rename.
2. **Is the `name:` too generic?** Discovery also refused `name: title` (the repo's original name) while `sort` loaded. Both skills now use specific names (`prepping`, `importing`) — keep the trigger phrases in `description`.

**No skills load on a fresh session** — check network: `.openhands/setup.sh` fetches the submodules from GitHub at workspace start. The script is deliberately non-fatal on failure (see Step 6), so an offline start leaves previously-initialized submodules working; run `git submodule update --init --recursive` manually once connectivity is back.

**Claude Code slash command missing** — the internal symlinks must resolve within the fetched repo. Verify in a fresh clone of `dogi/merge-prepping`:

```bash
git clone https://github.com/dogi/merge-prepping.git /tmp/merge-prepping
ls -L /tmp/merge-prepping/plugins/merge-prepping/skills/prepping/SKILL.md
cat /tmp/merge-prepping/plugins/merge-prepping/skills/prepping/SKILL.md
```

`-L` follows the symlink; `cat` should print the root `SKILL.md` content.

**Symlinks broken after a rebase** — git tracks symlinks as mode 120000 with the target path as content. A rebase that rewrites history can occasionally materialize them as regular files. Recreate with `ln -s` if `ls -l` shows a plain file instead of `->`.
