# Phase 112 — one guest predicate, and the guest row that does not exist

Picking up Phase 107's first *Found and left* item. Its finding was that the
port spells the guest check three ways — `user.id.startsWith('guest')` in the
screens, `couchId?.startsWith('guest')` in `validateUsername`, and Kotlin's
`_id?.startsWith("guest_")` unported — and called that "three spellings of one
predicate".

**That framing is partly a misreading, and the correction matters more than the
unification.** Kotlin does not have one spelling. It has eleven, and each port
site is a faithful transcription of the *particular* Kotlin spelling at the site
it ports. A `parity-auditor` pass at `effort: max` enumerated them; the two the
phase brief singled out as suspect are the two that were already right.

The real finding underneath is larger, and it is item 2's answer.

---

## 1. Kotlin's rule, and why it needs no unifying there

`createGuestUser(username)` → `saveUser(buildGuestUserJson(username))`, the
three-arg overload (`UserSyncRepository.kt:15`, `UserRepositoryImpl.kt:348`) →
`buildUserFromJson` → `applyJsonToUser` → `upsertUser`. Traced end to end, the
row it writes is:

```
id   == "guest_ada"      // the fresh entity's id is blank, so `_id` fills it
_id  == "guest_ada"
name == "ada",  firstName == "ada",  rolesList == ["guest"]
password == null
```

**That equality is the whole answer.** `id == _id == "guest_<username>"`, so
these all agree:

| | Spelling | Column | Prefix |
|---|---|---|---|
| K1 | `user.id.startsWith("guest")` | `id` | 5 |
| K2 | `_id?.startsWith("guest_")` | `_id` | 6 |
| K3 | `_id.orEmpty().startsWith("guest")` | `_id` | 5 |
| K4 | `_id?.contains("guest")` | `_id` | substring |
| K6 | `SUBSTR(id,1,5) != 'guest'` | `id` | 5 |
| K7 | `SUBSTR(_id,1,6) = 'guest_'` | `_id` | 6 |
| K8/K9/K10 | `NOT LIKE 'guest%'` / `userId?.startsWith("guest")` | a row's `userId` | 5 |
| K11 | `it._id?.isEmpty() == true` | `_id` | — **not a guest check** |

K1 has ~30 sites, K2 six, and the rest one to three each. Kotlin gets away with
this because nothing can write an id that starts `guest` without starting
`guest_`: the only ids either app mints are `guest_<username>` (the username is
validated non-empty), `org.couchdb.user:<name>`, and a millisecond timestamp.

**One Kotlin spelling genuinely disagrees with the rest.** `UserEntity.isGuest()`
(`UserEntity.kt:177-181`) is K2 **or** (a `guest` role without a `learner` role).
The role clause is reachable: `TransactionSyncManager.kt:230` walks
`tablet_users/_all_docs` into `insertUsersFromSync`, which filters only
`_design` docs and lets `applyJsonToUser` set `rolesList` unconditionally. A
server document `{"_id":"org.couchdb.user:zoe","name":"zoe","roles":["guest"]}`
therefore produces a row where `isGuest()` is **true** and
`id.startsWith("guest")` is **false** — and Kotlin then contradicts itself
inside a single fragment: `ResourcesFragment:487` hides the checkboxes (K5)
while `:534` runs `addToMyList` (K1). That is Kotlin's quirk, not a port bug,
and it is why the role clause must stay a **second** predicate if it is ever
ported — folding it into the id rule would silently widen the settings and
voices gates past their originals.

## 2. Does the port create guest rows? No — and that is the honest answer

`grep -rn "createGuest\|guestLogin\|GuestLogin\|btnGuest" lib test` finds only
`guest_dialog.dart` imports and comments. `login_screen.dart:11` says so in its
own header. Nothing pushes a guest-login route; every guest fixture in `test/`
is hand-built. **Every guest gate in the port is dead code today**, and
`buildUserFromJson`'s guest-adoption branch (item 3) stays unportable: there is
nothing to adopt.

So the unified predicate is still the deliverable, and item 3 is deferred. But
the audit turned the "next lane needs a target" line into an actual target, and
it is bigger than a login button. **The port is missing 15+ of Kotlin's guest
gates**, which is the finding Phase 107's framing obscured:

*Would have to be built, in order:*

1. `buildGuestUserJson(username)` — `{"_id": "guest_$username", "name":
   username, "firstName": username, "roles": ["guest"]}`. The `roles` entry is
   load-bearing: without it `home_screen.dart:351-355`'s
   `rolesList.isEmpty && !userAdmin` branch fires and a guest lands on
   `InactiveDashboardScreen`.
2. `createGuestUser(username)` → `_cacheUserDoc(buildGuestUserJson(username))`.
   Two lines: `_cacheUserDoc` already produces `id == couchId ==
   'guest_<username>'` for a doc with a non-empty `_id`. **It must not be
   routed through a become-member-shaped insert** — see §4.
3. `UserDao.getGuestUserByName` / `getGuestUsersByNames` / `getSyncedUsers` /
   `getSyncedUserByName` / `getDuplicateUsers` + `deleteByIds`, none of which
   the port has. `getSyncedUsers` is `couchId` non-blank **and** `id NOT LIKE
   'guest%'` — without it a guest's shelf is not excluded from upload.
4. `migrateGuestUser` (`UserRepositoryImpl.kt:278-290`) wired into
   `_cacheUserDoc`'s missing middle branch, plus `cleanupDuplicateUsers`
   (`:837-856`), whose caller is `become_member_screen.dart` — Phase 107
   deferred both for the same reason.
5. The guest-login dialog (`GuestLoginExtensions.kt`: lowercase-forcing field,
   300 ms debounced `validateUsername`, then the three-way `findUserByName`
   branch), `showGuestDialog`/`showUserAlreadyMemberDialog`, a `source` field on
   the saved-accounts picker (`UserDao.getSavedUsers` reads the `users` table;
   Kotlin reads a `SharedPreferences` list of DTOs carrying
   `source: "guest"|"member"`), and `resetGuestAsMember`.
6. **The gates.** `lib/ui/teams/`, `lib/ui/courses/`, `lib/ui/resources/`,
   `lib/ui/surveys/` and `lib/ui/user/` contain no guest check of any kind.
   Missing, with counterparts: team join/leave (`TeamDetailFragment:255`),
   course multi-select and add-to-shelf (`CoursesFragment:135/141/250/531`,
   `CourseSelectionController:35/81`, `CoursesAdapter:339`), the Join button
   (`TakeCourseFragment:212`), resources batch selection, add-resource and
   long-press edit (`ResourcesFragment:169/178/457/476/487/501/534`), the
   shelf button (`ResourceDetailFragment:215`), Edit profile
   (`UserProfileFragment:436`), start/adopt survey (`SurveysAdapter:105`),
   the new-voice FAB (`VoicesFragment:94`), every settings switch
   (`blockGuestSwitches`, `SettingsActivity:220-242` — the port's
   background-sync toggle is ungated), the toolbar sync action
   (`DashboardElementActivity:109`), the rating click listener
   (`BaseContainerFragment:154`), and the whole guest trial-limit UX
   (`DashboardActivity:744-748`). Several are subsumed rather than missing:
   the port has no add-team or add-meetup affordance at all, and
   `team_members_screen` gates its whole tab on `isLeader`.

Not a task but a decision the next lane owes: **whether to port
`isGuest()`'s role clause.** Its only producer is a server-authored
`tablet_users` document and the port has no `tablet_users` walk, so porting it
alone adds an unreachable branch while porting the walk without it lets a
`roles:["guest"]` account through gates Kotlin closes.

## 3. The unification

`UserMapper` gains three members, and `lib/` now contains **no** other guest
literal (`test/guest_predicate_parity_test.dart` enforces it by scanning the
source, because a review of one file cannot).

* `guestIdPrefix` = `'guest_'`
* `isGuestId(String?)` — the rule on a bare id
* `isGuest(UserRow)` — `isGuestId(id) || isGuestId(couchId)`

Call sites moved (7 Dart-level predicates, all of them):

| Site | Was | Now |
|---|---|---|
| `home_screen.dart:81` health key/iv | `user.id.startsWith('guest')` | `UserMapper.isGuest` |
| `home_screen.dart:149` challenge | same | same |
| `home_screen.dart:195` pending surveys | same | same |
| `home_screen.dart:345` `isGuest` (chat, feedback, library/courses headers, tiles) | same | same |
| `dashboard_drawer.dart:23` | `session?.id.startsWith('guest')` | same |
| `settings_screen.dart:175` storage | `session.id.startsWith('guest')` | same |
| `settings_screen.dart:334` reset app | same | same |
| `voices_screen.dart:117` share | `!user.id.startsWith('guest')` | same |
| `voices_screen.dart:192` reactions | same | same |
| `user_repository.dart:246` `validateUsername` | `couchId?.startsWith('guest')` | same |
| `activities_provider.dart:119` | `user.id.startsWith('guest')` | same |
| `activities_repository.dart:443` `_isGuest` | `userId.startsWith('guest')` | `UserMapper.isGuestId` |
| `voices_repository.dart:537` | `(row.userId ?? '').startsWith('guest')` | `UserMapper.isGuestId` |

**Two deviations, stated rather than buried.** The audit flagged both, and
both are kept:

* **Five characters to six.** Every site above tested `guest`; the helper tests
  `guest_`. No producible id distinguishes them, and the error directions are
  not symmetric: a false positive gates a real member out of features they are
  entitled to, while a false negative needs an id starting `guest` and not
  `guest_`, which only a guest-creator ignoring `guestIdPrefix` could mint —
  which is what exporting the constant prevents.
* **One column to two.** `validateUsername` was an exact port of K3 (`_id`
  only) and the screens were exact ports of K1 (`id` only). Reading both is
  what makes the helper impossible to disagree with itself, and Kotlin's own
  interchangeability rests on an equality **the port cannot yet enforce**,
  because nothing creates a guest row. On every row Kotlin produces the two
  columns are equal, so no answer changes; where they differ the helper errs
  towards "guest", withholding a privilege rather than granting one. The one
  direction to watch: if a future `migrateGuestUser` re-keys `_id` and leaves
  `id`, that row reads as a guest and its name becomes re-takeable. Kotlin's
  `migrateGuestUser` sets both — keep it that way.

**Deliberately not folded in**: `isGuest()`'s role clause, pinned by a test
named after the omission so it is visible rather than forgotten.

### Four Drift query bodies stay as they are

`app_database.dart` carries `like('guest%')` in four DAO predicates
(`RatingDao`, `CourseProgressDao`, `pendingLoginUploads`, `AchievementDao`).
Those are **faithful transcriptions of Kotlin's own SQL** — `AchievementDao.kt:19`,
`CourseProgressDao.kt:28` and `RatingDao.kt:35` all write `NOT LIKE 'guest%'`
verbatim — and a SQL `WHERE` cannot call a Dart predicate, so pinning them to
the Kotlin query text is the closest thing to one source of truth available
there. The guard test exempts that file and says why. They were also not this
lane's to edit.

## 4. Defects, each demonstrated failing first

### 4.1 Both settings guest gates read a provider the screen never watches

`settings_screen.dart` referenced `sessionProvider` exactly twice, both
`ref.read(...).valueOrNull` inside an `onTap`, with no `watch` and no `listen`
anywhere in the file. So the session was `null`, `session != null` was false,
the gate fell through — and a guest reached **`clearAllData()`, the only
destructive action in the app**, plus the storage tools. Neither gate had ever
had a test, which is how it survived.

The **fifth** instance of this shape (`take_exam_screen` Phase 100,
`add_resource_screen` 102, `public_survey_screen` and `take_survey_screen`
104/105). Latent in the shipping app for the usual reason — `router.dart:644`
holds a lifetime `ref.listen(sessionProvider)` and `:201` holds the redirect
while the session is loading — and live for any harness that does not install
the router, which is exactly why it was untestable.

Fixed by **watching** at build and passing the row down, rather than awaiting
`.future` in each callback: the value is read by two widgets on every rebuild,
so watching is both cheaper and the shape that cannot regress.

Kotlin has no equivalent hazard: `SettingsActivity.kt:183` resolves the user in
`onCreatePreferences` and `:247`/`:284` each re-resolve it inside the handler.

*Failing-first:* both guest tests find 0 guest dialogs; the member test passes.

### 4.2 An author lost their own voices the moment their account uploaded

`voices_screen.dart:115` was `row.userId == (user.couchId ?? user.id)` — a
*preference* between the two id columns. `VoicesAdapter.matchesCurrentUser`
(`VoicesAdapter.kt:666-669`) is an **or**:

```kotlin
if (id.isNullOrEmpty()) return false
return id == currentUser?._id || id == currentUser?.id
```

Same rule as `UserDao.getById`'s `WHERE id = :id OR _id = :id`, and the same
reason Phase 107 gave for it: a member registered on this device authors rows
under the locally-minted `'<millis>'` key and gains a `couchId` only once the
upload lands. Under the preference, every voice they had already posted stopped
being theirs — no edit, no delete — at the moment their account got a server
identity.

Ported as `UserMapper.matchesUser`, including the empty-or-null guard that
comes first in the Kotlin and is load-bearing: without it a row with no author
matches a session with no `couchId`.

*Failing-first:* exactly one of four new cases fails — the local-id post finds
no edit action.

### 4.3 The `password` column's documentation stated a false Kotlin fact

`tables.dart:42` said "Only ever set for guest users, which have an empty
`_id`", echoed in `user_mapper.dart:122` ("which is the guest shape") and
`user_repository.dart:178`. **A guest row's `_id` is `guest_ada`.**
`applyJsonToUser:261` reads `_id` *after* `_id = newId`, so the guest branch is
never taken and a guest's password stays null; guests are never authenticated
by comparison at all, because guest re-entry goes back through
`showGuestLoginDialog` (`GuestLoginExtensions.kt:64`), not `authenticateUser`.
The rows that do carry a plaintext password come from the **offline
become-member** path, whose `createMember` document has no `_id`
(`UserRepositoryImpl.kt:570-579`).

Not a comment nit: it is the specification the next lane will build
`createGuestUser` against, and following it would mint `couchId = null`, which
lands the row in `UserDao.pendingSyncUsers` (`couchId IS NULL | '' | isUpdated`)
and gets it POSTed to `_users` by `user_uploader.dart:91`. Kotlin never uploads
a guest, and the thing stopping it is precisely that `_id` is non-empty.

All three comments corrected, and `tables.dart` now names the constraint a
guest-creator has to honour. (Comment-only; no schema effect.)

### 4.4 `loginOffline`'s `isGuest` was true for exactly the rows that are not guests

Same misreading in code. The local was named `isGuest` and the branch it
selects is Kotlin's `if (it._id?.isEmpty() == true)` (`:862`) — accounts with
**no server identity**. A real guest row (`couchId = 'guest_ada'`) takes the
*other* branch and fails against a null `derived_key`, which is correct. Renamed
to `hasNoServerIdentity`, doc comment rewritten.

And its test had absorbed the same error: *"authenticates a guest against the
plaintext password"* seeded `id: 'guest-1'` with no `couchId` — a row
`createGuestUser` cannot produce. Renamed to what it tests, re-seeded to a
`'<millis>'` member, and a second case added pinning that a real guest row is
**not** authenticated by the plaintext branch. That case matters: a guest row
has no password, so had the branch been taken, `null == null` would sign a guest
in on an empty password.

The port's `couchId == null || couchId.isEmpty` remains equivalent to Kotlin's
`_id?.isEmpty()`, because `UserMapper.fromDoc` normalises the absent `_id` to
`null` rather than `''`. That is now written down.

### 4.5 Two fixtures spelled the prefix a fourth way

`home_screen_test.dart:676` seeded `_user('guest-ada')` — a **hyphen** — while
the same file's three other guest fixtures use `guest_ada`. The
five-character rule could not tell them apart, so a row `createGuestUser` can
never write had been standing in for a guest. Under `guest_` the test fails
loudly (the key/iv sync fires for what is now a member), which is the point.

`inactive_dashboard_screen_test.dart:166` had the same hyphen, and was wrong on
a second count: `rolesList: const []`, where `buildGuestUserJson` sends
`roles: ["guest"]`. Both corrected. The empty roles list is *kept* there, with
a comment saying why: a real guest row's `["guest"]` would hold
`rolesList.isEmpty` false and shut the inactive branch on its own, so the empty
list is what makes that test about the `!isGuest` clause rather than about the
roles.

Neither fixture had to be hunted for — the tighter prefix surfaced both by
failing, which is most of the argument for taking it.

## Found and left

* **`logCourseVisit` carries a guest gate Kotlin has nowhere.**
  `activities_repository.dart:230`. Kotlin's chain is ungated at every layer —
  `TakeCourseFragment:246` → `TakeCourseViewModel:55` →
  `CoursesRepositoryImpl:569` → `ActivitiesRepositoryImpl:88-108` — and
  `CourseActivityDao.getPendingUploads` does not filter guests either (the
  port's counterpart faithfully doesn't). So a guest opening a course logs a
  visit in Kotlin and nothing in the port. The other two `_isGuest` calls in
  that file do have counterparts (`UserSessionManager:102`,
  `ActivitiesRepositoryImpl:234`). Left: the file is outside this lane, and
  which way to resolve it is a judgement (Kotlin's own upload config filters
  guests elsewhere, so the gate may be the better behaviour).
* **Exam submissions from a guest are not filtered.**
  `SubmissionDao.pendingUploads` is `userId = ? AND isUpdated = 1`;
  `UploadConfigs.kt:239-251` sets `filterGuests = true` on `ExamResults` (only
  — the `Submissions` config at `:253` does not). Latent. Lane B's file.
* **`RatingDao.pendingUploads` drops Kotlin's `userId IS NULL OR` disjunct.**
  `RatingDao.kt:35` is `isUpdated = 1 AND (userId IS NULL OR userId NOT LIKE
  'guest%')`; the port has only the second half, and in SQL a `NOT LIKE`
  against NULL is NULL, so a null-`userId` row would be withheld. Inert today:
  the port's `Ratings.userId` is non-nullable (`tables.dart:372`) and
  `saveRating` requires it. Recorded because the disjunct's absence would
  become live if that column were ever relaxed.
* **`activities_provider.dart:119` reads `sessionProvider` without watching
  it** — the same shape as §4.1, in a file outside this lane. Its Kotlin
  counterpart (`recordSyncUserChallengeAction`) has no guest check at all, so
  the gate is a port addition either way; the `.valueOrNull` is the part worth
  fixing.
* **`team_voices_screen.dart:48` gates posting on `membership != null`** where
  `TeamsRepository.kt:19` is `!isGuest && (isMember || isPublic)`. The
  `!isGuest` half is subsumed (a guest is never a member); the `|| isPublic`
  half is missing, so a non-member cannot post on a public team.
* **`team_members_screen.dart:203-225` omits `RequestsAdapter.kt:63-67`'s
  `isRequester` disable.**
* **`take_exam_screen_test`'s verification-photo case is a wall-clock race with
  a hardcoded round count**, and it is worth naming because it cost this lane
  a full diagnostic cycle. `settleExam(tester, rounds: 40)` waits a fixed
  number of `runAsync` rounds for `Directory.create` + `writeAsBytes` + the
  drift row that follows — the count is hand-tuned, and its own comment says
  so. It failed once here on a busy machine and passed on two subsequent full
  runs, in isolation, and on the base tree; my diff shares no import with
  `take_exam_screen.dart`. So it is not this lane's defect, but it is a real
  fragility that any branch adding test files can trip. The fix is to poll
  until the row exists (with a deadline) instead of counting rounds. Lane B's
  file, so reported rather than edited.

  Reducing the pressure *this* lane adds was in scope and is done:
  `guest_predicate_parity_test` now reads asynchronously with a whole-file
  pre-filter instead of `readAsLinesSync` over ~400 files, so its isolate no
  longer starves its neighbours.

## For the integrator

* **No schema change.** Drift stays at v44. `app_database.dart` is **not
  touched at all** (see §3 on the four query bodies). `tables.dart` is touched,
  but only a doc comment — no column, type, converter or index — so no codegen
  and no migration.
* **No new `app_en.arb` keys.** Nothing user-facing changed.
* **Outside the named lane set**, and worth a targeted look: `tables.dart`
  (comment only), `lib/ui/dashboard/dashboard_drawer.dart`,
  `lib/providers/activities_provider.dart`,
  `lib/repository/activities_repository.dart`,
  `lib/repository/voices_repository.dart` — one predicate line each, taken
  because leaving a raw literal behind would defeat the guard test. No sibling
  lane owns any of them.
* Files touched: `lib/data/local/user_mapper.dart`, `lib/data/local/tables.dart`,
  `lib/repository/{user,activities,voices}_repository.dart`,
  `lib/providers/activities_provider.dart`,
  `lib/ui/dashboard/{home_screen,dashboard_drawer}.dart`,
  `lib/ui/settings/settings_screen.dart`, `lib/ui/voices/voices_screen.dart`,
  and `test/{guest_predicate_parity_test,data/local/user_mapper_test,repository/user_repository_test,ui/home_screen_test,ui/inactive_dashboard_screen_test,ui/settings_screen_test,ui/voices_screen_test}.dart`.
