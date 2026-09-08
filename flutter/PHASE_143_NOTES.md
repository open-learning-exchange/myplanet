# Phase 143 — the schema bump destroyed an unpublished survey clone, and `submissions` outlived it

Lane B of a four-lane round. The brief was Phase 138's own *Reported, not fixed*
entry, written by the lane that created the expectation and deliberately did not
close it. It was precise and it was right about the defect; it was **incomplete
about the fix**, in a way that would have deferred the data loss rather than
prevented it. That is the round's lesson and it is in *Job 2* below.

## Method

1. Read the migration path and both tables by hand, plus drift's own
   `Migrator.createAll`/`createIndex` in the pub cache and the generated
   `Index` statements — not the notes' description of them.
2. A `parity-auditor` pass at `effort: max` on the **ground truth**, before any
   Dart, asking six numbered questions. It confirmed the schema history,
   confirmed the index-ordering hazard empirically, **found a data-loss defect
   in the plan itself**, corrected my intended answer to the v47 question, and
   named two stale claims elsewhere in the tree.
3. Implement, each defect demonstrated failing first.
4. **Fifteen mutations**, each applied alone to green code and reverted.
   Fourteen redded immediately; one survived and is written up below, because a
   surviving mutation is the only kind worth reporting.
5. A second `parity-auditor` pass at `effort: max` aimed at the finished,
   already-green code.

## Job 1 — both survey tables join `localAuthorityTables`

`surveys` and `survey_questions` were not preserved while `submissions`,
`submission_answers` and `submission_questions` all are. So a schema bump on a
handset that had adopted a team survey but not yet published it dropped the
clone and its questions and **kept** the members' answer sheets — the same
orphaning Phase 138 removed from the sync path, reachable by an upgrade.

Demonstrated failing first, in
`test/data/local/survey_clone_survives_schema_bump_test.dart`, which drives the
production path rather than fabricating one: the source survey comes out of
`SurveyMapper.fromCourseDoc` reading a course document shaped like the server's,
the clone is minted by `SurveysRepository.adoptSurvey`, and the answer sheet by
`createBulkSurveySubmissions`.

```
adoptSurvey -> clone + question rows + a member's sheet
onUpgrade(46 -> 47)
  clone row      null          <- gone
  question rows  []            <- gone
  answer sheet   still there   <- the asymmetry
```

By the project's operative test — ***can a sync restore this?***, not *is it
local?* — the tables qualify: a **published** clone comes back on the next
`exams` walk, an **unpublished** one exists nowhere else. That is the `feedback`
argument verbatim. The mechanism is the `teams` precedent: preserve the whole
table and let the next walk's `deleteNotIn` evict the stale cache half, which it
already does for the question rows **in the same transaction** — so the pair
moves together and no question row is orphaned. Preserving only one of the two
*would* have orphaned; they had to land together.

### This is not a parity fix, and the notes should stop implying it is

Worth stating plainly, because the framing was wrong in my own head until the
ground-truth pass. Kotlin's `RoomModule` builds with
`fallbackToDestructiveMigration(true)` (`RoomModule.kt:57-61`) and there are no
`Migration` objects anywhere in the tree; `StepExam`, `ExamQuestion`,
`Submission` and `Answer` are all in the same `@Database`
(`AppDatabase.kt:124-127`). **Kotlin drops all four together and therefore
cannot reach this orphaning at all** — the sheets die with the survey. The
asymmetry this phase removes is one the *port* created for itself when it
preserved `submissions`. The deviation is licensed (`localAuthorityTables`
exists for exactly this) and it is the better behaviour, but it is a deviation,
not a gap.

## Job 2 — the `needsSync` default would have deferred the loss, not prevented it

**The defect the ground-truth pass found in the plan, and the most valuable
thing here.**

`Surveys.needsSync` arrived at v47, so a pre-v47 install has no such column and
preservation means `createAll` will not add it — hence a hand-written
`_addColumnIfMissing`. The column default is `false`, which is right for every
row the server sent. But for a clone adopted on a v46 build it is the worst of
both states:

* invisible to `SurveyDao.pendingAdoptedSurveys()` (`needsSync = true`), so it
  never publishes; **and**
* outside `SurveyDao.deleteNotIn`'s spare clause
  (`stepId IS NULL AND needsSync = false`), so the very next surveys walk
  deletes it and its questions and orphans the sheets.

Preserving the table would have moved the loss from the upgrade to the first
sync after it. **A fix that relocates a data loss reads exactly like a fix that
removes one**, and the only thing that separated them was asking what the row's
state is *after* the migration rather than whether it survived it.

So the flag is backfilled — and the port can do here what Phase 138 said it
could not, because **the port's clone id is deterministic where Kotlin's is
random**: `adoptSurvey` mints `'${surveyId}_$teamId'`
(`surveys_repository.dart:105`; its one production caller,
`team_surveys_screen.dart:121`, passes no `createId`) while
`SurveysRepositoryImpl.kt:85` uses `UUID.randomUUID()`. That id equality is a
*positive identification* of a row this device authored, which is precisely
what Phase 138 lacked when it rejected `_rev IS NULL`.

```sql
UPDATE surveys SET needs_sync = 1
WHERE source_survey_id IS NOT NULL
  AND team_id IS NOT NULL
  AND _rev IS NULL
  AND step_id IS NULL
  AND id = source_survey_id || '_' || team_id
```

**One row satisfies every conjunct without being local work**, found by the
implementation audit and accepted rather than predicated away. A clone another
handset published, attached to a course step on Planet, has its authoritative
`_rev` clobbered to NULL by the courses walk — `SurveyMapper._build` assigns
`rev` unconditionally and a course sub-object carries none, which is Phase
138's unfixed two-writer conflict — and once the course stops naming it,
`releaseStepJoinsForCourse` nulls `stepId` as well. There is no local
discriminator left, because `rev` is the only column that records "the server
has this" and it is exactly the column those two writers disagree about;
`courseId IS NULL` would miss a genuine clone of a course-attached survey, since
`adoptSurvey` copies `courseId`. The cost is bounded and self-clearing: the POST
takes a 409 and Phase 138's `_adoptExistingDocument` records the winning rev and
clears the flag. One POST and one GET, once, after which the row rejoins the
prune — **pinned as a pair**, not asserted, because each half passing alone is
the shape this project keeps getting wrong.

Two conjuncts are deliberately redundant (`x || NULL` is NULL, so the id
equality already fails without a `source_survey_id` or a `team_id`) and are kept
to say plainly that the repair is about a team adoption and nothing else — the
v45 health repair sets that precedent for a redundant conjunct kept for clarity.

## Job 3 — the index-ordering hazard, which is why the steps run before `createAll`

Not in the brief, and it would have shipped a **launch-loop crash** on any
device below v35.

`onUpgrade` drops the caches, then `DROP INDEX IF EXISTS` for every index, then
`m.createAll()`, then the hand-written column steps. `createAll` emits
`CREATE TABLE IF NOT EXISTS` — which no-ops on a preserved table — but a
**bare** `CREATE INDEX`, taken verbatim from the generated `Index`
(`app_database.g.dart:30793`:
`'CREATE INDEX surveys_course_id ON surveys (course_id)'`). Every index was just
dropped, so every one is recreated. `surveys_course_id` names `course_id`, which
`surveys` only gained at v35. On an older device the preserved table has no such
column, the `CREATE INDEX` raises `no such column: course_id`, and it throws out
of `onUpgrade` — which propagates from `beforeOpen`, so the database never opens
and the failure repeats on every launch.

The fix is ordering, not the statement: both survey blocks run **before**
`createAll`. That in turn needs two guards, because a pre-`createAll` step
cannot assume its table exists (an upgrade from a version predating the table):
`_addColumnIfMissing` now returns early on an empty `PRAGMA table_info`, and a
new `_tableExists` wraps the block containing the raw `UPDATE`, which has no
such tolerance of its own.

**No other preserved table is exposed to this today**, and I checked rather than
assumed: of the nine hand-written columns (`chat_history.is_uploaded`,
`users.is_updated`/`age`/`birth_place`, `teams.image_name`,
`team_tasks.is_notified`/`sync`/`link`, `news.reactions`), none appears in a
`@TableIndex`. `feedback_is_uploaded` is the near miss — an indexed column on a
preserved table — but `feedback.is_uploaded` has never needed a step. The rule
is now written at the reconciliation block: **check for an index before adding a
step after `createAll`.**

## Job 4 — a test that fails when the next column is forgotten

The stated cost of preservation is that `createAll` does not ALTER a preserved
table, and *forgetting a step does not fail loudly* — it leaves the column
absent on every existing install and breaks every query naming it. That cost is
what kept Phase 138 from doing this, so it had to be paid down rather than
inherited.

`migration_test.dart` now carries **frozen** `CREATE TABLE` literals for
`surveys` (pre-v35 and pre-v47) and `survey_questions`, installed over the
freshly-created tables the way an on-device upgrade would find them, and asserts
after the upgrade that `PRAGMA table_info` contains every column drift declares.
The literals are frozen **on purpose and the comment says so**: a future column
is absent from the literal and absent from the migration, therefore absent from
the PRAGMA, therefore red. Keeping the literal in step with the table would turn
the guard into coverage that cannot fail.

**Verified by mutation, not by argument** — a `phantomMutation` column added to
`SurveyQuestions` (the table with *no* steps at all), codegen re-run, suite red;
column removed, codegen re-run, suite green.

The literals also carry the drift guard the `team_tasks` precedent has and my
first cut lacked: `frozenSurveyColumns` and `migratedSurveyColumns` are checked
for **exact equality** against the live table before the literal is installed,
so a *renamed or removed* column fails at the fixture. The `containsAll`
assertions catch an addition; only this catches a rename.

## Job 5 — the three DAO queries other lanes reported as blocked on this file

Added, with their call-site swaps reported rather than half-done.

* **`SubmissionDao.countCompletedByUserAndExamId`** (`SubmissionDao.kt:24`).
  Three things Phase 139's item spelled out and I took as written: **no `type`
  predicate** (Kotlin's count has none, so adopting it closes a divergence
  rather than preserving one), **the `LIKE` pattern is not escaped** (Kotlin
  interpolates the raw exam id; escaping would make the port *stricter*), and a
  **NULL status must not count** — `NOT (status = 'pending')` is NULL for a NULL
  status and `WHERE NULL` excludes the row, which is the same three-valued
  outcome `status != 'pending'` gives and the opposite of what a Dart
  `status != 'pending'` filter does.
* **`NewsDao.getByNewsId`** (`NewsDao.kt:74`) — case-**sensitive**, because that
  query carries no `COLLATE NOCASE` where its neighbours do.
* **`NewsDao.getPlanetMessages`** (`NewsDao.kt:57-58`) — both comparisons folded
  with `lower()`, the idiom the rest of the DAO already uses for
  `COLLATE NOCASE`.

All three have no callers yet, so `test/data/local/dao_query_semantics_test.dart`
pins the SQL semantics their doc-comments claim — and pins two of them against
the **raw Kotlin statement** run on the same rows, rather than against my
reading of it. The three-valued cases are why: a NULL status, a null `userId`,
a NULL `docType`. Also pinned: `getByNewsId`'s case-sensitivity, and that an
exam id containing a quote cannot inject (drift binds the `LIKE` pattern as a
parameter, so the deliberate non-escaping is a *matching* looseness and not an
injection one). Four mutations, four reds.

## The v47 question, answered: that window is closed

The brief asked for a stated decision on handsets already upgraded to v47.
**There is no honest repair; the window is closed.** Four reasons, in order of
how decisive they are, and the third is the one that changed my answer.

1. **The population is empty.** `app/` is the shipping app and the Flutter port
   has not shipped. v47 landed last round on the migration branch, so no handset
   has ever run that upgrade. Everything below is why the answer would still be
   no if it had.
2. **The rows are gone.** `DROP TABLE`, not a soft delete. Nothing in the
   database holds the clone.
3. **Re-adopting through the UI is blocked, and the port blocks it itself.** My
   intended answer was "the recovery path is re-adoption, and it works because
   the clone id is deterministic" — the sheets would re-attach, since
   `parentId` is `'<cloneId>@<courseId>'` and a fresh adopt mints the same
   `cloneId`. **False.** `SurveysRepository.adoptableTeamSurveys`
   (`surveys_repository.dart:63-76`) excludes every `_parentSurveyId` among the
   team's submissions, and the adoption **marker** submission is one of them.
   That faithfully ports `getAdoptableTeamSurveys` (`:256-266`) — but in Kotlin
   it never bites, because a Room bump drops the submissions too and the source
   becomes adoptable again. **The port's preservation of `submissions` is what
   closes its own repair path.** Found by the ground-truth audit, not by me.
4. **Reconstruction would trade a closed loss for an open leak.** The only
   surviving trace is a submission's `parent` blob, and it is thin: the
   bulk-sent sheets `getOrCreateSurveySubmission` writes carry no `parent` and
   no question rows at all, and `createSurveyDraft` stores question **labels
   only**. Publishing a survey rebuilt from that would POST a document the
   device did not author to `exams` under the user's credentials — the exact
   leak `Surveys.needsSync` was introduced to close.

Doing nothing costs nothing further: the sheets survive, and an already-answered
sheet stays reviewable from `submission_questions` where it has rows.

## `schemaVersion` stays at 47 — the allocated 48 is unspent

Decided from the migration code, as the brief asked. Changing
`localAuthorityTables` membership alters no DDL, and `onUpgrade` runs only when
the version increases — so a bump would drop the caches for no reason and
discard unsynced writes in every non-preserved table. The preservation takes
effect at the *next* bump, whoever spends it. **48 is free.**

## The mutation that survived

Fourteen of fifteen redded on the first try. The other one: removing `AND step_id IS NULL`
from the backfill broke nothing, because every step-joined row in my fixture
also lacked a `source_survey_id`, so the id-shape clause already excluded it.

Rather than delete the conjunct as dead, I looked for the row that makes it
load-bearing, and it is producible: **a clone that published and was then
attached to a course step on Planet.** The courses walk writes it back with the
clone's own id and `sourceSurveyId`, with a `stepId`, and with `rev` NULL — a
course document's embedded survey is a sub-object and carries no `_rev` of its
own (the ownership conflict Phase 138 recorded at `SurveyMapper._build`). So it
satisfies the id shape *and* the rev clause, and `step_id IS NULL` is the only
thing stopping the backfill re-publishing a document the server already has.
That row is now in the fixture, and the mutation reds.

**A surviving mutation is a question about the predicate, not just about the
test.** The answer here was a fixture row; it could as easily have been a
conjunct to delete.

## Reported, not fixed

1. **`lib/repository/submissions_repository.dart:2140-2143` is now false.** Its
   `AnswerShape.forQuestion` doc-comment says closing the `hasOtherOption` gap
   needs a column on `survey_questions`, "That table is not in
   `localAuthorityTables`, so `createAll` rebuilds it and the column costs only
   a schema bump, no hand-written step." After this phase it costs a bump **and**
   a hand-written `_addColumnIfMissing` step. Outside this lane's file set, so
   recorded rather than edited; the equivalent claim in `PHASE_106_NOTES.md:250`
   *is* corrected in place, because nobody else touches historical notes. The
   replacement sentence, for whoever applies it: *"That table is in
   `localAuthorityTables` since Phase 143, so `createAll` will not alter it: the
   column costs a schema bump **and** a hand-written `_addColumnIfMissing` step,
   and `migration_test.dart`'s frozen-DDL guard fails until it has one."*
   `PHASE_123_NOTES.md:108-110` records the same three columns as still owed, so
   this is a live target, not a hypothetical one.
2. **`_isStepCompleted` should call the new DAO method.** The query is in
   `SubmissionDao` now; its caller is in
   `flutter/lib/ui/courses/take_course_screen.dart`, **Lane D's file**. The swap
   replaces a read-all-then-filter-in-Dart with
   `countCompletedByUserAndExamId(userId, examId) > 0`, and it **changes
   behaviour in the port's favour**: the Dart filter has a `type` predicate that
   Kotlin's count does not, so the swap closes a divergence. Adding the DAO
   method without the swap leaves it with zero callers — deliberate, since half
   a pair across two lanes is worse than a clean handover.
3. **`isAlreadyShared` and `planetNewsMessages` should call the new NewsDao
   queries.** Same shape: the queries are in, the callers are in
   `flutter/lib/repository/voices_repository.dart`, **Lane C's file**. Both
   currently walk `getAll()` and filter in Dart. Same result set, more rows
   read; not a correctness defect.
4. **A stale step-joined survey row is now unbounded.** New accretion this phase
   creates, and it is recorded at the preserved-set comment too.
   `SurveyDao.deleteNotIn` only ever considers rows with `stepId IS NULL`, and a
   step join is released only by `SurveyDao.releaseStepJoinsForCourse`, which
   `CoursesRepository` calls for the course documents **on the current page**
   (`courses_repository.dart:277-289`). A course deleted server-side is on no
   page, so nothing nulls its surveys' `stepId` and the prune can never reach
   them — the bump used to sweep them and no longer does. Concrete input: delete
   a course carrying a step survey on Planet; the courses walk's `deleteNotIn`
   removes the course row and the `surveys`/`survey_questions` rows persist
   across every future sync and every future bump. Not a correctness defect (the
   rows are unreachable from the UI once the step tile is gone) but it is
   unbounded, and it is the argument for giving `releaseStepJoinsForCourse` a
   whole-table sweep rather than a per-page one.
5. **Two self-healing paths lost their second line of defence.**
   `SurveysRepository.sync` runs `deleteNotIn` only
   `if (total == 0 || complete)` (`surveys_repository.dart:402-405`), so on a
   handset whose surveys walk never completes, stale rows now persist
   indefinitely where the bump used to clear them. And `tables.dart:587-593`
   relies on "the next surveys sync rewrites them" for rows an older build wrote
   through the pre-Phase-104 `choices` converter — still true, but the bump was
   previously a second guarantee and is not any more.
6. **`surveys.rev` still has two writers that disagree about who owns it**,
   unchanged from Phase 138's report. `SurveyMapper._build` writes
   `rev: Value(...)` unconditionally, so a course document's embedded copy
   clobbers the `exams` walk's authoritative `_rev` with NULL, where
   `ExamMapper.fromDoc` uses `_presentOrAbsent` for exactly this hazard.
   `survey_mapper.dart` is outside this lane's set. **It is now load-bearing
   where it was not**: it is the sole reason the v47 backfill has a reachable
   misfire at all (see *Job 2*), and fixing it — `_presentOrAbsent` for `rev`,
   as `ExamMapper.fromDoc` already does — would close that misfire as a side
   effect rather than needing a predicate change here. That makes it the
   highest-value item on this list.
7. **The `submissions` sweep's `parentId` shape is worth one more look.** Not
   touched, and not obviously wrong; noted because Phase 125 found the compound
   `'$surveyId@$courseId'` key disagreement the expensive way and this phase's
   fixtures now depend on that shape in two files.
