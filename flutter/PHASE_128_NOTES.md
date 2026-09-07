# Phase 128 — the step tile's *Redo survey* / *Retake test* labels

Lane A of a three-lane round. One missing feature, named by Phase 125 as its
own successor (`PHASE_125_NOTES.md` §"Found, not fixed" item 1) and correctly
so: this is **the only user-visible Kotlin reader of the composite survey
`parentId`**, and Phase 125's writer fix is its precondition.

`CourseStepFragment.hideTestIfNoQuestion` (`:241-262`) picks each assessment
button's wording off `CourseStepData.hasExam`/`hasSurvey`, which are
`submissionsRepository.hasSubmission(stepExams[0].id, step.courseId, userId,
"exam"/"survey")` (`CoursesRepositoryImpl.kt:536-546`). The port hardcoded
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

Two tests are **guards, not failing-first evidence**, and are labelled so rather
than counted above: "does not confuse the two types" and "is false for another
user" pin `countByUserParentAndType`'s existing `type`/`userId` clauses, which
predate this diff — reverting them means deleting an argument pass-through,
which proves nothing about a decision anyone might make.

`test/repository/step_tile_label_test.dart` is new (13 tests) and named for the
shape rather than the file, like `mandatory_survey_round_trip_test.dart`. Its
first test asserts the mappers really set `courseId` on the exam and the survey,
because the rest of the file proves nothing if they did not — Phase 113's exact
failure. **No fixture hand-writes a `parentId`**: every submission is authored
by `startExamSession` or `createSurveyDraft` against rows
`ExamMapper.fromCourseDoc` / `SurveyMapper.fromCourseDoc` produced from a
document shaped like the server's. `take_course_screen_test.dart` gains 5.

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

## Found, not fixed

**1. `resourcesInStep` counts a different thing from Kotlin's `btnResources`,
and reads differently too.** Kotlin sets
`getString(R.string.resources_size, data.resources.size)` — "Resources [%d]" on
`myLibraryDao.getByStepId(stepId)`, the rows actually on the device. The port
renders `l10n.resourcesInStep(step.noOfResources)`, an ICU plural ("No
resources" / "1 resource" / "{count} resources") on the **step document's own
declared count**. Those disagree whenever a step's resources have not all
synced, which is the normal case on a fresh install, and the plural means the
key can never derive a translation from the Kotlin XML (the tool skips plurals
for good reason). Same tile, one line above the two this phase fixed;
deliberately out of scope because it is a second feature, not this label swap.

**2. `takeTest` is a dead ARB key.** See the l10n section — removing it needs
Lane C's files.

**3. Phase 125's items 2–6 are all still open**, and item 2 (a locally-authored
submission duplicating when pulled back, because `upsertDocuments` keys on the
document `_id` while the local row's key is a sha1) now bears on this phase too:
a duplicated submission row would not change the label — `countByUserParentAndType`
is a `> 0` count — but it would double it in the list and the exporter, and the
machinery to prevent it (`SubmissionDao.getByIdOrRemoteId`) is still sitting
unused by the walk.

**4. Nothing invalidates `stepAssessmentProvider` when an attempt is made.**
`autoDispose` is what makes the label refresh: the learner leaves the step to
sit the exam, the provider's last listener goes, and re-entering re-reads. That
is the same argument `stepExamProvider`'s own doc makes and it is load-bearing
here for a new reason — a `PageView` keeps its neighbours alive, so a learner
who returns to the *same* step without the page being disposed could see a stale
label. I did not reproduce it and it needs a widget test that drives the real
exam screen, which no test in the port does yet.
