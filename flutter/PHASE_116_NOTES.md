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
position to copy. A tile next to the member list is where it belongs, and it is
ungated because it ranks the whole team either way.

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
sections and `DeepLinkHandler.publicSurveyLocation` resolve; the notification
destinations are built as `Routes.`-interpolated literals inside
`notifications_screen.dart`'s `switch`, which the guard's second scanning rule
reaches even though they reach `context.go` through a variable.

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
