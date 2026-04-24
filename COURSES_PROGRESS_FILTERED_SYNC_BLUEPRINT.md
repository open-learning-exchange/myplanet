# courses_progress filtered sync prototype

This prototype changes `myPlanet` full sync so `courses_progress` is no longer fetched with:

- `/_all_docs?include_docs=true`

Instead it now:

1. syncs `tablet_users` first
2. derives synced local user ids from `UserRepository`
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

- establish dependency ordering first
- derive the relevant local scope
- replace `/_all_docs` with paged `_find`
- insert the returned docs using the existing repository bulk-insert path

## Files changed

- `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`

## Pattern to copy for other databases

### submissions

- selector source: synced local user ids
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

- selector source: synced local user ids
- likely selector:

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

- selector source: synced local user names or ids, depending on stored field
- add a recency window once server indexes exist

### team_activities / teams

- these need a second dependency step:
  - sync membership docs or teams first
  - derive relevant `teamId`s
  - then query `team_activities` with `teamId: { "$in": [...] }`

## Server-side expectations

For this pattern to scale on real data, Planet should add indexes that match the selectors. For `courses_progress`, the first index to add is on `userId`.

Recommended follow-up on the Planet side:

- `courses_progress`: index `userId`
- `submissions`: index `userId`
- `notifications`: index `user`
- `team_activities`: index `teamId`

## Known limitations of this prototype

- It only changes `courses_progress`.
- It does not yet apply recency filtering.
- It assumes `courses_progress.userId` matches synced local user ids stored in `RealmUser`.
- It was not fully compiled in this environment because Gradle download is blocked by sandbox network restrictions.

## Suggested next step for myPlanet team

1. verify the `courses_progress.userId` field against a real server dataset
2. test sync timing before/after on a heavier Planet
3. extend the same pattern to `submissions`
4. then extend to `notifications`

That sequence should give the biggest practical reduction in sync size before tackling team-scoped tables.
