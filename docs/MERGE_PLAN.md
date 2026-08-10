# myPlanet — `merge` PR merge plan

Generated against `origin/master` @ `a9e363c` · **70 `merge`** + 12 `ready` + 2 `priority` PRs analysed.

Same methodology as the previous plan: conflict detection is **real three-way merging** (`git merge-tree --write-tree`, the engine GitHub uses), history deepened to full depth (3060 commits) before any `merge-base` was computed, every file-sharing pair merged in both directions, and the whole Wave 1 sequence simulated cumulatively.

> **New rule this round.** Soon-ready PRs carry the **`priority`** tag. They win every contest. Anything that conflicts with a `priority` PR goes **on hold** regardless of how ready it is — the reverse of last time, when #15158 was sacrificed to protect two review-complete PRs.

---

## Where the old plan got to

**3 of 76 PRs landed.** Master moved 10 commits since `a8a5252`; only three came from the plan.

| Old bucket | Planned | Landed | Still open |
|---|---|---|---|
| Wave 1A | 28 | 2 — [#15301](https://github.com/open-learning-exchange/myplanet/pull/15301), [#15320](https://github.com/open-learning-exchange/myplanet/pull/15320) | 26 |
| Wave 1B | 18 | 0 | 18 |
| Wave 2 | 12 | 1 — [#15323](https://github.com/open-learning-exchange/myplanet/pull/15323) | 11 |
| Wave 3 | 5 | 0 | 5 |
| `ready` | 13 | 0 | 13 |

The other 7 commits were dependabot ([#15421](https://github.com/open-learning-exchange/myplanet/pull/15421), [#15420](https://github.com/open-learning-exchange/myplanet/pull/15420), [#15418](https://github.com/open-learning-exchange/myplanet/pull/15418)) and agent-tooling housekeeping ([#15499](https://github.com/open-learning-exchange/myplanet/pull/15499), [#15453](https://github.com/open-learning-exchange/myplanet/pull/15453), [#15448](https://github.com/open-learning-exchange/myplanet/pull/15448), [#15436](https://github.com/open-learning-exchange/myplanet/pull/15436)) — none in the plan.

**Label churn since then:** [#15158](https://github.com/open-learning-exchange/myplanet/pull/15158) (last plan's "one casualty") and [#15355](https://github.com/open-learning-exchange/myplanet/pull/15355) lost `merge` and are now `change`; [#15267](https://github.com/open-learning-exchange/myplanet/pull/15267), [#15274](https://github.com/open-learning-exchange/myplanet/pull/15274), [#15291](https://github.com/open-learning-exchange/myplanet/pull/15291) moved back to `ready`. Two new arrivals: [#15318](https://github.com/open-learning-exchange/myplanet/pull/15318), [#15259](https://github.com/open-learning-exchange/myplanet/pull/15259).

**Four Wave-1 predictions from last time went stale as master moved** — [#15285](https://github.com/open-learning-exchange/myplanet/pull/15285) and [#15303](https://github.com/open-learning-exchange/myplanet/pull/15303) were Wave 1B and now conflict with `master` outright. That is the cost of the plan not being executed.

---

## Summary

| Bucket | Count |
|---|---|
| `merge`, merges clean into `master` | **61** |
| `merge`, already conflicts with `master` | **9** |
| Held to protect a `priority` PR | **6** |
| `ready` (pre-review) | 12 |
| `priority` (soon ready) | 2 — [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316), [#15446](https://github.com/open-learning-exchange/myplanet/pull/15446) |
| **Mergeable now, zero rebases** | **48** |

Cumulative simulation of all 48: **0 conflicts**, 118 files touched. **No `ready` or `priority` PR is broken by this wave** — the thing last plan couldn't achieve.

---

## Wave 1 — merge now · 48 PRs · ANY order

### Wave 1A — 36 PRs with zero conflict edges against anything

| PR | Title | Author |
|---|---|---|
| [#15070](https://github.com/open-learning-exchange/myplanet/pull/15070) | all: smoother secure prefs sensitive keys migrating (fixes #15456) | dogi |
| [#15259](https://github.com/open-learning-exchange/myplanet/pull/15259) | display newly added image when editing a voice post (fixes #15253) | **ragilzakaria** |
| [#15269](https://github.com/open-learning-exchange/myplanet/pull/15269) | Optimize `ResourceSearchUtils` by caching normalized titles | dogi |
| [#15271](https://github.com/open-learning-exchange/myplanet/pull/15271) | teams: smoother repository member statuses querying (fixes #15455) | dogi |
| [#15277](https://github.com/open-learning-exchange/myplanet/pull/15277) | teams: smoother surveys repository scanning (fixes #15458) | dogi |
| [#15280](https://github.com/open-learning-exchange/myplanet/pull/15280) | Refactor UI State Collection in BaseDashboardFragment | dogi |
| [#15281](https://github.com/open-learning-exchange/myplanet/pull/15281) | all: smoother date formatter caching (fixes #15459) | dogi |
| [#15283](https://github.com/open-learning-exchange/myplanet/pull/15283) | all: smoother fragment flow collecting (fixes #15460) | dogi |
| [#15295](https://github.com/open-learning-exchange/myplanet/pull/15295) | all: smoother realm terminology naming (fixes #15463) | dogi |
| [#15298](https://github.com/open-learning-exchange/myplanet/pull/15298) | teams: smoother repository view modelling (fixes #15465) | dogi |
| [#15304](https://github.com/open-learning-exchange/myplanet/pull/15304) | sync: smoother realtime sync mixin refresh handling (fixes #15467) | dogi |
| [#15308](https://github.com/open-learning-exchange/myplanet/pull/15308) | all: smoother view model sharing subscribed (fixes #15469) | dogi |
| [#15312](https://github.com/open-learning-exchange/myplanet/pull/15312) | all: smoother pager adapter diffing (fixes #15471) | dogi |
| [#15315](https://github.com/open-learning-exchange/myplanet/pull/15315) | Optimize NotificationUtils.getInstance allocations | dogi |
| [#15325](https://github.com/open-learning-exchange/myplanet/pull/15325) | Refactor dashboard-triggered key sync out of BaseDashboardFragment | dogi |
| [#15332](https://github.com/open-learning-exchange/myplanet/pull/15332) | teams: smoother events repository gson caching (fixes #15477) | dogi |
| [#15337](https://github.com/open-learning-exchange/myplanet/pull/15337) | 🧪 Add tests for ConfigurationsRepository | dogi |
| [#15338](https://github.com/open-learning-exchange/myplanet/pull/15338) | all: smoother retry repository testing (fixes #15480) | dogi |
| [#15341](https://github.com/open-learning-exchange/myplanet/pull/15341) | sync: smoother upload repository testing (fixes #15481) | dogi |
| [#15344](https://github.com/open-learning-exchange/myplanet/pull/15344) | sync: smoother download service error handling testing (fixes #15482) | dogi |
| [#15347](https://github.com/open-learning-exchange/myplanet/pull/15347) | all: smoother user entity role checking (fixes #15483) | dogi |
| [#15351](https://github.com/open-learning-exchange/myplanet/pull/15351) | all: smoother retry interceptor parking (fixes #15484) | dogi |
| [#15354](https://github.com/open-learning-exchange/myplanet/pull/15354) | ⚡ perf: cache SimpleDateFormat instances | dogi |
| [#15357](https://github.com/open-learning-exchange/myplanet/pull/15357) | teams: smoother surveys repository testing (fixes #15485) | dogi |
| [#15358](https://github.com/open-learning-exchange/myplanet/pull/15358) | all: smoother tags repository testing (fixes #15486) | dogi |
| [#15359](https://github.com/open-learning-exchange/myplanet/pull/15359) | sync: smoother upload shelf service testing (fixes #15487) | dogi |
| [#15360](https://github.com/open-learning-exchange/myplanet/pull/15360) | sync: smoother download repository testing (fixes #15488) | dogi |
| [#15361](https://github.com/open-learning-exchange/myplanet/pull/15361) | all: smoother activities repository testing (fixes #15489) | dogi |
| [#15363](https://github.com/open-learning-exchange/myplanet/pull/15363) | all: smoother notifications repository testing (fixes #15490) | dogi |
| [#15364](https://github.com/open-learning-exchange/myplanet/pull/15364) | life: smoother health repository testing (fixes #15491) | dogi |
| [#15365](https://github.com/open-learning-exchange/myplanet/pull/15365) | teams: smoother voices posting policy testing (fixes #15492) | dogi |
| [#15366](https://github.com/open-learning-exchange/myplanet/pull/15366) | sync: smoother download service early returning (fixes #15493) | dogi |
| [#15368](https://github.com/open-learning-exchange/myplanet/pull/15368) | sync: smoother download service url resolving (fixes #15494) | dogi |
| [#15378](https://github.com/open-learning-exchange/myplanet/pull/15378) | all: smoother apk log building (fixes #15496) | dogi |
| [#15381](https://github.com/open-learning-exchange/myplanet/pull/15381) | courses: smoother progress repository testing (fixes #15497) | dogi |
| [#15383](https://github.com/open-learning-exchange/myplanet/pull/15383) | A31. Use payloads instead of full rebinds in VoicesAdapter | dogi |

### Wave 1B — 12 PRs that win a contended file

| PR | Title | Author | Displaces |
|---|---|---|---|
| [#15268](https://github.com/open-learning-exchange/myplanet/pull/15268) | course: improve step navigation and UI (fixes #15254) | **Okuro3499** | [#15300](https://github.com/open-learning-exchange/myplanet/pull/15300) |
| [#15286](https://github.com/open-learning-exchange/myplanet/pull/15286) | courses: smoother course filter controller scope canceling (fixes #15462) | dogi | — |
| [#15311](https://github.com/open-learning-exchange/myplanet/pull/15311) | teams: smoother repository flow flowing (fixes #15470) | dogi | — |
| [#15314](https://github.com/open-learning-exchange/myplanet/pull/15314) | sync: smoother personals repository uploading (fixes #15472) | dogi | [#15340](https://github.com/open-learning-exchange/myplanet/pull/15340) |
| [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) | **`priority`** Split LegacyEntityDaos.kt into per-DAO files | dogi | [#15169](https://github.com/open-learning-exchange/myplanet/pull/15169), [#15275](https://github.com/open-learning-exchange/myplanet/pull/15275), [#15321](https://github.com/open-learning-exchange/myplanet/pull/15321) |
| [#15322](https://github.com/open-learning-exchange/myplanet/pull/15322) | all: smoother shared preferences repository moving (fixes #15474) | dogi | [#15353](https://github.com/open-learning-exchange/myplanet/pull/15353) |
| [#15324](https://github.com/open-learning-exchange/myplanet/pull/15324) | teams: smoother teams sync repository daos hiding (fixes #15475) | dogi | [#15356](https://github.com/open-learning-exchange/myplanet/pull/15356) |
| [#15328](https://github.com/open-learning-exchange/myplanet/pull/15328) | sync: smoother settings repository boundaries moving (fixes #15476) | dogi | — |
| [#15331](https://github.com/open-learning-exchange/myplanet/pull/15331) | ⚡ Optimize N+1 DB insertion during crash log sweep | dogi | [#15296](https://github.com/open-learning-exchange/myplanet/pull/15296) |
| [#15333](https://github.com/open-learning-exchange/myplanet/pull/15333) | all: smoother user repository gson caching (fixes #15478) | dogi | [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) |
| [#15334](https://github.com/open-learning-exchange/myplanet/pull/15334) | courses: smoother progress fragment gson caching (fixes #15479) | dogi | [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) |
| [#15374](https://github.com/open-learning-exchange/myplanet/pull/15374) | all: smoother server reachability connecting (fixes #15495) | dogi | [#15379](https://github.com/open-learning-exchange/myplanet/pull/15379) |

[#15286](https://github.com/open-learning-exchange/myplanet/pull/15286), [#15311](https://github.com/open-learning-exchange/myplanet/pull/15311) and [#15328](https://github.com/open-learning-exchange/myplanet/pull/15328) displace nothing new — their only conflict partners ([#15307](https://github.com/open-learning-exchange/myplanet/pull/15307), [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327)) are already on hold for `priority`.

### Copy-paste

```bash
REPO=open-learning-exchange/myplanet
for pr in 15070 15259 15268 15269 15271 15277 15280 15281 15283 15286 15295 15298 \
          15304 15308 15311 15312 15314 15315 15316 15322 15324 15325 15328 15331 \
          15332 15333 15334 15337 15338 15341 15344 15347 15351 15354 15357 15358 \
          15359 15360 15361 15363 15364 15365 15366 15368 15374 15378 15381 15383; do
  echo "=== merging #$pr"
  gh pr merge "$pr" --repo "$REPO" --squash --delete-branch || { echo "STOPPED at #$pr"; break; }
done
```

---

## On hold — 6 PRs sacrificed to `priority`

These merge clean today and are review-complete. They are held **only** because they contend with a soon-ready PR. Label them `on hold`; unblock after the `priority` PR lands, then rebase.

| PR | Title | Author | Held for | Contended file |
|---|---|---|---|---|
| [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) | Refactor RealtimeSyncMixin table updates to Repositories | dogi | [#15446](https://github.com/open-learning-exchange/myplanet/pull/15446) | `ResourcesFragment.kt` |
| [#15305](https://github.com/open-learning-exchange/myplanet/pull/15305) | all: smoother user model caching (fixes #15468) | dogi | [#15446](https://github.com/open-learning-exchange/myplanet/pull/15446) | `CoursesFragment.kt`, `ResourcesFragment.kt` |
| [#15307](https://github.com/open-learning-exchange/myplanet/pull/15307) | Stop re-creating adapters in `BaseRecyclerFragment` | dogi | [#15446](https://github.com/open-learning-exchange/myplanet/pull/15446) | `CoursesFragment.kt`, `ResourcesFragment.kt` |
| [#15169](https://github.com/open-learning-exchange/myplanet/pull/15169) | courses leaving rejoining breaks (fixes #15156) | **ragilzakaria** | [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) | `LegacyEntityDaos.kt` |
| [#15275](https://github.com/open-learning-exchange/myplanet/pull/15275) | teams: smoother repository dao querying (fixes #15457) | dogi | [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) | `LegacyEntityDaos.kt` |
| [#15321](https://github.com/open-learning-exchange/myplanet/pull/15321) | all: smoother repository count querying (fixes #15473) | dogi | [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) | `LegacyEntityDaos.kt` |

**The priority rule costs 3 PRs.** Without it, greedy MIS reaches **51**; with it, 48. That is the whole price, and it buys [#15446](https://github.com/open-learning-exchange/myplanet/pull/15446) (57 files, `enormous`) and [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) a clean landing.

Two things worth knowing before you commit to it:

- **[#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) is the expensive half.** It is `priority` *and* `merge`, so it lands in Wave 1 and takes 3 PRs down with it — a net loss of 2. All four touch `LegacyEntityDaos.kt`, and #15316's whole job is to delete that file. Dropping #15316 to Wave 2 instead would put [#15169](https://github.com/open-learning-exchange/myplanet/pull/15169), [#15275](https://github.com/open-learning-exchange/myplanet/pull/15275) and [#15321](https://github.com/open-learning-exchange/myplanet/pull/15321) into Wave 1 and take the count to 50 — but then #15316 has to be re-derived against three moved DAO edits, which is more work than three rebases. **Keep it in Wave 1.**
- **[#15446](https://github.com/open-learning-exchange/myplanet/pull/15446) is nearly free.** Of its three conflictors, [#15307](https://github.com/open-learning-exchange/myplanet/pull/15307) and [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) were Wave 2 in the last plan anyway. Only [#15305](https://github.com/open-learning-exchange/myplanet/pull/15305) is a genuinely new sacrifice.

---

## Wave 2 — 7 PRs · each needs a rebase after Wave 1

Verified: each is clean today, conflicts after Wave 1, and none chains cleanly with the others. All are 1–6 file mechanical rebases.

| PR | Title | Author | Blocked by | Files |
|---|---|---|---|---|
| [#15300](https://github.com/open-learning-exchange/myplanet/pull/15300) | Refactor: Remove cross-repo passthroughs on CoursesRepository | dogi | [#15268](https://github.com/open-learning-exchange/myplanet/pull/15268) | 6 |
| [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) | Use injected Gson instead of constructing one at call sites | dogi | [#15333](https://github.com/open-learning-exchange/myplanet/pull/15333), [#15334](https://github.com/open-learning-exchange/myplanet/pull/15334) | 4 |
| [#15296](https://github.com/open-learning-exchange/myplanet/pull/15296) | all: smoother crash log sweeping (fixes #15464) | dogi | [#15331](https://github.com/open-learning-exchange/myplanet/pull/15331) | 1 |
| [#15340](https://github.com/open-learning-exchange/myplanet/pull/15340) | 🧪 Add tests for uploadPersonalDocument in PersonalsRepository | dogi | [#15314](https://github.com/open-learning-exchange/myplanet/pull/15314) | 1 |
| [#15353](https://github.com/open-learning-exchange/myplanet/pull/15353) | enterprises: smoother csv export date caching (fixes #15501) | dogi | [#15322](https://github.com/open-learning-exchange/myplanet/pull/15322) | 1 |
| [#15356](https://github.com/open-learning-exchange/myplanet/pull/15356) | teams: smoother repository date formatter caching (fixes #15498) | dogi | [#15324](https://github.com/open-learning-exchange/myplanet/pull/15324) | 1 |
| [#15379](https://github.com/open-learning-exchange/myplanet/pull/15379) | 🧹 Reduce deep nesting in MainApplication ANR listener | dogi | [#15374](https://github.com/open-learning-exchange/myplanet/pull/15374) | 1 |

One pair was flipped versus last plan's logic: [#15268](https://github.com/open-learning-exchange/myplanet/pull/15268) (Okuro3499) now beats [#15300](https://github.com/open-learning-exchange/myplanet/pull/15300) (dogi) on `TakeCourseViewModel.kt`, keeping the external contributor's PR intact. The swap is free — both have exactly one conflict edge, so Wave 1 stays at 48 either way.

---

## Wave 3 — 9 PRs already broken against today's `master`

Need a rebase before they are mergeable at all, independent of this plan.

| PR | Title | Author | Files |
|---|---|---|---|
| [#15289](https://github.com/open-learning-exchange/myplanet/pull/15289) | Delete dead repository interface surface | dogi | 20 |
| [#15285](https://github.com/open-learning-exchange/myplanet/pull/15285) | all: less realtime sync manager companion singleton is more (fixes #15461) | dogi | 8 |
| [#15294](https://github.com/open-learning-exchange/myplanet/pull/15294) | Add ViewModels for Leaders, Life, and SendSurvey fragments | dogi | 7 |
| [#15302](https://github.com/open-learning-exchange/myplanet/pull/15302) | Move storage scanning and deletion out of StorageCategoryDetailFragment | dogi | 7 |
| [#15303](https://github.com/open-learning-exchange/myplanet/pull/15303) | sync: smoother sync repository server pulling (fixes #15466) | dogi | 7 |
| [#15318](https://github.com/open-learning-exchange/myplanet/pull/15318) | Refactor: Remove adapter-side mutable working lists | dogi | 7 |
| [#15282](https://github.com/open-learning-exchange/myplanet/pull/15282) | Move sorting logic from adapters to viewmodels | dogi | 6 |
| [#15258](https://github.com/open-learning-exchange/myplanet/pull/15258) | settings: ensure no button is hidden (fixes #15257) | **Okuro3499** | 2 |
| [#15339](https://github.com/open-learning-exchange/myplanet/pull/15339) | 🧪 Add unit tests for ResourcesRepositoryImpl | dogi | 1 |

[#15289](https://github.com/open-learning-exchange/myplanet/pull/15289) is the worst offender for the third plan running — Wave 5, then Wave 3, now Wave 3 again, still 20 files. [#15285](https://github.com/open-learning-exchange/myplanet/pull/15285) and [#15303](https://github.com/open-learning-exchange/myplanet/pull/15303) are **new arrivals** here: both were Wave 1B last time and rotted while master moved. That is the direct cost of the plan sitting unexecuted.

---

## Impact on the 12 `ready` + 2 `priority` PRs

**All 14 are unaffected by Wave 1 — zero casualties.** Every one that is clean today is still clean after all 48 merges. This is the concrete payoff of the priority rule.

| PR | Title | Author | Labels | Today | After Wave 1 |
|---|---|---|---|---|---|
| [#15446](https://github.com/open-learning-exchange/myplanet/pull/15446) | UI: redesign courses/library with grid/list views (fixes #15440) | **Okuro3499** | `priority` `enormous` | clean | **clean** |
| [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) | Split LegacyEntityDaos.kt into per-DAO files | dogi | `priority` `merge` | clean | *merged in Wave 1* |
| [#15267](https://github.com/open-learning-exchange/myplanet/pull/15267) | prevent download popup dialog cropping when text size is large | **ragilzakaria** | `ready` | clean | clean |
| [#15276](https://github.com/open-learning-exchange/myplanet/pull/15276) | Add indices to the 8 entities that have none | dogi | `ready` | clean | clean |
| [#15284](https://github.com/open-learning-exchange/myplanet/pull/15284) | Fix areContentsTheSame on adapters whose model has no equals() | dogi | `ready` | clean | clean |
| [#15291](https://github.com/open-learning-exchange/myplanet/pull/15291) | Refactor bare lifecycleScope flow collections | dogi | `ready` | clean | clean |
| [#15297](https://github.com/open-learning-exchange/myplanet/pull/15297) | Remove UserSessionManager from EventsDetailViewModel | dogi | `ready` | clean | clean |
| [#15306](https://github.com/open-learning-exchange/myplanet/pull/15306) | Cancel stale coroutine jobs in long-lived fragments | dogi | `ready` | clean | clean |
| [#15317](https://github.com/open-learning-exchange/myplanet/pull/15317) | Refactor MyHealthFragment data layer behind health boundary | dogi | `ready` `enormous` | clean | clean |
| [#15319](https://github.com/open-learning-exchange/myplanet/pull/15319) | Guard SyncManager and SyncTimeLogger debug logging | dogi | `ready` | clean | clean |
| [#15437](https://github.com/open-learning-exchange/myplanet/pull/15437) | suppress download suggestion dialog in Courses and myCourses | **ragilzakaria** | `ready` | clean | clean |
| [#15274](https://github.com/open-learning-exchange/myplanet/pull/15274) | Refactor UserRepositoryImpl to use targeted UserDao queries | dogi | `ready` | conflict | conflict |
| [#15326](https://github.com/open-learning-exchange/myplanet/pull/15326) | Refactor IO-bound logic to ViewModels | dogi | `ready` | conflict | conflict |
| [#15443](https://github.com/open-learning-exchange/myplanet/pull/15443) | myLife: better visibility toggle and dashboard refresh (fixes #15236) | **Okuro3499** | `ready` | conflict | conflict |

Three already conflict with `master` today, before this plan touches anything.

**One collision inside the `ready` bucket, unrelated to Wave 1:** [#15267](https://github.com/open-learning-exchange/myplanet/pull/15267) and [#15437](https://github.com/open-learning-exchange/myplanet/pull/15437) both edit `BaseResourceFragment.kt` and conflict with each other. Both are ragilzakaria's. Whichever is approved first wins; the other rebases. Same for [#15306](https://github.com/open-learning-exchange/myplanet/pull/15306) ↔ [#15317](https://github.com/open-learning-exchange/myplanet/pull/15317) on `MyHealthFragment.kt`.

---

## Caveats

- Conflict detection is **textual**. A clean merge does not guarantee the combined result compiles or that `testDefaultDebugUnitTest` passes.
- Highest semantic risk in Wave 1: **`TeamsRepositoryImpl.kt`** ([#15271](https://github.com/open-learning-exchange/myplanet/pull/15271), [#15311](https://github.com/open-learning-exchange/myplanet/pull/15311), [#15322](https://github.com/open-learning-exchange/myplanet/pull/15322), [#15324](https://github.com/open-learning-exchange/myplanet/pull/15324) land together), the **DAO split** ([#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) deletes `LegacyEntityDaos.kt` while [#15295](https://github.com/open-learning-exchange/myplanet/pull/15295) renames Realm terminology across 20 files), and the **Gson cluster** ([#15332](https://github.com/open-learning-exchange/myplanet/pull/15332), [#15333](https://github.com/open-learning-exchange/myplanet/pull/15333), [#15334](https://github.com/open-learning-exchange/myplanet/pull/15334) land while [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) is deferred). Run the test suite mid-wave, not only at the end.
- Squash-merging moves `master`'s SHA at every step; any PR with a *required up-to-date branch* rule needs a branch update between waves.
- **Execute promptly.** Last plan's two-week delay turned [#15285](https://github.com/open-learning-exchange/myplanet/pull/15285) and [#15303](https://github.com/open-learning-exchange/myplanet/pull/15303) from Wave 1B into Wave 3. Re-run this analysis if `master` moves for any reason other than executing this plan.

---

## Suggested label actions

Not applied — these mutate PR state on GitHub.

1. **`on hold`** → the 6 held PRs: #15169, #15275, #15305, #15307, #15321, #15327.
2. **`automerge`** → the 48 Wave-1 PRs, so `.github/workflows/automerge.yml` can drain them.

---

## Title audit — 35 un-massaged PRs

Titles come in three shapes: **house** (`area: smoother thing verbing (fixes #N)` — massaged), **partial** (issue linked, no `area:` prefix — all external contributors), **raw** (untouched since the agent opened it).

Raw title is a proxy for *never triaged*, and untriaged PRs rot:

| Bucket | raw share |
|---|---|
| Wave 1 (merges clean) | 9 / 48 — **19%** |
| Wave 2 (displaced) | 4 / 7 — 57% |
| Wave 3 (**broken vs master**) | 6 / 9 — **67%** |
| `ready` + `priority` | 10 / 14 — 71% |

**Not one of the 30 raw PRs links a tracking issue.** Every proposal below is therefore marked *needs issue* rather than carrying a `(fixes #N)`; the 5 partials keep the issue they already link and only gain the `area:` prefix.

Used as a **triage-order hint, not a gate** — labels stay authoritative for what merges, so Wave 1 stays at 48 and `priority` #15316 stays in. Nothing here has been applied to any PR.


### Wave 1 — `automerge` live

Squash-merge bakes the title into `master` permanently. The urgent ones.

| PR | Author | Current title | Proposed | |
|---|---|---|---|---|
| [#15259](https://github.com/open-learning-exchange/myplanet/pull/15259) | ragilzakaria | display newly added image when editing a voice post (fixes #15253) | `teams: smoother voice post image editing (fixes #15253)` | issue linked |
| [#15269](https://github.com/open-learning-exchange/myplanet/pull/15269) | dogi | Optimize `ResourceSearchUtils` by caching normalized titles to fix search lag | `resources: smoother search utils title caching` | needs issue |
| [#15280](https://github.com/open-learning-exchange/myplanet/pull/15280) | dogi | Refactor UI State Collection in BaseDashboardFragment | `dashboard: smoother base fragment state collecting` | needs issue |
| [#15315](https://github.com/open-learning-exchange/myplanet/pull/15315) | dogi | Optimize NotificationUtils.getInstance allocations | `all: smoother notification utils instancing` | needs issue |
| [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) | dogi | Refactor: Split LegacyEntityDaos.kt into per-DAO files | `all: smoother legacy entity daos splitting` | needs issue |
| [#15325](https://github.com/open-learning-exchange/myplanet/pull/15325) | dogi | Refactor dashboard-triggered key sync out of BaseDashboardFragment | `dashboard: smoother key sync repository moving` | needs issue |
| [#15331](https://github.com/open-learning-exchange/myplanet/pull/15331) | dogi | ⚡ Optimize N+1 database insertion bottlenecks during crash log sweep operations | `all: smoother crash log inserting` | needs issue |
| [#15337](https://github.com/open-learning-exchange/myplanet/pull/15337) | dogi | 🧪 Add tests for ConfigurationsRepository | `all: smoother configurations repository testing` | needs issue |
| [#15354](https://github.com/open-learning-exchange/myplanet/pull/15354) | dogi | ⚡ perf: cache SimpleDateFormat instances to reduce allocation overhead | `enterprises: smoother date format caching` | needs issue |
| [#15383](https://github.com/open-learning-exchange/myplanet/pull/15383) | dogi | A31. Use payloads instead of full rebinds in VoicesAdapter | `teams: smoother voices adapter payloading` | needs issue |

### On hold for `priority`

Held behind a priority PR — time to fix the title before it re-enters the queue.

| PR | Author | Current title | Proposed | |
|---|---|---|---|---|
| [#15169](https://github.com/open-learning-exchange/myplanet/pull/15169) | ragilzakaria | courses leaving rejoining breaks (fixes #15156) | `courses: smoother leaving and rejoining (fixes #15156)` | issue linked |
| [#15307](https://github.com/open-learning-exchange/myplanet/pull/15307) | dogi | Stop re-creating adapters in `BaseRecyclerFragment` | `all: smoother recycler fragment adapter reusing` | needs issue |
| [#15327](https://github.com/open-learning-exchange/myplanet/pull/15327) | dogi | Refactor RealtimeSyncMixin table updates to Repositories | `sync: smoother realtime sync mixin repository moving` | needs issue |

### Wave 2 — needs rebase

Displaced by a Wave 1 PR. Needs a rebase anyway; retitle in the same pass.

| PR | Author | Current title | Proposed | |
|---|---|---|---|---|
| [#15273](https://github.com/open-learning-exchange/myplanet/pull/15273) | dogi | Use injected Gson instead of constructing one at call sites | `all: smoother injected gson reusing` | needs issue |
| [#15300](https://github.com/open-learning-exchange/myplanet/pull/15300) | dogi | Refactor: Remove cross-repo passthroughs on CoursesRepository | `courses: less repository passthrough is more` | needs issue |
| [#15340](https://github.com/open-learning-exchange/myplanet/pull/15340) | dogi | 🧪 [Testing Improvement] Add tests for uploadPersonalDocument in PersonalsRepository | `sync: smoother personals repository testing` | needs issue |
| [#15379](https://github.com/open-learning-exchange/myplanet/pull/15379) | dogi | 🧹 [Code Health] Reduce deep nesting in MainApplication ANR listener | `all: smoother anr listener nesting` | needs issue |

### Wave 3 — broken vs `master`

Already conflicts. Two thirds of this bucket was never massaged.

| PR | Author | Current title | Proposed | |
|---|---|---|---|---|
| [#15282](https://github.com/open-learning-exchange/myplanet/pull/15282) | dogi | Move sorting logic from adapters to viewmodels | `all: smoother adapter sorting view modelling` | needs issue |
| [#15289](https://github.com/open-learning-exchange/myplanet/pull/15289) | dogi | Delete dead repository interface surface | `all: less repository interface surface is more` | needs issue |
| [#15294](https://github.com/open-learning-exchange/myplanet/pull/15294) | dogi | Add ViewModels for Leaders, Life, and SendSurvey fragments | `all: smoother leaders life survey view modelling` | needs issue |
| [#15302](https://github.com/open-learning-exchange/myplanet/pull/15302) | dogi | Refactor: Move storage scanning and deletion out of StorageCategoryDetailFragment | `settings: smoother storage scanning repository moving` | needs issue |
| [#15318](https://github.com/open-learning-exchange/myplanet/pull/15318) | dogi | Refactor: Remove adapter-side mutable working lists | `all: less adapter working list is more` | needs issue |
| [#15339](https://github.com/open-learning-exchange/myplanet/pull/15339) | dogi | 🧪 Add unit tests for ResourcesRepositoryImpl | `resources: smoother repository testing` | needs issue |

### `ready` / `priority`

Pre-review. A raw title here marks the untriaged backlog, not a defect.

| PR | Author | Current title | Proposed | |
|---|---|---|---|---|
| [#15267](https://github.com/open-learning-exchange/myplanet/pull/15267) | ragilzakaria | prevent download popup dialog cropping when text size is large (fixes #15263) | `resources: smoother download dialog sizing (fixes #15263)` | issue linked |
| [#15274](https://github.com/open-learning-exchange/myplanet/pull/15274) | dogi | Refactor UserRepositoryImpl to use targeted UserDao queries | `all: smoother user repository dao querying` | needs issue |
| [#15276](https://github.com/open-learning-exchange/myplanet/pull/15276) | dogi | Add indices to the 8 entities that have none | `all: smoother entity index adding` | needs issue |
| [#15284](https://github.com/open-learning-exchange/myplanet/pull/15284) | dogi | Fix areContentsTheSame on adapters whose model has no equals() | `all: smoother adapter contents comparing` | needs issue |
| [#15291](https://github.com/open-learning-exchange/myplanet/pull/15291) | dogi | Refactor bare lifecycleScope flow collections | `all: smoother lifecycle scope collecting` | needs issue |
| [#15297](https://github.com/open-learning-exchange/myplanet/pull/15297) | dogi | Refactor: Remove UserSessionManager from EventsDetailViewModel | `teams: less events view model session managing is more` | needs issue |
| [#15306](https://github.com/open-learning-exchange/myplanet/pull/15306) | dogi | Fix: Cancel stale coroutine jobs in long-lived fragments | `all: smoother fragment job canceling` | needs issue |
| [#15317](https://github.com/open-learning-exchange/myplanet/pull/15317) | dogi | Refactor MyHealthFragment data layer behind health boundary | `life: smoother health repository boundary moving` | needs issue |
| [#15319](https://github.com/open-learning-exchange/myplanet/pull/15319) | dogi | Guard SyncManager and SyncTimeLogger debug logging | `sync: smoother debug logging guarding` | needs issue |
| [#15326](https://github.com/open-learning-exchange/myplanet/pull/15326) | dogi | Refactor IO-bound logic to ViewModels | `all: smoother io view modelling` | needs issue |
| [#15437](https://github.com/open-learning-exchange/myplanet/pull/15437) | ragilzakaria | suppress download suggestion dialog in Courses and myCourses (fixes #15435) | `courses: less download suggestion dialog is more (fixes #15435)` | issue linked |
| [#15446](https://github.com/open-learning-exchange/myplanet/pull/15446) | Okuro3499 | UI: redesign courses/library with grid/list views (fixes #15440) | `courses: smoother grid and list viewing (fixes #15440)` | issue linked |

---

## Automerge state — as executed

`automerge` was applied to all 48 Wave-1 PRs, then **pulled from the 9 whose titles were never massaged**.
Squash-merge bakes the PR title into `master` permanently, so a raw title is not a cosmetic problem on a PR
that is queued to land.

**Currently labelled `automerge`: 39.**

Pulled pending a retitle — re-add once renamed:
[#15269](https://github.com/open-learning-exchange/myplanet/pull/15269) ·
[#15280](https://github.com/open-learning-exchange/myplanet/pull/15280) ·
[#15315](https://github.com/open-learning-exchange/myplanet/pull/15315) ·
[#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) ·
[#15325](https://github.com/open-learning-exchange/myplanet/pull/15325) ·
[#15331](https://github.com/open-learning-exchange/myplanet/pull/15331) ·
[#15337](https://github.com/open-learning-exchange/myplanet/pull/15337) ·
[#15354](https://github.com/open-learning-exchange/myplanet/pull/15354) ·
[#15383](https://github.com/open-learning-exchange/myplanet/pull/15383)

⚠️ [#15316](https://github.com/open-learning-exchange/myplanet/pull/15316) is the `priority` PR that
[#15169](https://github.com/open-learning-exchange/myplanet/pull/15169),
[#15275](https://github.com/open-learning-exchange/myplanet/pull/15275) and
[#15321](https://github.com/open-learning-exchange/myplanet/pull/15321) are held behind — holding it for a
retitle holds those three too. Rename it first.

### Proposed: split `automerge` into two tiers

One flat label can't say *why* a PR isn't in the queue. Mirroring the repo's existing
`review first` / `review next` pair:

| Label | Meaning | Today |
|---|---|---|
| `automerge` | reviewed, massaged, merges clean — drain now | 39 |
| `automerge next` | would merge clean, but blocked on something cheap and known (raw title, pending rebase) | 9 + Wave 2's 7 |

That makes the drainer's input unambiguous while keeping the blocked set visible instead of unlabelled.
`automerge next` does not exist yet — it needs creating before it can be applied.
