# courses_progress filtered sync prototype

This branch changes `myPlanet` sync policy so large user-bound tables are no longer treated as bootstrap data by default.

For steady-state sync, `courses_progress` is no longer fetched with:

- `/_all_docs?include_docs=true`

Instead it now:

1. syncs `tablet_users` first
2. derives candidate user ids from a device-local `RealmDeviceUser` store
3. queries `courses_progress/_find` with a selector:

```json
{
  "selector": {
    "userId": {
      "$in": ["user-a", "user-b"]
    }
  },
  "limit": 1000,
  "bookmark": "..."
}
```

## Why this is a useful prototype

`courses_progress` was the main failure source in issue `planet#9895`, and it is a good template for the other heavy user-scoped databases:

- `submissions`
- `notifications`
- `chat_history`
- `login_activities`

The important part is the pattern, not just this one database:

- split bootstrap sync from steady-state sync
- derive the relevant local scope from actual device users
- replace `/_all_docs` with paged `_find`
- insert the returned docs using the existing repository bulk-insert path

Just as importantly, this prototype exposed a bad assumption: `tablet_users` is not a device-local sync scope. It is effectively the set of non-admin users allowed to log into `myPlanet`, so `userId IN tablet_users` still behaves like near-full-table sync on a real Planet.

This branch now enforces that distinction in code:

- bootstrap sync defers the heaviest user-bound tables until the device has real user history
- `device_users` are stored in Realm and backfilled from local login history
- steady-state sync uses `device_users` for `courses_progress`, `submissions`, `login_activities`, and `chat_history`
- `team_activities` now scopes by derived `teamId` values from device-user memberships
- `notifications` are deferred from bootstrap and run as a background follow-up once device users exist

## Files changed

- `app/src/main/java/org/ole/planet/myplanet/model/RealmDeviceUser.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DeviceUserRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DeviceUserRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/ImprovedSyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncPolicy.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/data/RealmMigrations.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/UserSessionManagerTest.kt`

## Pattern to copy for other databases

### submissions

- selector source: true device-local users, not `tablet_users`
- likely selector:

```json
{
  "selector": {
    "userId": {
      "$in": ["user-a", "user-b"]
    }
  }
}
```

### notifications

- selector source: true device-local users, not `tablet_users`
- current branch behavior:
  - defer from bootstrap
  - run in background after main sync when device users exist
- likely future selector:

```json
{
  "selector": {
    "user": {
      "$in": ["user-a", "user-b"]
    }
  }
}
```

Add recency and unread rules later once the server indexes are ready.

### login_activities

- selector source: true device-local users, not `tablet_users`
- implemented selector field: `user`
- add a recency window once server indexes exist

### chat_history

- selector source: true device-local users, not `tablet_users`
- implemented selector field: `user`

### team_activities / teams

- implemented dependency path:
  - derive device users first
  - read local membership docs for those users
  - derive relevant `teamId`s
  - query `team_activities` with `teamId: { "$in": [...] }`

## Server-side expectations

For this pattern to scale on real data, Planet should add indexes that match the selectors. For `courses_progress`, the first index to add is on `userId`.

Recommended follow-up on the Planet side:

- `courses_progress`: index `userId`
- `submissions`: index `userId`
- `login_activities`: index `user`
- `chat_history`: index `user`
- `notifications`: index `user`
- `team_activities`: index `teamId`

## Known limitations of this prototype

- It moves `courses_progress`, `submissions`, `login_activities`, `chat_history`, and `team_activities` onto device-scoped or team-scoped `_find`.
- Bootstrap deferral is currently policy-based, not server-assisted.
- It does not yet apply recency filtering to activity/history tables.
- It assumes `courses_progress.userId`, `submissions.userId`, `login_activities.user`, and `chat_history.user` are the right first selectors, which still need verification against real server documents and indexes.
- `notifications` are backgrounded rather than fully device-scoped in this branch.
- It was not fully compiled in this environment because Gradle download is blocked by sandbox network restrictions.

## What testing showed

- `courses_progress/_find` works mechanically in `myPlanet`.
- `submissions/_find` can follow the same pattern with an existing `userId` field.
- `login_activities/_find` and `chat_history/_find` fit the same device-user pattern.
- `team_activities` is better treated as a team-scoped sync problem than a raw user-scoped one.
- Replacing `/_all_docs` with `_find` is not enough by itself.
- `tablet_users` cannot be used as the source of truth for device-local sync, because it is an auth/login dataset, not a locality dataset.
- `device_users` is the more viable source of truth for steady-state sync on the tablet.

## Suggested next step for myPlanet team

1. verify the `courses_progress.userId`, `submissions.userId`, `login_activities.user`, and `chat_history.user` selectors against real server documents
2. add the required Mango indexes on the Planet side for whichever selectors are chosen
3. retest bootstrap sync and steady-state sync separately
4. decide whether `notifications` should stay background-only or move to a selector-based steady-state sync
5. keep team-scoped selectors for `team_activities` aligned with the local membership/team derivation path

That sequence should give the biggest practical reduction in sync size before tackling team-scoped tables.
