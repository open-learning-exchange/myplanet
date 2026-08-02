# Kotlin → Flutter/Dart migration

Tracking document for migrating myPlanet from the **Kotlin/Android** app in `app/` to a
**Flutter/Dart** app in `flutter/`.

## Status

**Phase 1 complete — skeleton plus one end-to-end vertical slice.** The Flutter app builds,
analyzes clean, and passes its test suite. It is *not* yet a replacement for the Kotlin app: one
of 28 UI packages is ported.

## Strategy

- **Coexistence, green at every commit.** The Flutter app lives in `flutter/` alongside the
  untouched Kotlin app. `app/` keeps building and shipping; its `build.yml` / `test.yml` workflows
  are unmodified. A separate `.github/workflows/flutter.yml` guards the port, path-filtered to
  `flutter/**` so neither pipeline slows the other down.
- **Port by vertical slice, not by layer.** Each slice carries a feature from UI to network to
  disk, so every increment is a runnable, testable app rather than an unusable pile of models.
  Slice 1 is server configuration → login → resources list.
- **The Kotlin stays the specification.** Every ported file names its Kotlin counterpart in a
  doc comment, and behaviour is replicated including quirks (see *Faithful quirks* below).
  Deviations are called out explicitly rather than silently improved.
- **Drop-and-resync, no data copy.** Same policy as the Realm → Room migration: local rows are a
  cache of CouchDB, so the Flutter app starts with an empty database and re-pulls from the
  server. There is no Room → Drift data migration path, and none is planned.

## What Phase 1 covers

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
| Background work | AndroidX `WorkManager` | **Not yet ported** | The biggest open gap — see *Hard subsystems*. |

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

Two deliberate *deviations*, both flagged in code:

- **`ServerUrlMapper` no longer reads `BuildConfig.PLANET_*`.** Those come from the tracked
  `gradle.properties` — the committed-secrets problem `CLAUDE.md` documents. The Dart version takes
  its mapping table from `--dart-define=PLANET_SERVER_MAPPINGS=primary=alternative,...`, so nothing
  sensitive is committed. **The exposed PINs still need rotating server-side** regardless of this port.
- **Failure reasons are enums, not pre-localised strings.** `LoginSyncManager` returns English
  literals and `ConfigurationsRepositoryImpl` calls `context.getString(...)` inside the repository.
  The Dart repositories return `LoginFailureReason` / `ConfigurationFailureReason` and the UI
  localises, which keeps `BuildContext` out of the data layer.

## The hard part: what does not port mechanically

Ordered by risk, highest first.

1. **`WorkManager` → no equivalent.** `AutoSyncWorker`, `TaskNotificationWorker`,
   `NetworkMonitorWorker`, `RetryQueueWorker`, `DownloadWorker`, `FreeSpaceWorker`,
   `ServerReachabilityWorker`, `HeavyTableSyncWorker` rely on guaranteed, constraint-aware,
   OS-scheduled execution that survives process death. Flutter has no first-party answer;
   `workmanager` / `flutter_background_service` are thin platform-channel wrappers, and the
   Android side would remain Kotlin. **Decide this before Phase 2** — it may argue for keeping a
   Kotlin platform layer permanently rather than a pure-Dart app.
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
   `values-es/strings.xml` — nothing was machine-translated. Arabic also needs an RTL pass.

## Remaining UI packages (27 of 28)

`calendar`, `chat`, `community`, `components`, `courses`, `dashboard`, `dictionary`,
`enterprises`, `events`, `exam`, `feedback`, `health`, `life`, `maps`, `notifications`,
`onboarding`, `personals`, `ratings`, `references`, `settings`, `submissions`, `surveys`,
`teams`, `user`, `viewer`, `voices` — plus the rest of `sync`.

Suggested order, dependency-first: `dashboard` → `courses` → `teams` → `voices` → `submissions`
→ `surveys`/`exam` → the rest.

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

---

**Last updated**: 2026-08-02
**Phase**: 1 of N (skeleton + first vertical slice)
