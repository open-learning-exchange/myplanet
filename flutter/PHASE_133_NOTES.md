# Phase 133 — harvesting the 39 commits master had drifted by

Lane C of a three-lane parallel round. Branch `claude/harvest-master-39-v7c3`,
off `claude/kotlin-flutter-dart-migration-d3gmrd`. PR #16930.

Two tasks: harvest the largest upstream backlog this port has had, and record
Phase 113's `exam`/`survey` table routing in the migration doc's deviations
list, which had never carried it.

## Result in one paragraph

39 commits, **every one of them titled "smoother X" or "less X is more"**. Four
are real Follows. Two of those I ported (`3002830`, `64140ca`); two are blocked
on files this lane does not own and are in *Reported, not fixed* below. The
merge was clean — no conflicts, and `flutter/` is untouched by master, which has
no such directory. The remaining 35 are refactors, ANR fixes, memoisation,
logging, or fixes the port already has independently — but four of those 35 got
their *verdict* right and their *reasoning* wrong in the first pass, which is
recorded below because a wrong rationale is a defect in this project.

## Method

The batch's title shape is exactly the one Phase 126 was burned by: two
"smoother X" commits there turned out to be a new sync step and a wire-format
change stamping `app=myplanet` on every uploaded document. Phase 95 dismissed a
batch as refactors and Phase 96 re-read the same batch and found a ranked search
the port never had. So:

1. `harvest-triage` over the full range for a first pass.
2. **Every Follow re-read against the Kotlin by hand**, plus an independent hand
   pass on the commits whose subject touched a wire format, a sync/upload step, a
   query predicate, a filter/sort/ranking, or the caching of an identity or URL.
   I started that pass before triage returned, so the two are genuinely
   independent readings rather than one confirming the other.
3. `git merge origin/master`, then codegen, then the gate.
4. Two `parity-auditor` passes at `effort: max` — one on the ground truth (is my
   Kotlin reading right?), one on the finished, already-green implementation.
   Phases 110, 113, 116 and 119 each found more defects in the second.

## Verdicts — all 39

`F` = Follow, `—` = no counterpart / no port impact, `✓` = the port already has
the fixed behaviour independently.

| Commit | Subject (abbrev.) | | Note |
|---|---|---|---|
| `63a8308` | ratings repository viewer prompt coordinating | **F** | **New feature + new table.** Blocked: schema bump |
| `ed5609f` | base exam progress user scoping | **F** | **Data correctness.** Blocked: Lane A's file. Bigger than it looks — see below |
| `64140ca` | filter label formatting | **F** | **Ported.** Media-type filter chips showed raw server strings |
| `3002830` | activities repository dao counting | **F** | **Ported.** Blank titles + tie-break |
| `7167684` | chat history model payload sharing | — | Extraction. Not byte-identical (see below), but the port has no chat share at all |
| `97239df` | url utils base caching | ✓ | Not just memoisation — see below |
| `1ac4874` | repository shelves caching | — | A *move* of an existing 6h cache out of `SyncManager`, not a new sync step. Checked because it looked like one |
| `348fd92` | upload pipeline batch coordinating | — | Filter moved after chunking, retries queued per batch. Same items attempted; the port's `OutboxDrainer` is a different design |
| `4b0dc21` | retry queue concurrent working | — | Bounded concurrency (semaphore of 6). Outcomes and the pause-on-sync rule unchanged |
| `e6c17d1` | photo batch uploading | ✓ | Same concurrency change **plus** a real fix: success now gated on `response.isSuccessful`, not `body != null`. The port's `SubmitPhotosUploader` already gates on `NetworkSuccess`, which splits on HTTP status |
| `9c037f2` | plugin life route mapping | ✓ | Kotlin stopped matching localised title strings and now routes on a stable `imageId` — a latent i18n bug. `life_features.dart` already routes on an internal key |
| `4b7236b` | utilities text normalizing | — | ASCII fast path. NFD is identity on ASCII and the diacritics regex matches only combining marks, so byte-identical. Verified by hand, not taken on the title |
| `a650e59` | voices repository list inserting | — | Dedupe refactor, same output |
| `9a693d3` | server url mapping | — | `printStackTrace` → `Log.w`, an empty `.also {}` inlined |
| `2cd4b39` | progress repository sync inserting | — | `LinkedHashSet` dedupe for `.filter{}.distinct()`, same set |
| `9ebb92b` | health repository examination view modelling | — | Decrypt moved ViewModel → repository |
| `a0730c3` | repository visibility view modelling | — | Mutators return the updated list, saving a re-query. Same end state |
| `f10e9a2` | notifications repository counting | — | Extracts a shared count helper, same thresholds |
| `ca6dac1` | notifications repository querying | — | Skips a count call whose value was unused either way |
| `5ee7986` | android decrypter logging | — | `printStackTrace` → `Log.e`. **Does not touch the iteration count**, which is still the hard-coded 10 that quirk 1 pins |
| `a977a51` | personal repository resources dao updating | — | Read-mutate-write collapsed into one `UPDATE ... COALESCE` |
| `9c9dceb` | reports flow view modelling | — | Drops a needless `suspend` and a redundant `launch {}` |
| `92ac611` | dictionary repository word view modelling | — | Adds a DTO so the repository stops leaking the Room entity |
| `e30246b` | progress repository querying | — | Drops three identity `.map { it }` calls |
| `ace1922` | news model message without markdown | — | Deletes a dead property, zero references |
| `59e2e69` | user session full name | — | Deletes a dead field. Its `init` could throw at construction; the field was never read |
| `268bbe3` | network utils device identity caching | — | Memoises constant-per-process values |
| `e6c5ad4` | performance logging | — | Gates log lines on `Log.isLoggable` |
| `c3dc1c2` | resources courses requests view modelling | — | Parallelises independent repository calls |
| `21e999f` | download service notification caching | — | Foreground service with no Flutter analogue |
| `a6a4306` | reports csv exporting | — | Filter+sort moved into SQL `ORDER BY`; identical set and order |
| `959c5e4` | resources inline disk checking | — | ANR fix; Dart I/O is already off-thread |
| `9d572b4` | achievements cv editing | — | ANR fix; same output file |
| `a3ffde1` | download status filter resetting | — | Removes a conflicting click listener |
| `bf85950` | resources creation dispatcher providing | — | `resolveUriToPath` off the main thread |
| `cc89b3f` | time utils date formatting | — | `Calendar` → `LocalDate`; both emit wall-clock with a literal `Z`, neither converts |
| `8bc36a4` | json utils primitive factoring | — | Shared `getPrimitive` helper, same parse rules |
| `59af331` | service module application scoping | — | Adds a `CoroutineExceptionHandler` to the app scope |
| `15a21b7` | flexbox library migrating | — | Bundled `.aar` → AndroidX artifact, plus native chip layout |

## What the hand re-read changed

Four verdicts survived but their reasoning did not, and one mapping was wrong.
Recording these because "a no-counterpart verdict on a rename is the cheapest
place in this project to lose a behaviour" cuts both ways: a *correct* verdict
reached by a wrong route is one coin-flip from a wrong one.

**`97239df` is not "memoisation, and Dart is single-isolate so it does not
matter".** The commit adds a `cachedBaseUrl`, yes — but its substance is that
`setCouchdbUrl`, `setProcessedAlternativeUrl` and `setIsAlternativeUrl` were
one-line `pref.edit {}` calls that **invalidated nothing**, so the existing
header cache could serve a stale credential after a server switch. It renames
`invalidateHeaderCache` to `invalidateCaches` and calls it from all three. The
port is unaffected for a different and much better reason: `PlanetPrefs` writes
`_cachedPin`/`_cachedCouchDbUrl` through on every save (`planet_prefs.dart:183`)
and clears them on delete (`:201`), so it has no separate cache to go stale.

**`1ac4874` looked like a new sync step and is not.** The 6-hour
`shelves_with_data` cache with its comma-joined value already existed inline in
`SyncManager`; the commit moves it verbatim into `SyncRepository`. I checked this
one specifically because "a new caching layer in the sync path" is the Phase 126
data-loss shape, and it is worth writing down that it was checked and cleared.

**`7167684` is not byte-identical, and its counterpart mapping was wrong.**
`ChatSharePayload.buildShareMap` writes `(chat.title ?: "").trim()` where the
adapter wrote `"${chatHistory.title}".trim()` — a Kotlin string template of a
null renders the literal `"null"`, so a null-titled chat used to be shared with
the title `null` and is now shared with `""`. That is a genuine (if small) wire
change. It has no port impact, but not because the port matches: the port has
**no chat share at all** — `chat_history_screen.dart` contains no share action
and nothing in `flutter/lib` builds this payload (`aiProvider` appears only in
the chat detail screen and provider). Logged as a gap below.

**`ed5609f` is a bigger finding than "add a `userId` filter".** Details below.

## What I ported

### `3002830` — most-opened resource

`ORDER BY openCount DESC, title ASC` with `WHERE ... title IS NOT NULL AND
TRIM(title) != ''` replaced a `groupBy`/`maxByOrNull`. Three consequences, all
followed in `activities_repository.dart`:

- A whitespace-only title used to win and render as an empty stat.
- The exclusion is in `WHERE`, so it happens **before** the grouping: an untitled
  open no longer counts toward any resource's total, and a group whose *first*
  row happened to be untitled is no longer discarded wholesale — the old code
  read `group.first().title` and dropped the group on that one row alone.
- Ties break on title ascending instead of on iteration order.

Two new tests, both checked against the pre-fix implementation and red on it
(Phase 122's rule: a test that cannot fail reads as coverage and is worse than
none). The tie-break test inserts `Zoology` before `Anatomy` precisely so the old
`count > best.count` — strict, so first-max-wins — returns the other answer.

### `64140ca` — media-type filter labels

The filter lists whatever `mediaType` strings the synced rows carry, so its chips
were showing raw server values. `mediaTypeDisplayName` ports the seven-case
mapping. Two details that are easy to get wrong:

- Kotlin's `setAdapter` takes `label: (String) -> String = { it }` and only
  `listMedium` passes a mapper, so the language/subject/level lists still show
  raw values **in both apps**. The port's `labelFor` parameter is optional for
  the same reason.
- Only the *label* is mapped. The chip's value stays the raw medium, because that
  is what `ResourceFilter.mediaTypes` matches `MyLibraryRow.mediaType` against —
  mapping the value would have been the writer/reader key disagreement this
  project keeps finding.

All five ARB keys were **recovered, not generated**: `filter_pdfs`,
`filter_videos`, `filter_audio` and the two the commit itself added
(`medium_text_html`, `medium_html`) all ship human translations in every
`values-*/strings.xml`. That is why the pinned human-reviewed counts in
`placeholder_integrity_test.dart` move by the same five in all five locales,
Nepali and Somali included. `image` and `other` reuse `storageImages` and
`storageOther`, whose text is identical to Kotlin's `storage_images` and `other`
— the port's `storageOther` carries the generic "Other", not `storage_other`'s
"Other files" — rather than adding duplicate keys, which `app_en.arb` is guarded
against since Phase 60.

## Reported, not fixed

**1. `ed5609f` — exam grading and course progress. Two problems, one blocked.**

The commit adds `AND userId IS :userId` to
`CourseProgressDao.updatePassedByCourseAndStep` and threads the exam-taker's id
through `CoursesRepositoryImpl.updateCourseProgress`. Before it, passing an exam
for step N flipped `passed = true` for **every** row at `(courseId, stepNum)`
regardless of owner — on a shared device, one learner's pass completed the step
for everyone.

The port reproduces the old behaviour *and documents it as intentional*:
`progress_repository.dart:423` says "Flips `passed` to `[passed]` for every
user's row on `(courseId, stepNum)` ... An exam result is not per-user", and
`app_database.dart:3412` repeats it. **That rationale is now contradicted by
upstream and should be deleted along with the behaviour.** The fix is
`ed5609f` verbatim: a nullable `userId` parameter with `IS` semantics so a null
still matches null rows. It needs no schema bump (a `WHERE` clause changes no
DDL) — but `app_database.dart` is **Lane A's file**, so this lane did not write it.

**The larger finding, which the commit surfaced rather than caused:
`updateCourseProgress` has zero callers in the port.** Not in `lib/`, not in
`test/` — verified by grep across the whole `flutter/` tree. It is dead
plumbing, the Phase 119 shape ("a Dart writer already sitting uncalled"). The
consequence is concrete and is the Phase 113 class:

- `take_course_screen.dart:196` writes `passed: exams.isEmpty ? true : null`, so
  a step **with** an exam is deliberately left unpassed for the exam to grade.
- Nothing then grades it. `take_exam_screen.dart` touches
  `progressRepositoryProvider` only for `isCourseCertified`; the Kotlin does this
  at `ExamTakingFragment.kt:848`.
- `completedCourseIds` requires **every** step `passed`
  (`progress_repository.dart:215`).

So in the port **a course containing any exam step can never complete**, and the
completed-course stars on the home dashboard can never light for it. Fixing it
means calling `updateCourseProgress` from the exam submit path
(`take_exam_screen.dart`, which no lane owns) with the userId scoping above
(`app_database.dart`, which Lane A owns). Worth a lane of its own next round,
with a test that drives a real course-with-exam to completion — no current
fixture does.

**2. `63a8308` — the resource-viewer rating prompt is entirely unported. Needs a
schema bump, which no lane was allocated.**

Kotlin now asks the learner to rate a resource when they finish it and back out
of the viewer, once per `(user, resource)`. It needs: a `rating_prompt_log`
table (`userId`, `item`, `type`, `promptedAt`, primary key on the first three);
`RatingsRepository.isRatingPrompted`/`setRatingPrompted`; and an exit
coordinator. The predicate is `shouldShowResourceRatingDialog` = *not already
prompted* **and** *not already rated* (`summary.userRating == null &&
summary.existingRating == null`, with a `CancellationException` rethrow and any
other throw treated as "not rated"). `ResourcesExitCoordinator` guards re-entry
with a `handled` flag, bails to `finish()` for a blank resource id, an
unfinished resource or a blank user id, checks
`supportFragmentManager.isStateSaved` before showing, and records
`setRatingPrompted` **before** the dialog is shown, not after it is answered —
so declining still counts as prompted.

The port has none of it: no table, no repository methods, and no exit hook in
`resource_viewer_screen.dart`. This is a not-yet-ported feature, not a
regression. It needs a Drift table at the next allocated `schemaVersion`, and
the table is a local-authority one — it records something no server document
carries — so it belongs in `localAuthorityTables`.

**3. The chat share-to-team dialog is unported.** Surfaced by `7167684`.
`ChatHistoryAdapter` lets a learner attach a note and share a whole conversation
into a team's voices feed, serialising the conversation list into a nested `news`
sub-object. `chat_history_screen.dart` has no share action. Note when porting
that the payload nests the chat under `news` as a JSON *string* and sets
`chat: "true"` — the same nested-vs-top-level trap as the Phase 74 reactions
round trip, so test the pair.

**4. `ne`/`so` remain at 5 keys richer but structurally far behind.** Nothing to
do here; noted only because this phase moved their counts and a reader comparing
against the CLAUDE.md table will see the drift.

## The deviations-list entry (task 2)

`docs/kotlin-to-flutter-migration.md` carried four quirks and six deviations and
none of them was Phase 113's `exam`/`survey` routing, which Phase 128's step-tile
count, its buttons' presence and its take-vs-retake wording all rest on. Added
two entries, sourced from the "What the ground-truth audit overturned" section of
`PHASE_128_NOTES.md` and the `stepAssessmentProvider` doc comment:

- **The `exams` database is split into two tables at insert time**, because the
  port has no `type` column where Kotlin has one and splits at *query* time. The
  rule has to be "`type == 'surveys'` goes to `SurveyMapper`, everything else is
  an exam" — requiring `type == 'exam'` is what made `TakeExamScreen` unreachable
  in production, since that value is only ever an absent-key fallback and a real
  course test carries `type: "courses"`, which is never assigned anywhere in the
  Kotlin tree.
- **A step's Take Test button opens the row its own label interrogated.**
  Kotlin's `hasExam` filters on `type`, but `ExamDao.getFirstByStepId` does not,
  so on a step carrying both an exam and a survey the Kotlin button can open the
  survey row the label never asked about. A deliberate improvement, preserved
  rather than introduced.

## Gate

Merge was clean. `flutter/` is untouched by master (it has no such directory), so
no codegen was strictly required by the merge — it was run anyway before trusting
`analyze`, per the standing rule.

Kotlin CI runs on this push and should: the merge brings real `app/` changes onto
the branch, so `paths-ignore` does not skip `build.yml`/`test.yml`. That is the
filter working.
