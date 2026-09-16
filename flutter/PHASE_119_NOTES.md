# Phase 119 — the five missing sync walks

Phase 116 audited data-path reachability across the port and reported, without
fixing, that five walks Kotlin's `TransactionSyncManager`/`SyncManager` run have
no counterpart here. It called them "one phase, not five", and they are: the
walks share a shape, and four of the five had a Dart writer already sitting
uncalled — plumbing laid for a pull nobody wrote.

This is the same failure class Phase 113 found in the exam path, one level up.
**The data never arrives at all**, so every screen reading that table is dead no
matter how well it is written — and green tests cannot see it, because a
repository test builds its own rows and a screen test builds its own screen.

## The five, and what each one feeds

| walk | Kotlin | what was dead | Phase 116 |
|---|---|---|---|
| `shelf` | `SyncManager.kt:441-540` → `processShelfParallel` | My Library, My Courses, course progress, both home cards, the completed-course stars | D1/D2 |
| `tablet_users` | `TransactionSyncManager.kt:230-232` | member detail ("Unknown member" for everyone), the team leaderboard (one row), every team member's name (raw `org.couchdb.user:bob`) | D15 |
| `ratings` | `:242-244`, via `HeavyTableSyncWorker` | every rating average was the single number this device had typed | D4 |
| `tasks` | `:254-256` | a task made on Planet or by a teammate never arrived | D16 |
| `achievements` | `:260-262` + `downloadCvAttachmentsFromBatch` | a second device showed a blank ledger, and saving there 409'd into the outbox's permanent-failure branch | D19 |

Four of the five confirm Phase 116's "plumbing laid for a caller that does not
exist": `AchievementsRepository.syncAchievements`, `CoursesRepository.sync`'s
`shelfId` parameter, `TeamTaskDao.upsertAll` and `AchievementFiles.hasResume` —
the last of which carried a doc comment naming itself *"the Kotlin's
`!file.exists()` guard on the sync-in"* for a sync-in that did not exist.

No schema bump. Every table and column the five need already exists at
`schemaVersion` 45; the two gaps found are recorded under *Reported, not fixed*.

## The prune decision: none of the five prunes, and it is not a shortcut

The Kotlin issues **no delete of any kind** on any of these five paths — checked
per walk, not assumed. The only prune in the whole Kotlin sync is
`SyncManager.kt:396 → MyLibraryDao.deleteStalePublicNotIn`, which belongs to the
*resources* walk (phase 2), not to the shelf pass that follows it.

That matters more here than usual, because Phase 52 fixed exactly this shape for
resources (a mid-walk batch failure must set a flag and skip the cleanup) and
Phase 116 then found the resources walk clearing My Library on every sync
anyway. So, per table:

- **`users`** — also holds accounts registered offline that have never reached
  the server, and the session is restored by looking the signed-in user up here.
  A `deleteNotIn` would sign the user out. Kotlin's only delete is the guest
  re-key.
- **`team_tasks`, `achievements`** — preserved local-authority tables whose rows
  are authored here and keyed by a locally-minted id (`task-<micros>`) or a
  derived one (`<userId>@<planetCode>`). Neither appears in any `_all_docs` keep
  set before it has been uploaded *and* pulled back.
- **`rating`** — same: a rating submitted here is keyed by a locally-minted id.
- **shelf** — the stamp is a union (`MyLibrary.setUserId`,
  `MyCourse.setUserId`), so pulling one user's shelf must leave another user's
  membership on the same row intact. A prune here would be a prune of the
  *resources* and *courses* tables from a `keys` subset, which is categorically
  wrong.

`walkAllDocs` therefore takes no keep set at all, and says so at the top: a
caller that needs `deleteNotIn` needs the keep set, and the keep set is only
trustworthy when every page landed. The three walks that do prune (resources,
courses, tags) keep their own loops with their own `hadBatchFailure` guards.

## The local-authority decision: a pending edit wins

Three of the five tables have a column the device writes and the server document
knows nothing about. Kotlin overwrites all of them, because `fromJson` /
`insertRatingsFromSync` build a **fresh entity per document** and upsert it. That
is the shape Phase 56 (security data), Phase 74 (reactions) and Phase 98 (read
state) each had to stop being faithful to, and this phase makes the same call:

- **`team_tasks.isNotified`** — device-local, on neither the document nor
  `TeamTask.serialize`, and the only thing making a deadline reminder
  once-only. Kotlin resets it on every sync, so the same reminder fires again
  after each sync. The port never writes the column from the walk, so the flag
  survives.
- **A row still flagged `isUpdated` / `uploaded = false`** (ratings, tasks,
  achievements) takes **only the identity columns** — `_id` and `_rev`. Its
  content is an edit the user made offline that has not reached the server;
  adopting the server's older copy discards the edit *and*, by clearing the
  flag, stops it ever uploading. Kotlin does both.
- The `_rev` half is not incidental. Phase 116's D19 trap was that an
  achievement row carries no `_rev` until a walk supplies one, so its POST 409s
  and `OutboxDrainer` classifies 409 as permanent (`code < 500`) and abandons
  the row with no snackbar and no log. Handing the row the `_rev` is what makes
  its **next enqueue** a PUT. The same is true of ratings, whose
  `markRatingUploaded` never records the server id at all.

  **Not a retry, though — the next enqueue.** Every uploader serializes the row
  at queue time and `OutboxDrainer` replays `row.payload` verbatim, so a `_rev`
  stamped after a payload was queued never reaches that payload. It closes the
  case the defect is actually about — a fresh device, where the walk runs before
  the user has edited anything — and leaves the narrow one open: a row queued
  offline, then synced, then drained still POSTs without its `_rev`. Closing
  that means re-serializing at drain time or re-queueing after a sync, which is
  the outbox's business rather than this phase's. Reported below.

Mechanically, "takes only the identity columns" is an UPDATE
(`recordServerIdentity` / `recordServerRev`), not a partial companion through
the upsert: Drift validates an upsert's companion against the **insert** path,
so one carrying only `id`/`couchId`/`rev` is rejected for the required columns it
omits even though the row already exists. `OutboxDao.patch` already carries that
warning in a comment; this is the second and third place it bites.

`users` needed nothing new **for the device-only columns**: `UserMapper.fromDoc`
already returns `Value.absent()` for `key`, `iv`, `isUpdated`, `password` and an
unchanged `userImage`, and `insertAllOnConflictUpdate` leaves absent columns out
of the `DO UPDATE SET` list (verified against Drift, not assumed). Losing
`key`/`iv` would make every health record already encrypted with them
permanently unreadable.

It is **not** true of the twelve guarded profile columns, and the difference is
deliberate rather than overlooked — see *A pending profile edit does not win*
below.

## The defects, each demonstrated failing first

### 1. `TeamTaskDao.watchForTeam` required `status = 'active'`

The trap Phase 116 predicted, and the reason this one is worth stating first:
**the walk would have worked and the screen would have stayed empty.**

`TeamTask.serialize` (`model/TeamTask.kt:56-72`) emits `_id`, `_rev`, `title`,
`deadline`, `description`, `completed`, `completedTime`, `assignee`, `sync`,
`link` — and **no `status`**. `JsonUtils.getString` returns `""` for a missing
key, so `TeamTask.fromJson` writes `status = ""`. Kotlin's own predicate is
`status IS NULL OR status != 'archived'` (`TeamTaskDao.kt:11`, `:20`), which
`""` satisfies. The port asked for `'active'`, which it does not.

Reverting the predicate turns 3 of the 9 task tests red — *a task the server
sent reaches the team list*, *an archived task is excluded* and *a synced task
the device already holds is updated, not duplicated*, the last of which also
reads through `watchForTeam` on a `status = ''` row — while the other six still
pass, which is exactly what the defect looked like from outside: a sync
reporting success over an empty list. The column is non-nullable here with
default `'active'`, so the Kotlin's `IS NULL` arm has nothing to match and is
dropped; the walk writes the document's status verbatim, empty string included.

### 2. The shelf stamp was keyed on the wrong id

Found by the ground-truth audit before it shipped, and the sharpest thing in the
phase. A shelf document is keyed by its owner's **CouchDB** id — that is what
`shelf/_all_docs` returns — but every reader in this app scopes by
`session.user.id`, the **local row** id: `MyLibraryDao.watchResources`,
`CourseDao.watchCourses`, the home library and course cards, the
completed-course stars.

For an account that first appeared server-side the two are the same string. For
a member **registered on this device** they are not: that row keeps its
locally-minted `'<millis>'` id and gains a `couchId` only when the upload lands
(the Phase 107 identity rule, `user_mapper.dart:26-35`). Stamping the raw shelf
id for such a member writes a `userId` no reader can match — My Library stays
empty and the sync reports success.

`ShelfSyncRepository._localUserId` resolves it through `UserDao.getById`, which
matches either column. Removing the resolution turns *a member registered on
this device gets the stamp their screens read* red and leaves the other seven
shelf tests green. This is also why the sync centre runs `tabletUsers` **before**
`shelf`: the mapping has to exist.

### 3. A rating came back as two rows

`markRatingUploaded` only clears the dirty flag — it never records the
server-assigned `_id` (`UploadConfigs.kt:294-304`) — so a rating submitted here
keeps its locally-minted id for life. `insertRatingsFromSync` keys its row by
the CouchDB `_id` (`Rating().apply { id = _id }`), so the user's own rating comes
back as a *second* row and `getAggregate` counts both: rate a resource 5 stars,
sync twice, and the summary reads "2 ratings, average 5".

The port reconciles instead — a document whose `(type, item, userId)` matches a
local row that has no CouchDB id yet updates that row. Keying by `_id` alone
turns two ratings tests red. Same shape for tasks (`getByAnyIds` resolves through
`docId` first); a locally created task otherwise appears twice under its team.

### 4. Pending edits and the notification flag

Covered above; each demonstrated by disabling its guard:

| disabled | test that goes red |
|---|---|
| the ratings identity-only branch | *an unsent rating keeps its rate and stays queued* |
| the tasks identity-only branch | *an edit made offline survives the pull that carries its old copy* |
| writing `isNotified: false` from the walk | *a re-pull does not re-fire a deadline notification* |
| the achievements pending branch | *an edit not yet uploaded survives the pull, and gains its rev* |

### 5. The guest adoption dropped the guest's key

Not shipped — caught between writing and committing, by the ground-truth pass.
Kotlin's guest branch assigns `this.id = id; this._id = id` **before** calling
`applyJsonToUser` (`UserRepositoryImpl.kt:1126-1131`), because `applyJsonToUser`
only reassigns `id` when it is blank. The port's first cut passed the guest row
to `UserMapper.fromDoc` as `existing`, which keys the companion on
`existing.id` — still `guest_ada` — so the row was updated in situ under the
guest key and the account stayed unresolvable by its CouchDB id.

The fix carries the guest's device-only columns across by hand
(`_adoptGuest`). That is needed because the port rebuilds the row rather than
mutating it, as Kotlin does: written under a *new* key there is nothing for
`Value.absent()` to preserve, so `key`, `iv`, `password` and `userImage` would
each silently take their default.

## Deliberate divergences from the Kotlin

Recorded because each is a place where a later reader will find the two apps
behaving differently and should not have to re-derive why.

1. **The three reconciliations and preservations above.** Kotlin duplicates the
   row and clobbers the edit in every one of these cases; the duplication is
   observable (a rating counted twice, a task listed twice under its team).
2. **The shelf walk covers `resources` and `courses` only.**
   `Constants.shelfDataList` has four arms, but `batchInsertMeetups(docs)` and
   `batchInsertMyTeams(docs)` take **no shelf id** — they record no membership,
   and they re-insert documents the port's own `meetups` and `teams` walks pull
   in full, from the same databases, in the same pass. There is nothing for them
   to add. (Kotlin's meetups arm is in fact *destructive* here:
   `Meetup.fromJson(doc, "", existing)` writes `userId = ""`, clearing
   attendance. Not porting it avoids inheriting that.)
3. **The courses arm writes the course and its steps, not the embedded exams and
   surveys**, where `upsertRoomCoursesFromSync` writes all four. The `courses`
   walk runs earlier in the same pass over the same documents and owns those
   tables, including the step-join release whose churn Phase 113 had to fix.
4. **No six-hour shelf cache.** Kotlin caches "which shelves have data" in
   preferences for six hours (`SyncManager.kt:468-491`), so a shelf that gains
   its first resource is invisible to sync for up to six hours. The port re-reads
   every time — and, having just read the documents, does not re-fetch each shelf
   individually the way `processShelfParallel` does.
5. **`hasShelfData` keeps Kotlin's key set verbatim**, `teamIds` included, which
   is a Kotlin bug: `Constants.shelfDataList` processes `myTeamIds` and the
   string `teamIds` appears nowhere else in the Kotlin codebase, so a shelf whose
   only non-empty array is `myTeamIds` is judged "no data" and skipped entirely.
   Not "fixed" here because this walk processes neither team key; widening the
   set would only spend two requests on a shelf with nothing in it for us.
6. **`sendToNation: null` does not abort the page.** `Achievement.fromJson:120`
   calls `.asString` on the raw element, which throws on a JSON null out of a
   function with no per-document try/catch, taking the whole page with it. The
   port's coercion tolerates it.
7. **`ratings.user` and `ratings.createdOn` are dropped**, and **`team_tasks.sync`
   and `team_tasks.link` are dropped** — see *Reported, not fixed*.
8. **A rating's rater is resolved to the local `users` row id**, the same rule
   the shelf walk follows and for the same reason: the document names the rater
   by CouchDB id, `submitRating` stores `session.user.id`, and for a member
   registered on this device those differ.
9. **A count response with no `total_rows` fails the area** rather than
   reporting an empty success. `JsonUtils.getInt` reads a missing key and a real
   zero alike, and a green tick over a walk that never issued a page request is
   the worst possible outcome for this phase in particular. Kotlin has no count
   query at all and would simply page. The port's other four count-first walks
   still conflate the two.
10. **The walks are foreground sync-centre areas.** Kotlin runs `ratings` in
   `HeavyTableSyncWorker` after the main sync, and the other four in phase 1 or
   3 of the sync itself. The port has no heavy-table worker; every pull is an
   area of the sync centre. The page sizes are Kotlin's (`ratings` 20, the rest
   its 1000 default reduced to an `AdaptiveBatchProcessor` seed).

### A pending profile edit does not win, unlike the other three tables

`applyJsonToUser`'s rule for `firstName`…`age` and `joinDate` is "a non-empty
incoming value wins" (`UserRepositoryImpl.kt:205-256`), and the port's
`UserMapper._keepingStored` is a faithful port of it. So the `tablet_users` walk
reverts a profile edit made offline, where the ratings/tasks/achievements walks
preserve theirs. Newly reachable, since before this phase the port never pulled
`tablet_users` at all.

Left faithful, for a reason that is about consequences rather than about
fidelity alone: `UserMapper.fromDoc` leaves `isUpdated` absent, so the row stays
dirty and the outbox entry `_queueUserUpload` made at edit time still carries
the edited payload. The edit reaches the server; what the user sees locally is
stale until the next pull after it lands. The other three had a strictly worse
failure — the walk cleared the flag, so the edit was discarded *and* never
uploaded. If a later phase decides the twelve columns should follow the same
rule as the other three, this is the fourth instance, not a new question.

## Reported, not fixed

**Two missing columns, each needing a schema number I am not allowed to
allocate.** Both are deferrable, and I have deferred them rather than working
around them:

- **`rating.user` and `rating.createdOn`.** `insertRatingsFromSync` writes both
  and `Rating.serializeRating` re-emits them. `RatingsUploader._toDoc` already
  rebuilds `user` from the `users` table and derives `createdOn` from
  `parentCode`, and a sync-in row is `isUpdated = false` so it never re-enters
  `pendingUploads` — the columns are write-only from the port's side today.
  `Ratings` is not a preserved table, so adding them is a plain `createAll`.
- **`team_tasks.sync` and `team_tasks.link`.** `TeamTask.fromJson` sets both and
  `TeamTask.serialize` re-emits them verbatim; `TeamTasksRepository.serialize`
  rebuilds them from `teamId` and the session's planet code, which is what
  `upsertTask` does for a locally authored row. `team_tasks` **is** a preserved
  table, so this one needs a hand-written `_addColumnIfMissing` step as well as a
  version.

**Two pre-existing defects in `MyLibraryMapper` that the shelf walk now
inherits**, both in the `resources` walk already and both left alone because
they are that walk's, not this phase's:

- `my_library_mapper.dart:85` reads `'tag'` where `MyLibrary.kt:291` reads
  `'tags'`. Phase 116 called this inert; it is inert only because nothing else
  fills the column.
- `my_library_mapper.dart:93` reads `privateFor` as a string, while Kotlin reads
  the nested `privateFor.teams` (`MyLibrary.kt:294-298`). Dart's
  `JsonUtils.getString` falls through to `toString()`, so a document carrying
  `{"privateFor": {"teams": "abc"}}` stores the Dart literal `{teams: abc}` —
  the exact shape of the Phase 104 `SurveyMapper.choices` defect, and a value
  no team-id predicate can ever match.

**A `_rev` stamped after a payload was queued never reaches it.** All three
uploaders (`ratings_uploader.dart:44-60`, `achievements_uploader.dart:47-58`,
`team_tasks_uploader.dart:21-34`) serialize at enqueue time and `OutboxDrainer`
replays the stored body, and nothing re-queues after a sync. So a row queued
offline and then synced still sends its original `_id`-less body, and CouchDB
mints a second document. The fix is to re-serialize at drain time, or to sweep
pending rows after a sync; both are outbox changes.

**The shelf pull ignores `removed_log`.** `ShelfRepository.upload` subtracts it
(`shelf_repository.dart:112-118`) — that subtraction is what makes "leave"
stick — while the new pull re-stamps `userId` on a resource the user removed
offline whose removal has not uploaded yet. Kotlin is identical, so this is not
a regression; it is worth naming because `removed_log` is a preserved
local-authority table whose stated purpose is exactly this, and only one of its
two directions now honours it.

**`TeamMapper.fromDoc` has none of `insertMyTeam`'s document rules** — no
`status == "archived"` early return, no `membership`-supersedes-`request`
delete, no already-a-member `request` skip (`TeamsRepositoryImpl.kt:1223-1237`).
Not reachable from this phase, since the shelf walk does not route teams, but it
is the reason a future phase should not casually add that arm.

## Files touched outside this lane's set

None. Lane B owns `submissions_repository.dart` and
`notifications_repository.dart` and their mappers and tests; neither is touched.
Lane C owns `lib/l10n/`, `test/l10n/` and `tool/arb_from_strings_xml.dart`; the
only file of theirs this phase touches is **`lib/l10n/app_en.arb`**, which the
brief explicitly designates for new strings, with five keys appended at the end
of the file to keep the hunk out of the way: `ratings`, `syncShelfDescription`,
`syncMembersDescription`, `syncRatingsDescription`, `syncTasksDescription`,
`syncAchievementsDescription`.

Nine existing test files gained a `PlanetApi` argument, because three
repositories now take one; a shared `test/support/mock_planet_api.dart` holds
the double. An unstubbed mocktail mock throws on any call, which is the right
default: reaching the network from a local-only test should fail loudly.

## A second audit, on the finished implementation

Phases 110, 113 and 116 each had a second `parity-auditor` pass find real
defects in their own green work. So did this one, and the first finding inverts
the phase's own headline for one of the five walks. The suite was green (1978
tests), format and analyze clean; that is not evidence.

| # | defect | why the first pass missed it |
|---|---|---|
| 1 | **the achievements walk skipped the server ledger on any device where the screen had been opened** | the "pending edit" test used a real edit |
| 2 | ratings: two documents for one item collapsed onto one row, dropping a rating from the average | no test sent two documents for one item |
| 3 | ratings: the rater was matched by CouchDB id where the table stores the local row id | the shelf walk had this rule; ratings did not |
| 4 | guest adoption carried 5 of 17 device-only columns | the guest test supplied the profile fields in the document |
| 5 | `syncAchievements` used raw casts, so one non-string scalar lost the whole page | no test sent a malformed document |
| 6 | a count response with no `total_rows` ticked green without a single page request | every walk test stubbed one page answering every `skip` |

**Finding 1 is the one to learn from, and it is a rule this port already had.**
`Achievements.uploaded` is the port's *inverted* name for Kotlin's `isUpdated`,
and its column default is `false` — the wrong way round, since `Achievement()`
starts `isUpdated = false`. `getOrInitialize` wrote no value, so a blank
placeholder row read as "an edit the user has not uploaded". The walk then
preserved that blank row *instead of* the server's ledger — permanently, because
nothing ever clears the flag — and stamped it with a valid `_rev`, arming the
next Save to PUT an empty ledger over the real one. Before this phase that PUT
had no `_rev` and 409'd, which is what had been protecting the server copy.

`achievementEntryProvider` calls `getOrInitialize` on watch, so **opening the
achievements screen once was enough**. The preservation rule this phase adopted
turned the exact defect it was meant to fix into a permanent one, and it did so
by reading a column default as a user intention. *A flag whose default means
"dirty" cannot distinguish a placeholder from an edit.*

**The largest gap was in the tests, not the code.** Every `stubWalk` in the five
new files returned the whole document set in one page and matched any `skip`, so
nothing exercised the loop that feeds the writers: changing `skip += rows.length`
to `skip += 1` still passed all 38. `test/core/sync/table_walk_test.dart` now
drives the loop against a corpus that honours `limit`/`skip` — multi-page,
short-page, `_design`-only page, error rows with no `doc`, a mid-walk failure,
and the malformed count.

The pass also corrected six factual claims in this file and in the new doc
comments — the "`AdaptiveBatchProcessor` grows it" claim (`initialSize` is the
ceiling, not the floor), the "no delete of any kind" claim (the guest re-key is
a delete), a "1000 documents" figure where the page is 100, a past-tense claim
that `CoursesRepository.sync`'s `shelfId` now has a caller (it still does not —
the shelf walk calls `CourseMapper.fromDoc` directly), the `_rev`/retry claim
above, and "2 of the 9 task tests" where it is 3. `UserDao.getByAnyIds` also
gained the chunking every other id-list query in that file has; it binds each id
twice, so it chunks at half the usual size.

Two smaller behaviours changed with it: a failed shelf-probe chunk now skips that
chunk and carries on, as `checkShelfBatchForDataOptimized` does, rather than
failing the whole pass; and `DashboardSyncArea`'s two load-bearing orderings are
now pinned by a test, because the existing one asserted
`items.map(…) == DashboardSyncArea.values`, which is tautological with respect to
order and would survive an alphabetising refactor that broke both.

Things the pass checked and found sound, recorded so nobody re-derives them: the
`teamIds`/`myTeamIds` verbatim copy, the meetups/teams arms genuinely taking no
shelf id, the courses arm's exam/survey skip (the step→assessment join lives on
`Exams.stepId`, not on `CourseSteps`, so the step prune cannot reach it), the
`ratings` page size, Drift leaving `Value.absent()` columns out of
`DO UPDATE SET`, no `deleteNotIn` reachable from any of the five, and that the
shelf walk's sequential loop is *safer* than Kotlin's `Semaphore(6)`, which is a
lost-update race on `my_library.userId`.

## The tests

51 across six files, plus the constructor updates in nine existing ones.

Each document is built in the shape CouchDB actually stores — `_users` keyed
`org.couchdb.user:<name>` with the photo as an `_attachments` entry, a task with
`link: {teams: …}` and no `status`, a rating with the rater embedded as a `user`
object, a shelf with `resourceIds`/`courseIds` arrays — rather than flattened to
the columns the row happens to have. **That is the whole point of the exercise**:
the `status` defect was invisible to a fixture written from the row's columns,
and would have stayed invisible to one.

Every walk has a `never prunes` test that puts a locally-authored row in the
table, runs a walk that does not list it, and asserts it survives.
