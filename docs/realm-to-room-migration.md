# Realm → Room migration

Tracking document for migrating myPlanet's local persistence from **Realm-Java 10.19.0 (EOL)**
to **AndroidX Room 2.8.4**.

## Strategy

- **Coexistence, green at every commit.** Room is added *alongside* Realm. Each domain is moved
  to Room independently; the build compiles and unit tests pass after every step. Realm (plugin,
  deps, `RealmMigrations`, `DatabaseService`, `RealmRepository`, and the now-dead `Realm*` model
  classes) is removed only in the final commit, once nothing references it.
- **Drop-and-resync, no data copy.** Existing on-device Realm data is *not* migrated into Room.
  On first launch after upgrade the Realm file is discarded and Room starts empty, then re-pulls
  from the Planet/CouchDB server. Room is therefore configured with
  `fallbackToDestructiveMigration(true)`. (User-decision: acceptable loss of unsynced local-only
  data such as drafts.)
- **Leave dead Realm models in place until the end.** Removing a `RealmObject` subclass changes
  Realm's derived schema and would force a Realm schema-version bump + migration mid-transition.
  To avoid destabilising the still-live Realm store, each migrated `Realm*` model class is left
  untouched (dead code) until Realm is deleted wholesale in the final commit.

## The hard part: three generic subsystems

The bulk of the work is *not* the 38 model classes — it is that the data layer is built on
Realm's **generic runtime APIs**. Three subsystems are generic over `RealmObject`/`Realm` and are
shared by many domains, so they must be re-architected (not just ported per-model):

1. **`repository/RealmRepository`** — base class for all 23 repositories. Exposes
   `queryList(clazz){ equalTo(...) }`, `findByField`, `count`, `queryListFlow`, `save/update/delete`,
   `withRealm`, `executeTransaction` — generic over any `RealmObject`. Room validates SQL at compile
   time against per-entity DAOs, so each of the **207 `.where()` call sites** becomes a concrete DAO
   query. Reactive `queryListFlow` maps to Room `Flow<List<T>>` DAO queries.
2. **`services/upload/UploadConfig` + `UploadCoordinator`** — generic upload framework keyed by
   `modelClass: KClass<T : RealmObject>` with `queryBuilder: (RealmQuery<T>) -> RealmQuery<T>` and
   `additionalUpdates: (Realm, T, …)`. Must become database-agnostic (e.g. `fetchPendingItems` +
   DAO-backed update hooks) before any uploadable model can move.
3. **`services/sync/TransactionSyncManager`** — pulls CouchDB docs and writes them into Realm via
   `createObject`/`RealmList` inside each repository's `insert()`. Each `insert()` is rewritten to
   build Room entities and call DAO upserts.

Because these are shared, **no synced/uploaded model is cleanly separable** until the generic
frameworks are ported. Recommended order:

1. Foundation (done) + self-contained domains that bypass all three frameworks (Dictionary).
2. Re-architect `RealmRepository` → `RoomRepository` surface (or move repos to direct DAO
   injection) and port the reactive flow helper.
3. Re-architect the upload framework to be Room-based.
4. Port `TransactionSyncManager` + per-repository `insert()`.
5. Convert the remaining model domains + their DAOs + repositories + tests, one at a time.
6. Delete Realm (plugin, deps, `RealmMigrations`, `DatabaseService`, `RealmRepository`, all
   `Realm*` model classes, `librealm-jni.so` `doNotStrip`, `enableJetifier`).

## Entity mapping rules

| Realm | Room |
|-------|------|
| `open class X : RealmObject()` | `@Entity(tableName="…") class X` (or `data class`) |
| `io.realm.annotations.PrimaryKey` | `androidx.room.PrimaryKey` |
| `io.realm.annotations.Index` (field) | `@Entity(indices=[Index("field")])` |
| `io.realm.annotations.Ignore` | `androidx.room.Ignore` |
| `RealmList<String>?` | `List<String>?` + `Converters` (JSON) |
| `RealmList<RealmChild>?` (nested) | child is its own `@Entity` with a parent-id column; the parent's list is an `@Ignore` field populated by the repository (or a Room `@Relation` POJO). Applies to `courseSteps`, `attachments`, `answers`, `conversations`. |

New Room code lives under `data/room/` (`entity/`, `dao/`, `AppDatabase`, `Converters`) and is
wired through `di/RoomModule`.

## Progress

- [x] Build env verified (Gradle 9.6.1 + Android SDK; `compileDefaultDebugKotlin` green).
- [x] Room deps added (coexisting with Realm).
- [x] Foundation: `Converters`, `AppDatabase`, `RoomModule`.
- [x] **Dictionary** domain migrated end-to-end (`DictionaryEntity`, `DictionaryDao`,
      `DictionaryActivity`). First proven template (raw-Realm activity).
- [x] Realm transition config: `DatabaseService` now uses `deleteRealmIfMigrationNeeded()` so a
      model leaving the Realm schema recreates the Realm file instead of crashing (drop-and-resync).
- [x] **Life** domain migrated end-to-end (`RealmMyLife` is now a Room `@Entity`, `MyLifeDao`,
      `LifeRepositoryImpl` off `RealmRepository`, both Life test files ported). First proven
      *repository* template (in-place model conversion, class name kept so UI is untouched).
- [ ] Re-architect `RealmRepository`, upload framework, sync.
- [ ] Remaining 36 model domains.
- [ ] Migrate 39 Realm-based test files.
- [ ] Remove Realm; full `assembleDefaultDebug` + `testDefaultDebugUnitTest` green.

## Local build environment (ephemeral sessions)

This repo needs Gradle 9.6.1 (AGP 9.2.1) + Android SDK (`platforms;android-37.0`,
`build-tools;37.0.0`). In restricted environments where `services.gradle.org` redirects to a
GitHub-gated release, fetch the Gradle distribution from a non-GitHub mirror
(e.g. `https://mirrors.cloud.tencent.com/gradle/gradle-9.6.1-bin.zip`) into the wrapper cache, and
install the SDK from `dl.google.com` via `cmdline-tools`. Set `sdk.dir` in `local.properties`.
