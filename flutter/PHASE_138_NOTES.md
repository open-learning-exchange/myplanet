# Phase 138 — the adopted survey clone's lifecycle, and `pendingUploads`' status test

Lane A of a four-lane round. Two jobs, both handed over precisely by earlier
rounds' *Reported, not fixed* lists, and the round's own lesson is that **the
handover was right about the defect and wrong about half of the fix** — twice.

## Job 1 — an adopted team survey clone was destroyed on the next sync

Phase 136 found the clone had a lifetime of minutes. This is Phase 116's class
at its worst: ported, tested, green, and *destroyed*.

`SurveysRepository.adoptSurvey` mints a team's private copy of a shared survey
locally. Kotlin then uploads it — `ExamDao.getPendingAdoptedSurveys()`
(`ExamDao.kt:21`, `sourceSurveyId IS NOT NULL AND _rev IS NULL`) feeds
`UploadConfigs.AdoptedSurveys` (`UploadConfigs.kt:186-195`) to the `exams`
endpoint — and Kotlin's `exams` walk is insert-only, with no
delete-except-ids on that table anywhere in the tree (`bulkInsertExamsFromSync`
upserts; `ExamDao.deleteById` has zero production callers). The port had
neither half: no adopted-survey uploader anywhere in `lib/`, and
`SurveyDao.deleteNotIn` pruning every row without a `stepId` that the server
did not name.

Demonstrated failing before anything was written, on the production
`adoptSurvey` over a course document shaped like the server's:

```
deleteNotIn(['survey-1'])  ->  clone row null, question rows gone
a member's answer sheet    ->  parentId resolves to no survey at all
```

Three pieces:

1. `SurveyDao.pendingAdoptedSurveys()` — the port of `ExamDao.kt:21`, **both
   clauses**. `sourceSurveyId` alone is not a local-authorship marker: the
   courses walk reads it straight off a server-embedded survey
   (`survey_mapper.dart:163-167`), so an adopted copy Planet itself published
   carries it, and sweeping on one clause would re-POST somebody else's
   document. Same conjunction Phase 136 had to restore in
   `hasUnfinishedSurveys` after shipping half of it.
2. `SurveyDao.markUploaded(id, rev)`, and `deleteNotIn` spares
   `sourceSurveyId IS NOT NULL AND rev IS NULL` — an **unpublished** clone
   only, so the exemption lapses on publication rather than making a clone
   immortal. A document the server has stopped naming is still a deletion this
   prune should honour.
3. `AdoptedSurveysUploader` on the outbox, shaped like `EventsUploader`, with
   the sweep wired into both submissions-sweep sites.

### Kotlin records nothing, and that is a defect rather than a behaviour to port

`UploadConfigs.AdoptedSurveys` declares **no `responseHandler` and no
`markUploaded`** — contrast `Meetups` five lines above it (`:172-184`), which
has both. So the Kotlin row keeps `_rev = NULL`, stays in
`getPendingAdoptedSurveys()` for the life of the install, and is re-POSTed on
every sweep; `serializeExam` always writes `_id` (`StepExam.kt:73`), so CouchDB
answers the second POST with a 409 and every one after it, silently. Recording
the rev is also what lets the prune exemption be *scoped* — without it the only
available predicate is "spare every clone forever".

Note what this corrects in Phase 136's write-up: the clone does not survive in
Kotlin because it "joins every later walk's keep set". **There is no keep
set.** It survives because Kotlin never deletes. The upload's purpose there is
publication; in the port the upload alone would not have sufficed, because the
port's keep set is built only from `SurveyMapper.fromDoc`-mappable documents —
which is what the next finding turns on.

### The payload has to carry `type`, and the brief's version would have undone the fix

The brief said to POST `SubmissionsRepository.surveyParentDocument`. Doing
exactly that would have destroyed the clone by a longer route.

`surveyParentDocument` deliberately omits `type`: for a submission's embedded
`parent` object the value is not recoverable, because the port splits Kotlin's
one `exams` table on `type` and then discards it. But `SurveyMapper.fromDoc`
accepts a document only when `type == 'surveys'` (`survey_mapper.dart:66-69`)
and `ExamMapper.fromDoc` accepts **everything that is not**
(`exam_mapper.dart:30-35`), with `JsonUtils.getString` yielding `''` for a
missing key. So a typeless upload would come back as a **graded course test**
in the `exams` table, its id would go into `examIds` and never into the surveys
walk's `ids`, and `deleteNotIn` would delete the surveys row anyway.

For this upload the type *is* known: `createMappedSurvey` copies it from the
source (`SurveysRepositoryImpl.kt:180`) and every list `adoptSurvey` is
reachable from queries `type = "surveys"` (`ExamDao.kt:29-31`), so source and
clone are always plural. `serializeExam` emits it unconditionally
(`StepExam.kt:80`). `AdoptedSurveysUploader.documentFor` adds it, pinned by a
test that runs the payload back through both mappers.

**`courseId` survives the round trip**, and not by luck: `fromDoc` writes
`courseId`/`stepId` as `Value.absent()` when the document omits the key
(`_presentOrAbsent`), so the join `adoptSurvey` authored is preserved. Kotlin
would lose it — `bulkInsertExamsFromSync` calls
`insertCourseStepsExams("", "", jsonDoc, "")`, `checkIdsAndInsert` skips blank
ids, and Room's `@Upsert` is a whole-row replace, which is why Kotlin's
`getSurveyInfos` has to accept `pId == surveyId` **or**
`pId.startsWith("$surveyId@")` (`SurveysRepositoryImpl.kt:304-307`).

### Publishing the clone broke the mandatory-survey gate

**The most valuable thing this phase found, and it was a defect this phase
introduced.** Found by the ground-truth audit pass, on already-green code.

Phase 136 keyed `hasUnfinishedSurveys`' clone skip on
`sourceSurveyId != null && rev == null`, citing `ExamDao.kt:21`. That was exact
only for as long as nothing ever published a clone. Job 1 publishes it, and
the moment the rev is recorded the guard stops firing: the clone still carries
the source's `courseId`, so `getByCourseId` hands it to **every** learner on the
course, and nobody outside the adopting team has — or can have — a sheet keyed
`"<cloneId>@<courseId>"`. On `MANDATORY_SURVEY_COURSE_ID` that is Phase 125's
outcome reached by a new route: the course becomes uncompletable for everyone
outside whichever team adopted its survey.

Demonstrated: `hasUnfinishedSurveys('course-1','outsider')` is `false` before
the drain and `true` after it, permanently.

The guard is now `sourceSurveyId != null && stepId == null` — *does the courses
walk own this row's course join?* A server-authored course-step survey always
gets a `stepId` from `SurveyMapper._build` (positional, never blank); a clone
never has one, deliberately; and `releaseStepJoinsForCourse` can only null it
**together with** `courseId`, which drops the row out of this query altogether.
Publication-independent, and Phase 136's own counter-example — a server-authored
adopted step survey, which must still gate — passes unchanged and fails on the
one-clause version.

**And Kotlin has no such guard, which is why borrowing its predicate was the
mistake.** `getSurveysByCourseId` filters
`getByCourseIdAndType(courseId, "survey")` — *singular*
(`SubmissionsRepositoryImpl.kt:367-379`) — so a Kotlin clone, being `"surveys"`,
is never in that result set at all, rev or no rev. This is a port-invented
guard on port-invented breadth. `ExamDao.kt:21` is an **upload sweep**;
`rev` is a correct discriminator for a prune and was never one for a gate.

## Job 2 — `pendingUploads` had no status test, and one of the two named rows should keep going

Phase 134's sweep made an existing divergence systematic: `pendingUploads` runs
on every sync for every row on the handset, with `isUpdated = true` as its only
gate, where Kotlin's sweeps are `status = 'complete' AND …` (`SubmissionDao:41`)
and `type = 'exam' AND … AND (_id IS NULL OR _id = '')` (`:40`). It named two
rows the port sends and Kotlin never does. **Decided: only one of them stops.**

**The team-adoption marker stops.** `createSurveyAdoptionSubmission` writes
`status: ''` with `isUpdated: true`; no Kotlin config selects it. It is local
bookkeeping that a team took a copy of a shared survey — no learner authored it
and it carries no answers — and uploading it put an answerless `submissions`
document into Planet's response set for every adoption.

**Job 1 is what makes that safe, which is why the brief said to do Job 1
first.** Before this phase the marker was the port's *only* server-side trace
of an adoption, so withholding it would have lost the adoption off-device
entirely. With the clone published — carrying `teamId` and `sourceSurveyId` —
a second handset in the team learns of the adoption from the survey document,
which is the route Kotlin has always used.

**The `createDraft` free-form draft does not stop, and Phase 134's note that it
should is wrong.** `createDraft` is the submissions screen's New-submission
button, and `submissions_screen.dart:172-194` runs `queuePending` **and**
`drain` on the very next lines: the learner typed a title and an answer and
pressed Save. Having no Kotlin writer makes the *document shape* port-only; it
does not make the user's intent absent. Excluding it would have made that button
write to the device and nothing else — silently, since the sweep would return
zero and the drain would find nothing to send. "Planet may refuse this shape" is
a reason to fix the shape, not to strand the data. Kept, with a guard test
carrying the reasoning so the claim is not re-seeded.

### A null status is not a blank one

The first cut was `coalesce(status,'') IN ('', 'pending')`, and the coalesce was
wrong in the direction that loses data. Two of this table's *existing* tests
build a row with no status at all to probe the guest-exam test, and the sync-in
stores null for a document that omits the key — so coalescing null to `''`
silently stranded every status-less row. Caught by the full suite, not by my
own tests.

The predicate is `status IS NOT NULL AND status = ''`. It is safe to be strict
because a marker is only ever in this set as it was written, with the literal
empty string: the re-pull that turns `''` into null also writes
`isUpdated: false`. This is the **inverse** of the asymmetry Phase 136
celebrated catching in the adoption *guard*, where `it.status.orEmpty().isEmpty()`
(`SurveysRepositoryImpl.kt:206`) does mean null-or-blank. Same two values, two
readers, opposite correct answers — worth knowing before anyone "makes them
consistent". `FALSE AND NULL` is `FALSE` in SQL, so a null status fails the
conjunction rather than poisoning it to NULL and dropping the row.

Written as an **exclusion** rather than an allow-list of
`complete`/`requires grading`/`pending`. The two forms select identically today
— every local writer that sets `isUpdated: true` sets one of those four
statuses, and `upsertDocuments` writes `isUpdated: false` on every pulled row,
so a server-authored status can never reach the predicate — but they fail
differently: an unanticipated status under an allow-list is silently stranded,
under the exclusion it is one extra document.

## Method notes

**Two mutation checks silently did not apply, and both looked like a passing
suite.** `dart format` had reflowed the exact lines the mutation strings
matched, so `str.replace` was a no-op and the suite went green — which reads as
"nothing pins this fix". Both were caught only because the *first* one's green
result was implausible. Every mutation script here now asserts
`s.count(old) == 1` before writing. Phase 122's rule generalises further than
it was written: **mutation-test the test, and assert that the mutation landed.**

Nine mutations were run and every one was caught by the intended test: the
prune exemption, the payload's `type`, `markUploaded`, the exemption's scope,
`pendingAdoptedSurveys`' second clause, the status exclusion, the F1 guard
reverted to `rev`, the F1 guard's second clause dropped, and the null-status
coalesce.

**Two files outside the lane's listed set were touched**, deliberately and
flagged for the integrator: `lib/providers/dashboard_sync_provider.dart` and
`lib/background_entrypoint.dart`, two lines each. They are in **no other lane's**
set this round. The alternative was shipping an uploader with no caller —
ported, tested, green, and dead, which is the exact failure this phase is about.
Phase 134 established these same two files as the sweep sites for the sibling
uploader, and the addition is a literal port of
`SubmissionsUploader.kt:83-84`'s two adjacent calls.

**No Drift `schemaVersion` bump.** A prune exemption, a new query and a rev
write change no DDL; `rev` and `sourceSurveyId` already exist on `Surveys`.
**47 is unspent** — the next round can take it.

## Files

* `lib/data/local/app_database.dart` — `SurveyDao.pendingAdoptedSurveys`,
  `SurveyDao.markUploaded`, the `deleteNotIn` exemption,
  `SubmissionDao.pendingUploads`' status exclusion.
* `lib/repository/adopted_surveys_uploader.dart` — new.
* `lib/repository/submissions_repository.dart` — the `hasUnfinishedSurveys`
  guard.
* `lib/providers/app_providers.dart` — provider + drainer handler.
* `lib/providers/dashboard_sync_provider.dart`, `lib/background_entrypoint.dart`
  — the sweep, ahead of the submissions sweep and ahead of the surveys pull.
* `docs/kotlin-to-flutter-migration.md` — five deviations entries, including the
  four Phase 134 asked for and could not reach.
* `test/repository/adopted_survey_survives_sync_test.dart` (11),
  `test/repository/pending_uploads_status_test.dart` (4).

Gate green: `dart format` clean, `flutter analyze` clean, **2472 tests pass**
(2457 before).

## Reported, not fixed

### A schema bump still destroys an unpublished clone, and `submissions` outlives it

**The one this phase creates the expectation for and does not close.** `surveys`
and `survey_questions` are **not** in `localAuthorityTables`, while
`submissions`, `submission_answers` and `submission_questions` all are. So a
schema bump on a handset that has adopted but not yet drained drops the clone
and its questions and *keeps* the members' answer sheets — the same orphaning
this phase removed from the sync path, reachable by an upgrade instead.

By this project's own stated test — *can a sync restore this?*, not *is it
local?* — the answer is mixed and the tables qualify: a **published** clone
comes back on the next `exams` walk, an **unpublished** one exists nowhere else.
That is the `feedback` argument verbatim ("a feedback sync exists now, but it
only refills what already reached the server — which is precisely not these
rows"), and the mechanism is the `teams` precedent: preserve the whole table and
let the next `deleteNotIn` evict the stale cache half.

**Not done here, deliberately, because the cost lands on future rounds rather
than this one.** `createAll` does not *alter* a preserved table, so every
future column on `Surveys` or `SurveyQuestions` would need a hand-written
`_addColumnIfMissing` step — and forgetting one does not fail loudly, it leaves
the column absent on every existing install and breaks every query that names
it. `Surveys` gained `courseId`/`stepId` as recently as v35, so this is not a
hypothetical tax. It wants its own diff, with both tables' columns audited
against the upgrade path. Whoever takes it should read the
*preserved-table test* section of `docs/kotlin-to-flutter-migration.md` first.

### A 409 has no recovery, and the port's deterministic clone id makes one reachable

Kotlin mints the clone id with `UUID.randomUUID()`
(`SurveysRepositoryImpl.kt:86`); the port uses `'${surveyId}_$teamId'`
(`surveys_repository.dart:105`). The port's is better — two handsets adopting
the same survey for the same team converge on **one** document where Kotlin
writes two — but it makes a conflict ordinary rather than freakish: two leaders
in one team both adopt before either syncs, and the second POST is a 409.

Kotlin recovers: `UploadCoordinator.kt:169-204` GETs `$baseUrl/exams/<localId>`
on a conflict and reads `_id`/`_rev` back. The port has nothing —
`OutboxDrainer` classifies any `code < 500` as permanent
(`outbox_drainer.dart:160-172`) — so the row is abandoned with the local rev
unrecorded. It is **bounded**, which is why it is reported rather than fixed:
the losing device's next `exams` walk pulls the winner's document, upserts a rev
onto its own row, and the clone leaves `pendingAdoptedSurveys()`, so the
accretion is one dead outbox row per conflict rather than one per sync. The
handler's comment says this in place.

The fix is a conflict arm — GET the document, read the rev, `markUploaded`, and
report success — and it belongs at the drainer or in `PlanetApi`'s vocabulary
rather than in one uploader, since it is the same shape for every
client-supplied-`_id` POST in the tree. Bundling it here would fix one of
twenty.

### The abandoned-row accretion Phase 134 reported now has a twentieth instance

Unchanged and still a cross-uploader policy call: `OutboxDao.findOpen` ignores
an `abandoned` row so a fresh enqueue is never blocked by one, `clearAbandoned`
has one caller in the tree and `cleanup()` has none, and `outbox` is preserved
across schema bumps. `AdoptedSurveysUploader` inherits the shape. The 409 above
is its most likely trigger here, and it is bounded for the reason given.

### `releaseStepJoinsForCourse` would strip a clone that ever carries a `stepId`

Phase 136 reported this as a hazard for a future round; it is now **twice**
load-bearing, because `hasUnfinishedSurveys`' guard reads `stepId == null` as
"this is a locally minted clone". If a later round decides the clone should
carry a `stepId` — to match Kotlin's step button — it must fix three things
together: spare `sourceSurveyId IS NOT NULL` in
`SurveyDao.releaseStepJoinsForCourse`, re-key the `hasUnfinishedSurveys` guard
on something else, and re-read `SurveyDao.deleteNotIn`'s `stepId IS NULL`
selection. Doing only the first silently reverts Phase 136; doing only the
second silently reverts this phase. `app_database.dart` is this lane's file, but
the change is not: nothing today writes that `stepId`, so a guard for it would
be unreachable code guarding an unreachable state.

### `progress_repository.dart:92-95` still reads as a contradiction

Phase 136's report, unchanged, and still outside this lane's set. Its comment
justifies a `groupBy` with "`adoptSurvey` copies `stepId` onto a new row, so a
step can legitimately carry more than one" — a statement about *Kotlin*, which
now contradicts two files rather than one. The `groupBy` is correct and should
stay; it wants one clarifying clause.

### Adopt is still gated on team leadership; Kotlin gates on guest

Phase 132's and Phase 136's report, unchanged. `team_surveys_screen.dart:32` has
`canAdopt = membership?.isLeader == true` where Kotlin has no role gate at all
(`SurveysAdapter:83-90`, whose only visibility rule hides the start affordance
for a `guest` user). Still an authorization change wanting its own diff, and
still not in this lane's set. Worth noting that this phase raises the stakes
mildly: adoption now publishes a document, so widening who may adopt widens who
may write to the `exams` database.
