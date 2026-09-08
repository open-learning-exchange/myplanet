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

1. `Surveys.needsSync` (**schema v47**), a local-authorship flag written only by
   `adoptSurvey` — see *The port cannot ask Kotlin's question* below for why the
   literal port of `ExamDao.kt:21` was a data leak.
2. `SurveyDao.pendingAdoptedSurveys()` sweeps on that flag;
   `SurveyDao.markUploaded(id, rev)` records the rev and clears it; and
   `deleteNotIn` spares a row that still carries it — an **unpublished** clone
   only, so the exemption lapses on publication rather than making a clone
   immortal. A document the server has stopped naming is still a deletion this
   prune should honour.
3. `AdoptedSurveysUploader` on the outbox, shaped like `EventsUploader`, with
   the sweep wired into both submissions-sweep sites, each in its own `try`.

### The port cannot ask Kotlin's question, and the literal port leaked data

**The most serious thing the implementation audit found, and the fix is a new
column rather than a better predicate.**

`ExamDao.getPendingAdoptedSurveys()` is `sourceSurveyId IS NOT NULL AND
_rev IS NULL`, and in Kotlin that names **exactly one writer**:
`JsonUtils.getString` returns `""` for a missing key (`JsonUtils.kt:64-68`) and
both sync writers assign `_rev = JsonUtils.getString("_rev", …)`
unconditionally (`StepExam.kt:47`, `CoursesRepositoryImpl.kt:736`), so a synced
Kotlin row's `_rev` is `""` and never NULL. Only `createMappedSurvey`'s
explicit `_rev = null` (`SurveysRepositoryImpl.kt:172`) satisfies it — which
makes the `sourceSurveyId` clause nearly vacuous there.

**The port's mappers use `getStringOrNull`, so absent becomes NULL and that
distinction is gone.** `rev IS NULL` is true for every survey whose document
omitted `_rev` — which is *every* course-embedded survey, because a sub-object
carries no `_rev` of its own, and every public-API one. With `sourceSurveyId`
as the only other clause, and the courses walk reading that straight off the
server, the sweep selected **another team's private copy** and POSTed it to
`exams` under the signed-in user's credentials, and the prune spared it forever.
Reproduced on the tree's own embedded-survey fixture shape:

```
SWEPT: [survey-embedded]   teamId: someone-elses-team
```

Every embedded-survey fixture in the tree omits `_rev`; the only one that adds
it is the counter-example test I wrote in the first round, which is precisely
why my own test file was blind to the shape it should have caught.

A flag with one writer has no such ambiguity. It also frees both predicates
from depending on `surveys.rev`, a column **two sync walks disagree about the
ownership of**: `SurveyMapper._build` writes `rev: Value(...)` unconditionally,
so a courses walk clobbers the exams walk's authoritative rev with NULL, where
`ExamMapper.fromDoc` uses `_presentOrAbsent` for exactly this reason. That is
`survey_mapper.dart`, outside this lane's set — reported below, and no longer
load-bearing for anything here.

### Kotlin *does* record the rev, and claiming otherwise was my own misreading

**The clearest instance in this project's history of "a Kotlin citation is not a
Kotlin reading", and it is worse than the usual shape: the ground-truth audit
told me correctly and I wrote the opposite anyway.**

I read the config declaration — `UploadConfigs.AdoptedSurveys` really does
declare no `markUploaded` and no `responseHandler`, unlike `Meetups` five lines
above it — and concluded Kotlin records nothing. But `UploadConfig` carries a
**default** `persistUploaded` keyed on the model class (`UploadConfig.kt:46-56`,
`StepExam::class -> UploadUpdateType.Exams`), which
`UploadRepositoryImpl.markExamsUploaded` resolves to
`exam._rev = result.remoteRev` (`UploadRepositoryImpl.kt:76`), and
`runPipeline` calls it on every successful batch (`UploadCoordinator.kt:63-68`).
Kotlin records the rev, its row leaves `getPendingAdoptedSurveys()`, and there
is no second POST. The `Meetups` contrast was wrong twice over: `Meetups` is a
`RoomUploadConfig`, whose `persistUploaded` *is* only that lambda
(`RoomUploadConfig.kt:42-45`) so it has no default to fall back on, and its
`ResponseHandler.Custom("id", "rev")` is byte-identical to the `Standard`
default (`UploadConfig.kt:24`).

The first audit pass reported this correctly — it named
`UploadConfig.persistUploaded`, the `StepExam::class` branch, and
`markExamsUploaded`, and said "the lane's `markUploaded(id, rev)` matches this".
I latched onto the sentence before it ("declares no `responseHandler` and no
`markUploaded`") and wrote a wrong claim into four places: the DAO's doc
comment, the commit message, a test's `reason:` string, and a
*Deliberate deviations* entry — which is a **contract for later rounds**, so it
would have told the next reader that Kotlin's adopted-survey upload is broken.
All four are corrected. **Read the whole finding, not its first sentence**, and
when an audit and your own summary disagree, the audit is not the thing to
trust less.

What *is* port-specific: the port also prunes this table, where Kotlin never
deletes from `exams` at all. So `markUploaded` here serves two purposes against
Kotlin's one — record the rev, and clear the flag that keeps a clone out of the
prune.

Note also what this corrects in Phase 136's write-up: the clone does not survive
in Kotlin because it "joins every later walk's keep set". **There is no keep
set.** It survives because Kotlin never deletes. In the port the upload alone
would not have sufficed, because the port's keep set is built only from
`SurveyMapper.fromDoc`-mappable documents — which is what the type finding
below turns on.

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

### Three more the implementation audit found in this phase's green code

**The wiring was pinned by nothing.** All fifteen first-round tests built
`AdoptedSurveysUploader` directly and called `uploader.handler(...)` by hand, so
deleting the sweep from both sync paths — or deleting the handler's
registration in `outboxDrainerProvider` — left the whole suite green while the
clone was destroyed again. The registration mattered most and least visibly:
without it `OutboxDrainer._send` falls through to its generic replay branch,
which POSTs the stored payload, gets a 201 and calls `markCompleted`
**without ever calling `SurveyDao.markUploaded`** — the document exists, the
local row is still `needsSync`, and the clone is re-POSTed every sweep. **This
is the Phase 113 shape exactly: the writer exists, the reader exists, and
nothing proves anything calls them.** `test/providers/adopted_survey_sweep_test.dart`
now drives a real `ProviderContainer` from both paths and asserts the end state
on the row, which is reachable only if the sweep ran *and* the drain dispatched
to this uploader's own handler.

**The published clone reached a second handset with `courseId = null`, so the
two handsets keyed member sheets differently** — the Phase 120/125
writer-reader key class, across devices this time, and newly reachable *because*
this phase publishes the clone at all. `courseId` survives on the authoring
handset only because `_presentOrAbsent` leaves an existing value alone; device
B has never seen the row, so absent means NULL, and its Send keys the same
survey's sheets bare where A keys them compound. One member, two pending
sheets, and a column that oscillates between `repairCourseSurveyParentIds` and
the next pull. `documentFor` now emits `courseId`, which neither app's
serializer does — Kotlin is *consistent* rather than correct here, wiping the
column on the authoring handset too, which is why
`getSurveyInfos.resolveParentId` accepts both key shapes; the port has no such
tolerant reader. **This qualifies a claim in the first round of these notes**
("`courseId` survives the round trip, and not by luck"): it survived on the
device that wrote it and never arrived anywhere else.

**Both sweeps shared one `try`, contradicting the method's own doc comment.**
`queuePendingSubmissions`' doc says, forty lines above the code, "**Its own
`try`, not a shared one** … that is a Kotlin weakness rather than a behaviour to
reproduce; `UserDataWorker`'s per-step `runCatching` is the shape to follow, and
it is this one." It no longer was. And `SubmissionsUploader.kt:80-88`'s shared
`try` is not a counter-example, because `uploadAdoptedSurveys()` **cannot
throw** — `UploadCoordinator.runPipeline` catches `Exception` internally
(`UploadCoordinator.kt:87-92`) — while `queuePending` can, since it reads device
identity and `PlatformDeviceIdentitySource.read` rethrows on an engine with no
channel and no primed cache, which is what a headless WorkManager engine is.
One adopted clone on such a handset skipped the submissions safety net Phase 134
added *and*, in the foreground, the whole outbox drain. Each arm now has its own
`try`, pinned by a test whose identity source fails its **first** read only —
without that the two shapes are indistinguishable, since both arms read
identity.

### And one the audit's own boundedness claim turned into a fix

The audit reported the 409/deterministic-id gap as *bounded* and therefore
reportable, and my first write-up said the same. Probing it — rather than
reasoning about Drift's upsert semantics — showed it is **unbounded**: a
mapper's companion omits `needsSync` and `insertOnConflictUpdate` writes only
the columns present, so the losing device's re-pull gives its row the winner's
`rev` and leaves the flag set, and it re-POSTs and abandons one outbox row every
sync for the life of the install. Reachable by two leaders adopting the same
survey before either syncs, which the port's *deterministic* clone id makes
ordinary where Kotlin's `UUID.randomUUID()` makes it impossible.

So the 409 arm is ported rather than reported: `UploadCoordinator.kt:169-204`
GETs the document that already exists, takes its `_rev`, and reports success.
The port now does the same, with the drain's credential (the endpoint is stored
credential-free, so an unauthenticated CouchDB read is a 401 and the recovery
would never fire — pinned, after the first version of that test used
`any(named: 'authHeader')` and passed with the header removed). A GET that
fails returns the original 409 rather than inventing success: clearing the flag
with no rev in hand would drop the clone out of the prune exemption while it is
still, as far as this device knows, unpublished.

**Worth noting as a method point.** Two claims in this phase were wrong in the
same way — "Kotlin records nothing" and "the 409 case is bounded" — and both
were *reasoned* from a plausible reading rather than executed. The first cost a
false contract in the deviations list; the second would have shipped an
unbounded write loop. Neither survived a probe.

## Method notes

**Two mutation checks silently did not apply, and both looked like a passing
suite.** `dart format` had reflowed the exact lines the mutation strings
matched, so `str.replace` was a no-op and the suite went green — which reads as
"nothing pins this fix". Both were caught only because the *first* one's green
result was implausible. Every mutation script here now asserts
`s.count(old) == 1` before writing. Phase 122's rule generalises further than
it was written: **mutation-test the test, and assert that the mutation landed.**

**Twenty-one mutations** were run across both rounds and every one is caught by
the intended test — and **four of the twenty-one silently did not apply on the
first attempt**, all for the same reason. Every mutation script now asserts
`s.count(old) == 1` before writing; two of the four were caught only because
their green result was implausible, and one (`M21`, the 409 GET's credential)
turned out to be a real gap in the *test* rather than a failed mutation: it
passed with the header removed because the mock matched
`any(named: 'authHeader')`.

**Two files outside the lane's listed set were touched**, deliberately and
flagged for the integrator: `lib/providers/dashboard_sync_provider.dart` and
`lib/background_entrypoint.dart`, two lines each. They are in **no other lane's**
set this round. The alternative was shipping an uploader with no caller —
ported, tested, green, and dead, which is the exact failure this phase is about.
Phase 134 established these same two files as the sweep sites for the sibling
uploader, and the addition is a literal port of
`SubmissionsUploader.kt:83-84`'s two adjacent calls.

**Drift `schemaVersion` 47 is spent** — `Surveys.needsSync`. The next round must
take 48. The first round of this phase needed no bump and said so; the audit's
data-leak finding is what made a column the right answer rather than a better
predicate. No `_addColumnIfMissing` step: `surveys` is not in
`localAuthorityTables`, so it is dropped and recreated with the column. The
`if (from < 47)` branch is present and deliberately empty, so the next reader
sees the version was considered rather than forgotten.

**Nine existing `SurveyRow(...)` fixtures in seven test files gained
`needsSync: false`** — a non-nullable Drift column is a required argument on the
row class. Three are under `test/ui/`, which is the nearest this lane came to
another lane's territory; each edit is one line beside `teamShareAllowed: false`
and carries no behaviour.

## Files

* `lib/data/local/app_database.dart` — `SurveyDao.pendingAdoptedSurveys`,
  `SurveyDao.markUploaded`, the `deleteNotIn` exemption,
  `SubmissionDao.pendingUploads`' status exclusion.
* `lib/repository/adopted_surveys_uploader.dart` — new.
* `lib/repository/submissions_repository.dart` — the `hasUnfinishedSurveys`
  guard.
* `lib/providers/app_providers.dart` — provider + drainer handler.
* `lib/data/local/tables.dart` — `Surveys.needsSync`.
* `lib/providers/dashboard_sync_provider.dart`, `lib/background_entrypoint.dart`
  — the sweep, ahead of the submissions sweep and ahead of the surveys pull,
  each in its own `try`.
* `test/providers/adopted_survey_sweep_test.dart` (3) — the wiring.
* Nine `SurveyRow(...)` fixtures in seven existing test files.
* `docs/kotlin-to-flutter-migration.md` — five deviations entries, including the
  four Phase 134 asked for and could not reach.
* `test/repository/adopted_survey_survives_sync_test.dart` (11),
  `test/repository/pending_uploads_status_test.dart` (4).

Gate green: `dart format` clean, `flutter analyze` clean, **2479 tests pass**
(2457 before).

## Reported, not fixed

### A schema bump still destroys an unpublished clone, and `submissions` outlives it

**The one this phase creates the expectation for and does not close.** `surveys`
and `survey_questions` are **not** in `localAuthorityTables`, while
`submissions`, `submission_answers` and `submission_questions` all are. So a
schema bump on a handset that has adopted but not yet drained drops the clone
and its questions and *keeps* the members' answer sheets — the same orphaning
this phase removed from the sync path, reachable by an upgrade instead.

**And it is not hypothetical for this release: the v47 bump above is such a
bump.** Any handset carrying an adopted clone that has not yet reached the
server loses it on upgrade to this build, and keeps the answer sheets. The
window is narrow — an adoption is published on the next sync, and the outbox
drains on app resume — but it is real, and it is the price of fixing the leak
with a column. Worth stating plainly rather than leaving in the general case.

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

### The abandoned-row accretion Phase 134 reported now has a twentieth instance

Unchanged and still a cross-uploader policy call, but the audit sharpened what
is bounded and what is not — and my first write-up of this had it backwards.
`OutboxDao.findOpen` ignores an `abandoned` row so a fresh enqueue is never
blocked by one, `clearAbandoned` has one caller in the tree and `cleanup()` has
none, and `outbox` is preserved across schema bumps. So **any permanent refusal
that does not resolve the local row's state inserts one dead row and makes one
doomed POST per sync, unbounded** — a 400/401/403, or the handler's own "no
id/rev" error (`code == null`, so `(code ?? 0) < 500` is permanent on the first
attempt). The auditor demonstrated three passes, three rows, three POSTs.

I originally reported the 409 case as the bounded example, reasoning that a
later walk would supply the rev and drop the clone out of the sweep. **Probed,
and false**: a mapper's companion omits `needsSync` and Drift's
`insertOnConflictUpdate` writes only the columns present, so the re-pull hands
the row a `rev` and leaves the flag set — `needsSync=true rev=1-winner`, still
swept. That is why the 409 arm is *fixed* in this phase rather than reported
(see above) instead of being left as an example of a benign case. The general
policy item stands for every other refusal and for all twenty uploaders: bound
`cleanup()` and give it a caller, or clear an item's abandoned rows on
re-enqueue and lose the diagnostic.

### `surveys.rev` has two writers that disagree about who owns it

`SurveyMapper._build` (the courses walk) writes `rev: Value(...)`
unconditionally, so a course document's embedded copy of a survey clobbers the
`exams` walk's authoritative `_rev` with NULL. `ExamMapper.fromDoc` documents
precisely this hazard for `stepId`/`courseId` and uses `_presentOrAbsent` —
"whichever of the two walks lands last decides…" — and `rev` wants the same
treatment. It did not matter while nothing read `surveys.rev`; the first round
of this phase made it decide an upload *and* a prune, which is why the fix moved
to a dedicated flag instead. A full foreground pass repairs it (courses is area
2, surveys area 5), but `retry(DashboardSyncArea.courses)`, a courses-only
refresh, or a failed surveys walk leaves it NULL.

`survey_mapper.dart` is outside this lane's set, and nothing here depends on the
column any more — recorded so the next round does not build on `surveys.rev`
without settling it first.

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

### The outbox freezes `endpoint` at enqueue while the walk reads the live config

Narrow, inherited from the outbox design rather than added here, and worth
recording because this phase makes a *prune* depend on an upload. Both
`credentialFreeDbUrl` and `dbUrl` switch on `config.isAlternativeUrl`
(`url_utils.dart:45-51`). If the app moves to the clone URL between enqueue and
drain while the primary is still reachable, the clone is published on one host
and pruned by the other host's walk — and the `needsSync` exemption cannot save
it, because publication is exactly what cleared the flag. Kotlin cannot reach
this: it reads the live table at send time and never prunes `exams`.

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
