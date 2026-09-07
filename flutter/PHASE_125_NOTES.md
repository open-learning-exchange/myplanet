# Phase 125 — the mandatory-survey key (`parentId`)

Lane A of a three-lane round. One defect, carried over from
`PHASE_123_NOTES.md` §"Found, not fixed" item 0, where Phase 123 called it
*"a live defect… the most severe thing in this file"* and left it for its own
phase because the fix is a design call rather than a one-liner.

**Every port writer stored a course-attached survey's submission `parentId` as
the bare survey id, and `hasUnfinishedSurveys` — the gate on finishing
`MANDATORY_SURVEY_COURSE_ID` — queried `"$surveyId@$courseId"`.** The count was
always 0, so a learner who *had* answered the attached survey was told they had
not, and the MyPlanet Onboarding course could not be completed however many
times they answered.

This is the port's recurring shape at its most expensive: **a writer and a
reader disagreeing about a key, where each half passes its own test and only
the pair is wrong** — Phase 74's reactions, Phase 100's photo id, Phase 116's
community feed identifier, Phase 120's `parent.questions`. The reader's own
test is the tell. It seeded its row through `upsertDocuments` with a
hand-written `'parentId': 'survey-a@course-1'`, a key the port's own writers
could not produce, and passed for the four phases the gate was broken. **A
fixture that fabricates the key is not evidence.**

---

## The key both sides now use

`"$surveyId@$courseId"` when the survey has a non-empty `courseId`, else the
bare survey id — `SubmissionsRepository.examParentId`, which the exam path has
used since Phase 110 and which every survey writer now shares.

That is Kotlin's key, and Kotlin is internally consistent about it:
`createExamSubmission` (`SubmissionsRepositoryImpl.kt:449-456`) builds it from
**the exam row's own `courseId`** — the request carries no course id, and
`startExamSession`'s `parentId` parameter is used only for the pre-existing-row
lookup (`:414`), never by the writer; Kotlin's own test pins it
(`SubmissionsRepositoryImplTest.kt:401-405`). `createBulkSurveySubmissions`
(`:201-207`) reaches the same string through `examDao.getById(examId)?.courseId`
and then reads with it too (`getPendingByUsersAndParent`, `:210`).
`hasSubmission` (`:174-190`) queries exactly it.

| writer | before | now |
|---|---|---|
| `createSurveyDraft:111` | `survey.id` | `examParentId(examId: survey.id, courseId: survey.courseId)` |
| `createBulkSurveySubmissions:522` | passed the bare `surveyId` through | resolves `surveyDao.getById(surveyId)?.courseId` **once, before the loop**, as Kotlin does |
| `getOrCreateSurveySubmission:539` | stored its `parentId` argument | unchanged — Kotlin's `getOrCreateSubmission` (`:624-642`, and dead in Kotlin: zero callers in `app/src`) also stores its argument verbatim and leaves the shape to the caller. Its one caller is the row above |
| `createSurveyAdoptionSubmission` | bare | **deliberately still bare** |

The adoption marker is the one row that must keep the bare id.
`createMappedSubmission` (`SurveysRepositoryImpl.kt:221`) writes
`parentId = examId` with no course suffix and `findExistingAdoption`
(`:205-207`) recognises it by comparing the whole column, so routing it through
`examParentId` would make every adoption invisible to its own lookup and
re-adopt on each load. It follows that a bare-id `type = 'survey'` row is not
always a stale answer sheet — which is what the repair's status clause is for,
and it is not theoretical: replaying its removal spuriously unblocks the course,
and the *first cut of that clause was itself a live defect*. See below.

`hasUnfinishedSurveys` now derives its key through `examParentId` too rather
than interpolating its own string, so the two cannot drift apart again.

## What happens to existing bare-id rows

**They are repaired in place, and they had to be: they do not heal on their
own.** I checked the sync path rather than assuming. The port uploads a
submission with whatever `parentId` the row carries (`serialize`:
`'parentId': row.parentId ?? ''`), and `upsertDocuments` writes `parentId`
straight back from the document (`:1312`), so a pull re-asserts the bare id
rather than correcting it. Kotlin confirms the shape from the other side: it
uploads `parentId` verbatim (`:775`, `:827`), reads it back verbatim (`:671`,
`:684`), has no Room migrations at all
(`RoomModule.kt:60` `fallbackToDestructiveMigration(true)`), and therefore
**normalises nothing** — a bare-id row round-trips as a bare-id row forever.

Left alone, a learner who had already answered the mandatory survey would be
told to answer it again: the entire bug, survived once more, plus a duplicate
answer sheet in the reporting the survey exists to produce.

So `repairCourseSurveyParentIds(courseId)` rewrites them — a lazy, idempotent
`UPDATE` returning how many rows it changed, called from the top of
`hasUnfinishedSurveys`.

**It needs no schema number, and a bump would be the wrong tool.** No DDL
changes — `parentId` has always been on `Submissions` — and `submissions` is in
`localAuthorityTables`, so an upgrade *preserves* exactly the rows that need
repairing and repairs none of them. `schemaVersion` stays 46 and no generated
output moved.

Two rows it must not touch, both `type = 'survey'` with a bare id: the adoption
marker, above; and a submission for a survey with no `courseId`, whose bare id
is already what `examParentId` produces. Both are pinned. It also skips
`exam`-typed rows, which matters because the two id spaces are not disjoint —
`_liveParentDocument`'s own comment says so — and an exam attempt's key belongs
to the exam path.

Calling a write from inside a boolean read is the one thing here I would rather
not have done. The alternatives were worse: a migration step needs a schema
number I do not have, and a repair hung off the submissions sync leaves an
offline learner — the normal case for this app — blocked until they next reach
a server. `hasUnfinishedSurveys` is the only read in either tree whose
predicate is the whole `parentId`, so it is the only read a stale row can
answer wrongly, and it runs at exactly the moment the answer matters: when the
learner taps Finish.

## The completed-survey-to-finished-course round trip

`test/repository/mandatory_survey_round_trip_test.dart` — 16 tests, named for
the shape rather than the file, alongside the port's other reachability guards.
Every survey row comes out of `SurveyMapper.fromCourseDoc` reading a course
document shaped like the server's, and every submission is authored by the
production writer through `SurveysRepository.submitResponse` or
`createBulkSurveySubmissions`. Nothing hand-writes a key. The first test
asserts the mapper really did set `courseId`, because the rest of the file
proves nothing if it did not.

The user-visible half is a widget test in
`test/ui/courses/take_course_screen_test.dart`: seed the onboarding course and
its attached survey, answer it through the real writer, tap **Finish**. Pre-fix
it failed with

```
Expected: no matching candidates
  Actual: Found 1 widget with text "please complete the survey to finish the course"
```

and post-fix the finish reaches the rating dialog it is gated in front of.

## Each defect, with failing-first evidence


Each row names the revert it was replayed against, because **which** revert
matters: the writer fix and the repair are defence in depth for the same
symptom, so a test that only asserts "the course can be finished" cannot tell
them apart.

| # | defect | revert replayed | pre-fix failure |
|---|---|---|---|
| 1 | a learner who answered the attached survey is still blocked | the whole diff (the pre-fix head) | `hasUnfinishedSurveys` → `Expected: false / Actual: true` |
| 1 | the same, at the screen | the whole diff | the toast on a Finish tap after answering |
| 1 | the sheet's key | `createSurveyDraft`'s key alone | `Expected: 'survey-1@course-1' / Actual: 'survey-1'` |
| 1 | `createBulkSurveySubmissions`' key | the bulk writer alone | `Expected: 'survey-1@course-1' / Actual: 'survey-1'` |
| 2 | a bare-id sheet an earlier build wrote | the repair call | `Expected: false / Actual: true` |
| 3 | `updateSurveyResponse` read the whole `parentId` as a survey id | that reader alone, writers left fixed | `Expected: ['It was fine'] / Actual: []` — resuming a pending sheet found no questions and wrote **no answers at all** |
| 4 | the dashboard prompt keyed on the raw value | that reader alone, writers left fixed | `Expected: length of <1> / Actual: []` — the prompt silently dropped every pending survey belonging to a course |
| 5 | the repair rewrote the **adoption marker** | the `status` clause | `Expected: true / Actual: false` — the marker reached the composite key, the status-blind count accepted it, and the course was **spuriously unblocked** |
| 6 | the repair rewrote an **exam** attempt sharing a survey's id | the `type` clause | `Expected: 0 / Actual: 1` |
| 7 | re-sending a survey duplicated a legacy pending sheet | the bulk pre-loop repair | `Expected: length of <1> / Actual: [2 rows]` |

**Two rows are the whole diff rather than one rule, and that is a real
limitation, not shorthand.** Reverting `createSurveyDraft`'s key alone leaves
2189 of 2190 tests green, because `repairCourseSurveyParentIds` heals the bare
id at read time — so both headline behavioural tests, including the screen one,
still pass. They discriminate *writer OR repair*, not the writer. The writer is
pinned by the two key assertions and nothing else. The first draft of these
notes claimed every row had been demonstrated red against the exact rule it
guards; the implementation audit caught that, and it was wrong for the screen
row. Next lane: revert a survey writer and you get one repository failure and
green at every screen.

Readers I checked and left alone because they are already right:
`progress_repository._examIdFromParent` and `courses_providers`'
`split('@').lastWhere(courseIdSet.contains)` both split; `_deleteExamSubmissions`
and `latestPendingByUserAndParent` are passed the composite by their callers;
`surveys_repository:146`'s adoption check compares a bare id to a bare row.
`_liveParentDocument` hand-rolled its own `split('@').first` and now shares
`parentBaseId`, so there is one derivation rather than four.

---

## Two things the ground-truth audit overturned

Both were mine, and the second changes what this fix *is*.

**`adoptSurvey` does not always produce a course-less survey.** I had assumed a
team clone could never carry a `courseId`, which is what makes
`SurveyDao.getByCourseId`'s lack of a team filter safe. Kotlin's
`createMappedSurvey` copies **both** `courseId` and `stepId` from the source
(`SurveysRepositoryImpl.kt:181-182`), and `getAdoptableTeamSurveys` filters only
on `type = "surveys" AND isTeamShareAllowed = 1` (`ExamDao.kt:30-31`) — so a
course-attached survey whose document sets `teamShareAllowed: true` *is*
adoptable and the Kotlin clone inherits the course join. The **port's** clone
omits both columns (`surveys_repository.dart:99-118`), and that omission is now
load-bearing rather than incidental: if anyone "fixes" the clone to copy
`courseId` for parity, this gate starts demanding that the learner complete
every team's copy of the survey. Pinned by a test.

**Kotlin's mandatory-survey gate is almost certainly dead, so this is internal
consistency rather than restored parity.** `getSurveysByCourseId` (`:366-368`)
filters `examDao.getByCourseIdAndType(courseId, "survey")` — **singular** —
while `StepExam.type` is the embedded document's own `type`
(`CoursesRepositoryImpl.kt:744`: `if (examJson.has("type")) … else examKey`),
which for a survey document is `"surveys"`. Every other Kotlin reader uses the
plural: the Take Survey button itself is `getByStepIdAndType(stepId, "surveys")`
(`:531`), the surveys list is `getByType("surveys")`, `ExamDao`'s three defaults
are `"surveys"`, and `docs/DOMAIN_MODEL.md:100` states it independently.

The two queries are mutually exclusive, so **at most one of the Take Survey
button and the mandatory block can ever see a given survey**. A document
carrying `type: "surveys"` — the weight of evidence, since that is what the
`exams` database holds and the button is a long-standing shipping feature —
gets a button and never blocks. A document carrying no `type` blocks forever
with no button that could satisfy it.

The port cannot reproduce that split even if it wanted to: `Surveys` has no
`type` column, because `ExamMapper.fromDoc` uses the document's `type` to
*choose the table* and then discards it. Its reader is `getByCourseId` with no
type filter, so it finds the survey the button opens — which is the behaviour
the Kotlin feature was written to have. **The port's symptom, "the onboarding
course cannot be finished", is port-only; the fix makes a behaviour Kotlin does
not have internally consistent.** The composite key is still the right thing to
converge on: it is what Kotlin uploads, what Planet joins on, and what the
port's own exam path has always written. Documented at the code, at
`hasUnfinishedSurveys`.

**This belongs under *Faithful quirks* in `docs/kotlin-to-flutter-migration.md`**
and I have not put it there — that file is not this lane's to edit. Integrator:
the paragraph above is the text.

## The second audit found a live defect in my own repair

An audit of the ground truth does not audit the implementation. The diff was
green — format clean, analyze clean, 2184 tests, CI green on `62ecebc` — which
is exactly the state Phase 120 and Phase 123 shipped defects in. The second
`parity-auditor` pass found one live defect, four unguarded halves and two
overstated claims.

### The live defect: `isNotValue('')` is `IS NOT`, not `!=`

The repair's guard was `row.status.isNotValue('')`, meant to negate Kotlin's
`it.status.orEmpty().isEmpty()` (`SurveysRepositoryImpl.kt:203-207`). Drift's
`isNotValue` emits the SQL infix operator **`IS NOT`**
(`drift-2.34.3/.../expression.dart:118-120` → `:148-154`), and `NULL IS NOT ''`
is **true** — so the guard admitted every null-status row, which is exactly the
class it existed to exclude.

**And a marker reaches null status by an ordinary route.**
`createSurveyAdoptionSubmission` writes `status: ''` *and* `isUpdated: true`, so
the generic `pendingUploads` sweep uploads it (Phase 123's item 4 is right that
`UploadConfigs.AdoptedSurveys` is unported, but the generic sweep still carries
the row); `serialize` sends `'status': ''`; and `upsertDocuments` reads it back
through `JsonUtils.getStringOrNull`, which maps the empty string to **null**
(`json_utils.dart:19-22`). So every marker that has round-tripped, and every
marker a Kotlin handset in the same deployment published, arrives with
`status = NULL`.

The consequence is the harm this phase exists to prevent, caused by the fix: the
marker was rewritten to `survey-1@course-1`, the status-blind count accepted it,
and a learner's Finish sailed past a survey they had never answered. Reproduced
against the then-current head:

```
Expected: true
  Actual: <false>
nothing has been answered; the marker is not an answer sheet
```

**In a real mixed fleet this is the repair's *most likely* match, not a corner
case.** A bare-id *answer sheet* can only come from a pre-Phase-125 build of an
unshipped port, or from Kotlin's `exams.courseId` race (item 3 below). A bare-id
*adoption marker* is written that way on purpose by every Kotlin handset there
is. The load-bearing half of the discriminator was the half that failed.

Now `coalesce([status, '']).equals('').not()` — the same idiom, for the same
reason, as `SubmissionDao.pendingUploads`' coalesced guest operands. **A drift
`isNotValue` is `IS NOT`; when you mean `orEmpty().isEmpty()`, coalesce.**

### The unguarded halves, and one more defect they exposed

| finding | now |
|---|---|
| a null-status bare-id row was untested | the reproduction above, as a test |
| **`createBulkSurveySubmissions` duplicated a legacy pending sheet** | `_repairSurveyParentId` runs before the loop; test red on its removal |
| `row.type.equals('survey')` was revertible with the suite green | an `exam` attempt sharing a survey's id; red on its removal |
| the null-user divergence was pinned only against a course with *no* surveys, where Kotlin agrees | pinned against a course that has one |
| the writer fix is masked by the repair at every screen | said plainly above, rather than over-claimed |

The duplicate is a defect the writer fix itself opened, and worth stating:
`getOrCreateSurveySubmission`'s existence check is
`latestPendingByUserAndParent`, a comparison of the **whole** `parentId`.
Pre-Phase-125 the writer and that lookup agreed on the bare key. With the writer
corrected and no repair, a legacy bare-id pending sheet is invisible to it, so
re-sending the survey gives the member a *second* sheet: their prompt dedupes to
the older one, they answer it, and the leftover row re-offers the same survey
until they answer it twice — two answer sheets uploaded for one survey.

`if (target == survey.id) continue;` was dead when the repair was only reachable
by `courseId` (every row `getByCourseId` returns has a non-empty `courseId` by
exact equality, so the target can never equal the bare id). It is live now that
`createBulkSurveySubmissions` hands it an arbitrary survey, and pinned. Two
clauses remain non-discriminating and are kept for clarity rather than
behaviour: the `courseId.isEmpty` early return (an empty `courseId` selects no
rows anyway) and, strictly, the dead-branch short-circuit when reached by
course — both are early-outs for an identical result, not guards.

### Two claims of mine it overstated

**"The port cannot reproduce that split."** It cannot on `type` — `Surveys` has
no `type` column. But the gate reads `courseId` and the button reads `stepId`,
and `SurveyMapper.fromDoc` writes each independently with `_presentOrAbsent`
(`survey_mapper.dart:93-94`), so a document carrying `courseId` and no `stepId`
lands found-by-the-gate and offered-by-no-button: the same shape I attributed to
Kotlin alone. Kotlin genuinely cannot do it (`StepExam.insertCourseStepsExams`
never reads either key from the document, `StepExam.kt:36-58`). Corrected at the
code.

**A test that seeds its own key.** The dashboard test hand-wrote
`'survey-1@course-1'` — the fixture shape these notes criticise two paragraphs
in. It now drives `createBulkSurveySubmissions`, so it covers the pair. Writing
it that way exposed a second flaw of mine: the leftover bare-id row I had added
*rescued* the assertion, so the test passed with the reader reverted. It is now
two tests — one with only the composite row, which discriminates the reader
(`Actual: []` on its revert), and one with both, which pins the
prompt-once-not-twice guarantee and is honestly labelled as not failing-first
evidence.

### Recorded, not changed

**The repair never reaches Planet.** It writes only `parentId` and leaves
`isUpdated` alone, so an already-uploaded row is repaired locally while the
CouchDB document keeps the bare key permanently — every other device needs its
own local repair. A payload already snapshotted into the outbox by `queuePending`
also POSTs the bare key. Re-queueing *would* be safe (`serialize` emits
`_id`/`_rev` when present, so CouchDB updates in place rather than duplicating),
and I am still not doing it: silently rewriting historical server documents on a
Finish tap is a bigger action than unblocking a learner, and Kotlin never
rewrites an uploaded `parentId` either. What needed correcting is the
justification — "the port uploads whatever `parentId` the row carries" is true
of the *general* case and precisely not of the already-uploaded rows the repair
fixes. The residue is real: Planet keeps a bare-id document its own joins may
not match to the survey.

**Confirmed at parity, stated because a clean result is useful.** The audit
independently checked every writer of a submission `parentId` (five; none
missed), every reader (my list of three was complete — it found no fourth, and
confirmed `submissions_exporter.dart`, `submission_detail_screen.dart`,
`submissions_screen.dart`, `user_information_screen.dart` and
`public_survey_uploader.dart` never read it), that composite is what Kotlin
uploads for the same row, that the repair causes no stream churn when it no-ops
(`UpdateStatement` notifies only `if (rows > 0)`), that calling it from a
boolean read breaks neither caller, and that both paths the fix serves are
reachable — the step's Take Survey tile into `submitResponse`, and the home
prompt's stripped `surveyId` into `updateSurveyResponse`. Neither fix guards a
dead path.

## Divergences from `hasSubmission` kept deliberately

These are now documented at the code as well as here — the implementation
audit noted that only (c) had made it into the doc comment.

1. **The null-user guard.** Kotlin has none in `hasUnfinishedSurveys`; the
   guard is inside `hasSubmission`, which returns `false` for a blank `userId`,
   inverting to "unfinished" — so Kotlin blocks a null user. The port returns
   `false` early. It is a real logic divergence and unobservable in Kotlin
   (the survey list is always empty there, per the typo above); the port's
   version cannot block a user it has no id to check. Kept. The blank-*course*
   half is not a divergence at all: `getByCourseIdAndType("", …)` matches
   nothing either.
2. **`questionDao.countByExamId(stepExamId) == 0 → false` is not ported.** In
   `getCourseStepData` that rule reads sanely — do not claim a submission
   exists for an exam whose questions have not synced. Inside
   `hasUnfinishedSurveys` it inverts into "a question-less survey blocks the
   course forever". Porting it would reintroduce the exact user-blocking bug
   this phase exists to remove, for a survey nobody can answer. Not ported,
   and recorded rather than silently skipped.
3. **`countByUserParentAndType` stays status-blind**, as Kotlin's is: a sheet
   the learner has merely *opened* satisfies the gate. Faithful, and the reason
   the repair must not touch adoption markers.

---

## `pendingUploads` scoping — the brief's second item

The brief asked me to check Phase 123's notes before assuming this was done.
**It is done**, and the notes are not "partially examined" on it — D2 is the
larger half of that phase. I re-read the query rather than the prose:
`SubmissionDao.pendingUploads` (`app_database.dart:2319-2340`) is unscoped
(`isUpdated = true`), excludes a guest *exam* attempt but not a guest's
completed survey (Kotlin's own asymmetry between `UploadConfigs.ExamResults`
and `UploadConfigs.Submissions`), excludes the anonymous `public_%` sheet, and
coalesces both guest operands. Six tests in
`submissions_repository_test.dart` were each demonstrated red against the exact
revert they guard, and Phase 123's second audit added a two-owner `queuePending`
test after finding the whole user-visible effect revertible with the suite
green. Nothing here needed changing.

What remains on that path is Phase 123's own "found, not fixed" list — items 1,
2, 5 and 6 (the stored exam blob's field list, the two-serializer split,
`sender`/`source`/`parentCode`, and POST-where-Kotlin-PUTs) — none of which is
about scoping.

---

## Files touched outside this lane's set

Both crossings are for a defect the writer fix would otherwise have caused, not
for tidiness, and each is one expression plus its comment.

| file | why |
|---|---|
| `lib/repository/surveys_repository.dart` | `updateSurveyResponse` read the whole `parentId` as a survey id. With the writers corrected it found no questions and wrote **no answers at all** — a learner resuming a sent sheet would have uploaded it empty. Demonstrated red. |
| `lib/providers/dashboard_providers.dart` | `pendingSurveysProvider` keyed its dedupe map and its `getByIds` lookup on the raw `parentId`, where Kotlin's `getUniquePendingSurveys` uses `examIdFromParentId()`. With the writers corrected the prompt dropped every pending course-attached survey. Demonstrated red. Plus one import. |

Lane B (18 upstream master commits) and Lane C (`lib/ui/notifications/`,
`lib/l10n/`, `lib/ui/components/relative_time.dart`) — no overlap either way.
No new l10n keys; `app_en.arb` untouched.

---

## Found, not fixed

The next round's highest-yield brief is this list, so it is precise.

**1. The step tile never says "Redo survey" or "Retake test", and this is the
only user-visible Kotlin reader of the composite survey key.**
`CourseStepFragment.hideTestIfNoQuestion` (`:242-262`) swaps
`record_survey` → `redo_survey` and `take_test` → `retake_test` off
`getCourseStepData`'s `hasExam`/`hasSurvey`, which are
`hasSubmission(stepExams[0].id, step.courseId, userId, "exam"/"survey")`
(`CoursesRepositoryImpl.kt:530-542`) — the composite key, on the *plural*-typed
rows, so unlike the mandatory gate **this one works in the shipping Kotlin
app**. The port hardcodes `l10n.takeTest` and `l10n.recordSurvey`
(`take_course_screen.dart:395,416`). Deliberately not done here: it needs two
new ARB keys (Kotlin's `take_test`/`retake_test` carry a `%d` count the port's
`takeTest` does not), a port of `hasSubmission` itself, and a provider in
`courses_providers.dart` — a file this round's other lanes could touch, and
exactly the collision *Running parallel lanes* warns about. It is a missing
feature rather than a defect in the key, so widening this diff for it would
have been the wrong call. **It is the natural next phase, and my writer fix is
its precondition** — without the composite key the survey half of the swap
could not have worked.

**2. A locally-authored submission that is uploaded and then pulled back
probably duplicates.** `upsertDocuments` keys its row on the document `_id`
(`:1300`) while a locally-authored row's primary key is a sha1 and
`markUploaded` only fills in `couchId`/`rev` — so the pull inserts a *second*
row for a submission the device already has. The port has already solved this
shape once, for team tasks: `TeamTaskDao.getByAnyIds` (`app_database.dart:500`)
exists precisely because "`markUploaded` fills in `_id`/`_rev` and leaves `id`
alone… so a sync-in keyed on the document `_id` would insert a second row".
`SubmissionDao` has `getByIdOrRemoteId`, so the machinery is there and unused
by the walk. I did not confirm it end to end and it is outside this diff, but
it bears on this phase: it is *why* the repair survives a sync (the repaired
sha1-keyed row is not the row the pull overwrites) and it would double every
submission in the list and the exporter.

**3. Kotlin's `exams.courseId` is not stable, so its reader could not have been
written the port's way.** `bulkInsertExamsFromSync` calls
`insertCourseStepsExams("", "", jsonDoc, "")` (`SurveysRepositoryImpl.kt:387`),
`checkIdsAndInsert` skips blank ids (`StepExam.kt:61-68`) leaving
`courseId`/`stepId` null, and Room's `@Upsert` is a full-row replace — with
`"exams"` and `"courses"` in the *same parallel batch* (`SyncManager.kt:143-168`),
whichever lands last decides whether a course-attached survey still knows its
course. Phase 110 recorded this and the port already diverges
(`_presentOrAbsent`, `survey_mapper.dart:93`). Worth knowing: even with the
singular/plural typo corrected, Kotlin's `getByCourseIdAndType` would be a coin
flip.

**4. The port's public-survey sheet now uploads a composite `parentId` where
Kotlin's would upload a bare one**, because `SurveyMapper.fromDoc` keeps a
document's `courseId` while Kotlin's public path stores the survey with
`courseId = null`. Nothing on either side reads it back by the bare id — the
port resolves the row by the `submissionId` `createSurveyDraft` returned, and
`countByUserParentAndType` is user-scoped so a `public_<millis>` sheet cannot
unblock a signed-in learner's course. Composite is also the more correct value
for Planet to join on. Recorded because it is a change on the wire that no test
pins from the public side.

**5. Kotlin's `deleteCourseProgress` deletes no exam submissions at all**
(`CoursesRepositoryImpl.kt:589-598`): it collects `examDao.getByCourseId(courseId)`
ids — bare — and passes them to `getUnuploadedNonSurveyByParentIds`, whose
predicate is `parentId IN (:parentIds)`, while every submission for those exams
carries the composite key. Same defect class as this phase's, in the Kotlin. The
port has no `deleteCourseProgress`, so it inherited neither the method nor the
bug; if anyone ports it, port the fix.

**6. Phase 123's items 0b and 4 are still open and still coupled.** The port's
team-survey sharing does not reach the server by any path
(`UploadConfigs.AdoptedSurveys` unported), and because nothing uploads the
adopted row, `SurveyDao.deleteNotIn` deletes it on the next complete exams walk
— after which a member who answered that survey offline uploads a two-key
`parent` and the second device renders answers with no questions. My adoption
test now pins the clone's *shape* (no `courseId`, no `stepId`); it does not
touch the upload gap.
