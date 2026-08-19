# OpenHands memory — myPlanet

## Skills (auto-loaded from .agents/skills/)

Skill repos are git submodules. `.openhands/setup.sh` initializes them at
session start, before skill discovery runs. In any other context (fresh
manual clone, other agents), initialize them yourself:

```bash
git submodule update --init --recursive
```

- **merge-prepping** — rewrite PR titles into house style; source: https://github.com/dogi/merge-prepping
- **kotlin-importing** — sort/clean Kotlin imports; source: https://github.com/dogi/kotlin-importing

## Reference docs

- CLAUDE.md — full codebase guide (architecture, build, conventions)
- docs/DOMAIN_MODEL.md — learning domain: roles, courses, teams, surveys, sync
- docs/CODE_STYLE_GUIDE.md — naming, imports, coroutines, Room, Hilt, UI
- docs/TESTING.md — test patterns per layer
- docs/AGENT_SPELLBOOK.md — summoning AI agents on PRs + "The Skill Sync": how the shared skills are wired up and maintained

## Flutter port — state of the port (in `flutter/`)

The Dart port runs alongside `app/` (the shipping Kotlin app). Slices land one
UI package at a time. Conventions worth remembering across slices:

- **Toolchain is not pre-installed in the sandbox.** Flutter 3.44.8 stable
  (the version pinned in `.github/workflows/flutter.yml`) is cloned on demand:
  `git clone --depth 1 -b 3.44.8 https://github.com/flutter/flutter.git ~/flutter`,
  then `export PATH="$HOME/flutter/bin:$PATH"`. `unzip` must be installed
  (`sudo apt-get install unzip`) before the SDK will extract. Before any
  analyze/test: `cd flutter && flutter pub get`, `dart run build_runner build`
  (generated sources are gitignored), `flutter gen-l10n`.

- **DB schema**: drift, `AppDatabase` in `flutter/lib/data/local/app_database.dart`.
  Schema version is `schemaVersion`. **Drop-and-resync** is the migration
  policy: CouchDB cache tables are dropped and refilled by the next sync;
  locally-authored tables are listed in `localAuthorityTables` and preserved.
  - Bump `schemaVersion` on any entity change. New cache tables auto-create via
    `createAll` (`CREATE TABLE IF NOT EXISTS`). New **preserved** tables also
    auto-create, but **must** get a preservation test in
    `test/data/local/migration_test.dart` and be added to its `covered` set, or
    that test fails.
  - Adding a column to a **preserved** table needs a hand-written
    `_addColumnIfMissing` step guarded by `if (from < N)` (see the
    `chat_history.is_uploaded` v25 step).
  - `migration_test.dart`'s `runUpgrade(from:)` defaults to
    `schemaVersion - 1`; the chat_history tests pin `from: 24` because they
    exercise the v24→v25 path.
- **Sync pull**: repositories expose `sync{Table}(config, onProgress)` returning
  `SyncResult` (`SyncComplete(savedCount)` / `SyncFailed`). Paginated
  `_all_docs` pulls via `AdaptiveBatchProcessor`. Tables with local writes do
  **not** run `deleteNotIn`; pure caches do.
- **Upload write-back**: an `*Uploader` per domain (`queuePending(config)` +
  `handler`). `queuePending` scans pending rows and enqueues to the `outbox`;
  the `OutboxDrainer` (registered in `app_providers.dart`'s
  `outboxDrainerProvider` handler map, keyed by `Uploader.type`) sends them on
  app resume. UI write sites call `queuePending` after a local write (see
  `take_exam_screen.dart`, `take_course_screen.dart`).
- **Dashboard sync areas**: `DashboardSyncArea` enum in
  `dashboard_sync_provider.dart`; each area maps to a `*SyncNotifier` in its
  feature's `*_providers.dart`. A "sync courses" refreshes related tables
  together (e.g. `CourseSyncNotifier` runs courses + progress + certifications).
- **Tests**: `flutter analyze` (must be clean) + `flutter test` (CI gate). The
  Flutter workflow also gates on formatting — run `dart format lib test` before
  pushing (check with `dart format --output=none --set-exit-if-changed lib test`).
  Repository tests use `AppDatabase.memory()` + mocktail `Mock implements
  PlanetApi`. Uploader tests mirror `test/repository/ratings_uploader_test.dart`.
  - **l10n/initState trap**: a `ConsumerStatefulWidget` that needs
    `AppLocalizations.of(context)` must kick off its async load in
    `didChangeDependencies` (guarded by a `_loaded` bool), **not** `initState`.
    `initState` runs before the localization delegates finish loading, so
    `AppLocalizations.of(context)` throws and the load Future never sets
    `_isLoading=false` — the spinner spins forever and `pumpAndSettle` times
    out. See `storage_breakdown_screen.dart` / `storage_category_detail_screen.dart`.
  - **dart:io under `flutter test`**: `Directory.exists()`/`File` async ops
    hang under the binding's fake clock (they need the real event loop). A
    screen that walks the filesystem inline is therefore untestable — route
    the read through a repository seam and mock it in widget tests (as
    `storage_breakdown_screen_test.dart` does via `getOfflineResourceItems`).
  - **Platform channels as seams**: when a feature needs a platform-only API
    with no pure-Dart equivalent (device free space via Android
    `StorageStatsManager`, etc.), define an abstract Dart seam
    (`lib/core/system/*.dart`, e.g. `DiskStats`) backed by a
    `MethodChannel` impl, plus the Kotlin handler in
    `flutter/android/app/src/main/kotlin/.../MainActivity.kt`
    (`configureFlutterEngine` override). Production sets
    `DiskStats.instance = _MethodChannelDiskStats()`; a `diskStatsProvider`
    lets widget tests inject a fake. The Kotlin side is not unit-tested in
    the Flutter gate (it compiles only on an Android build).
