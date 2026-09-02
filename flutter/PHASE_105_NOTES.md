# Phase 105 — the achievement hints, a named `file_not_found`, and the first tests for `health_provider`

Three strands: the two `.arb` follow-ups Phase 103 left for the integrator, and
first tests for `providers/health_provider.dart` (572 lines, no coverage). The
tests found **eleven** defects, ten of them shipped and one inherited red test
in a neighbouring file. Every fix below was demonstrated failing against the
pre-fix code first, one at a time.

The headline finding is that **the port could not record a health examination
at all** — not "recorded it wrongly": the row it wrote was unreachable from the
screen that was supposed to show it, and the second one destroyed the patient's
health profile. The reason nobody noticed is worth as much as the fix, so it is
written up under *A test that seeds the database has stopped testing the app*.

## 1. The three achievement form hints

`fragment_edit_achievement.xml` hints its three free-text fields with
`summary_of_achievemnets` ("Summary of achievements - Briefly summarize your
achievements", note the Kotlin key's typo), `my_goals_description` and
`my_purpose_description` — the guiding questions that tell the user what to
write. The port labelled them `noAchievementAdded` (the port of
`no_achievement_added`, a *different* string that `AchievementFragment` uses as
the read-only view's empty state) plus bare `myGoals`/`myPurpose`. Three new
`app_en.arb` keys, spelled correctly, and `edit_achievement_screen` wired to
them. `achievements_screen`'s uses of `noAchievementAdded`/`noGoalAdded`/
`noPurposeAdded` are correct as they stand — `setupAchievementHeader`'s
`ifBlank` fallbacks — and are untouched.

## 2. `fileNotFound` names the file

Kotlin's `file_not_found` is `File not found: %s` and
`EditAchievementFragment:234` passes the filename. The port's key was a bare
"File not found locally". It is now an ICU message with a `{fileName}`
placeholder, passed at all three call sites (the CV/resume toast and the
resource viewer's two text renderers, which have `resource.filename`).

`flutter gen-l10n` is happy with the five locale files that still carry the
placeholder-less translation — it emits `String fileNotFound(String fileName)`
with the argument unused — so nothing there needed touching, per this round's
constraint. The translation pass will pick the placeholder up.

## 3. `health_provider.dart` — the defects

### The port could not record an examination

`getPatientHealthRecords` lists a patient's examinations with
`getByProfileId(health.userKey)`. That is Kotlin's data model: the patient has a
**profile row** (`pojo`, whose `_id` is the patient's id and whose encrypted
`data` is the `MyHealth` blob), it mints a `userKey`, and every examination
points back at it through `profileId`. `HealthExaminationActivity.saveData`
writes both halves — `createPojo()` for the profile row, then the examination
with `profileId`/`creatorId = health.userKey`.

`ExaminationNotifier.save` wrote neither. It called `createExamination` with no
`profileId` and with the **patient's** id in `userId`, and never wrote a profile
row at all. Consequences, in the order a clinic would meet them:

1. **The first examination was invisible.** `getByProfileId(null)` matches
   nothing, so the record vanished the moment it was taken. No error, no log.
2. **The second examination overwrote the first.** `_loadData`'s
   `else if (_userId != null)` branch loaded `getByIdOrUserId(_userId)` — the
   *profile* row in Kotlin's model, which the port's own examinations matched
   because of the `userId` above — into `state.examination`. `save` then took
   the update branch. Kotlin keeps that row in a separate `pojo` and loads
   `examination` **only** when an examination id was passed
   (`loadData(userId, examinationId)`).
3. **Once a profile row existed, saving an examination destroyed it.** Same
   branch: the row `save` updated was the patient's profile, so its `data`
   column — emergency contact, special needs, and the `userKey` every
   examination is found by — was replaced with the examination blob.
4. **Two rows with the patient's `userId` made the lookup throw.**
   `getByIdOrUserId` ends in `getSingleOrNull`, so `Bad state: Too many
   elements` came out of `getPatientHealthRecords`, `getHealthProfile` and
   `saveHealthProfile` alike. `PatientDetailNotifier.selectPatient` catches and
   swallows it, so the screen simply stopped updating.

`save` now mirrors `saveData`: resolve the patient, load or `initHealth()` the
profile, stamp `lastExamination`, write the profile row, and give the
examination `profileId`/`creatorId = health.userKey` plus `gender`,
`age` (`TimeUtils.getAge(dob)`), `planetCode`, `isSelfExamination`
(`currentUser?._id == pojo?._id`, null equality included) and, inside the
encrypted blob, `createdBy` — the examiner. `createExamination`'s `userId`
is now optional and defaults to the row's own id, which is both what Kotlin
writes (`_id` and `userId` are one `generateIv()`) and the invariant
`_docToCompanion` maintains for every synced row (`userId: doc['_id']`).

*Failing-first:* with `_loadData` reverted, "two examinations do not break the
patient lookup" reports 0 examinations; with the `profileId`/attribution
stamping reverted, four tests fail and the record cannot be found at all; with
`userId: patientId` restored, the same test dies on `Bad state: Too many
elements`.

### The examiner was read off the wrong field

`creatorId` is **not** the examiner: `saveData` sets it to the patient's
`health.userKey`, the same value as `profileId`. The examiner is
`getString("createdBy", encrypted)` — inside the record's encrypted `data` —
which `HealthExaminationAdapter.submitExaminations` decrypts off the UI thread
before it submits the list. `my_health_screen`'s card and detail dialog read
`exam.creatorId`, which was harmless only because nothing ever set it: with
Kotlin's value in there, every card would have named a cipher key, and where the
key equalled the patient id every provider-taken examination would have read as
a self-examination.

`getPatientHealthRecords` already decrypts every row to collect creator ids, so
it now keeps `createdByOf` (examination id → `createdBy`) on `HealthRecord`,
precomputed exactly as Kotlin precomputes it, and the two widgets take it as a
parameter. *Failing-first:* with the card reading the column, the new
`my_health_screen` test cannot find `provider-1` on screen at all.

### Both editors edited the wrong person

`MyHealthFragment` passes the **selected patient** to `AddHealthActivity` and
`HealthExaminationActivity` as a `"userId"` intent extra, and `AddHealthActivity`
loads its form through `loadHealthData(userId)`. The port's screens took no
patient at all: both resolved `sessionProvider` and used the signed-in user, and
`healthDataProvider` was session-scoped so it *could* not describe anyone else.
A health provider who picked a patient and tapped either button was therefore
reading — and writing — their own record.

`healthDataProvider` is now `FutureProvider.family<HealthData?, String>`,
`AddHealthScreen` and `AddExaminationScreen` take a `userId`, the two routes
carry it as a query parameter, and `my_health_screen` passes
`patientIdOf(detail.user)`. Where the parameter is absent the fallback is
`patientIdOf(session)` rather than `session.id`: those two differ for a
locally-registered member, and mixing them would file one person's records under
two profiles.

This one is **not** evidenced by a failing test, and should not be reported as
if it were: the pre-fix screens have no parameter to pass, so the test would not
compile. It is established by reading the Kotlin and by the fact that no code
path could reach another patient. The `healthDataProvider` family has coverage
in the new file ("reads the patient it is keyed by, not the signed-in user").

Two smaller things fell out of the same read. `add_health_screen`'s
`ref.invalidate(healthDataProvider)` had a comment saying it refreshed
`MyHealthScreen` and did nothing of the sort — nothing watches that provider;
the screen renders `patientDetailProvider` — so a saved profile stayed
invisible. It now `refresh()`es the patient detail, which is what
`MyHealthFragment.onResume` does. And the FAB was an either/or: the patient
picker for a health provider, "Add health record" for everyone else. The layout
carries **both** buttons and `setupButtons` hides only `btnnewPatient` from a
non-provider, so the port left the health role — whose whole purpose is
recording other people's examinations — with no way to record one.

### The edit form never prefilled, and its edits were dropped

`HealthExaminationActivity` awaits `viewModel.state.first { !it.isLoading }`
before `initExamination()` and keeps Save disabled until it arrives. The port
read `ref.read(examinationNotifierProvider(params))` on the line *after* the one
that creates the notifier, so it always read the `isLoading: true` state with
nothing in it: editing an examination opened a blank form, and saving that form
wrote a new record. `ExaminationNotifier` now exposes a `loaded` future and the
screen awaits it, then prefills, then enables Save.

Behind that sat a second bug on the same path. Both the decrypt and the
re-encrypt used `exam.userId ?? _userId` as the crypto owner, and a synced
examination's `userId` is the *document's own `_id`* — so the user lookup
missed, the decrypt returned null (a blank form), the encrypt returned null,
and `updateExamination`'s `data ?? existing.data` kept the old ciphertext. The
edited notes were discarded in silence. Kotlin passes the patient both ways
(`getEncryptedDataAsJson(user)`, `encrypt(json, user.key, user.iv)`), so the
owner is now `_userId`. *Failing-first:* "an edit re-encrypts against the
patient, not the row id" fails on the prefill assertion first, which is the
decrypt half of the same line.

### `UserDao.getById` lost every locally-registered member

Kotlin's is `SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1`. The
port's matched `id` only — and `patientIdOf` deliberately hands over the
CouchDB id (`_id ?: id`, because health documents are keyed by it). For a member
created by `become_member_screen` those differ for good: the row keeps its local
`'<millis>'` id and gains a `couchId` when the upload lands. So
`getPatientById(patientIdOf(user))` returned null, `selectPatient` reset to the
empty state, and the member's own health screen said "health record not
available" forever. Now `id.equals(id) | couchId.equals(id)` with `limit(1)` —
the `LIMIT 1` matters for the same reason Kotlin has it, and keeps
`getSingleOrNull` from throwing when the two halves match different rows.

`ensureSecurityKeys` needed the other half of that: it resolved the row with
`getById` and then wrote back with `WHERE id = <the argument>`, so for such a
member nothing was written, the re-read still had no key, and `encryptData`
returned null — every examination recorded for them stored a null blob.
`ensureUserSecurityKeys` upserts the entity it resolved; this now keys the write
on `user.id`.

**This is the one change outside the health domain** (`data/local/app_database.dart`,
two DAO methods, no table or schema change). The integrator may want a second
pair of eyes on the blast radius: every `getById` caller gains the `_id`
fallback, which is Kotlin's behaviour everywhere — there is only one `getById`
in its `UserDao`.

### The smaller five

* **No re-entrancy guard on save.** `saveExamination` opens with
  `if (_isSaving.value) return`; without it the create branch ran twice —
  `state.examination` is still null the second time — and one form wrote two
  examinations. Note what makes this defect *look* absent: two unguarded saves
  each mint their own `initHealth` key, so the profile row ends up naming one of
  them and `getByProfileId` reports a single examination while the database
  holds two. The test counts rows, not record entries; asserted the other way it
  passes on the broken code.
* **A stale error outlived its retry.** `error ?? this.error` cannot clear, so
  the screen went on reporting a failure the retry had fixed. `copyWith` takes a
  `clearError` flag and `save` sets it.
* **`HealthQueue.queuePending` read `sessionProvider` with `valueOrNull`** on a
  path where nothing watches it, so every queued outbox row carried a null user.
  Awaited now, inside the caller's `try` — Phase 100's rule.
* **Two request races.** `HealthViewModel` cancels `searchJob` and
  `selectPatientJob`; Riverpod has no job to cancel, so a slow early query
  landed *after* a fast later one. Typing "ali" quickly left the list showing
  the matches for "a", and two quick taps in the picker could leave the screen
  on the first patient. Both notifiers now carry a generation counter and a
  superseded request publishes nothing.

### Inherited red: `public_survey_screen_test`

Five tests in that file were **already failing at this branch's head** (verified
by stashing this phase's changes and regenerating l10n). Three were collateral
from Phase 103's own fix: it made a cancelled profile step send nothing, and the
tests' `answerAndSubmit` helper reaches the profile step and calls
`pageBack()` — a cancel. They now complete the step (year of birth + Save),
which is what a respondent does.

The other two were real. One is the defect Phase 103's own test comment
describes and left unfixed: `public_survey_screen` chose between the resources
list and the login screen on `ref.read(sessionProvider).valueOrNull`, on a
screen that never watches it, so every signed-in respondent was sent to log in.
Awaited now — the Phase 100 shape for the fourth time in this port. The other
was a test-side ordering trap worth knowing: **the profile step shows its own
snackbar, so the survey screen's is queued behind it** and cannot appear until
that one's four seconds are up. A test asserting the second message has to pump
past the first. (It also means the delivered-path assertion matches the profile
screen's snackbar, since both use `thankYouForTakingSurvey`.)

One formatting fix travels with this: `dart format` (Dart 3.12.2, the version
`flutter.yml` pins) reformats `test/repository/outbox_repository_test.dart`,
so the gate's `--set-exit-if-changed` step was red on the branch head.

## A test that seeds the database has stopped testing the app

`my_health_screen_test`'s `seedHealthRecord` writes examination rows with a raw
drift upsert, and its comment says why:

> Give two rows the patient's `userId` and `getByIdOrUserId`'s
> `getSingleOrNull` throws, which is why this writes rows directly rather than
> looping `createExamination`.

Phase 95 met the defect, wrote the workaround into the fixture, and moved on —
the same shape as Phase 103's achievements uploader, whose tests hand-patched a
`couchId` the app never produced and whose one test of the empty case
*codified* the bug as intended behaviour. The lesson has now cost two phases:
**when a fixture cannot be built through the app's own write path, the reason is
a bug report.** The new tests drive the real repository against an in-memory
database for exactly this reason; a mocked repository would have recorded the
same wrong calls happily.

## Notes for the next person testing here

* `ExaminationNotifier` and the two patient notifiers start work from their
  constructors. `loaded` covers the examination one; for
  `PatientDetailNotifier` there is no such handle, so a test either spins the
  event loop until the state settles (`waitUntil` in the new file) or passes
  **no session**, which makes `loadInitialPatient` a no-op and leaves the
  test's own `selectPatient` calls the only ones in flight. Two tests need
  that: the constructor's selection otherwise supersedes theirs and their
  awaited call returns having published nothing.
* `getPatientHealthRecords(userId, user)` decrypts with the `user` **you pass**.
  A row captured before the first write has no key/iv — those are minted by
  `ensureSecurityKeys` inside the first `encryptData` — so re-read the patient
  first. `recordFor` in the new file does.
* The gated repository in the new file lets a test release queries **by
  argument**, not in call order, which is what makes "the later request answers
  first" expressible. Releasing more calls than were made must be a no-op:
  a superseded request returns before its second query, so a strict wait
  deadlocks on the fixed code and the failure looks like a hang.
* `seedHealthRecord` gives the profile row a `profileId`, so it renders as an
  extra self-examination card. The app's own profile row has none.

## For the integrator

* **New `app_en.arb` keys**: `summaryOfAchievements`, `myGoalsDescription`,
  `myPurposeDescription`. **Changed**: `fileNotFound` now takes `{fileName}`
  (with a `placeholders` block). The five locale files are untouched, so those
  four strings fall back to English / drop the filename until the translation
  pass.
* **No schema change.** Drift stays at v44; no table or column was touched.
  `data/local/app_database.dart` is edited, but only two `UserDao` query
  bodies (`getById`, `ensureSecurityKeys`) — flagged above because it is the
  one change with reach beyond this lane.
* Phase 103's third `.arb` follow-up — localising `memberLevels`, since
  `R.array.level` is translated in all five locales and the port sends the
  displayed label — is **not** done here: it needs the `values-*` translations,
  which this round may not touch.
* Still open in this domain, found and deliberately left: the examination form's
  `conditions` map is sent whole where Kotlin sends only the checkboxes the user
  toggled in this session (`mapConditions` starts empty, so a Kotlin re-save
  drops the conditions it did not touch — a quirk the port improves on
  silently); `_ExaminationCard`'s "self" test compares `createdBy` against the
  patient's local `id` while `createdBy` is a CouchDB `_id`, which is what
  Kotlin compares too and is wrong in both for a member whose ids differ; and
  `my_health_screen`'s team-detail-style Finances/Edit gating is untouched.
