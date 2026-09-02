# Phase 113 — the exam/survey ↔ course-step join

Phase 110 finished porting Kotlin's exam retry model and then reported that the
screen it had just hardened might be unreachable in production. **It was right,
and the cause is worse than it recorded**: the port did not merely fail to *join*
a course step to its exam — it never wrote the exam at all.

## The verdict

`TakeExamScreen` had two entries, `course_detail_screen.dart:190` and
`take_course_screen.dart:326`, and both resolved the exam through
`exams.stepId == course_steps.id`. Neither could ever match with real synced
data, for two independent reasons.

**1. A course step's test arrives embedded in the course document, and the port
threw it away.** `CoursesRepositoryImpl.buildCoursePayload`
(`CoursesRepositoryImpl.kt:670-698`) walks `doc["steps"]`, mints the step's own
id, and calls `collectRoomExam(stepJson, "exam", …)` and
`collectRoomExam(stepJson, "survey", …)` — which read `steps[i].exam` and
`steps[i].survey` and write them into the `exams` table with **that step id**
(`:745`) and the course id (`:746`). The port's `CourseMapper` parses steps and
nothing else; its doc comment says the exam halves "arrive with the `ui/exam`
package", and they never did. So the only writer of the port's `exams` table was
the `exams`-database walk, which took `stepId` from the document's own `stepId`
key.

That key does not exist. `StepExam.serializeExam` (`StepExam.kt:70-94`) — the
only exam serializer in the Kotlin app, and the payload of its only exam upload
path — emits no `stepId` and no `courseId`. **`stepId` is a purely
client-derived identifier with no server representation**, and the `exams` walk
passes `""` for it (`SurveysRepositoryImpl.kt:387` →
`StepExam.checkIdsAndInsert`, which skips a blank argument). The information
exists only in the course document's `steps` array. The port was reading a key
the Kotlin never writes and the server never sends.

**2. The exams walk rejected every real course test anyway.**
`ExamMapper.fromDoc` required `type == 'exam'`. That is the value
`insertCourseStepsExams` uses only as a *fallback* for a document with no `type`
key at all. **No Kotlin query anywhere filters the exams table on
`type = "exam"`.** The complete set of type filters is `"courses"`
(`CoursesRepositoryImpl.kt:196`, `:530`), `"surveys"` (nine sites), and one
stray singular `"survey"` (`SubmissionsRepositoryImpl.kt:367`). A course test
carries `type: "courses"` — `:530`, `getByStepIdAndType(stepId, "courses")`, is
the sole thing that shows Kotlin's Take Test button — so the port's mapper
returned `null` for it and `deleteNotIn` then deleted the row if anything else
had written it.

Phase 110 called half of this settled in-repo and half an inference. The
inference is now as settled as it can be without a Planet server: the
`"courses"` type filter is not a Room-era invention but verbatim from the Realm
implementation (`91f63abd:CoursesRepositoryImpl.kt:543-552`) and has shipped for
years; rows with `type = "surveys"` must exist for the individual-surveys screen
and team survey adoption to work at all, and the only producers of either are
the two walks copying the document's own `type`.

**So both entries into `TakeExamScreen` were dead, and so was the step view's
Take Survey button** — surveys share the whole path, `collectRoomExam` included.
Phase 110's parting question ("check whether surveys are too") answers yes.

Every port fixture had to hand-fake `'stepId': 'step-1'` because there was no
other way to get a row the lookup could find.

## What changed

`ExamMapper.fromCourseDoc` and `SurveyMapper.fromCourseDoc` port
`collectRoomExam` for the `"exam"` and `"survey"` keys, sharing one
`ExamMapper.mapStepExams` step walk, and `CoursesRepository.sync` upserts their
rows alongside the courses and steps — where Kotlin's `upsertRoomCoursesFromSync`
does (`CoursesRepositoryImpl.kt:650-651`). The exam id is the embedded document's
`_id`, falling back to `"$courseId-$stepId-$examKey"`; the step id is
`CourseMapper.stepIdFor`, passed in rather than imported so the mappers stay
independent.

`ExamMapper.fromDoc`'s accept rule became **"anything that is not a survey"**,
which is Kotlin's: `bulkInsertExamsFromSync` parses every document of the
database and the survey/test split happens later, at query time. The port has no
`type` column — it splits into two tables what Kotlin keeps in one — so the split
has to happen at parse, and this is the only rule that reproduces the Kotlin's
partition.

`fromDoc` on both mappers now writes `stepId`/`courseId` as `Value.absent()`
rather than `Value(null)` when the document omits the key. Same shape as the
Phase 56 security-data fix and the Phase 74 reactions round trip: two writers,
one column, and the one that knows nothing must not overwrite the one that does.

`stepExamProvider` moved off `ExamDao.getByStepId` (whose `getSingleOrNull`
throws on two rows) to `getByStepIds([id]).firstOrNull`, matching
`ExamDao.getFirstByStepId`'s `LIMIT 1`. Not hypothetical: `adoptSurvey`
(`SurveysRepositoryImpl.kt:181-182`) copies `stepId` and `courseId` onto a
**new UUID row**, so a shareable course-step survey can legitimately produce two
rows for one step. `course_detail_screen.dart`'s duplicate `examForStepProvider`
— the same query under a second name — is gone in favour of it.

No schema change (still v45), no table or converter touched, so no
`build_runner` step. No new `app_en.arb` key.

## Defects, each demonstrated failing first

| # | defect | pre-fix failure |
|---|---|---|
| 1 | **the courses walk dropped the step's exam and survey entirely** | probe: after `CoursesRepository.sync` of a course whose step embeds an exam, `examDao.getByStepId('course-1:0')` → `Expected: not null / Actual: <null>`; same for `surveyDao.getByStepId` → `Expected: an object with length of <1> / Actual: <[]>` |
| 2 | **the exams walk rejected a `type: "courses"` document** | probe: `ExamMapper.fromDoc({'_id': 'exam-1', 'type': 'courses', …})` → `Expected: not null / Actual: <null>` |
| 3 | **the exams walk cleared the join on the next pull** | demonstrated by reverting only `_presentOrAbsent` back to `Value(...)`: `the exams walk keeps the step exam the courses walk wrote` fails on `the exams walk must not clear the join it has no way to know about` |
| 4 | **a course test was deleted as an unknown type** | demonstrated by reverting only the accept rule to `type != 'exam'`: `a course test is not pruned as an unknown document type` fails with `Expected: not null / Actual: <null>` — the row the courses walk wrote is not in the exams walk's keep set, so `deleteNotIn` removes it in the same sync pass |

Defects 3 and 4 are worth separating from 1 and 2 because they are not about
getting the row written — they are about the row surviving the rest of one sync.
The port syncs `DashboardSyncArea.courses` (index 1) **before** `.surveys`
(index 4), and the surveys area owns `deleteNotIn` for both tables, so a step
exam written at index 1 is offered for deletion at index 4 minutes later. It
survives only because a course test is also a document of the `exams` database
(the embedded object carries a CouchDB `_rev`, meaningless on anything unstored;
and the Realm-era course walk prefetched the existing `exams` rows by those
embedded ids) **and** because the mapper now accepts it. `step_assessment_sync_test.dart`
pins that interaction, because it is exactly the kind of agreement between two
files that a later tidy-up breaks silently.

## Reachability, confirmed on the screens

Two widget tests fill an in-memory database from a real-shaped course document
through the real mappers and then assert the buttons appear, with
`stepExamProvider`/`stepSurveysProvider` **not** overridden — so the join is the
thing under test rather than the button:

- `course_detail_screen_test.dart`: `a step carrying an embedded exam offers Take exam, from a real sync`
- `take_course_screen_test.dart`: `the step view offers Take test and Take survey for an embedded pair`

**The payoff, stated plainly: the graded-course-exam work of Phases 100 and 106
is now reachable.** Certified-course verification photos, the retry gate,
`mistakes`, the `"$examId@$courseId"` parent id — all of it sat behind a button
that could not appear.

## The three consequences Phase 110 asked about

**Per-step mistake totals (`progress_repository.dart:72-75`) are now computed
correctly — and still are not on screen, for a different reason.**
`ProgressRepository.courseProgress`, which returns `CourseStepProgress` with
`questionCount` and `totalMistakes`, has **no caller in `lib/` at all**; the only
callers are its own tests. So Phase 110's defect 7 (the `parentId` fix) now
joins correctly to an exam that exists, and `progress_repository_test.dart` has
a test proving it, but nothing renders the result. That is a separate gap and
this phase does not close it. What *is* on screen — the courses list's
"My Progress" mistake counts (`courses_providers.dart:404-438`) — never used this
join; it matches submissions to courses with `parentId.contains(courseId)`.

**Surveys share the problem exactly.** Same `collectRoomExam`, same step id, same
absent join. Fixed in the same shape, and `stepSurveysProvider` now returns rows.

**Certified-course exams: reachable.** See above.

## Deliberate divergences

1. **A `steps[i].survey` with no `type` key is filed as a survey.** Kotlin types
   it `"survey"` — singular — and then only ever queries for `"surveys"`
   (`CoursesRepositoryImpl.kt:531`), so such a row is reachable from neither
   button. (This is itself a Room-era regression: the Realm implementation
   defaulted to the literal `"exam"` for both call sites, so `"survey"` could
   never be produced and `SubmissionsRepositoryImpl.kt:367` was dead code;
   `07b11362` changed the default to the key and made `:367` live and `:531`
   dead for these rows. The two are mutually exclusive over the same rows and
   both are live code — a contradiction the Kotlin has not noticed.)
   Reproducing it would mean putting a survey in the exams table and offering it
   as a graded test, which is worse than Kotlin's silence rather than equal to
   it.
2. **A `steps[i].exam` with no `type` key is filed as an exam.** Kotlin writes
   `"exam"` and `:530` then returns nothing, so the Take Test button is hidden
   for a test that exists — silently, with no error. The port shows it.
3. **The port's step exam and step survey cannot be confused.** Kotlin's
   `getFirstByStepId` (`ExamDao.kt:13`) has no type filter and no `ORDER BY`, so
   on a step carrying both, which row the exam button opens is unspecified SQL —
   it happens to be the exam because `collectRoomExam` is called for `"exam"`
   first and both go into one `upsertAll`. The port's two tables make the
   question unaskable.

## A second audit, on the finished implementation

Phase 110's harder-won lesson, repeated exactly: **an audit of the ground truth
does not audit the implementation.** The first pass established the Kotlin and
the fix was green — format clean, analyze clean, 1822 tests, CI green on
`7a1b78a9`. A second pass pointed at my own diff found **twelve** findings, four
of them serious, and one of my new tests then uncovered a thirteenth.

The pattern worth recording: *every* one of the serious findings was hidden by
the friendliest possible fixture. `step_assessment_sync_test.dart` embedded the
**same map objects** in the course document that it served from the exams
database, so the two copies were byte-identical, always had an `_id`, and always
carried questions. That single shape hid four separate defects.

| # | defect | pre-fix failure |
|---|---|---|
| A | **the exams walk deleted step assessments in the same sync pass.** I had reasoned that a course test is also an `exams` document so it is in that walk's keep set — true for the happy case, and false for four classes of row: a locally derived id (Kotlin's `ifBlank` fallback), an `_id` this satellite has not replicated, a step survey whose standalone document is not `type: "surveys"`, and `total_rows == 0` on the exams database, which runs `deleteNotIn([])` and empties both tables. `complete`/`hadBatchFailure` does not help: these ids are structurally absent from the keep set however complete the walk | `a derived-id step assessment survives the exams walk prune` — the row is written by the courses area and gone after the surveys area |
| B | **nothing ever cleared a stale `stepId` again.** `Value.absent()` fixed the clobber and removed the only mechanism that ever *retired* a join. Kotlin can afford that: its step ids are a hash of the step's own JSON. The port's are positional (`'$courseId:$index'`), so deleting a step shifts every later id down one and the removed step's exam silently reappears under whatever step took its slot | `removing a step releases the exam it used to carry` — step `course-1:0` inherited `exam-1` |
| C | **both step buttons navigated nowhere.** `'${Routes.exam}/${exam.id}'` concatenates the route *pattern*: `/courses/exam/:examId/exam-1`, which matches no route and lands on go_router's error page. The survey button was wrong twice over — same pattern bug, and it aimed at `PublicSurveyScreen` (anonymous, network-fetched) where Kotlin's `btnTakeSurvey` opens the signed-in user's own submission | `the step view offers Take test…` — `Found 0 widgets with text "EXAM_ROUTE exam-1"`; `Record survey opens the signed-in survey screen` — `Found 0 widgets with text "SURVEY_ROUTE survey-1"` |
| D | **`stepExamProvider` cached a pre-sync `null` for the process lifetime.** A `FutureProvider.family` with no `autoDispose` and nothing invalidating it: open a course before the sync writes the join and the step never offers its test until restart. Latent before, because nothing ever wrote the join; now the sole gate on the feature. Kotlin re-reads in `CourseStepFragment.onViewCreated` | reasoning, then `.autoDispose` |
| E | **a `steps[i].exam` typed `surveys` was dropped entirely.** My `accept` filter skipped it on the reasoning that "it is a survey there too" — but nothing then filed it as one, because `SurveyMapper.fromCourseDoc` only read the `survey` key. Kotlin writes it and its Take Survey button finds it. A test had pinned the loss as intended | `leaves a step exam that declares itself a survey to SurveyMapper` |
| F | **a duplicate question id aborted the whole courses walk.** `batch.insertAll` raises `UNIQUE constraint failed` where Kotlin's `@Upsert` lets the later row win, and neither `sync` catches — so the remaining pages and the cleanup are skipped and the area fails. Reachable without adversarial data: the id falls back to a positional `$examId-$index`, which a later question may carry as its own `id` | `duplicate question ids do not produce a duplicate row` |
| G | **the thirteenth, which the fix for A/B surfaced rather than the audit.** `exam_questions.id` is the question's own document id, unique only *within* an exam — so **two different exams carrying a question `q1` collide globally**. My new test hit it immediately. Kotlin has the same primary key and loses one question silently to `@Upsert`; the port threw. Fixed where Kotlin fixes it, at the DAO: `insertAllOnConflictUpdate` | `SqliteException(1555): UNIQUE constraint failed: exam_questions.id` out of `SurveysRepository.sync` |
| H | **the courses walk wiped questions the exams walk wrote.** `ExamDao.upsertAll` deletes an exam's questions before reinserting the map entry, and I wrote an entry for every step exam including an empty list — so a course embedding an exam with no `questions` array emptied it. Kotlin's `questionDao` never deletes | `the courses walk does not wipe questions the exams walk wrote` |
| I | **Take test appeared on a course the learner had not joined.** `CourseStepFragment.onViewCreated` hides *both* buttons when `!userHasCourse`, after `hideTestIfNoQuestion` has shown them. The port computed `isMyCourse` and passed it only to the navigation bar | `a course the learner has not joined offers neither` |
| J | **`noOfQuestions` disagreed between the two walks.** Kotlin counts the raw array on both (`getJsonArray("questions", exam).size()`); `fromDoc` counted the parsed rows, so a non-object entry made the same row's count depend on which walk ran last | `noOfQuestions counts the raw array, as the Kotlin does` |

**And one gap the join made fixable, which the diff had left on the table.**
`CourseStepFragment.saveCourseProgress` passes
`if (stepExams.isEmpty()) true else null` (`:97`): a step with no test is passed
by reaching it. The port passed `null` unconditionally — because until this phase
nothing could tell the two apart, `stepExams` being precisely the join that did
not exist. **A course with no tests could therefore never complete.** Now
ported.

Fixes for A and B, and for G, are in `app_database.dart`: `deleteNotIn` on both
tables spares rows with a non-null `stepId` (Kotlin has no prune here at all, so
sparing is the closer behaviour), a new `releaseStepJoinsForCourse` on each DAO
detaches the assessments a course document no longer claims — which hands the
spared row back to the prune, so sparing is not immortality — and the question
batches upsert instead of insert.

Two findings were left as reported rather than fixed, both outside the join:
Kotlin relabels the buttons `retake_test`/`redo_survey` from
`hasSubmission(...)`, which the port does not; and `SurveyDao.getByCourseId` has
no type filter where Kotlin's is `getByCourseIdAndType(courseId, "survey")` —
singular, i.e. only type-less embedded step surveys — so the mandatory-survey
block now fires for step surveys Kotlin's query would never match. That block is
gated to one hardcoded onboarding course, so the blast radius is a single course.

## For the integrator

- **Files outside Lane A's set were touched, all reported rather than assumed.**
  None is owned by Lane B (`lib/l10n/`) or Lane C (`lib/ui/teams/`,
  `teams_provider`, `teams_repository*`):
  - **`lib/data/local/app_database.dart`** — the largest crossing, and not
    optional: without it the feature loses data on every sync (defect A above).
    `ExamDao.deleteNotIn` and `SurveyDao.deleteNotIn` gain a
    `stepId IS NULL` restriction; each DAO gains `releaseStepJoinsForCourse`;
    the two question batches move from `insertAll` to
    `insertAllOnConflictUpdate`. No schema change — no column, no index, no
    version bump.
  - `lib/providers/app_providers.dart` — two added arguments to
    `coursesRepositoryProvider`, because `CoursesRepository` now needs `ExamDao`
    and `SurveyDao` to write the rows the course document carries.
  - `test/repository/exam_flow_test.dart` — no behaviour change; one test
    renamed and commented, because `an exam is reachable from its step` was
    passing on a fixture shape the server does not produce and read as if it
    covered the real path.
  - `test/support/` untouched.
- **The `exams`/`surveys` prune is now a two-writer question.** Both
  `deleteNotIn`s belong to the `exams` database walk but the tables no longer
  belong to it alone; the `stepId IS NULL` restriction and
  `releaseStepJoinsForCourse` are a pair and neither is safe to remove without
  the other. Both carry the reasoning at the code.
- **`ProgressRepository.courseProgress` has no UI caller.** Worth a phase: the
  per-step question count and mistake total it computes are exactly what
  Kotlin's `ProgressGridAdapter` renders.
- **One Kotlin behaviour deliberately not chased**, recorded so a later phase
  does not read it as a gap: `07b11362` dropped the Realm survey path's
  `createdDate = courseCreatedDate` override
  (`91f63abd:CoursesRepositoryImpl.kt:818-822`), so a course-step survey's
  `createdDate` now comes from the survey document. `getSurveys` sorts on that
  column. The port follows current master.
