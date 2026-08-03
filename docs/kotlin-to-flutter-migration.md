# Kotlin → Flutter/Dart migration

Tracking document for migrating myPlanet from the **Kotlin/Android** app in `app/` to a
**Flutter/Dart** app in `flutter/`.

## Status

**Phase 15 complete.** The Flutter app is *not* yet a replacement for the Kotlin app:
**14 of 28 UI packages** are ported.

- **Phase 1** — skeleton plus the server configuration → login → resources slice.
- **Phase 2** — dashboard shell (bottom-tab navigation) plus the courses list and detail.
- **Phase 3** — the first write-back path: shelf upload, so joining or leaving a course reaches
  the server.
- **Phase 4** — the calendar package and its dashboard destination.
- **Phase 5** — localized, persisted first-launch onboarding and router gating.
- **Phase 6** — offline user profile, editable account metadata, and dashboard destination.
- **Phase 7** — persisted system/light/dark appearance settings and safe server details.
- **Phase 8** — downloadable, SQLite-backed offline dictionary and search experience.
- **Phase 9** — reactive notifications, filters, unread badges, read actions, and deletion.
- **Phase 10** — personalized My life ordering/visibility and the references launcher.
- **Phase 11** — offline personal-item creation, editing, deletion, and pending-upload tracking.
- **Phase 12** — offline course ratings, comments, aggregates, edits, and upload tracking.
- **Phase 13** — durable write-back: an outbox replacing `RetryQueue`/`RetryQueueWorker`, drained
  on app resume, with personal-note upload as its first append-style path.
- **Phase 14** — offline submission creation, durable upload, list, question-aware answer review, PDF export, and detail metadata, backed by a paginated,
  reactive Drift cache with pull-to-refresh, status filters, progress reporting, and safe stale
  cleanup.
- **Phase 15** — offline meetups list/detail/create/edit, join/leave shelf state, CouchDB mapping,
  reactive Drift persistence, and durable outbox upload.

## Strategy

- **Coexistence, green at every commit.** The Flutter app lives in `flutter/` alongside the
  untouched Kotlin app. `app/` keeps building and shipping; its `build.yml` / `test.yml` workflows
  are unmodified. A separate `.github/workflows/flutter.yml` guards the port, path-filtered to
  `flutter/**` so neither pipeline slows the other down.
- **Port by vertical slice, not by layer.** Each slice carries a feature from UI to network to
  disk, so every increment is a runnable, testable app rather than an unusable pile of models.
- **The Kotlin stays the specification.** Every ported file names its Kotlin counterpart in a
  doc comment, and behaviour is replicated including quirks (see *Faithful quirks* below).
  Deviations are called out explicitly rather than silently improved.
- **Drop-and-resync, no data copy.** Same policy as the Realm → Room migration: local rows are a
  cache of CouchDB, so the Flutter app starts with an empty database and re-pulls from the
  server. There is no Room → Drift data migration path, and none is planned.

  **Except where the row is not a cache.** `AppDatabase._localAuthorityTables` exempts `outbox`,
  `my_personal`, `removed_log` and `my_life` from the drop: they hold un-pushed writes, private
  notes that may never have been uploaded, the "leave" records that keep the shelf merge from
  re-adding something, and the user's own ordering. No sync can give any of that back, so
  dropping it would silently discard work done offline. The exemption has a cost worth knowing:
  `createAll` will not *alter* a preserved table, so changing one of their shapes needs a
  hand-written migration step.

## What is ported

| Slice | Kotlin source | Flutter destination |
|---|---|---|
| Server configuration | `ConfigurationsRepositoryImpl.getMinApk`, `SyncActivity`, `ServerDialogExtensions` | `repository/configurations_repository.dart`, `ui/sync/server_config_screen.dart` |
| Login (online + offline) | `LoginSyncManager.login`, `UserRepositoryImpl.authenticateUser`, `LoginActivity` | `repository/user_repository.dart`, `ui/sync/login_screen.dart` |
| Resources list | `SyncManager` phase 2, `ResourcesRepositoryImpl`, `ResourcesFragment` | `repository/resources_repository.dart`, `ui/resources/resources_screen.dart` |
| PBKDF2 credential check | `utils/AndroidDecrypter.kt` (via `jpbkdf2`) | `core/crypto/pbkdf2.dart`, `core/crypto/android_decrypter.dart` |
| URL construction | `utils/UrlUtils.kt` | `core/utils/url_utils.dart` |
| Server mirror mapping | `services/sync/ServerUrlMapper.kt` | `core/sync/server_url_mapper.dart` |
| Adaptive batch sizing | `services/sync/AdaptiveBatchProcessor.kt` | `core/sync/adaptive_batch_processor.dart` |
| JSON coercion | `utils/JsonUtils.kt` | `core/utils/json_utils.dart` |
| Version comparison | `utils/VersionUtils.kt` (partial) | `core/utils/version_utils.dart` |
| Dashboard navigation host | `ui/dashboard/DashboardActivity.kt` | `ui/dashboard/dashboard_shell.dart` |
| Courses list, search, filters | `CoursesRepositoryImpl`, `CoursesFragment` | `repository/courses_repository.dart`, `ui/courses/courses_screen.dart` |
| Course detail and steps | `CourseDetailFragment`, `MyCourse`/`CourseStep` | `ui/courses/course_detail_screen.dart`, `data/local/course_mapper.dart` |
| Shelf write-back | `UserRepositoryImpl.uploadShelfData`, `UploadToShelfService`, `RemovedLog` | `repository/shelf_repository.dart`, `removed_log` table |
| Calendar | `CalendarFragment` | `ui/calendar/calendar_screen.dart` |
| Onboarding | `OnboardingActivity`, `OnboardingAdapter` | `ui/onboarding/onboarding_screen.dart` |
| User profile (offline view/edit) | `UserProfileFragment`, `UserProfileViewModel` | `ui/user/profile_screen.dart`, `providers/session_provider.dart` |
| Settings (appearance/server details) | `SettingsActivity`, `ThemeManager` | `ui/settings/settings_screen.dart`, `providers/settings_provider.dart` |
| Dictionary | `DictionaryActivity`, `DictionaryDao` | `ui/dictionary/dictionary_screen.dart`, `repository/dictionary_repository.dart` |
| Notifications | `NotificationsFragment`, `NotificationsRepositoryImpl` | `ui/notifications/notifications_screen.dart`, `repository/notifications_repository.dart` |
| My life | `LifeFragment`, `LifeRepositoryImpl` | `ui/life/life_screen.dart`, `repository/life_repository.dart` |
| References | `ReferencesFragment`, `ReferencesAdapter` | `ui/references/references_screen.dart` |
| Personals (offline CRUD) | `PersonalsFragment`, `PersonalsRepositoryImpl` | `ui/personals/personals_screen.dart`, `repository/personals_repository.dart` |
| Ratings (course UI/offline data) | `RatingsFragment`, `RatingsRepositoryImpl` | `ui/ratings/rating_dialog.dart`, `repository/ratings_repository.dart` |
| Durable write-back queue | `services/retry/RetryQueue.kt`, `RetryQueueWorker`, `model/RetryOperation.kt` | `repository/outbox_repository.dart`, `repository/outbox_drainer.dart`, `ui/outbox_drain_scope.dart` |
| Personal-note upload | `PersonalsRepositoryImpl.uploadPersonalDocument`, `Personal.serialize` | `repository/personals_uploader.dart` |
| Submissions create/upload/list/detail/answers | `Submission`, `Answer`, `SubmissionDao`, `UploadConfigs.Submissions`, `SubmissionsFragment`, `SubmissionDetailFragment` | `repository/submissions_repository.dart`, `repository/submissions_uploader.dart`, `ui/submissions/submissions_screen.dart`, `ui/submissions/submission_detail_screen.dart` |
| Events/meetups | `Meetup`, `MeetupDao`, `EventsRepositoryImpl`, `EventsDetailFragment`, meetup upload config | `data/local/meetup_mapper.dart`, `repository/events_repository.dart`, `repository/events_uploader.dart`, `ui/events/` |

`SharedPrefManager.getFirstLaunch()` is misleadingly named: it defaults to `false` and is set to
`true` once onboarding finishes, so it actually means "onboarding already done". The port stores
the same polarity under the clearer name `onboardingComplete`.

## Technology mapping

| Concern | Kotlin/Android | Flutter/Dart | Notes |
|---|---|---|---|
| DI | Dagger Hilt 2.60 + `@EntryPoint` | Riverpod 2 providers | Runtime graph, no kapt/KSP. `@EntryPoint` escape hatches for Workers become unnecessary. |
| Local DB | Room 2.8.4 (was Realm) | Drift 2.28 | Both are SQLite + DAOs + compile-checked queries. `Flow<List<T>>` → `Stream<List<T>>`. |
| Networking | Retrofit 3 + OkHttp 5 | Dio 5 | Retrofit's annotated interface becomes thin methods; `NetworkResult` sealed class ports directly. |
| Async | Coroutines + `StateFlow` | `Future`/`Stream` + Riverpod `Notifier` | `suspend fun` → `Future`, `StateFlow` → `Notifier`, `SharedFlow` → `Stream`. |
| Navigation | Activities/Fragments + `FragmentNavigator` | go_router | Per-activity prefs checks collapse into one declarative `redirect`. |
| UI | XML layouts + View Binding + RecyclerView | Widgets | 181 layout files have no direct equivalent; they are rewritten, not converted. |
| Prefs | `SharedPrefManager` | `shared_preferences` | |
| Secure storage | `SecurePrefs` (Tink `EncryptedSharedPreferences`) | `flutter_secure_storage` | Keystore-backed on Android either way. |
| Localization | `res/values-{lang}/strings.xml` | `.arb` + `gen-l10n` | Mechanical key-by-key transform; see below. |
| Background work | AndroidX `WorkManager` | Outbox + lifecycle drain | Durable write-back ported; timed/no-user work still open — see *Hard subsystems*. |

## Faithful quirks (deliberate non-improvements)

These look like bugs and are reproduced anyway, because changing them changes behaviour for
existing users and servers:

1. **PBKDF2 iteration count is hard-coded to 10** in `AndroidDecrypter`, ignoring the `iterations`
   field on the CouchDB `_users` document. Raising it would lock out every existing user.
2. **`buildCouchdbUrl` drops the URL path.** `https://planet.example.org/ml` becomes
   `https://satellite:PIN@planet.example.org:443` — CouchDB is reached at the host root under `/db`.
3. **`getUserImageUrl` form-encodes both segments** but rewrites `+` back to `%20` only in the
   image name, not the user id.
4. **Partial syncs are not rolled back.** A page failure mid-walk leaves earlier pages persisted,
   matching `SyncManager`.

Deliberate *deviations*, all flagged in code:

- **`ServerUrlMapper` no longer reads `BuildConfig.PLANET_*`.** Those come from the tracked
  `gradle.properties` — the committed-secrets problem `CLAUDE.md` documents. The Dart version takes
  its mapping table from `--dart-define=PLANET_SERVER_MAPPINGS=primary=alternative,...`, so nothing
  sensitive is committed. **The exposed PINs still need rotating server-side** regardless of this port.
- **Failure reasons are enums, not pre-localised strings.** `LoginSyncManager` returns English
  literals and `ConfigurationsRepositoryImpl` calls `context.getString(...)` inside the repository.
  The Dart repositories return `LoginFailureReason` / `ConfigurationFailureReason` and the UI
  localises, which keeps `BuildContext` out of the data layer.
- **The PIN and the credentialed CouchDB URL live in secure storage.** `SharedPrefManager` keeps
  `serverPin` and `couchdbURL` in plain `SharedPreferences`, which is world-readable by root and
  is swept up by Android auto-backup. `PlanetPrefs` puts both in `flutter_secure_storage` and
  caches them in memory at startup so the config getter stays synchronous.
- **`resourceRemoteAddress` is credential-free.** `MyLibrary.insertMyLibrary` writes
  `scheme://satellite:<pin>@host/resources/...` into every resource row, putting the PIN in the
  database thousands of times over. The port strips the userinfo and attaches credentials at
  request time. An empty CouchDB URL yields `null` rather than the Kotlin's unusable
  `http:///resources/...`.
- **Course step ids are position-derived, not content-derived.** Embedded steps carry no `_id`, and
  the Kotlin derives one as `Base64(stepJson.toString())`. That is unsafe as a primary key: two
  steps with identical content collide, so the upsert silently drops one (within a course) or moves
  it to the wrong course (across courses), and the key length grows with the step's text. The port
  uses `<courseId>:<position>` — bounded, unique, and stable across syncs. Safe to diverge on
  because the id is local in both apps, never a server value. `CourseDao.upsertAll` also deletes
  steps past the new length, which upserting alone cannot do when a course shrinks.
- **URL components are percent-encoded.** `buildCouchDbUrl` encodes the PIN, `userDocUrl` encodes
  the login name (keeping the `org.couchdb.user:` colon literal, which CouchDB requires), and
  `resourceUrl` returns `null` instead of interpolating the literal text `null`. All are no-ops
  for well-formed input; the Kotlin is simply wrong when a value contains `@`, `/` or a space.

## Write-back

Phase 3 opened the write path with `ShelfRepository`, which pushes the user's shelf document
(`courseIds` / `resourceIds` / `meetupIds`) back to CouchDB. Two things are worth knowing before
extending it:

- **The payload is derived state, not a queue.** It is recomputed from SQLite on every upload, so a
  failed push needs no retry bookkeeping — the next one simply sends current truth. Prefer this
  shape over an outbox for anything else that is a whole-document overwrite.
- **Deletions need an explicit record.** The merge unions local ids with the server's, so a "leave"
  would be silently re-added. The `removed_log` table (port of `RemovedLog`) is what makes a
  removal stick. Note the asymmetry, which is the Kotlin's: the removal list filters only the
  *server* side, so re-adding something beats a stale removal record.

Not yet solved: uploads only run while the user is in the app and acting. Submissions, news and
team writes need retry and background delivery — see item 1 below.

## Platform policy

Both platforms must permit cleartext, because the primary myPlanet deployment is a local community
server on plain HTTP (`http://<ip>:5000`):

- **Android** — `INTERNET` is declared in the *main* manifest (the Flutter template only declares
  it for debug/profile, so release builds would have had no network at all), plus
  `usesCleartextTraffic` and a `network_security_config.xml` mirroring the Kotlin app's.
- **iOS** — `NSAppTransportSecurity` / `NSAllowsArbitraryLoads` in `Info.plist`. Narrow this to
  specific domains if the deployment ever standardises on HTTPS.

## The hard part: what does not port mechanically

Ordered by risk, highest first.

1. **`WorkManager` → no equivalent. Resolved for write-back; still open for the rest.**
   `AutoSyncWorker`, `TaskNotificationWorker`, `NetworkMonitorWorker`, `RetryQueueWorker`,
   `DownloadWorker`, `FreeSpaceWorker`, `ServerReachabilityWorker` and `HeavyTableSyncWorker` rely
   on guaranteed, constraint-aware, OS-scheduled execution that survives process death. Flutter
   has no first-party answer; `workmanager` / `flutter_background_service` are thin
   platform-channel wrappers whose Android side would remain Kotlin.

   The unblocking observation is that `RetryQueue` and `RetryQueueWorker` do two separable jobs.
   The queue is a SQLite table — it is what actually survives process death — and the worker only
   decides *when* to drain it. So durability ports directly and only the trigger needs replacing.
   `OutboxRepository` is that table (a faithful port of `RetryOperation`, including the
   `min(30s · 2^attempt, 30min)` backoff and the `code >= 500` retryable rule from
   `UploadCoordinator`), `OutboxDrainer` replaces the worker, and `OutboxDrainScope` triggers it
   at startup and on every app resume.

   **The residual gap is scheduling, not durability**: a write made offline is sent the next time
   the app is opened with connectivity, not while it is closed. For an app users open regularly
   that is a latency cost rather than a correctness one, and it is bounded — the operation sits in
   SQLite until it succeeds or exhausts its attempts.

   This unblocks the *append* write-backs (`submissions`, `voices`, personal notes) that the
   shelf's derived-payload trick could not cover, because an append lost to a dead network is not
   recoverable by recomputation. `PersonalsUploader` is the first one built on it. What is still
   unresolved is periodic background work with no user present — `AutoSyncWorker`'s timed sync and
   `TaskNotificationWorker`'s deadline notifications genuinely need OS scheduling, and those are
   the cases that may still argue for a permanent Kotlin platform layer.
2. **`TeamsRepositoryImpl` (~1785 lines).** The largest file in the codebase, spanning team
   creation, tasks, membership roles and reactive queries. Should be split by responsibility
   *during* the port, not carried over whole.
3. **The generic upload framework.** `UploadConfig` is generic over `KClass<T : RealmObject>` with
   `queryBuilder: (RealmQuery<T>) -> RealmQuery<T>`. Dart has no reified generics and Drift's DAOs
   are concrete per-table, so `UploadCoordinator` needs re-architecting around explicit
   `fetchPending` / `markSynced` callbacks per upload type — the same problem the Realm → Room
   migration hit, and the same shape of answer.
4. **181 XML layouts.** No conversion tool produces idiomatic widgets. These are rewrites, and
   they dominate the remaining effort.
5. **Media playback and viewers.** Media3/ExoPlayer, OSMDroid offline maps, Markwon markdown and
   the shared `ResourceViewerActivity` each need a package choice and a fidelity review
   (`video_player`/`media_kit`, `flutter_map`, `flutter_markdown`).
6. **The 5 existing locales.** `values-{ar,es,fr,ne,so}/strings.xml` → `.arb` is mechanical and
   scriptable, but `crowdin.yml` must be repointed at `flutter/lib/l10n/*.arb`. Phase 1 ships
   `app_en.arb` in full and `app_es.arb` populated **only** from strings that already exist in
   `values-es/strings.xml` — nothing was machine-translated. Where a screen needs a string the
   Kotlin never had, the English is authored and the other locales are left absent so `gen-l10n`
   falls back and Crowdin can translate it properly. Arabic also needs an RTL pass.

   As of the ratings/personals/notifications batch, `app_en.arb` holds 166 keys and `app_es.arb`
   64. A key is carried into Spanish only when a `values-es` counterpart is unambiguous — the
   Kotlin string name normalises to the ARB key *and* its English text matches, or the English
   text matches exactly and every candidate shares one translation. That leaves **102 keys
   English-only**, and they are genuinely new phrasings (`profileUnavailable`,
   `dictionaryDownloadFailed`, `savedOffline`, the rating messages), not an unfinished pass.
   `gen-l10n` reports them on every build; they are Crowdin's to fill.

   Two quirks are reproduced rather than corrected, because they are what Spanish users see in
   the shipping app today: `myCourses`/`myLife`/`myHealth`/`myPersonals`/`achievements` resolve to
   `misCursos`/`miVida`/`miSalud`/`misPersonales`/`misLogros` — untranslated camelCase in
   `values-es` — and `search` is lower-case in English but `Buscar` in Spanish. Both are upstream
   defects in `strings.xml`; fixing them in Crowdin corrects the Kotlin and Flutter apps together,
   whereas diverging here would make the two apps disagree.

## Remaining UI packages (14 of 28)

`chat`, `community`, `components`, `enterprises`, `exam`,
`feedback`, `health`, `maps`,
`surveys`, `teams`, `viewer`, `voices` — plus submission answers/create/export, personal attachments/upload,
rating upload/sync, storage/retry, and the
rest of `settings`, plus profile photo/upload, membership, and the rest of `user`, and the
rest of `sync` and `dashboard` (the Kotlin dashboard's activity cards, surveys widget and drawer
are not ported; only the navigation host is).

Suggested order, dependency-first: `teams` → `voices` → `submissions` → `surveys`/`exam` → the
rest. Course progress, exams and certification are deliberately deferred with their own packages
rather than bundled into the courses slice.

## Working on the Flutter app

```bash
cd flutter

flutter pub get

# Generated sources are gitignored and must be built before analyze/test.
dart run build_runner build
flutter gen-l10n

flutter analyze
flutter test
flutter build apk --debug

# Point at mirrored community servers without committing their addresses:
flutter run --dart-define=PLANET_SERVER_MAPPINGS=http://a.example=https://a-clone.example
```

### Conventions

- `lib/core/` is **pure Dart** — it must not import `package:flutter`. That keeps URL building,
  crypto, JSON coercion and version comparison testable without a widget binding.
- Every ported file names its Kotlin counterpart in its doc comment.
- Repositories return plain rows/values, never live database objects — the same rule
  `CLAUDE.md` states for Realm/Room.
- Security-critical code is tested against **published or independently generated vectors**
  (RFC 6070 for PBKDF2; Python `hashlib` digests for the credential check), never against the
  implementation itself.

### Known gap: PDF export is Latin-only

`SubmissionsExporter` renders through the `pdf` package's built-in Helvetica, which is
WinAnsi-encoded. Arabic and Nepali are supported app locales, and their glyphs are simply absent
from that font — a submission with a Devanagari or Arabic title exports without crashing (verified)
but with those characters missing. Fixing it means embedding a Unicode TTF, a ~1 MB binary asset
that was deliberately removed from the port. Decide that trade before the export is offered to
users in an RTL or Devanagari locale.

### Two drift traps that silently lose writes

Both cost a debugging round in the ratings/personals batch, and both fail *quietly* — the write
succeeds, it just doesn't do what the Kotlin did.

- **`insertOnConflictUpdate` only writes the columns the companion carries.** Room's `@Update`
  writes the whole row. So a `Companion.insert(...)` that omits a field leaves the existing value
  in place instead of resetting it, and `row.toCompanion(true)` (`nullToAbsent`) drops every
  field you just set to `null`, so a cleared description or phone number can never be cleared.
  Port an `@Update` with `toCompanion(false)`, and name the columns a partial upsert must reset —
  `NotificationsRepository` has to pass `isRead: const Value(false)` explicitly to match
  `NotificationsRepositoryImpl`.
- **Widget tests fall through to the real database.** `wrapScreen` redirects
  `appDatabaseProvider` to `AppDatabase.memory()` ahead of the caller's overrides. Without it a
  screen that reads an un-overridden DAO — an unread badge, a rating summary, a filter list —
  reaches `AppDatabase.open()`, whose `path_provider` lookup has no platform channel under
  `flutter test`; screens read those through `.valueOrNull ?? <default>`, so the error is
  swallowed and the test passes while asserting against nothing. The backstop turns that into a
  "Timer is still pending" failure at teardown: when you see it, override the provider the screen
  actually reads.

---

**Last updated**: 2026-08-03
**Phase**: 15 of N (offline events/meetups)
