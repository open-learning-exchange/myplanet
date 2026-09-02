# Phase 104 — `SurveyMapper.choices` was corrupting every consumer

Three follow-ups Phase 102 flagged, plus four defects found while closing them.
Eight defects in all, each demonstrated failing on the pre-fix code before it
was touched.

## 1. `SurveyMapper` destroyed the choices it stored

`SurveyMapper.fromDoc` put `choices` through `JsonUtils.getStringList`, whose
`e.toString()` turns `{"id":"water","text":"Water"}` into the **Dart literal**
`{id: water, text: Water}`. Both halves are lost at once: the id — which is
what an answer records and what Planet matches — is gone, and the label is a
string nobody wrote. Proven directly:

```
PROBE stored choices: [{id: water, text: Water}]
PROBE on screen:      {id: water, text: Water}
```

The Kotlin is unambiguous about the intended shape. `ExamQuestion.choices` is
a `String?` holding the choices array **verbatim**
(`gson.toJson(getJsonArray("choices", question))`), and every reader parses it
back: `ExamTakingFragment.showCheckBoxes`/`selectQuestion` walk it as a
`JsonArray` and read `getString("text", choice)`/`getString("id", choice)`,
`ExamAnswerUtils.getChoiceTextById` builds an id→text map out of it, and
`SubmissionsRepositoryImpl` looks a choice object up by id to format an answer.
`StepExam.insertCourseStepsExams` runs that same `insertExamQuestions` for a
survey document, so surveys are not a different shape — the port simply had two
mappers for one Kotlin function, and only `ExamMapper` had the structured one.

**The fix is the port's own existing idiom.** `ExamQuestions.choices` already
uses `ExamChoiceListConverter`; `SurveyQuestions.choices` now does too, and
`SurveyMapper` parses through a new `ExamChoice.listFromJson` shared with
`ExamMapper` (whose private `_parseChoices` it replaces).

**No schema bump, and none was needed** (still v44). A converter is Dart-side
only: the generated column is `GeneratedColumn<String>('choices', …, false,
type: DriftSqlType.string, defaultValue: Constant('[]'))` before and after, so
the DDL is byte-identical and no migration step exists to write. Rows an
earlier build wrote decode as a choice whose text is that flattened literal —
not a crash — until the next surveys sync rewrites them; `survey_questions` is
a pure cache and is not in `localAuthorityTables`. Nothing else under
`lib/data/local/` changed shape.

### What each consumer was doing with the corrupted value

- **`take_survey_screen`** drew the flattened literal as the checkbox/radio
  label *and* stored it as the answer. Now renders `choice.text` and stores
  `jsonEncode(choice.toJson())`, which is what `Answer.valueChoicesArray` reads
  straight back (`gson.fromJson(choice, JsonObject::class.java)`) and what
  `public_survey_screen` already wrote. Resuming a pending submission decodes
  the entries back and keeps only choices the question still offers.
- **`SubmissionsRepository.createSurveyDraft`** copied the literals into
  `submission_questions.choices`, which is the display list behind the detail
  screen's "Choices:" row. It now stores `choice.text`, exactly as
  `createExamDraft` already did — that table is **preserved** across schema
  bumps, so its converter deliberately did not change.
- **`SubmissionsRepository.serialize`** sent `answers[].value` as a list of
  *strings*. `Answer.createObject` sends `valueChoicesArray`, i.e. parsed
  objects. `_answerChoices` now parses each entry, passing a non-JSON entry
  through untouched — that is how the exam path's bare choice ids survive;
  Kotlin has no such entries and `gson.fromJson` would throw on one.
- **`submission_detail_screen`** and **`SubmissionsExporter`** joined the raw
  entries, which after the above would print raw JSON at the user. Both now go
  through `ExamChoice.labelsFor`, a port of
  `SubmissionsRepositoryExporter.formatAnswer`
  (`JSONObject(choice).optString("text", choice)` per entry, raw entry as the
  fallback).
- **`SurveysRepository.adoptSurvey`** and **`_buildPublicAnswers`** needed no
  change: the first is a passthrough, the second reads the *answer* rows.

### `PublicSurveyScreen`'s local re-parse is gone

It escaped the corruption by parsing the fetched document itself. With the
mapper fixed that copy was a second, divergent parser — and the divergence was
load-bearing: it keyed question ids on the **route's** `surveyId` while the
mapper keys them on the **document's** `_id`, so where those differ the answer
map `_submit` builds could miss every row `createSurveyDraft` writes.
`_loadSurvey` now reads back the rows `saveSurveyFromPublicApi` has just
written. `_submit`'s push/`saved` flow is untouched.

Removing it depended on a **fifth** defect: `_parseQuestions` had a
body→header→title fallback the mapper lacked, because `SurveyMapper` read
`header` where `ExamQuestion.insertExamQuestions` reads **`title`** — the same
defect `ExamMapper` already carries a note about. A `title`-labelled question
mapped to `header: null` (`PROBE stored header: null`), so every question of
every real survey was unlabelled on the offline screen. The mapper now reads
`title` with `header` as a tolerant fallback.

## 2. A zero-question survey was submittable

`ExamTakingFragment` hides the form and the submit button and sets the counter
to `no_questions` when a document carries none. `PublicSurveyScreen` offered
Submit on an empty page, and `_submit`'s answered-everything guard is
*vacuously true* for zero questions, so it created and POSTed an answer-less
submission:

```
PROBE zero-question submit button: 1
PROBE zero-question submissions:   1
```

The screen now shows the empty-survey message and no button.
**No new `.arb` key**: `surveyHasNoQuestions` ("This survey has no questions")
already exists and is what `take_survey_screen` — which already had this guard
— shows for the same condition. Kotlin's two identical strings say "No
questions available"; reusing the port's own message keeps the two survey
screens consistent and adds nothing for the translator. Kotlin also shows a
Snackbar carrying that same text a second time; that is not ported.

## 3. Any create failure now annotates the title field

`AddResourceActivity.saveResource` branches on the **mode**, not on what went
wrong: a failed edit is toasted `failed_to_update_resource` (:164-167) and
**any** failed create sets `tlTitle.error = resource_title_already_exists`
(:204-207). That is a quirk, not a mistake to improve on — `saveLocalResource`
also fails when the picked file is missing, when storage is unavailable, and on
an IO or security error during the copy, and all four wear the duplicate-title
message.

The port keyed the message on `LocalResourceError`, which happened to read
correctly for the one failure it can currently produce and diverged for every
other. It now branches on `_isEditMode`. Note the divergence is **not reachable
with today's error set** — `_validate` catches an empty title before the repo
call and the file-copy failures are still an open gap — so the two tests drive
it through a repository stub, the only way to reach those messages at all.

## Defects found on the way

- **`take_survey_screen._submit` silently discarded the whole answered sheet.**
  It read `ref.read(sessionProvider).valueOrNull` on a screen that never
  watches that provider, so `user` was null and the early return dropped
  everything with no dialog, no snackbar and no row: `PROBE answer rows after
  submit: 0`, `PROBE submissions: 0`. **The third instance of this exact
  shape** (Phase 100's exam submit, Phase 102's add-resource save), and latent
  in the app for the same reason — the router holds a `ref.listen` on the
  session. Now `await ref.read(sessionProvider.future)` **inside** the `try`,
  per the Phase 100 harvest amendment, so a rejecting session takes the failure
  path rather than reintroducing the silence. **A provider a screen reads but
  never watches is null; await its `.future`, inside the guard.**
- **`selectMultiple` was matched case-sensitively in `take_survey_screen`.**
  `ExamTakingFragment.startExam` compares with `ignoreCase = true`; a document
  spelling it `selectmultiple` drew radios, so the respondent could pick
  exactly one of the answers they meant to give. Phase 102 fixed the
  public-survey half of this; the offline half was still wrong.
- **`_questionFromJson` read choice labels from `res` only.**
  `ExamAnswerUtils.choiceDisplayValue` is `text` first with `res` as the
  fallback, and `addCompoundButton` reads `text` alone. So a synced submission
  whose questions carry ordinary `{"id":…,"text":…}` choices stored `['', …]`
  and the detail screen's "Choices:" row was a list of commas.

## Failing-first evidence

Ten new tests fail on the pre-fix code (`git stash push -- flutter/lib`, then
`build_runner` so the generated sources match, and the same test files run):

| test | pre-fix result |
|---|---|
| `survey_mapper` keeps a choice object as an id/text pair | `Actual: ['{id: water, text: Water}', '{id: power, text: Power}']` |
| `survey_mapper` keeps a bare-string choice | `Actual: ['Yes', 'No']` (strings, not pairs) |
| `survey_mapper` reads the label out of `title` | `Actual: <null>` |
| `take_survey` renders the choice text | `Found 0 widgets with text "Water"` |
| `take_survey` stores the picked choice as `{id, text}` | tap misses; 0 answer rows written |
| `take_survey` stores every checked choice | same |
| `take_survey` question row carries the labels | same |
| `take_survey` renders `selectmultiple` as checkboxes | radios drawn |
| `submissions_repository` serializes a choice as an object | `Actual: ['{"id":"water","text":"Water"}']` |
| `submissions_repository` caches a choice by its display label | `Actual: ['', 'Power', 'Other']` |
| `submission_detail` shows the label, not its JSON | `Found 0 widgets with text "Water, Power"` |
| `public_survey` a zero-question survey cannot be submitted | button present, 1 submission created |
| `add_resource` any other create failure names the title field | `Found 0 widgets with text "A resource with this title already exists"` |
| `add_resource` a failed edit is toasted | `Found 0 widgets with text "Failed to update resource"` |

`test/data/local/converters_test.dart` cannot run pre-fix at all —
`ExamChoice.listFromJson`/`labelFor`/`labelsFor` do not exist there.

## New coverage

- **`test/ui/surveys/take_survey_screen_test.dart` — 8 tests, the screen's
  first.** It was the visible end of the corruption and had none.
- **`test/data/local/converters_test.dart` — 11 tests, the file's first**,
  including that an old flattened row still decodes rather than throwing.
- 3 in `survey_mapper_test`, 3 in `public_survey_screen_test`, 3 in
  `submissions_repository_test`, 3 in `add_resource_screen_test`, 1 in
  `submissions_screen_test`. One existing assertion updated:
  `surveys_repository_test`'s `questions.last.choices` now expects
  `ExamChoice` pairs.

**1631 tests, all passing** on Flutter 3.44.8 (the version `flutter.yml`
pins), format clean and analyze clean.

While this phase was in flight, the five `public_survey_screen_test`
`submitting` cases were red on the fork point — a cross-lane disagreement over
Phase 103's `saved != true` gate. Throughout my work they failed *identically*
to the baseline (`No matching calls … only the survey fetch`, i.e. the POST
never fired); my changes neither fixed, worsened, nor altered them, and I
touched neither those tests nor the gate. `origin/…-d3gmrd` at `2a9e0f7` then
reverted the gate — the parity audit found Kotlin does post a declined profile
step — and awaited `sessionProvider.future` in `_submit`/`_leave`. That merge
is in this branch, and with it the five pass. My zero-question guard sits on
the corrected submit path: it returns before `_submit` is reachable at all, so
it is independent of how that path ends.

## Test-harness notes

- The `take_survey_screen` selection set is `Set<ExamChoice>`, so
  `find.byType(RadioListTile<Object?>)` matches nothing — it is
  `RadioListTile<ExamChoice?>`, the same trap Phase 102 recorded for the
  public-survey card.
- Both survey screens hold an indefinite `CircularProgressIndicator` while
  submitting: never `pumpAndSettle` after tapping Submit.
- `add_resource_screen_test` reaches the unreachable-in-practice error branches
  with `_FailingResources`, a subclass of the **real** `ResourcesRepository`
  that stubs only the two write methods — the edit-mode prefill still runs
  against the database.

## For the integrator

- **No `.arb` key was added**, in any locale. Item 2 reuses
  `surveyHasNoQuestions`; items 1 and 3 needed none. The five non-English
  files are untouched.
- **No schema bump** (still v44) and no table shape changed. The one
  `lib/data/local/tables.dart` edit is a converter swap on
  `SurveyQuestions.choices` whose generated DDL is identical — see §1. If you
  would rather that came through you, it is a two-line revert plus the mapper.
- `flutter/pubspec.lock` is left exactly as the merge brought it. A
  `flutter pub get` on 3.44.8 wants `intl` 0.20.2, `matcher` 0.12.19, `meta`
  1.18.0 and `test_api` one patch below the committed entries, so the lock in
  git was resolved by a different SDK. CI resolves the way 3.44.8 does, so
  nothing is broken — but the file will keep drifting on any `pub get` until
  it is re-resolved on the pinned SDK.
- On the formatter-drift warning: my independent `dart format` run on 3.44.8
  reformatted `outbox_repository_test.dart` to **byte-identical** content to
  `e4c3b46`'s hand-restore, which is the useful confirmation that 3.44.8 is
  the version to trust for that gate. Nothing of mine is in that file now.

## Gaps found and deliberately left

- ~~`_submit` on `public_survey_screen` still reads `sessionProvider` with
  `.valueOrNull`.~~ **Closed by the merge**: `2a9e0f7` awaits
  `sessionProvider.future` in both `_submit` and `_leave`, which is the same
  fix this phase applied to `take_survey_screen` — a **fourth** instance of
  that shape, found independently by two lanes in one round.
- `_answerChoices`' tolerance for a non-JSON entry exists because the exam path
  stores bare choice ids in `valueChoices` where Kotlin's
  `ExamTakingFragment` stores choice objects. Making the two paths agree is a
  separate slice; until then the exam upload sends ids where Kotlin sends
  objects.
- `submission_detail_screen`'s correctness check lowercases `valueChoices` and
  compares them to `correctChoices` (ids). A survey answer now holds JSON
  objects, so that comparison can only ever match the exam path's ids — which
  is what it was written for, but nothing states it.
- Everything Phase 102 listed and left stands: no resources uploader, the
  picked file is never copied (which is why item 3's other three failure modes
  are unreachable), no `hasOtherOption`/`ratingScale`, no guest gate, a
  repeated Submit after a failed post minting a second submission, and
  `SurveyMapper.fromDoc` still requiring `type == 'surveys'`.
