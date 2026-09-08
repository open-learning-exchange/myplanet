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

`SurveysRepositoryImpl.kt:163-191` makes **nineteen** assignments — every one
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
(`stepSurveysProvider` → `getByStepId`) — and the second audit corrected my
reason. The survey tile renders **no** count (`take_course_screen.dart:468-474`
says so itself; only the *test* tile counts). The real hazard is worse: the
button opens `surveys.first.id` (`:499`), so a copied `stepId` would point a
learner's Take Survey button at whichever team's private clone sorted first.
Kotlin's `getByStepIdAndType(stepId, "surveys")`
(`CoursesRepositoryImpl.kt:530`) does exactly that.

## The reader had to change, and the brief's premise is dead code

With `courseId` on the clone, `SurveyDao.getByCourseId` returns clones, and
`hasUnfinishedSurveys` iterates exactly that query. Left alone it would demand
that **every learner on the course complete every team's private copy** — a
learner outside the adopting team has no sheet for it and never can have one.
That is Phase 125's bug re-entered from the writer's side, and the guard
`'the gate does not demand a team's private copy'` fails on the unfiltered
version (mutation-tested: `Expected: false / Actual: <true>`).

So `hasUnfinishedSurveys` now skips a clone — **on both halves of Kotlin's own
predicate**, `sourceSurveyId IS NOT NULL AND _rev IS NULL` (`ExamDao.kt:21`).
The first cut had only the first half and that was a real regression; see
*What the second audit found in my own green code*.

**Skipping is not a divergence from a live Kotlin behaviour.** Kotlin's gate
cannot see a clone under any document shape:

* `getSurveysByCourseId` (`:367-369`) filters
  `getByCourseIdAndType(courseId, "survey")` — **singular** — while every
  survey in the app, clone included (`type = exam.type`), carries `"surveys"`:
  the take-survey button (`CoursesRepositoryImpl.kt:530`), all three survey
  lists, `ExamDao`'s defaults. So for a normally shaped document the gate
  matches nothing at all and `hasUnfinishedSurveys` is constant `false`.
* The singular value is written only by `CoursesRepositoryImpl.kt:746`'s
  `else examKey`. My first draft claimed that in *that* branch Kotlin's gate
  does match the clone; **it cannot**, and the second audit caught it. Every
  list `adoptSurvey` is reachable from is plural — `getAdoptableTeamSurveys`,
  `getTeamOwnedSurveys` and `getIndividualSurveys` are all `type = "surveys"`
  (`ExamDao.kt:29-31`) — so an adoption source is always `"surveys"` and so is
  its clone. The conclusion survives and is *stronger* than the argument I
  wrote for it, but the false step is the dangerous half: it is exactly the
  sentence a later round would cite to remove the skip.

`repairCourseSurveyParentIds` deliberately does **not** skip clones: a clone's
member sheets are exactly the rows a pre-Phase-136 build keyed bare, so the
sweep is how they heal. The two loops want opposite things from the same query,
and the doc comment now says so.

## Two more defects in the same function

* **The adoption marker's `parent` document disowned the course** — on the
  fallback path only, and my first write-up overstated it. Kotlin's
  `createParentJsonString(exam)` is handed the **source** exam (resolved at
  `:73`, ten lines before the clone's id exists) and writes
  `put("courseId", exam.courseId ?: "")`; the port hardcoded `''`. But nothing
  *published* that field: `serialize` prefers the live survey row
  (`_liveParentDocument`) and `surveyParentDocument` emits no `courseId`, as
  Kotlin's `StepExam.serializeExam` does not either. The stored blob is sent
  only when the source survey row has gone missing. So this is faithfulness on
  a rarely taken path, not a defect anyone observed — the commit message ranks
  it too highly.
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
* `test/repository/adopted_team_survey_course_key_test.dart` — new, 13 tests
  (the commit message for the first push says nine; two landed in a follow-up
  and two more with the second audit's fixes).
* `test/repository/mandatory_survey_round_trip_test.dart` — the Phase 125
  pinned test now pins the corrected behaviour.

No Drift `schemaVersion` bump: both columns already exist.

## What the second audit found in my own green code

Both passes were run at `effort: max`, ground truth before implementing and
implementation after it was green — and the second one earned its keep, as it
has in every phase that ran it.

### `sourceSurveyId != null` alone silently loosened the gate

**A regression this phase introduced, and the worst kind: it made a blocking
check stop blocking.** `sourceSurveyId` is *not* a local-authorship marker in
the port. The courses walk reads it straight off the server's embedded survey
(`survey_mapper.dart:163-167`), so a step survey that is itself an adopted copy
lands with `courseId`, `stepId` **and** `sourceSurveyId`. That row has a Take
Survey button and a working retake label — it can be satisfied — and my skip
ignored it. A learner on `MANDATORY_SURVEY_COURSE_ID` could tap Finish having
answered nothing and see no snackbar; the challenge dialog would report the
survey task done.

Kotlin's own test for a locally minted clone is the **conjunction**
`sourceSurveyId IS NOT NULL AND _rev IS NULL` (`ExamDao.kt:21`) — I cited that
line as authority and then ported half of it. The predicate is now the whole
thing, which this phase's own `rev: Value(null)` makes exact: a server row
always carries a rev, a clone never does. Pinned by
`'a server-authored step survey still gates, even if adopted'`, on a course
document carrying `_rev` and `sourceSurveyId`, which fails on the one-clause
version.

### The adoption guard had the null-vs-empty bug this phase celebrates catching

Kotlin's `findExistingAdoption` predicate is `it.status.orEmpty().isEmpty()`
(`:206`) — null **and** `''`. The port's was `row.status == ''`, which misses
null, and null is the *normal* state for a marker that has synced: `serialize`
sends `'status': ''` and `upsertDocuments` reads the empty string back as null
(`json_utils.dart:19-22`). The same shape `_repairSurveyParentId` needs
`coalesce(status, '')` for, three hundred lines away, with a doc comment
explaining why. Now `(row.status ?? '').isEmpty`.

Harmless today only because the port's marker id is deterministic, so the
rewrite lands on the same row rather than duplicating (Kotlin would insert a
second marker) — but it reset the status, bumped both timestamps and re-flagged
`isUpdated`, re-queueing the marker for upload on every stray adopt tap.

### Claims of mine it corrected

* the "other Kotlin branch" argument, above — a misread citation;
* the `parent.courseId` impact, above — not observable on the wire;
* the Take Survey *count* — the survey tile renders none; the real hazard is
  the button's target;
* seven citations off by one or two (`:180-181` → `:181-182` for the
  `stepId`/`courseId` pair, `createUserJsonString:139-141` → `:142-143`, the
  `doc` key span `:139-147` → `:142-148`, the take-survey button `:531` →
  `:530`), all now fixed;
* the test count, which was nine and is thirteen.

### And the framing, which is worth stating plainly

**With the clone skipped, no reader in `lib/` distinguishes the bare key from
the composite one for a clone.** The only two readers comparing a whole
`parentId` are `latestPendingByUserAndParent` (writer-side dedupe,
self-consistent under either key) and `countByUserParentAndType` (reached from
`hasSubmission`, which only ever sees step surveys, and from
`hasUnfinishedSurveys`, which now skips clones). Off-device, Planet has no
clone document to join against, because the port never uploads one.

So this is a **writer-faithfulness** change, not the live writer/reader
disagreement the commit message describes. It is still worth having — Send and
the answering path now derive the key from one place so they cannot drift
apart, a mixed Kotlin/port fleet agrees, and the transition is covered by the
repair — but the ranking in *Reported, not fixed* is the correct one: **the
uploader/prune gap is the item that loses data. This one does not.**

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

### `progress_repository.dart:92-95` now reads as a contradiction

Not touched — Lane B's file this round. Its comment justifies a `groupBy` with
"`adoptSurvey` copies `stepId` onto a new row, so a step can legitimately carry
more than one". That is a statement about *Kotlin*, and it was unambiguous
while the port copied neither column; now that `adoptSurvey` deliberately
withholds `stepId` it reads as a contradiction of
`surveys_repository.dart`'s block. The `groupBy` itself is correct and should
stay — Kotlin's clone really does share the step. It wants one clarifying
clause, from whoever owns that file.

### The repair's idempotence claim is device-local only

`repairCourseSurveyParentIds`' doc says "after the first pass no row matches the
bare id any more". True on the device: but `upsertDocuments` writes `parentId`
verbatim from the document and `_repairSurveyParentId` does not set
`isUpdated`, so a *server-authored* bare-id row is rewritten locally on every
Finish tap and re-reverted on the next pull. Pre-existing (Phase 125), and the
clone extension adds no new instance — a clone has no server rows, because the
port never uploads it. Recorded because this phase re-asserted the claim.

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
