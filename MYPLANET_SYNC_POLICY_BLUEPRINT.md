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

## Device-user catch-up sync

Bootstrap deferral creates a real correctness gap on the first appearance of any device user — not just the very first sync. A new user logging in (or signing up) on a tablet that already has other device users still has all of *their* user-bound data missing locally, because every prior sync's `_find` selector was scoped to the previous device users. Most visible failure mode: the new user starts a team survey, the local DB shows no existing team submission (because that submission was scoped out by the previous selector), and the tablet writes a duplicate.

To close the gap, `UserSessionManager.onLoginAsync` triggers a one-shot foreground sync the moment `DeviceUserRepository.upsertFromLogin` reports a real **insert** (not just a touch of `lastLoginAt` on an existing row). Three cases fall out:

- First-ever login on a fresh tablet → insert → catch-up runs.
- Same user re-login → update → no catch-up; regular sync cadence handles them.
- New user (login or signup) on an existing tablet → insert → catch-up runs.

By the time the catch-up sync evaluates `applyForegroundPolicy`, `RealmDeviceUser` includes the new user, so the previously-deferred tables are pulled with a selector that covers them.

Open follow-up: the catch-up currently fires from `onLoginAsync` and runs concurrently with the post-login UI. A cleaner UX is to hold the redirect (`SyncActivity.openDashboard()`) until the catch-up's `onSyncComplete` fires, so users only enter the app once their data is local. That requires plumbing a completion signal through the existing `onLoginAsync` callback path and is left as a separate UI-flow change.

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

Submissions need two passes because they come in two flavors:

- **Per-user submissions** (exam attempts, individual surveys) carry a populated `user` object with `_id`.
- **Team-scoped submissions** (team surveys) are written with an empty `user` object and `team._id` set (`submissions.service.ts:223-225`). A pure user-scoped selector misses these entirely. They matter for correctness, not just completeness — Planet's submission writer checks for an existing team submission *before* creating a new one, so missing local copies cause duplicate writes from the tablet.

CouchDB 2.3.1's Mango planner does not reliably index-union `$or` across two different fields, so we run two separate paginated `_find` calls instead of one combined query.

- pass 1 selector: `user._id: { "$in": deviceUserIds }`, against `user-id-index`
- pass 2 selector: `team._id: { "$in": teamIds }`, against `team-id-index`
- `teamIds` derived via `TeamsRepository.getTeamIdsForUsers(deviceUserIds)` — same path used for `team_activities`
- pass 2 is skipped when no team memberships exist for the device users
- endpoint: `submissions/_find`
- page size: 100 per pass, bookmark pagination
- both passes run in the foreground phase once device users exist
- both passes feed the same `submissionsRepository.bulkInsertFromSync` path; results don't overlap by construction (team submissions have empty `user._id`)

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
- pulled via `_all_docs` inside the background job — **intentionally not user-scoped**
- planet writes some notifications with sentinel values like `user: 'SYSTEM'` (admin events such as cross-planet connection requests) and the planet UI's reader OR's the current user with `'SYSTEM'` for admins (`notifications.component.ts:67-71`). A strict `user IN [...]` selector on myplanet would silently drop those. Volume from issue `#9895` was small (~2.5k docs / 1m34s), so background `_all_docs` is a reasonable trade-off versus adding policy logic for sentinel values.

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

These Mango indexes are required for the `_find` selectors to scale; without them the server falls back to full-table scans and most of the win is lost. They have been added to `couchdb-setup.sh` in the planet repo (under issue `planet#9895`):

- `courses_progress`: index `userId` (`user-index`)
- `submissions`: index `user._id` (`user-id-index`) plus index `team._id` (`team-id-index`) for the team-scoped pass
- `login_activities`: index `user` (`user-index`)
- `chat_history`: index `user` (`user-index`)
- `team_activities`: index `teamId` (`team-index`)

`notifications` is intentionally not indexed for user lookup — see the notifications section above.

## Known limitations

- Bootstrap deferral is policy-based, not server-assisted — the server still has the full table available; we just stop pulling it client-side until we have a device scope.
- No recency window on the activity / history tables yet; we still pull every matching doc for every device user.
- Selector fields verified against the planet codebase: `courses_progress.userId`, `submissions.user._id`, `login_activities.user`, `chat_history.user`, `team_activities.teamId` all match the actual document shape on Planet.
- `notifications` is backgrounded on `_all_docs` rather than selector-scoped, by design — see the notifications section.
- The branch was not fully compiled in the original sandbox environment because Gradle download was blocked; verify a clean build locally before merging.

## Suggested follow-ups

1. Hold the post-login redirect until the device-user catch-up sync completes (UI-flow change in `SyncActivity.onLogin`), so users only reach the dashboard once their data is local.
2. Add a recency window to `login_activities` and `chat_history` once we have a sense of how far back is useful on a real device.
3. Re-run bootstrap and steady-state sync separately on real data to confirm the duration regressions from issue #9895 are gone.
