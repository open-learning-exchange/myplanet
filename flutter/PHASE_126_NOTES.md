# Phase 126 — harvest of the 18-commit master backlog (Lane B)

`origin/master` was **18 commits ahead** of the migration branch (merge-base
`d06a82b`, master head `9ff1273`, version 0.69.19 → 0.69.36) — the first harvest
backlog in several rounds worth triaging rather than eyeballing. Merged in at
`575df73`; master never touches `flutter/`, so the merge was clean and codegen
needed no re-run for a Drift change. The gate was green on the merge before any
port work: format 0 changed, analyze clean, 2171 tests.

## Triage verdict, per commit

`harvest-triage` produced the first pass; **every Follow below was then re-read
against the Kotlin by hand**, because Phase 95 read a 26-commit batch as "all
refactors" and Phase 96 found a real gap in the same batch, and because a
citation is not a reading (Phases 106/110/113/117).

| Commit | Subject | Verdict |
|---|---|---|
| `9255eac` | sync: smoother courses resources uploading | **Follow** — new sync step, data-loss class |
| `27c0470` | all: smoother models document origining | **Follow** — wire-format change on every uploaded document |
| `aca425a` | teams: smoother submissions repository stamping | **Follow — Lane A's files**, reported not edited |
| `ae20602` | resources: smoother viewer playback view modelling | **Follow** — behaviour the port never had |
| `fdf474d` | life: smoother repository layout view modelling | **No counterpart needed** — see below, with a guard test |
| `9ff1273` | courses: smoother progress batch deleting | No port impact (per-id loop → one batched transaction, same deletes) — **but see the pointed-at gap below** |
| `0b381bd` | teams: smoother courses achievements dictionary view modelling | No port impact — MVVM extraction into new `*ViewModel` classes, same repository calls |
| `8cf9660` | sync: smoother repositories json utils documents mapping | No port impact — extracts the repeated `_design`-skip/doc-unwrap loop into `JsonArray.toSyncDocuments()`; the port's walks already have that shape |
| `2cbeb8c`, `0a68450` | all: smoother file utils url resolving | No port impact — `2cbeb8c` rewrites Android `ContentResolver`/`MediaStore` URI helpers the port replaces with `file_picker`; `0a68450` parses URL segments once instead of three times, byte-identical output, and `core/files/resource_files.dart` already matches |
| `57ac002` | sync: smoother submit photos dao batch updating | No port impact — Room per-row loop → one batched `@Update` |
| `d230a9f` | teams: smoother surveys details landscaping | No port impact — `layout-land/*.xml` only; the port lays out responsively with no orientation resource set |
| `b9ff7cc` | resources: smoother list filter state preserving | No port impact — `onSaveInstanceState` Bundle round-trip for Fragment recreation; the port's filters are container-scoped Riverpod providers, so the problem being patched does not exist here |
| `f172c79` | all: smoother notifications task date caching | No port impact — memoises a pure function per notification id |
| `9dff477` | sync: smoother url utils base64 handling | No port impact — `android.util.Base64` → `java.util.Base64` for JVM testability, `NO_WRAP` output unchanged |
| `348c45d` | all: smoother version utils comparing | No port impact — `compareVersions` avoids an allocation, same result; the port's is pinned by `version_parity_test.dart` |
| `6c0db18` | enterprises: smoother reports dao filtering | No port impact — moves the archived filter and `createdDate DESC` sort into the `@Query`; `watchReports` already does both in SQL |
| `c0ae729` | all: smoother importing | No port impact — unused-import cleanup plus a version bump |

Version parity: the Kotlin app is now 0.69.36 against the port's 0.69.18. Same
minor, so `version_parity_test.dart` passes — it tolerates patch lag by design
(`automerge.yml` bumps the Kotlin version on every merge, and an exact-equality
rule turns every PR run red). **Not tightened, and not bumped**: a bump is a
release decision, not a harvest one.

## What was ported

### `9255eac` — the shelf is pushed *before* the pull phase

`SyncManager.startFullSync` gained a `pushCurrentUserShelf()` step ahead of the
parallel table set: `userRepository.getUserModel()?.let { uploadShelfData(it) }`,
wrapped so an `Exception` is logged and the sync continues
(`CancellationException` rethrown).

The port had `ShelfRepository.upload` and three callers — all of them *inline*,
fired by the add/remove UI (`ResourceShelfActions.setMemberships`,
`CourseDetailScreen._setMembership`, the events one). None is retried and
nothing rescans, so a shelf change made while offline was never pushed at all,
and the next sync pulled the server's older shelf document over it.
`DashboardSyncArea.shelf` is the *pull* and correctly runs last; this is the
opposite end of the pass.

`DashboardSyncNotifier.pushCurrentUserShelf()` now runs first in `syncAll`.
The session is `await ref.read(sessionProvider.future)` rather than
`.valueOrNull` — Kotlin resolves its own user before deciding, and the standing
rule from four independent instances (Phases 100/102/104/106) is that a provider
a caller reads but never watches is null; the `await` is inside the `try`
because a future can reject where `valueOrNull` could not.

**Failing-first.** `test/providers/dashboard_sync_provider_test.dart` gains four
tests and drives `syncAll` for the first time (a bare `planetApiProvider` mock
keeps the sixteen pulls off the network — each throws on its first call and is
recorded as errored, which is fine because what is under test runs before them).
With the method present but the call site removed:

```
00:00 +4 -1: shelf push before the pull phase syncAll pushes the local shelf before the first table pull [E]
  Expected: [Instance of 'ServerConfig', 'user-ada', 'org.couchdb.user:ada']
    Actual: <null>
  syncAll never pushed the shelf
```

The ordering assertion is not "the push happened" but "every area was still
`waiting` when it happened", captured inside the mock's `thenAnswer`.

### `ae20602` — media resume position and playback speed

Kotlin added per-resource playback-position persistence and a global
playback-speed menu: `SharedPrefManager.getMediaPlaybackPosition`/`Speed`,
`ResourceViewerViewModel.calculateEffectivePlaybackPosition`, and hooks in
`ResourceViewerFragment` on `onIsPlayingChanged`, `STATE_ENDED`, `onPause` and
`onDestroyView`. The port had none of it.

New `lib/ui/viewer/media_playback.dart` carries the policy as pure functions,
because the players want a platform view no widget test can serve while the
arithmetic is where the behaviour lives:

* `effectivePlaybackPosition` — the near-end reset. Two quirks the Kotlin has
  that a naive port loses: the threshold test is `<` not `<=`, so exactly
  2000 ms from the end is *kept*; and it is skipped entirely when
  `duration <= 0`, so a player that has not resolved a length yet keeps its real
  position rather than being zeroed.
* `shouldPersistPlaybackPosition` — the write throttle, including the
  `effectivePosition == 0` clause that lets a finished resource clear its entry
  even though the previous write was moments earlier.
* `playbackSpeedIndex` — `abs(it - current) < 0.05`, falling back to index 1.
* `MediaPlaybackStore` over `PlanetPrefs`, using the **Kotlin key names**
  (`media_progress_<key>`, `media_playback_speed`) so a device that has run both
  apps reads the same values, and deleting rather than storing a zero.

`_MediaProgressTracker` installs the three hooks on both the video and the audio
player (Kotlin installs the same set on each). `dispose` saves before releasing
the controller, matching `onDestroyView`'s order. The speed lives in a
`ValueNotifier` the toolbar action and the active player share, so a chosen speed
applies to what is already playing, as `exoPlayer?.setPlaybackSpeed` does.

One harness trap avoided: the store is read **only** for a video or audio
resource, not in `initState`. `planetPrefsProvider` is deliberately
un-overridden in the widget-test harness (the Phase 75 trap), so an eager read
would have thrown out of every existing viewer test for a text or image
resource.

**Failing-first.** Four of the five new widget tests in
`test/ui/viewer/resource_viewer_screen_test.dart` fail on the pre-fix screen
(`Found 0 widgets with text "1.0x"`, and the three that tap it fail on a
zero-hit finder); 19 unit tests cover the policy and the store.

While writing these: `_getResourceType` routes video and audio on
`mediaType`/`resourceType` only, never on the extension, so a row whose
`filename` is `lesson.mp4` with no media type falls through to
`ResourceType.text`. Pre-existing, out of this commit's scope, **not changed** —
recorded because the test had to work around it.

### `27c0470` — `"app": "myplanet"` on every uploaded document

Kotlin extracted a `JsonObject.addDocumentOrigin(androidId)` helper stamping
`androidId` **and a new `app = "myplanet"`**, then called it at every
locally-authored-document serializer. Two classes of change, and the difference
decides what the port needs:

* **Dedup sites**, where the call replaced an existing
  `addProperty("androidId", …)` — so the only new field on the wire is `app`:
  `ApkLog`, `CourseActivity`, `MyLibrary`, `MyPlanet` (×2), `NewsLog`,
  `Personal`, `Rating`, `SearchActivity`, `UserEntity`,
  `ActivitiesRepositoryImpl.serializeLoginActivities`/`serializeResourceActivities`,
  `SubmissionsRepositoryImpl.getExamUploadPayload`/`serializeSubmission`,
  `TeamsRepositoryImpl.serializeTeamActivities`, `UserRepositoryImpl.createMember`,
  `UploadManager.createImage`.
* **New-stamp sites**, a pure addition, so **both** fields are new — and these
  documents get *only* those two, no `deviceName`/`customDeviceName`:
  `CourseProgress`, `Feedback`, `Meetup`, `StepExam`, `SubmitPhotos`, `TeamTask`,
  `VoicesRepositoryImpl.serializeNews`.

Nothing in the port wrote an `app` field at all. The port's analogue of the
helper is `DeviceIdentity.documentFields`, so `documentFields` is now
`{...originFields, deviceName, customDeviceName}` with a new `originFields`
getter (`androidId` + `app`) — the direct analogue of `addDocumentOrigin`, which
is what the new-stamp uploaders spread, since spreading the full
`documentFields` there would send two fields Kotlin does not.

Ported: `device_identity.dart` (the `originFields`/`documentFields` split),
`DeviceTelemetry` in `activities_uploader.dart` (the port's second, parallel
identity mechanism, covering the three activity documents), and
`course_progress_uploader.dart`, `feedback_uploader.dart`,
`events_uploader.dart`, `team_tasks_uploader.dart`, `voices_uploader.dart` for
the new-stamp sites — each now takes a `DeviceIdentitySource` and reads it once
per `queuePending`, guarded on an empty pending list so a pass with nothing to
send makes no platform-channel call, as `personals_uploader.dart` does.
`myplanet_activities_uploader.dart` gets `app` at both of `MyPlanet.kt`'s two
sites (each usage *row* is stamped; the `usages` container is not, and carries
no document-level `androidId` either).

`user_mapper.dart` and `user_repository.dart` gain `app` on the account-creation
branch **only**. Their pre-existing divergence — the port omits
`androidId`/`uniqueAndroidId`/`customDeviceName` there, because neither builder
holds a device-identity seam and Planet ignores the three on account creation —
is deliberately left as it was; `app` needs no seam, so adding it widens
nothing.

`submit_photos_uploader.dart` needed no change: it already spreads
`documentFields`, so it picked `app` up centrally (and it over-sends both device
names relative to Kotlin, which is the pre-existing item recorded below).

Two paths checked and confirmed to need nothing. The **public-survey** POST is
not a CouchDB document: `SurveysRepositoryImpl.submitPublicSurveyTo` builds
`{answers, user}` for `/api/public/surveys/…/submissions` and calls no
`addDocumentOrigin`, so `buildPublicSubmissionBody` is correct unstamped. And
the top-level **survey submission** document is covered centrally, because
`submissions_uploader.dart` spreads `documentFields` over
`SubmissionsRepository.serialize`'s output.

A hazard the change exposed, worth knowing in production terms: adding the
identity read to `FeedbackUploader` turned `feedback_screen_test.dart` red on
all three of its outbox assertions, because that harness uses the real
`feedbackUploaderProvider`, `deviceIdentitySourceProvider` reaches the platform
channel, `read()` threw, and `FeedbackCreateNotifier.submit`'s `catch` swallowed
it — so a *saved* feedback reported as failed and unqueued. Fixed in the test
with `FixedDeviceIdentitySource`, matching `take_exam_screen_test`. **The same
hazard already existed at the eight pre-existing `documentFields` sites**
(personals, submissions, ratings, teams, search activity, team log, submit
photos), so the shape was kept rather than a swallow invented at one of
thirteen. In production `PlatformDeviceIdentitySource.read()` falls back to the
primed preference cache and only rethrows when both values are empty, so this
is a narrow window — but if it should be swallowed, it should be swallowed at
all thirteen, which is a decision for a slice and not for a harvest lane.

**Failing-first** per behaviour, in `device_identity_test.dart`,
`user_mapper_test.dart`, `user_repository_test.dart` and each touched uploader's
test; 41 net new tests across the phase, 2212 passing.

### The search-activity device id, found while porting the stamp

`SearchActivity.serialize` is the **one** `addDocumentOrigin` call site in the
whole Kotlin tree that passes the argument explicitly:
`addDocumentOrigin(VersionUtils.getAndroidId(context))`, the bare
`Settings.Secure.ANDROID_ID`. Every other site takes the parameter's default,
`NetworkUtils.getUniqueIdentifier()`, which is `androidId + "_" + Build.ID`.
Extracting the helper is what made that visible — before `27c0470` the
difference was two unrelated-looking lines in two files.

The port's `search_activity_uploader.dart` spread `documentFields`, whose
`androidId` is the composite (`device_stats.dart` documents that), so **search
activities reported a different device id than the app they replace** — one
handset appearing to Planet as two devices depending on which document it was
aggregating. `serialize` now takes the bare id from `DeviceStats.androidId()`
and overrides that one field, leaving both device names on the identity as
Kotlin's own `addProperty` calls right after `addDocumentOrigin` do.

The existing test asserted `'android-id_build-id'` — it was pinning the
divergence rather than the Kotlin, so that expectation is corrected in place
with a comment saying why, which is the only honest way to change a green
assertion. The new test fails on the pre-fix code with
`Expected: 'android-id' / Actual: 'android-id_build-id'`.

Deliberately **not** changed on the same argument: `submit_photos_uploader.dart`
over-sends both device names where Kotlin's `SubmitPhotos.serialize` stamps
origin only. That one is a *superset* of the Kotlin document rather than a
different value for the same key, so nothing on the server can misread it.

## `fdf474d` — why My Life needs no port

Kotlin added `normalizeUserId` (null, blank and `"--"` all collapse to "no
user") and tightened the DAO predicate from
`(:userId IS NULL OR userId IS NULL OR userId = :userId)` to an exact match for
a real id plus a null/empty/`"--"` bucket for no user.

The port was **already at the post-commit behaviour and cannot reach the
pre-commit one**: `MyLifeDao.watchForUser` has always been
`row.userId.equals(userId)`, and it cannot produce the placeholders either —
`"--"` is a Kotlin *preferences* sentinel
(`sharedPrefManager.getUserId().ifEmpty { "--" }`, the same one
`team_courses_screen.dart` already documents), while a guest here carries a real
`guest_<username>` row id. The `distinctBy { dedupKey() }` dedup the commit
keeps has nothing to dedup here: `seedIfEmpty` is count-guarded per user and the
ids are deterministic `<userId>:<feature>`. The `sortedBy { weight }` it adds,
the port's DAO already does in SQL.

Rather than a no-op change, `test/repository/life_repository_test.dart` gains a
test that seeds `'--'` and `''` rows and proves they stay out of a signed-in
user's list — the reader's half of the reachability question, pinned so a future
loosening of the predicate fails.

One divergence found and deliberately left: the port has no analogue of Kotlin's
`myLifeCache_<userId>` preferences cache, which `getMyLifeForDashboard` falls
back to when the database has no visible rows. Drift persists the rows, so the
fallback has nothing to rescue. Reported, not ported.

## Reported, not fixed

### To Lane A — `aca425a`, `lib/repository/submissions_repository.dart`

Real behaviour changes, verified in the diff, all inside `SubmissionsRepositoryImpl`:

1. **`createExamSubmission` no longer drops the team when the lookup fails.**
   `persistedTeamId = teamId?.takeIf { it.isNotBlank() }` is written to
   `submission.teamId` unconditionally, and `teamObject` is built from
   `persistedTeamId` with `name`/`type` taken from the local team **if it
   resolved** (`team?.name`, `team?.type`). Previously the whole
   `teamObject`/`membershipDoc`/`user` block was gated on `team != null`, so a
   team document missing from the local cache silently produced a submission
   with no team association at all. Also: `type` is no longer defaulted to
   `"team"` — it is `team?.type`, nullable.
2. **The team lookup is now failure-tolerant** — a new `getTeamByIdOrNull`
   swallows `Exception` and rethrows `CancellationException`.
3. **`serializeSubmission` — the *survey* upload path — now emits a `team`
   object.** It previously emitted none, and the port's own doc comment says so
   ("`serializeSubmission` emits none"); that comment is now stale. Both paths
   go through a new `resolveTeamJson`, which falls back from `teamObject._id` to
   `submission.teamId`, back-fills a blank `name`/`type` from the local team,
   and **omits the key entirely** when it cannot resolve one rather than writing
   a null.

4. **One `27c0470` site lands here too: the nested `parent`.**
   `StepExam.serializeExam` is a **new-stamp** site, and two of its three
   Kotlin callers are `SubmissionsRepositoryImpl` embedding it as a
   submission's `parent` sub-object (`:778`, `:849`) — so after `27c0470` a
   submission's nested `parent` carries `androidId` and `app`, a quirk of the
   serializer being shared with the `exams`-database upload
   (`UploadConfigs.kt:192`). The port's counterpart is
   `SubmissionsRepository.examParentDocument`, which is in Lane A's file and
   was therefore not touched. It wants `DeviceIdentity.originFields` — the
   origin pair only, no device names — which means `examParentDocument` needs a
   `DeviceIdentity` passed in, since it is `static` and holds no seam. The
   top-level submission document already picked `app` up centrally through
   `submissions_uploader.dart`'s `documentFields`, so this is the nested object
   alone.

### To Lane C — `lib/l10n/`

`ae20602` added two strings to `values/strings.xml` **and to all five
`values-*/strings.xml`** — `playback_speed` ("Playback Speed") and
`playback_speed_format` (`%1$sx`). I added `playbackSpeed` and
`playbackSpeedValue` to `app_en.arb` only (with the placeholder declared in the
`@` block, `type: String`). The five human translations upstream ships are
exactly the recovery work Phases 114/118/121 established is strictly better than
machine translation — and `playback_speed_format` carries a placeholder, the
class Phase 121 found `tool/arb_from_strings_xml.dart` skips outright.

### Pre-existing gaps this batch pointed at

* **A locally created resource never leaves the handset.** The largest find of
  the round, and reachability-class: Kotlin's `UploadConfigs.getResourcesConfig`
  fetches `getPendingResourceUploads()`, serializes each with
  `MyLibrary.serialize`, POSTs to `resources` and adopts the returned id/rev.
  The port has **no `resources` outbox type and no equivalent path at all** —
  `add_resource_screen` (Phase 102, 33 tests) writes the `my_library` row,
  `saveLocalResource` marks it offline and adds it to the shelf, and the shelf
  push then uploads an id pointing at a document the server does not have.
  Every layer is green and tested; nothing carries the document. The three
  questions the guards encode answer badly here: the writer is the add-resource
  screen, the reader is the server, and no writer connects them.
  **No schema bump needed** — Kotlin's pending query is
  `SELECT * FROM my_library WHERE _rev IS NULL`, and `MyLibraryTable.rev` is
  already nullable, so a locally created row is identifiable exactly as Kotlin
  identifies it. This wants its own slice: an uploader, an outbox type, and the
  `markResourceUploaded` id/rev adoption.
* **`ApkLog.serialize`, `NewsLog.serialize` and `UploadManager.createImage` have
  no Dart counterpart** (crash/ANR logs, voice-view logging, and the profile
  image POST respectively). Noted rather than investigated; each is a separate
  question from this batch.
* **`CoursesRepositoryImpl.deleteCourseProgress`/`deleteCoursesProgress` has no
  Dart counterpart.** Surfaced by reading around `9ff1273`, whose own diff is
  only a batching change. `setShelfMembership(joined: false)` in the port flips
  membership and writes a removal-log entry; it never purges the course's local
  exam/submission/answer rows the way Kotlin does. Predates this commit range.
  Not ported — it wants its own slice, and it is a delete path, which is exactly
  where a hasty port does damage.
* **`submit_photos_uploader.dart` over-sends device fields.** Kotlin's
  `SubmitPhotos.serialize` stamps origin only (no `deviceName`/
  `customDeviceName`); the port spreads the full `documentFields`. Pre-existing,
  harmless, not changed in this phase.

## The second audit pass, and what it found in my own green code

Run per the standing rule (Phases 110/113/116/119 each found real defects this
way). It found nine things in code that was already committed, formatted,
analysed and passing. Seven are fixed; two are recorded below for the
integrator.

**The resume position was lost on the commonest way of leaving a video.**
Kotlin saves in `onPause` *and* `onDestroyView` — two save points. The port had
one and a half: `dispose`, plus a save on a pause *transition*. Backgrounding an
Android app produces no `isPlaying` transition — `video_player`'s platform side
has no lifecycle handling at all — so pressing Home and letting the OS reclaim
the process saved nothing and the video reopened at 0:00. `_MediaProgressTracker`
is now a `WidgetsBindingObserver` and saves on `paused`/`inactive`/`hidden`.
This is the finding that most justifies the pass: the feature worked in every
test and failed on the most ordinary user action there is.

**The ported feature was unreachable for audio, and I had recorded only half of
why.** The note above said `_getResourceType` routes on `mediaType` "never on
the extension" and called it out of scope. Both halves were wrong.
`ResourceOpener.resolveType` — the only production path from a resource list
into the Kotlin viewer — routes on the **extension** via
`Utilities.getMimeType`, and never consults `mediaType` or `resourceType` at
all. And the failing case is not a niche server row: the port's own
add-resource form offers `'Audio/Music/Book'` and `'Graphic/Pictures'`, so an
mp3 created *in this app* routed to the text viewer, where the playback
features — gated on the video/audio types — could not appear. Extension
routing now comes first, with the column checks kept after it as a deliberate
superset. Two tests, both failing pre-fix with `Found 0 widgets with text
"1.0x"`.

**`_lastSavedMs` was seeded on restore, and the comment four lines above said it
was not.** Kotlin leaves `lastSavedPositionMs` at `-1L` through a restore, so
the first save after resuming always lands; seeding it threw away a
resume-then-leave-quickly (stored 60000, watched to 61500, wrote nothing).
Bounded at 2 s of staleness so not data loss, but a divergence whose own comment
claimed the opposite. Seeding removed.

**Two claims of mine were false and are corrected.** The commit message and two
doc comments said the Kotlin preference key names let "a device that has run
both apps read the same values". That cannot happen three independent ways:
`shared_preferences` prefixes every key with `flutter.`, keeps them in its own
`FlutterSharedPreferences` store rather than the Kotlin app's
`Constants.PREFS_NAME` file, and encodes the speed as a double where Kotlin
writes a `putFloat`. Matching the names is still right — it is the convention
`_keyLanguage` and `_keyLastSync` already follow — but for legibility, not
interchange. And the `@playbackSpeedValue` description wrote the Kotlin format
as `%1$s x`; it is `%1$sx`.

**The shelf push reached one of Kotlin's two entry points.** `startFullSync` is
called from `SyncActivity` *and* `AutoSyncWorker`; the port's `syncAll` has one
caller, the sync centre. So the offline-then-app-closed case — precisely the one
the step exists for — was still unfixed. `background_entrypoint.dart`'s
`syncSteps` now opens with a `shelf_push` step, swallowing failure by returning
`true` as Kotlin logs and continues.

**Two smaller corrections.** The push now runs *before*
`recordSyncChallengeAction`, where Kotlin has it, which also resolves
`sessionProvider` before that method reads it with `.valueOrNull` — closing a
latent null for free. And the test comment claiming a bare mocktail mock
"throws on its first call" was wrong: mocktail returns **null** by default, and
what actually stops the pulls is the implicit-downcast `TypeError` that null
produces where a non-nullable `Future` is declared. Same outcome, wrong
mechanism, and the difference matters — a `void` or nullable-returning mocked
member would hand back null and let the code carry on past where the comment
promises it stops.

### From the audit, reported not fixed

* **`PathResourceViewerScreen` got neither half of `ae20602`.** Kotlin gates the
  speed menu and the position save on `type == VIDEO || AUDIO`, not on having a
  `resourceId` — `getMediaKey()` is `resourceId ?: filePath.orEmpty()` — so a
  personal note's `.mp3` resumes and honours the chosen speed in the Kotlin app.
  The port's equivalent screen has neither. `_MediaProgressTracker` is reusable
  as-is with the file path as the key; it is a second screen's worth of wiring,
  which is a slice rather than a harvest follow.
* **Two undocumented improvements in the shelf payload, now on the hot path.**
  `shelf_repository.dart` writes `'_id': shelfDocId` (the CouchDB id) where
  Kotlin's `getShelfData` writes the *local row* id, and omits `_rev` on a 404
  where Kotlin writes `_rev: ""`. Both make the port succeed where Kotlin fails
  — for a member registered on this device the two ids differ, so Kotlin PUTs a
  body whose `_id` disagrees with its URL. Neither is in
  `docs/kotlin-to-flutter-migration.md`'s deviations list, and this phase is
  what makes that matter: before it, `upload` fired only on an add/remove tap,
  and it now fires on every sync for every user. Worth folding into that
  document, which no lane owns this round.
* **`_MediaProgressTracker` has no direct coverage and cannot get any.** It is a
  private class inside the screen file, and in every widget test
  `VideoPlayerController.file(...).initialize()` rejects, so `attach` is never
  reached — which is how the two defects above got through green. The
  extraction this phase did stopped one layer short: a narrow interface over
  `position`/`duration`/`isPlaying`/`isBuffering`/`isCompleted` would make the
  wiring testable. Recorded as the next obvious step rather than done here,
  because it changes the shape of a screen two other lanes may be reading.

One pre-existing wrinkle this phase aggravates: `_initPlayer` assigns
`_controller` only inside `if (mounted)`, so a widget unmounted during
`initialize()` leaks the controller, and `attach` adds two awaits to that
window. Left alone — the fix belongs with the extraction above, not bolted on.

## Dependency drift

`flutter pub outdated` reports **103 packages** with a newer version available
(the "89 packages have newer versions incompatible with dependency constraints"
line the session hook prints counts a narrower set): 17 direct, 2 dev, 84
transitive. **No upgrade started.** What the integrator needs to decide with:

**Nothing is security-relevant.** No package has `isCurrentAffectedByAdvisory`.
Three flags short of that:

* **`build_daemon 4.1.3` is a *retracted* version** (4.1.6 available).
  Transitive under `build_runner`, which is itself constraint-blocked at 2.15.1,
  so it cannot be fixed by a bump of its own. A retracted version was pulled
  from pub by its author; worth resolving on those grounds even though nothing
  here misbehaves.
* **`sqlite3_flutter_libs`'s latest is `0.6.0+eol`** — the `+eol` is the author
  signalling end-of-life. This is what bundles SQLite under Drift, so it is the
  one item on this list that could become blocking rather than merely stale.
  Needs a look at what Drift recommends now.
* **`flutter_secure_storage_macos` and `js` are discontinued.** Both transitive
  and both irrelevant to an Android app (`js` is the legacy web-interop shim).

**Six direct dependencies are a major version behind** — this is the real work,
and `flutter_riverpod` is not a phase, it is several:

| Package | Current | Latest |
|---|---|---|
| `flutter_riverpod` / `riverpod` | 2.6.1 | 3.4.3 |
| `go_router` | 14.8.1 | 18.0.1 |
| `flutter_secure_storage` | 9.2.4 | 11.0.0 |
| `package_info_plus` | 8.3.1 | 10.2.1 |
| `file_picker` | 11.0.2 | 12.2.0 |
| `flutter_map` | 7.0.2 | 8.3.2 |

Riverpod 2 → 3 touches every provider in the port and every harness in
`test/support/`; four majors of `go_router` touch every route. Both deserve
their own phase with nothing else in it, and neither should share a round with
lanes editing screens. The remaining direct drift is patch/minor and safe to
take in one pass whenever someone wants it (`dio`, `drift`/`drift_dev`,
`video_player`, `pdfx`, `table_calendar`, `workmanager`, `mime`, `latlong2`);
`intl`, `meta` and `build_runner` are constraint-blocked by the Flutter SDK pin
and will move when 3.44.8 does.

29 transitive packages are a major version behind, all pulled by the six above
or by the SDK.
