# TODO — the refactor-drain run (2026-08-27/28)

What a two-day run of the dual-lane refactor workflow taught us, written down so the next
round doesn't relearn it. Three consumers: the **spellbook / `dogi/agents-summoning` skill**
(§1), the **kickoff prompt** for the next drain (§2), and the **agent APIs** we reverse-engineered
when the GitHub-comment channel ran out of road (§3). §4 is what's still unknown.

Everything here is from observed behavior with receipts. Where a claim is inferred rather than
measured, it says so.

---

## 1. Spellbook corrections

Facts that contradict or extend `docs/AGENT_SPELLBOOK.md`. Fold these into the grid and field
notes; several are load-bearing.

### Mentions

- **A mention added by *editing* a comment does not fire.** GitHub only notifies on the original
  body. On PR #16339, `@openhands` appended in an edit 23 s after posting produced silence;
  the same handle in a fresh comment acked in 20 s. This is invisible in the rendered page —
  the comment looks identical either way. Compose the mention in the first draft or not at all.
- **`@openhands-agent` is not a handle.** Only `@openhands`. A comment using it got no response
  and no reaction.
- **Jules *does* respond to a plain PR comment**, not only to a submitted review as the grid
  claims. On #16311 a quoted review + `@jules` in an ordinary issue comment drew a 👀 and a
  push within 4½ minutes.

### Jules

- **Second failure wording, not in the spellbook:**
  `"I apologize, but I encountered an unexpected error and wasn't able to complete the task."`
  Unlike `"Jules has failed to create a task…"` it does **not** tell you to re-add the label, so
  a watcher matching only the documented string misses it. Three issues (#16448, #16450, #16452)
  sat dead on this for three hours. The remove/re-add retry does fix it — all three shipped
  within 26 minutes of being retried.
- **Jules does not answer nudges.** Four stalled issues (#16449, #16498, #16499, #16515) ignored
  `@jules` status comments entirely. All four completed after being relabelled to `openhands`.
- **Jules pushes no-op commits when the ask is a question.** #16311's review ended with a design
  question; six pokes produced **six commits with byte-identical messages and an unchanged diff**.
  It produces motion that looks like compliance. Give Jules a decided instruction or don't poke it.

### OpenHands

- **Answers nudges reliably.** All five stalled issues replied to an `@openhands` status comment
  within four minutes; two shipped PRs immediately.
- **Takes over another agent's PR.** #16311 (a Jules branch, six no-ops deep) was handed to
  OpenHands with the design question restated as a decision to make. One session, one push,
  correct answer with its own reconstruction of the failure path.
- **Reverses its own work when the review is right.** The strongest results of the run were
  deletions: #16513 removed the `SyncStatusCoalescer` it had just written after agreeing it was
  net-negative; #16603 rewrote its approach after finding an API-34 crash; #16608 restored search
  ranking it had accidentally dropped. Don't phrase asks so that "produced a commit" is the win
  condition.

### Do the metadata edit yourself after two misses

#16545 took three reviews with one unchanged blocker: the title claimed a `StateFlow` conversion
the diff did not make, and that title becomes the squash-merge commit message. Jules missed it
twice — the second time it renamed a *test* instead of the title. Agents are reliable on code and
unreliable on PR metadata. After the second miss, apply the reviewer's own suggested string
directly with `update_pull_request`, then pipe only what is left, telling the agent explicitly that
the title is done and not to touch it. Piping an unchanged ask a third time buys another no-op.

### Handing a stalled Jules PR to OpenHands

Worked on #16311 and again on #16323/#16324. The handover comment should carry three things:
the review's substance restated as a decision; an instruction to **verify against the current diff
rather than trust the earlier agent's commit messages** (Jules's messages claimed fixes the diff
did not always contain); and any environmental context the new agent could not infer — for
#16323/#16324 that was the dead `push` trigger, plus an explicit ask to *report whether its own
push produces `build`/`test` runs*, since a push from a different actor was the one untested
variable.

### The summon wording that worked

```
@openhands follow up on my review and/or @Okuro3499 s improvement
instructions, then commit and push. If you conclude no change is
warranted, push nothing and say why.
```

The escape-hatch sentence is the load-bearing half — without it you pressure the agent into a
commit even when declining is correct.

---

## 2. Kickoff prompt for the next drain

### Shape

- Two lanes, `jules` and `openhands` labels on issues. Track at **issue level only** — never
  subscribe to or drive PRs; PRs are completion signals.
- `DONE` = the issue has a linked PR. `BUSY` = labelled and open, no linked PR.
- Lane is determined by **which busy list the issue is in**, never by the PR's draft flag —
  the draft split is not reliable (#16389 and #16530 were non-draft OpenHands PRs).
- Self-re-arming `send_later` chain for the check loop (cron has a **1-hour minimum**, so it
  can't do 5-minute cadence), plus an hourly cron watchdog that only verifies the chain is alive.

### Detection — get this right the first time

**Use the issue's own `closed_by_pull_requests` field** (`issue_read` method `get`). It is
authoritative and label-proof.

Do **not** reconstruct completion from a label-filtered PR search. The query inherited from the
saved GitHub URLs excluded `-label:change`, which silently hid four completed issues (#16434,
#16478, #16485, #16500) for hours — about a fifth of the run was invisible to it.

Other traps, each hit at least once:

| Trap | Reality |
|---|---|
| `status:success` / `status:pending` search filters | Wrong. #16480 read `pending` with all five check runs green. Use `pull_request_read` → `get_check_runs`. |
| Green CI on a PR that hasn't pushed | It's green from *yesterday's* commit. Require a **new commit** *and* green runs. |
| `issue_read` method `get_labels` on a PR | Fails: *"Could not resolve to an Issue"*. Get labels via `search_pull_requests` with `fields: ["number","labels"]`. |
| `issue_write` labels | **Replaces** the whole set. Carry every non-target label across verbatim, and **re-fetch each round** — the labels workflow re-runs on every push and changed sizes mid-flight (#16345, #16513 gained `enormous`). |
| Reading only `get_comments` | A formal review (`get_reviews`) can be **newer** than the newest `## Review: NN/100` comment and is the blocking one. On #16526 and #16539 the comment review said *mergeable* while a later `CHANGES_REQUESTED` was what moved them to `change`. Read both; act on whichever is newer. |
| Treating every `CHANGES_REQUESTED` as live | One whose `commit_id` is **not the head** is already answered — #16320 arrived labelled `change` on a review the next commit had fixed. Shortcut: if `PR.updated_at == review.submitted_at`, nothing was pushed after it, so it is LIVE; if `updated_at` is later, re-check against the head sha. |

### Keeping ticks cheap: a state file

Persist per PR `{lane, session, comments, updated_at, last_review_id, rating, acted, pending_ci}`
and diff each tick's search against it. `comments` + `updated_at` come free in the one search call,
so an unchanged PR costs **zero** further calls — on a 10-PR queue only two needed inspection.
Without this, "new" silently degrades to "everything" after a context compaction and you re-summon.

⚠️ **Check-run completion does not bump `updated_at`.** A PR waiting on CI can go green while the
differ still reports `same`, and it would never be flipped. Carry a `pending_ci` flag and always
inspect those rows. This was caught only because #16545 had been flagged by hand the tick before.

### The harness underneath the loop is not stable — plan for it

Two independent things drop out mid-run, and both are silent.

**The GitHub MCP server can disconnect.** All 58 `mcp__github__*` tools vanish at once, with a
notice saying not to search for them. There is **no fallback**: this environment has no `gh` CLI
and no direct GitHub API access, so for the duration GitHub is entirely unreachable — no search, no
check runs, no label flips, no comments. It lasted ~35 minutes on 2026-08-28 (19:57→20:36) and
swallowed five or six ticks.

Recovery is automatic — the server reconnects and a notice says its tools "will appear shortly" —
but the tools do **not** come back on their own: `ToolSearch` with `select:<names>` waits for a
connecting server and reloads the schemas. So **never report a GitHub capability as unavailable
without calling ToolSearch first.** The "gone" notice and the "reconnecting" notice read alike, and
only the search tells them apart.

**The cron store is in-memory and is wiped on every MCP reconnect.** `CronList` came back empty
after each one. Start every tick with `CronList` and re-create the job when it is gone, or the loop
stops without saying so.

What made both survivable: **the agent lanes do not run through the harness.** Jules and OpenHands
are reached by plain `curl` from the scratchpad, and both kept working through the blackout — two
OpenHands sessions pushed commits and CI went green on #16324 and #16585 while GitHub was
unreachable from here. Nothing was lost; the loop merely observed it late, and the next tick diffed
against the state file and picked both up as if nothing had happened. **An outage costs latency,
not work — provided the state lives on disk rather than in the conversation.** That is the second
reason for the state file, and the stronger one.

### Two things that must never be piped at an agent

Both produce the no-op commit loop:

- **A review recommending the PR be closed.** #16584 came back at 20/100 — *"This PR is now empty…
  zero changed files against master"* — with the reviewer explicitly deferring the close to a
  maintainer. There is no code change to ask for.
- **A review whose asks a later commit already satisfied.** Report it for a human instead.

### What the queue looked like at the end

151 open PRs from dogi: **107 `merge`, 36 `ready`, 5 `change`, 0 `review`**. `change` is the only
stage where work accumulates — everything flipped during the run had already moved on. Two
consequences:

- The loop's job is small and bounded: keep `change` draining. It never needs to look at the other
  143.
- The stale-`CHANGES_REQUESTED` sweep matters most on the **`merge` backlog**, not on `change`. A
  blocking review there stalls the automerge drain one PR at a time, and 8 were found sitting in
  exactly that state on `ready`. Run `review:changes_requested` across the merge set before a drain.

**`change` reached 0 at 20:38.** All five remaining PRs cleared inside the last hour once the
OpenHands lane was summoned from a working account and the CI blockage was understood — three
agent pushes, one deliberate no-op with a metadata fix, one base merge. Draining `change` from 5 to
0 took about an hour of ticks, so the steady state is cheap; the expensive part was the two
diagnoses (whose account, and why CI was frozen), and both are written down above.

### Issue authoring

Copy the task text from `refactor_tasks.md` **verbatim** — rating line, full problem paragraph,
`Files:`, `Verification:`. The only original text is a 1–3 sentence **Ask:**. Shrinking or
paraphrasing loses the detail the agent needs.

The **verification notes are the most valuable content in the backlog** — they frequently correct
the proposer or invalidate the task outright. Read them before filing. Five of 156 tasks were
skipped on that basis (stale premise, redundant with a sibling task, contradicts an established
convention, adds consumer-less dead code, premise already satisfied in the codebase).

### Concurrency — the thing that actually bit

Firing 24 summons inside 90 seconds exceeded the concurrent-runtime cap. OpenHands calls
`pause_old_sandboxes` before starting new work, silently evicting the **oldest** sandboxes.
Seventeen ran; the seven oldest were paused mid-first-sentence. The event log on #16385 shows its
last action at 08:22:35 — *two seconds after acking* — thinking *"I'll start by understanding the
PR context and fetching the review comments to follow up on."*

Consequence: **stagger the summons.** A burst doesn't parallelize, it thrashes. And a paused
session is indistinguishable from a stalled one through the GitHub surface — which is what sent
us hunting for a wording problem that never existed.

---

## 3. Agent APIs

### OpenHands Cloud — `https://app.all-hands.dev`

Auth: `Authorization: Bearer $KEY`, key from all-hands.dev settings.

**The docs list five endpoints; the real spec has 164.** It is served at
`https://app.all-hands.dev/openapi.json` — *not* under `/api/v1/`, which returns the SPA shell.

Verified by read-back, not just a `{"success":true}`:

| Call | Effect |
|---|---|
| `GET /api/v1/app-conversations?ids=…` | `sandbox_status` + `execution_status`. Repeat `ids=` for batch. |
| `POST /api/v1/sandboxes/{id}/resume` | PAUSED → STARTING. Restarts the **container only**. |
| `POST /api/v1/sandboxes/{id}/pause` | RUNNING → PAUSED. |
| `GET /api/v1/conversation/{id}/events/search?limit=100` | Full agent event stream — `ActionEvent`, `StreamingDeltaEvent`, thoughts, timestamps. **Works while PAUSED.** This replaces scraping "I'm on it!" out of PR comments. |
| `GET /api/v1/app-conversations/{id}/git/changes?path=/workspace/project` | Uncommitted work. 409 while paused: *"Sandbox is paused; resume it before reading git state."* |
| `PATCH /api/v1/app-conversations/{id}` | Metadata only — title, public, repo, branch. No status field. |

Not yet exercised, but the important one:

```
POST /api/v1/app-conversations/{id}/send-message
{ "role": "user", "content": [...], "run": true }
```

`content` is required; **`run` is a boolean**. This almost certainly does what the UI's unpause
does, and unlike an `@openhands` comment it continues the existing session rather than spawning a
new one — so it costs no slot. Also `POST /api/v1/conversations/{id}/pending-messages` queues
without running.

**Budget exhaustion is invisible from GitHub.** When the account's LLM budget is spent, an
`@openhands` mention still posts *"I'm on it!"* and the sandbox still reaches `RUNNING` — the
summon looks successful on the PR. Only the API shows it: `execution_status: error` about two
seconds after the ack, and a `ConversationErrorEvent` in the event log:

```
429 budget_exceeded — Current cost: 491.13, Max budget: 0.0
```

Observed 2026-08-28 on #16554 and #16577. This is the third distinct failure that looks identical
through the GitHub surface (eviction, idle timeout, budget).

⚠️ **There is no pre-flight health probe. Do not invent one.** We tried
`GET /api/billing/credits` and it looked authoritative — `{"credits":"0.00"}` on the account whose
sessions were dying. It is a **prepaid credit balance, unrelated to the failure**: a second account
(`olevim`) ran sessions to completion at the identical `0.00` while the first stayed blocked. What
killed the first was a per-team LLM budget (`Max budget: 0.0`), which nothing reachable exposes.
`GET /api/quota/status` is likewise a red herring — its `reset_at` is midnight UTC and it is what
the all-hands.dev quota page counts down to, but its `daily_limit`/`remaining` are `null` and it
says nothing about the budget. And `/api/v1/app-conversations` **requires explicit `ids`**, so an
account cannot be polled in the abstract to find its own recent sessions.

So the gate is **after** the summon, not before: post, wait ~30 s, read `execution_status`. `error`
means that account is blocked — stop summoning for it this tick. Cost is at most one wasted summon;
gating on `credits` instead blocked a *working* lane for several ticks.

### ⚠️ The summoning account is whoever posts the comment

This is the single most important operating fact and it took most of a day to see. An `@openhands`
mention creates a session **on the account of the GitHub user who posted it** — the ack says so
outright (*"**olevim** can track my progress at all-hands.dev"*). So:

- An agent driving the loop posts as whatever identity its token has, and can therefore only ever
  summon on **that** account. If that account is budget-blocked, every summon it posts is dead on
  arrival, and no amount of waiting or retrying helps.
- The seven PRs that "proved the lane recovered" had been summoned **by a different human**, on a
  different account. The lane never recovered; a second account was doing the work. Several ticks
  were spent reasoning about the wrong thing because the GitHub surface makes the two identical.
- Corollary for the writeup below: *"OpenHands is dead"* and *"the account I post as is dead"* are
  different claims. Say which one you mean.

**Sessions are per account, and a foreign id returns `null`.** With two OpenHands accounts in play,
`GET /api/v1/app-conversations?ids=…` silently yields a null entry for a session the key does not
own — easy to misread as "gone". The ack comment names the owner (*"**olevim** can track my
progress at all-hands.dev"*), which is how you tell which key to use. Tooling must say
"NOT VISIBLE to any key" rather than reporting a state it cannot see.

**Two pause causes that look identical:**

1. **Eviction** — `pause_old_sandboxes`, oldest-first, when over the concurrent cap.
2. **Idle timeout** — a resumed sandbox with nothing queued boots, finds no work, shuts down.

**API resume ≠ UI unpause.** Resuming seven sandboxes brought them all to RUNNING; `execution_status`
stayed `paused` and every one idled back down. A manual UI unpause on #16389 and #16422 reached
`execution_status: running` and both then pushed real work. `/resume` restarts the container; it
does not restart the agent.

Unexplored: the per-runtime API. Every conversation carries a `conversation_url` on
`<host>.prod-runtime.all-hands.dev` plus its own `session_api_key`. Execution control may live there.

### Jules — `https://jules.googleapis.com/v1alpha`

Auth: `x-goog-api-key` header, key from jules.google.com/settings. Better documented than
OpenHands'.

| Method | Path |
|---|---|
| POST | `/sessions` — `{prompt, title, sourceContext, requirePlanApproval, automationMode}` |
| GET | `/sessions` · `/sessions/{id}` |
| POST | `/sessions/{id}:sendMessage` — `{prompt}` |
| POST | `/sessions/{id}:approvePlan` |
| DELETE | `/sessions/{id}` |
| GET | `/sessions/{id}/activities` · `/activities/{activityId}` |

Activity types: `planGenerated`, `planApproved`, `userMessaged`, `agentMessaged`,
`progressUpdated`, `sessionCompleted`, `sessionFailed` (with a `reason`).

**The task ID in the PR body is the session ID.** `*PR created automatically by Jules for task
<ID>*` → `GET /sessions/<ID>` resolves directly, no lookup table. The canonical link is
`jules.google.com/session/<ID>`; the `/task/<ID>` form in the PR body is not the session URL.

### `sendMessage` replaces the manual paste — the most valuable thing here

**Jules never reads PR comments.** It moves only when text is delivered into its session, which
was being done by hand. `POST /sessions/<id>:sendMessage {"prompt": "<review markdown>"}` does
exactly that, and **it wakes a session already in `COMPLETED`** — verified 2026-08-28 on #16324
(`COMPLETED` → `IN_PROGRESS` ~95 s after delivery, then a real push). Six reviews were piped this
way with no paste; #16381 went idle-since-08:57 → delivered 14:08 → pushed 14:15.

⚠️ **The API returns HTTP 200 for an empty body and silently no-ops**, writing a blank
`userMessaged` activity into the session. A 200 is not proof of delivery — confirm a new
`userMessaged` in `/activities`, and enforce a non-empty prompt client-side.

Tooling built for this lives in the session scratchpad: `agents.py` (both lanes' live state in one
call), `jsend.py` (deliver a review), `jtail.py` (last activities, for confirming delivery and
skipping duplicates).

Two more things the API buys us:

- **`sessionFailed.reason` is a typed field** — the failure we spent a morning detecting by
  string-matching comment text.
- **`planGenerated` without `planApproved` would mean waiting for approval** — but on this account
  approval is automatic: `planApproved` lands ~3 s after `planGenerated`, originator `user`. That
  **disproves the plan-park hypothesis** for #16311's no-op loop; look elsewhere.

---

## 4. Open questions, and the ones that got answered

- **What is the OpenHands concurrent cap?** Behaviour fits ~17 (24 summoned, 17 ran, the 7 oldest
  evicted). No endpoint exposes it — `/api/quota/status` is a *daily request* quota (null on this
  plan), and `billing/subscription-access`, `/api/v1/settings` and `web-client/config` carry
  nothing. Currently inferred only. `GET /api/organizations/{org_id}/conversations/stats` and
  `usage-stats` are untried and might have it.
- **Does `send-message` with `run: true` restart a parked OpenHands agent?** Still untested — the
  Jules equivalent works, so this is the obvious next thing to try. If yes, the whole drain becomes
  scriptable with no GitHub comments at all: summon → poll events → read git diff → pause to
  release the slot → next.
- **Is the per-runtime API the real execution control?** Unexplored.
- **Is the 17-slot cap per account, per org, or per plan?** Affects whether staggering or a
  smaller batch is the right fix.

### The stuck-`push`-trigger failure — solved

**Symptom.** On some branches the `push` event silently stops producing workflow runs. Confirmed
with receipts on #16323 (`list_workflow_runs` filtered to its branch): commits 1–3 each got
`labels` + `build` + `test`; commits 4, 5 and 6 got **only `labels`**. The `build`/`test` runs were
never *created* — not queued, not cancelled — so there is nothing to re-run, and re-pushing failed
three times. `labels.yml` keeps firing because it is `pull_request_target`, which does not depend
on the push event; `build.yml`/`test.yml` are `push`. Same shape on #16324. Six other branches got
all five checks in the same minutes, so it is per-ref, not repo-wide.

**Cause, confirmed twice.** A push from a *different actor* clears it. The `actor` field reads
`dogi` on every stuck run, and every retry was also `dogi`; that was the one variable never tried.

1. #16323 — an OpenHands session running as **olevim** pushed `f5434b4`, and that push produced
   `labels` + `build` + `test`, all green, on a branch that had been frozen for hours.
2. #16324 — no code change was warranted, so instead `update_pull_request_branch` merged the base
   in. That merge commit is authored by the API caller, not `dogi`, and it produced all five check
   runs within two seconds. First complete CI on that PR in hours.

**So there are two fixes, and the second needs no code change at all:**

- Hand the PR to an agent running on a different account and let its push unstick the branch.
- Call `update_pull_request_branch`. It is the cheaper move when the review needs no code change —
  it refreshes CI *and* brings the PR up to date with master, and the base had moved anyway.

**Still worth doing:** `build.yml` has no `workflow_dispatch` (`"Workflow does not have
'workflow_dispatch' trigger"`), so the dispatch workaround reaches only 3/5 checks and cannot flip
a PR. `test.yml` has it and produced normal check runs within seconds. **Adding `workflow_dispatch:`
to `build.yml` is a two-line change** that makes this class of failure recoverable without needing
a second account or a base merge.

**Root cause still unknown** — why GitHub stops creating `push` runs for one ref-and-actor pair is
not explained by anything observable from here. But it no longer blocks the drain.

### Review hygiene on a flip

Flipping `change` → `review` is not the whole job. A stale `CHANGES_REQUESTED` keeps the PR at
`mergeable_state: blocked`, which stalls the automerge drain — 8 PRs were sitting at `ready` in
exactly that state before anyone noticed. Find them with
`search_pull_requests … review:changes_requested`.

**Re-requesting the reviewer does not unblock it — only a dismissal does.** Verified: re-requesting
left four PRs `blocked`, while a manual dismissal on #16345 flipped it to `clean`. And **no MCP tool
dismisses a review** (`pull_request_review_write` offers create / submit_pending / delete_pending /
resolve_thread only), so on this toolset a human has to do it. Re-request what you can, then name
the PRs still owed a dismissal rather than implying they are unblocked.
