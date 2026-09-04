# Phase 123 — the submission upload shape

Lane B of a three-lane round. Two defects Phase 120 verified and reported
without fixing (`PHASE_120_NOTES.md` §"Reported, not fixed", items 1 and 2),
both in the submissions upload path, both the shape this port keeps paying
for: **a writer and a reader disagreeing about a key, where each half passes
its own test and only the pair is wrong.**

---

## D1 — `parent.questions` was dropped on upload

### What the Kotlin does

`serializeSubmission` (`SubmissionsRepositoryImpl.kt:813-862`) does **not**
upload the `parent` blob the submission row carries. It asks `getPayloadData`
(`:756-761`) for the live exam —

```kotlin
val examId = submission.examIdFromParentId()          // parentId.substringBefore("@")
val exam = examId?.let { examDao.getById(it) }
val questions = exam?.id?.let { questionDao.getByExamId(it) } ?: emptyList()
```

— and emits `StepExam.serializeExam(exam, questions)` when it finds one
(`:840-841`), whose `StepExam.kt:92` adds
`object.add("questions", ExamQuestion.serializeQuestions(questions))`. The
stored blob is the `else if` for an exam this device no longer has (`:842`).

### What the port did

`SubmissionsRepository.serialize` always sent the stored blob. That blob is
tiny: `_openExamSession` writes `{_id,_rev,name,courseId,totalMarks}` and
`createSurveyDraft` writes `{_id,name}`. Neither carries `questions`.

The reader half is the port's own: `upsertDocuments` fills the
`submission_questions` table from `parentJson?['questions']` — a table Kotlin
does not have, because Kotlin's detail screen reaches the questions through the
`ExamQuestion` rows it already holds. So the port had a reader for a key its
own writer never emitted.

**The consequence is silent cross-device data loss.** A learner completes an
exam or a survey on handset A; it uploads; handset B — or Planet web, or the
same handset after a reinstall — pulls the document and renders an answer sheet
of answers with no questions to put them against. Nothing errors, nothing logs,
and the detail screen looks merely empty rather than broken.

### The fix

`serialize` now resolves the parent the way Kotlin does and falls back to the
blob only when the row is gone. Because the port splits Kotlin's single `exams`
table into `Exams` and `Surveys` (`ExamMapper.fromDoc`: `type == 'surveys'` goes
to `SurveyMapper`, everything else is an exam), the one `examDao.getById` in the
Kotlin is two lookups here — `ExamDao` then `SurveyDao`. `SubmissionsRepository`
gains `ExamDao` as a constructor dependency, which is exactly what
`SubmissionsRepositoryImpl` injects (`examDao`, `questionDao`) for the same
reason.

`examParentDocument` and `surveyParentDocument` are static and pure, so the
field list is pinned by a test rather than reachable only through an upload.

**Two deliberate departures from `serializeExam`, both documented at the code:**

1. **`type` is not emitted — on either side.** Kotlin writes `exam.type` off the
   single table; the port's `Exams` has no such column, because the mapper uses
   the document's `type` to *choose the table* and then discards it. Adding the
   column is a schema change, and 46 belongs to Lane A this round — but the
   value is not recoverable from the table anyway: a course test carries
   `'courses'` (`CoursesRepositoryImpl.kt:744` keeps the document's own key), a
   standalone one with no `type` becomes `'exam'` (`StepExam.kt:39`), and
   nothing on `ExamRow` tells the two apart. Nothing in either tree reads a
   submission's `parent.type`, so the key is omitted rather than guessed.

   The first cut of this emitted `'surveys'` from `surveyParentDocument`, on the
   reasoning that the exams-database walk routes on `type == 'surveys'` and so
   pins the value. **The first `parity-auditor` pass overturned it**:
   `SurveyMapper.fromCourseDoc`'s second pass files every `steps[i].survey` into
   `Surveys` with **no** type filter at all, and Kotlin types one of those
   `"survey"` — singular. For exactly the rows the exam branch refuses to guess
   for, that would have been a guess.
2. **Each question carries its `id`, and its label under `title` as well as
   `header`.** `ExamQuestion.serializeQuestions` (`ExamQuestion.kt:110-124`)
   emits seven keys — `header`, `body`, `type`, `marks`, `choices`,
   `correctChoice`, `hasOtherOption` — with no id, and the label under `header`.

   The **id** matters because `_questionFromJson` keys the pulled row on it and
   falls back to a positional `<submissionId>-q<index>`, while the answers
   alongside it carry the real question id. A faithful upload hands the second
   device a question set its own answers cannot be joined to — and the audit
   showed that is *worse* than sending no questions at all: `submissions_exporter`
   prints the answers only through its `questions.isEmpty` fallback, so
   unjoinable questions print every prompt with no answer and drop the answers
   entirely.

   The **label** matters because `header` is a key no real document has. Every
   reader of a question in either tree takes it from `title` — Kotlin's
   `insertExamQuestions` (`ExamQuestion.kt:76`), the port's
   `ExamMapper.parseQuestions` and `SurveyMapper` — so what
   `serializeQuestions` uploads cannot be read back by the code that reads every
   other copy of the same question. Kotlin demonstrates that on itself:
   `SurveysRepositoryImpl.adoptSurvey:90-93` feeds `serializeQuestions` straight
   into `insertExamQuestions`, and **every adopted team survey in the shipping
   Kotlin app loses each question's id and its label.** Both keys are sent.

Same call as Phase 120's `answers.examId`: keep the quirk unless it breaks a
reader the port has and Kotlin does not.

`SurveyQuestions` has no `marks`, `correctChoice` or `hasOtherOption` column, so
a survey's questions omit those three keys rather than inventing defaults; a
survey question has no correct answer to lose. (Kotlin would send `""`, `[]` and
`false` there — `JsonUtils` defaults, not data.) Its `id` is `_rawQuestionId`
(`questionId ?? id`), which is what the answer's `questionId` carries and what
`createSurveyDraft` keys the local rows by.

**Worth saying out loud, so nobody goes looking for the Kotlin counterpart:**
Kotlin's submissions pull never reads `parent.questions` at all
(`upsertRoomSubmissionsFromSync:662-736` stores the blob and stops), because
Kotlin reaches a submission's questions through the `ExamQuestion` rows it
already holds. `submission_questions` is a port-only mechanism. This fix is not
restoring a Kotlin capability — it is feeding a reader Kotlin does not have,
which is exactly why the two departures above are justified.

### The evidence

`test/repository/submissions_sync_round_trip_test.dart` gains a group that
builds a **second `AppDatabase`** — a second handset that has never synced the
`exams` database, which is also what Planet web looks like — uploads from the
first and pulls into the second. All three were red before the fix:

| test | pre-fix failure |
|---|---|
| an exam attempt keeps its questions | `Expected: ['Capital of France?'] Actual: []` |
| the answers still line up with the questions | `Bad state: No element` — no question rows at all |
| a survey submission keeps its questions | `Expected: ['How is the water?'] Actual: []` |

Five more pin the document itself — `serializeExam` field for field, the three
`if`-guarded fields (`_rev`, `sourceSurveyId`, `teamId`), the question shape,
the survey's *absent* `type`, and the blob fallback — so the rest of the parent
cannot be quietly dropped again.

---

## D2 — `pendingUploads` was scoped the opposite way to Kotlin

### What I verified before changing it

The brief warned that widening an upload query is safe in a test and surprising
on a shared handset, so this is what I read rather than what Phase 120 said.

Kotlin reaches the `submissions` endpoint through **two** upload configs, and
neither is scoped to a user:

| config | query | serializer | guests |
|---|---|---|---|
| `UploadConfigs.Submissions` (`:253-267`) | `getPendingSubmissions()` — `status = 'complete' AND (isUpdated = 1 OR _id IS NULL OR _id = '')` (`SubmissionDao.kt:41`) | `serializeSubmission` | not filtered |
| `UploadConfigs.ExamResults` (`:239-251`) | `getPendingExamResults()` — `type = 'exam' AND parentId IS NOT NULL AND userId IS NOT NULL AND (_id IS NULL OR _id = '')` (`:40`) | `getExamUploadPayload` | `filterGuests = true`, `userId.startsWith("guest")` (`UploadConfig.kt:41-44`) |

The port has **one** uploader standing in for both, and its query was
`userId = ? AND isUpdated = true`.

### What changed, and what deliberately did not

- **The user scope is gone.** This is the actual defect. A shared handset is the
  normal deployment for this app: member A finishes a survey
  (`status = 'complete'`, `isUpdated = 1`, no `_id`) and signs out without
  syncing; member B signs in and syncs; arm B, being unscoped, sends A's sheet.
  Under the session scope it never left the device — and nothing in the port
  rescans, so *never* was literal.

  **It is not the bulk-survey send that depends on this**, which is the claim
  the brief inherited and the first `parity-auditor` pass overturned:
  `SendSurveyFragment` → `SurveysViewModel.sendSurveyToUsers` →
  `createBulkSurveySubmissions` writes each member's sheet `pending` and not yet
  updated, matching *neither* Kotlin arm, and the only writer of an answer is
  `saveExamAnswer` under the signed-in user. On the answering turn, scoped and
  unscoped agree. Someone would have checked that claim and found it does not
  hold.
- **`isUpdated` stays the status gate**, rather than Kotlin's
  `status = 'complete'`. The port merges the two configs, and an exam is never
  `complete` — it finishes at `requires grading`
  (`SubmissionsRepositoryImpl.kt:549-553`) — so filtering on `complete` would
  strand every exam attempt. The existing code comment already argued this; I
  re-derived it rather than trusting it.
- **`ExamResults`' guest filter is ported**, since the `isUpdated` arm is what
  stands in for that config: an exam attempt whose `userId` starts with `guest`
  is not queued. A guest's *completed survey* still is, because
  `UploadConfigs.Submissions` has no guest filter — the asymmetry is Kotlin's.
- Both operands are `coalesce`d. `type = 'exam' AND userId LIKE 'guest%'` is SQL
  `NULL` on a null column and `NOT NULL` is `NULL`, so the uncoalesced form
  drops a row that is nobody's guest exam rather than keeping it. That is its
  own test.
- **The anonymous public-survey answer sheet is excluded, and this one has no
  Kotlin counterpart.** It is the hazard the first audit ranked first, and it is
  exactly the "safe in a test, surprising on a real handset" case the brief
  warned about. `public_survey_screen:246` mints `public_<millis>` as the owner
  of a respondent who has no account — a sentinel Kotlin has no equivalent of,
  because `PublicSurveyActivity` runs the ordinary exam fragment and its sheet
  is authored by the signed-in user or nobody. The sheet is `status =
  'complete'`, `isUpdated = 1`, no `couchId`, and belongs to the **public**
  endpoint, which is not a CouchDB insert.

  Concretely, on a configured, signed-in handset — a supported flow: open the
  deep link, answer, tap Save on the profile step. `UserInformationScreen
  ._queueUpload` resolves the *signed-in* session and calls
  `queuePending`. Unscoped without this exclusion, that queues the respondent's
  sheet to the authenticated `<db>/submissions` with
  `user._id = "public_1770000000000"` — and then `markUploaded` sets
  `uploaded`, which is the exact flag `PublicSurveyUploader.queue` refuses to
  queue on (`public_survey_uploader.dart:66`). The answers reach the wrong
  database and the right one declines them.

### Three things I read and deliberately left alone

1. **`ExamResults`' status-blind arm is not ported.** Read literally, `type =
   'exam' AND (_id IS NULL OR _id = '')` uploads a *half-finished* attempt the
   moment any sync runs; `markUploaded` then stamps `_id`, and the finished
   attempt at `requires grading` matches **neither** arm afterwards — arm A
   because it now has an `_id`, arm B because its status is not `complete`. So
   porting it faithfully would let a mid-attempt sync permanently strand the
   finished attempt. The port's `isUpdated` gate reaches the same place for the
   ordinary case — take the exam offline, sync afterwards — without that hole.
2. **The survey-adoption record keeps uploading — as a hold-harmless choice,
   not a requirement.** Kotlin's `SurveysRepositoryImpl.createMappedSubmission`
   (`:210-242`) writes `status = ""`, `isUpdated = true`, no `_id`, which
   matches neither arm, so Kotlin never uploads it: it is a purely local marker
   read by `findExistingAdoption` and `getTeamSubmissionExamIds`. My first
   rationale for keeping the port's upload was that narrowing would risk the
   port's team-survey sharing, and **the audit showed that rationale is wrong**:
   Kotlin gets the adoption to the server by a different route entirely, as an
   *exam* document — `adoptSurvey:85-98` writes a `StepExam` with
   `sourceSurveyId` set and `_rev = null`, and `UploadConfigs.AdoptedSurveys`
   (`UploadConfigs.kt:186-195`) POSTs it to the `exams` endpoint via
   `ExamDao.getPendingAdoptedSurveys()`. Continuing to upload the adoption
   *submission* changes nothing and cannot lose data, so it stays; the real gap
   it uncovered is below.
3. **Kotlin's `_id IS NULL` disjunct is not added.** It looks like free
   fidelity, and it is not. The port's public-survey path is
   `createSurveyDraft` (`status = 'complete'`) followed by
   `markPublicSubmitted`, which clears `isUpdated` but records no `_id`, because
   the public endpoint is not a CouchDB insert and returns no document handle.
   Adding the disjunct would re-queue every delivered public answer sheet into
   the *regular* submissions uploader and POST it a second time. Kotlin cannot
   hit this: `PublicSurveyActivity` posts the answers directly
   (`SurveysRepositoryImpl.submitPublicSurvey:470`) and writes no local row at
   all — so the disjunct guards a case that exists only in the port, and guards
   it wrongly.

### The evidence

Six tests in `test/repository/submissions_repository_test.dart`, each
demonstrated red by replaying the exact revert it guards:

| test | reverted to | pre-fix result |
|---|---|---|
| a second learner's completed survey is still queued | the session scope | `Actual: []` |
| a sheet answered before the next member signs in still goes out | the session scope | `Bad state: No element` |
| a guest's completed survey is queued, as in Kotlin | the session scope | `Bad state: No element` |
| a guest's exam attempt is never queued | no guest clause | `Expected: empty` |
| a row with no type or owner is not mistaken for a guest exam | uncoalesced operands | `Bad state: No element` |
| an anonymous public answer sheet is not queued here | no `public_%` clause | `Expected: empty` |

---

## `app_database.dart`, line by line

Lane A owns `schemaVersion` 46, the migration, and the ratings/team-task
sections of this file. **This lane's whole diff there is one method in the
submissions DAO section**, and there is no schema change:

| what | change |
|---|---|
| `SubmissionDao.pendingUploads` | the query above, plus its doc comment. The signature drops its `String userId` parameter. |

Nothing else in the file is touched. `schemaVersion` stays 45; no table, column
or converter changed, so no generated output moved.

---

## Files touched outside this lane's set

| file | why |
|---|---|
| `lib/providers/app_providers.dart` | one line — `ref.watch(examDaoProvider)` in `submissionsRepositoryProvider`, the production half of D1's new dependency |
| `lib/repository/submissions_uploader.dart` | `queuePending` calls `pendingUploads()` with no argument; `userId` stays as the outbox row's session tag, now documented as not a filter |
| nine test files | one added constructor argument each (`database.examDao`); three of them also drop `pendingUploads`' argument. No assertion changed: `exam_flow`, `public_survey_uploader`, `step_assessment_sync`, `submissions_uploader`, `submit_photos_uploader`, `surveys_repository`, `survey_export`, `ui/exam/take_exam_screen`, `ui/courses/course_progress_screen`. |

---

## Found, not fixed

**0. A live defect, found while checking my own notes, and the most severe thing
in this file.** It is not part of either reported defect and is not fixed here.

**Every port writer stores a course-attached survey's `parentId` as the bare
survey id, and the mandatory-survey check looks for `surveyId@courseId`.** So it
never matches, and a learner who *has* completed the attached survey is still
told they have not.

Kotlin is internally consistent: `createBulkSurveySubmissions` (`:201-228`)
builds `"$examId@$courseId"` when the exam has a course, `createExamSubmission`
(`:449-456`) does the same, and `hasSubmission` (`:175-190`) queries exactly
that. The port writes the bare id from `createSurveyDraft` (`:102`),
`getOrCreateSurveySubmission` (`:539`) and `createBulkSurveySubmissions`
(`:522`), while `hasUnfinishedSurveys` (`:604`) — the port of `hasSubmission`,
Phase 52 — builds `'${survey.id}@$courseId'`.

Reproduced against the current head: seed a survey with `courseId: 'course-1'`,
run `createSurveyDraft` for `user-1` (which stores `parentId: survey-1`,
`status: complete`), then ask `hasUnfinishedSurveys('course-1', 'user-1')` —
it answers `true`. Its two readers are `take_course_screen._onFinish`, which
blocks the pop with the mandatory-survey toast, and `challenge_provider`, which
counts the challenge task incomplete. So on the current build a learner cannot
finish `MANDATORY_SURVEY_COURSE_ID` at all, however many times they answer.

Left for its own phase because the fix is a design call, not a one-liner: making
the *writers* Kotlin-shaped changes the stored `parentId` on devices in the
field and ripples through `latestPendingByUserAndParent`,
`_deleteExamSubmissions`, `countByUserParentAndType` and the dashboard's
pending-survey prompt; making the *reader* use the bare id leaves the port
internally consistent but still unlike Kotlin. Guessing between those inside an
upload-shape diff is exactly how a lane ships a regression.

**0b. D1 is still open for a team-adopted survey, and the second audit
demonstrated it.** The fix treats the stored blob as the fallback for an exam
this device no longer has. For a team survey that is not a rare case, it is the
case after the next sync.

`SurveysRepository.adoptSurvey` mints a **local-only** id, `'${surveyId}_$teamId'`,
with no `stepId` and no `courseId`. `SurveyDao.deleteNotIn` spares only rows with
a `stepId`, and the exams-database walk calls it with server ids on every
complete pass — and nothing uploads the adopted row (item 4 below), so it is
never in that keep set. It is deleted. A member who answers a shared team survey
offline, then syncs before the outbox drains, uploads a two-key `parent` and the
second device renders answers with no questions: exactly the failure D1's three
round-trip tests were written to prevent. They pass because they never prune.

Kotlin cannot reach this — it stores the adopted survey as a `StepExam` and
publishes it through `UploadConfigs.AdoptedSurveys`, so the row is in the exams
walk's keep set on the next pull. **The root cause is item 4, not the parent
lookup**, and the fix lives in `surveys_repository.dart` and the surveys DAO
section of `app_database.dart` — neither of which is this lane's to edit. It
belongs with item 4 in the same phase.

1. **The stored exam blob is not Kotlin's.** `_openExamSession` writes
   `{_id,_rev,name,courseId,totalMarks}` where `createExamSubmission`
   (`SubmissionsRepositoryImpl.kt:456-464`) writes
   `{_id,name,courseId,sourcePlanet,teamShareAllowed,noOfQuestions,isFromNation}`
   — no `_rev`, no `totalMarks`, four fields the port omits. Now that the live
   exam wins on upload, the blob is only the fallback for an exam this device
   has deleted, and the one field anything reads from it (`name`, via
   `submissionDisplayTitle`) is in both. Left alone to keep this diff to the two
   reported defects.
2. **The port has one serializer where Kotlin has two.** `getExamUploadPayload`
   (`:763-811`) differs from `serializeSubmission` in three ways: it adds a
   `team` object from `submission.teamObject`, it sends `parentId`/`type`/
   `status` raw instead of Kotlin's `""`/`"survey"`/`"pending"` defaults, and it
   always writes `parent` — even as `null` — rather than omitting it. Merging
   the two is what D2's single query stands on; splitting them is its own phase.
3. **The adoption record's uploaded `parent` changes shape** as a side effect of
   D1: it used to carry `SurveysRepository`'s hand-built
   `{_id,name,courseId,sourcePlanet,teamShareAllowed,noOfQuestions,isFromNation}`
   and now carries the live survey document. That is what Kotlin's serializer
   would do with the same row, so it is a move toward parity rather than away —
   but Kotlin never uploads the record at all, so nothing upstream pins it.
4. **`UploadConfigs.AdoptedSurveys` is unported, and it is a bigger hole than
   either defect above.** Kotlin publishes an adopted team survey by POSTing the
   new `StepExam` to the `exams` endpoint (`UploadConfigs.kt:186-195`, driven
   from `AutoSyncWorker:134` and `SubmissionsUploader:86`). The port's
   `SurveysRepository.adoptSurvey` (`:78-179`) writes the adopted `Surveys` row
   and its questions locally and nothing anywhere uploads them — grep
   `AdoptedSurveys` across `flutter/lib` returns only l10n strings. **The port's
   team-survey sharing does not reach the server today by any path.** Found by
   the first audit while checking a rationale of mine that turned out to be
   wrong; it wants its own phase.
5. **`serialize` still omits `sender`, `source` and `parentCode`**, which
   `serializeSubmission:835-837` always sends, and `createSurveyAdoptionSubmission`
   stores `source` and `parentCode` on the row only for them to be dropped. The
   file's existing comment calls the planet identifiers a known community-code
   gap; `sender` is not covered by that and is a plain omission. Three lines to
   close, left out to keep this diff to the two reported defects.
6. **The port POSTs where Kotlin PUTs.** `UploadCoordinator.kt:135-149` routes an
   item with a non-empty `_id` to `PUT <base>/submissions/<id>`; only a
   null/empty `_id` gets a POST. `SubmissionsUploader.handler` always POSTs
   (`submissions_uploader.dart:58-63`). CouchDB treats a POST carrying
   `_id`+`_rev` as an update, so this is equivalent in practice — but the branch
   is live, because `markComplete` sets `isUpdated` without clearing `couchId`.
7. **`examParentDocument` exports the port's own `correctChoice` normalisation.**
   The port stores lowercased choice **ids** (`ExamMapper._parseCorrectChoices`)
   where Kotlin's `correctChoiceArray` holds a choice's `res` value or the raw
   entries. It round-trips self-consistently — `_questionFromJson` lowercases
   whatever it reads — but it is new on the wire, and worth knowing if Planet
   ever compares the two.

---

## The audits

Two `parity-auditor` passes at `effort: max`, per the brief.

**The first, on the ground truth, before implementing.** It confirmed the
Kotlin reading behind both fixes — including, in more detail than I had, why
`ExamResults`' status-blind arm must not be ported — and overturned four things:

| what I had | what the Kotlin says |
|---|---|
| two conditional fields in `serializeExam` | three — `_rev` is guarded too (`StepExam.kt:73-75`) |
| `surveyParentDocument` can recover `type` as `'surveys'` | only for the exams-database walk; `SurveyMapper.fromCourseDoc` files a course step's survey with no type filter |
| the unscoping is what the bulk-survey flow needs | it is not; the shared handset is |
| keeping the adoption upload protects team-survey sharing | it does not — Kotlin publishes the adoption as an *exam* document, and that path is unported |

It also ranked first a hazard I had not seen at all: the anonymous
public-survey sheet, above. And it argued the `title` key, which I had left at
Kotlin's `header` — having already broken fidelity for `id` on precisely that
reasoning, emitting one and not the other was an inconsistency rather than a
decision.

**The second, on the finished diff.** Phase 120's lesson repeated: an audit of
the ground truth does not audit the implementation. The diff was green — format
clean, analyze clean, 2062 tests, and CI green on `c30f1e9` — which is exactly
the state Phase 120 shipped a rendering bug in. The second pass replayed 24
reverts and injections against the full suite and found **one live defect and
five unguarded halves**.

### The live defect it found: the parent lookup preferred the wrong table

`_liveParentDocument` tried `Exams` and then `Surveys`, and the notes treated
that order as a formality. **The two id spaces are not disjoint.**
`ExamMapper.mapStepExams` synthesizes `'$courseId-$stepId-$examKey'` for an
embedded step document with no `_id`, and `SurveyMapper.fromCourseDoc`'s first
pass uses the same `examKey: 'exam'` — so one `steps[i].exam` produces the same
id string whichever table its `type` files it under. Flip that `type` on Planet
and the next courses walk writes the row to the other table while the first
copy survives, because neither `deleteNotIn` prunes a row that still has a
`stepId`.

Kotlin cannot reach this: one table, one row, `@Upsert` overwrites `type` in
place. So there is no Kotlin order to port — but there is a disambiguator the
port has and Kotlin does not need: **the submission's own `type`**. An answer
sheet knows whether it was answering a survey or a test. `_liveParentDocument`
now takes it and tries that table first, defaulting to the survey side to match
`serializeSubmission:828`'s own `submission.type ?: "survey"`. Two tests pin
both directions; the survey one is red on the old unconditional order, uploading
`Stale test` where the sheet answered `Live survey`.

### The five unguarded halves, each now pinned

Each could be reverted with all 2062 tests green, and each is something this
file or a code comment calls load-bearing:

| unguarded | now pinned by |
|---|---|
| the `Exams`/`Surveys` lookup order | the two collision tests above |
| **D2's entire user-visible effect** — a Dart-side `.where((row) => row.userId == userId)` re-added in `queuePending` re-scoped the uploader with the suite green | a two-owner `queuePending` test asserting both sheets are enqueued and each document keeps its own owner |
| `surveyParentDocument`'s field list — five of its six rules reverted green, `title` and all three `if`-guards among them | a field-for-field test, plus an `if`-guard test |
| the `coalesce` on the guest `type` operand | a guest row with a null `type` |
| the `coalesce` on the guest `userId` operand | an exam attempt with a null `userId` |

The last two are the subtle pair. The existing test seeds a row with **both**
columns null, and SQL's `FALSE AND NULL = FALSE` means either `coalesce` alone
rescues it — so removing one at a time was invisible. Both of the inputs each
one actually protects are writable by the sync-in: `normalizeSubmissionUserId`
returns null for an empty `user._id`, and `type` comes straight from a document
that may omit it.

### Two findings recorded rather than fixed

**`serialize` has a new throw surface, and it diverges from Kotlin in the
port's favour.** Kotlin wraps the whole of `serializeSubmission` in
`try { … } catch { printStackTrace() }` (`:812-861`) with `getPayloadData` as
its first statement, so a Kotlin DAO failure uploads an empty `{}` document.
The port propagates, `queuePending`'s loop aborts with the rows before it
already enqueued, and the screen reports the failure. That is better behaviour,
but it is not the Kotlin's, the diff widens the surface from one DAO read per
row to three or four, and the loop now iterates the whole handset's backlog
rather than one user's. Saying so here rather than changing it.

**The `public_%` pattern's `_` is LIKE's wildcard, not an escaped literal.**
The code comment claimed the choice was deliberate; the two patterns differ only
on the bare string `public`, and no id the port mints is either one. The comment
now says that instead of overstating it.
