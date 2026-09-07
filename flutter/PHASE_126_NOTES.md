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

See the merged section below for the file-by-file record and the failing-first
evidence.

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
