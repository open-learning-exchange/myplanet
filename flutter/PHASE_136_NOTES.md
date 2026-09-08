# Phase 136 — an adopted survey clone loses its course

Lane C of a four-lane round. Branch `claude/adopted-survey-course-key-j3v7`,
based on `claude/kotlin-flutter-dart-migration-d3gmrd`. PR #16937.

Brief: `SurveysRepositoryImpl.createMappedSurvey` copies `courseId` **and**
`stepId` onto an adopted team clone; the port's `adoptSurvey` copied neither,
and Phase 132's Send fix turned that omission into Phase 125's writer/reader
key disagreement. Two field copies plus proving the chain.

It was not two field copies. One of the two must **not** be copied, the reader
had to change to stay satisfiable, and the sentence in the brief that says
Kotlin's gate counts the clone as "one more requirement" is describing dead
code.

## `createMappedSurvey`, read field by field

`SurveysRepositoryImpl.kt:162-192` makes **nineteen** assignments — every one
of `StepExam`'s constructor properties. Nothing is left at a default.

| Kotlin | source | port |
|---|---|---|
| `id = newSurveyId` | minted | ✓ |
| `_rev = null` | literal | ✓ **now explicit** |
| `createdDate` / `updatedDate` / `adoptionDate` | three separate `now()` calls | ✓ (one timestamp) |
| `createdBy = userModel?.id` | adopting user | ✓ |
| `totalMarks` | source | ✓ |
| `name = "${exam.name} - $teamName"` | source + team | ✓ |
| `description` | source | ✓ |
| `type = exam.type` | source | **no column** |
| `stepId = exam.stepId` | source | **withheld — see below** |
| `courseId = exam.courseId` | source | ✓ **this phase** |
| `sourcePlanet` | source | ✓ |
| `passingPercentage` | source | ✓ |
| `noOfQuestions` | source | **no column** |
| `isFromNation` | source | ✓ |
| `teamId = teamId` | argument, not `exam.teamId` | ✓ |
| `sourceSurveyId = examId` | source id | ✓ |
| `isTeamShareAllowed = false` | literal | ✓ |

`type` and `noOfQuestions` have nothing to lose: the port splits Kotlin's one
`exams` table into `Surveys` and `Exams` on the document's own `type` and then
discards it, and the marker's `parent` document computes the question count
from the rows. Neither is a gap.

`_rev = null` and `isTeamShareAllowed = false` are the two assignments that
look like noise and are load-bearing: `ExamDao.kt:21` queues the clone for
upload with `sourceSurveyId IS NOT NULL AND _rev IS NULL`, and
`isTeamShareAllowed = false` is what keeps the clone out of the adoptable list
and out of the individual list. The port already had the second; `rev` is now
written explicitly rather than left absent, so an upsert onto an existing id
cannot inherit a stale rev and silently un-queue the clone from the uploader
this port still owes (see *Reported, not fixed*).

## `courseId` is copied and `stepId` is not

`stepId` is the one field where copying Kotlin's assignment would be copying a
word whose meaning differs between the trees, and the port's own sync machinery
would punish it. Proven, not reasoned:

* Kotlin's step ids are a hash of the step's JSON; the port's are **positional**
  (`CourseMapper.stepIdFor`, `'$courseId:$index'`). Because deleting a step
  shifts every later id down one, the courses walk *owns* the join and retires
  any row its course document does not name —
  `SurveyDao.releaseStepJoinsForCourse` nulls `stepId` **and `courseId`
  together**.
* A clone is minted locally and is in no course document, so it is never in
  that keep set. Probed on a clone-shaped row:
  `releaseStepJoinsForCourse('course-1', {'survey-1'})` →
  `courseId=null stepId=null`. Copying `stepId` would hand the next courses
  sync a licence to strip the very `courseId` this phase writes.
* With `stepId` null the update cannot select the row at all
  (`stepId IS NOT NULL`), so the course join holds.

Withholding it also keeps the clone off the course step's Take Survey button
(`stepSurveysProvider` → `getByStepId`), whose *count* the port renders to the
learner. Kotlin's `getByStepIdAndType(stepId, "surveys")` does list every
team's clone there — a quirk worth not importing.

## The reader had to change, and the brief's premise is dead code

With `courseId` on the clone, `SurveyDao.getByCourseId` returns clones, and
`hasUnfinishedSurveys` iterates exactly that query. Left alone it would demand
that **every learner on the course complete every team's private copy** — a
learner outside the adopting team has no sheet for it and never can have one.
That is Phase 125's bug re-entered from the writer's side, and the guard
`'the gate does not demand a team's private copy'` fails on the unfiltered
version (mutation-tested: `Expected: false / Actual: <true>`).

So `hasUnfinishedSurveys` now skips `sourceSurveyId != null` — Kotlin's own
test for "this row is an adopted copy" (`ExamDao.kt:21`).

**That is not a divergence from a live Kotlin behaviour.** Both branches of the
Kotlin fork are broken, and the audit pinned both:

* `getSurveysByCourseId` (`:367-369`) filters
  `getByCourseIdAndType(courseId, "survey")` — **singular** — while every
  survey in the app, clone included (`type = exam.type`), carries `"surveys"`:
  the take-survey button (`:531`), all three survey lists, `ExamDao`'s
  defaults. So for a normally shaped document the gate matches nothing at all
  and `hasUnfinishedSurveys` is constant `false`.
* The only writer of the singular value is `CoursesRepositoryImpl.kt:746`'s
  `else examKey` — a step survey whose embedded document omits its own `type`.
  There the gate does match, and matches the clone, but
  `getTeamOwnedSurveys` filters `"surveys"`, so no screen lists that clone and
  the obligation is one nothing can discharge.

`repairCourseSurveyParentIds` deliberately does **not** skip clones: a clone's
member sheets are exactly the rows a pre-Phase-136 build keyed bare, so the
sweep is how they heal. The two loops want opposite things from the same query,
and the doc comment now says so.

## Two more defects in the same function

* **The adoption marker's `parent` document disowned the course.** Kotlin's
  `createParentJsonString(exam)` is handed the **source** exam — resolved at
  `:73`, ten lines before the clone's id exists — and writes
  `put("courseId", exam.courseId ?: "")`. The port hardcoded `''`. Confirmed by
  the audit: the `parent` blob describes the source throughout (`_id` = source
  id, name without the `" - $teamName"` suffix, `teamShareAllowed` = the
  source's `true`).
* **The marker's `user.doc` had no `name`.** Kotlin writes
  `_id, name, userId, teamPlanetCode, status, type, createdBy` under `doc`
  (`:139-147`). `adoptSurvey` gains a `userName` parameter, filled from the
  session user the screen already watches. It is **omitted rather than sent as
  null** because that JSON is built with `org.json`, whose
  `put(String, Object)` removes the key for a null value — the same asymmetry
  that makes `_id`/`name` disappear while `userId`/`createdBy` become `""`.

`teamPlanetCode`, `source` and `parentCode` are left as they are: the audit
confirms Kotlin takes them from `SharedPrefManager.getPlanetCode()`/
`getParentCode()` — the **device's** codes, not the user row's — and
`serialize`'s doc comment already files them under the port-wide
*community-code parity gap*. Wiring the user row's values at this one call site
would have been a plausible-looking wrong answer.

## Where Kotlin is worse and the port is deliberately not following

Three, all confirmed by the ground-truth audit and all already true of the port
before this phase; recorded so nobody "restores parity" into them.

1. **Kotlin blanks every cloned question's text.** `adoptSurvey:90-93` round-trips
   the questions through `serializeQuestions` → `insertExamQuestions`, which
   writes the header under `header` and reads it back from `title`, so
   `header` becomes `""`; question ids are renumbered `"<newSurveyId>-<i>"` and
   `scaleMax` resets to 9. The port copies the question rows directly.
2. **Kotlin's adoption dedup is dead on the team path.** `createMappedSubmission`
   sets only the `@Ignore`d `membershipDoc` and never the persisted `teamId`
   column, so `findExistingAdoption`'s `getByUserIdAndTeamId` can never match.
   The port's `createSurveyAdoptionSubmission` writes the column, so its guard
   works.
3. **Kotlin's Send button is unreachable.** `SurveysAdapter:62` sets
   `sendSurvey.visibility = GONE` in `init` and nothing ever sets it visible,
   so `createBulkSurveySubmissions` is dead in the shipping app. The port's
   Send *is* reachable, which is why the key it writes has to be right even
   though nothing upstream exercises it.

The port also does not have Kotlin's `getSurveyFormState` gap (Finding 9: an
exact `parentId IN (…)` on bare ids that a compound key defeats) — the port has
no such reader; `_teamSubmissionSurveyIds` matches on the `parent` document's
`_id`, which is the bare source id under either key.

## The question the brief asked me to decide

> whether the repair sweep should be extended to rescue clones already written
> without a `courseId`, or whether the next sync rewrites them.

**Neither. The next sync deletes them, and no extension is worth writing until
that is fixed.** Decided from the code and probed:

* `SurveyDao.deleteNotIn` spares only rows with a non-null `stepId`. A clone has
  none (deliberately, above), its id is locally minted, and **the port has no
  adopted-survey uploader**, so it is never in the walk's keep set. Probe:
  `deleteNotIn(['survey-1'])` after an adopt → `DELETED=1`, clone `null`. The
  clone and its questions are destroyed on the next surveys sync.
* A clone already in the field with `courseId = null` cannot be backfilled by
  `adoptSurvey` — the `adoptedTeamSurvey` guard returns early — and the adopt
  button is hidden for an already-adopted source, so a backfill branch there
  would be dead code.
* Its member sheets are keyed bare, which is *internally consistent* with the
  bare-keyed reads on that device: no double sheet, no unsatisfiable gate. The
  only divergence is against a Kotlin handset in the same deployment.
* Reaching such a clone from the sweep would mean joining clone → source →
  `courseId`, a new DAO query in Lane B's file, to repair rows whose survey is
  about to be deleted anyway.

So: no repair extension. The two live rescue paths already cover everything
reachable — `createBulkSurveySubmissions` repairs the very survey it keys
(so a leader re-sending heals their team's stale sheets), and
`repairCourseSurveyParentIds` now reaches clones because they carry a
`courseId`. Both are pinned.

One honest caveat, from the audit: **a clone's `courseId` does not survive a
sync round trip in Kotlin either.** `StepExam.serializeExam` emits no `courseId`
and no `stepId`, and the `exams` walk that pulls the document back cannot set
them (`insertCourseStepsExams` reads neither from the JSON, and its one
production call site passes `("", "", doc, "")`). So the join is a
locally-authored, pre-sync fact in both trees. That is exactly the window Send
runs in — adopt, then send — which is why the fix matters, and it is also why
nobody should expect the column to be there after a sync.

## Files

* `lib/repository/surveys_repository.dart` — `adoptSurvey`: `courseId` copy,
  explicit `rev: null`, `userName`, the `parent` document's `courseId`, one
  hoisted question read.
* `lib/repository/submissions_repository.dart` — `hasUnfinishedSurveys` skips
  clones; the doc bullet that called the omission load-bearing is replaced.
  Nothing near `queuePending` touched (Lane A).
* `lib/ui/teams/team_surveys_screen.dart` — passes `userName` from the session
  user it already watches.
* `test/repository/adopted_team_survey_course_key_test.dart` — new, 9 tests.
* `test/repository/mandatory_survey_round_trip_test.dart` — the Phase 125
  pinned test now pins the corrected behaviour.

No Drift `schemaVersion` bump: both columns already exist.

## Reported, not fixed

### An adopted team survey clone is deleted on the next sync, and never uploaded

**The largest thing this phase found, and it is Phase 116's class: ported,
tested, green, and destroyed.** Kotlin uploads the clone —
`ExamDao.getPendingAdoptedSurveys()` (`sourceSurveyId IS NOT NULL AND
_rev IS NULL`) feeds `UploadConfigs.kt:186-195`, endpoint `exams` — so it
becomes a real document, gains a `_rev`, and is in every later walk's keep set.
Kotlin also has **no** delete-except-ids on that table at all.

The port has neither half. There is no adopted-survey uploader anywhere in
`lib/`, and `SurveyDao.deleteNotIn` prunes every row without a `stepId` that
the server did not name. Probed above: the clone and its question rows are gone
after one surveys sync, so a team's adopted survey is a local artefact with a
lifetime of minutes, its answer sheets orphaned.

The fix is three pieces in three lanes' files, which is why it is reported:

1. `SurveyDao` gains a pending-adopted query and `deleteNotIn` spares
   `sourceSurveyId IS NOT NULL AND rev IS NULL` — `app_database.dart`, Lane B.
2. An `AdoptedSurveysUploader` on the outbox, POSTing
   `SubmissionsRepository.surveyParentDocument` to `exams` and recording
   id/rev — the outbox files are Lane A's.
3. `adoptSurvey` enqueues it. That half is mine and I have deliberately not
   written it alone: an enqueue with no drainer and no prune exemption changes
   nothing.

Whoever takes it should treat it as the round's highest-value item, ahead of
this phase's own subject: a wrong `parentId` mis-files answers, a deleted
survey loses them.

### `releaseStepJoinsForCourse` would strip a clone that ever carries a `stepId`

The reason `stepId` is withheld. If a later round decides the clone should
carry it — to match Kotlin's step button, say — then
`SurveyDao.releaseStepJoinsForCourse` must first spare
`sourceSurveyId IS NOT NULL`, or the next courses sync nulls the clone's
`courseId` with it and this phase silently reverts. `app_database.dart`,
Lane B's file this round.

### Adopt is still gated on team leadership; Kotlin gates on guest

Unchanged from Phase 132's report, and still in one of my files.
`team_surveys_screen.dart:32` — `canAdopt = membership?.isLeader == true`, where
Kotlin has no role gate at all: `SurveysAdapter:83-90` routes any tap to
`onAdoptSurvey`, and the only visibility rule is
`if (userId?.startsWith("guest") == true) startSurvey.visibility = View.GONE`.
The audit confirms the adopt path is reachable for any member through the team
SURVEY tab (`TeamPagerAdapter:89-94`, `SurveyFragment:152-155`). Left alone for
the reason Phase 132 gave: it is an **authorization** change, the current gate
is pinned by an existing test, and it wants its own diff. The fix is
`canAdopt = userId?.startsWith('guest') != true`, plus the same predicate
hiding the start affordance on `surveys_screen.dart` (not in this lane's set).

### A team with no name adopts differently in the two trees

`adoptSurvey:81-83` wraps the whole clone block in
`if (!teamName.isNullOrEmpty())`, so Kotlin with an unresolved team name writes
the adoption marker and **no clone** — and, per the audit, then leaves the
source adoptable so every further tap inserts another marker. The port always
creates the clone, falling back to the source's name. The port's behaviour is
the more useful one and porting the guard would make Adopt silently do nothing,
so this is recorded rather than changed.

### `hasUnfinishedSurveys` and the challenge dialog's alias

Unchanged, but now with a citation worth keeping: Kotlin's `hasPendingSurvey`
(`:381-389`) differs from `hasUnfinishedSurveys` only in passing
`survey.courseId` where the other passes `courseId`, and the DAO pinned
`courseId = :courseId`, so the two are the same function under two names. The
port's alias is correct.
