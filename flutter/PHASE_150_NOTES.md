# Phase 150 — `my_library` preservation, and four corrections in `app_database.dart`

Lane A of a four-lane round. Five items, all landing in
`flutter/lib/data/local/app_database.dart` and its neighbours:

1. **The schema bump destroys resources the user created offline** and leaves
   `resourceOffline` false on every downloaded resource
   (`PHASE_146_NOTES.md` *Reported, not fixed* item 1). Outranks the rest.
2. The challenge tally excludes replies where Kotlin's counts them
   (`PHASE_147_NOTES.md` item 1).
3. Three false dartdoc claims on those same queries (`PHASE_142_NOTES.md`
   item 5, `PHASE_147_NOTES.md` item 2).
4. `HealthExaminationDao.markUploaded(id, null)` erases a stored `_rev`
   (`PHASE_148_NOTES.md` item 2) — a blocker for Lane C.
5. The `surveys` entry in `_localAuthorityTables` carries a claim its own phase
   retracted (`PHASE_143_NOTES.md` item 4).

Notes are written as the work lands; the *Reported, not fixed* section is at the
bottom.

## Method

1. Read the Kotlin by hand for each of the five claims before touching Dart —
   `NewsDao.kt`'s two challenge counters, `VoicesRepositoryImpl.postReply`,
   `HealthExaminationDao.kt` and `HealthRepositoryImpl`,
   `MyLibrary.insertMyLibrary`, `MyLibraryDao.getPendingUploads` and
   `UploadConfigs.getResourcesConfig`, and the port's
   `SurveysRepository.individualSurveys`.
2. A `parity-auditor` pass at `effort: max` on nine lettered claims, aimed at
   the **ground truth**, before implementing.
3. Implement, each defect demonstrated failing first.
4. Mutation-test every fix.
5. A second `parity-auditor` pass at `effort: max` aimed at the finished,
   already-green code.

## Jobs 2 and 3 — the challenge tally, and two comments that misnamed it

Both confirmed against the Kotlin and both fixed in one edit, since they sit on
the same two queries.

`NewsDao.countDistinctCommunityVoiceDates` / `…ForUser` (`NewsDao.kt:60-73`)
have exactly three predicates: the time window, the optional `userId`, and
`viewIn LIKE '%"section":"community"%'`. The port's two queries each carried an
`_isTopLevel(r)` conjunct on top of that. `postReply` copies the parent's
`viewIn` verbatim (`VoicesRepositoryImpl.kt:319`) **and so does the port's**
(`voices_repository.dart:710-716`), so a reply to a community voice is a
community-section row in both apps: Kotlin credits the day it was posted and
the port dropped it. The port **under**-counted, which is the direction that
matters — the dialog told a learner they had not done something they had.

The two dartdocs went with the predicate. Each claimed to be a port of a
`NewsDao.getInTimeRange`, which Kotlin does not have, and each described its
result as "all top-level *community* voices" — a filter neither query
implements, because the community half is the caller's. They now name the real
counterpart, quote its SQL, and say which half lives in the caller.

**The third false claim reported alongside them was not in this file.**
`PHASE_142_NOTES.md` item 5's "The Kotlin filters `isCommunitySection` in
memory after the DAO query" is on `getCommunityVoiceDates` in
`voices_repository.dart`, and Phase 147 already corrected it there. The brief
listed all three as mine; two were. The one comment in `app_database.dart` that
*does* claim in-memory filtering — `watchTopLevelMessages`, three lines below —
is **true**: `VoicesRepositoryImpl.getTopLevelMessagesFlow:136-160` really does
map the whole list and read inside `viewIn` in Kotlin. It was left alone.

Three tests, driven through the production `createPost`/`postReply` rather than
hand-built rows, because the whole defect is about a reply's stored shape and a
fixture that set `viewIn` itself would assert about a document instead. Two red
before the change. The third is the guard that a team thread still never counts
— it is `isCommunityNews` in the caller that excludes it, not `_isTopLevel`, so
removing the top-level filter must not widen the tally.

## Job 4 — a null rev erased a health examination's stored one

Confirmed, fixed, and landed first: `PHASE_148_NOTES.md` records it as a
blocker for the lane doing 409-recovery work across the uploaders, so it went
out in its own commit with a PR comment routing the alignment.

`HealthExaminationDao.markUploaded(id, rev)` wrote `rev: Value(rev)`, so the
`HealthUploader` branch that has an id and no rev erased the revision the row
already held. `_rev` is the only thing that lets the next edit PUT rather than
conflict, and `HealthRepository.cacheDocuments` skips a locally dirty row
(`PHASE_148_NOTES.md` item 3), so nothing supplies it again — the record 409s
for the life of the install.

**The line numbers in the report were stale and its own correction said so, so
they were read rather than trusted.** The method is at `:3961`, not `:3812`;
the correct pattern is at `:1198`/`:2066` as the report's amendment says, not
`:1035`.

**One sub-claim needed settling before the comment could be written, and it
changes what the fix *is*.** Kotlin's single-id overload `markUploaded(id,
rev: String?)` *does* still write `_rev = :rev`, so on its own it has the same
defect. What `1004e90` added is the `@Transaction` map overload (`:36-46`),
which partitions the null-rev ids into `SET isUpdated = 0 WHERE _id IN (:ids)`
— and that is the one the app calls, from
`HealthRepositoryImpl.markHealthExaminationsUploaded:66-69`, the only caller
anywhere. The single-id overload's only caller is the non-null half of that
partition, so a null never reaches it. **So the port's `Value.absent()` is
parity, not an improvement on the Kotlin** — which is the opposite of what it
would have been had the live path used the other overload, and is why it was
worth the two greps.

Blank is deliberately not folded in with null: Kotlin partitions on
`it.value == null`, so an empty-string rev is written. Pinned, because folding
it in is the obvious "tidier" reimplementation.

`HealthRepository.markUploadedBatch` delegates to this DAO method, so its copy
of the bug is fixed by the same line. It still has zero callers.

Three mutations, each reding a different test: restoring `Value(rev)`, folding
blank in with null, and leaving the dirty flag set.

**A method-body mutation needs an anchor, not a text substitution.** Three
scripted `str.replace` mutations of `rev: Value(rev)` landed on
`TeamTaskDao.markUploaded` and `NotificationDao.markSynced` instead of the
health method — a two-line pattern that reads as unique is not, in a
4800-line file of DAOs that all write a rev and clear a flag. The tell was
that all three mutations red the *same* test, which is not what three
different mutations do; the file was reset and every mutation re-applied
through an exact-match edit. Phase 135's rule was *read the tree after an
audit pass*; this is the same rule for one's own mutation runs, and `git diff`
with context is what answers it.

## Job 5 — a retraction that reached the notes and not the code

Confirmed. The `surveys` entry in `_localAuthorityTables` still said an
orphaned step-joined survey row is "unreachable from the UI (the step tile is
gone with the course)", which `PHASE_143_NOTES.md` item 4 retracts.
`SurveysRepository.individualSurveys` (`surveys_repository.dart:41-46`) filters
on `!row.teamShareAllowed && (row.teamId ?? '').isEmpty` and tests neither
`stepId` nor `courseId`; a course-embedded survey arrives with neither team key
set (`SurveyMapper.fromCourseDoc` reads a missing `teamShareAllowed` as false
and a missing `teamId` as null), so the orphan satisfies that predicate exactly
and is listed at `/life/surveys`, tappable, for good.

The sentence replaced is the one that decided the accretion was not worth
fixing, which is why it is worth correcting rather than deleting.

## Job 1 — `my_library` is preserved, and two things had to land first

### The membership test, answered

***Can a sync restore this?*** For `my_library`, no, twice over — and the first
half is worse in the port than in Kotlin, which the brief's framing did not
say:

* **A resource the user created offline.** `saveLocalResource` writes a row
  with no `_rev`, no document and no outbox entry. Kotlin uploads its
  equivalent: `MyLibraryDao.getPendingUploads` is `WHERE _rev IS NULL`
  (`MyLibraryDao.kt:94-95`) → `UploadConfigs.getResourcesConfig` (endpoint
  `resources`) → `UploadManager.uploadResource:167-200`, which uploads the file
  as an attachment too, and it runs in the ordinary sweep (`AutoSyncWorker
  :129`, `UserDataWorker:84`, `TeamsRepositoryImpl:928`). **The port has no
  resources uploader at all** — no `resources_uploader.dart`, no entry in the
  outbox handler registry, nothing that POSTs to a `resources` endpoint. So in
  Kotlin the row converges on a document and the loss window is the gap before
  the next sweep; in the port the window never closes and the row is the only
  copy that will ever exist.
* **`resource_offline` / `resource_local_address` / `downloaded_rev`, on any
  row.** Kotlin re-derives the flag from the disk on every pull
  (`insertMyLibrary` → `FileUtils.checkFileExist`, `MyLibrary.kt:263-265`,
  and it re-derives in *both* directions, so a deleted file clears it too), so
  its rebuilt table repairs itself on the next sync. `MyLibraryMapper.fromDoc`
  names none of the three, so the port's cannot: the bytes stayed on disk, the
  table said otherwise, and the bell asked the user to download every resource
  they already had.

So the table joins `teams`, `surveys` and `news` as a preserved hybrid.

### `schemaVersion` 49 was allocated and **is spent** — the implementation audit is why

The first cut did not bump, and argued — correctly, for DDL — that preservation
changes migration *behaviour* and not schema: a device on v47 runs
`onUpgrade(47 → 48)` under the new code and keeps its rows, a device on v48
already has the shape, and drift's bookkeeping (`PRAGMA user_version`, written
only after `beforeOpen` returns, which is null here) wants nothing.

**Sound argument, wrong conclusion, because it answered the wrong question.**
The implementation audit asked Phase 143's question about the rows this phase
newly *keeps* rather than the columns it adds, and the row class preservation
exists for comes out of the migration permanently self-contradictory. A device
that used Add Resource on any earlier build has, for each locally created
resource, `resource_offline = 1` and the raw `FilePicker` path in
`resource_local_address`, because the pre-fix writer copied no file. Old code
deleted that row. Preserving the table keeps it, and it is then a dead end that
**lies**: My Library shows the offline pin (the list sorts
`resource_offline DESC`), the detail screen offers *View* rather than Download,
the viewer finds nothing under `ole/<id>/`, and the Download it falls back to
cannot work either because `urlFor` needs a `couchId` a local row has never
had. **A dead end that lies is worse than the absence it replaced** — so the
fix would have *relocated* the loss rather than removing it, which is exactly
the failure this phase was briefed to avoid and quotes in its own comments.

v49 therefore carries a repair, and the repair is why the number is spent: the
affected population includes devices already on v48, which never enter
`onUpgrade` again unless the version moves. **The no-bump and no-repair
decisions were coupled and the first cut presented them as independent** —
banking 49 while shipping no repair would have spent the only vehicle for one.

```sql
UPDATE my_library SET resource_offline = 0, resource_local_address = NULL
WHERE _id IS NULL AND resource_local_address LIKE '%/%'
```

**The audit proposed `_rev IS NULL` for the first clause, and it has a false
positive.** A resource embedded *only* in a course document arrives rev-less (a
step sub-object carries none) while `MyLibraryMapper` still writes its
`couchId` — so `urlFor` can build a URL, the user can download it, and
`markDownloaded` then writes an absolute path onto a rev-less row. That clause
would have cleared a genuine download. `_id IS NULL` cannot: every
mapper-written row carries a `couchId`, and only `saveLocalResource` leaves it
unset. **Third instance this round of *`_rev IS NULL` is authorship in Kotlin
and is not in the port*** — and the first where it caught a *proposed* fix
rather than a shipped one. Pinned by *the repair cannot reach a row that owns a
real file*.

`LIKE '%/%'` is the other half: `_primaryAttachment` skips any attachment key
containing a separator and the fixed writer stores a basename, so only a picker
path has one. Without it the repair would un-flag every locally created
resource on every future bump — pinned by *the repair leaves a post-fix local
resource alone*.

Mutation 17 is the one worth copying: leaving `schemaVersion` at 48 reds the
version-floor assertion, which is Phase 146's *"the test exercised the
migration correctly and was blind to whether the migration would ever be
invoked"* recurring for a **repair** rather than a column. The floor moved from
48 to 49 in the same edit.

### What preservation costs, and why the loop is exhaustive

`createAll` does not ALTER a preserved table, so every column has to be
reconciled by hand — and `my_library` is exposed to the *worse* form of that
hazard, not the mild one. `MyLibraryTable` carries
`@TableIndex(name: 'my_library_course_id', columns: {#courseId})`, and drift
emits a **bare** `CREATE INDEX … ON my_library (course_id)` with no
`IF NOT EXISTS`. The migration drops every index and lets `createAll` rebuild
them, so on an install lacking `course_id` the upgrade **aborts entirely** —
it does not merely omit a column. `step_id` has to be reconciled before the
walks run for a second reason: `CourseDao.upsertAll` runs
`UPDATE my_library SET step_id = NULL, course_id = NULL` inside the
courses-walk transaction, so a missing column takes the whole walk with it.

Mutation 13 moves the loop below `createAll` and four tests red with
`SqliteException(1): no such column: course_id` — the ordering is load-bearing
and now pinned.

**The loop reconciles every column rather than a version-gated list**, and that
is a considered choice rather than laziness. The table has always been dropped
and rebuilt, so what is on disk is exactly the shape of the installed version;
`open_which_file` arrived at v36 and `step_id`/`course_id` at v48, and **this
repository's clone is shallow (240 commits), so when the older columns arrived
cannot be established from git at all.** A version-gated list would therefore
be guessing about the part of the history that is invisible. The loop is
exhaustive by construction and idempotent — `_addColumnIfMissing` no-ops on a
present column and on an absent table — and for this one table it retires the
failure Phase 143 warned about, where a forgotten step is silent until a query
names the column.

`id` is skipped explicitly: `ALTER TABLE ADD COLUMN` on a `NOT NULL` column
with no default is illegal in SQLite, and while the guard means the loop can
never reach it, relying on a guard to hide an illegal statement is safe by
accident. **Mutation 14 removes that skip and survives**, which is the honest
result and is recorded rather than papered over: no reachable fixture can make
the clause load-bearing, because a `my_library` without its primary key is not
`my_library`. Deleting the clause would remove a guard against a future
refactor of `_addColumnIfMissing`, so it stays with the reason written beside
it. Every other column is nullable or carries a literal default, which
`Migrator.addColumn` emits unparenthesised and SQLite accepts — including the
two `CHECK (… IN (0, 1))` booleans, for which `surveys.needs_sync` and
`users.is_updated` are the shipping precedent.

### Phase 143's question: what is each row's state *after* the migration?

**No *column* needs a backfill, and the reason is structural rather than
lucky — but the question is about rows, and answering it about columns is how
this phase nearly shipped the loss it was fixing.** The `surveys.needsSync`
shape cannot recur here because `my_library` has no authorship flag at all: the
state that marks a row local is `_rev IS NULL`, which a preserved local row
already carries and which is Kotlin's own `getPendingUploads` predicate. That
much is true and it is not the whole answer: a row can need repairing because
an **older writer** wrote it wrong, with no new column involved at all, and
that is exactly what the v49 repair above exists for. *"Does a new column
default wrongly?"* is a proper subset of *"what state is each row in after the
migration?"*, and the first cut of these notes answered only the subset. `step_id`/`course_id` arrive NULL,
which is the truthful value ("in no course document") and what the courses
walk overwrites on its next page — a guessed backfill would be actively wrong,
since nothing on the row records which step it came from.

Walking every reader (`watchResources` both arms, `resourcesOnShelf`,
`watchResourcesNeedingUpdateCount`, `getByStepId`, `getCourseResources`,
`getById`, `countByTitle`, `deleteNotIn`), a preserved row is in a coherent
state in all of them. One is even repaired by preservation: `countByTitle` is
the duplicate-title guard, and today the row it should match is destroyed.

**But two states get worse, and pretending otherwise would be the Phase 143
mistake in reverse.** Both are recorded under *Reported, not fixed*: a stale
`resource_offline = 1` loses the bump as its automatic repair, and a
course-step row whose sub-object carried no `_rev` used to be swept by the bump
and now is not — which is why the `_rev` fix below is part of this phase rather
than a follow-up.

### Verifying Phase 146's claim rather than inheriting it

Phase 146 says its `deleteNotIn` fix "spares exactly the rows preservation is
for". The prune's eligibility is
`is_private = 0 AND _rev IS NOT NULL AND _rev != ''`, so: a `saveLocalResource`
row is spared (rev NULL), a private team resource is spared (`is_private = 1`),
and a synced public row not in `keepIds` is still deleted. Necessary, and
confirmed.

**"Exactly" is too strong, in two directions.** `_rev IS NULL` is not
authorship in the port — a course-step sub-object carries no `_rev`, so the
spared set is wider than the preservation rationale — and a *downloaded* public
row is prunable, so a prune can still orphan a file on disk. And the
empty-`keepIds` branch, which is a port of nothing Kotlin runs, deletes the
whole synced catalog; preservation offers no protection against it. So the
prune fix is necessary for safe preservation and is not sufficient on its own.

### The two fixes that had to land first

Preservation makes a wrong row permanent, so both of these are part of the same
job rather than follow-ups:

1. **`saveLocalResource` never copied the picked file.** Kotlin copies it into
   `<ext>/ole/<id>/<basename>` before writing the row and stores the basename
   (`ResourcesRepositoryImpl.kt:235-256`, `:278-280`, `FileUtils.kt:47-49`) —
   the same convention `ResourceFiles` uses. The port stored the raw
   `FilePicker` path and set `resourceOffline = true` over the top, so the
   resource was unopenable and opening it destroyed both columns. Preserving
   that row would have preserved the claim rather than the resource. Found by
   the ground-truth audit, not by me.
2. **`MyLibraryMapper.fromDoc` blanked the `_rev` another writer recorded.**
   Two writers, one column, and only one of them sees a revision — the same
   shape Phase 146 fixed for the attachment columns and left on this one. A
   blanked `_rev` is outside the prune's eligibility, so under preservation the
   row becomes immortal *and* visible in the public catalog. Kotlin has the
   identical defect (`MyLibrary.kt:237` writes `""` for a missing key) and its
   Room bump sweeps the wreckage; the port's no longer would, which is what
   turns a self-healing quirk into an accretion. Fixed as a documented
   deviation.

### What the falsified comments were, and where

Preservation makes a sentence false in nine places. The audit found four I had
not:

| File | What it said |
|---|---|
| `tables.dart` (`stepId`/`courseId`) | *"Safe to index because `my_library` is a cache … so it never enters the pre-`createAll` reconciliation block."* The conclusion **inverts**: it is now exactly such a table. |
| `app_database.dart` (the v48 step) | *"not in `[_localAuthorityTables]`, so the drop loop above deleted it … **also has no `@TableIndex`**."* The second half was **already false the day it was written** — Phase 146 added the index in the same commit. |
| `migration_test.dart` `covered` set | Asserted equal to the preserved set; reds by design. |
| `migration_test.dart` pure-cache test | Used `my_library` as **the canonical example of a table that is nothing but a cache**. Moved to `tag`, which is one. This example has now moved twice (`users` → `my_library` → `tag`), and the moves are the history of the preserved set. |
| `migration_test.dart` `openWhichFile` test | *"The table is a cache so it is dropped and recreated."* |
| `migration_test.dart` frozen-DDL dartdoc | Predicted precisely this change and said which tests would red. It was right. |
| `migration_test.dart` dropped-rows test | Asserted the rows are discarded. Replaced by its counterpart. |
| `PHASE_146_NOTES.md:182-188` | Historical note; left as written, since nobody edits past notes and the sentence is dated. |
| `CLAUDE.md`, `.claude/agents/parity-auditor.md`, `docs/kotlin-to-flutter-migration.md` | **27 tables** → 28. Taken under the *making an existing statement true* carve-out; the `parity-auditor` copy is the highest-leverage one, since every parity audit reads it. |

### Tests

`local_resource_survives_schema_bump_test.dart` drives the production
`saveLocalResource` and the real `MyLibraryMapper` against a document shaped
like the server's, with a real temp file and `ResourceFiles.baseDirectory`
pointed at a sandbox — so it asserts the bytes are still where the viewer
resolves them, not merely that a row survived. Four cases: the offline-created
resource, the downloaded resource's three flags (plus the bell count staying
0), the stale cache half still being evicted by the next walk, and the private
team resource.

In `migration_test.dart`: the dropped-rows test becomes *a resource row
survives the upgrade, walk-pruned after*; *a pre-v36 resource table gains every
column it is missing* installs the frozen v47 shape **minus `open_which_file`**
and asserts the loop restores it, which is the case a version-gated list would
have missed; and *every `my_library` column is either old or a deliberate
decision* is a column inventory that reds when a column is added — the loop
cannot backfill, so the inventory is what makes that decision visible rather
than silent. That last one is the Phase 143 lesson turned into a guard.

Mutations, all through anchored edits after the lesson recorded under Job 4:

| # | Mutation | Result |
|---|---|---|
| 11 | `my_library` out of the preserved set | 7 red |
| 12 | reconciliation loop removed | 4 red |
| 13 | loop moved below `createAll` | 4 red, `no such column: course_id` |
| 14 | explicit `id` skip removed | **survives** — recorded above, no reachable fixture can make it fail |

## The second audit pass

Run at `effort: max` against the finished, already-green code. Findings folded
in above; see the git history for what changed after it.

## Reported, not fixed

Each names the file, the change, and why it was not made here. Ordered by data
at risk.

1. **The port has no resources uploader at all, and that is what makes
   `my_library` unrestorable.** Kotlin's `uploadResource` carries a
   `_rev IS NULL` row to the `resources` database and its file to an
   attachment; the port has no `resources_uploader.dart`, no entry in the
   outbox handler registry (`app_providers.dart:645-682`), and nothing that
   POSTs to a `resources` endpoint. Preservation stops the row being
   *destroyed*; it does not make it reach the server, and the shelf document
   already advertises its id (item 7). **There is a trap in the obvious
   implementation, and it is why this is not a small job:** `_rev IS NULL` is a
   sound authorship test in Kotlin (`JsonUtils.getString` returns `""` for a
   missing key, so `insertMyLibrary` never leaves a null) and is **not** one in
   the port, where a course-step sub-object also yields a rev-less row. An
   uploader keyed on that predicate would POST server-owned resources to
   `resources` under the user's credentials — Phase 138's rejection of
   `_rev IS NULL` as authorship, in a new table. It needs a `needsSync`-style
   flag first, which is a column on a now-preserved table and so a **backfill
   decision** (the reconciliation loop adds the column; only a person can
   decide what existing rows should say). An uploader is also Lane C's file
   class.
   **And one coupling the file copy creates, which whoever writes that uploader
   must handle:** the viewer resolves bytes at
   `<base>/ole/<couchId ?? id>/<filename>`, and this phase writes the copy
   under the *local* id because a local row has no `couchId`. The moment an
   uploader records one — as Kotlin's `markResourceUploaded` does
   (`ResourcesRepositoryImpl.kt:797-818`, which sets `_id`/`_rev` and leaves
   the addresses alone) — the file becomes invisible to the viewer. Either
   relocate the directory on upload, or keep the viewer on `id` for a row whose
   file it wrote itself. Severity: high — it is the whole reason this table is
   preserved.
2. **A stale `resource_offline = 1` has lost its only automatic repair.**
   Kotlin re-derives the flag from the disk on every pull that carries an
   attachment (inside `if (doc.has("_attachments"))` and
   `if (key.indexOf("/") < 0)`, so a course sub-object re-derives nothing);
   the port's mapper writes it never, and until this phase the bump was the
   accidental repair —
   the row was destroyed and re-pulled with the column default. Now nothing
   repairs it except opening the resource
   (`resource_viewer_screen.dart:71-75`), while the list sorts offline-first
   and the detail screen hides Download in favour of View. Concrete input:
   download a resource, delete the app's `ole/<docId>/` directory with a file
   manager, upgrade — the row still claims to be downloaded. The fix is to port
   `checkFileExist` into the sync-in, which means disk IO in a mapper that is
   deliberately pure; it is the natural companion to Phase 146 item 3's
   `reconcileHtmlResourceOffline`, still unported anywhere. Severity: medium.
3. **A permanently-failing `download_queue` entry — latent, and the caller is
   already the guard.** `ResourceDownloader.download` enqueues *before*
   checking the URL (`resource_downloader.dart:37-41`) and `urlFor` needs a
   `couchId` (`:28-29`), null for a locally created resource, so such an entry
   could never complete — and the only branch that ever clears one is "the
   `my_library` row is gone" (`background_entrypoint.dart:42-53`), which the
   bump used to trigger. Preserving both tables removes that trigger.
   **But nothing can reach it**, which the implementation audit established
   and my first draft of this item got wrong: `download` has two production
   callers, and the persisting one (`resource_viewer_screen.dart:227-247`) is
   reachable only from inside `if (canDownload)`, which *is*
   `urlFor(...) != null`; the background caller passes
   `persistInBackground: false`. The guard my draft proposed adding to
   `resource_downloader.dart` is already enforced two lines from the read.
   Severity: **latent, not medium** — recorded because enqueue-before-check is
   a trap for the next caller, not because a user can hit it today.
4. **A course-only resource row is immortal.** The `_rev` fix means a row the
   `resources` walk has *also* seen keeps its real revision and stays
   prunable, which is the common case. A resource that exists **only** inside a
   course document still lands with `_rev` NULL and no shelf entry, so the
   prune spares it for ever and it shows in the public catalog. The bump used
   to sweep it. Same accretion class as the `surveys` entry, and the same
   argument for an authorship flag as item 1.
5. **The prune can orphan a file on disk.** A downloaded public row is
   prunable, and nothing deletes `<base>/ole/<docId>/` when the row goes. Not
   new, and preservation does not change it — recorded because the file-copy
   work above made it visible: the port now writes files it has no reaper for.
6. **The empty-`keepIds` prune branch is still a port of nothing.** Phase 146
   item 6 reported it; preservation offers no protection, since the branch
   applies the same eligibility and deletes the entire synced public catalog. A
   re-provisioned satellite, or any 200 whose body lacks `total_rows`, empties
   the library.
7. **The shelf upload publishes an id that resolves to no document.**
   `ShelfRepository._localResourceIds` maps `r.resourceId ?? r.id` over the
   user's shelf (`shelf_repository.dart:179-185`) and `saveLocalResource` sets
   `resourceId` to its own generated id, so a locally created resource's local
   id is PUT into the server shelf document's `resourceIds` — a dangling
   reference, permanently, because nothing ever creates the document. Kotlin
   has the same shape and converges because `uploadResource` runs. Contrast
   `_localMeetupIds` (`:193-200`), which deliberately omits an id-less meetup
   for exactly this reason and says so. Closes with item 1.
8. **The private-team-resource path is dead in the port.**
   `saveLocalResource`'s `isPrivateTeamResource` branch cannot be true: the
   screen derives it from `widget.teamId`, the route reads it from a `?teamId=`
   query parameter, and neither pusher supplies one
   (`resources_screen.dart:139`, `resource_detail_screen.dart:99`). Kotlin
   reaches it from `TeamResourcesFragment.kt:140-144`. Relatedly `privateFor`
   has **zero readers** — a private resource is excluded from the catalog arm,
   absent from the shelf arm, and reachable only through a `resourceLink` row
   that `saveLocalResource` does not create (Kotlin creates it on upload,
   `ResourcesRepositoryImpl.kt:809-816`). So the preservation rationale rests
   on the non-private local resource, which is reachable; wiring the private
   one up without item 1 would produce an immortal invisible row.
9. **`resourceLocalAddress` carries two incompatible formats.** The mapper
   writes the CouchDB attachment *name* (matching `MyLibrary.kt:262`);
   `markDownloaded` writes a full filesystem path. `saveLocalResource` was a
   third format and is now the first. Benign because every reader tests only
   non-null — and it stops being benign the moment anything treats the column
   as a path. Preservation extends the lifetime of every stored value.
10. **The community-voice predicate differs in kind between the apps.**
    Kotlin's is a raw `viewIn LIKE '%"section":"community"%'`, which SQLite
    evaluates ASCII-case-insensitively, so `"Section":"Community"` counts
    there; the port parses the JSON and reads `section` case-**sensitively**.
    Conversely a serializer emitting `"section": "community"` with a space
    fails Kotlin's `LIKE` and passes the port's parse. Neither app's own
    writers produce either shape, so this is latent. The port also loads every
    `news` row in the window into memory, twice per evaluation, where Kotlin
    runs one `COUNT(*)` — performance only. `voices_repository.dart` is another
    lane's file.
11. **`health_uploader.dart:87-90` now says something false**, and it is the
    comment a lane doing 409-recovery work will read: *"The port cannot copy
    that half: its `markUploaded` writes `rev: Value(rev)` … and
    `app_database.dart` is not this lane's file."* True when Phase 148 wrote
    it, false since `c5f02b2`. Lane C's file; raised on the PR as well as here.
12. **`HealthRepository.markUploadedBatch` still has zero callers.** Its copy
    of the null-rev bug is fixed by the DAO change, so what remains is the
    dead-code question `PHASE_142_NOTES.md` item 7 opened.
13. **`TeamTaskDao.markUploaded` writes `rev` unconditionally, and that is
    parity — do not "fix" it.** `TeamTaskDao.kt:50-51` writes
    `_rev = :remoteRev` with no map overload and no null-rev partition, and
    `team_tasks_uploader.dart:59-62` guards `rev is! String` before calling.
    Recorded because it looks exactly like the health defect and a later lane
    reading only the DAO would change it into an undocumented improvement over
    the Kotlin. (I mutated it into that state by accident mid-session; it did
    not ship.)
14. **`add_resource_screen` accepts a save with no file where Kotlin requires
    one** (`ResourcesRepositoryImpl.kt:235-238` fails on a null `resourceUrl`).
    Left as a divergence and pinned, because closing it changes what the form
    accepts rather than what it stores — and the screen is not this lane's
    file. It is also the state that keeps item 3 reachable.
15. **`my_library` is the first table whose new columns are reconciled
    automatically, and the other 27 are not.** The loop could be generalised to
    every preserved table, which would retire the whole class of
    forgotten-`_addColumnIfMissing` bugs. Two reasons it was not done here: a
    hand-written step can also carry a **backfill**, which a loop cannot, and
    `migration_test.dart`'s frozen-DDL guards exist precisely to make a
    forgotten step loud — generalising would make those guards pass
    vacuously. Worth a slice with the column inventory extended to each table,
    not a side effect of this one.

## Two notes on the gate

**A "Build Android APK" job on a Flutter-only round is `flutter.yml`'s own,
not the Kotlin build — and I nearly wrote the opposite down.** Seeing that job
run beside *Analyze and test*, I drafted a note here saying a `.claude/**`
edit escapes `build.yml`'s `paths-ignore` and rebuilds both Kotlin flavours.
Both halves were wrong. The run is `.github/workflows/flutter.yml` (checked
via the API rather than inferred from the job name), which builds an APK **of
the port** — something CLAUDE.md already records under the
`sqlite3_flutter_libs` refusal. And the pattern would not have leaked anyway:
`build.yml` ignores `**.md` *and* `**/*.md`, and `**` matches across `/`, so
`.claude/agents/parity-auditor.md` is covered. The filter worked exactly as
documented. Kept as a note because a job *name* is not a workflow, and because
this is the third claim this round that did not survive having its citation
opened — the other two were mine as well.

**Test count: 2735 → 2758.** Exactly the 23 added here (3 tally, 4 health rev,
4 mapper rev, 6 file copy, 4 preservation, 2 migration guards), which is worth
stating as arithmetic rather than "all green": a suite that loses a file still
passes.

## What the second audit pass changed

Run at `effort: max`, read-only, against the finished and already-green code.
It confirmed the Kotlin citations (it opened all of them and every one said
what this file says it says) and found the defects elsewhere — which is the
argument for the second pass in one line: **the ground-truth audit checks
whether you read the Kotlin right; only the implementation audit checks whether
what you built does what you think.**

Fixed here as a result:

1. **The v49 row repair** — the headline finding, written up above. This phase
   would otherwise have relocated a data loss into a permanent dead end that
   lies, in the row class it exists to protect.
2. **The reconciliation blocks moved above the drop loop.** Drift does not wrap
   a migration in a transaction, so a throwing `ALTER` left `onUpgrade`
   abandoned with every cache already dropped and `createAll` never reached.
   Reconciling first makes that failure non-destructive — the next open retries
   the same `from`, and every step is idempotent. Nothing there depended on the
   drop; the cost is zero.
3. **A zero-byte pick no longer claims to be downloaded.**
   `ResourceFiles.existingFileFor` requires `length > 0`, so an empty copy is
   invisible to the viewer and a row flagged over it dead-ends exactly as the
   uncopied path did. Both derived columns now come from the copy or neither
   does, and the stub is deleted. **Kotlin's `copyTo` has this hole**, so this
   is a deliberate deviation rather than parity — recorded at the code.
4. **`catch (_)` narrowed to `on Exception`.** Kotlin catches `IOException` and
   `SecurityException` and lets anything else propagate; the bare catch turned
   a programming error in `ResourceFiles.fileFor` into the form's "resource
   title already exists". (That *routing* is real parity —
   `AddResourceActivity` shows the title error for any create failure — but the
   swallowing was not.)
5. **The moved block's own first paragraph** said "the indexes were all just
   dropped", which the move falsified in the same edit. Caught by re-reading
   the diff, not by the audit.
6. **`voices_repository.dart`'s two sentences that this diff falsified**: the
   dartdoc still called the tally "top-level", and the hand-off paragraph still
   listed all three defects as another lane's, unfixed. Both under the *making
   an existing statement true* carve-out.
7. **`migration_test.dart`'s frozen-DDL dartdoc**, which predicted this exact
   change and said "these tests red when that happens" — a prediction that was
   right, left standing, and therefore telling a reader the guard had fired
   when it had not. The prediction is now quoted as a record rather than
   asserted as a fact. **My own notes listed this among the nine falsified
   comments and said "It was right" — so the notes claimed a correction that
   never landed.** The only row of that table where the code disagreed with the
   write-up.
8. **The loop's "needs no hand-written step" sentence**, which read as
   permission to add columns freely. The bump is still mandatory (the loop runs
   inside `onUpgrade`), and two column shapes — a `NOT NULL` column with a
   `clientDefault`, and `withDefault(currentDateAndTime)` — are refused by
   SQLite in an `ADD COLUMN` and would abort the upgrade on every install.
9. **`MyLibraryTable` gained a class-level dartdoc.** Every other preserved
   table says so at class level; this one buried it ninety lines down on
   `stepId`, which is not where a later lane looks first.
10. **The `_rev` deviation's cost is now written at the guard.** A resource in
    both the `resources` walk and a course document, deleted server-side, is
    now prunable where it used to be immortal — so it is pruned, re-inserted
    from the sub-object with no `_attachments`, and comes back not-downloaded
    with its bytes orphaned. Narrow, stable, and accepted; the alternative
    makes every such row permanently unprunable.
11. **Prose**: "Kotlin re-derives on **every** pull" overstated it (the
    re-derivation sits inside `if (doc.has("_attachments"))` and
    `if (key.indexOf("/") < 0)`, so a course sub-object triggers none — the
    substance survives, the word did not); a test comment named
    `_presentOrAbsent` for the function actually called `_revOrAbsent`; and
    item 3 of *Reported, not fixed* was **refuted** — the enqueue guard it asks
    a future lane to add is already enforced by the only caller that can reach
    it. Demoted rather than deleted.
12. **Test housekeeping.** The preservation test never restored
    `ResourceFiles.baseDirectory`; and my new helper was a **third** copy of a
    seam-restorer that exists twice already as
    `getApplicationDocumentsDirectoryFallback`, under a different name, with a
    dartdoc calling a thrower "the production default" and a rationale ("leaks
    into every later test in the same shard") that is wrong because
    `flutter test` gives each file its own isolate. Phase 78/95's
    don't-hand-roll rule, in a diff that cites both. Now one name and one
    behaviour, with the reason for restoring stated correctly.

### Assertions the audit named as unable to discriminate

Kept, because each pins a rule rather than the fix, and the audit found no
vacuous ones among the load-bearing set. Recorded so nobody counts them as
evidence: *a step-only resource still arrives with no revision* and *a document
that carries an explicit null revision clears it* both pass against the pre-fix
code (`Value(null)` and `Value.absent()` agree on an insert; the second
discriminates against a hypothetical "never write a null rev", which is what it
says it does), and *every preserved table has a preservation test* proves
bookkeeping rather than coverage — as its own comment admits, though its name
promises more.

Two fixture caveats worth the same honesty. `local_resource_survives_schema_bump_test.dart`
creates its database at the current version, so its `runUpgrade()` exercises the
**drop loop only** — all 39 `_addColumnIfMissing` calls no-op, and the
reconciliation is covered in `migration_test.dart` instead. And its
private-team-resource case drives a state the app cannot currently produce
(item 8: no caller supplies a `teamId`), so it proves preservation for
something unreachable. Neither is wrong; neither is evidence of what its file
header implies.
