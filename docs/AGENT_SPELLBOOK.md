# The Agent Spellbook — Summoning Other AIs on PRs

> **Reviewers** speak; **Doers** act — an unleashed Doer mention defaults to commits on your branch. That's the one distinction to hold before casting anything.
>
> This page has two layers. **The Grid** is the timeless mechanics: how each agent is summoned, what it does, and which file binds it — portable to any repo these agents are installed on. **Field notes** are dated observations with receipts, mostly from the live multi-agent experiment on [PR #15436](https://github.com/open-learning-exchange/myplanet/pull/15436) (2026-08-07/08), where each agent also fact-checked its own row. New evidence goes in the notes; the grid changes only when behavior does. The behavioral rules — the **Laws of Summoning** — live in `CLAUDE.md` → "The Agent Spellbook", so the agents that ingest that file carry them as standing instructions. A third layer, **The Armory** (end of page), is the plumbing that arms the doers: how the shared skills are maintained once in their own repos and synced into Claude Code, OpenHands, and Copilot.

## The Grid

| Agent (identity) | Type | Summon | Pushes to your branch? | Standing instructions |
|---|---|---|---|---|
| **CodeRabbit** (`coderabbitai[bot]`) | Reviewer | auto on push (skips drafts & dependabot; auto-pauses after 5 reviewed commits) · `@coderabbitai review` / `full review` / `resolve` / ⛔`approve` (needs `request_changes_workflow`, off here) / `fix ci [commit]` / `autofix [stacked pr]` / `resolve merge conflict` / `generate {docstrings, unit tests, sequence diagram}` / `configuration` / `pause`·`resume` / `help` — `ignore` only works in the PR **description**. Answers in minutes; chats in threads | only opt-in (`fix ci commit`, `autofix`, generators) | teachable per-repo **learnings** from PR discussion; config via `.coderabbit.yaml` — quiet-by-default here, and ingests `CLAUDE.md` as code guidelines |
| **Codex** (`chatgpt-codex-connector[bot]`) | Reviewer (+ cloud tasks) | `@codex review` · targeted: `@codex review for issues in <scope>` — 👀 ack in seconds, review in minutes, 👍 if clean. Auto-review on open/ready only if enabled in repo Codex settings | no — `@codex fix it` / `address that feedback` starts a cloud task that may update the PR **or** deliver a sibling branch/PR | `## Code Review Rules` in `AGENTS.md`; `codex` label, `*-codex/*` branches |
| **Copilot** (`Copilot` / `copilot-swe-agent`; Reviewers-UI reviews author as `copilot-pull-request-reviewer[bot]`) | Doer, instruction-following | `@copilot <ask>` (write-access users only) · Reviewers UI (Comment-only reviews — never approves, no auto re-review) · assign an issue (spawns its own `copilot/**` PR). Acks in ~30 s | **default** — mentions work on any PR and push to that branch; say "open a separate PR" to redirect | coding agent: `.github/copilot-instructions.md` + nearest `AGENTS.md`, with a root `CLAUDE.md`/`GEMINI.md` as the alternative (an `AGENTS.md` takes precedence and unbinds `CLAUDE.md`); code review: `AGENTS.md` but not `CLAUDE.md` |
| **Devin** (`devin-ai-integration[bot]`) | Doer, instruction-following | `@devin <ask>` — session link in ~10 s; one session **adopts** the PR, later mentions join it (no races from Devin). Reviews: the bare literal **`@devin review`** triggers Devin Review in ~1 min — verbose review asks (`@devin please review — comment only…`) go **silent**, and "additional findings" are gated behind its web UI | yes, unless leashed in the mention | Knowledge ingests `CLAUDE.md`/`AGENTS.md`; ⚠️ commit identity is a configurable "commit authoring mode" — audit via PR timeline, not `git log` |
| **OpenHands** (`openhands-ai[bot]`) | Doer, unleashable | `@openhands <ask>` · `openhands` label on an issue — "I'm on it!" + session link in ~10 s; **new session per mention**, and *any* mention (even "help") reads as "fix what's open" | yes — leash compliance is **mixed**: broke an explicit no-push once (2026-08-07), honored "comment only" three times running (2026-08-09); see field notes | root `AGENTS.md` (auto-loaded memory) + skills from `.agents/skills/*/SKILL.md` (submodules, bootstrapped by `.openhands/setup.sh` — see "The Armory" below); `.openhands/microagents/repo.md` also supported (unused here). All prompt-level, not a guardrail; commits authored `openhands` |
| **Jules** (`google-labs-jules[bot]`) | Issue-driven Doer | `jules` label on an issue (reliable) / Jules app. PR feedback only on **Jules-created PRs** via a submitted review (👀 ack per comment, then pushes fixes); acts on every review comment by default — opt-in Reactive Mode **narrows** that to explicit `@Jules` mentions. Mentions on foreign PRs go silent (no session owns the branch) | own `jules-*` PRs only — including follow-up commits there on accepted review feedback | `AGENTS.md` + per-repo memory (no `CLAUDE.md` support); configurable commit-authoring modes — audit via PR timeline; quotas 15/100/300 tasks/day by plan |
| **Claude Code** (`claude[bot]` for reviews) | Session Doer + managed Reviewer | claude.ai/code session · ⛔ `@claude review` is **turned OFF for this repo — do not summon** (works technically via the claude.ai-managed Code Review; billing killed it — see field notes) | review: no, comments only; sessions: their own `claude/**` branch (session-ID suffix; first push with `git push -u`) | sessions read `CLAUDE.md`; can subscribe to PR events and drive to green; review model is server-side and undocumented, not admin-configurable |
| **Dependabot** (`dependabot[bot]`) | Scheduled | daily runs · `@dependabot rebase` / `recreate` / `ignore …` / `unignore …` / `show … ignore conditions` — on **its own PRs only** | own PRs | `.github/dependabot.yml` (Actions + Gradle bumps) |

## Field notes

Dated observations with receipts — the evidence behind the grid. Append here; retest and retire what goes stale. (Adopting this spellbook in another repo? Take the grid, the laws, and the vendor links — then grow your own notes.)

- **Devin** — 2026-08-09: on [PR #15499](https://github.com/open-learning-exchange/myplanet/pull/15499), two verbose review asks (`@devin please review — comment only, focus on…`) got **no response at all**; the bare literal `@devin review` answered within ~1 min — via **Devin Review**, its automated reviewer, not a session ("No Issues Found", with "3 additional findings" visible only at app.devin.ai). Treat `@devin review` as a fixed command: leashes and focus instructions in the trigger comment suppress it rather than steer it. Later the same day, four consecutive mentions (`review` ×2, `?` ×2) produced only two "Failed to start a Devin session. Please try again." replies — an outage presents as silence or that error; retry later instead of rephrasing.
- **All mention-summoned agents** — 2026-08-09: ⚠️ **backticks don't defuse a mention.** Writing `` `@openhands prepping` `` inside a code span in an ordinary status comment on PR #15499 spawned a real (unleashed!) OpenHands session. GitHub notifies on the handle regardless of code formatting. When *talking about* an agent, drop the `@` (write "openhands prepping") — only use the live handle when you mean to summon.
- **OpenHands** — 2026-08-09: honored "comment only — do not push" on **three consecutive summons** on PR #15499 (softening the 2026-08-07 leash-break note — mixed, not hopeless), and was the only reviewer to live-test the target environment: it caught the same skill silently dropped by discovery **twice for different reasons** — first a too-generic `name: title`, then (after the rename) invalid YAML from an unquoted `description:` containing `scope:`. Ask it to *verify by running*, not just review — that's its edge. Still double-posts under the mentioning user's account and the bot.
- **CodeRabbit** — 2026-08-09: on PR #15499's push cadence the free-OSS rolling limit produced rate-limit banners most of the afternoon; `@coderabbitai review` while limited only re-queues, and against an already-reviewed commit replies "Already reviewed" (the command forces a review only when auto-reviews are paused). "✅ Addressed in commit X" self-marks tracked every fix reliably. Shellcheck-sourced findings (e.g. SC2164) arrive tagged "Source: Linters/SAST tools".
- **CodeRabbit** — 2026-08-08: free-OSS review allowance is real and bites under load (5/dev/hr rolling; Pro+ 10) — it queued "next review in N minutes" banners through a whole afternoon of pushes. Auto-pauses after 5 reviewed commits on a fast-moving branch (`auto_review.auto_pause_after_reviewed_commits`). A review aborts if the head moves mid-review. `approve` needs `reviews.request_changes_workflow: true`. Stored a learning from PR #15436 discussion ("reference-test doc examples must track the real test; update the test first") — the learnings feature works as advertised. Withdrew two findings gracefully when shown they were moot. The grid's quiet-by-default / `approve`-off facts date from PR #15448's `.coderabbit.yaml`. Untested there: rate-limit banners under `review_status: false`; the Pro+-gated `fix ci commit` / `resolve merge conflict` on the free OSS tier.
- **Codex** — 2026-08-07/08: P2/P3 badges observed on GitHub reviews, though docs claim only P0/P1 get flagged there. Targeted scope (`review for issues in <file>`) honored precisely. Caught a `lateinit` crash hazard *after* another reviewer had approved the commit, and later three factual errors in this very document — the strongest single-finding reviewer of the experiment.
- **Copilot** — 2026-08-07: a formal Reviewers-UI review request produced **no review at all**; mention-asks answered in ~30 s throughout. Honored an explicit "comment only — do not push" leash. 2026-08-08: answered a first-party question about its own instruction files, confirming the `CLAUDE.md`-as-alternative rule with a docs link — cross-confirmed independently by Devin the same day.
- **Devin** — 2026-08-07/08: honored "comment only" leashes on every ask. Its commits wore the requesting user's git identity (commit-authoring mode) — the PR timeline was the only reliable attribution. Disclosed its environment gaps unprompted ("had to install the Android SDK first") — the model for showing receipts. Ran two doc-verification research tasks and delivered link-backed verdicts, including catching its own row's overreach.
- **OpenHands** — 2026-08-07: pushed to the branch **against an explicit no-push instruction** (the only leash break of the experiment); 4+ concurrent sessions from separate mentions; double-posts under both the mentioning user's account and the bot; disputed its own receipts-documented behavior in self-review. Trust the timeline over its self-description.
- **Jules** — 2026-08-07: `@jules` PR mentions got no response — explained 2026-08-08 by vendor docs: PR feedback is scoped to sessions that own the PR, and this was a foreign branch. Reactive Mode (2025-09 changelog) narrows replies to `@Jules` mentions rather than enabling them. `jules` label on an issue remains the reliable summon (20+ bot-authored PRs in this repo's history).
- **Claude Code** — 2026-08-08: ⛔ **`@claude review` retired after one test.** The managed Code Review bills $15–25/review against org overage credits (non-refundable, separate from plan usage). The single live summon consumed the org's entire $20 monthly cap ($22.60 — the cap doesn't stop a mid-process review) and delivered **zero findings**: one "review skipped" reply at the spend limit, then a capacity-limited run posting only a "review may be incomplete" warning. At this repo's review volume (~123 PRs/day) that's ~$1,845–3,075 per day. CodeRabbit and Codex cover the same ground free — the doc policy here is the explanation; the enforcement is the spend cap / repo setting at claude.ai/admin-settings/claude-code.
- **Dependabot** — 2026-08-08: current dotcom command list verified — `rebase`, `recreate`, the `ignore`/`unignore` family, `show … ignore conditions`; the old merge commands are gone.

## Vendor grimoires

Official references; the grid and field notes record observed behavior where the two differ.

- CodeRabbit: [review commands](https://docs.coderabbit.ai/reference/review-commands) · [configuration](https://docs.coderabbit.ai/reference/configuration) · [learnings](https://docs.coderabbit.ai/knowledge-base/learnings) — `@coderabbitai configuration` prints this repo's live config
- Codex: [GitHub integration](https://learn.chatgpt.com/docs/third-party/github)
- Copilot: [code review](https://docs.github.com/en/copilot/concepts/agents/code-review) · [changing existing PRs](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/cloud-agent/make-changes-to-an-existing-pr) · [instruction-file support matrix](https://docs.github.com/en/copilot/reference/custom-instructions-support)
- Devin: [GitHub integration](https://docs.devin.ai/integrations/gh) (commit authoring modes) · [Knowledge](https://docs.devin.ai/product-guides/knowledge)
- OpenHands: [GitHub cloud](https://docs.openhands.dev/openhands/usage/cloud/github-installation) · [repo microagents](https://docs.openhands.dev/modules/usage/prompting/microagents-repo)
- Jules: [running tasks](https://jules.google/docs/running-tasks/) · [usage limits](https://jules.google/docs/usage-limits/) · [changelog](https://jules.google/docs/changelog/)
- Claude Code: [Code Review](https://code.claude.com/docs/en/code-review) (managed; admin settings at claude.ai/admin-settings/claude-code)
- Dependabot: [comment commands](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-pull-request-comment-commands)

## Cross-cutting facts

- **No vendor documents a hard "never push" switch** — every standing-rule mechanism (microagents, Knowledge, `AGENTS.md`) is prompt-level context, and Law 1 (scope your summons) is best-effort, not enforcement: the OpenHands field note records a prompt leash failing. The enforceable controls are GitHub-side — branch protection/rulesets and app/repository write permissions.
- **Copilot and Devin ingest `CLAUDE.md` directly** (Copilot's coding agent lists it first-party; Devin via Knowledge; CodeRabbit reads it too since PR #15448, as review guidelines via `knowledge_base.code_guidelines` in `.coderabbit.yaml`) — the Laws of Summoning bind those two simply by being merged. Two per-feature caveats: Copilot **code review** reads `AGENTS.md` but not `CLAUDE.md` (it still reads `.github/copilot-instructions.md` and path instructions), and for the coding agent the nearest `AGENTS.md` **takes precedence** over a root `CLAUDE.md`. Outside github.com the support matrix narrows for agent-instruction files specifically: VS Code Chat still reads `.github/copilot-instructions.md` and path-specific instructions, but `AGENTS.md` is the only *agent-instruction* filename it honors (no `CLAUDE.md`/`GEMINI.md`); IDE code review reads no agent-instruction file at all. Jules reads only `AGENTS.md` — which this repo **now has** as a real file (added 2026-08-09 with the skill-sync setup: OpenHands memory + skill pointers, see "The Armory" below), so Jules and Copilot review are bound by it. The cost of a real (non-symlink) `AGENTS.md` is that it silently replaces `CLAUDE.md` for Copilot's coding agent — `.github/copilot-instructions.md` compensates by pointing Copilot back at `CLAUDE.md` first. (The earlier recommendation here — symlink `AGENTS.md` → `CLAUDE.md` — was superseded by that file-plus-pointer arrangement.)

## The Armory — skill sync (one repo, every agent)

Each skill (`merge-prepping`, `kotlin-importing`) is maintained **once** in its own repo, and Claude Code, OpenHands, and Copilot all load the same files on every session in this repo: OpenHands auto-loads from `.agents/skills/<name>/SKILL.md` (submodules bootstrapped by `.openhands/setup.sh` before discovery), Claude Code loads the plugins through the marketplaces, and Copilot picks the skills up via its instruction file.

### Why this works

- **Claude Code** reads `.claude/settings.json` → fetches plugin marketplaces from GitHub → follows internal symlinks to find `SKILL.md`.
- **OpenHands** reads `.agents/skills/<name>/SKILL.md` → auto-loads on every session. It does **not** read `.claude/settings.json` or fetch marketplaces.
- A **git submodule** at `.agents/skills/<name>/` makes the files physically present after `git submodule update --init`, on any machine or VM.
- **Internal symlinks** (inside the skill repo) resolve on every checkout — no broken-link problem.

### Alternatives considered (and why the submodule won)

Brainstormed with OpenHands; two simpler options were on the table:

- **Option A — copy the skill into this repo** as plain files under `.agents/skills/merge-prepping/`. OpenHands would auto-load it, and the trigger comment shrinks to `@openhands prep this PR` (or even `@openhands fix the title` — the skill description lists the trigger phrases). But the copy forks the source of truth: every edit to `dogi/merge-prepping` must be manually re-copied here, and the two versions silently drift.
- **Option B — no install, explicit comment each time**: `@openhands clone https://github.com/dogi/merge-prepping, read plugins/merge-prepping/skills/prepping/SKILL.md, and apply the title procedure to this PR`. Works today with zero setup, but it's verbose and relies on the model finding and following the skill each run — not guaranteed.

The **submodule** approach keeps Option A's auto-load and short trigger while preserving a single source of truth: the skill repo stays canonical, and this repo pins a specific commit of it that's bumped deliberately (see Maintenance).

The payoff, side by side — what Option B required per invocation:

> `@openhands clone https://github.com/dogi/merge-prepping, read plugins/merge-prepping/skills/prepping/SKILL.md, and apply the title procedure to this PR`

and what the same invocation is now (production-proven on [PR #15499](https://github.com/open-learning-exchange/myplanet/pull/15499) — retitled the PR and created its tracking issue):

> `@openhands prepping`

### Step 1 — restructure each skill repo

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

### Step 2 — add submodules to myPlanet

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

### Step 3 — write AGENTS.md (OpenHands memory)

OpenHands auto-loads `AGENTS.md` on every session. Put cross-references there so the model knows where everything lives.

```markdown
# OpenHands memory — myPlanet

### Skills (auto-loaded from .agents/skills/)
- **merge-prepping** — rewrite PR titles into house style; source: https://github.com/dogi/merge-prepping
- **kotlin-importing** — sort/clean Kotlin imports; source: https://github.com/dogi/kotlin-importing

### Reference docs
- CLAUDE.md — full codebase guide (architecture, build, conventions)
- docs/DOMAIN_MODEL.md — learning domain: roles, courses, teams, surveys, sync
- docs/CODE_STYLE_GUIDE.md — naming, imports, coroutines, Room, Hilt, UI
- docs/TESTING.md — test patterns per layer
- docs/AGENT_SPELLBOOK.md — summoning AI agents on PRs + "The Armory": how the shared skills are wired up and maintained
```

### Step 4 — .claude/settings.json (already correct, no change)

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

### Step 5 — point Copilot at the skills

Copilot's coding agent reads `.github/copilot-instructions.md` plus the nearest `AGENTS.md` — and a real `AGENTS.md` at the root **replaces** `CLAUDE.md` for it (see Cross-cutting facts above). Since this setup makes `AGENTS.md` a real file (OpenHands memory), `.github/copilot-instructions.md` restores the pointer: read `CLAUDE.md` first, then lists the skills and the submodule-init command.

### Step 6 — bootstrap submodules before OpenHands discovery

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

### Result — what each system sees

| System | Reads | Loads skill from | Trigger |
|---|---|---|---|
| Claude Code | `.claude/settings.json` | GitHub fetch + plugin symlinks | `/merge-prepping:prepping` |
| OpenHands | `.agents/skills/<name>/SKILL.md` + `AGENTS.md` | submodule, initialized by `.openhands/setup.sh` before discovery | `@openhands prepping` (bare skill name — production-proven on PR #15499) or any trigger phrase from the description (`prep this PR`, `fix the title`) |
| Copilot (coding agent) | `.github/copilot-instructions.md` + `AGENTS.md` | submodule, after `git submodule update --init` | `@copilot <ask>` |

### CI and default-checkout implications

Submodules are **not** fetched by default: a plain `git clone`, and `actions/checkout` without a `submodules:` setting, leave `.agents/skills/*` as empty directories (`git submodule status` shows a `-` prefix on the commit). None of the current workflows (`build.yml`, `test.yml`, `release.yml`, `automerge.yml`) need the skills, so CI is intentionally left untouched — the app build must not pay for a skill fetch.

If a future workflow or agent bootstrap step expects `.agents/skills/**` to exist, it must opt in explicitly, either:

```yaml
- uses: actions/checkout@v7
  with:
    submodules: recursive
```

or by running `git submodule update --init --recursive` as a step. Fresh human clones should use `git clone --recurse-submodules` (documented in the README's Development section).

### Maintenance

- **Edit the skill once** — change `SKILL.md` or `references/title-corpus.md` in `dogi/merge-prepping`.
- **Bump the submodule** in myPlanet when you want to pick up the change. Two pitfalls make the obvious commands wrong: the submodule checkout is on a detached HEAD, so a plain `git pull` inside it has no branch to update; and `--remote --merge` would *merge* the remote tip into that detached HEAD — if the pin has diverged (e.g. after an upstream force-push, which has happened to these repos), that mints a local merge commit that doesn't exist upstream, and committing its gitlink breaks every fresh clone. Use checkout mode, which pins the remote branch tip directly:

  ```bash
  git submodule update --remote -- .agents/skills/merge-prepping
  git add .agents/skills/merge-prepping
  git commit -m "bump merge-prepping skill submodule"
  ```

- **Same for `kotlin-importing`** — edit `SKILL.md` or `kotlin-importing.py` in `dogi/kotlin-importing`, then:

  ```bash
  git submodule update --remote -- .agents/skills/kotlin-importing
  git add .agents/skills/kotlin-importing
  git commit -m "bump kotlin-importing skill submodule"
  ```

- **Regenerate the corpus** periodically from the myPlanet log (use `origin/master` — a local `master` branch doesn't exist on PR-branch checkouts):

  ```bash
  git log --format=%s origin/master > /tmp/titles.txt
  # drop into references/title-corpus.md in dogi/merge-prepping, commit, bump submodule
  ```

### Troubleshooting

**OpenHands doesn't find the skill** — confirm the submodule is initialized and `SKILL.md` is at `.agents/skills/merge-prepping/SKILL.md` (not nested deeper):

```bash
ls .agents/skills/merge-prepping/SKILL.md
git submodule status
```

If the path is nested (e.g. `.agents/skills/merge-prepping/plugins/.../SKILL.md`), Step 1 wasn't applied — the root restructure is required.

**File exists but the skill is still "unknown"** — suspect the frontmatter, not the path, and check it in this order:

1. **Is the YAML valid at all?** One-line repro (needs PyYAML, not in the stdlib — `python3 -m pip install pyyaml` first if missing): `python3 -c "import yaml; yaml.safe_load(open('SKILL.md').read().split('---')[1])"`. An **unquoted `description:` containing a colon-space** — e.g. quoting the house-style pattern `scope: smoother thing doing` verbatim — is a YAML mapping indicator and kills the whole parse (`mapping values are not allowed here`), silently dropping the skill. Quote the value (single quotes; double any internal apostrophes). Field-tested 2026-08-09: this was the real reason `prepping` failed to load even after its rename.
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

**Symlink looks fine but doesn't resolve** — the git blob *content* can be malformed even when `ls -l` shows a plausible `->` target: a trailing newline in the stored target (typically from generating the link with `echo target > link` instead of `ln -s`) makes it point at a filename ending in an invisible `\n`. Repro: `readlink <link> | od -c` (or upstream, `git cat-file -p :<path> | od -c`) — the stored target must not end in `\n`. Recreate with `ln -s`; never write link targets via echo/printf. Field-tested 2026-08-09: `kotlin-importing.py`'s plugin symlink shipped with exactly this defect while its sibling `SKILL.md` link was clean.
