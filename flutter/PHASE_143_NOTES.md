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
4. **Nineteen mutations**, each applied alone to green code and reverted.
   Seventeen redded immediately; **two survived**, and both are written up
   below, because a surviving mutation is the only kind worth reporting.
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

### The first cut of that predicate was a data leak, and I argued my way past it once

**The most important thing in this phase, and it took two audit passes plus a
retraction to land.**

The implementation audit found that the id shape is *not* authorship. A team's
clone that is shared **publicly** is served back under that very id by
`/api/public/surveys/<teamId>/<surveyId>` — the deep link's `surveyId` *is* the
clone — and `saveSurveyFromPublicApi` stores it with `sourceSurveyId`, `teamId`,
no `stepId`, and a `rev` that is NULL whenever the remote Planet omits `_rev`
(`surveyParentDocument` emits `_rev` only for a non-null rev, and `Surveys.rev`'s
own doc-comment states every public-API survey has none). All five conjuncts.

**And the harm was not the bounded kind.** The document lives on the *link's
origin* planet while `AdoptedSurveysUploader.endpointFor` POSTs to the
*configured* server, which has no document under that id — so the POST
**succeeds**, publishing a survey this device never authored into the user's own
`exams` database, under their credentials, stamped with this handset's origin
fields. Verbatim the leak `Surveys.needsSync` was introduced to close, reopened
for one upgrade by the migration that installs the column.

I had already met a *different* row with the same shape — a published clone
Planet attached to a course step, whose `rev` the courses walk clobbers — and
**written it up as an accepted misfire**, reasoning that the 409 arm makes it
self-clearing. That reasoning was sound for that row and wrong as a policy, and
the public-API row is the one it does not cover. The retraction is the lesson:
***"this misfire is harmless" is a claim about one producer, and there is always
another producer.*** The right question was never "how bad is this misfire" but
"what evidence do I actually have that this row is mine".

There is such evidence, and it is local, single-writer and already preserved:
`adoptSurvey` writes an **adoption marker** submission beside every clone
(`createSurveyAdoptionSubmission` — `parentId` is the *source* id, `teamId` the
team). So the predicate gains an `EXISTS` conjunct, which is what the v45 health
repair's own decisive conjunct is too:

```sql
  AND EXISTS (SELECT 1 FROM submissions AS marker
              WHERE marker.parent_id = surveys.source_survey_id
                AND marker.team_id   = surveys.team_id)
```

**`rev` could never have carried this weight**, and that is the general point:
it is the one column two sync walks disagree about the ownership of (Phase 138's
unfixed `surveys.rev` item), so a predicate leaning on it inherits that
ambiguity. The marker has exactly one writer.

The conjunct closes **both** routes, which is how you can tell it is the right
shape rather than a patch: where the marker is present on the course-step row,
both leaders genuinely adopted, the deterministic ids converge *by design*, and
the 409 arm reconciles them — so the mitigation I over-generalised turns out to
be exactly right for the case it actually covers. And it fails safe in its one
wrong direction: `adoptSurvey` writes the clone before returning early on an
empty `userId`, so such a clone has no marker and is not flagged, leaving it
unpublished — the state it was already in — rather than publishing something
foreign.

**Both halves of the marker match are load-bearing, and one was pinned by
nothing** until a mutation said so. Dropping `marker.team_id = surveys.team_id`
broke no test, because no fixture had the ordinary case: two teams adopt the same
shared survey, and one of them shares its clone publicly. A marker for that
source exists, so a `parentId`-only match flags the *other team's* document. Now
pinned.

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
so a new column fails at the *fixture* rather than at the outcome — the earliest
and clearest place to be told a step is owed.

**And the column *names* were not enough**, which the implementation audit
caught with a case I had not thought of: a column whose **type** changes keeps
its name, no `_addColumnIfMissing` step can ALTER a type, and every assertion in
the file stays green while an upgraded device carries `TEXT` and a fresh one
`INTEGER`. So the fixture now compares the **DDL text** SQLite actually holds
against the frozen literal — which is what those literals claim to be, and the
claim was previously unchecked. Verified by changing `passingPercentage` from
`text()` to `integer()`, re-running codegen, and watching it red with
*"the frozen surveys DDL no longer matches what drift creates"*.

My justification comment for the exact-equality check was also simply wrong, in
both halves, and is corrected in place: a *rename* does **not** slip past
`containsAll` (the new name is absent after the upgrade, so it reds anyway), and
a *removal* does slip past but is harmless, because drift maps columns by name
and an extra column on an upgraded table is ignored. The check's real value is
the early, specific failure — not catching a case the outcome assertions miss.

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

## The two mutations that survived

Seventeen of nineteen redded on the first try. Both survivors were the same
lesson from opposite ends — a conjunct nothing pinned — and neither was dead
code once I looked for the row that needed it.

**First:** removing `AND step_id IS NULL`
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

**Second**, and after the marker conjunct landed: dropping
`marker.team_id = surveys.team_id` from it broke nothing. The fixtures had no
case where a marker exists for the source survey but for a *different* team —
which is the ordinary situation, not a corner: two teams adopt the same shared
survey, and one shares its clone publicly. A `parentId`-only match then flags
the other team's document, which is the leak again by a shorter route. Now
pinned, through the production `saveSurveyFromPublicApi`.

**A surviving mutation is a question about the predicate, not just about the
test.** Both times the answer was a fixture row rather than a conjunct to
delete — and both times the missing row was one a *producer-driven* fixture
would have had. The first cut's negative set was six hand-written
`INSERT INTO surveys` statements described in the comment as "shapes taken from
their producers, not invented"; they were reasoned from each producer's first
write and stopped there. That is this project's own "a fixture that fabricates a
join is not evidence" rule, applied to the negative set, and it is the reason
the public-API leak survived my first implementation *and* my first mutation
round.

## Reported, not fixed

0. **`docs/kotlin-to-flutter-migration.md` and `CLAUDE.md` both list four
   preserved tables against the real 27.** Pre-existing staleness rather than
   something this phase broke, but the migration doc's *preserved-table test*
   section is the one a lane is told to read before touching this set, and it
   names `outbox`, `my_personal`, `removed_log` and `my_life` only. Lane A owns
   `docs/kotlin-to-flutter-migration.md` this round and `CLAUDE.md` is nobody's,
   so both are reported. One line each would do it.
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
   across every future sync and every future bump.
   **My first write-up called these rows "unreachable from the UI once the step
   tile is gone", and the implementation audit showed that is false.**
   `surveysProvider` filters `watchAll()` through
   `SurveysRepository.individualSurveys()`, whose predicate is
   `!row.teamShareAllowed && (row.teamId ?? '').isEmpty` — **no `stepId` or
   `courseId` test at all** — so an orphaned step survey with no team is listed
   at `/life/surveys` and is tappable, forever. The *visibility* matches Kotlin
   (`ExamDao.getByType("surveys")` has no filter either), but Kotlin's Room bump
   drops the table and self-cleans where the port's no longer does. So this is
   a user-visible accretion, not a hidden one, and it is the argument for giving
   `releaseStepJoinsForCourse` a whole-table sweep rather than a per-page one.
   Corrected here because the sentence I got wrong is the one that decided this
   was not worth fixing.
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
7. **`survey_clone_survives_schema_bump_test.dart`'s `from: 47` default means
   its three original tests exercise no migration step.** `to` is
   `schemaVersion`, which is 47, so no `from <` branch fires: those three cover
   the `localAuthorityTables` membership change, the index drop-and-rebuild and
   `createAll` not clobbering a preserved table — and would stay green if every
   step this phase adds were deleted. That is deliberate (they are the
   *preservation* tests, and the newer tests in the same file use `from: 46`),
   but `migration_test.dart`'s frozen fixtures are the only thing that will
   catch a forgotten step on the **next** bump. Worth knowing before trusting
   the file's name.
8. **`getPlanetMessages` folds the argument with Dart's Unicode-aware
   `toLowerCase()` and the column with SQLite's ASCII-only `LOWER()`.** So a
   planet code containing a non-ASCII letter matches in Kotlin
   (`COLLATE NOCASE` on both sides) and not in the port. The idiom is
   pre-existing throughout `NewsDao` (three other queries do the same), so this
   is inherited rather than introduced, and unreachable while planet codes are
   ASCII. Fixing it properly means `COLLATE NOCASE` in raw SQL rather than
   `lower()`.
9. **`NewsDao.getPlanetMessages` takes a non-nullable `String` with no
   empty-string guard**, matching Kotlin, where
   `VoicesRepository.planetNewsMessages(String? planetCode)` returns `const []`
   for null-or-empty. That guard is the port's own. Whoever does the call-site
   swap must keep it — a naive `getPlanetMessages(planetCode!)` turns a null
   into a crash.
10. **The `submissions` sweep's `parentId` shape is worth one more look.** Not
   touched, and not obviously wrong; noted because Phase 125 found the compound
   `'$surveyId@$courseId'` key disagreement the expensive way and this phase's
   fixtures now depend on that shape in two files.
