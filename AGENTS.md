# OpenHands memory — myPlanet

## Skills (auto-loaded from .agents/skills/)

Skill repos are git submodules. `.openhands/setup.sh` initializes them at
session start, before skill discovery runs. In any other context (fresh
manual clone, other agents), initialize them yourself:

```bash
git submodule update --init --recursive
```

- **agents-summoning** — summon other AI agents on PRs/issues: who answers, how to leash a doer, why a summon went silent; source: https://github.com/dogi/agents-summoning
- **merge-prepping** — rewrite PR titles into house style; source: https://github.com/dogi/merge-prepping
- **kotlin-importing** — sort/clean Kotlin imports; source: https://github.com/dogi/kotlin-importing

Reviewers speak; doers act — an unleashed doer mention (`@openhands`, `@devin`,
`@copilot`) defaults to commits on your branch, so add "comment only" when that
isn't wanted.

## Reference docs

- CLAUDE.md — full codebase guide (architecture, build, conventions)
- docs/DOMAIN_MODEL.md — learning domain: roles, courses, teams, surveys, sync
- docs/CODE_STYLE_GUIDE.md — naming, imports, coroutines, Room, Hilt, UI
- docs/TESTING.md — test patterns per layer

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
  - **Never `pumpAndSettle` while a spinner is on screen.** A button that
    swaps its icon for a `CircularProgressIndicator` during an async action
    (`take_exam_screen`'s Submit, while `_isSubmitting`) keeps an indefinite
    animation running, so `pumpAndSettle` spins to its **ten-minute** default
    and the test looks hung rather than failed. Drive those flows with
    `runAsync` + `pump` rounds. Real `dart:io` inside such a flow needs
    generously more rounds than drift's in-memory reads do — a
    `Directory.create` + `writeAsBytes` pair took roughly five times as many
    as everything else in `take_exam_screen_test.dart`.
  - **Bytes on disk and the row that points at them need one key.** The
    write side and the read-back side must derive the path from the same id,
    or the read silently finds nothing. `take_exam_screen` filed a capture
    under the *submission* id while `SubmitPhotosUploader` looked for it under
    the *photo row* id, so no verification photo ever uploaded and nothing
    logged an error. Where the id is minted inside a repository, expose the
    derivation (`SubmissionsRepository.photoIdFor`) rather than letting the
    caller guess.
  - **Providers a screen reads but never watches.** `ref.read(someAsyncProvider)
    .valueOrNull` is `null` until something else in the graph has resolved that
    provider. `take_exam_screen` read `sessionProvider` that way and dropped the
    whole graded attempt when it came back null; the shipping app only hid it
    because the router holds a `ref.listen` on the session. Await
    `<provider>.future` when the screen genuinely needs the value.
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
  - **CP1252 em-dash trap in docs**: em-dashes written into `CLAUDE.md`,
    `AGENTS.md`, or `docs/` have been corrupted to a raw CP1252 `0x97` byte
    three separate times (twice in the migration doc, once in CLAUDE.md),
    leaving the file invalid UTF-8. After editing docs with em-dashes, verify
    before committing:
    `python3 -c "open('<file>','rb').read().decode('utf-8')"`.
  - **Auditing upstream commits**: when citing master commits in
    `docs/kotlin-to-flutter-migration.md`, use the real abbreviation from
    `git log --format=%H --abbrev=9` — a fabricated suffix reads fine and
    cites the wrong commit.
  - **Pure functions vs provider-watched repos**: when a filter/sort
    `*Provider` needs logic that lives on a repository, prefer a top-level
    pure function and have the provider call *that* rather than
    `ref.watch(<repo>Provider)`. A provider-watched repo transitively
    watches `planetApiProvider`/`serverConfigProvider`/`planetPrefsProvider`,
    and `planetPrefsProvider` is `UnimplementedError` in the widget-test
    harness — so the provider throws on build, the screen renders the error
    branch, and a "no results" screen test fails with "could not be loaded"
    instead. The repo's impl method can delegate to the same pure function so
    repository tests still exercise it through the interface. (Phase 75,
    `searchChatsForMode`/`sortChatsByRecency`.)
  - **Accent folding: use `core/utils/text_utils.normalizeText`. Do not
    hand-roll a decomposition table.** Dart's core library has no NFD
    normalizer and its `RegExp` rejects the `\p{InCombiningDiacriticalMarks}`
    block name (`FormatException: Invalid property name`, even with
    `unicode: true`) — which is real, and already solved: `text_utils`
    folds via the `diacritic` package. An earlier slice wrote a second
    `normalizeText` over a hand-written Latin-1 table in
    `core/utils/text_normalize.dart`; the two disagreed on 7 of 15 accented
    samples (`Škoda` → `škoda` vs `skoda`, `Māori` → `māori` vs `maori`),
    and because chat search used one while resource search used the other,
    `skoda` found the resource `Škoda` but not a chat about it. That file was
    deleted in Phase 78 and its divergences are pinned in
    `text_utils_test.dart`, so a narrower reimplementation fails the suite.
