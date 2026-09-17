# Phase 116 — the reachability audit

Phase 113 found `TakeExamScreen` **unreachable in production** — not broken,
unreachable. Its route was fine, its screen was fine, its tests were green, and
no live path led to it: `CourseMapper` dropped the step's embedded exam, so the
join `exams.stepId == course_steps.id` could never match real synced data. Three
phases of correct exam work (the Phase 100 verification photo, the Phase 106
choice shape, the Phase 110 retry model) were guarding a button that could not
appear. Phase 108 separately found `Routes.userInfo` declared and routed with
nothing navigating to it.

**Green tests cannot detect this class.** A screen test builds its screen
directly; a repository test builds its own rows. The route table and the
navigation calls were only ever exercised one at a time, and a fixture that
fabricates a join makes the test pass and the feature dead. This phase audits
reachability across the port and leaves guards behind, because a guard is worth
more than any individual fix.

Two questions, asked of every screen:

1. **Route reachability.** Does a live path exist from an entry point — a tap, a
   deep link, a notification — to this screen?
2. **Data-path reachability.** Does the table the screen reads ever get written
   in a shape its predicate can match, by a real writer, from a real document?

---

## Part 1 — route reachability

### What was found

Six defects, every one demonstrated failing on the pre-fix code by
`test/ui/route_reachability_test.dart` before the fix landed.

| # | screen | defect | severity |
|---|---|---|---|
| 1 | `AddResourceScreen` | route registered as top-level `'add'` — matched nothing, all 3 callers hit the error page | **dead** |
| 2 | `WebViewScreen` | route registered as `Routes.webView.replaceFirst('/', '')` — same shape | **dead** |
| 3 | `TeamLeaderboardScreen` | route registered, string translated, nothing navigates | **dead** |
| 4 | `ChatDetailScreen` (new chat) | `context.push(Routes.chat)` pushes the *pattern* `/life/chat/:chatId` | latent |
| 5 | `CommunityBottomSheet`, `CommunityScreen(fromLogin: true)` | the login-screen community entry point was never ported | **dead** (reported) |
| 6 | `CustomDropdownButton`/`AlwaysNotifyDropdown`, `CheckboxList`/`CheckboxStringList` | component widgets with zero references in `lib/` or `test/` | dead code (reported) |

### 1 and 2 — a top-level route's path is its whole path

```dart
GoRoute(path: 'add',                              builder: … AddResourceScreen …),
GoRoute(path: Routes.webView.replaceFirst('/',''), builder: … WebViewScreen …),
```

Both sat in the **top-level** `routes:` list. go_router 14.8.1 has no assert
against a relative path there (`configuration.dart:35-50` checks only for a
trailing `/`), and `debugKnownRoutes()` prints them as `/add` and `/web-view`
because it concatenates onto the empty parent path — so the route table *looks*
right. The matcher does not concatenate: `RouteMatchBase._matchByNavigatorKey`
starts a top-level route with `remainingLocation = uri.path`, i.e. `/resources/add`,
and matches the raw pattern `add` against it. It matches nothing at all — not
`/resources/add`, and not `/add` either.

Measured on the pre-fix router, through `router.configuration.findMatch`:

```
/resources/add     -> error=true
/web-view          -> error=true
/add               -> error=true
add                -> assertion: 'uri.path.startsWith(newMatchedLocation)'
```

So every caller landed on go_router's default error page:

- `resources_screen.dart:139` — the My Library add-resource FAB;
- `resource_detail_screen.dart:98` — the resource edit button;
- `community/services_screen.dart:61` — every external link in the community
  services list.

**`AddResourceScreen` has been unreachable for as long as it has existed.**
Phase 72 ported it (create, edit, file pick), Phase 102 gave it 33 tests and
closed seven defects in it — including one about *which screen the FAB appears
on* — and none of that work could ever run. It is the exam defect again, one
layer up: the screen was audited, tested and fixed while nothing could open it.
`WebViewScreen` (Phase 73) is the same.

Fixed in `lib/ui/router.dart` by giving both routes their own constant.

### 3 — a screen with a route, a string and no link

`/life/teams/:teamId/leaderboard` was registered and matched fine.
`TeamLeaderboardScreen`, `TeamLeaderboardCalculator`, the route and the
`leaderboard` string (translated in all six locales) all landed in Phase 73, and
nothing in `lib/` ever navigated there. `teams_screen.dart`'s team-detail list
links to tasks, members, calendar, courses, finances, plan, reports, resources,
surveys and voices — every sibling but this one.

It has no Kotlin counterpart on master (`grep -rn leaderboard app/src/main` is
empty) — Phase 73 took it from the unmerged `14880` branch — so there is no tab
position to copy. A tile next to the member list is where it belongs.

**Gated on `canView`, and the first cut was not.** The second pass caught it:
`watchMembers` is `watchTeamDocuments(teamId, 'membership')` with no viewer
predicate, so an ungated tile put a non-member of a *private* team in front of
its full roster with each member's course, survey and visit counts. Kotlin's
non-member page set is Plan and Members and nothing else. `team_detail_gates_test`
now lists `Leaderboard` among its gated entries, which is what makes that test's
title (`a non-member of a private team sees only Plan and Members`) true of the
leaderboard rather than merely unfalsified.

### 4 — pushing a route pattern

`chat_history_screen.dart:48`, the New chat button:

```dart
ref.read(chatConversationProvider.notifier).startNewChat();
context.push(Routes.chat);          // '/life/chat/:chatId'
```

go_router matches `:chatId` against the literal text `:chatId`, so this
*succeeded* — and opened `ChatDetailScreen(chatId: ':chatId')`, whose `initState`
then ran `loadChat(':chatId')`, undoing the `startNewChat()` two lines above it.
It worked only because `loadChat` (`chat_provider.dart:112-115`) finds no row
with that id and returns without touching state.

This is Phase 113's defect C exactly, and the same file already carries a comment
about it at its *other* tap site (`:179-184`) — the tap-a-conversation path was
fixed and the New chat path beside it was not. Fixed with a `/life/chat/new`
route registered ahead of `:chatId`, mirroring `calendar/events/new` ahead of
`:meetupId`. Safe against collision: a chat id is the CouchDB `_id` the server
assigns (`chat_repository_impl.dart:106-117` fails rather than mint one), so
`new` is not a value the `:chatId` route can be asked for.

### 5 — the login screen's community entry, never ported

`CommunityBottomSheet` (`community_screen.dart:114`) is the port of
`HomeCommunityDialogFragment.kt`, and **nothing constructs it — in `lib/` or in
`test/`.** Kotlin shows it from `LoginActivity.setupAdditionalListeners`
(`LoginActivity.kt:188-196`): an `openCommunity` button, visible whenever the
configured URL is non-empty and not `/db`. The port's `login_screen.dart` has no
such button.

The same gap kills `CommunityScreen(fromLogin: true)` — the three-tab login
variant. The router builds `fromLogin: false` and that is the only construction
anywhere, so `_loginTabCount`, and both `if (!widget.fromLogin)` branches, are
dead. `communityId` is likewise never passed.

**Reported, not fixed.** Adding the button plus its visibility gate plus the
bottom sheet's presentation is a port of a Kotlin screen affordance, not a defect
fix, and this is an audit lane. It is a small, well-specified phase: the widget
already exists and the Kotlin gate is two lines.

### 6 — component widgets nothing calls

Zero references in `lib/` and zero in `test/`:

- `lib/ui/components/custom_dropdown.dart` — `CustomDropdownButton`,
  `AlwaysNotifyDropdown` (CLAUDE.md already records `CustomDropdown` as
  uncalled; this confirms both classes and adds that no test covers them either)
- `lib/ui/components/checkbox_list.dart` — `CheckboxList`, `CheckboxStringList`

Reported. Deleting them is a judgement about whether a later phase wants them,
not a reachability fix.

### What is reachable

Everything else. The sweep is mechanical rather than narrative, and it is in the
guard: `test/ui/route_reachability_test.dart` builds the real router, reads every
`Routes` constant and every navigation location out of `lib/` source, and asserts
they meet. After the fixes above, all six rules pass with an allowlist of four
redirect targets, the public-survey deep link, and `/exam/user-info/:submissionId`
(reachable, but through `Navigator.push` from `PublicSurveyScreen` rather than a
location — Phase 108's finding, now recorded as deliberate rather than lost).

Deep links and notification taps are covered too. All five `deepLinkRoute`
sections and `DeepLinkHandler.publicSurveyLocation` resolve, and the seven
notification destinations resolve through the scanner's literal and
bare-constant rules — five as `Routes.`-interpolated strings, two as bare
constants, all of them reached from `context.go` through a variable.

### The guard

`test/ui/route_reachability_test.dart`, six tests, four rules:

1. **Every `Routes` constant resolves to a registered route.** Catches 1 and 2.
2. **Every navigation location in `lib/` resolves.** Catches 1 and 2 at the call
   site, and anything whose target drifts from the route table later.
3. **No navigation carries an unsubstituted `:param`.** Catches 4, and Phase
   113's defect C.
4. **Every registered route is navigated to from somewhere**, with an allowlist
   whose entries each carry a reason. Catches 3, and makes adding an unreachable
   route a deliberate act rather than an accident.

It is **source-derived, not a hand-written list**: it parses the `Routes`
constants out of `router.dart` and scans every `.dart` file under `lib/` for
navigation targets, resolving `${Routes.x}` interpolations and standing a
placeholder in for runtime ids. A route or a `context.push` added in a later
phase is covered without anyone remembering to come back here — which is the
whole point, since the reason this class survived so long is that nothing
connected the two halves.

What the scanner cannot read, it says so: `_unresolvedNavigations` feeds a
fifth test whose allowlist names the three call sites that build a location out
of a variable, with the reason for each. That channel exists because the second
pass (below) proved the first cut of this guard had the failure it was written
to catch — call sites dropped in silence.

---

## Part 2 — data-path reachability

The harder half, and the one the exam defect belonged to. For each screen: which
table does it read, through what predicate, who writes those columns, and can a
writer's value satisfy the reader's predicate on a document shaped like the
server's?

Four `parity-auditor` passes at `effort: max` covered the port's screens in
disjoint groups. **Every claim below marked ✔ I re-verified myself** by grep or
probe against the source; the rest carry the auditing pass's evidence and are
flagged as such.

### The audit table

Verdicts: **live** — proved end to end on server-shaped data. **suspect** — the
port's predicate is narrower than Kotlin's and I could not obtain a real
document to settle it. **dead** — no writer can satisfy the reader.

| Screen | Reads | Predicate | Verdict |
|---|---|---|---|
| resources (catalog), filter sheet, collections | `my_library`, `tag` | `is_private = 0 AND user_id NOT LIKE …` | live |
| resources (My Library), courses (My Courses), courses progress, home library/course cards, completed-course stars | `my_library`/`courses`.`user_id` | `LIKE '%"<uid>"%'` | **dead** — D1, D2 |
| add resource | writes `my_library` | — | **dead** — D3 |
| resource detail — rating card; course detail rating | `ratings` | `type = ? AND item = ?` | **dead** — D4 |
| resource viewer, path viewer, web view | `my_library.filename` + disk | `ole/<couchId>/<filename>` | suspect — D14 |
| courses list/detail, take course, **step exam + step survey** | `courses`, `exams`, `surveys` | `step_id = '<courseId>:<i>'` | **live** ✔ (Phase 113 re-verified end to end) |
| take exam | `exams`, `exam_questions`, `certifications` | `id`, `exam_id`, `courseIds LIKE` | live |
| submissions list + detail; home pending-survey dialog | `submissions` | `user_id = '<uid>'` | **dead** — D5, D6 |
| notifications list, grouping, tap destination | `notifications` | `switch (type)` | **dead** — D7 |
| challenge dialog / mandatory-survey block | `submissions` | `parentId = '<surveyId>@<courseId>'` | **dead** — D8 |
| voices community feed | `news` | `viewIn[]._id == viewer` | **was dead** — D9, fixed |
| voice compose → upload | writes `news` | — | **dead** — D10 |
| voice thread replies | `news` | `replyTo = parent.docId ?? parent.id` | **dead** for local posts — D11 |
| community services | `teams` | `docType = 'service'` | **dead** — D12 ✔ |
| community leaders; community name/type | `PlanetPrefs.communityLeaders` | non-empty | **dead** — D13 ✔ |
| community finances/reports tabs | `teams` | `teamId = ''` | **dead** — D13 |
| member detail, team leaderboard, team member names | `users` | `id = :userId` | **dead** — D15 ✔ |
| team tasks | `team_tasks` | `teamId = ? AND status = 'active'` | **dead** for server tasks — D16 ✔ |
| team voices | `news` | `viewIn[]._id == teamId` only | suspect — D17 |
| team resources | `teams` → `my_library` | `docType = 'resourceLink'` exactly | suspect — D18 |
| achievements, edit achievement, CV | `achievements` | `id = '<userId>@<planetCode>'` | **dead** — D19 ✔ |
| add health (write side) | writes `health_examinations` | — | **dead** — D20 |
| become member (offline) | writes `users` | — | **dead** — D21 |
| dictionary | `dictionary` | `word_normalized = ?` | suspect — D22 |
| public survey (load) | `surveys` | `type == 'surveys'` | suspect — D23 |
| my health (read), add examination | `health_examinations` | `profileId = myHealth.userKey` | **live** (probed through the real cipher) |
| teams catalog, team detail, members list, join requests, finances, reports, courses, surveys, plan, calendar | `teams`, `surveys`, `meetup` | various `docType` | **live** (probed through the real mappers) |
| surveys list, take survey, send survey | `surveys`, `submissions` | `type`/`teamShareAllowed` | live |
| chat history + detail | `chat_entries` | `user = <session.name>` | live |
| feedback list/detail/create | `feedback` | `owner = <session.name>` or manager | live |
| settings, storage breakdown, storage category | disk + `my_library` | parent dir = `couchId` | live |
| life, personals, references, dictionary shell, maps, calendar, events | `my_life`, `my_personal`, `meetups` | `userId = session.id` | live |
| login, server config, sync center, profile, activities, dashboard shell/drawer, onboarding, about/disclaimer, inactive dashboard | — | — | live |

### The headline: five sync-in walks Kotlin has that the port does not ✔

I grepped `lib/` for every `_all_docs` walk. The complete set is
`login_activities, chat_history, configurations, courses, meetups, feedback,
health, notifications, courses_progress, certifications, resources, submissions,
exams, tags, news, teams`. Missing, each verified absent by grep:

| missing walk | Kotlin | what dies |
|---|---|---|
| **`shelf`** (`SyncManager.kt:442-540` → `processShelfParallel`) | pulls each user's shelf and stamps `userId` onto their resources/courses/meetups/teams | D1: My Library, My Courses, courses progress, both home cards, completed-course stars |
| **`tablet_users`** (`SyncManager.kt:145-149`) | upserts the planet's accounts into `users` | D15: member detail, the leaderboard, every team member's name |
| **`ratings`** (`HeavyTableSyncWorker.kt:39-41`) | community ratings | D4: every rating average shows only this device's own rating |
| **`tasks`** (`SyncManager.kt:145-149`) | team tasks | D16: a task made on Planet or by a teammate never arrives |
| **`achievements`** (`TransactionSyncManager.kt:259-261`) | the achievements ledger + CV attachment | D19: a second device shows blank, and saving there 409s into the outbox's permanent-failure branch |

`_users/_find` for community leaders (`LoginSyncManager.kt:123-147`) is a sixth
absent call — D13.

**This is one phase, not five.** The walks are the same shape as the sixteen the
port already has, and four of the five have a Dart repository method already
written and uncalled (`AchievementsRepository.syncAchievements`,
`CoursesRepository.sync(shelfId:)`, `TeamTaskDao.upsertAll`,
`PlanetPrefs.setCommunityLeaders`) — plumbing laid for a pull nobody wrote. That
is the Phase 113 shape at the top of the stack: a parameter threaded end to end
for a caller that does not exist.

**Two traps for whoever closes it**, both found by the audit rather than guessed:

- `TeamTaskDao.watchForTeam` is `status = 'active'` where Kotlin is
  `status IS NULL OR status != 'archived'`, and `TeamTask.serialize` emits **no
  `status`** — so every synced task would arrive with a null status, be rejected
  by the port's predicate, and the screen would stay empty with the walk
  working. ✔
- `AchievementsRepository` rows carry no `_rev` until a sync supplies one, and
  `OutboxDrainer` classifies the resulting 409 as permanent (`code < 500`) and
  abandons the row with no snackbar and no log. Adding the walk fixes both ends;
  adding only the upload makes it worse.

### The defects, individually

Numbered for citation. Everything here is **reported**, not fixed, except D1 and
D9.

**D1 — the resources walk cleared My Library on every sync. FIXED.**
`my_library.userId` has two writers over one column, and
`MyLibraryMapper.fromDoc` writes `userId: Value(existingUserIds)`
unconditionally. Its one production caller passed none of the six `existing*`
arguments, so every sync wrote `const []` over the shelf. Add a resource to My
Library, tap sync, My Library is empty. The sibling walk in
`courses_repository.dart` already did it correctly. Guarded by
`test/repository/shelf_membership_survives_sync_test.dart` and, mechanically, by
`test/data/local/mapper_preserves_local_columns_test.dart`.

**D2 — no `shelf` walk**, so even with D1 fixed a shelf built on Planet web or on
another device never arrives, and `CoursesRepository.sync(shelfId:)`'s parameter
has no production caller. ✔

**D3 — a locally created resource is deleted by the next sync and never
uploaded.** `deleteNotIn` removes every local id absent from the keep set, with
no `rev` guard and no `isPrivate` guard, where Kotlin's `deleteStalePublicNotIn`
spares both; and there is no resources uploader at all. So Save on the
add-resource screen (itself unreachable until this phase — see Part 1) writes a
row that the next sync destroys, having never left the device.

**D4 — no `ratings` walk.** The only writer of the table is the local `submit()`,
whose one caller always passes the session user. So the read predicate
(`type = ? AND item = ?`, deliberately unscoped by user) can only ever see one
user's row. Two knock-ons: `ratings.couchId`/`rev` are structurally null, so
every re-rating POSTs a new document where Kotlin PUTs; and `ratings` is not in
`localAuthorityTables`, so a schema bump loses the user's own ratings with no
sync to restore them.

**D5 — the submissions list cannot show anything the server sent.**
`upsertDocuments` reads a top-level `userId` key; Kotlin derives it from the
nested user object (`normalizeSubmissionUserId(user._id)`) and its upload
serializer emits no top-level `userId` at all. Worse, `deleteNotIn(savedIds)`
keys on CouchDB `_id`s while a locally authored row is keyed by a sha1, so the
learner's own submissions are pruned once `markUploaded` clears `isUpdated`. Only
port-authored documents survive, because the port's own `serialize` happens to
emit the non-standard key — which is why nothing noticed.

**D6 — `submissions.parent` and `submissions.user` are stored as Dart map
literals.** `JsonUtils.getStringOrNull` falls through to `Map.toString()`, so the
column holds `{_id: exam-1, name: Week 1 quiz}`. That string is drawn as the list
title and the detail headline, `jsonDecode` throws on it (silently swallowed in
`SurveysRepository._parentSurveyId`, so team-survey adoption sees nothing), and a
re-upload replaces Planet's `parent`/`user` objects with the literal. Same shape
as Phase 104's `SurveyMapper.choices`. The same file's `_answerFromJson` carries
a comment about this exact trap.

**D7 — every server-originated notification is unactionable and mislabelled.**
`NotificationParser.resolveType` — a faithful port of Kotlin's `resolveType`,
which maps the server's raw `team`/`newTask`/`replyMessage` onto the seven
display types — **has no caller in `lib/`** ✔. The repository stores the raw
value, and all three readers (destination resolver, grouping, icon/title) switch
on it, so a join request lands in "Other" with a generic bell and does nothing
when tapped. The unread badge is unaffected, so the bell says there is something
and the row is on screen; it just cannot be identified or opened. `subType`, which
the repository does compute correctly, is read nowhere ✔.

**D8 — the mandatory-survey block can never clear.** `hasUnfinishedSurveys`
builds `parentId = '<surveyId>@<courseId>'`; `createSurveyDraft` writes the bare
`survey.id`, never the suffix. Kotlin's `createSubmission` writes the suffixed
form. So a submitted course survey still reads as outstanding — permanently
pinning the challenge dialog's task and permanently blocking
`take_course_screen._onFinish`'s pop.

**D9 — the community feed filtered on the wrong identifier. FIXED.** ✔
`shareToCommunity` writes `viewIn[]._id = '<planetCode>@<parentCode>'`, matching
Kotlin's `shareNewsToCommunity`; `communityFeedProvider` filtered on
`user.couchId ?? user.id`. The two halves of the port disagreed about one key, so
no shared voice could reach the feed — including the sharer's own. The comment
that justified it ("`viewIn` entries carry server ids") was true and beside the
point: they carry a *planet* id, not a *user* id. Guarded by
`test/repository/community_share_round_trip_test.dart`, which spans both halves,
because each half had a passing test and only the pair was wrong.

**D10 — a voice composed in the port uploads with no audience and no author.**
`VoicesActions.createPost` passes no `viewInId`, `viewInSection`, `messageType`
or `userJson`, so `serialize` omits `viewIn` and `user` entirely. The document is
invisible in the port's feed, the Kotlin app's, and Planet web; and on the next
sync `NewsMapper.fromDoc` writes `Value(null)` over `userId`/`userName` from the
absent user object, so the author loses their own post's byline and its
edit/delete affordance. D9's fix makes the *shared* path work; this one is the
compose path and is a separate change.

**D11 — replies to a locally composed post vanish when it uploads.** `postReply`
writes `replyTo = parent.id` (the local row id) while both reply providers read
with `docId ?? id` (the server id). They coincide for a synced post and diverge
permanently for a local one.

**D12 — the community Services tab reads a `docType` no writer produces.** ✔ The
port queries `'service'`; Kotlin's `getTeamLinks()` is `getByDocType("link")`,
and `"service"` appears nowhere in `app/src/main`. The tab always shows its empty
state.

**D13 — three preferences with no writer.** ✔ `setCommunityLeaders`,
`setCommunityName` and `setPlanetType` have no caller anywhere in `lib/`. Kotlin
fills them from `LoginSyncManager.syncAdmin()` and
`SyncConfigurationCoordinator`. So the Leaders tab is permanently empty, the
community title is always the generic fallback, and `isCommunity` is always
false, which mislabels the leaders tab "Nation Leaders". Separately, the
community Finances and Reports tabs query `teamId = ''` because `communityId` is
never passed to `CommunityScreen`; Kotlin passes `"<planetCode>@<parentCode>"`.

**D14 — the download key diverges from Kotlin's.** Kotlin downloads and stores by
the `_attachments` key (`resourceLocalAddress`); the port uses the document's
`filename` field for both URL and path. The two Dart sides agree with each other,
so this is not self-inconsistent — but a resource whose `filename` differs from
its attachment name 404s in the port and works in Kotlin. Two tells that it is a
slip: `MyLibraryMapper` computes the correct Kotlin URL into
`resourceRemoteAddress` and nothing reads that column, and the download button's
visibility is gated on `resourceLocalAddress` while the download itself uses
`filename`. Suspect: no real document to settle it.

**D15 — no `tablet_users` walk**, so `users` holds only accounts that have signed
in on this device ✔. Member detail shows "Unknown member" for everyone else; the
leaderboard silently drops every member without a row, leaving a one-row board;
the team members list falls back to rendering the raw `org.couchdb.user:bob`.
Kotlin's `getJoinedMembers` drops such rows rather than showing an id.

Worth stating because the brief flagged it: the synthetic team ids
`/life/teams/community/members/<id>` and `/life/teams/voices/members/<id>` are
**not** a defect. The provider renders fine with them — visits read 0 and
`isLeader` false, correct for a non-team context. The screen dies one line
earlier, on the `users` lookup.

**D16 — no `tasks` walk** ✔, plus the `status` predicate trap above.

**D17 — `teamVoicesProvider` implements half of Kotlin's query.** `NewsDao.kt:33`
is `(viewableBy = 'teams' AND viewableId = :teamId) OR viewIn LIKE :pattern`; the
port has only the `viewIn` half. The corroborating signal is internal: the port's
own team-chat badge counts by exactly the branch its screen ignores, so the badge
and the list count disjoint sets. Suspect only because no real document settles
which shape the server writes.

**D18 — team resources misses two of Kotlin's three sources.** Kotlin is
`docType IS NULL OR '' OR 'resourceLink' OR 'link'`, unioned with team-private
resources (`isPrivate = 1 AND privateFor = :teamId`); the port requires
`docType = 'resourceLink'` exactly and has no private half, though both columns
exist and are filled. Given D12 establishes `'link'` as a real server value, the
`'link'` branch is likely live data.

**D19 — no `achievements` walk** ✔, with the 409 trap above.

**D20 — `AddHealthScreen` writes the health profile and never enqueues it.**
`queuePending` has two callers: the *examination* form and a retry button that
only appears once a row has already been abandoned. Kotlin sweeps
`getUpdatedHealthExaminations()` on every auto-sync regardless of which screen
dirtied the row, so the profile — and the name/dob/email/phone edits that ride
with it — reach the server. In the port they stay on the handset unless the user
later records an examination, which masks the bug.

**D21 — an account created offline never reaches CouchDB.** `_submit` calls
`uploadNewUser` once, synchronously; on failure it enqueues nothing.
`UserUploader.queuePending`'s single caller is the profile editor, and neither
`syncAll` nor the background runner calls it. Kotlin drains
`getPendingSyncUsers` on every auto-sync. `docs/kotlin-to-flutter-migration.md`
describes this durable path (Phase 63) as if the enqueue exists.

**D22 — the dictionary import aborts on a duplicate entry.**
`DictionaryMapper.fromJson` derives `id` from `language:word:code` where Kotlin
uses a random UUID, so homographs collide; `replaceAll` uses `insertAll`, which
throws `UNIQUE constraint failed` and leaves the table empty, and nothing on the
path catches — the button spins forever with no error. Suspect because the
shipped source file could not be fetched to confirm a duplicate exists. Same
shape as Phase 113's defect F.

**D23 — the public-survey load is stricter than Kotlin's.**
`SurveyMapper.fromDoc` requires `type == 'surveys'`; Kotlin's
`saveSurveyFromPublicApi` runs the document through `insertCourseStepsExams` with
no type filter at all, defaulting a missing `type` to `"exam"`. If the public API
returns a projection without `type`, the respondent gets "Could not load survey".
The screen's only test hands the mapper `'type': 'surveys'`, so the guard has
never been exercised against anything else.

### Smaller divergences found in the same sweep

`submissions.uploaded` reads a top-level key Kotlin derives from `rev`, so every
synced submission renders "not turned in". `isResourceOffline` drops Kotlin's
`_rev == downloadedRev` half, so a resource whose attachment was replaced still
claims to be on disk. `MyLibraryMapper` reads `'tag'` where Kotlin reads
`'tags'` (inert today). `Tag.isAttached` differs for a tag with no `attachedTo`
(the port is more useful than Kotlin). Kotlin writes a `my_library` row per
course-step resource with `courseId`/`stepId`; the port's table has neither
column, and the step view's "N resources" card has a chevron with no `onTap`.
The background auto-sync omits `notifications` and `activities`, which the
foreground sync center runs. `notifications.title` has no producer. `InlineComments`
never calls `queuePending`, so a task comment rides along the next unrelated
voice action. And `course_mapper.dart:9-10` still carries the doc comment Phase
113 proved false — the code was fixed and the sentence was not.

### Two claims in the migration doc that the code does not support

Recorded because a later phase will read them as settled:

- *"The sync-in path is exercised end-to-end"* for achievements
  (`docs/kotlin-to-flutter-migration.md:3588-3591`) — true of the repository
  method, false of the app: nothing calls it.
- *"the port's courses sync already preserves shelf membership"* (`:1550-1552`)
  and *"the port's shelf processing holds its api via the repository"*
  (`:1640-1641`) — there is no shelf pull.

I have not edited that document; it is outside this lane's set.

---

## What this lane fixed, and what it reported

**Fixed** (each demonstrated failing on the pre-fix code first):

| | defect | files |
|---|---|---|
| Part 1 #1 | `AddResourceScreen` unroutable | `ui/router.dart` |
| Part 1 #2 | `WebViewScreen` unroutable | `ui/router.dart` |
| Part 1 #3 | `TeamLeaderboardScreen` had no entry point | `ui/router.dart`, `ui/teams/teams_screen.dart` |
| Part 1 #4 | New chat pushed a route pattern | `ui/router.dart`, `ui/chat/chat_history_screen.dart` |
| D1 | the resources walk cleared My Library every sync | `repository/resources_repository.dart` |
| D9 | the community feed filtered on the wrong identifier | `providers/voices_provider.dart` |

**Reported**: Part 1 #5 and #6, and D2–D8, D10–D23. The five missing sync walks
are one phase; the notification `resolveType` gap (D7), the submissions sync-in
shape (D5/D6) and the voice compose payload (D10) are each their own.

**Files touched outside this lane's set**, all defects rather than tidying, none
owned by Lane B (`repository/progress_repository.dart`,
`providers/courses_providers.dart`, `ui/courses/`) or Lane C (`l10n/`,
`test/l10n/`, `tool/`):
`lib/ui/chat/chat_history_screen.dart`, `lib/ui/teams/teams_screen.dart`,
`lib/repository/resources_repository.dart`, `lib/providers/voices_provider.dart`,
`test/ui/teams/team_detail_gates_test.dart`.

## The guards

| file | catches |
|---|---|
| `test/ui/route_reachability_test.dart` | a route nothing navigates to, a navigation no route serves, a pushed `:param` pattern, an unreadable call site |
| `test/repository/shelf_membership_survives_sync_test.dart` | a sync walk retracting what a local tap set, on resources *and* courses |
| `test/data/local/mapper_preserves_local_columns_test.dart` | a mapper call omitting an `existing…` argument — the D1 defect, mechanically |
| `test/repository/community_share_round_trip_test.dart` | a writer and a reader disagreeing about a key, across two files |

## A second audit, on the finished implementation

Phase 110's and 113's lesson, repeated exactly: **an audit of the ground truth
does not audit the implementation.** The Part 1 fix was green — format clean,
analyze clean, full suite passing — and a `parity-auditor` pass at `effort: max`
aimed at my own diff found the guard materially weaker than both its doc comment
and this file claimed.

It reverted each of the four fixes and confirmed the guard went red on every one,
including a reconstruction of Phase 113's defect C. Then it injected a *new*
broken navigation into three call sites the scanner could not read and watched
the suite stay green:

| # | blind spot | injected defect that passed |
|---|---|---|
| A | the scanner read only the **first token** after `context.push(`, so a ternary, a variable and 2 of 7 notification switch arms were invisible | `context.go(session != null ? '/totally-not-a-route' : Routes.login)` |
| B | `_stripComments` cut at `indexOf('//')`, which truncates `route.startsWith('http://')` into an unbalanced quote — one line above the `/web-view` push this phase fixed | a push on that same line |
| C | `_resolve` dropped a glued interpolation as "must be a query string" | a `patientQuery` that adds a path segment |
| D | the leaderboard tile was ungated where its neighbours gate on `canView`, and `watchMembers` has no viewer predicate | a non-member of a private team seeing the full roster and everyone's counts |

A, B and D are fixed: the scanner now takes the **whole balanced argument** of a
navigation call and reads every literal and constant in it; the comment stripper
is string-aware and preserves newlines; a fourth rule reads any `/`-leading
literal in `lib/ui/` and `lib/providers/` (scoped there because repositories and
mappers build URLs and disk paths out of such strings — four measured false
positives outside those layers); and the tile moved inside `canView`, with
`team_detail_gates_test` extended so its title is true of the leaderboard rather
than merely unfalsified. All three injected defects now fail the guard, replayed.

C is not fully fixed and is now **declared** rather than silent: `_resolve`
reports a partial resolution, and a fifth test requires every unreadable call
site to be in an allowlist with its reason. The prefix is still checked; a
non-query suffix on those two health-screen locations would still pass.

The pass also corrected three sentences in this file, all now rewritten: the
notification-arm claim, the "covered without anyone remembering" claim, and the
scanner's own "the tree has none today".

**The most useful thing it found is not in the table.** Eighteen test files use
`widget_harness.dart`'s `pushTargets`, which builds a **fresh `GoRouter`
containing only the routes the test names**. With the router reverted to the dead
`/web-view` path, `services_screen_test.dart`'s *"an external route navigates to
the web view screen"* still passes — it registers its own `/web-view` and asserts
the tap lands on it. That test was green for the entire life of the dead route.
It is Phase 113's fabricated join one layer up, and it is why a source-derived
guard, rather than more widget tests, is the thing that closes this class.
