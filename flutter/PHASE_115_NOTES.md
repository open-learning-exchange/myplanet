# Phase 115 — the team surfaces: a coverage correction, first tests, and 23 defects

Lane C. **23 defects, every one demonstrated failing on the pre-fix code before
it was touched** — 30 failing tests across six groups, with the failing-first
count recorded per group below. 59 new tests in total; the balance pin behaviour
that was already correct, including two things a plausible "fix" would have
broken (§4, §8.6).

Two of the 23 are minor by any reading — a label and a surplus menu entry, both
in §5 — and they are counted rather than quietly dropped so the number means
what it says.

---

## 1. What the coverage actually was (a correction to the brief)

The brief said `lib/ui/teams/teams_screen.dart` "is 404 lines and is referenced
by exactly **one** test file — the thinnest coverage-to-size ratio left in the
port". Measuring by **symbol reference** rather than filename (the brief's own
warning, after the Phase 108 filename heuristic missed 22 chat tests) gives a
different and worse answer. The reference count was right; the conclusion was
not.

| file | lines | test files referencing it |
|---|---|---|
| `leaderboard/team_leaderboard_screen.dart` | 284 | **0** |
| `inline_comments.dart` | 220 | **0** |
| `teams_screen.dart` | 404 | 1 (`team_detail_screen_test.dart`) |
| every other file in `lib/ui/teams/` | 100–646 | 1–2 |

Two corrections follow.

**Two team files had no test at all**, and neither is `teams_screen.dart`. The
leaderboard's *calculator* is tested (4 tests, `test/ui/teams/leaderboard/`),
which is what makes the screen easy to miss: the directory has a test file, just
not one that touches the 284-line screen beside it.

**And the one file referencing `teams_screen.dart` covers one of its two
screens.** `teams_screen.dart` holds `TeamsScreen` (the catalog list, lines
14–149) *and* `TeamDetailScreen` (150–385). `team_detail_screen_test.dart`'s two
tests are both about `TeamDetailScreen`'s visit logging — nothing touched the
team list, and nothing touched the detail screen's gates. So the thinnest
surface was not one file but three screens: `TeamsScreen`, the leaderboard, and
the comment thread.

This phase covers all three, plus the detail screen's gates.

---

## 2. `inline_comments.dart` — 8 tests, 3 defects

`test/ui/teams/inline_comments_test.dart`. Failing first: **4 of 8**
(the submit path proves one defect twice).

**An empty author name took the whole thread down.** `_CommentTile` rendered its
avatar as `(comment.userName ?? '?').characters.first.toUpperCase()`. `??` only
guards *null*, and `''.characters.first` throws `StateError: No element` — so a
synced `News` row carrying `"userName": ""` threw out of `build` and took the
comment thread, and therefore the team-tasks screen hosting it, with it. This is
the Phase 95 shape exactly (`parts[0][0]` behind a dead `isEmpty` guard), and
`voices_screen.dart:301-304` already had the correct sibling
(`trimmed.isEmpty ? '?' : …`) while this file carried the broken copy.

The fix adds `initialFor(String?)` to `lib/ui/components/profile_avatar.dart` —
the file the port's own rule points at — rather than a fourth local copy. **When
you need an initial from a bare name string, call `initialFor`; when you have a
`UserRow`, use `ProfileAvatar` / `displayName`.**

**A comment submitted before the session resolved was silently dropped.**
`_addComment` read `ref.read(sessionProvider).valueOrNull` on a widget that
never watches that provider, so the value was null and the `if (user == null)
return` discarded the comment with no error, no snackbar and no row. This is
the **fifth** independent instance of the shape CLAUDE.md already records as a
rule. Fixed by awaiting `sessionProvider.future` **inside** the enclosing `try`,
because a future can reject where `valueOrNull` could not.

The same fix moves `_controller.clear()` to *after* the write lands, so a failed
post leaves the text in the field to retry instead of discarding it.

**A failed comment stream read as "nobody has commented."** The branch order was
`isLoading` → `valueOrNull?.isEmpty == true` → iterate `valueOrNull ?? []`. On an
error `valueOrNull` is null, so the empty-state line did not show and the loop
iterated nothing: the expanded thread rendered just its input box, and the
collapsed header said "0 comments" — affirmatively stating there are none when
we do not know. A `comments.hasError` branch now shows `commentsUnavailable`.

---

## 3. `leaderboard/team_leaderboard_screen.dart` — 10 tests, 6 defects

`test/ui/teams/leaderboard/team_leaderboard_screen_test.dart`. Failing first:
**6 of 10**.

Note on the specification: **this screen has no Kotlin counterpart.** There is no
`ui/teams/leaderboard/` in `app/`, and `grep -rn "Leaderboard" --include=*.kt`
finds nothing — Phase 73 ported it from the unmerged upstream `14880` branch. So
these are correctness defects, not parity divergences, and they were found by
testing rather than by comparison.

1. **The current user was never highlighted.** `_load` read
   `ref.read(sessionProvider).valueOrNull` and `_load` runs from `initState`, so
   the value was null on every load, `currentUserId` was null, `isCurrentUser`
   was false for every row, and the highlight the feature exists for never
   fired. The **sixth** instance of the shape. Fixed with
   `await ref.read(sessionProvider.future)`.
2. **The display name omitted the middle name and was not trimmed.** The screen
   hand-rolled `[firstName, lastName].join(' ')` falling back to
   `user.name ?? userId`, where `profile_avatar.dart`'s `displayName` includes
   `middleName`, trims each part, and falls back past an empty username. A
   synced `"name": " jane "` with no first or last name rendered as `" jane "` —
   a visually blank row. Now calls `displayName(user)`.
3. **The visit stat carried no number.** `_Stat(label: l10n.numberOfVisits)`
   renders the bare label "Number of visits" as the entire stat, beside two
   siblings that both read "2/5 courses"/"1/3 surveys". `entry.visitCount` was
   computed, passed into the entry, and never displayed. `numberOfVisits` is
   *correct* in `member_detail_screen.dart:123-126`, where a separate `value`
   sits beside it; here it was the whole stat. New key `visitsCount`.
4. **A failed load spun forever.** `_load` had no error handling: any throwing
   dependency escaped, `_loading` stayed true, and the screen sat on an
   indefinite `CircularProgressIndicator` with no way out and nothing logged.
   Now a `_failed` flag renders `leaderboardUnavailable`.
5. **Tied members ranked non-deterministically.** `TeamLeaderboardCalculator`
   sorted by courses then surveys and stopped. Member ids arrive through a
   `Set`, and Dart's `List.sort` is not stable, so two members with identical
   scores — most of a fresh catalog — could swap rank between two loads of
   unchanged data. A total order (name, then id) now makes the ranking
   reproducible.
6. **A stale load could overwrite a fresh one.** Toggling the period starts a
   second `_load` without cancelling the first. This one is *hardening the error
   fix required*, not a defect found: `_period` is read after all the awaits, so
   the late load happened to compute the new period's data. But once `_load`
   catches, a stale load's `_loading = false` would clear the spinner for the
   live one. A `_loadToken` generation guard closes both.

One thing deliberately **not** changed: switching to "This month" filters the
surveys column but not the courses column, because `CourseProgressSummary`
carries no timestamps — there is no completion date to filter on. That is a data
limitation, not a fixable gate.

---

## 4. `TeamsScreen` (the catalog list) — 14 tests, 3 defects

`test/ui/teams/teams_screen_test.dart`. Failing first: **3** (the other 11 pin
existing correct behaviour).

**The search matched the description; Kotlin matches the name only.**
`TeamViewModel.applyFilters` (`TeamViewModel.kt:112-118`) is
`teams.filter { it.name?.contains(searchQuery, ignoreCase = true) == true }`.
The port also tested `row.description`, so typing a word that appears only in a
team's plan surfaced a team whose name does not contain the query at all.

**The important half of this finding is what was *not* changed.** The obvious
reading — "another flat `contains` that Phases 96 and 97 replaced with the
ranked `ResourcesSearchUtils` algorithm for resources and surveys" — is wrong.
The teams screen genuinely is the flat one: no ranking, no word splitting, no
accent folding. Adding `text_utils.normalizeText` or startsWith-before-contains
here would *introduce* a divergence. The port was already right on that axis and
the tests now pin it so nobody "fixes" it.

The `.trim()` on the query is **kept, deliberately**: Kotlin does not trim, so
`"Alpha "` there filters to names containing a trailing space and shows nothing.
That is unhelpful behaviour rather than behaviour worth reproducing, and it is
recorded at the line.

**Every keystroke replaced the list with a full-screen spinner.**
`teamsProvider` watches `teamsSearchProvider` and `teamsTypeProvider`, so each
character tears the provider down and rebuilds it; `AsyncValue.when`'s
`skipLoadingOnReload` defaults to `false` and explicitly "does not skip loading
states if triggered by `Ref.watch`". Typing "alpha" therefore blanked the list
and flashed a centered spinner five times, once per character, re-subscribing
`watchCatalog` and re-running `memberStatuses` + `recentVisitCounts` each time.
Kotlin re-filters an in-memory list and never shows a loading state at all.
`skipLoadingOnReload: true`. The same flash hit every tap of the type segment,
covered by its own test.

---

## 5. `TeamDetailScreen`'s gates — 13 tests, 6 defects

`test/ui/teams/team_detail_gates_test.dart`. Failing first: **9 of 13**.

Gates in this area have a documented history of being subtly wrong (Phase 99
found the finance and report screens requiring leadership where Kotlin requires
plain membership), so every claim below was read out of the Kotlin directly
rather than taken from the audit that surfaced it.

**A private team's whole contents were open to any visitor.** `buildPages`
(`TeamDetailFragment.kt:74-92`) branches on `isMyTeam || team?.isPublic == true`:
a non-member of a non-public team gets exactly **two** pages, Plan/Mission and
Members. The port's link list was unconditional apart from three entries, so a
non-member of a private team was offered its task list, its survey list, its
resource list and its discussion thread. The gate is now
`canView = membership != null || team.isPublic`, which also closes the
*other* direction the audit noted: a non-member of a **public** team gets
Calendar in Kotlin and did not in the port.

**A private enterprise's financial reports were open to non-members.** Kotlin
puts `ReportsPage` inside the same branch (`:85`). Called out separately from
the finding above because the payload is financial data.

**A guest was offered membership.** `setupNonMyTeamButtons` (`:255-258`) hides
the button outright for a `guest…` id, before any of the join wiring runs; the
list row is hidden for guests too (`TeamsAdapter.kt:102`). The port had no guest
branch and would enqueue a join request carrying the `guest_` id. Now reads
`UserMapper.isGuest`, the port's single guest predicate.

**A leader could not leave.** `onPressed: membership.isLeader ? null : …` — the
Phase 99 shape again, and Phase 99 did not catch this one.
`setupMyTeamButtons` (`:285-308`) attaches the leave handler with **no leader
test**, and `markMembershipsForLeave` has none either; the members tab's own
leave hands leadership to a successor first but still never refuses. Kotlin has
`isTeamLeader` and pointedly does not use it here — the one place leadership
changes the affordance is the *list row*, where a leader gets Edit instead of
Leave.

**Leaving took effect on one tap.** Every Kotlin leave is behind a
`confirm_exit` Yes/No dialog (`:291-306`, and `TeamFragment.kt:280-287` for the
list). The port called `leave` straight from `onPressed`, so one mis-tap dropped
the membership and queued its tombstone. Three tests cover the dialog: that it
appears, that No leaves the membership alone, and that Yes goes through —
per the brief's rule that "the button looks usable" is not "the button works".

**The last member was offered a leave button.** `TeamDetailFragment.kt:179-186`
hides it once `getJoinedMemberCount(teamId) <= 1 && isMyTeam`. A null count now
still shows the button, because the Kotlin check is async too and only hides on
arrival.

Two smaller corrections in the same file:

- **The enterprise/team branching**: Courses is no longer offered for an
  enterprise (`buildPages` swaps `CoursesPage` out for `FinancesPage`, `:84`),
  and the resources entry is labelled Documents for an enterprise, matching
  `DocumentsPage`/`ResourcesPage` (`TeamPageConfig.kt:63-69`). The
  Finances-for-a-plain-team's-members surplus is kept, as
  `docs/kotlin-to-flutter-migration.md` records it as deliberate.
- **`memberships[team.teamId]` was dead code.**
  `getTeamMemberStatuses` (`TeamsRepositoryImpl.kt:562-601`) keys membership on
  the team document's `_id` alone. The fallback read the *team row's own*
  `teamId`, which for a root team is null or `''` — and `''` can never be a key
  because `teamMembershipsProvider` excludes empty ids. It could never resolve,
  and if it ever had it would have reported a *parent* team's membership for a
  sub-document. Removed.

**`_logVisitOnce` set its latch before reading an unwatched provider.** The
seventh instance of the shape, with a twist that makes it worse: `_visitLogged =
true` is committed *before* the post-frame callback reads the session, so a null
read dropped the visit for the entire mount with nothing to retry it — and the
undercounted `team_log` row feeds the catalog sort. Kotlin's `createTeamLog`
(`:426-441`) awaits its own `getUserModel()`.

---

## 6. The add/remove gates on the destination screens — 10 tests, 3 defects

`test/ui/teams/team_add_remove_gates_test.dart`. Failing first: **5 of 10**.

The port dropped Kotlin's `btnAddDoc` from the detail screen and moved the add
action onto the destination screens — defensible — but both screens then drove
*add and remove from one `canManage = isLeader` flag*, and neither gate is
leadership.

- **`team_resources_screen.dart`**: `TeamResourcesFragment.kt:51-52` is
  `binding.fabAddResource.isVisible = isMember` — `isMemberFlow`, i.e.
  `TeamsRepositoryImpl.isMember`, **plain membership** — while remove really is
  `isTeamLeader` (`:73`). The two gates are genuinely different; the port had
  one. An ordinary member could not link a resource Kotlin lets them link.
- **`team_courses_screen.dart`**: Kotlin's add has **no gate in the fragment at
  all** — it is driven by `btnAddDoc`, shown to any member
  (`TeamDetailFragment.kt:286-289`). And remove is
  `canRemove = currentUserId.equals(teamCreator, ignoreCase = true)`
  (`TeamCoursesFragment.kt:44-46`), where `getTeamCreator` is the team row's
  `userId` (`TeamsRepositoryImpl.kt:1120-1123`) — the **creator**, not the
  leader. So the port both offered the unlink to a leader who did not create the
  team and withheld it from a creator who is not the leader. The case-insensitive
  comparison has its own test.

`test/ui/team_courses_screen_test.dart`'s "a leader sees the add-course and
remove buttons" was asserting the defect. It is now "the team creator sees…"
with the session and `teamProvider` overridden so the user is both.

---

## 7. `TeamMembershipActions` — 4 tests, 2 defects

`test/providers/team_membership_tombstone_test.dart`. Failing first: **3 of 4**.

**A tombstone could be queued with a null `_rev`, failing permanently.**
`markMembershipsForLeave` (`TeamsRepositoryImpl.kt:1323-1337`) branches on the
revision: `if (membership._rev.isNullOrBlank())` the row is deleted locally and
**nothing** is uploaded; only a revision-bearing row becomes a
`{_id, _rev, _deleted: true}` document. The port enqueued unconditionally, so
joining a team offline and leaving before the first sync sent `"_rev": null`,
which CouchDB rejects 4xx — and the outbox's retryable rule is `code >= 500`, so
the row failed out **permanently**, for a document the server never had. Same
shape in `removeMember` and in `respond(accept: false)`. Guarded by
`_serverKnowsRow`, which tests `isNullOrBlank` rather than `== null` (an empty
string has its own test).

**Every action read the session without watching it.**
`TeamMembershipActions` is a plain `Provider`, so its `ref` never watches
`sessionProvider`; `requestToJoin`, `leave`, `removeMember` and `makeLeader` all
did `ref.read(sessionProvider).valueOrNull` and took a silent `return false`
when it was null. Instances **eight through eleven** of the shape, and the first
in a provider rather than a screen — worth noting because the "the router holds
a `ref.listen`, so it is latent" mitigation is a property of the *screens*, and
a provider cannot rely on its callers having one. Now `await _session()`.

---

## 8. Found and verified, **not** fixed — outside this lane's file set

Each was read out of the Kotlin and confirmed. None is in
`lib/ui/teams/`, `lib/providers/teams_provider.dart` or
`lib/repository/teams_repository*.dart`, so per "one file, one owner" they are
reported rather than edited.

1. **A team edit never reaches CouchDB, and permanently shadows the server's
   copy.** `TeamsRepository.updateTeam` sets `isUpdated: Value(true)` and
   returns. Kotlin's `UploadManager.uploadTeams()` sweeps
   `TeamDao.getUpdatedTeams()` (`WHERE isUpdated = 1`) on every sync; the port
   has no sweep — write-back is per-action outbox enqueues — and
   `TeamsUploader.types` does not include a plain team document. `updateTeam`'s
   only caller (`team_plan_screen.dart:183-195`) awaits it and pops the dialog.
   Two silent consequences: the edit never leaves the device, and because
   `TeamMapper.fromDoc` keeps local values for a flagged row, **every future
   server-side change to that team's name, description, courses, public flag,
   services or rules is discarded on every sync from then on.** Needs
   `lib/repository/teams_uploader.dart` and an outbox upload type. This is the
   most consequential finding of the round and should be the next phase.
2. **The member count is not Kotlin's member count.**
   `TeamDao.countByTeamIdAndDocType` uses `COUNT(DISTINCT userId)`, requires
   `userId IS NOT NULL`, and joins `users` for existence — a member whose
   profile has not synced does not count. `TeamDao.watchMemberCount`
   (`lib/data/local/app_database.dart:581-587`) is a plain `COUNT(id)`. It
   compounds the last-member leave gate fixed above: the count that protects the
   last member is now read, but the number is still wrong.
3. **The catalog's root-team predicate is not Kotlin's.** Kotlin's root test is
   `teamId IS NULL OR TRIM(teamId) = ''` and never consults `docType`;
   `TeamDao.watchCatalog` (`app_database.dart:525-535`) uses
   `t.docType.isNull()`. **A team document that carries `docType` is invisible
   to the port's catalog entirely**, and the only in-repo evidence about the
   server's shape — `TeamsRepositoryBulkInsertTransactionTest.kt:55-62`,
   `CollapsedEntitiesRoundTripTest.kt:174` — builds a root team as
   `{_id, docType: "team", …}`. Every Dart fixture omits `docType` for teams, so
   the suite cannot catch it. **Settle this against a live Planet server before
   the rest**: if team documents do carry `docType`, the team list is empty and
   everything above is downstream of a screen nobody can populate.
4. **Creating a team or an enterprise is unreachable.** `TeamFragment.kt:67`
   wires an `addTeam` FAB to `createTeamAlert(null)` (hidden for guests), and
   `TeamViewModel.createTeam` rejects a duplicate name via `isTeamNameExists`
   before `createTeamAndAddMember` writes the team *and* the creator's
   `isLeader` membership in one go. The port has no FAB, no `createTeam`, and no
   `teamNameAlreadyExists` key. Duplicate-name validation is missing from the
   *edit* path too (`team_plan_screen.dart:177-182` checks only for empty).
5. **The list row has no action column.** `TeamsAdapter.showActionButton`
   (`:90-146`) gives each row one of edit (leader) / leave (member) / a disabled
   tinted hourglass (pending request) / join (non-member), hidden for guests,
   plus a feedback button. The port's row has a chip and a chevron. Join and
   leave are reachable on the detail screen, but **the "Requested" state is
   invisible everywhere in the list** — `TeamsRepository.memberStatuses`
   deliberately drops `hasPendingRequest` — and Edit and Feedback are reachable
   nowhere. Kotlin's row also shows Created On, Type and Total Visits; the visit
   count is already fetched by `teamsProvider` to feed the sort and then thrown
   away.
6. **`sortTeamsCatalog` uses the unstable `List.sort`** where Kotlin's
   `sortedWith` is stable (`lib/repository/teams_repository.dart:34` — inside my
   file set, but the ties are non-member teams with zero recent visits and
   Kotlin's own base order is unspecified, `getRootTeamsByType` having no
   `ORDER BY`, so there is no correct order to restore). Left alone
   deliberately, unlike the leaderboard's, where the displayed *rank* makes
   reproducibility visible to the user.
7. **The `teamLeaderboard` route has no pusher.** `lib/ui/router.dart:93,505-512`
   declares it and nothing navigates to it, so the screen this phase just tested
   is unreachable in the app. Not a Kotlin divergence — Kotlin has no leaderboard
   at all — but the detail screen's link list is where an entry would belong, and
   adding one is a product decision rather than a parity fix.

Also recorded, so nobody "fixes" it: `TeamFragment.showNoResultsMessage` has a
`no_teams_found_for_search` branch whose `searchQuery` parameter is always `""`
at its sole call site, so the search-specific message is dead code in Kotlin.
The port's single `noTeams` matches Kotlin's actual behaviour.

---

## 9. Method, and what the two audit passes cost

Two `parity-auditor` passes at `effort: max`, per Phase 110's lesson that an
audit of the Kotlin ground truth does not audit the implementation.

The first pass — pointed at the Kotlin — produced the finding list in §5 and §8.
Its single most useful contribution was a **negative** one: it read
`TeamViewModel.applyFilters` carefully enough to say that the teams search is
genuinely flat, and that adding the Phase 96/97 ranked algorithm would be the
divergence. Without that, "another flat `contains`" was the obvious and wrong
fix, and this phase would have shipped it.

Per Phase 106, every load-bearing Kotlin claim was re-read at source before
acting on it — `buildPages`, `setupMyTeamButtons`, `setupNonMyTeamButtons`, the
member-count gate, `markMembershipsForLeave`, `getTeamCreator`,
`TeamResourcesFragment`'s two gates. All held.

## 10. Test-harness notes for whoever comes next

- **`SegmentedButton` hides the selected segment's icon** behind a checkmark, so
  an icon count that includes it is wrong by one.
- **The detail screen's link list is a `ListView(children: [...])`** — children
  below the 600px fold are built but never mounted, so `find.text` returns 0 for
  content that renders fine, and a *negative* assertion then passes for the
  wrong reason. `scrollUntilVisible` was fragile here; setting
  `view.physicalSize` to 1000×3000 for the file puts the whole list in frame and
  makes the negative assertions in §5 mean what they say.
- **`pumpAndSettle` does not advance a pending `Future.delayed`** if no frame is
  scheduled — it returns immediately. A test that waits on a deliberately
  delayed provider needs an explicit `pump(Duration(...))` first.
- **Re-pumping `wrapScreen` with different overrides does not reliably re-emit a
  `StreamProvider` override.** Two tests that toggled a gate by re-pumping had to
  be split into two `testWidgets`; the second half failed for that reason and
  not for the reason it was written.
- **`teamsProvider`'s `await for` rethrows into the test zone as well as into
  the provider**, and the zone report lands after the test body, so
  `takeException` cannot catch it. Override `teamsProvider` itself to test the
  screen's error branch.

## 11. Strings

New keys in `app_en.arb` only, each with its `@` placeholder block where it
takes one: `commentsUnavailable`, `visitsCount` (`{count}`),
`leaderboardUnavailable`, `confirmLeaveTeam`, `teamDocuments`. The five locale
files are Lane B's.

## 12. Gate

`dart format --output=none --set-exit-if-changed lib test` clean.
`flutter analyze` — no issues.
`flutter test` — **1860 pass**, against 1801 at the branch base: exactly the 59
new tests, no regressions.
