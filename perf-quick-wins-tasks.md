# myPlanet — 10 Performance Quick-Win Tasks (one PR each)

Scope: low-hanging fruit only. Each task is a small, self-contained PR.
**No two tasks touch the same file**, so all 10 can be reviewed/merged in one
round (~9.99 PRs/day budget) without merge conflicts.

| # | Task | Files touched | Size |
|---|------|---------------|------|
| 1 | SurveyFragment: kill double `notifyDataSetChanged()` | `ui/surveys/` (2 files) | S |
| 2 | VoicesAdapter: hoist per-bind allocations | `ui/voices/VoicesAdapter.kt` | S |
| 3 | EnterprisesFinancesAdapter: per-bind `lowercase()` | `ui/enterprises/EnterprisesFinancesAdapter.kt` | XS |
| 4 | CommunityLeadersAdapter: per-bind `activity.findViewById` | `ui/community/CommunityLeadersAdapter.kt` | XS |
| 5 | UploadToShelfService: nested fire-and-forget launches | `services/UploadToShelfService.kt` | S |
| 6 | SharedPreferences `.commit()` sweep | `services/SharedPrefManager.kt`, `services/sync/TransactionSyncManager.kt` | XS |
| 7 | Utilities.toast: per-call `Handler` allocation | `utils/Utilities.kt` | XS |
| 8 | CameraUtils: JPEG byte copy on main looper | `utils/CameraUtils.kt` | S |
| 9 | Pager adapters: redundant copies in diff path | `ui/courses/CoursesPagerAdapter.kt`, `ui/teams/TeamPagerAdapter.kt` | XS |
| 10 | ConfigurationsRepositoryImpl: repository hop to main | `repository/ConfigurationsRepositoryImpl.kt` | S |

Overlap check: every file appears in exactly one row. Safe to merge in any order.

---

## Task 1 — SurveyFragment: replace the two `notifyDataSetChanged()` side-band updates

**Evidence:** `ui/surveys/SurveyFragment.kt:180` and `:185`. `SurveysAdapter` is
already a `ListAdapter` (uses `DiffUtils.itemCallback`), but two observers keep
`surveyInfoMap` / `bindingDataMap` outside the list and call
`getAdapter().notifyDataSetChanged()` on every emission — a full rebind that
defeats the DiffUtil the adapter already pays for.

**Change (pick the smallest):**
- Pass both maps into the adapter via a setter and re-`submitList(currentList)`
  so DiffUtil computes the real delta, **or**
- fold the two maps into the item model / a combined UI-state flow in
  `SurveyViewModel` and keep a single `submitList` path.

**Why:** removes 2 full-list rebinds per survey/info update on a list screen;
unblocks later ViewModel/UI-state consolidation (roadmap item 3) by giving the
fragment one state stream.

**Files:** `ui/surveys/SurveyFragment.kt`, `ui/surveys/SurveysAdapter.kt`
(optionally `SurveyViewModel.kt` — keep the ViewModel change out if the PR
should stay tiny).

**Tests:** existing `SurveysAdapter`/survey unit tests should still pass; add
one assertion that info-map updates don't call `notifyDataSetChanged`.

---

## Task 2 — VoicesAdapter: hoist per-bind allocations out of `onBindViewHolder`

**Evidence:** `ui/voices/VoicesAdapter.kt`
- `:703` — `String.format(Locale.getDefault(), "(%d)", replyCount)` allocated per bind
- `:889`, `:982` — `path.lowercase(Locale.getDefault()).endsWith(".gif")` /
  `imageUrl.lowercase(...)` recomputed per bind

**Change:**
- Replace the `String.format` with a plurals/string-resource placeholder
  (`getString(R.string.reply_count, n)`) or a cached `MessageFormat`.
- Replace `lowercase(...).endsWith(".gif")` with
  `endsWith(".gif", ignoreCase = true)` (no allocation, no locale).
- If a GIF check helper already exists, reuse it; otherwise add one private
  `isGif(path: String?)` fun in the adapter — no new public API.

**Why:** Voices is the busiest scroll surface in the app; per-bind allocations
directly cause GC churn/jank while scrolling.

**Files:** `ui/voices/VoicesAdapter.kt` only.

**Tests:** existing VoicesAdapter tests cover binding; no new infra needed.

---

## Task 3 — EnterprisesFinancesAdapter: drop per-bind `lowercase()`

**Evidence:** `ui/enterprises/EnterprisesFinancesAdapter.kt:44` —
`TextUtils.equals(item.type?.lowercase(Locale.getDefault()), "debit")` runs on
every bind.

**Change:** `item.type.equals("debit", ignoreCase = true)` (null-safe,
allocation-free, locale-independent — also fixes the Turkish-locale
lowercasing bug class).

**Files:** `ui/enterprises/EnterprisesFinancesAdapter.kt` only. One-line diff.

---

## Task 4 — CommunityLeadersAdapter: hoist `activity.findViewById` out of `onBindViewHolder`

**Evidence:** `ui/community/CommunityLeadersAdapter.kt:52` —
`activity?.findViewById<View>(R.id.fragment_container) != null` walks the
activity view tree on **every bound row**.

**Change:** resolve it once (lazy field or cache after first lookup) — the
container's existence doesn't change during the adapter's lifetime. Keep the
behavior identical; just stop re-querying per bind.

**Files:** `ui/community/CommunityLeadersAdapter.kt` only. ~3-line diff.

---

## Task 5 — UploadToShelfService: remove nested fire-and-forget launches

**Evidence:** `services/UploadToShelfService.kt` — `uploadUserData()` and
`uploadSingleUserData()` already run inside `appScope.launch(io)`, then call
`uploadToShelf(...)` / `uploadSingleUserToShelf(...)`, which launch **another**
`appScope.launch(io)`. Double dispatch, broken structured concurrency (inner
failures can't propagate to the outer try/catch), and the outer coroutine
finishes before the upload it "triggered".

**Change:** make `uploadToShelf` and `uploadSingleUserToShelf`
`private suspend fun` and call them directly from the existing coroutines.
Behavior stays sequential; the error path in the outer `catch` now actually
covers shelf-upload failures.

**Why:** correctness + removes 2 redundant dispatcher hops per sync; makes the
upload flow linear and readable — a prerequisite for the sync/upload
consolidation (roadmap item 5).

**Files:** `services/UploadToShelfService.kt` only.

**Tests:** existing upload/sync unit tests should cover; if a test asserts on
async timing, adjust to `runTest` semantics.

---

## Task 6 — SharedPreferences `.commit()` sweep (2 spots)

**Evidence:**
- `services/SharedPrefManager.kt:284` — `clearPreferences()` does
  `editor.clear().apply()` then re-puts on the same editor and calls
  `editor.commit()` (blocking the calling thread, possibly main during
  logout).
- `services/sync/TransactionSyncManager.kt:326` — checkpoint removal uses
  `.commit()` with an unused return value.

**Change:**
- `clearPreferences()`: collapse into a single `pref.edit { clear(); for
  ((k, v) in tempStorage) putBoolean(k, v) }` — one atomic `apply()`.
- `TransactionSyncManager`: `.commit()` → `.apply()`.

**Why:** `.commit()` is synchronous disk I/O on the caller's thread; both
return values are ignored, so `apply()` is strictly better.

**Files:** `services/SharedPrefManager.kt`,
`services/sync/TransactionSyncManager.kt`.

---

## Task 7 — Utilities.toast: stop allocating a `Handler` per background toast

**Evidence:** `utils/Utilities.kt:47` — every toast posted from a background
thread allocates a new `Handler(Looper.getMainLooper())`.

**Change:** hoist to one shared handler (`private val mainHandler by lazy {
Handler(Looper.getMainLooper()) }` in the `Utilities` object). Toasts fire
constantly during sync — this is a hot path.

**Files:** `utils/Utilities.kt` only. ~2-line diff.

---

## Task 8 — CameraUtils: move the JPEG byte copy off the main looper

**Evidence:** `utils/CameraUtils.kt:52` —
`imageReader?.setOnImageAvailableListener({ ... })` is registered **without a
handler**, so the listener runs on the main looper — including
`ByteArray(buffer.capacity())` + `buffer.get(bytes)` (a full JPEG copy,
~640×480) before the existing `scope.launch { savePicture(...) }`.

**Change:** keep `image.close()` ordering correct: move the buffer read into
the launched coroutine (close the image there after copying), or pass a
background `Handler`/`Executor` to the listener. Smallest correct diff wins.

**Why:** capture currently stalls the UI thread for the duration of the copy.

**Files:** `utils/CameraUtils.kt` only.

**Tests:** manual capture smoke test; existing CameraUtils unit tests still
pass.

---

## Task 9 — Pager adapters: remove redundant copies in the diff path

**Evidence:**
- `ui/courses/CoursesPagerAdapter.kt:27-38` — builds `listOf(null) + steps`
  and `listOf(null) + newSteps` (two full copies), then `steps.clear();
  steps.addAll(newSteps)` (a third copy) before dispatching the diff.
- `ui/teams/TeamPagerAdapter.kt:48-58` — `pages = newPages.toList()` copies
  even when `newPages` is already an immutable `List`.

**Change:**
- CoursesPagerAdapter: diff against lightweight wrappers (or indices) instead
  of materializing `listOf(null) + ...` twice; swap the backing list by
  reference where possible.
- TeamPagerAdapter: drop the `.toList()` when the incoming list is already
  immutable.

**Why:** pure allocation removal on the course/team detail pager update path;
keeps `DiffUtils.calculateDiff` usage intact (no behavior change).

**Files:** `ui/courses/CoursesPagerAdapter.kt`,
`ui/teams/TeamPagerAdapter.kt`.

---

## Task 10 — ConfigurationsRepositoryImpl: drop the repository→main hop in `handleVersionEvaluation`

**Evidence:** `repository/ConfigurationsRepositoryImpl.kt:463` —
`serviceScope.launch(dispatcherProvider.main) { ... }` wraps pure version
comparison (`VersionUtils.getVersionCode`, `Constants.showBetaFeature` →
SharedPreferences read) plus the callback invocation. None of it needs the
main thread; launching on main also decouples it from the calling coroutine.

**Change:**
1. Verify the `CheckVersionCallback` implementations hop to main themselves
   before touching views (check the login/sync callers).
2. Run the evaluation on `io`/`default` inline (no extra launch) and let UI
   callers switch dispatchers at the edge.

**Why:** repositories shouldn't dispatch to main (dispatcher discipline per
`docs/CODE_STYLE_GUIDE.md`); removes a main-thread scheduling point from the
login/version-check path — unblocks the DI/data-layer cleanup (roadmap items
1 & 4).

**Files:** `repository/ConfigurationsRepositoryImpl.kt` only (touch callers
only if step 1 shows a missing main-hop — then keep that in the same PR).

**Tests:** existing ConfigurationsRepository tests; assert the callback fires
on the injected test dispatcher.

---

## Suggested merge order (safest first)

1. Task 3 (one-liner) → 2. Task 6 → 3. Task 7 → 4. Task 4 → 5. Task 9 →
6. Task 2 → 7. Task 8 → 8. Task 5 → 9. Task 1 → 10. Task 10

Tasks 1, 5, 8, 10 carry the only behavioral risk; the rest are mechanical.
