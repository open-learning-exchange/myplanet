# Phase 131 — the resource-update notification row, and the brief that was wrong about it

Lane A. One item from Phase 130's *Reported, not fixed* list: the Android bell
carries a *"You have N resources not downloaded"* row and the port's never did,
because `NotificationsRepository.updateResourceNotification` was ported with six
tests and **no production caller** — the "ported, tested, green and dead" class.

Two `parity-auditor` passes at `effort: max` were run, one on the Kotlin before
any Dart and one on the finished diff. **The first overturned the brief's central
factual claim and caught a defect this lane had just written**, which is the
fifth round running that a lane's own reading of the Kotlin needed the audit to
correct.

---

## What the ground-truth audit corrected

### The predicate is the shelf, not the catalog

Phase 130's note — and this lane's brief, quoting it — said
`MyLibraryDao.countPublicNeedingUpdateForUserPattern` is
`isPrivate = 0 AND (userId IS NULL OR userId NOT LIKE :userPattern)`, "the
catalog predicate Phase 97 already ported for `watchResources`, counted rather
than listed."

It is not. `MyLibraryDao.kt:119-124` is:

```sql
SELECT COUNT(*) FROM my_library WHERE isPrivate = 0
  AND userId LIKE :userPattern ESCAPE '\'
  AND (resourceOffline = 0
       OR (resourceLocalAddress IS NOT NULL AND _rev IS NOT downloadedRev))
```

`userId LIKE` — the **My Library shelf**, plus a third clause the brief did not
mention at all. The catalog predicate `(userId IS NULL OR userId NOT LIKE …)`
lives in `getPublicNotUserPattern` at `MyLibraryDao.kt:126-130`, the very next
method. A reader who scrolled two lines too far wrote the note, and it was
repeated verbatim into a brief a round later.

The difference is not cosmetic: under the catalog reading the row would have
counted resources the user has never added and excluded every resource they
have, which is the opposite of what the notification means. It also decides
where the tap lands — `openMyFragment` (`DashboardActivity.kt:951-959`)
overwrites the fragment arguments with `isMyCourseLib = true`, so the Android
tap opens **My Library**, consistent with a shelf-scoped count.

`test/repository/resources_needing_update_count_test.dart` pins the distinction
with a fixture that counts 1 under the shelf predicate and 2 under the catalog
one, so re-seeding the mistake fails the suite.

### The third clause, and why `IS NOT` is load-bearing

`resourceOffline = 0 OR (resourceLocalAddress IS NOT NULL AND _rev IS NOT
downloadedRev)` — never downloaded, or downloaded and stale. `downloadedRev` is
stamped from `_rev` when the file lands (`ResourcesRepositoryImpl.kt:369,390,404`)
and then stays put while `_rev` follows the sync, which is what makes the
comparison mean anything.

SQLite's `IS NOT` is the **null-safe** inequality. The table the audit produced,
for `resourceOffline = 1` with a non-null local address:

| `_rev` | `downloadedRev` | counted |
|---|---|---|
| `3-abc` | NULL | **yes** |
| NULL | NULL | no |
| `4-def` | `3-abc` | yes |
| `3-abc` | `3-abc` | no |

Row 1 is the one a mis-port loses. Written `_rev != downloadedRev` the
comparison is SQL NULL, hence falsy, and every row an older build marked offline
without stamping `downloadedRev` silently drops out of the count. Following
Phase 122's practice the test was mutation-checked: swapping `IS NOT` for `!=`
in the query turns **exactly one** test red, the one written for it. A test that
cannot fail reads as coverage without being any.

One exclusion neither the brief nor this lane had listed, and the audit did:
`resourceOffline = 1` with a NULL `resourceLocalAddress` is never counted,
whatever the revisions say — `resource_local_address IS NOT NULL` is an `AND`
*inside* the `OR`, not a third top-level case.

### The "throttled focus path" is a data-change path

The brief called `DashboardActivity.kt:623-630` "the throttled focus path". It
is `onRealmDataChange`, reached from `setupDashboardDataObserver` (`:591-595`)
collecting `dashboardDataFlow` — a **data-change** path. `onResume`
(`:1097-1101`) calls only `checkNotificationPermissionStatus()` and
`updateLastSyncStatus()`, and the audit established that
`checkNotificationPermissionStatus` cannot reach `onNotificationPermissionGranted`
(`BasePermissionActivity.kt:438-453` only ever calls
`onNotificationPermissionChanged(false)`), so resume is not even an indirect
trigger through it.

The three real trigger points:

1. **load** — `initializeDashboard` → `binding.root.post { checkIfShouldShowNotifications() }`
   (`:193`, `:690-699`), gated `fromLogin || !notificationsShownThisSession`,
   after `delay(1000)`;
2. **data change** — `:591-595` → `:622-630`, gated on the same flag, throttled
   to once per 5000 ms;
3. **notification permission granted** — `:1116-1121`, gated, unthrottled.

The audit then supplied the thing that actually decides the port's design, and
it is a *lifecycle* fact rather than a call site: `collectWhenStarted` is
`repeatOnLifecycle(STARTED)` (`FlowExtensions.kt:38-43`), and a Room `Flow`
emits its current result on collection. Foregrounding the dashboard restarts the
collection and re-runs the check. So the Android app *does* refresh on
foreground — through lifecycle-scoped re-collection, not through `onResume`.

That is why the port watches a drift count stream rather than firing once on
mount: a `StreamProvider` scoped to the mounted dashboard is the same shape, and
it is the substitution the port already makes for
`RealtimeSyncManager.dataUpdateFlow` throughout (`watchResources` says so at its
own declaration). Two of `dashboardDataFlow`'s four merged flows are
`my_library` queries, which is precisely the invalidation `readsFrom:
{myLibraryTable}` subscribes to.

Three omissions, each deliberate and none an invention:

* **no 1000 ms delay** — it lets the Kotlin dashboard draw before a DB round
  trip; a drift stream's first emission is already off the first frame;
* **no 5 s throttle** — it guards storming Realm change callbacks, and the write
  is already a no-op on an unchanged count;
* **no permission trigger** — it exists so Kotlin can raise an OS notification
  it suppressed, and `createResourceNotification` has no production caller in
  the Android app either (`DashboardUiState.newNotifications` has no writer;
  `PHASE_130_NOTES.md` found the same thing from the other end).

### The phase title was wrong, and so was the row it described

The brief calls the string *"You have N undownloaded resources"*.
`values/strings.xml:658` is `You have %1$d resources not downloaded`, which is
what the port's `resourceNotificationMessage` already says in all six locales.
It is a plain `<string>`, **not** a `<plurals>`, so Kotlin renders "You have 1
resources not downloaded" at a count of one. The port's ARB entry is a simple
`{count}` placeholder rather than a plural, so it reproduces the quirk — worth
recording, because it looks like a bug to fix and fixing it would be a
divergence.

---

## The defect this lane wrote, caught by the audit and demonstrated failing

The first cut put `ref.watch(resourceUpdateNotificationProvider(session.id))`
next to the unread-badge read at the top of `HomeScreen.build` — **above** the
`if (isInactive) return const InactiveDashboardScreen();` early return.

`DashboardActivity.kt:165-182` calls `handleGuestAccess()` at `:169` and
`return@launch`es **before** `initializeDashboard()` at `:176`. Despite its name
`handleGuestAccess` (`:301-315`) gates on `rolesList.isEmpty() && userAdmin !=
true` — the *inactive* condition, not guest. So for an inactive user none of the
three triggers ever runs: no data observer, no notification check, no badge.

Placed above the return, the port authored a row the Android app never writes,
for the one user who has no bell to see it in. `test/ui/resource_notification_reachability_test.dart`'s
*an inactive user gets no resource notification* failed on that code:

```
Expected: null
  Actual: NotificationRow:<NotificationRow(id: org.couchdb.user:inert:resource:count, …
00:02 +2 -1: an inactive user gets no resource notification [E]
```

Moving the watch below the early return makes it pass, and a sibling test pins
the other half of the same gate: a **guest** carries `roles: ["guest"]`, so
`rolesList` is non-empty, the guest falls through to the full dashboard, and
does get the row. There is no guest gating on this path in either app.

## Failing-first evidence for the phase itself

Before any production change, both reachability tests failed on the dead row:

```
Expected: not null
  Actual: <null>
   … the dashboard must author the resource-update notification
```

The second test is the one that answers the brief's third question rather than
assuming it. `notification_format.dart`'s `resource` arm reads
`kotlinToIntOrNull(message)`, which only succeeds on a bare integer; the test
takes the row **the live writer produced** and asserts it formats to
`You have 3 resources not downloaded`. A fixture-built row could not have shown
that the writer and the reader agree — which is the shape of Phase 74's
reactions and Phase 100's verification photo, where each half passed alone.

## What was ported

| piece | where |
|---|---|
| `likeEscapedUserPattern` — `ResourcesRepositoryImpl.userIdPattern` (`:64-70`) | `lib/data/local/app_database.dart`, above `MyLibraryDao` |
| `countResourcesNeedingUpdate` / `watchResourcesNeedingUpdateCount` — `countPublicNeedingUpdateForUserPattern` (`MyLibraryDao.kt:119-124`) | `MyLibraryDao`, after `resourcesOnShelf` |
| `countLibrariesNeedingUpdate` (`:213-216`), null guard included | `lib/repository/resources_repository.dart`, after `localCount` |
| `DashboardViewModel.updateResourceNotification` (`:134-137`) + its trigger points | `resourceUpdateNotificationProvider`, `lib/providers/dashboard_providers.dart` |
| the call site | `lib/ui/dashboard/home_screen.dart`, **below** the inactive-user return |

`NotificationsRepository.updateResourceNotification` itself needed no change:
the audit read the Kotlin in full (`NotificationsRepositoryImpl.kt:51-80`) and
the port already matches it — the `<userId>:resource:count` id, the bare count
as the message, `relatedId` set to the same count (nothing reads it), the hard
`deleteById` at zero, and unread + restamped on a change **in either
direction**. That last one matters: a count dropping 5 → 3 re-marks the row
unread in both apps, and the zero case is a delete, so a user who reads the
notification at 3, downloads all three, then adds two more gets a *new unread*
row rather than a re-marked one.

### Quirks reproduced rather than fixed

* **The null guard is `== null`, not `isNullOrBlank`**, unlike the sibling
  `getMyLibrary`/`getMyLibraryFlow`. A blank user id therefore reaches the query
  as the pattern `%""%`, matching nothing (a serialized `["a","b"]` renders its
  separator `","`, never `""`). Pinned as 0-either-way so the difference reads
  as deliberate.
* **A user id containing a backslash can never match — in either app.** The
  column holds a JSON list, so `a\b` is *stored* as `["a\\b"]`; the pattern goes
  the other way, escaping for `LIKE` and being unescaped again by `ESCAPE '\'`,
  so it looks for a single backslash. Kotlin's Gson converter and `userIdPattern`
  produce exactly the same mismatch. `_` and `%` are **not** JSON-escaped, so
  those two escape correctly and are tested doing so. Unreachable in practice
  (`org.couchdb.user:<name>`), and the fix would have to be made in both apps at
  once — see *Reported, not fixed*.

## Regions touched

* `lib/data/local/app_database.dart` — one new top-level function above
  `MyLibraryDao`, two new methods plus one private helper inside it, after
  `resourcesOnShelf`. **No schema change and no `schemaVersion` bump**: a
  counting query adds no DDL.
* `lib/repository/resources_repository.dart` — two methods after `localCount`.
* `lib/providers/dashboard_providers.dart` — one provider after
  `myLibraryStreamProvider`.
* `lib/ui/dashboard/home_screen.dart` — one `ref.watch` immediately below the
  `isInactive` early return in `build`.
* `test/ui/home_screen_test.dart`, `test/ui/inactive_dashboard_screen_test.dart`
  — one override each in the existing harness builders (see below).
* new: `test/repository/resources_needing_update_count_test.dart` (17),
  `test/ui/resource_notification_reachability_test.dart` (4).

### A note for anyone writing tests against this screen

Adding a live drift stream to `HomeScreen` turned **25 existing tests red at
once**, all with `'!timersPending'` and none with a failed expectation.
Cancelling a drift query stream schedules a zero-duration timer through
`StreamQueryStore.markAsClosed`; if the `ProviderScope` is disposed by the
harness backstop *after* the body returns, that timer is still pending and the
binding asserts. `team_voices_screen_test.dart:40-43` had already documented
this, which is why every other screen test overrides its streams.

Two ways out, and this phase uses both deliberately: the existing harnesses
override the new provider (they are not testing it), and the reachability file —
which must run the real stream — unmounts the tree and settles *inside* the test
body, then reads the database, which outlives the widget. It belongs with the
`pumpAndSettle` traps `CLAUDE.md` already lists for the resource viewer and the
exam screen: **a failure that names a timer is the harness, not the behaviour.**

---

## Reported, not fixed

1. **`MyLibraryDao.watchResources` does not escape its `LIKE` pattern**, where
   the count added here does and the Kotlin does throughout. It interpolates
   `'%"$shelfUserId"%'` directly, so a user id containing `_` (LIKE's
   single-character wildcard) matches another user's shelf entry, and one
   containing `%` matches almost anything. The file is in this lane's set but
   the change is outside this phase: it moves catalog/shelf membership for every
   consumer of `resources_providers.dart`, which is not, and it wants its own
   failing-first test per call site. `likeEscapedUserPattern` is now available
   for whoever takes it. Same applies to `resourcesOnShelf` on the line above.
2. **The JSON/LIKE escaping mismatch is a genuine two-app bug**, described
   above. Nobody should fix it in the port alone: the two apps would then
   disagree about shelf membership for the affected ids. It needs escaping
   against the stored JSON form (`jsonEncode(userId)` minus its brackets) in
   `ResourcesRepositoryImpl.userIdPattern` and here at the same time.
   Unreachable today.
3. **`MyLibraryTable.userId` is non-nullable with a `'[]'` default**, so
   `watchResources`' catalog arm `r.userId.isNull() | …` has a branch that can
   never be true. Harmless — `'[]'` matches no `%"x"%` pattern — but it is dead
   SQL that reads as a ported condition. Not touched because changing it is a
   schema question.
4. **The Kotlin's inactive-user gate silences the whole bell, not just this
   row.** For `rolesList.isEmpty() && userAdmin != true` there is no data
   observer, no notification check and no badge, because `handleGuestAccess`
   returns before `initializeDashboard`. The port's `InactiveDashboardScreen`
   reaches the same outcome by having no bell at all. Recorded because it is not
   obvious from the method name, and because the next person to add a
   dashboard-load side effect will face the same placement question this phase
   got wrong on the first try.
5. **`updateResourceNotification` upserts on an unchanged count where the port
   returns early.** Kotlin rewrites `message` and `relatedId` to the same values
   and leaves `isRead`/`createdAt` alone; the port skips the write entirely. The
   observable state is identical unless a row's `relatedId` has drifted from its
   `message`, which no writer can produce. Left alone — the method has six tests
   and is not this phase's to churn.
6. **`relatedId` on this row is the count, not a resource id**, in both apps,
   and nothing reads it (`NotificationsFragment.kt:128` is
   `"resource" -> openMyFragment(ResourcesFragment())`). It is a field carrying
   a value that looks like an identifier and is not one. Recorded for whoever
   next reads a `relatedId` generically.
7. **The Android tap opens My Library, the port's opens the catalog.**
   `openMyFragment` overwrites the fragment arguments with `isMyCourseLib = true`
   (`DashboardActivity.kt:951-959`); the port's resolver maps `resource` to
   `/resources`, which is the catalog unless `resourceShelfOnlyProvider` happens
   to be set. Now that the row exists this is reachable, where before it was
   dead either way. The change is one line in
   `lib/ui/notifications/notification_destination.dart` — **not in this lane's
   file set**, which is why it is here rather than in the diff. It should
   probably set the shelf toggle the way `_openLibraryCard` does, since the
   count it comes from is shelf-scoped.
