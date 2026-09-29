# Combined task plan — test speed, UI boundaries, data boundaries

date: 2026-09-29 · assembled base commit: `3a01b49` (master)
source plans, all written against base `354410d` (48 commits / 149 files behind current master):

| ID | Source | Branch · file |
|----|--------|---------------|
| **A** | test-speedup | `claude/optimistic-thompson-yyvh9m` · `docs/test-speedup-tasks-2026-09-25.md` |
| **B** | refactor / UI→data leaks | `devin/1790366658-refactor-task-plan` · `docs/REFACTOR_TASKS_2026-09-25.md` |
| **C** | repository boundaries | `codex/refactor-repository-boundaries-for-data-layers` · `docs/repository-boundary-work-orders-2026-09-25.md` |

None of the three branches has an open PR — all three are plan-only. Every claim below was
re-verified against `3a01b49`, not taken from the source plans; where master has moved, this
document says so.

---

## 1. How the three plans compare

|  | **A — test speedup** | **B — UI→data leaks** | **C — data boundaries** |
|---|---|---|---|
| Tasks | 10 | 10 | 10 |
| Total size budget | ~360 lines | ~1120 lines | ~165 lines |
| Median task size | ~20 lines | ~115 lines | ~15 lines |
| Layer touched | test sources (9/10) + 1 main seam + 1 CI script | UI ↔ repository seam, main sources only | DAO + SQL (7/10), ViewModel (2), service (1) |
| Evidence quality | **highest** — cites CI run 36030352276, per-class `time=` from JUnit XML, measured seconds per task | medium — cites exact file:line for every call site, but no measurement of payoff | mixed — exact file:line, but 3 of 10 contexts are factually wrong against master |
| Verified accurate | 10/10 (one payoff is conditional) | 10/10 call sites; 1 SQL plan is unsound | **6/10** — C4 partly done, C5 is a no-op, C7 names the wrong column, C9 changes behavior |
| User-visible risk | ~zero (tests only) | **high** — exam flow, resource open, sync bootstrap | low–medium (retry order, upload predicates, cancellation) |
| Reviewability | excellent, mechanical | poor at the top end — B4 spans an 853-line fragment | excellent, near-trivial diffs |
| Parallelism | high, disjoint files | low — fragments and base classes collide | **highest** — each task is 1–2 files |
| Roadmap coverage | 8 (tests), 3/9 (portability) | 3, 5, 9, 10 | 1, 5, 7, 8; advances 9, 10 |
| What it gets wrong | nothing material; A6's payoff depends on an unmerged PR | B9's `ORDER BY` on string timestamps is a correctness trap; B3 builds a VM-as-proxy | 4 tasks rest on stale or wrong readings of the DAOs |

**The short version.** A is the best-engineered plan: it measured before it prescribed, every
task is reversible, and it is the only one that pays back on every CI run for every contributor.
C is the best-shaped plan: ten disjoint, 2-to-30-line orders that can merge in any order — but
its accuracy has decayed hardest against master, and four of its ten orders are worth less than
they claim. B chases the most valuable target (the UI→repository leak is the largest structural
debt of the three) but writes the largest, riskiest diffs against the least-tested code, and it
is the only plan whose tasks can each ship a user-visible regression.

**They are complementary, not competing.** A touches test sources and one CI script. C touches
DAOs, two ViewModels and one service. B touches fragments, activities and services. The only
genuine file collisions are three pairs, listed in §4 — everything else composes.

**A common blind spot.** All three plans are pre-merge-verified against open PRs and none is
post-merge-verified: they were written on `354410d` and master has since taken 48 commits across
149 files. C4 is the casualty (see §5) and A6's stated payoff is the other. Re-verify any task
against HEAD before starting it, not against the source plan.

---

## 2. Rating method

**Impact** 1–5 — how much a real user, or a real contributor on every push, notices.
**Cost** XS (<20 lines) · S (<50) · M (<150) · L (>150).
**Risk** 1–5 — chance of a regression or a wasted review round; test-only work floors at 1–2.
**Grade** — impact ÷ (cost × risk), adjusted for whether the plan's own stated context survives
contact with master. A grade below C means *do not run this task as written*.

---

## 3. Full rating table — all 30 tasks

### Plan A — test speedup

| # | Task | Impact | Cost | Risk | Grade | Verification against `3a01b49` |
|---|------|:---:|:---:|:---:|:---:|---|
| **A2** | `EdgeToEdgeUtilsTest` → plain JUnit | 4 | XS | 1 | **A+** | Confirmed: `@RunWith(RobolectricTestRunner)` at line 22, every collaborator is a mockk. 17.4s for a verify-only test. Best ratio in all 30 tasks. |
| **A10** | Teach the timing summary to separate warm-up from slowness | 3 | M | 1 | **A** | Script present. Meta-task: every later "did this help?" claim depends on it. **Run this first.** |
| **A1** | Hoist per-test static mocks (`UploadManagerTest`, `NetworkUtilsMockTest`) | 4 | S | 3 | **A-** | Confirmed exactly: 5 `mockkStatic`/`mockkObject` in `setup()` × 24 tests, `unmockkAll()` + a redundant `unmockkObject(UrlUtils)` in `tearDown()`. Risk is `clearAllMocks` leaving cross-test stub bleed — run the class 3× as the plan says. |
| **A5** | `ThemeManagerTest`: build the activity only in the dialog test | 3 | S | 1 | **A-** | Confirmed: `HiltTestApplication` at line 30 with no `HiltAndroidRule`, `buildActivity(...).setup()` in `setUp()` for 5 tests, 1 of which uses it. |
| **A3** | Drop dead Hilt from `NetworkUtilsTest`/`IntentUtilsTest`; fold `NetworkUtilsStateTest` | 3 | S | 2 | **A-** | Confirmed: both carry `@HiltAndroidTest` with nothing injected. |
| **A8** | `AndroidDecrypterTest`, `LeadersViewModelTest` → plain JUnit | 2 | XS | 1 | **A-** | Confirmed. `UserEntityParseLeadersTest` correctly excluded (`org.json` has no JVM impl). |
| **A9** | Extract `parseVitalReading` out of `HealthExaminationActivity` | 3 | S | 2 | **A-** | The only main-source task in A, and the only one that is both a speedup and a refactor: it deletes a reflection-based Hilt activity launch. Bridges A into B/C territory. |
| **A7** | Unpin `sdk = [34]` in 3 adapter tests | 2 | XS | 2 | **B+** | All three pins confirmed present. `ServerAddressAdapterTest` also still pins 34 and is correctly excluded (#17544). Risk: a class may genuinely fail on SDK 36 — the plan already says restore-with-a-comment. |
| **A4** | Delete the 3 tautological `DispatcherProvider` tests | 3 | XS | 2 | **B+** | All three confirmed present; Hilt validates the binding at compile time, so the DI test proves nothing at runtime. Downgraded only because "delete tests" always costs a review round. |
| **A6** | Unpin SDK 33 in `CoursesItemUtilsTest`, use a themed context | 2 | S | 2 | **B** | Pin confirmed at line 24 — **but** `ChatAdapterTest` still pins `sdk = [33]` on master, so #17602 has not merged and the stated payoff ("the only reason a fork boots an SDK-33 sandbox") does not hold yet. The context-instead-of-activity half stands on its own. |

### Plan B — UI→data leaks

| # | Task | Impact | Cost | Risk | Grade | Verification against `3a01b49` |
|---|------|:---:|:---:|:---:|:---:|---|
| **B7** | `SyncActivity` post-sync bootstrap → `ResourceDownloadCoordinator` | 3 | S | 2 | **A-** | Confirmed at lines 566–576. The cleanest boundary move in plan B: a data fetch that already sits next to the coordinator it feeds. Best in plan B. |
| **B5** | `ResourceOpenViewModel` out of `BaseContainerFragment` | 4 | M | 4 | **B** | Confirmed: 8 repository calls in a base class every library detail screen inherits. Highest structural payoff in B — and the highest blast radius, since every subclass inherits the change untested. |
| **B1** | `PublicSurveyViewModel` | 3 | M | 3 | **B+** | Confirmed: two repositories injected into an activity with no ViewModel, 5 call sites. Self-contained; good first ViewModel extraction. |
| **B3** | `TeamDetailFragment` → existing `TeamViewModel` | 3 | M | 2 | **B** | Confirmed: 11 direct `teamsRepository` calls beside an already-injected `TeamViewModel`. **Critique:** step 1 adds six pass-through methods with "same signatures, no reshaping" — that builds a ViewModel-as-repository-proxy. Worth doing, but move state (member count, join state) into `StateFlow` rather than adding proxies. |
| **B2** | `AddLocalResourceViewModel` | 3 | M | 3 | **B** | Confirmed: 4 call sites in a 292-line activity. Correctly refuses to bolt onto the unrelated personals `AddResourceViewModel`. |
| **B10** | Stop `VoicesActions` reaching into `ActivitiesRepository` | 3 | M | 3 | **B** | Confirmed, including the constraint: `ReplyActivity:195` is owned by #17648, so the deprecated overload must stay. 4 existing `VoicesActionsTest` cases call the old signature and keep compiling. Cost: a deprecated method left behind as debt with no ticket to remove it. |
| **B6** | Extract `NewsImageUploader` from `UploadManager` | 3 | M | 3 | **B** | Confirmed: `createImage` at 122, `uploadNews` at 271, `/news/_bulk_docs` at 340, `markNewsUploaded` at 364. **Collides with A1** — see §4. |
| **B8** | `ProcessUserDataViewModel` | 2 | M | 3 | **B-** | Confirmed. Lowest payoff in B: it moves three calls behind a ViewModel while the `takeWhile` over `SyncUiState` — the actually fiddly part — stays in the activity. |
| **B4** | `ExamTakingViewModel` for `BaseExamFragment` + `ExamTakingFragment` | 4 | L | 4 | **B-** | Confirmed: 3 repository types, 11 call sites, across a 224-line base and an **853-line** fragment. The biggest leak left and the correct target — but "~150 changed lines, 3 files" underestimates it and a single PR here is unreviewable. **Split into B4a (base fragment, 5 calls) and B4b (exam fragment, 6 calls).** |
| **B9** | Push chat ordering + `_rev` lookup into `ChatDao` | 2 | S | 4 | **C+** | Two halves, one good one bad. **Good:** `getLatestRev` really does `SELECT *` just to read `_rev` — a projection query is a clean win. **Bad:** `createdDate`/`updatedDate` are `String?` and the Kotlin sort is `maxOf(toLongOrNull())` — numeric. `MAX(COALESCE(createdDate,''), COALESCE(updatedDate,''))` compares them *lexicographically*, so `"9999999999"` sorts above `"10000000000"`. The plan hedges ("after confirming how dates are stored") instead of resolving it. **Ship the projection half; drop the ORDER BY half** unless the columns are migrated to INTEGER. |

### Plan C — data boundaries

| # | Task | Impact | Cost | Risk | Grade | Verification against `3a01b49` |
|---|------|:---:|:---:|:---:|:---:|---|
| **C10** | Preserve coroutine cancellation in `UploadToShelfService` | 4 | S | 1 | **A** | Confirmed: broad `catch (e: Exception)` at 44 and 62, `catch (e: Throwable)` at 86, bare `printStackTrace()` at 127. A genuine correctness bug — cancellation becomes a success callback and obsolete uploads keep running. Best task in all 30 by impact-per-risk. |
| **C1** | Deterministic `RetryDao.getPending` order | 3 | XS | 1 | **A** | Confirmed: no `ORDER BY` on the pending query. `RetryDaoTest` already exists to extend. Real behavior fix in ~4 lines of SQL. |
| **C8** | `ProgressViewModel` → `getCurrentUserId()` | 2 | XS | 1 | **A-** | Confirmed **and proven equivalent**: `getUserModel()` is `userDao.getById(sharedPrefManager.getUserId())`, so `user.id` *is* `getCurrentUserId()`. Safe substitution. |
| **C2** | Chunk `SubmitPhotosDao.getByIds` | 2 | XS | 1 | **B+** | Confirmed: raw `IN (:ids)` on an `Array<String>`. Matches the existing house idiom (`FeedbackDao`, `TagDao`, `NotificationDao` all do `getByIdsInternal` + `chunked(900)`). Hardening, not a live bug — >999 photos in one submission is implausible. Add the DAO test C2 omits. |
| **C3** | Chunk `MyLifeDao.getByIds` | 1 | XS | 1 | **B** | Confirmed, same idiom — but both callers pass small lists (one id at 23, visible-life ids at 43). Pure consistency work. `MyLifeDaoTest` already exists. |
| **C7** | Deterministic `NewsLogDao.getPendingUploads` order | 2 | XS | 1 | **B-** | Ordering point is valid (no `ORDER BY`; `time: Long?` exists for `ORDER BY time ASC, id ASC`). **The stated context is wrong:** the predicate is `_id IS NULL OR _id = ''`, not a revision predicate. Rewrite the context, keep the change. |
| **C4** | Stabilize `ResourceActivityDao.getMostOpenedResource` | 2 | XS | 2 | **B-** | **Largely done on master already** (`6f739a5`): the nonblank-title filter and `ORDER BY openCount DESC, title ASC` that steps 1–2 ask for are both present. The one real defect survives — a bare `title` selected under `GROUP BY resourceId`, so SQLite may return any row's title. Rewrite to that single point. |
| **C6** | `CourseActivityDao`: accept empty revisions as pending | 1 | XS | 2 | **C-** | `CourseActivity._rev` is `String? = null`, so the `IS NULL` predicate is already the live path; `OR _rev = ''` is speculative. **Find a writer that stores `""` before running this** — otherwise it is a query widened on a guess, and widening an upload predicate risks re-uploading acknowledged rows. |
| **C9** | `CourseProgressViewModel` → `getCurrentUserId()` | 2 | XS | 3 | **D** | **Do not run as written.** It reads `user?._id`, not `user?.id`. `UserEntity` declares both (`@PrimaryKey id` at 28, `_id` at 29) and `getCurrentUserId()` returns the SharedPrefs user id — i.e. `.id`. Substituting it silently changes which key the progress query uses. Its twin C8 is safe *because* C8 reads `.id`; C9 is the same edit onto a different field. Needs a `getCurrentUserDocId()` or it must keep `getUserModel()`. |
| **C5** | `SearchActivityDao`: treat null revisions as pending | 1 | XS | 1 | **D** | **Near no-op.** `SearchActivity._rev` is `var _rev: String = ""` — non-nullable, so Room generates a `NOT NULL` column and `_rev IS NULL` can never match a row. The premise ("locally created search events with a null revision") cannot occur. Drop, or keep 2 lines as pure defensiveness with the context corrected. |

---

## 4. Conflicts and sequencing

Three real collisions across the 30 tasks. Everything else is file-disjoint and can run in parallel.

1. **A1 ↔ B6 — `UploadManagerTest` / `UploadManager.uploadNews`.** B6 moves `uploadNews` and
   `createImage` into a new `NewsImageUploader`; A1 restructures the mock lifecycle of the same
   test class and specifically rewraps its one `uploadNews derives mimeType…` test (line 324).
   **Order: A1 first** — it is smaller, test-only, and gives B6 a stable class to move the test
   out of. Running B6 first invalidates A1's step 4 outright.
2. **A7 ↔ B10 — `VoicesActionsTest.kt`.** A7 deletes the `sdk = [34]` pin at line 29; B10's
   deprecated-overload contract keeps the file's 4 `showMemberDetails` tests compiling. Different
   lines, so git will merge them — but if B10 lands first, re-run A7's unpin check against the
   new test body. **Either order; A7 first is cheaper.**
3. **A10 ↔ every A task.** A10 changes what "slowest" means in the CI summary. Land it first or
   every later task's before/after numbers are measured on the metric A10 exists to replace.

**A6's precondition:** its payoff needs #17602 (which unpins `ChatAdapterTest` from SDK 33) to be
merged. On master both pins are still present. Either wait for #17602, or fold its one-line unpin
into A6 and say so — do not claim the SDK-33 sandbox saving while a second pin survives.

**B's internal ordering:** B1 → B2 → B3 → B10 → B8 → B5 → B4a → B4b. Start with the self-contained
activities, finish with the base class and the exam flow, so the pattern is settled before it is
applied to the code that breaks loudest.

---

## 5. Combined backlog

Six waves. Waves 0–2 are near-free and near-riskless; wave 5 is where the real structural work is
and where review capacity should be spent.

### Wave 0 — measure first (1 task)
**A10** — teach `test_timing_summary.py` to separate sandbox warm-up from real per-test slowness.
Everything downstream reports its payoff through this script.

### Wave 1 — free CI wins, test sources only (7 tasks, ~2 hours, risk ≤ 2)
**A2** (A+) · **A8** · **A5** · **A3** · **A4** · **A7** · **A6**
All test-only, all reversible, zero user-visible surface. Can go out as one PR per task or, given
the size labels, as two grouped PRs: *unpin-and-de-Robolectric* (A2, A6, A7, A8) and
*drop-dead-Hilt-and-fixtures* (A3, A4, A5).

### Wave 2 — one-file correctness in the data layer (5 tasks, risk ≤ 1)
**C10** (A — the one real bug in all three plans) · **C1** (A) · **C8** (A-) · **C2** · **C3**
Plus **C7** and **C4** with their contexts rewritten per §5 below.

### Wave 3 — small boundary moves (3 tasks)
**B7** (A- — cleanest move in plan B) · **A9** (extract `parseVitalReading`) ·
**B9-projection-only** (the `_rev` projection half; drop the `ORDER BY` half).

### Wave 4 — test infra, with care (1 task)
**A1** — highest single CI saving in the set, but the only test task that can go flaky. Run each
class 3× and report the class `time=` before and after, as the plan requires. Must precede B6.

### Wave 5 — ViewModel extractions, one PR each (8 tasks)
**B1** → **B2** → **B3** → **B10** → **B8** → **B5** → **B4a** → **B4b**
This is the actual structural debt. Each of these ships a user-visible path and none of them has
unit coverage today; treat "the suite stays green" as necessary but nowhere near sufficient, and
walk the acceptance criteria on a device.

### Do not run as written (4)
- **C9** — wrong field (`_id` vs `.id`). Rework or drop.
- **C5** — the column is non-null; the predicate can never match.
- **C6** — needs evidence that anything writes `""` before widening an upload predicate.
- **B9's `ORDER BY` half** — lexicographic comparison of numeric string timestamps.

---

## 6. Corrections the source plans need

Carry these into any task file derived from the three plans.

| Task | Correction |
|---|---|
| **C4** | Context is stale. `6f739a5` already added the nonblank-title filter and `ORDER BY openCount DESC, title ASC` that steps 1–2 request. Rewrite to the one surviving defect: `title` is selected bare under `GROUP BY resourceId`. |
| **C5** | `SearchActivity._rev` is `String = ""`, non-nullable. `_rev IS NULL` matches nothing. Drop the task or restate it as defensiveness against a future nullability change. |
| **C6** | `CourseActivity._rev` is `String? = null`. Name a writer that stores `""` or drop it. |
| **C7** | The predicate is `_id IS NULL OR _id = ''`, not a revision predicate. Keep `ORDER BY time ASC, id ASC`; fix the stated reason. |
| **C9** | `getCurrentUserId()` returns the SharedPrefs user id, which equals `UserEntity.id` — the ViewModel reads `_id`. Add `getCurrentUserDocId()` or leave `getUserModel()` in place. |
| **B9** | Split. Ship the `SELECT _rev` projection; hold the `ORDER BY` until the timestamp columns are numeric. |
| **B3** | Move state into `StateFlow`, don't add six pass-through methods to `TeamViewModel`. |
| **B4** | Split into B4a (`BaseExamFragment`, 5 calls) and B4b (`ExamTakingFragment`, 6 calls). "~150 lines, 3 files" is optimistic for an 853-line fragment. |
| **A6** | #17602 has not merged — `ChatAdapterTest` still pins SDK 33. Either wait, or fold that unpin in and restate the payoff. |
| **All** | Re-verify against HEAD, not against `354410d`. Master has taken 48 commits over 149 files since the plans were written. |
