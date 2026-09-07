# Phase 130 — the tray tap, and a trailing space the derivation ate

Lane C. Two items, both handed over by Phase 127's *Reported, not fixed* list:
a correct handler nothing could reach, and one character the ARB derivation
threw away.

Two `parity-auditor` passes at `effort: max` were run, one on the Kotlin before
any Dart and one on the finished diff. **The first overturned three of the eight
claims this lane had written down**, and one of the three changed the design:
see *What the ground-truth audit corrected*. That is the fourth round running
where a lane's own reading of the Kotlin was wrong in a way only the audit
caught.

---

## Item 1 — the tray tap

### What the port actually raises

One thing. `TaskDeadlineNotifier` (Phase 42) is the port's only producer of an
OS notification, through `NotificationConfig.task`:

```
id        = task.id          type = "task"
relatedId = task.id          actionable = true   (from this phase)
title     = "✅ New Task Assigned"
message   = "<title>\nDue: <EEE dd, MMMM yyyy>"
```

That matches `TaskNotificationWorker` → `NotificationUtils.createTaskNotification`,
and — with one refinement the audit supplied — it is the **only** Kotlin producer
that routes through `NotificationUtils.NotificationManager` and therefore the
only one that carries extras or action buttons. The other five factories
(`createSurveyNotification`, `createJoinRequestNotification`,
`createStorageWarningNotification`, `createResourceNotification`,
`createSummaryNotification`) are called only from `DashboardActivity.kt:214`'s
`state.newNotifications.forEach { showNotification(it) }`, and
`DashboardUiState.newNotifications` has exactly two writers: its own
`emptyList()` default and `clearNewNotifications`. Nothing populates it. So
those five have no live caller, and **`summary_`-prefixed notification ids are
never minted at runtime.**

### What each notification type does on tap

There are three gestures, not one, and the Kotlin gives each a different
behaviour. This is the table the brief asked for; every row is reachable in the
port today.

| gesture | Kotlin path | read-marking | navigates to |
|---|---|---|---|
| the notification **body** | `setContentIntent` → `DashboardActivity` with `from_notification=true` → `handleNotificationIntent`'s first branch | `markNotificationAsRead(id, userId)` — **does not** stamp `createdAt`; `summary_`-aware | the destination for its type |
| **Mark as Read** button | `NotificationActionReceiver` `ACTION_MARK_AS_READ` (`:34-39`) | `markNotificationsAsRead({id})` — **stamps** | nowhere (but see below) |
| **View Task** button | `NotificationActionReceiver` `ACTION_OPEN_NOTIFICATION` (`:51-66`) → `DashboardActivity` with `auto_navigate=true` → the second branch | `markNotificationsAsRead({id})` — **stamps** | the destination for its type |

And the destinations, by type, which is the other half of the question. The port
resolves all of them through the one shared resolver, so this table is
`NotificationDestinationResolver` plus `notificationDestinationLocation`:

| type | port location | Kotlin |
|---|---|---|
| `task` | `/life/teams/<teamId>/tasks` | `TeamDetailFragment(navigateToPage = TasksPage)`, team id from the cached task's `link.teams` |
| `join_request` | `/life/teams/<teamId>/members?tab=requests` | `TeamDetailFragment(JoinRequestsPage)`, after stripping a `join_request_` prefix |
| `team_join` | `/life/teams/<teamId>` | `TeamDetailFragment`, no page |
| `chat` | `/life/teams/<teamId>` | `TeamDetailFragment(ChatPage)` — the port has no team-chat tab, a pre-existing divergence |
| `voice_reply` | `/life/voices/<voiceId>` | `ReplyActivity` |
| `resource` | `/resources` | `ResourcesFragment` |
| `storage` | `/profile/settings/storage` | `ACTION_INTERNAL_STORAGE_SETTINGS`, the OS screen — pre-existing divergence, the port has its own |
| anything else | nowhere | `openNotificationsList(...)` |

Only `task` has a producer. The rest are reachable from the bell row, which is
the path Phase 49 ported and this phase now shares.

### Yes, the resolver was reused — and so was the location mapping

The brief asked for the resolver rather than a second mapping. Two changes make
that literal:

* `NotificationDestinationResolver.resolveFor({type, relatedId})` is split out
  of `resolve(NotificationRow)`, which now delegates to it. A tray tap has no
  row — Kotlin's `auto_navigate` branch switches on the two intent extras alone
  — and the type it carries (`NotificationUtils.TYPE_*`) is already the same
  *resolved* spelling `resolvedNotificationType` produces, so one switch serves
  both callers with nothing translated between them.
* `notificationDestinationLocation` moved out of `notifications_screen.dart`,
  where it was a `switch` inside a private widget method. Phase 124 built the
  row formatter on this same enum for the same reason.

The second move bought something beyond de-duplication: the mapping is now
**enumerable**, which is what lets the reachability guard walk it exhaustively.
See below.

### The three plugin links, and why one design decision changed

`flutter_local_notifications` splits what Android hands to one Activity:

| Kotlin | port |
|---|---|
| `createNotificationIntent`'s three extras | one `payload` string, JSON, using the Kotlin's own extra names (`notification_type`/`notification_id`/`related_id`) so a reviewer can read them side by side |
| `DashboardActivity.onCreate` → `handleNotificationIntent(intent)` | `NotificationTapSource.launchTap()` → `getNotificationAppLaunchDetails` |
| `onNewIntent` → `handleNotificationIntent(intent)` (`:1106`) | `NotificationTapSource.taps()`, from the `initialize` response callback |
| `PendingIntent.getBroadcast` → `NotificationActionReceiver` | `AndroidNotificationAction` + `actionId` on the response |

**The design decision the audit changed.** This lane had read Kotlin's action
buttons as background handlers — they are `PendingIntent.getBroadcast` into a
`BroadcastReceiver`, and *Mark as Read* obviously should not launch an app — and
had planned `showsUserInterface: false` plus a `@pragma('vm:entry-point')`
background response handler with its own `ProviderContainer`, on the model of
`background_entrypoint.dart`. That was wrong.

Every one of the receiver's three arms calls its own
`markNotificationAsRead(context, notificationId)`, and that helper's tail is
unconditional:

```kotlin
val dashboardIntent = Intent(context, DashboardActivity::class.java)
dashboardIntent.action = "REFRESH_NOTIFICATION_BADGE"
dashboardIntent.flags = FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_NEW_TASK
context.startActivity(dashboardIntent)              // :104-112
```

So tapping *Mark as Read* on a task reminder brings myPlanet's dashboard to the
foreground in the Android app, cold-starting it if the process is dead. The
Kotlin's own test concedes it: `NotificationActionReceiverTest.kt:130-132`
captures a *list* of intents and searches it, because `startActivity` is called
twice on the open path.

Both actions are therefore `showsUserInterface: true`, which routes them through
the ordinary response callback in the UI isolate — where the Riverpod graph and
the session already exist. **The background isolate, the second entry point and
the container it would have needed are all unnecessary**, and the simpler design
is the more faithful one. The one thing the port does not reproduce is the
`REFRESH_NOTIFICATION_BADGE` intent itself: the port's bell is a live drift
stream, so there is nothing to refresh.

`cancelNotification: true` on both actions ports the receiver's
`clearNotification(it)`. `autoCancel` covers the body tap only, so without it an
action would mark the notification read and leave it in the tray.

### At parity, and this is the part worth remembering: no tray gesture marks anything read

The read-marking half of all three gestures is inert on real data, in **both**
apps.

`TaskDeadlineNotifier` mints the notification with `id = task.id`, exactly as
`TaskNotificationWorker.kt:48-53` does. But a `task` notification *row* is a
synced CouchDB notification document: its primary key is that document's own
`_id`, and the task id lands in `relatedId`. Concretely — notification doc
`{_id: "n-77", type: "newTask", link: "/teams/view/team-9", …}` beside task doc
`{_id: "t-42", …}` — the tray notification's id is `t-42` and the unread row is
`n-77`. So `markOneAsRead("t-42")` updates zero rows, and `markAsRead({"t-42"})`
early-returns on an empty `getByIds` before it writes anything. The bell badge
does not move.

Resolving `relatedId` back to a notification row would fix it. **That would be
an improvement the Android app does not have**, which makes it a divergence
rather than a repair, so it is reproduced and written down instead — with a test
that asserts the no-op and says why (`at parity: the tray marks nothing read on
real data`). The navigation half works precisely because it reads `relatedId`,
which is why the tap is worth wiring regardless.

Two related dead ends the audit established, both now unreachable rather than
merely uncalled: **nothing stores a row with `type = "task"`** at all
(`parseNotification` writes the server's raw `"newTask"` and `resolveType` maps
it at read time), so `markSummaryAsRead(userId, "task")` could not match a row
even if a `summary_task` id were minted — and none is. The `summary_` branch is
doubly dead. It is still pinned by a test, because it is the one behaviour the
body-tap path has that the action path does not.

### Deliberate divergences, all four of them

1. **The body tap goes where the button goes.** Kotlin's `from_notification`
   branch opens the plain team *list* for a task, because it passes `taskId` to
   `TeamFragment`, which reads only `fromDashboard` and `type` and discards it.
   The same hole is in its survey arm (`SurveyFragment` reads only `isTeam` and
   `teamId`). So Kotlin's branch-1 refinement is non-functional across the
   board, while its `auto_navigate` branch and its in-app row tap both open the
   team's tasks page. Two of three agree and the third disagrees by way of an
   argument nothing reads; the port puts all three in one place. This is the
   brief's instruction and it is also the better reading.
2. **The shared resolver is the forgiving one.** Kotlin's button path
   (`getTaskTeamInfo`) does `teamDao.getById(teamId) ?: return null` and emits
   nothing when the team row is absent, so the tap silently does nothing; the
   row path (`getTaskDetails` + `resolveAndOpenTeam`'s `?: relatedId`) still
   opens something. Sharing one resolver means the tray navigates in a case
   where the Android app does not. Deliberate, and the cheaper failure.
3. **`ACTION_STORAGE_SETTINGS` and the four non-task action arms are not
   ported.** Their factories have no live caller, so porting them adds library
   code with no producer — the rule `notification_config.dart` already states in
   its header. One line each when a producer appears.
4. **An unresolvable type stays where it is** rather than opening the
   notifications list. Same reasoning `deepLinkRoute` gives for its own
   fall-through: yanking the user off the screen they are on is worse than doing
   nothing. Unreachable anyway — the only live producer always sets
   `notification_type = "task"`.

Two Kotlin quirks that need no port because Flutter cannot have them:
`openCallFragment`'s tag semantics can swallow the navigation entirely (looking
at Team A's detail, tap a notification for Team B's task → nothing happens,
because both use the bare `TeamDetailFragment` tag), and the whole slice is
gated on `initializeDashboard()`, so an inactive user's notification tap is
silently ignored in the Android app. The port's scope wraps the navigator and
has neither problem.

### Can the reachability guard cover a tray entry point? Yes — three ways

The brief asked. `test/ui/route_reachability_test.dart` gains three rules,
because a tray tap fails in three independent places and the existing scanner
could see none of them: there is no `context.go` call site to scan for.

1. **`every notification destination resolves to a registered route`** — walks
   `NotificationDestinationKind.values` through `notificationDestinationLocation`
   and checks each against the real route table. Exhaustive over the enum, so a
   new kind cannot be added without an arm (that would not compile) and an arm
   cannot name a path the router does not serve. This is strictly stronger than
   what was there: `notifications_screen.dart` sits in the test's `declared`
   blind-spot map, and while the mapping was a switch inside a private widget
   method the only rule that could reach its arms was "a `Routes.x` mentioned
   anywhere in this file counts as reached", which cannot tell a live arm from a
   dead one. The file stays declared (the argument is still not a literal) with
   its reason repointed at the new rule.
2. **`a system-tray tap on the notification the port raises navigates`** — takes
   the config `TaskDeadlineNotifier` itself builds, runs it through the real
   payload codec and the real handler, and asserts the location resolves. The
   payload is *not* hand-built: Phase 113's lesson is that every fixture
   fabricated the join and that was the symptom, so it comes from
   `NotificationTapPayload.forConfig`.
3. **`every action the notification offers is one the handler knows`** — the
   button ids cross a process boundary (the OS holds them while the app is
   dead), so a rename on one side and not the other is silent, and
   `notificationTapFrom` drops an unknown id rather than crashing. The failure
   mode is a button that does nothing.

Plus a fourth, `the platform wiring a tap depends on is present`, which is a
**source-text** assertion and says so. Three links in the chain are arguments to
a plugin call, and `FlutterLocalNotificationsPlugin` has a private constructor —
it cannot be faked or subclassed from a test. Each of the three (`payload:`,
`onDidReceiveNotificationResponse:`, `NotificationTapScope` mounted in
`app.dart`) was missing before this phase and each alone is enough to make a tap
do nothing. `DeepLinkScope` is checked alongside it: same exposure, and nothing
guarded it.

---

## What the ground-truth audit corrected

Recorded because three of eight were wrong, and the pattern is the one
`CLAUDE.md` keeps warning about.

| this lane's claim | verdict |
|---|---|
| `TaskNotificationWorker` is the **only** live tray producer | **refined.** Three more post notifications: `ServerReachabilityWorker.showServerNotification` (live via `NetworkMonitorWorker`, and tappable — a bare `contentIntent` with no extras, so the tap falls through both branches of `handleNotificationIntent` and is a plain app launch), `DownloadService`/`DownloadWorker` via `DownloadUtils`, and `ResourceViewerFragment`'s "Recording Audio" notification. The defensible claim is the narrower one used above: only `TaskNotificationWorker` goes through `NotificationUtils.NotificationManager`, and so only it carries extras or actions. |
| *Mark as Read* does not launch the app | **overturned.** It does, unconditionally. This changed the design — see above. |
| `parseNotification` writes a `type = "task"` row | **overturned.** It writes the raw `"newTask"`; `resolveType` maps at read time. And the read-marking no-op is broader than this lane had it: not just the body tap, but all three gestures. |
| the body tap's `clearNotification` runs | **refined.** It is always a null-receiver no-op — `notificationManager` is assigned *after* `initializeDashboard()` returns, and `initializeDashboard()` is what calls `handleNotificationIntent`. The notification still disappears, via `autoCancel`. The port matches by having no explicit cancel on that path. |

Four claims were confirmed as written, including the two-branch mutual
exclusivity and that branch 1's extras are read by nothing.

---

## Item 2 — the trailing space, and the key that demonstrates it

Phase 124 reported it and declined on ownership; Phase 127 owned `tool/` and
declined for a better reason — no key it was adding went through that path with
a trailing space, so a fix would have been untested. **The key that demonstrates
it already existed, and was already wrong.**

`app_en.arb` has exactly four values ending in a space, and every one is a label
a value is drawn straight after: `storageRunningLow`, `storageAvailable`,
`selected`, `selectResources`. Android's quoting is how that space survives in
`strings.xml` (`<string name="storage_running_low">"Storage running low: "</string>`),
and all five translated locales carry it.

The two plain-text derivation rules wrote `translated[name]?.trim()`. The
recovery path (`--adopt` → `_proposal`) calls `_mirrorTrailingSpace`. So the two
keys repaired by hand kept their space and the two the tool derived lost it —
**in all five locales, ten values**:

```
ar/storageRunningLow = "التخزين قليل:"          fr/storageAvailable = "Espace de stockage disponible:"
es/storageRunningLow = "Almacenamiento bajo:"   ne/storageAvailable = "स्टोरेज उपलब्ध:"        (…and six more)
```

Both plain-text rules now go through one exported `derivePlainTextValue`, so a
third rule cannot disagree again, and the two keys were deleted and re-derived
with the space intact. The mirror is deliberately **one-directional**: it never
removes whitespace the template does not ask for (which would start writing
trailing spaces into 890 ordinary strings), and it takes the *template's*
decision rather than trusting each translator — `values-ar/select_resources` is
unquoted and carries no trailing space at all, so the mirror supplies it.

**Where the loss is visible, and where it is not.** `formatStorageNotification`
interpolates `'$runningLow $percent%'` — the Kotlin template adds a space of its
own — so its own separator masks the missing one, and `renderNotificationHtml`
collapses the double space the Kotlin ends up with. The guard is therefore on the
derivation's *output* across the shipped locale files, not on one caller: the
next consumer that concatenates without a separator would render
`Almacenamiento bajo:10%`, in a string a translator cannot see is wrong.

The same run picked up two more keys, both real: `playbackSpeed` and
`playbackSpeedValue` derive from the Kotlin `playback_speed` /
`playback_speed_format` and had simply never been through the tool — the phase
that added the English keys did not re-run it. `playbackSpeedValue` is `{speed}x`
in all five locales because the Kotlin string is `%1$sx` in all five: a
multiplier suffix nobody translates. That is the translation, not a missing one.
`placeholder_integrity_test.dart`'s pinned counts move +2 per locale: ar 412,
es 464, fr 411, ne 413, so 413. So the ARB diff is **six** keys per locale, not
four: two re-derived and two newly added. One caveat, since this document has
been wrong about l10n measurement before: `playbackSpeedValue` raises the
"human reviewed" count without adding any translated *text*, because the value
is identical in all six files. It is a real translation of a string nobody
translates, and it is also exactly the kind of number that makes a coverage
figure look better than it is.

---

## Failing-first evidence

**Item 2** was red on the shipped data before a line changed. The guard reads
`app_en.arb` for values ending in a space and checks all five locales:

```
these locale values lost the template's trailing space, so the label runs into
the value it prefixes:
  ar/storageRunningLow = "التخزين قليل:"
  ar/storageAvailable = "التخزين المتاح:"
  es/storageRunningLow = "Almacenamiento bajo:"
  … 10 in total, five locales × two keys
```

**Item 1** is a missing feature, so the pre-fix probe is what a source scan can
prove, and it proved three of four:

```
the notification the port shows carries a payload            RED
the notification the port shows offers its Kotlin actions    RED  (no AndroidNotificationAction anywhere in lib/)
something in lib/ receives a notification response           RED  (nothing hears a tray tap)
markNotificationAsRead has a caller in lib/                  passed — falsely
```

The fourth is worth keeping: it passed because the probe matched a **prose
mention in a comment**, `notifications_provider.dart:47`. Grepped for a real
call, `markNotificationAsRead(` appears only in the repository that defines it.
Phase 127's report was right and the naive probe was not — the same shape as the
`<basename>_test.dart` heuristic `CLAUDE.md` warns about. **Grep for the call,
not the name.** The shipped guard strips comments.

Then every new guard was checked by injecting the defect it claims to catch.
Thirteen injections, thirteen red:

| injection | red |
|---|---|
| a destination arm names a path the router does not serve | 3 (including a rule that already existed) |
| the *View Task* button's id drifts from the handler's | 1 |
| `createTaskNotification` stops being `actionable` | 3 |
| the shown notification carries no payload | 1 (the source-text rule) |
| `NotificationTapScope` dropped from `app.dart` | 1 (the source-text rule) |
| the body tap uses the stamping path | 3 |
| the action buttons use the non-stamping path | 2 |
| *Mark as Read* navigates like the other two | 1 |
| the session is read unwatched instead of awaited | 2 |
| a swipe-away is treated as a tap | 1 |
| an unknown action id is accepted | 1 |
| an empty action id is read as an unknown action | 1 |
| the resolver is fed the notification id instead of `relatedId` | **0 at first** — see below |
| the two entry points made to disagree about a destination | 4 |
| the cold-start tap is never asked for (**shipped by accident, see above**) | 3 |
| the resolved location is computed and thrown away (**shipped**) | 3 |
| the tap stream is never subscribed to | 3 |
| the launch tap is awaited before the subscription is taken | 1 |
| the response handler is nulled (green before the fix) | 1 |
| the permission request leaves `main()` (green before the fix, twice) | 1 |
| a `NotificationTypes` constant with no resolver arm | 1 |
| the format path stops mirroring the trailing space | 1 |

**Three found gaps rather than confirming guards**, which is the point of doing
them. Feeding the resolver `notificationId` instead of `relatedId` left the
whole suite green, because the task factory sets both to the same string and it
is the only producer; the two are not interchangeable
(`createStorageWarningNotification(percent, customId)` sets `id = customId` with
`relatedId = "storage"`), so a `voice_reply` payload whose fields differ now
separates them. And the response-handler and permission-request assertions were
each green under their own defect until narrowed from a *name* to a *value* —
the second one twice. See *What the second audit pass found* above.

Also demonstrated red for item 2: the unit tests on `derivePlainTextValue` with
`_mirrorTrailingSpace` removed, and the file-level guard after re-deriving with
the defect in place.

---

## Crossings

One, listed as the rule requires. `lib/data/local/app_database.dart`'s
`markAsRead` dartdoc said "Selection mode's *mark selected as read* is its one
caller"; the tray action buttons are now a second caller and the comment would
have misled the next reader about which of the two statements the tray uses.
Comment-only, inside the notifications DAO region, no code touched.

---

## What the second audit pass found in this phase's own green code

Seven items, and the pattern `CLAUDE.md` records held again: **an audit of the
ground truth does not audit the implementation.** Everything below was green,
formatted and analyzed when the pass started.

**The one that matters, and it arrived by accident.** The pass mutation-tested
`notification_tap_scope.dart` — deleting the `launchTap()` call, then the
`go(location)` call — and **all 2301 tests stayed green.** Nothing mounted
`NotificationTapScope`, so the two lines that connect the whole chain to the app
were the one part of it with no guard: every other link had one, including a
source-text rule asserting the scope is *mounted* in `app.dart`. What the
mounted widget then does was unchecked. With the first mutation a learner taps a
deadline reminder on a closed app and lands on `/` — the cold-start case is the
*only* one that matters for a reminder raised by a headless worker — and CI is
green.

That is this phase's own subject one layer up, and it cost something to learn:
the mutations were in the working tree when a `git add -A` ran, so `452b9b6`
shipped them and `11d19d9` restored them. **Do not `git add -A` while a
background agent has the tree.** `test/ui/notification_tap_scope_test.dart` now
covers the scope with six tests, following `deep_link_scope_test.dart` (mounted
inside `MaterialApp.router`'s `builder`, because that context is above the
`InheritedGoRouter` and `GoRouter.of` would throw there). Both shipped mutations
go red, as do "never subscribes" and "awaits the launch tap before
subscribing".

**Two guards that passed for the wrong reason**, both in the source-text rule,
both now fixed and re-injected:

* `contains('onDidReceiveNotificationResponse:')` matched the parameter *name*,
  so `onDidReceiveNotificationResponse: null` — which drops every tap that
  arrives while the app is running — left it green. It asserts the value now.
* Nothing checked that anything calls the presenter in the UI isolate. The
  response handler is registered by `initialize`, and the only thing that
  reaches it is `main.dart` asking for the notification permission — move that
  request to a screen, a plausible refactor, and `taps()` is dead for the whole
  process while `launchTap()` keeps working (Android registers *that* handler in
  `onAttachedToEngine`, independent of Dart's `initialize`). A rule now pins the
  call. Its first cut matched the function's own *declaration*, which survives
  the call's removal, so the injection stayed green until it was narrowed to
  `unawaited(_requestNotificationPermission());`. **Two of this phase's
  source-text assertions matched a name where they meant a value** — that is the
  failure mode of the technique, and the reason each one has to be injected
  against.

**A type nothing can reach.** `resolveFor` has no `survey` or `course` arm —
they are two of Kotlin's six `TYPE_*` values and the bell-row handler this
resolver was ported from has neither. Add a survey tray notification and its
payload decodes cleanly, its type is `'survey'`, and `default: return null`
swallows it, with the tray-tap test still green because it drives only
`NotificationConfig.task`. A rule now walks every `NotificationTypes` constant
through `resolveFor` and requires a destination, reconciling its list against
the source file so a constant added there and not here fails first.

**Three comments that stated something false**, each corrected in place rather
than deleted, because each will otherwise be cited:

* *The two Kotlin intents do not carry the same extras.* This lane's comment
  said they did. `createNotificationIntent` (the body tap) puts
  `notification_type`, `notification_id`, `from_notification` and
  `config.extras`; only `createOpenPendingIntent` carries `related_id` at all,
  and the body branch reads the extras instead. Benign for the task factory,
  where `extras["taskId"]` and `relatedId` are the same string, and not in
  general — `createStorageWarningNotification` sets `relatedId = "storage"` and
  no extras.
* *"The plugin does not double-deliver" is true, but not for the reason given.*
  Android's `onNewIntent` both invokes the callback *and* calls
  `setIntent`, and `getNotificationAppLaunchDetails` re-reads that intent every
  time — so a tap already delivered is reported as a launch tap for the rest of
  the process. What makes the port safe is narrower: `_start` runs once, from
  `initState`. That is a live trap, because `OutboxDrainScope` in the same
  directory re-runs on `AppLifecycleState.resumed`; adding the same hook here
  would replay the last tap on every foreground, restamp included. Now
  documented as such, with "de-duplicate on the payload first" attached.
* *A background isolate can localise.* The hardcoded English action titles are
  right because `addNotificationActions` passes literals, not `getString` — but
  the second half of the rationale, "no `BuildContext` for an `.arb` lookup",
  is false: `AppLocalizations.delegate.load(locale)` needs none. Worth
  correcting because the port *does* carry a translated `markAsRead` for the
  bell screen and the next reader will reach for it.

**A fourth derivation path the trailing-space fix missed.** `derivePlainTextValue`
unified the two *plain-text* rules, and its dartdoc claimed no third rule could
disagree. True of a third plain-text rule; the ICU path is a fourth, and
`convertAndroidFormat` still did `_parseAndroidFormat(translation.trim())`.
Unreachable today — no `app_en.arb` value containing a `{` ends in a space — and
closed anyway, because the guard that *would* catch it scans every
trailing-space template key, so the red would have pointed at the test rather
than at the missing mirror. `convertAndroidFormat` mirrors now, with a test on
the shape rather than on live data.

**And one thing the pass confirmed rather than overturned**, which is worth
recording because it was the design's biggest unknown: `notificationTapFrom`
drops nothing Android actually delivers. `extractNotificationResponseMap` reads
`actionId` from an intent extra the content intent never sets, so a body tap's
`actionId` is genuinely null and never empty; the three response types are as
assumed. The dismissal arm is unreachable for a different reason than the
comment claimed — the plugin only attaches a delete intent when
`dismissIsolate` is set, which the presenter never does — so the guard is
defensive, and kept, with the sentence fixed.

All six divergences this lane claimed were challenged and all six held.

---

## A second dead row, found by sweeping this lane's own files

The phase's own theme, applied to the rest of the file it was working in: every
public member of `NotificationsRepository` was grepped for a **call** in `lib/`
(not a mention — see the false pass in *Failing-first evidence*). Thirteen have
callers. One does not.

**`updateResourceNotification` has no production caller, and cannot get one.**
Six tests call it, including a round-trip test and a screen test, so it is
ported, tested, green and dead — the exact row this phase was opened to close,
one method further down the same file.

In the Android app it is live. `DashboardViewModel.updateResourceNotification`
(`:134-137`) reads `resourcesRepository.countLibrariesNeedingUpdate(userId)` and
hands the count over; `checkAndCreateNewNotifications` (`:354-362`) calls it, and
that is reached from `DashboardActivity` on dashboard load
(`checkIfShouldShowNotifications`, `:690-698`) and again on the throttled
focus path (`:623-630`). So the Android bell carries a *"You have N
undownloaded resources"* row and the port's never does.

It is not one missing call site. **The count has no port at all**:
`countLibrariesNeedingUpdate` → `MyLibraryDao.countPublicNeedingUpdateForUserPattern`
(`MyLibraryDao.kt:124`) is `isPrivate = 0 AND (userId IS NULL OR userId NOT LIKE
:userPattern)` — the catalog predicate Phase 97 already ported for
`watchResources`, counted rather than listed — and nothing in `flutter/lib`
computes it under any name.

Two consequences beyond the missing row. `notification_format.dart`'s `resource`
arm reads `kotlinToIntOrNull(message)`, which only succeeds on the bare count
this writer stores; a server-sent `newresource` notification resolves onto the
same type but carries prose, so it falls through to the message verbatim. That
branch — and `resourceNotificationMessage` with it — is therefore dead in
production too, and its tests pass because they build the row directly. And the
bell's unread count differs from the Android app's by however many resources
need updating.

Not fixed here, and deliberately: the fix needs a `resourcesRepository` count
plus a dashboard-load hook, which lands in `lib/providers/dashboard_providers.dart`
and `lib/repository/resources_repository.dart` — neither in this lane's file set,
and the dashboard is a collision surface. It is a small vertical slice for
whoever takes it, and the three pieces are named above.

---

## Reported, not fixed

1. **No tray gesture marks anything read**, in either app, because the
   notification's id and the notification row's id are different documents.
   Explained above, tested, and deliberately not repaired — the fix would be an
   improvement the Android app lacks. If it is ever wanted, the change is one
   line in `NotificationTapHandler`: resolve `relatedId` to a row id before
   marking. It should be taken *together* with the Kotlin, not in the port
   alone, or the two apps' bell badges will disagree.
2. **The Kotlin's `isNotified` is reset by every tasks sync-in, and the port
   already diverges.** `TeamTask.fromJson` never sets the flag, so
   `bulkInsertTasksFromSync`'s `upsertAll` writes the default `false` over a
   locally-set `true` — the "sync-in rewriting a locally-authored column" class,
   in the Android app. Consequence there: dismiss a deadline reminder, let the
   process die, sync, and the same task notifies again. **The port does not have
   it** — `TeamTasksRepository.insertTasksFromSync` never writes `isNotified`,
   deliberately, and its dartdoc says why. Recorded here because the difference
   is easy to mistake for a port bug when comparing the two apps' reminders, and
   because it is the one place this slice is *better* than the Kotlin rather than
   merely different. Nothing to do.
3. **`TaskDeadlineNotifier` discards `show`'s boolean**, so a task suppressed by
   a disabled channel is still marked notified and never notifies again. At
   parity (`TaskNotificationWorker.kt:54` discards it too) and left alone.
4. **`canShowNotification` is not ported at all.** Kotlin gates on
   `areNotificationsEnabled()` plus five `SharedPreferences` flags under
   `"notification_preferences"` — and nothing in the app ever writes those
   flags, so they are permanently their `true` defaults and there is no settings
   surface for them. Porting them would add five dead preferences. The
   app-level gate is what `flutter_local_notifications` enforces anyway. Neither
   app checks per-*channel* disablement, which is the case that actually
   silently drops a notification.
5. **A tap taken while signed out is lost**, where `DeepLinkScope` persists a
   pending section link across the sign-in it triggers. The router's redirect is
   `if (!isSignedIn) return Routes.login` (`router.dart:211-213`), so the
   handler's `go(location)` is overridden. The window is narrow — the body-tap
   arm awaits `sessionProvider.future` before navigating, so a *restoring*
   session is waited out rather than raced, and only a genuinely signed-out user
   loses the tap — and Kotlin loses it too, by hanging the whole slice off
   `initializeDashboard()`. Reachable only for a user who signed out between the
   reminder being raised and being tapped, which needs a signed-in user for the
   reminder to exist at all. Persisting it would mean a `pendingNotificationTap`
   preference on the `DeepLinkHandler.takePendingLocation` model.
6. **`markSummaryAsRead` remains unreachable in production** — it now has a
   caller (the body-tap arm) but no producer mints a `summary_` id, and no row
   carries a resolved `type` that a summary id could name. Pinned by a test as
   the one behaviour distinguishing the two read paths.
7. **Somali and Nepali still carry no value for the two repaired keys' 459
   siblings.** Unrelated to this phase, and the l10n row of the migration table
   is the one that most needs a human pass rather than another derivation run.
