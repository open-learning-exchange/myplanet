# myPlanet device-user-scoped sync policy

This branch changes `myPlanet` sync policy so the heaviest user-bound databases are no longer fetched as if they were bootstrap data. Instead they are scoped to the actual users that have logged into this device.

The shape of the change is the same across all five user-scoped databases:

- split bootstrap sync from steady-state sync
- derive a device-local scope from a `RealmDeviceUser` store (backfilled from local login history on first run, maintained on every subsequent login)
- replace `/_all_docs?include_docs=true` with paged `_find`
- insert the returned docs through the existing repository bulk-insert path

`tablet_users` is **not** used as the locality source. It is the set of non-admin users allowed to log into `myPlanet`, so `userId IN tablet_users` is effectively a near-full-table scan on a real Planet. `device_users` (a Realm-backed table of users actually seen on this device) is the source of truth for steady-state sync.

## Bootstrap vs. steady-state vs. background

The three policy sets live in `SyncPolicy.kt`:

```kotlin
val bootstrapDeferredTables = setOf(
    "courses_progress",
    "submissions",
    "login_activities",
    "notifications",
    "chat_history",
    "team_activities"
)

private val steadyStateBackgroundTables = setOf(
    "courses_progress",
    "notifications"
)
```

How each set is applied:

- **Bootstrap (no device users yet)** — `applyBootstrapPolicy` filters `bootstrapDeferredTables` out of the requested table list. The first sync after install therefore skips the heavy user-bound tables entirely; they have no device scope to filter on yet.
- **Steady-state foreground (device users exist)** — `applyForegroundPolicy` runs the bootstrap filter (a no-op once device users exist) and additionally removes `steadyStateBackgroundTables` from the foreground job, so they don't block sync completion.
- **Background follow-up** — `backgroundTablesFor` returns `courses_progress` (when `courses` or `courses_progress` was requested) plus `notifications`. These run on a separate coroutine via `startBackgroundDeferredSync` after the main sync wraps up.

Both sync paths apply the same three calls:

- `SyncManager.startFullSync` and `SyncManager.startFastSync` (legacy path)
- `ImprovedSyncManager.start` (improved path)

So the policy is enforced regardless of which manager the user is routed through, and regardless of fast vs. standard mode.

## Per-database steady-state implementations

All scoped paths are dispatched from `TransactionSyncManager.syncDb`. Three of them share the generic `syncFindByField` helper; `courses_progress` and `submissions` each have a bespoke helper because of their pagination tuning and field projection needs.

### courses_progress

- selector source: `RealmDeviceUser.userId`
- selector: `userId: { "$in": [...] }`
- endpoint: `courses_progress/_find`
- page size: 1000, bookmark pagination
- requests a fields projection (`_id`, `_rev`, `courseId`, `createdDate`, `createdOn`, `parentCode`, `passed`, `stepNum`, `updatedDate`, `userId`) to keep payloads small
- runs as a background follow-up after foreground sync, not in the foreground phase

### submissions

- selector source: `RealmDeviceUser.userId`
- selector: `userId: { "$in": [...] }`
- endpoint: `submissions/_find`
- page size: 100, bookmark pagination
- runs in the foreground phase once device users exist

### login_activities

- selector source: `RealmDeviceUser.userName`
- selector: `user: { "$in": [...] }`
- endpoint: `login_activities/_find`
- page size: 1000, bookmark pagination
- implemented through the shared `syncFindByField` helper

### chat_history

- selector source: `RealmDeviceUser.userName`
- selector: `user: { "$in": [...] }`
- endpoint: `chat_history/_find`
- page size: 1000, bookmark pagination
- implemented through the shared `syncFindByField` helper

### team_activities

- dependency path: device users → local team membership (`TeamsRepository.getTeamIdsForUsers`) → `teamId` set
- selector: `teamId: { "$in": [...] }`
- endpoint: `team_activities/_find`
- page size: 1000, bookmark pagination
- skipped when there are no team memberships for the current device users

### notifications

- deferred from bootstrap and run as a background follow-up after main sync once device users exist
- not yet selector-scoped on the wire — still pulled via `_all_docs` inside the background job
- the obvious next step is `user: { "$in": deviceUserNames }`; recency / unread filters can be added once the server side has matching indexes

## Tables intentionally left on `_all_docs`

Issue `planet#9895` listed several heavy tables. Not all of them are user-scoped, so the policy doesn't touch them:

- `ratings` — small payload (the issue's run was 88 docs); slow only because it queues behind other requests. Not user-bound, so a `_find` selector wouldn't help.
- `teams` — small and not strictly user-scoped; current `_all_docs` behavior is fine.
- `tablet_users` — synced first, deliberately, because it's the auth scope that other tables depend on.

## Files changed

- `app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt`
- `app/src/main/java/org/ole/planet/myplanet/data/RealmMigrations.kt`
- `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt`
- `app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt`
- `app/src/main/java/org/ole/planet/myplanet/model/RealmDeviceUser.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DeviceUserRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DeviceUserRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/ImprovedSyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncPolicy.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/UserSessionManagerTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/sync/TransactionSyncManagerTest.kt`

## Server-side expectations

For these `_find` selectors to scale on real data, Planet should add Mango indexes that match them. Without the indexes the server falls back to full table scans and most of the win is lost:

- `courses_progress`: index `userId`
- `submissions`: index `userId`
- `login_activities`: index `user`
- `chat_history`: index `user`
- `team_activities`: index `teamId`
- `notifications`: index `user` (once selector-scoped)

## Known limitations

- Bootstrap deferral is policy-based, not server-assisted — the server still has the full table available; we just stop pulling it client-side until we have a device scope.
- No recency window on the activity / history tables yet; we still pull every matching doc for every device user.
- The selector field choices (`courses_progress.userId`, `submissions.userId`, `login_activities.user`, `chat_history.user`) still need verification against real production documents.
- `notifications` is backgrounded but not yet selector-scoped.
- The branch was not fully compiled in the original sandbox environment because Gradle download was blocked; verify a clean build locally before merging.

## Suggested follow-ups

1. Verify `courses_progress.userId`, `submissions.userId`, `login_activities.user`, and `chat_history.user` against real server documents.
2. Add the Mango indexes listed above on the Planet side.
3. Move `notifications` from "deferred + background" to a `user`-scoped `_find` matching the others.
4. Add a recency window to `login_activities` and `chat_history` once the server indexes are in place.
5. Re-run bootstrap and steady-state sync separately on real data to confirm the duration regressions from issue #9895 are gone.
