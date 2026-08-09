# Skill sync setup — Claude Code + OpenHands

Goal: maintain `merge-prepping` (and `kotlin-importing`) **once** in their own repos, and have both Claude Code and OpenHands auto-load the same skill on every session in this repo.

## Rollout status

- [x] **Step 1** — `dogi/merge-prepping` and `dogi/kotlin-importing` restructured with root `SKILL.md` (plugin paths symlinked back)
- [x] **Step 2** — submodules added at `.agents/skills/` (pinned at `merge-prepping@dad667f`, `kotlin-importing@0943989`)
- [x] **Step 3** — `AGENTS.md` written at the repo root
- [x] **Step 4** — `.claude/settings.json` already correct, no change needed

All four steps are live: OpenHands auto-loads from `.agents/skills/<name>/SKILL.md` (after `git submodule update --init`), and Claude Code keeps loading the plugins through the marketplaces.

## Why this works

- **Claude Code** reads `.claude/settings.json` → fetches plugin marketplaces from GitHub → follows internal symlinks to find `SKILL.md`.
- **OpenHands** reads `.agents/skills/<name>/SKILL.md` → auto-loads on every session. It does **not** read `.claude/settings.json` or fetch marketplaces.
- A **git submodule** at `.agents/skills/<name>/` makes the files physically present after `git submodule update --init`, on any machine or VM.
- **Internal symlinks** (inside the skill repo) resolve on every checkout — no broken-link problem.

## Alternatives considered (and why the submodule won)

Brainstormed with OpenHands; two simpler options were on the table:

- **Option A — copy the skill into this repo** as plain files under `.agents/skills/merge-prepping/`. OpenHands would auto-load it, and the trigger comment shrinks to `@openhands prep this PR` (or even `@openhands fix the title` — the skill description lists the trigger phrases). But the copy forks the source of truth: every edit to `dogi/merge-prepping` must be manually re-copied here, and the two versions silently drift.
- **Option B — no install, explicit comment each time**: `@openhands clone https://github.com/dogi/merge-prepping, read plugins/merge-prepping/skills/title/SKILL.md, and apply the title procedure to this PR`. Works today with zero setup, but it's verbose and relies on the model finding and following the skill each run — not guaranteed.

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
    └── skills/title/
        ├── SKILL.md          → symlink → ../../../../SKILL.md
        └── references/
            └── title-corpus.md → symlink → ../../../../../references/title-corpus.md
```

Commands (run inside the `dogi/merge-prepping` checkout):

```bash
# move the skill files to the repo root
mkdir references
git mv plugins/merge-prepping/skills/title/SKILL.md SKILL.md
git mv plugins/merge-prepping/skills/title/references/title-corpus.md references/title-corpus.md

# recreate the plugin paths as symlinks pointing back up
ln -s ../../../../SKILL.md plugins/merge-prepping/skills/title/SKILL.md
mkdir -p plugins/merge-prepping/skills/title/references
ln -s ../../../../../references/title-corpus.md plugins/merge-prepping/skills/title/references/title-corpus.md

git add -A && git commit -m "restructure: canonical SKILL.md at root, plugin path symlinked"
git push
```

Symlink depth check: the `SKILL.md` link lives in `plugins/merge-prepping/skills/title/`, four directories below the root, so its target climbs four levels (`../../../../SKILL.md`); the corpus link lives one directory deeper (`…/title/references/`), so it climbs five (`../../../../../references/…`). Verify with `ls -L` after creating them (see Troubleshooting).

Repeat the same pattern for `dogi/kotlin-importing`, adjusting for its actual layout: the skill lives at `plugins/kotlin-importing/skills/sort/` and ships a script (`kotlin-importing.py`) instead of a `references/` directory — move both `SKILL.md` and the script to the repo root and symlink both back from the plugin path (same four-level climb: `../../../../SKILL.md`, `../../../../kotlin-importing.py`).

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

## Result — what each system sees

| System | Reads | Loads skill from | Trigger |
|---|---|---|---|
| Claude Code | `.claude/settings.json` | GitHub fetch + plugin symlinks | `/merge-prepping:title` |
| OpenHands | `.agents/skills/<name>/SKILL.md` + `AGENTS.md` | submodule (root `SKILL.md`) | `@openhands prep this PR` (skill description handles trigger phrases) |

## Maintenance

- **Edit the skill once** — change `SKILL.md` or `references/title-corpus.md` in `dogi/merge-prepping`.
- **Bump the submodule** in myPlanet when you want to pick up the change (same procedure for `kotlin-importing` — swap the path). Note the submodule checkout is on a detached HEAD, so a plain `git pull` inside it has no branch to update; use the submodule-safe form:

  ```bash
  git submodule update --remote --merge -- .agents/skills/merge-prepping
  git add .agents/skills/merge-prepping
  git commit -m "bump merge-prepping skill submodule"
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

**Claude Code slash command missing** — the internal symlinks must resolve within the fetched repo. Verify in a fresh clone of `dogi/merge-prepping`:

```bash
git clone https://github.com/dogi/merge-prepping.git /tmp/merge-prepping
ls -L /tmp/merge-prepping/plugins/merge-prepping/skills/title/SKILL.md
cat /tmp/merge-prepping/plugins/merge-prepping/skills/title/SKILL.md
```

`-L` follows the symlink; `cat` should print the root `SKILL.md` content.

**Symlinks broken after a rebase** — git tracks symlinks as mode 120000 with the target path as content. A rebase that rewrites history can occasionally materialize them as regular files. Recreate with `ln -s` if `ls -l` shows a plain file instead of `->`.
