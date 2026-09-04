# Phase 102 — first tests for `add_resource_screen` and `public_survey_screen`

Two of the port's largest hand-written surfaces had no tests at all. They do
now — 33 between them — and, as in Phases 91, 95 and 100, writing the first
ones surfaced shipped defects: **seven**, each demonstrated failing on the
pre-fix code before it was touched.

## `AddResourceScreen` — 16 tests, four defects

The Kotlin specification is `AddResourceActivity.kt`. Covered: the
create/edit split, the current-year prefill, the private-team switch and its
`teamId` visibility rule, the whole `validate` ladder
(title → description → levels → subjects, in that order and no other), the
duplicate-title refusal, the edit-mode prefill and update.

**The dropdowns crashed the edit screen on ordinary server data.**
`_loadExistingResource` prefilled `_language`/`_openWith`/`_mediaType`/
`_resourceType` from the row and handed them to `DropdownButtonFormField`,
which asserts that a non-null value matches **exactly one** item. The option
lists are the eight fixed `strings.xml` arrays; a synced row carries whatever
the server wrote — a lowercase `mediaType`, an ISO language code,
`mediaType: "HTML"` (which `ResourcesRepositoryImpl` checks for by name, so it
is real data). In debug that is a hard assertion out of `build`; in release the
assert is stripped and the spinner silently shows its hint. `_DropdownField`
now drops a value it does not know, which is what `setupHintSpinner` does.
Worth noting the port added this prefill on its own: `prefillFields` sets only
the six text fields plus subject and levels, and `updateLocalResource` never
writes those four columns anyway.

**The signed-in user never reached the row.** `_save` read
`ref.read(sessionProvider).valueOrNull` on a screen that never watches that
provider — the Phase 100 shape exactly. `AddResourceActivity` resolves
`userSessionManager.getUserModel()` in `onCreate` and paints it into
`tv_added_by`, then reads both back when it builds the request, so `addedBy` is
a string in every case. The screen now awaits `sessionProvider.future`, inside
a guard, so a rejecting session costs the attribution rather than the resource.
**A provider a screen reads but never watches is null; await its `.future`.**

**`saveLocalResource` wrote `userId: [""]` where the Kotlin writes `[]`.**
`MyLibrary.setUserId` returns early on a null or blank id and leaves the list
empty. `[""]` is not a harmless placeholder: it fails the My Library predicate
(`userId LIKE '%"<uid>"%'`) *and* passes the catalog one
(`userId IS NULL OR userId NOT LIKE …`), so the row the user had just created
appeared in the public catalog and nowhere in their own library — the inverse
of both Kotlin views.

**The add-resource button was on the wrong screen.**
`ResourcesFragment.setupAddResourceButtonListener` is
`addResource.visibility = if (isMyCourseLib) VISIBLE else GONE`; the port had
`!_selecting && !shelfOnly`, and `shelfOnly` *is* `isMyCourseLib`. So the
affordance was missing from My Library, where it belongs, and offered in the
catalog, where the Kotlin hides it. One character, and it made everything above
reachable only from the wrong place.

Also fixed: edit mode substituted the current year for a row whose `year` was
null, where `prefillFields` does `setText(resource.year)` and leaves the field
empty — the port was quietly restamping a year the server never carried.

## `PublicSurveyScreen` — 17 tests, three defects

The Kotlin specification is `PublicSurveyActivity.kt`, which hosts the shared
`ExamTakingFragment` — so the fetch/POST envelope is the Activity's and the
question rendering and answer rules are the fragment's. Covered: both
load-failure paths, the `{"survey": {…}}` envelope, the three question
renderers, the answer requirement, and all four submit outcomes (delivered,
queued, and each post-submit destination). Every test drives the real
`SurveysRepository` and `SubmissionsRepository` over an in-memory database with
only `PlanetApi` mocked, so what lands in `submissions` is the real thing.

**An untouched answer sheet could be submitted.** The guard keyed on a
`required` flag read off the document — but `ExamQuestion` has no such field,
Planet never writes one, and `JsonUtils.getBool` returns `false` for a missing
key. So it was false for every question of every real survey and the guard was
unreachable: open a survey, tap **Submit survey**, and a complete submission is
created and POSTed as a row of empty strings. `ExamTakingFragment` has no
notion of an optional question — `btnNext` stays hidden until the current one
is answered and `isQuestionAnswered` blocks the submit otherwise — so every
question is now required, and the `required` field and its ` *` marker are
gone, matching the Kotlin, which has neither. **A flag no writer ever sets
reads as a working guard and is a dead branch.**

**`selectMultiple` was matched case-sensitively in the screen and
case-insensitively in the repository.** `ExamTakingFragment.startExam` compares
with `equals(…, ignoreCase = true)`, and so does this port's own
`SurveysRepository._buildPublicAnswers` (`_typeEquals`). The screen used `==`.
A document spelling the type `selectmultiple` therefore drew **radio buttons** —
the respondent could pick exactly one — while the repository, matching
case-insensitively, dutifully built a one-element array. The payload shape was
right; the other answers never existed. Each half had passing coverage of its
own; nothing ran the two together, which is what the new test does. Same shape
as the Phase 74 reactions round trip and the Phase 100 photo id.

**The load-failure card was a dead end.** `Navigator.of(context).maybePop()` on
a screen a deep link opened as the first route on the stack pops nothing, so a
respondent whose survey would not load sat on the failure card with a Close
button that did nothing. `PublicSurveyActivity` calls `finish()`; `_leave` now
pops when it can and otherwise goes where the submit path goes.

One thing deliberately **not** changed: `_submit` also reads `sessionProvider`
with `.valueOrNull` on a screen that never watches it. It is resolved in
practice on this path — `UserInformationScreen.initState` reads the same
provider first, and the screen is awaited before the branch runs — and a test
covering both destinations passes on the unmodified code, so there is no defect
to fix here. The two destination tests pin it.

## Test-harness notes

- **Both forms are taller than the 600px test surface.** `AddResourceScreen` is
  one `SingleChildScrollView`, which builds every child eagerly: `find.text`
  locates a widget below the fold, `tap` misses it, and — the trap —
  `dragUntilVisible` never scrolls, because its finder already evaluates
  non-empty. `tester.ensureVisible` is unreliable here too once a field has
  focus. Set a tall `tester.view.physicalSize` and the problem disappears.
- Never `pumpAndSettle` after tapping Submit on either screen: both buttons
  hold an indefinite `CircularProgressIndicator` while saving.
- The public-survey submit path pushes `UserInformationScreen` and **awaits**
  it, so a test has to dismiss that route (`tester.pageBack()`) before the POST
  is attempted — which is also how a respondent who declines the profile form
  gets there.
- `RadioListTile` in the question card is typed `RadioListTile<ExamChoice?>`;
  `find.byType(RadioListTile<Object?>)` matches nothing.

## For the integrator

**Two `.arb` keys are needed** (not added here, per the lane rules). Both back
`AddResourceScreen`, which today surfaces raw English literals returned by the
repository (`'Resource title already exists'`, `'Resource not found'`) straight
into the title field's `errorText`:

| key | English value | Kotlin source |
|-----|---------------|---------------|
| `resourceTitleAlreadyExists` | `A resource with this title already exists` | `strings.xml` `resource_title_already_exists` |
| `failedToUpdateResource` | `Failed to update resource` | `strings.xml` `failed_to_update_resource` |

With those in place the remaining Kotlin behaviour is small: show
`resourceTitleAlreadyExists` in `tlTitle.error` for *any* create failure
(`AddResourceActivity.kt:204-207`), and show `failedToUpdateResource` as a
snackbar on the edit path rather than on the title field
(`AddResourceActivity.kt:164-167`).

**No schema change was needed and none was made** (still v44), and no file
under `lib/data/local/` was touched.

## Gaps found and deliberately left (reported, not fixed)

Both audits ran at `parity-auditor`/max. What they turned up beyond the seven
fixes, worth its own phase:

- **A locally created resource never reaches CouchDB, and the next sync deletes
  it.** There is no `resources_uploader.dart` and no outbox type for it, where
  `UploadConfigs.getResourcesConfig` POSTs rows with `_rev IS NULL` and
  `markResourceUploaded` stamps the id back. Meanwhile `MyLibraryDao.deleteNotIn`
  has neither of the Kotlin prune's two guards (`_rev IS NOT NULL AND _rev != ''`
  and `isPrivate = 0`), so the row is destroyed on the next resources sync. The
  whole add-resource feature currently ends at the local row.
- **The picked file is never copied.** `ResourcesRepositoryImpl` copies the
  source into `ole/<id>/<filename>` and stores the bare filename;
  `saveLocalResource` stores the picker's absolute path and writes nothing. The
  viewer then treats the row as a stale offline flag and calls
  `markNotDownloaded`, which nulls the only pointer the user had. Related:
  nothing checks that the file exists, where the Kotlin fails the save outright.
- `markResourceAdded` and `syncTeamActivities` are not called after a save.
- The `teamId` route parameter has no pusher, so private team-resource creation
  is unreachable — `team_resources_screen` only links an existing resource.
- Editing is not ownership-gated: `ResourcesAdapter` shows the edit menu only on
  a long-press of a row whose `addedBy` is the current user, and never for a
  guest; the port puts an unconditional edit icon on every resource detail.
- The add-resource button has no guest gate (`guestDialog`); adding one means
  the screen must start watching `sessionProvider`, which is why it is not in
  this phase.
- `AddResourceActivity`'s on-focus-loss duplicate-title check has no counterpart
  — `resourceTitleExists` is ported with **no caller at all** — and the
  clear-on-focus-gain half is missing too. Blocked on `resourceTitleAlreadyExists`.
- No exit-confirmation dialog on the add-resource form (Kotlin confirms on Up,
  and deliberately does *not* on Cancel). The port already ships this dialog for
  the examination screen.
- `spn_lang` and `spn_open_with` carry `android:enabled="false"` in
  `activity_add_resource.xml`, so the Kotlin can only ever write `""` for those
  two. The port makes all four freely selectable — an undocumented improvement.
  Unselected also writes SQL `NULL` where the Kotlin writes `""`.
- The year field has `maxLength="4"` and `inputType="number"` in the layout; the
  port sets only a soft `keyboardType`.
- **`hasOtherOption` and `ratingScale`/`scaleMax` are not ported at all.** A
  respondent cannot give the "Other" answer, and a rating-scale question renders
  as a 3-line text box instead of `1..scaleMax` buttons.
- A choice question whose type is neither `select` nor `selectMultiple` renders
  pickable radios but exports `""` — the screen stores the pick under `choices`
  and `_buildPublicAnswers` falls through to `value`. Kotlin renders nothing and
  blocks the respondent, which is a dead end rather than a quirk to copy, so
  this needs a decision rather than a translation.
- Public-survey answers are held in memory until Submit; `ExamTakingFragment`
  writes each one on every Back/Next/Submit and in `onDestroyView`.
- A repeated Submit after a failed post mints a **second** submission row
  (`createSurveyDraft` hashes a fresh `DateTime.now()`); `PublicSurveyActivity`
  guards with `if (uploading) return`.
- A zero-question survey is submittable; Kotlin shows *No questions available*
  and hides the button. Needs an `.arb` key.
- `SurveyMapper.fromDoc` requires `type == 'surveys'`, which
  `StepExam.insertCourseStepsExams` does not — a public survey document without
  that key renders in the Kotlin and fails to load in the port. (Pinned as
  current behaviour by one of the new tests, so a future fix has to update it.)
- Adjacent, and the most alarming of the lot: `SurveyMapper` puts
  `choices` through `JsonUtils.getStringList`, whose `e.toString()` turns
  `{"id":"water","text":"Water"}` into the Dart literal `{id: water, text: Water}`.
  `PublicSurveyScreen` sidesteps it by re-parsing the document itself, but
  `createSurveyDraft` copies the mangled strings into `submission_questions` and
  `take_survey_screen` renders them as checkbox labels. It is in
  `lib/data/local/`, so it is the integrator's to allocate.
