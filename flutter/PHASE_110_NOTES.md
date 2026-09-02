# Phase 110 — the exam retry model

Phase 106 logged three divergences with one shared caveat: they could not be
closed individually because they are all consequences of an exam model the port
did not have. This is that model.

Kotlin's exam **refuses to advance past a wrong answer**. `updateAnsDb` returns
the verdict `saveExamAnswer` computed and both `btnNext`
(`ExamTakingFragment.kt:196-204`) and `btnSubmit` (`:641-645`) bail with the
`incorrect_ans` snackbar when it is false, so the learner retries until the
answer is right. Everything else follows from that:

- `mistakes` accumulates, `(existing?.mistakes ?: 0) + 1` per wrong attempt,
  and is uploaded (`Answer.createObject` sends `"mistakes"`).
- every answer in a *finished* submission is `isPassed = true`, because no
  wrong one can be left behind — a consequence, not an assertion.
- there is no score. `continueExam` shows a thank-you dialog
  (`BaseExamFragment.kt:127-149`) and the submission goes up as
  `requires grading` for Planet to mark.

The port graded the whole attempt in widget state, wrote it once at the end,
computed a percentage, and never wrote `mistakes` at all.

## What changed

**The attempt is now persisted before the answers, and each answer as it is
given.** `createExamDraft` (one write at the end) is replaced by
`startExamSession` + `saveExamAnswer`, which is where Kotlin has them. This is
not a stylistic port: `mistakes` is `existing.mistakes + 1`, so there has to be
an `existing` row on disk to add to. Reimplementing the accumulator in widget
state would have been the "port the field without the model" half-measure
Phase 106 warned against.

| Kotlin | port |
|---|---|
| `startExamSession(recreate = true, deleteStale)` → `createExamSubmission` | `SubmissionsRepository.startExamSession` |
| `deleteExamSubmissions(examId, courseId, userId)` | `_deleteExamSubmissions` |
| `saveExamAnswer(ExamAnswerData)` → `Boolean` | `SubmissionsRepository.saveExamAnswer` → `Future<bool>` |
| `ExamAnswerUtils.checkCorrectAnswer` + 3 helpers | `ExamGrading.isCorrect` / `isSelectionCorrect` / `isTextCorrect` |
| `btnNext` / `btnSubmit` / `btnBack` | `_advance` / `_submitExam` / `_goBack` |
| `isQuestionAnswered` | `_isAnswered` |
| `continueExam`'s thank-you dialog | `_showResult` |

No schema change (still v44); `mistakes`, `grade` and `isPassed` were already
columns on `SubmissionAnswers`, and `serialize` was already uploading
`mistakes` — it was just always 0.

Two `app_en.arb` keys added, English only: `thankYouForTakingExam` and
`pleaseAnswerToContinue`. `incorrect_ans` reuses the existing, already
translated `incorrectAnswer` key rather than adding a sixth — see *For the
integrator*.

## The audit overturned my first reading, and it mattered

I started from the belief that Kotlin's `select` grading can never return true:
`saveExamAnswer` passes `ansForCheck = getChoiceTextById(question, ans)` — the
choice's display **text** — into `checkSelectAnswer`, which compares it with
`question.getCorrectChoice()`, and `ExamQuestion.insertCorrectChoice` looks
like it stores ids. I wrote that into `exam_grading.dart` as the justification
for the port's id-based comparison. **It was wrong twice over**, and a
`parity-auditor` pass at `effort: max` caught both halves:

1. `insertCorrectChoice`'s scalar branch does not store the id. It matches on
   `id` and then stores the matched choice's **`res`** (`ExamQuestion.kt:96-108`)
   — the legacy display-value key — so for a `{id, text}` choice it stores
   `[""]`. Same verdict, different mechanism, and the mechanism says the intent
   was text-vs-text all along.
2. **`insertCorrectChoice` is not the parser a course exam goes through.**
   There are two independent writers of `correctChoiceList`, and the one that
   matters is `CoursesRepositoryImpl.extractCorrectChoices`
   (`CoursesRepositoryImpl.kt:804-821`), on the `courses` walk, which resolves
   each entry through `ExamAnswerUtils.choiceDisplayValue` and stores **display
   text**. So `correctChoiceList` holds `["Water"]`, `ansForCheck` is
   `"Water"`, and `checkSelectAnswer` lowercases both. **Kotlin's grading
   works.**

So the port's id comparison is a *representation choice*, not a rescue: Kotlin
normalises the answer key to text, `ExamMapper._parseCorrectChoices` normalises
it to ids, and each compares like with like. Ids are the more robust half —
unique, where Kotlin's text comparison silently collapses two choices that
share a label — and it is what the port has stored since Phase 106. The point
that *is* load-bearing is that the mapper and the grader must agree: porting
`checkCorrectAnswer` literally on top of an id-storing mapper would compare a
label with an id, pass nothing, and under the gate produce an exam nobody can
finish. The comment in `exam_grading.dart` now says that instead of the wrong
thing.

**A Kotlin citation is not a Kotlin reading.** I named the right functions and
misread what they do, exactly as Phase 106 records happening to Phase 103.

## Defects found and closed, each demonstrated failing first

Eight of the nine new gate tests fail against the pre-fix `lib/`, and the two
grading defects were demonstrated with a throwaway probe. Run: the five
changed `lib/` files stashed, the new tests left in place.

| # | defect | pre-fix failure |
|---|---|---|
| 1 | **the screen advanced past a wrong answer** | `a wrong answer does not advance and says so` — `Found 0 widgets with text "Incorrect answer"` |
| 2 | **`mistakes` was never written** (column default 0, uploaded as if it meant something) | `each wrong attempt adds a mistake…` — `Found 0 widgets with text "Lyon"`: the second loop pass had nowhere to tap, because the un-gated screen had already moved to question 2 |
| 3 | **pressing on with no answer** was accepted, so a blank press would have scored a mistake against an unanswered question | `pressing on with no answer asks for one…` — `Found 0 widgets with text "Please select / write your answer to continue"` |
| 4 | **going back did not record the answer** — Kotlin's `btnBack` calls `updateAnsDb()` before moving (`:187-194`), so the mistake counts | `going back records the answer as it stands` — `Bad state: No element`: no answer row for q2 existed at all |
| 5 | **the result dialog invented a score** and a pass/fail against `passingPercentage`, which has no Kotlin counterpart at all (`grep -rn passingPercentage app/src/main` finds only parse/store/re-serialize) | `a finished exam is sent for grading rather than scored` — `Found 0 widgets with text "Thank you for taking this exam!…"` |
| 6 | **submission status was `complete`** where `saveExamAnswer` writes `requires grading` for an exam | same test — `status` was `'complete'`, `grade` was a device-computed percentage |
| 7 | **`parentId` was the bare `courseId`** where `createExamSubmission` writes `"$examId@$courseId"`. `ProgressRepository._examIdFromParent` takes the leading `@`-segment to find the exam, so the per-step mistake counts could never have matched their exam even once `mistakes` was populated — defect 2 was hiding this one | same test — `parentId` was `'course-1'`, not `'exam-1@course-1'` |
| 8 | **`isTextCorrect` was exact equality** where `checkTextAnswer` is `correctChoices.any { normalizedAns.contains(it) }` (`ExamAnswerUtils.kt:88-95`), asserted by Kotlin's own `testCheckCorrectAnswer_InputText`. Harmless under a percentage; under the gate it refuses a right answer carrying a stray word and traps the learner | probe: `isTextCorrect(q, 'It is Paris, I think')` → `Expected: true / Actual: <false>` |
| 9 | **a keyless question could not be passed** (`isSelectionCorrect` returned false on an empty key by design, for a percentage model). Under the gate that is a question no answer satisfies — and every `ratingScale` question in an exam has no key | probe: `isTextCorrect(rating, '7')` → `Expected: true / Actual: <false>` |
| 10 | **the per-answer `grade` badge** rendered `isCorrect ? 1 : 0` where Kotlin writes `1` for every answer and its adapter renders nothing | assertion, not a test: `answers.every((a) => a.grade == 1)` |

Two of the nine gate tests pass pre-fix and are kept as companions rather than
evidence: `a right answer advances` (the un-gated screen advanced too) and
`a text answer passes by containment` (the un-gated screen finished the exam
regardless of the verdict — its real evidence is defect 8's probe).

## The three decisions Phase 106 asked for

### Per-answer `grade`: take Kotlin's field, drop the badge

`saveExamAnswer` writes `grade = if (type == "exam") 1` — **1 for every exam
answer, right or wrong**. It is a "worth one mark" marker, not a score. The
audit confirms it is read **nowhere** in `app/src/main` (only the write and a
round-trip test) and is **not uploaded** — `Answer.createObject` emits only
`value`, `mistakes`, `passed`, `questionId`.

So the port writes `1`, and `submission_detail_screen`'s trailing badge is
**removed**. Keeping the port's `isCorrect ? 1 : 0` would have been the only
way to keep the badge meaningful, and that means inventing a field value to
feed a widget Kotlin does not have (`QuestionAnswerAdapter.bind` never reads
it, `item_question_answer.xml` has no view for it). The verdict is already on
the tile as its leading icon; how many tries it took is `mistakes`, which the
courses-progress screen shows per step, as Kotlin does.

### Submission status: `requires grading`

`complete` is the **survey** value. `saveExamAnswer`'s `when` is explicit:
`complete` for a final explicit *survey* answer, `requires grading` for an
exam's, `pending` otherwise. Adopted.

What reads it, from the audit:

- **Planet**: `requires grading` is what a teacher's grading queue selects on.
  The in-repo corroboration is `SurveysRepositoryImpl.kt:310`, which treats
  `complete` and `requires grading` as the same "finished" state.
- **Kotlin locally**: functionally via `status != 'pending'` —
  `SubmissionDao.kt:24` → `isStepCompleted` → `TakeCourseFragment.kt:323` step
  unlocking. Nothing ever moves a submission off `requires grading` locally;
  only a re-pull can.
- **the port**: nothing breaks. `pendingUploads` has no status filter, the
  exporter prints it verbatim, and the tile subtitle prints the raw string —
  which is what `SubmissionsAdapter.kt:77` does too, so a learner seeing
  "requires grading" is parity, not a regression.

I had feared the opposite: `SubmissionDao.getPendingSubmissions` is
`WHERE status = 'complete' AND …`, which would exclude a `requires grading`
exam and mean Kotlin never uploads one. **The audit refuted that**: there is a
second, exam-specific config, `UploadConfigs.ExamResults` (`:239-251`), feeding
from `getPendingExamResults` — `WHERE type = 'exam' AND parentId IS NOT NULL
AND userId IS NOT NULL AND (_id IS NULL OR _id = '')`, **no status filter**.
Kotlin does upload exam submissions. The premise failed, so the "bug or intent"
question does not arise: `requires grading` is intentional and `ExamResults`
was written to carry it.

The one thing that follows for the port: `SubmissionDao.pendingUploads`'
missing status filter is now **load-bearing and deliberate**. Adding
`status = 'complete'` to it, in a later tidy-up that reads
`getPendingSubmissions` as the model, would silently strand every exam
submission on the device. That note belongs on `pendingUploads` in
`app_database.dart`, which another lane owns this round — see *For the
integrator*.

### Submission-level `grade`: 0, and left to Planet

`createExamSubmission` sets no submission grade and neither does
`saveExamAnswer`; the only writer in Kotlin is the sync-in
(`JsonUtils.getLong("grade", submission)`). The port's device-computed
percentage is gone. Under the gate it could only ever have read 100%.

## Deliberate divergences

Three, all recorded at the code:

1. **A question with no answer key accepts any answer.** Kotlin fails it —
   `extractCorrectChoices` returns `emptyList()` and every check helper is then
   vacuously false. Under the gate that is a question no answer satisfies and
   no route past but abandoning the exam, and it is not hypothetical: every
   `ratingScale` question in an exam is in that position, as is any `input`
   whose author supplied no key. **This is the one place I think Kotlin's
   behaviour is wrong for this app**, and the integrator can overrule it. An
   *unanswered* question still fails, so the gate keeps its other job.
2. **A wrong press of Finish leaves the attempt `pending`.** Kotlin assigns
   `isExplicitSubmission = true` at `:631` *before* calling `updateAnsDb`, so a
   wrong final answer writes `requires grading` for an exam the learner has not
   finished — and `isStepCompleted` (`status != 'pending'`) then unlocks the
   next course step off a wrong answer. Pressing Back flips it back, which is
   how the bug stays quiet. Not ported; here the status also decides
   `isUpdated`, so it would additionally queue a half-finished attempt for
   upload.
3. **A half-finished attempt is not uploadable.** `getPendingExamResults` is
   status-blind, so a Kotlin attempt abandoned mid-exam goes up as `pending` on
   the next sync — and since `deleteExamSubmissions` then recreates the row
   with an empty `_id`, the retake POSTs a *second* document. The port holds the
   attempt until it is submitted (`isUpdated: false` until then). The port's
   `queuePending` is also swept by unrelated callers (`submissions_screen`), so
   without this an exam in progress would leak out on any other upload.

And one structural difference: the port creates the session on the **first
save** rather than before the first question is drawn, to keep `build` free of
side effects. The only behavioural consequence is that abandoning an exam
without answering anything leaves no row, where Kotlin leaves an empty
`pending` attempt its next `deleteStale` clears.

## Kotlin behaviours deliberately **not** ported

Recorded so a later phase does not read them as gaps. All from the audit.

- **`checkAnsAndContinue`'s `else` branch is dead code.** Its one caller is
  reachable only with `cont == true` for an exam (the `!cont` path returned
  already) and `cont` is unconditionally `true` for a survey. `isLastAnsvalid`
  is written and read nowhere.
- **Kotlin's free-text verdict reads stale state.** `updateAnsDb` passes `ans`,
  and for `input`/`textarea` `ans` is never updated from the EditText —
  `saveCurrentAnswer` and the text watcher both write `answerData.singleAnswer`
  instead. So a free-text answer is graded against `""` on the first press and
  marked wrong; navigating away and back re-renders, `loadSavedAnswer` assigns
  `ans`, and the same answer then passes. **A Kotlin free-text exam question
  can only be passed by leaving it and coming back.** The stored `value` is
  correct throughout; only the verdict is computed from the stale field. The
  port grades the answer it recorded, so this cannot arise.
- **Two parsers race over `exam_questions`.** `"exams"` and `"courses"` sync
  concurrently (`SyncManager.kt:145-169`), both produce the same row id, and
  `QuestionDao.upsertAll` is `@Upsert` — a full-row replace. So whichever walk
  lands last decides whether `correctChoiceList` holds labels or `[""]`,
  per sync, nondeterministically. The same race nulls `exams.stepId`. A defect,
  not a quirk: the same exam grades differently after two syncs. The port has
  one writer.
- **`ServerReachabilityWorker`'s upload gate disagrees with its selection.**
  `countPendingExamResults` is `LOWER(status) = 'pending' AND …`, so on that
  path a device whose only unsent work is a `requires grading` exam never opens
  the gate — while the two ungated callers upload it fine. The port has one
  queue.
- **`btnSubmit` doubles as `btnNext`.** Kotlin's Submit button is never hidden
  (no `android:visibility` in `fragment_exam_taking.xml`), reads "Submit"
  mid-exam and "Finish" on the last question, and pressing it mid-exam runs the
  same gate and then advances; `btnNext` is a shortcut that appears only once
  the question is answered. The port keeps its one forward button per state
  (Next, then Submit on the last question). The gating is identical; only the
  button count differs.
- **`hasOtherOption`** stays unported, as Phase 106 recorded, so
  `handleChecked`'s `ans = "other"` — which `getChoiceTextById` cannot resolve
  and which therefore traps the learner on any `select` where they pick
  "Other" — has no counterpart either.

## For the integrator

- **Two `app_en.arb` keys appended** at the end of the file:
  `thankYouForTakingExam`, `pleaseAnswerToContinue`. English only; the five
  locale files are untouched. Lane A owns `lib/l10n/`, so expect a trivial
  last-line conflict.
- **`incorrect_ans` reuses the existing `incorrectAnswer` key** ("Incorrect
  answer"), which was defined, translated in all six locales, and used nowhere.
  Kotlin's string is "Incorrect answer, please try again", and its five
  `values-*/strings.xml` translations are real human ones — better than the
  `[Nepali] Incorrect answer` placeholders the ARB carries. A follow-up key
  derived from `incorrect_ans` would be strictly better; I did not add one
  because the locale files are not mine this round.
- **No schema bump** (still v44) and no table or converter touched, so no
  `build_runner` step is needed for this lane.
- **One query lives in the wrong layer.** `_deleteExamSubmissions` builds its
  `delete` statements in `submissions_repository.dart` because `SubmissionDao`
  has no delete-by-parent and `app_database.dart` is Lane C's this round. It
  should become `SubmissionDao.deleteExamSubmissions(parentId, userId)`.
  Likewise `saveExamAnswer` reads the whole answer set and rewrites it, because
  `upsertAll`'s `answers:` map replaces a submission's answers wholesale; a
  `SubmissionDao.upsertAnswer` would drop that round trip. Neither is a
  correctness problem — both are inside one `transaction` — and the rows are
  one per question.
- **A note is owed to `SubmissionDao.pendingUploads`** saying its missing
  status filter is deliberate, because an exam is never `complete` and
  `getPendingSubmissions`' `status = 'complete'` would strand every one. Same
  file, same lane, not mine to write.
- **Two reachability blockers the audit surfaced, both outside this slice, and
  both meaning this gate may be guarding a screen nobody can open.** I did not
  act on either: the premise is an inference from Kotlin's DAO queries rather
  than a captured Planet document, and it is cheap to settle with one `curl`
  against a Planet `exams` database. Worth a phase of its own.
  1. **`TakeExamScreen` may be unreachable.** Its only entry is
     `course_detail_screen.dart:190`, gated on `ExamDao.getByStepId`. But
     `exam_mapper.dart:57` takes `stepId` from the exam document's own `stepId`
     key — which the Kotlin never reads from there — while the port's step ids
     are synthetic (`course_mapper.dart:117`, `'$courseId:$stepIndex'`). The
     lookup can only match a document that literally carries
     `"stepId": "<courseId>:<index>"`. The button may never render.
  2. **Nothing may fill `exams`/`exam_questions` for a real course test.** The
     only writer is `surveys_repository.dart:295` via `ExamMapper.fromDoc`,
     which returns `null` unless `type == 'exam'` — while the in-repo Kotlin
     evidence (`CoursesRepositoryImpl` reads exams as
     `getByStepIdAndType(stepId, "courses")`) says a Planet course test carries
     `type: "courses"` and arrives on the **courses** walk, which
     `course_mapper.dart:9-10` explicitly excludes. Every port test builds on a
     fixture (`'type': 'exam'`, `'stepId': 'step-1'`) that agrees with the
     mapper and may agree with nothing on the server.

  `exam_mapper.dart` is in this lane's file set and I left it alone
  deliberately — changing which documents it accepts on an inference would be
  reckless, and the fix, if it is one, belongs with the course-walk change in
  `course_mapper.dart`, which is not mine.
