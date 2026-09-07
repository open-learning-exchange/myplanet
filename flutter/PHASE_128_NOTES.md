# Phase 128 — the step tile's *Redo survey* / *Retake test* labels

Lane A of a three-lane round. One missing feature, named by Phase 125 as its
own successor (`PHASE_125_NOTES.md` §"Found, not fixed" item 1) and correctly
so: this is **the only user-visible Kotlin reader of the composite survey
`parentId`**, and Phase 125's writer fix is its precondition.

`CourseStepFragment.hideTestIfNoQuestion` (`:241-261`) picks each assessment
button's wording off `CourseStepData.hasExam`/`hasSurvey`, which are
`submissionsRepository.hasSubmission(stepExams[0].id, step.courseId, userId,
"exam"/"survey")` (`CoursesRepositoryImpl.kt:537-546`). The port hardcoded
`l10n.takeTest` and `l10n.recordSurvey`, so **a learner who had already sat the
test was invited to take it for the first time, every time** — and the count
Kotlin puts in that label was absent entirely.

Unlike the mandatory-survey gate Phase 125 fixed, **this reader works in the
shipping Android app**: `getCourseStepData` reads the *plural*-typed rows
(`getByStepIdAndType(stepId, "surveys")`, `:534`), which is what a survey
document actually carries, where `hasUnfinishedSurveys`' Kotlin original reads
the singular `"survey"` and therefore matches nothing. So this is restored
parity, not internal consistency.

## What the tile says in each state

| state | label |
|---|---|
| exam on the step, no attempt by this user | **take test [N]** |
| exam on the step, an attempt exists for `stepExams[0]` | **Retake Test [N]** |
| exam on the step, its questions have not synced yet | **take test [N]** — the `countByExamId == 0` guard |
| exam present, attempt belongs to another user | **take test [N]** |
| survey on the step, no answer sheet | **Record survey** |
| survey on the step, a sheet exists (any status) | **redo survey** |
| course the learner has not joined, or a guest | neither tile |

`N` is `exams.length`, the whole list, while the boolean reads only
`exams.first` — Kotlin's quirk (`getString(R.string.take_test, exams.size)`
against `hasSubmission(stepExams[0].id, …)`), ported rather than corrected and
pinned by a test.

Two states are worth stating because the `false` branch means something
different here than it does at `hasUnfinishedSurveys`' call site. There, `false`
means *finished*; here it means *not yet taken*. So every guard in
`hasSubmission` inverts into "offer a first attempt": a blank user id, a step
whose `courseId` never synced, and an exam whose questions have not arrived all
read as untaken rather than blocking anything. That is why the
`questionDao.countByExamId(stepExamId) == 0 → false` rule **is** ported here and
deliberately is **not** ported in `hasUnfinishedSurveys`, where it would invert
into "a question-less survey blocks the course forever".

The label is also status-blind, as Kotlin's `countByUserParentAndType` is
(`SubmissionDao.kt:23`): merely *opening* an exam attempt makes the tile read
"Retake". Faithful.

## The `%d` count keys derived their translations — all three, all five locales

**Yes, and this is worth recording because it is the first time the Phase 121
placeholder conversion has paid off on a new key.** Three keys were added to
`app_en.arb`, worded to match the Kotlin English exactly:

| key | English | Kotlin source |
|---|---|---|
| `takeTestCount` | `take test [{count}]` | `take_test` = `take test [%d]` |
| `retakeTestCount` | `Retake Test [{count}]` | `retake_test` = `Retake Test [%d]` |
| `redoSurvey` | `redo survey` | `redo_survey` = `redo survey` |

Running `dart tool/arb_from_strings_xml.dart` derives a human translation for
**all three in all five locales** — ar/es/fr/ne/so, 15 values, every one already
shipping in the Android app. I ran it to check and then reverted the five locale
files, because they and the tool belong to Lane C. `app_en.arb` is the only l10n
file this diff touches.

**Integrator: a derivation run is wanted, and it adds 5 keys per locale, not 3.**
The other two are `playbackSpeed` and `playbackSpeedValue`, template keys that
arrived with the Phase 126/127 harvest merge (`6b6e9c8`) and have never been
derived; the run picks them up as well. All five are human translations from the
XML and therefore unflagged, so
`test/l10n/placeholder_integrity_test.dart`'s `humanReviewed` map moves by +5 in
every locale: ar 410→415, es 462→467, fr 409→414, ne 411→416, so 411→416. That
test file is Lane C's too.

The exact literal English is why it worked, and it is a real trade-off rather
than a free win: **`Take test [{count}]` would have derived nothing.** The tool
matches a format string on its literal with the holes removed, exactly and
case-sensitively (`arb_from_strings_xml.dart` header, "Matching is exact literal
only"), so one capital letter forfeits five human translations. `take test [3]`
reads worse in English than `Take test [3]`; five reviewed translations beat one
capital, and Kotlin's own lowercase is the specification. Same reasoning for
`redo survey`. If anyone prefers the capital later, the price is named here.

Three consequences I did **not** act on:

* **`takeTest` is now dead** — its only caller was the line this phase replaced.
  Deleting it from `app_en.arb` would orphan its value *and* its `x-mt`
  metadata block in ar/es/fr, which `test/l10n/placeholder_integrity_test.dart`
  ("no x-mt flag outlives the string it marks") checks; both are Lane C's files.
  Left in place, unused, for whoever does the next l10n pass. It is the only key
  I know of in that state.
* **`takeTest` could not simply have grown a `{count}`.** ar/es/fr carry values
  for it with no placeholder, so declaring one would generate a getter taking an
  argument those three locales silently drop — the Phase 109 defect class, and
  `placeholder_integrity_test`'s second test would fail on it.
* **`recordSurvey` is "Record survey" where Kotlin is "Record Survey"**, which
  is why it derived by *shared English* rather than by name in the first place.
  It already has five human translations and I left it alone: replacing one
  valid translation with another is not a repair.

## The `hasSubmission` port, and where the port cannot copy the Kotlin

`SubmissionsRepository.hasSubmission` (`submissions_repository.dart`, in the
read-path region beside `hasUnfinishedSurveys`) is a straight port of
`SubmissionsRepositoryImpl.hasSubmission` (`:176-192`) with one unavoidable
divergence.

**Kotlin keeps every question, exam and survey alike, in one `exam_questions`
table keyed by `examId`** (`QuestionDao.kt:13`), because it keeps every exam and
survey in one `exams` table distinguished by `type`. The port split both pairs
— `Exams`/`Surveys` and `ExamQuestions`/`SurveyQuestions` — so the count has to
pick its table from the `type` argument, the same value Kotlin would have read
off the `exams` row. Copying the Kotlin literally (always `ExamQuestions`) is a
live defect, not a harmless simplification: it finds nothing for a survey, the
guard short-circuits, and **every answered survey reads as unanswered**.
Demonstrated failing.

Phase 52 ported `hasSubmission` once already, as `hasUnfinishedSurveys`, and
neither is reusable for the other — the brief was right to warn. They differ on
the question-count guard (above), on the blank-user guard's meaning, and on
direction: `hasUnfinishedSurveys` loops a course's surveys asking "is any one
missing", this asks about one named exam. They now sit next to each other with
each divergence documented at the code.

No DAO change and no schema change. `SubmissionDao.countByUserParentAndType`
already existed (Phase 125), and the question counts reuse
`ExamDao.questionsFor` / `SurveyDao.questionsFor`. `schemaVersion` stays **46**
and no generated output moved, so no `build_runner` run was needed.

## The provider

`courses_providers.dart` gains `stepAssessmentProvider`, a `FutureProvider
.autoDispose.family` keyed on `({stepId, courseId, userId})` returning
`({exams, surveys, hasExam, hasSurvey})` — the assessment half of Kotlin's
`CourseStepData`, computed the way `getCourseStepData` computes it.

`stepExamProvider` (nullable single row) is kept for `course_detail_screen` and
now derives from a new **`stepExamsProvider`** (the list), because
`getByStepIdAndType(stepId, "courses")` is a list and its *size* is what the
label carries. `take_course_screen`'s `_recordProgress` moved to the list too,
so its `passed` argument is literally Kotlin's `if (stepExams.isEmpty()) true
else null`.

`_StepContent` now takes `userId`. It watches one provider where it used to
watch two, and the two tiles read their visibility, count and wording off it.
Both tiles' `onTap` awaits its `context.push` and then calls a local
`refreshAssessment` — see defect 5 below for why that is not optional, and why
the `push` literal has to stay at the call site.

## Each defect, with failing-first evidence

Each row names the exact revert it was replayed against, because two of the four
are decisions rather than omissions and a test that only asserts "the label is
right" cannot tell them apart.

| # | defect | revert replayed | pre-fix failure |
|---|---|---|---|
| 1 | **a learner who has taken the test is still told to take it** | both tiles' labels back to `l10n.takeTest` / `l10n.recordSurvey` | `Expected: exactly one matching candidate / Actual: Found 0 widgets with text "Retake Test [1]"` — 5 tests red |
| 1b | the same for the survey half | as above | `Found 0 widgets with text "redo survey"` |
| 1c | the label carried no count | as above | `Found 0 widgets with text "take test [1]"` |
| 2 | **a survey's question count read `ExamQuestions`** (Kotlin's single table, copied literally) | the `type == 'survey'` table choice | `Expected: true / Actual: <false>` in the repository, and `Found 0 widgets with text "redo survey"` at the screen — 4 tests red |
| 3 | an exam whose questions have not synced claims an attempt | the `questions == 0` guard | `Expected: false / Actual: <true>` |
| 4 | a second exam's submission swapped the label | `exams.first` widened to "any exam" | `Expected: false / Actual: <true>` |
| 5 | **the label could not change within a session** | `pushAndRefresh` back to a bare `context.push` | `Expected: exactly one matching candidate / Actual: Found 0 widgets with text "Retake Test [1]"` |

Two tests are **guards, not failing-first evidence**, and are labelled so rather
than counted above: "does not confuse the two types" and "is false for another
user" pin `countByUserParentAndType`'s existing `type`/`userId` clauses, which
predate this diff — reverting them means deleting an argument pass-through,
which proves nothing about a decision anyone might make.

Defect 5 is the one the ground-truth audit found and it is the most important
of the five, because without it the other four guard a label the learner cannot
watch change. **Kotlin recreates the fragment and the port does not.**
`btnTakeTest` calls `openCallFragment` (`CourseStepFragment.kt:282`) →
`FragmentNavigator.replaceFragment(…, addToBackStack = true)`, a `replace()`
transaction, so popping back rebuilds `CourseStepFragment`, `onViewCreated`
runs again and `getCourseStepData` re-queries. `context.push` leaves
`TakeCourseScreen` mounted, so `_StepContent` keeps its listener, the
`autoDispose` family member is never disposed and its future never re-runs — the
label stayed on *take test [1]* until the learner left the course entirely.
The fix awaits the pop and invalidates.

**And the port's own reachability guard caught the first version of that fix.**
Folding `await context.push(location)` into a helper made the navigation
unreadable, and `test/ui/route_reachability_test.dart` failed with
`[_NavSite:take_course_screen.dart:379 -> location]` — its message offers a
`declared` exception map and that would have been the wrong door to take. The
route literals stay at each `onTap` and only the invalidate is factored out.
Phase 116 wrote that guard after finding four unreachable screens; this is the
first time it has fired on a *new* navigation rather than an audit.

That is the same argument `stepExamProvider`'s own doc comment already makes one
level down ("a step opened before the sync wrote its exam answered `null` for
the rest of the process"), unsatisfied one level up. **When a screen's state
depends on something another screen writes, `autoDispose` is not the refresh —
the pop is.**

`test/repository/step_tile_label_test.dart` is new (13 tests) and named for the
shape rather than the file, like `mandatory_survey_round_trip_test.dart`. Its
first test asserts the mappers really set `courseId` on the exam and the survey,
because the rest of the file proves nothing if they did not — Phase 113's exact
failure. **No fixture hand-writes a `parentId`**: every submission is authored
by `startExamSession` or `createSurveyDraft` against rows
`ExamMapper.fromCourseDoc` / `SurveyMapper.fromCourseDoc` produced from a
document shaped like the server's. `take_course_screen_test.dart` gains 5, and three of its existing
expectations move from `Take test` to `take test [1]`.

## `submissions_repository.dart` — which regions this lane touched

**Read path only, one addition, no edits to anything that existed.**

| region | change |
|---|---|
| between `_repairSurveyParentId` and `hasUnfinishedSurveys` | **added** `hasSubmission` (+ its doc comment). Nothing else in the file is modified — no line of `createExamSubmission`, `serializeSubmission`, the team-object block, or any other write-path method is touched. |

Lane B owns the write path in the same file. `git diff` on it shows exactly one
insertion hunk.

## Files touched outside the read path

| file | why |
|---|---|
| `lib/l10n/app_en.arb` | the three keys above; additions only |
| `lib/providers/courses_providers.dart` | `stepExamsProvider`, `stepAssessmentProvider`, and `stepExamProvider` re-expressed as the list's head |
| `lib/ui/courses/take_course_screen.dart` | the two tiles, `_StepContent.userId`, `_recordProgress` on the list |
| `test/ui/courses/take_course_screen_test.dart`, `test/repository/step_tile_label_test.dart` | the tests |

No overlap with Lane C (`lib/ui/notifications/`, the five locale files,
`tool/arb_from_strings_xml.dart`, `test/l10n/`) except the two hand-offs named
under the derivation section, which are reports rather than edits.

## What the ground-truth audit overturned

Three things I had wrong or would have got wrong, all found by a
`parity-auditor` pass at `effort: max` before the implementation was finished.

**A two-exam step is port-only, so the `[%d]` is structurally `[1]` in the
Android app.** I justified the list-returning provider by "a step can carry
several exams". It cannot, in Kotlin: `steps[i].exam` is read with
`getAsJsonObject` so it is one object per step, and the standalone `exams` walk
calls `insertCourseStepsExams("", "", jsonDoc, "")` whose `checkIdsAndInsert`
skips blank ids, leaving `stepId` null — so nothing Planet sends produces two
rows sharing a `stepId`. The **port** can: `ExamMapper.fromDoc` writes a
document's own `stepId` (`_presentOrAbsent`), which is a Phase 110 deviation
made on purpose. So the `exams.first`-decides quirk is ported for fidelity to
what the Kotlin *says*, on a state only the port can reach, and the test that
pins it says so rather than implying a Kotlin scenario. The list shape is still
right — it is what `getByStepIdAndType` returns and where the `1` comes from.

**`hideTestIfNoQuestion` reads no question count.** The name promises the guard
is about questions; it hides on *list emptiness* and the only question count in
the whole feed is inside `hasSubmission`, where it changes the label's **wording**
and never a button's presence — the inversion of what the name says. The Dart
provider is named for what it computes.

**Kotlin's Take Test button can open a row its own label never interrogated, and
the port is already better.** `hasExam` asks about `stepExams[0]`, selected by
`stepId = ? AND type = 'courses'`; the button routes through
`BaseExamFragment.initExam` → `ExamDao.getFirstByStepId`, which is
`WHERE stepId = ? LIMIT 1` with **no type filter** — so on a step carrying both
an exam and a survey it can open the survey row. The port pushes
`exams.first.id`, the same row the label read, so label and destination cannot
disagree. That property predates this phase; this diff preserves it
deliberately rather than by accident, and it is now documented at the provider.

Two more worth recording because they explain the Kotlin's odd surface:
`type = "courses"` is **never assigned anywhere in the Kotlin tree** — it is the
server's value copied verbatim, and the in-tree *fallbacks* are `"exam"` and
`"survey"` singular, which no Kotlin query ever selects. And
`CourseStepFragment.onCreateView` (`:79-80`) forces both buttons VISIBLE with
their XML defaults, so between view creation and the data arriving Kotlin shows
the literal string `take test [%d]` — percent-d and all — and `Take Survey`, a
*third* string that is not part of this swap. Pre-load flicker; deliberately not
ported.

## Found, not fixed

**1. The resources tile above the two this phase fixed is the port's own
affordance, and it has a chevron that does nothing.** `take_course_screen.dart`
renders `ListTile(… trailing: Icon(Icons.chevron_right))` with **no `onTap`** —
same in `course_detail_screen.dart`. Kotlin's live analogue is
`BaseContainerFragment.setResourceButton`, reached from
`CourseDetailFragment`, and it opens a **download dialog**.
`CourseStepFragment`'s own `btnResources` is **dead code** on two independent
counts (it sits inside `legacy_buttons_container`, declared
`android:visibility="gone"` with nothing anywhere making it visible, and
`setListeners` sets the button GONE at `:292`), so the number Kotlin computes
at `:121-122` is never rendered. My first draft of this item claimed the port
diverged from a live Kotlin count; it does not, because there is no live Kotlin
count. What remains true and worth logging: the port's
`l10n.resourcesInStep(step.noOfResources)` is an **ICU plural**, which
`tool/arb_from_strings_xml.dart` skips for good reason, so that key can never
derive a translation from the Kotlin XML — and the chevron promises a
navigation nothing implements.

**2. `takeTest` is a dead ARB key.** See the l10n section — removing it needs
Lane C's files.

**3. Phase 125's items 2–6 are all still open**, and item 2 (a locally-authored
submission duplicating when pulled back, because `upsertDocuments` keys on the
document `_id` while the local row's key is a sha1) now bears on this phase too:
a duplicated submission row would not change the label — `countByUserParentAndType`
is a `> 0` count — but it would double it in the list and the exporter, and the
machinery to prevent it (`SubmissionDao.getByIdOrRemoteId`) is still sitting
unused by the walk.

**4. `TakeCourseFragment.changeNextButtonState` is unported** — the next-step
lock for `MANDATORY_SURVEY_COURSE_ID` (`TakeCourseFragment.kt:315-338`), which
refuses Next with `please_complete_test`/`please_complete_survey`. It reads the
**same** `CourseStepData` this phase now reads, but off
`stepExams.isNotEmpty()`/`stepSurvey.isNotEmpty()` rather than
`hasExam`/`hasSurvey`, and the port's `_NavigationBar` has no equivalent. A
separate gap, not made worse by this diff — but the next lane to touch this
screen has the provider it would need already in place.

**5. Kotlin's Take Test button can open a row its own label never asked about,
and none of the port's type-routing deviations are in
`docs/kotlin-to-flutter-migration.md`.** Both are recorded in the code (see the
`stepAssessmentProvider` doc); neither is in the migration doc's *Faithful
quirks / deliberate deviations* list, which still carries four quirks and six
deviations, none of them Phase 113's `exam`/`survey` table routing. This phase's
count, the buttons' presence and the take-vs-retake wording all rest on that
routing. **Integrator: that list is the place for it, and this file is not the
lane's to edit.** The text is the "What the ground-truth audit overturned"
section above.

**6. Stale Kotlin line citations are widespread, and I fixed only my own
region.** The ground-truth audit found them in `exam_mapper.dart` (three),
`survey_mapper.dart` (one), `app_database.dart` (one, an
`ExamDao.getByStepIdAndType(stepId, "survey")` that is actually the plural) and
in `examParentId`'s doc (`createExamSubmission` is `:446-453`, not `:449-456`).
I corrected the citations inside `hasUnfinishedSurveys`' doc, since leaving them
wrong next to the function I had just ported correctly would be worse; the rest
belong to other lanes' files or to the write path. Given that misreading a
correctly-named function is this project's most expensive recurring failure, a
pass that just fixes citations would be cheap and worth a lane.
