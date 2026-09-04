# Phase 106 — the exam path sent choice ids where Kotlin sends choice objects

The two gaps Phase 104 logged and deliberately left, plus three defects found
closing them — one of which would have silently defeated the whole change.
Each demonstrated failing on the pre-fix code before it was touched.

## 1. What Kotlin actually writes

`ExamTakingFragment` is the **single** Kotlin writer for both exams and
surveys — `saveExamAnswer`'s `type` only picks the grading, the verdict fields
and the status, and nothing in `ExamTakingFragment`/`BaseExamFragment` branches
the answer shape on it. (Grepping `Answer(` construction in `app/` returns two
sites: `saveExamAnswer` and the sync-in.) So the shape is one thing, and
`SubmissionsRepositoryImpl.kt:517-541` says what it is:

| question type | `value` | `valueChoices` |
|---|---|---|
| `select` | `ansForCheck` — the choice's **display text** via `getChoiceTextById`, whose fallback is `map[id] ?: id` | `[{"id":"<ans>","text":"<ansForCheck>"}]`, or `[]` when nothing is picked |
| `selectMultiple` | `""` — the **empty string** | one `{"id":"<id>","text":"<text>"}` per `listAns` entry |
| anything else | the typed text | `null` |

The two halves are not independent. `Answer.createObject` sends `value`
whenever it is **non-empty** and only falls back to `valueChoicesArray` when it
is not, so what reaches Planet is: a bare **string** for `select` and for
text/rating, an **array of objects** for `selectMultiple`, and `[]` for an
unanswered `select` or an unanswered text question. `valueChoicesArray` is
never reached for a `select` that has a selection — `ans.isNotEmpty()` gates
`valueChoices`, and `ansForCheck` is then non-empty either way, so the pair
(empty `value`, objects present) is unreachable for that type. Kotlin's own
sync-in (`:707-724`) confirms the round trip: a JSON-array `value` becomes
`valueChoices`, a primitive becomes `value`.

### What the port did

- **`createExamDraft` stored the bare choice ids** (`valueChoices: ['c1']`) and
  left `value` null.
- **`createSurveyDraft`/`updateSurveyAnswers`** stored the objects correctly —
  Phase 104 fixed that half — but took `value` from the screen, which has no
  text field on a choice question and so hands over `''`. Kotlin derives it
  from the question.
- **`serialize` had the precedence the other way round**: `valueChoices`
  first, `value` only as the fallback. So even with the write shape aligned, a
  `select` answer would have uploaded `[{id,text}]` where Kotlin uploads
  `"Water"`.
- Three different `value` conventions were in play for one question type:
  `null` (exam), `''` (`take_survey_screen`), and `null` again from
  `public_survey_screen`'s select branch.

### The fix

A single `AnswerShape.forQuestion({type, choices, selected, text})` in
`submissions_repository.dart` — the port of that Kotlin branch, living where
Kotlin has it (the repository) rather than being re-derived per screen. All
three question-bound writers go through it:

- `createExamDraft` hands it the choice **ids** the screen records (that is
  what a radio/checkbox holds and what grading compares against) and it
  resolves each label from the question, the way `getChoiceTextById` does.
- `createSurveyDraft`/`updateSurveyAnswers` hand it the stored entries
  decoded back to `ExamChoice`, and it re-resolves them the same way.
- `serialize`'s precedence is flipped to value-first, with the condition
  `isNotEmpty` rather than `!= null` — `!value.isNullOrEmpty()` is what
  `createObject` tests, so an unanswered row uploads `[]` and not `""`/`null`.

`SubmissionDraftAnswer.choices` therefore becomes an *input* that gets
re-resolved rather than a value stored verbatim, which is what let
`public_survey_screen.dart` (owned by another lane this round) stay untouched:
whatever convention a caller uses for `value`, the repository now normalises it.

`converters.dart` grew the three pieces this needs: `ExamChoice.encode()` (the
stored entry), `ExamChoice.decode()` (the single reader for one, hoisted out of
`take_survey_screen`'s private duplicate) and `ExamChoice.textById()` (the port
of `getChoiceTextById`).

### `_answerChoices`' non-JSON tolerance is gone

It existed only to let the exam path's bare ids past, and Phase 104 asked for
its removal once the paths agreed. Every entry is now a choice object — both
write paths go through `AnswerShape`, and the sync-in stores JSON (see §3) —
so the branch is dead.

It is **not** left to throw the way `gson.fromJson(choice, JsonObject::class
.java)` does, and that is deliberate: `submission_answers` is in
`localAuthorityTables`, so a bare-id row an earlier build wrote survives every
schema bump, and `SubmissionsUploader.queuePending` serializes every pending
row in one unguarded loop — one such row would block the entire queue for
good. `ExamChoice.decode` resolves a bare entry to `{id: raw, text: raw}`,
which is exactly what Kotlin's own unresolvable-id fallback produces
(`getChoiceTextById` returns `map[id] ?: id`, so `saveExamAnswer` writes the id
as the text too). Nothing is invented and nothing is silently dropped.

## 2. The detail screen's correctness check

`submission_detail_screen`'s `_AnswerTile` lowercased `[value, ...valueChoices]`
and required every `correctChoices` entry to be present. `correctChoices` holds
choice **ids**; a stored entry is the whole `{id, text}` object. So the check
could only ever match the exam path while it stored bare ids, and matched
nothing at all once both paths stored objects. It now reduces each entry to its
id via `ExamChoice.decode` before comparing.

The comment says why it is not a port. Kotlin's `getSubmissionDetail` computes
`isCorrect = question.getCorrectChoice()?.contains(answer.value) == true`, but
`QuestionAnswerAdapter.bind` never reads the field and
`item_question_answer.xml` has no view for it — **the Kotlin detail screen
shows no correctness indicator at all**, so the icon is the port's own and
there is no Kotlin rendering to be faithful to. Its comparison is not available
to copy either: it pits the display text against a list of ids for `select`,
and `answer.value` is the empty string for `selectMultiple`. (It is not
literally never-true — a scalar `correctChoice` with `res`-labelled choices
makes it accidentally right, and a scalar `correctChoice` with `{id,text}`
choices makes it accidentally true for *any* `selectMultiple` selection,
because both sides are `""`. Two narrow document shapes, and the output is
discarded regardless.) Comparing ids is what `ExamGrading` does and follows
the precedent `ExamMapper._parseCorrectChoices` already set and documented.
`isPassed` stays the first signal: it is the verdict Kotlin actually computes
and uploads.

## 3. The defect that would have defeated all of it

`SubmissionsRepository._answerFromJson` — the **sync-in** — did
`rawValue.map((value) => value.toString())`. Kotlin's counterpart is
`valueElement.asJsonArray.map { it.toString() }`, and Gson's
`JsonElement.toString()` emits **JSON**. Dart's `Map.toString()` emits
`{id: paris, text: Paris}`, which is not JSON.

So every choice answer that came down from the server was cached in a form
nothing downstream could read back: the re-upload sent that literal as a
quoted **string** where Planet expects the object, the detail screen and the
PDF export printed it verbatim, and the id could not be recovered. And after
this phase it would have been the *only* remaining source of non-decodable
entries — the new id-based correctness check would have ticked every locally
authored answer and missed every synced one, which is the sort of split that
survives a whole test suite. Found by the parity audit, not by me.

A bare-string element is stored **unquoted**, where Gson keeps its quotes.
That is a deliberate divergence: Kotlin's own `valueChoicesArray` then throws
casting the `JsonPrimitive` to a `JsonObject`, so the unquoted form is the
better of two bad options (`SubmissionsRepositoryImplTest` pins that row's
list size only, so the throw is unnoticed upstream).

## 4. `ExamChoice.fromJson` read `text` alone

An exam document may label a choice with `res` rather than `text` — that is
why `ExamQuestion.insertCorrectChoice`'s single-id branch reads `res` at all.
Reading `text` alone gave those choices a blank label *and*, once
`AnswerShape` derives `value` from the choice, a blank stored value.

`fromJson` now reads `text` first with `res` as the fallback, which is
`ExamAnswerUtils.choiceDisplayValue` — the function that produces `value` —
and the rule `SubmissionsRepository._questionFromJson` already used for the
cached display labels (Phase 104). It diverges from `addCompoundButton`, which
reads `text` alone and so genuinely draws such a button blank; the two Kotlin
functions disagree with each other and the port keeps one display rule, the
same call Phase 104 made.

## Failing-first evidence

Ten new tests, each run against the pre-fix `lib/`:

| test | pre-fix result |
|---|---|
| `exam_flow` a select answer is stored as the choice object, not its id | `Actual: ['c1']`, `value` null |
| `exam_flow` a selectMultiple answer stores objects and an empty value | `Actual: ['c1', 'c2']` |
| `exam_flow` an unknown choice id falls back to the id as its text | `Actual: ['ghost']` |
| `exam_flow` a select answer uploads its display text, as Kotlin does | `Actual: ['c1']` — an array where Kotlin sends `'Paris'` |
| `submissions_repository` a survey select answer carries the choice text as its value | `Actual: ''` |
| `submissions_repository` a synced choice answer is cached as JSON, not as a Dart map | `Actual: ['{id: paris, text: Paris}']` |
| `submissions_screen` a choice answer is marked correct by its choice id | `Found 0 widgets with icon "IconData(U+0E159)"` |
| `converters` reads the label out of `res` when there is no `text` | `ExamChoice(id: 'power', text: '')` |
| `submissions_repository` a choice pick survives a question with no type | `Actual: []` — the pick discarded (see below) |
| `submissions_repository` a survey selectMultiple answer uploads objects | passed pre-fix — kept as the regression pin for the half Phase 104 fixed |
| `submissions_repository` serializes a bare entry as an object, not as a bare id | rewritten from `serializes a bare choice id untouched`, which asserted the removed tolerance |

Three existing assertions were updated to the new shape:
`exam_flow`'s `records each answer with its verdict` and
`take_exam_screen`'s partial-multi-select case (both `['c1']` →
`['{"id":"c1","text":"Paris"}']` / `['{"id":"c1","text":"Red"}']`), and the
tolerance test above.

### A regression this change nearly introduced

Reviewing the diff turned up one case where routing the survey path through
`AnswerShape` *lost* data the old code kept: a question that offers choices but
names **no type**. `take_survey_screen`'s card renders a radio group off
`choices.isNotEmpty` and reads the type only to pick checkboxes over radios, so
such a question really does get answered — and the plain-text branch (what
`saveExamAnswer` does for an unrecognised type) would have written `[]` over
the pick. It went from stored-and-uploaded to silently discarded, which is the
exact class of bug this phase exists to close.

`AnswerShape` therefore treats a question with choices and a selection as a
single-choice question regardless of type. Kotlin needs no such clause and has
none: `ExamTakingFragment.startExam`'s `when` renders **no input at all** for
an unrecognised type, so the answer cannot exist there. The clause can never
fire for the exam path either — `take_exam_screen._buildAnswerInput` defaults a
type-less question to a text field, so it never produces a selection. It is
scoped to exactly the screen whose renderer is more permissive than Kotlin's.

One knock-on tidy, recorded because it is a behaviour change with no
reachable symptom: `take_survey_screen._loadExistingAnswers` seeded the text
controller from `answer.value` for *every* question, and a `select` answer's
`value` is now the picked choice's label. It now seeds only a question that
offers no choices. Nothing observable changes today — a radio group cannot be
cleared, so `_submit`'s answered-everything guard cannot be fooled by the
leftover text, and a `selectMultiple` answer's `value` is empty — so it is a
guard against a later regression rather than a defect, and there is no test
for it because the case is unobservable from outside the widget.

## Divergences found and deliberately left

All four came out of the parity audit, and none is an answer-*shape* question,
which is why they are reported rather than folded in. They are the integrator's
to allocate.

- **`mistakes` is never written by the port** (column default 0), where Kotlin
  accumulates `(existing?.mistakes ?: 0) + 1` per wrong attempt and **uploads
  it**. This cannot be ported on its own: `mistakes` is only ever non-zero
  because a Kotlin exam **refuses to advance past a wrong answer**
  (`updateAnsDb` returns the verdict and both `btnNext` and `btnSubmit` bail
  with the `incorrect_ans` snackbar), so the learner retries until right —
  which is also why every answer in a finished Kotlin submission has
  `isPassed = true`. The port grades once and computes a percentage. Porting
  the field without the gate would upload a permanent 0; porting the gate is a
  different exam model and its own slice.
- **Per-answer `grade`.** Kotlin sets `grade = 1` for *every* exam answer,
  right or wrong — a "worth one mark" marker, not a score. The port sets
  `isCorrect ? 1 : 0`. Not uploaded, and the port's tile renders it as a badge,
  where Kotlin's adapter renders nothing; adopting Kotlin's value would make
  the badge meaningless. Left, with the same model caveat as `mistakes`.
- **Submission status.** `saveExamAnswer` sets `"complete"` only for a survey
  and `"requires grading"` for an exam's final answer; `createExamDraft` writes
  `'complete'`, and `requires grading` appears nowhere in `flutter/lib`. That
  is the status a teacher's grading queue on Planet selects on, so this one is
  consequential — but it changes what the submissions screen filters show and
  wants its own audit.
- **`correctChoices` is populated for non-select questions.**
  `ExamQuestion.insertExamQuestions` calls `insertCorrectChoice` only when
  `type?.startsWith("select") == true && question.has("choices")`;
  `exam_mapper.dart` calls `_parseCorrectChoices` for every question. So
  `{"type":"input","body":"2+2","correctChoice":"4"}` is gradeable in the port
  and ungradeable in Kotlin. Replicating the gate would make
  `ExamGrading.isTextCorrect` dead code and score every text question 0 — the
  divergence is only tolerable in Kotlin because of the cannot-advance model
  above. Left deliberately; also note `checkTextAnswer` is substring
  containment where `isTextCorrect` is exact equality, unobservable in Kotlin
  today for the same reason.
- **`hasOtherOption` stays unported**, so `saveExamAnswer`'s
  `{"id":"other","text":"<otherText>"}` entries never arise and `AnswerShape`
  has no branch for them. Recorded on `AnswerShape` itself: closing it needs a
  `hasOtherOption` column on `survey_questions`, which is not a preserved
  table, so `createAll` rebuilds it and the cost is a schema bump with no
  hand-written step.
- **`createDraft` bypasses `AnswerShape`** and writes its entries verbatim. It
  has no question rows and so cannot resolve a label, and no Kotlin
  counterpart — nothing in Kotlin creates a submission whose `parent` is a bare
  title. In production it is only ever called with free text. Named here so
  `AnswerShape` does not read as authoritative when one caller sits outside it.
- **The exporter's precedence flip is cosmetic, not a parity finding.** It was
  choices-first where `formatAnswer` is value-first, but the two are
  indistinguishable given the writer shape (a `select` row carries the same
  label in both halves, a `selectMultiple` row has an empty `value`, and a
  synced row has only one of the two). Flipped anyway for one rule everywhere,
  and the comment says it is not a fix. `serialize` is the only place the
  precedence is observable, because there the branches produce different JSON
  **types**.
- **Two Kotlin quirks not worth porting**, recorded so a later phase does not
  read them as gaps. `listAns` is a `HashMap` keyed by the button *text*, so
  Kotlin's `selectMultiple` entry order is unspecified and two choices sharing
  a label collapse into one; the port emits question order keyed by id.
  And a choice object with no `id` records **nothing** in Kotlin
  (`tag = ""` → `ans = ""` → `ans.isNotEmpty()` false) where the port falls the
  id back to the text — a divergence in the port's favour with a committed test
  pinning it.
- Also worth knowing, though nothing depends on it: after a `select` answer
  round-trips through the server the local row is `value="Paris",
  valueChoices=null`, and `populateCacheFromSavedAnswers` reads only
  `valueChoices` — so **Kotlin cannot restore a single-select selection from a
  synced row**. The id is deliberately not carried for that type.

## For the integrator

- **No `.arb` key added**, in any locale. The five non-English files are
  untouched.
- **No schema bump** (still v44) and no table shape changed. `tables.dart` is
  not touched at all this round — `AnswerShape` writes into the existing
  `StringListConverter` column, and the `converters.dart` additions are new
  methods on `ExamChoice`, not a converter swap.
- **`public_survey_screen.dart`, `surveys_repository.dart` and
  `submissions_uploader.dart` are untouched**, deliberately: the first two
  belong to another lane's surface, and routing everything through
  `AnswerShape` made a change there unnecessary. `surveys_repository`'s
  `_buildPublicAnswers` keeps working unchanged and **must not be unified**
  with `serialize`: `PublicSurveyActivity.buildPublicAnswers` sends the choice
  *object* for `select` where the `submissions` endpoint takes the string, so
  the two endpoints genuinely differ and the port already reproduces the split.
- The **submission status** divergence above is the one item here I would
  allocate a number to next.
