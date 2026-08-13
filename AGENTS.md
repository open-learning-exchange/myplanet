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
- **Tests**: `flutter analyze` (must be clean) + `flutter test` (CI gate).
  Repository tests use `AppDatabase.memory()` + mocktail `Mock implements
  PlanetApi`. Uploader tests mirror `test/repository/ratings_uploader_test.dart`.
