# Merge waves — release 0.65.x

**Repo** [open-learning-exchange/myplanet](https://github.com/open-learning-exchange/myplanet) ·
**Base** [`41d89f46`](https://github.com/open-learning-exchange/myplanet/commit/41d89f46cfc2c2376e0ba8e43a480624184ffaa9) (`origin/master`, tag [v0.65.48](https://github.com/open-learning-exchange/myplanet/releases/tag/v0.65.48)) ·
**Heads fetched** 2026-08-20 10:41 UTC · **revalidated** 10:46 UTC (base and all 7 heads unmoved)

Label in force: **`merge`** (the `ready to merge` → `merge` rename has landed). 124 open PRs, 7 carry `merge`.

Conflicts below are **textual only** — `git merge-tree --write-tree` against this exact base. A clean merge is not a passing build. Re-run the plan if base moves for any reason other than executing it.

---

## Wave 1 — queue now

Verified clean **cumulatively** by chaining `merge-tree --write-tree` + `commit-tree` from `41d89f46`. Zero failures. Order is not load-bearing: the conflict graph over the clean-vs-base set has **no edges**, and no two PRs here touch the same production file.

| # | PR | Title | Author | Files | Behind | CI | Head |
|---|----|-------|--------|------:|-------:|----|------|
| 1 | [#15832](https://github.com/open-learning-exchange/myplanet/pull/15832) | sync: skip resource cleanup on batch failure ([fixes #15831](https://github.com/open-learning-exchange/myplanet/issues/15831)) | Okuro3499 | 1 | 0 | ✅ 4/4 | `810dcdf5` |
| 2 | [#15835](https://github.com/open-learning-exchange/myplanet/pull/15835) | sync: fix credential extraction from alternative URL ([fixes #15834](https://github.com/open-learning-exchange/myplanet/issues/15834)) | Okuro3499 | 2 | 0 | ✅ 4/4 | `584545bd` |
| 3 | [#15837](https://github.com/open-learning-exchange/myplanet/pull/15837) | sync: preserve credentials when server omits them ([fixes #15836](https://github.com/open-learning-exchange/myplanet/issues/15836)) | Okuro3499 | 2 | 0 | ✅ 4/4 | `e0625de0` |
| 4 | [#15771](https://github.com/open-learning-exchange/myplanet/pull/15771) | resources: support openWhichFile for nested HTML ([fixes #15634](https://github.com/open-learning-exchange/myplanet/issues/15634)) | Okuro3499 | 7 | 2 | ✅ 4/4 | `7945a9c5` |
| 5 | [#15772](https://github.com/open-learning-exchange/myplanet/pull/15772) | courses: smoother progress scrolling ([fixes #15553](https://github.com/open-learning-exchange/myplanet/issues/15553)) | J-S-webskas | 1 | 126 | ⚠️ green on stale base | `dedb44e7` |

```
base 41d89f46
  ok  + 15832   tree 1036e20e
  ok  + 15835   tree c7cd1fb0
  ok  + 15837   tree f4c44f14
  ok  + 15771   tree 589eb4ec
  ok  + 15772   tree 7d7d0b5c
failures: 0
```

**Run the unit suite after #15771**, not only at the end of the wave — it is the only entry that changes persisted schema and production behaviour together. (I could not run it here: this container has no Android SDK.)

**#15772 caveat** — its green checks were earned 2026-08-18, 126 commits behind base. Layout XML only, so exposure is small, but the tick was earned on a different tree.

### #15771 ships the second schema bump of this release — batch it here

`AppDatabase` 8 → 9 for the new `openWhichFile` column on `MyLibrary`. **Land it in 0.65.x.** The bump is free now and expensive later:

| Series | Opened | Schema at series start |
|--------|--------|-----------------------:|
| [v0.63.0](https://github.com/open-learning-exchange/myplanet/releases/tag/v0.63.0) | 2026-08-03 | 5 |
| [v0.64.0](https://github.com/open-learning-exchange/myplanet/releases/tag/v0.64.0) | 2026-08-11 | 6 |
| [v0.65.0](https://github.com/open-learning-exchange/myplanet/releases/tag/v0.65.0) | 2026-08-17 | **8** |

0.65.x already carries a schema bump — 8 landed at [`a08fc566`](https://github.com/open-learning-exchange/myplanet/commit/a08fc566) via [#15607](https://github.com/open-learning-exchange/myplanet/pull/15607) on 08-17, the commit that opened the series. Room hashes the compiled schema and compares it once at open, so a device upgrading from a 0.64.x build rebuilds **once** whether it lands on 8 or 9. Deferring #15771 to 0.66.x does not avoid a rebuild — it buys a **second** one, a week later, for every device.

The 0.65.0 → 0.66.0 cut is imminent (series gaps have been 8 and 6 days; 0.65.0 opened 08-17), so this is now or a second rebuild next week.

`#15771` is the **only** in-play PR that changes the schema version. The one other open PR touching it, [#15808](https://github.com/open-learning-exchange/myplanet/pull/15808), is `WIP`/`experiment` and out of play — nothing else is waiting to be batched.

---

## Final wave — conflicts with base, cannot be queued

Not an ordering problem. A contended file means somebody rebases; no wave order rescues it.

| PR | Title | Author | Conflicting paths | What it needs |
|----|-------|--------|-------------------|---------------|
| [#15656](https://github.com/open-learning-exchange/myplanet/pull/15656) | all: smoother teams repository finances membership splitting ([fixes #15840](https://github.com/open-learning-exchange/myplanet/issues/15840)) | dogi | `di/RepositoryModule.kt`<br>`repository/TeamsRepository.kt` | **Pure staleness.** Already diagnosed in review 08-17: merge master in, keep both sets of bindings. No judgement calls. Unactioned 3 days. |
| [#15594](https://github.com/open-learning-exchange/myplanet/pull/15594) | enterprises: smoother finances landscaping ([fixes #15577](https://github.com/open-learning-exchange/myplanet/issues/15577)) | ragilzakaria | `ui/enterprises/EnterprisesFinancesFragment.kt` | **Real overlap**, not staleness. [#15768](https://github.com/open-learning-exchange/myplanet/pull/15768) and [#15773](https://github.com/open-learning-exchange/myplanet/pull/15773) rewrote the same fragment in the last drain. Needs a call on which behaviour survives. |

Both were mergeable when the previous plan was written. Break attributed by walking master's first-parent path and re-testing at each step:

- #15594 broke at [`a8c444f4`](https://github.com/open-learning-exchange/myplanet/commit/a8c444f4) — [#15613](https://github.com/open-learning-exchange/myplanet/pull/15613)
- #15656 broke at [`230991ae`](https://github.com/open-learning-exchange/myplanet/commit/230991ae) — [#15649](https://github.com/open-learning-exchange/myplanet/pull/15649)

---

## Not in the plan

### `priority` — protected, cost 0 PRs

Both merge cleanly against base and nothing in the wave conflicts with either, so the greedy maximum independent set is **5 → 5, delta 0**. Neither carries `merge`, so neither displaces anything.

| PR | Title | Author | vs base | Note |
|----|-------|--------|---------|------|
| [#15897](https://github.com/open-learning-exchange/myplanet/pull/15897) | Fix Robolectric race condition by pre-fetching android-all jars | dogi | ✅ clean | touches `test.yml`, `app/build.gradle`, `docs/TESTING.md` |
| [#15914](https://github.com/open-learning-exchange/myplanet/pull/15914) | test: smoother Robolectric warm-up by retiring `@Config` SDK pins… | claude[bot] | ✅ clean | 34 files; also carries `triage`, `close?` |

⚠️ These are **competing solutions to one problem** — both attack Robolectric flakiness, both edit `docs/TESTING.md`, and they merge cleanly only *textually*. Resolve as a design decision, not a merge decision.

### `ready` — impact only, never merged

Landing the wave changes nothing here; verified against the simulated post-wave tree.

| PR | Title | Author | vs base | vs post-wave |
|----|-------|--------|---------|--------------|
| [#15825](https://github.com/open-learning-exchange/myplanet/pull/15825) | local event task reminders workmanager notifications ([address #15115](https://github.com/open-learning-exchange/myplanet/issues/15115)) | ragilzakaria | ✅ clean | ✅ clean |
| [#15838](https://github.com/open-learning-exchange/myplanet/pull/15838) | prevent Become a Member from giving error in screen rotation ([fixes #15556](https://github.com/open-learning-exchange/myplanet/issues/15556)) | J-S-webskas | ✅ clean | ✅ clean |
| [#15699](https://github.com/open-learning-exchange/myplanet/pull/15699) | show rating dialog only after user finishes a resource… | J-S-webskas | ❌ `ResourceViewerViewModel` | ❌ already broken |
| [#15820](https://github.com/open-learning-exchange/myplanet/pull/15820) | teams: smoother task and meetup comment threads managing ([fixes #15112](https://github.com/open-learning-exchange/myplanet/issues/15112)) | ragilzakaria | ❌ `app/build.gradle`, `EventsRepository` | ❌ already broken |
| [#15824](https://github.com/open-learning-exchange/myplanet/pull/15824) | gamification achievement hub offline badges streaks ([address #15114](https://github.com/open-learning-exchange/myplanet/issues/15114)) | ragilzakaria | ❌ `app/build.gradle` | ❌ already broken |

The wave breaks nothing that was not already broken. #15820 and #15824 both conflict on `app/build.gradle` — a hot file that will keep breaking anything parked on it.

---

## Previous plan — reconciled against `git log`, not the label search

82 commits landed on master since 2026-08-17. Of the 71 closed PRs still carrying `merge`:

| Bucket | Count |
|--------|------:|
| merged (subject `(#N)` present on `origin/master`) | 70 |
| **closed unmerged while still labelled `merge`** | **1** |
| open + `automerge` (live queue) | 0 |
| label removed | 0 |
| **total** | **71** ✅ sums |

### ⚠️ [#15631](https://github.com/open-learning-exchange/myplanet/pull/15631) was dropped and nobody noticed

*resources: smoother viewer markdown utils loading ([fixes #15775](https://github.com/open-learning-exchange/myplanet/issues/15775))* — closed 2026-08-18 20:01 UTC, `merged: false`, still labelled `merge`, `mergeable_state: unstable`, never received `automerge`. Plausibly superseded by [#15587](https://github.com/open-learning-exchange/myplanet/pull/15587), which landed and touches the same viewer code — inference, not verified. This is the drainer failure mode that leaves no trace and no label search will surface.

---

## Execution checklist

- [ ] Add `automerge` to [#15832](https://github.com/open-learning-exchange/myplanet/pull/15832), [#15835](https://github.com/open-learning-exchange/myplanet/pull/15835), [#15837](https://github.com/open-learning-exchange/myplanet/pull/15837), [#15771](https://github.com/open-learning-exchange/myplanet/pull/15771), [#15772](https://github.com/open-learning-exchange/myplanet/pull/15772) — read each PR's **live** labels first and send existing + new; the issues API replaces the whole array
- [ ] Run the unit suite once #15771 is in, before the rest of the wave drains
- [ ] Cut 0.66.0 only after #15771 is in 0.65.x, or accept a second device-wide DB rebuild next week
- [ ] Ping authors on [#15656](https://github.com/open-learning-exchange/myplanet/pull/15656) (mechanical merge) and [#15594](https://github.com/open-learning-exchange/myplanet/pull/15594) (needs a behaviour call)
- [ ] Decide [#15897](https://github.com/open-learning-exchange/myplanet/pull/15897) vs [#15914](https://github.com/open-learning-exchange/myplanet/pull/15914) — one Robolectric fix, not two
- [ ] Triage [#15631](https://github.com/open-learning-exchange/myplanet/pull/15631): reopen, or strip `merge` so the next reconciliation is clean

---

### Method

Clone deepened with `git fetch --deepen=3000` (base depth 3,147 — at the original shallow depth `merge-base` returns nothing usable and healthy PRs read as unmergeable). All 8,442 PR heads fetched once into `refs/remotes/pr/*`; analysis then ran entirely locally with `git merge-tree --write-tree` (git 2.43.0). PR set from paginated `list_pull_requests` with labels filtered locally — never from a label search, whose index lags writes. All 14 in-play refs confirmed byte-identical to their API head SHAs before testing.

Titles and labels were re-read at 10:46 UTC. #15832, #15835 and #15837 show `updated_at` 10:42 — after the ref fetch — but their head SHAs are unchanged, so that was label or comment activity, not a push. A prepping agent is demonstrably active on this backlog: #15771's head moved 63 minutes before the snapshot. **Treat anything older than a few hours as fiction and re-fetch.**
