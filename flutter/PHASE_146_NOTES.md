# Phase 146 — the port never ingested a course step's resources

Lane A of a four-lane round. One job, from Phase 145's *Reported, not fixed*
item 2 — the eighth consecutive round briefed from the previous round's list:

> Kotlin's courses walk buffers each embedded resource document
> (`queueCourseResources`, `CoursesRepositoryImpl.kt:792-798`) and drains it
> into `my_library` stamped with `courseId` and `stepId`
> (`flushPendingCourseResources`, `:819-863`). `CourseMapper._parseSteps` keeps
> only the length. So the port holds a count of resources it never stored.

This is the *Reachability* class: not a widget that renders wrong, but a table
that is never written, so three readers — the inline per-step resource list,
the course-level download button, and the step's auto-download and next-step
prefetch — have nowhere to read from. `course_detail_screen.dart:204` renders
`resourcesInStep(step.noOfResources)`, a number the port could not
substantiate.

## Method

1. Read the Kotlin by hand: `buildCoursePayload`, `queueCourseResources`,
   `flushPendingCourseResources`, `MyLibrary.insertMyLibrary`, both
   `resources`-walk call sites, `MyLibraryDao`'s queries, and **both** drain
   call sites (`TransactionSyncManager.kt:302` and
   `CoursesRepositoryImpl.kt:508`).
2. A `parity-auditor` pass at `effort: max` on **twelve lettered claims**,
   before touching Dart. It confirmed seven, corrected two, and **refuted one
   outright** — the refuted one was a data-loss bug sitting in my own file set
   that the phase would otherwise have shipped a stamp into. See below.
3. Implement, each defect demonstrated failing first.
4. Mutation-test every fix — ten mutations, one of which survived and turned
   out to be a missing fixture row rather than a redundant conjunct.
5. A second `parity-auditor` pass at `effort: max` aimed at the finished,
   already-green code.

## What the ground-truth audit changed

### The refutation: `deleteNotIn` was never the port of Kotlin's prune

I asked the auditor to confirm, as claim L, that an ingested course resource
survives the `resources` walk's `deleteNotIn` because it is a document of the
`resources` database and therefore in that walk's keep set. It refuted the
premise the question rested on.

Kotlin's prune is **not** "delete what the walk did not list".
`MyLibraryDao.deleteStalePublicNotIn` (`MyLibraryDao.kt:170-175`) is

```sql
DELETE FROM my_library
WHERE _rev IS NOT NULL AND _rev != '' AND isPrivate = 0
  AND resourceId NOT IN (:currentResourceIds)
```

and its empty-list twin `deleteAllStalePublic` (`:177-178`) carries the same two
guards. The port's `MyLibraryDao.deleteNotIn` had **no predicate at all**. Three
row classes were being destroyed on every resources sync:

* **A resource the user created offline.** `saveLocalResource` writes a row with
  no `rev`, no CouchDB document and no outbox entry — nothing can give it back —
  and the first resources sync deleted it, along with the pointer to the file
  they picked.
* **Every private team resource**, which the public walk never lists and so
  could never keep.
* **A course step's resource whose embedded sub-object carries no `_rev`.**

The third is why this belongs in *this* phase rather than a later one: it is the
difference between the stamp being durable and being a one-sync artefact. The
courses walk would write the join and the next resources walk would delete the
row underneath it. Fixed, demonstrated failing first, and pinned by
`resource_prune_eligibility_test.dart`.

**Two writers of one table disagreeing about what "stale" means** is a new
member of a familiar family. The catalogued shape is a sync-in rewriting a
locally-authored *column* (Phases 56, 74, 98); this is a sync-in **deleting the
row**, and the same question exposes it — *can this walk vouch for what it is
about to overwrite?* A walk over `resources` cannot vouch for a row that has
never been to the server.

### The correction: a malformed element does not cost one document, it costs the walk

I had claimed Kotlin's per-document `try`/`catch` in `upsertRoomCoursesFromSync`
swallows the `IllegalStateException` that `.asJsonObject` throws on a non-object
element in a step's `resources` array. It does — **only when `continueOnError`
is true**, and the parameter defaults to `false` (`:619`). Of the two callers:

* `batchInsertMyCourses:506` passes `true` — the **shelf** path. One document is
  dropped; the rest of the batch is written; the drain still runs.
* `bulkInsertFromSync:610-612` passes nothing — the **sync walk**. The exception
  propagates to `TransactionSyncManager.kt:335`, which returns `0`. **The entire
  courses table walk aborts at that page**, nothing on it is written, and the
  drain at `:302` never runs for it.

So the port filters, rather than reproducing this. It also filters a blank or
whitespace `_id` and a `_design` id — neither of which `queueCourseResources`
filters, though `batchInsertResources` filters both before writing the very same
table (`ResourcesRepositoryImpl.kt:668-671`). Kotlin's unfiltered behaviour is a
latent corruption, not a quirk worth preserving: `insertMyLibrary` returns a row
for any non-empty document, so an `_id`-less resource writes `id = ""`, the
primary key collides, and every such resource in a batch collapses into one row
carrying the last one's title and the last one's step.

Recorded as a deviation in `CourseMapper._parseStepResources`' doc comment,
because "the port is stricter than the Kotlin" is exactly the kind of thing a
later parity audit flags as a defect if nobody wrote down that it was chosen.

## The stale-join hazard, and why it is worse in the port than in Kotlin

The brief flagged Phase 136's warning that the port's step ids are positional
(`CourseMapper.stepIdFor`, `'<courseId>:<index>'`) and asked what the ingestion
assumes about a reorder. Working it through, with the auditor:

**A pure reorder is safe.** Swap steps A and B and both are re-stamped in the
same pass, because every surviving step is written every sync.

**Deletion is not.** Course `c1` with steps `[A, B]`, A holding `r1` and B
holding `r2`. First sync: `r1.stepId = 'c1:0'`, `r2.stepId = 'c1:1'`. The author
deletes A. The next sync writes one step, `'c1:0'` = B, and stamps
`r2.stepId = 'c1:0'` — but **nothing revisits `r1`**, which still says `'c1:0'`.
`getByStepId('c1:0')` now returns both. A resource belonging to a deleted step
would appear under the step that took its slot, and the auto-download would
fetch it.

Kotlin cannot reach this: its step ids hash the step's own JSON, so a stale
stamp matches no live step and merely dangles. **The port's exposure is a false
join where Kotlin's is accretion**, which is the difference between a wrong
answer and a harmless one — so this needed fixing rather than documenting.

`MyLibraryDao.releaseStepJoinsForCourse` is the direct analogue of
`ExamDao.releaseStepJoinsForCourse`, whose own doc comment already explains this
hazard for its table. Three details are not copied blindly:

* It runs for **every** course on the page, not only those that still have
  resources — otherwise removing a course's last resource never clears the join.
  (Mutation 6 pins this.)
* Its keep set is the **union across the whole document**, so a resource
  appearing in two steps is not un-stamped by the release that follows the
  upsert that just wrote it.
* It nulls `courseId` alongside `stepId`. For the exam and survey tables that
  reads as symmetry; here it is the only correct choice, because
  `getCourseResources` and `getByCourseId` read `courseId` on its own, so a
  stale one would keep over-filling the course download dialog. (Mutation 5.)

And it is a partial-column update, never `toCompanion` — `my_library` rows carry
`userId`, `resourceOffline`, `downloadedRev` and `resourceLocalAddress` that a
whole-row write would clobber.

## The preservation mechanism, and why it is thinner than Kotlin's

The brief's first constraint: write `stepId`/`courseId` only when non-blank, so
the plain resources walk cannot clear the course link on a re-pull.

Kotlin has **two independent guards** and the port has one. `insertMyLibrary`
skips each assignment when the value is blank (`MyLibrary.kt:231-236`) *and*
both `resources`-walk call sites hand it the stored entity to mutate
(`ResourcesRepositoryImpl.kt:641`, `:685`), so the link survives even if the
guard were removed. The port's single guard is `Value.absent()`, which works
because drift builds its `ON CONFLICT DO UPDATE SET` clause from the companion's
**present** columns alone — `toColumns(true)` at
`drift-2.34.4/lib/src/runtime/query_builder/statements/insert.dart:392`, emitted
at `:410-417`. Verified in the pub cache rather than from memory, because the
whole design rests on it.

That makes `Value.absent()` load-bearing rather than tidy. A `Value(null)` there
restores the defect, and so would routing any `my_library` write through a
whole-row `toCompanion(true)`. Mutation 1 reds three tests.

The ingestion also passes all six `existing*` merge lists.
`MyLibraryMapper.fromDoc` writes `userId: Value(existingUserIds)`
unconditionally, so a new caller that omitted them would retract shelf
membership — the Phase 116 defect, in a new writer.
`mapper_preserves_local_columns_test.dart` catches that mechanically, and
mutation 3 confirms it reds *and* that the behavioural test reds beside it. No
`shelfId` is passed, matching Kotlin: the drain sends no `userId` at all, so
`setUserId` returns early (`MyLibrary.kt:123-130`) and course ingestion never
*adds* membership either. A course-only resource therefore lands on nobody's
shelf and in the public catalog, which is right — a step's resource is not a
shelf item.

## The schema bump

**Allocated `schemaVersion` 48, and taken.** Do not reuse it.

`my_library` is **not** in `localAuthorityTables` — confirmed by reading the
set, as the brief asked, because Phase 143 had just changed it. So the upgrade's
drop loop deletes the table and `createAll` rebuilds it carrying both columns:
**no hand-written `_addColumnIfMissing` step is needed**, and the table has no
`@TableIndex`, so it never enters the pre-`createAll` reconciliation block
either. (Kotlin indexes only `_rev`, `titleNormal` and `resourceId` —
`MyLibrary.kt:32` — so not indexing `stepId` is parity as well as convenience.)

The migration test **can fail**, which took two goes. The frozen v47 DDL was
dumped from `sqlite_master` *before* the columns were added, not retyped, and is
guarded against drift the way the surveys literal is. Adding `my_library` to the
preserved set reds five tests. But the first cut had a hole: leaving
`schemaVersion` at 47 while adding the columns changed **nothing**, because
these tests call `migration.onUpgrade` directly and drift's version machinery is
what actually decides whether it runs at all. An explicit
`expect(database.schemaVersion, greaterThanOrEqualTo(48))` closes it. That is
Phase 122's *"make the v46 migration test able to fail"* recurring in a new
disguise: the test exercised the migration correctly and was blind to whether
the migration would ever be invoked.

## Where the write happens, and the constructor that was not changed

`CoursesRepository` holds no `MyLibraryDao`, and adding one means editing
`app_providers.dart` — outside this lane's file set. The tempting alternative,
an *optional* named parameter, is exactly how a ported feature ends up green and
dead, which is the failure class this whole phase is about. So the write goes
through `CourseDao` (in `app_database.dart`, which this lane owns), which
reaches the sibling accessor on the same `AppDatabase`. `CourseDao` already owns
the courses walk's write, including its stale-step cleanup, so the courses
walk's third table is a consistent place for it.

The port writes per page from inside `CoursesRepository.sync`, which is the
analogue of `TransactionSyncManager.kt:302` — Kotlin's *in-walk* drain, not a
single flush at the end. That ordering is the point: a step's resources are
readable before the sync finishes.

Collapsing Kotlin's buffer into a return value also removes two ways it
misbehaves, both found by the ground-truth pass: a throw mid-page leaves items
queued under a `courseId` whose row was discarded, and the shelf path runs up to
six coroutines (`Semaphore(6)`) against the one shared `pendingCourseResources`,
so a failure in one shelf's flush discards another shelf's queued items.

## Files touched

| file | change |
|---|---|
| `lib/data/local/tables.dart` | `MyLibraryTable` += `stepId`, `courseId` |
| `lib/data/local/app_database.dart` | `schemaVersion` 48; `MyLibraryDao` += `getByStepId`, `getByCourseId`, `getCourseResources`, `releaseStepJoinsForCourse`; `deleteNotIn` eligibility guards; `CourseDao` += `existingResources`, `upsertCourseResources` |
| `lib/data/local/course_mapper.dart` | `CourseResourceDoc`, `ParsedCourse.resources`, `_parseStepResources` |
| `lib/data/local/my_library_mapper.dart` | `stepId`/`courseId` params, `_stampOrAbsent` |
| `lib/repository/courses_repository.dart` | `_ingestCourseResources`, wired into the page loop |
| `lib/repository/resources_repository.dart` | `getAllStepResources`, `getCourseResources` |
| `test/repository/course_step_resources_test.dart` | new, 12 tests |
| `test/repository/resource_prune_eligibility_test.dart` | new, 3 tests |
| `test/data/local/course_mapper_test.dart` | +5 tests |
| `test/data/local/my_library_mapper_test.dart` | +3 tests |
| `test/data/local/migration_test.dart` | +3 tests, frozen v47 `my_library` DDL |
| `test/repository/resources_repository_test.dart` | `row()` fixture emits a `_rev` |

**No ARB key is added and no locale file is touched**, so there is no derivation
hand-off for the integrator.

### The fixture change is a finding in its own right

Three existing tests in `resources_repository_test.dart` failed when the prune
gained its guards, and the reflex — that the change must be wrong — was wrong.
The `row()` fixture built "server documents" with **no `_rev`**, which a real
`_all_docs?include_docs=true` response always carries. So those three tests were
asserting that the walk prunes rows the walk can never produce, and they would
have kept passing over the data-loss bug indefinitely. The fixture now emits a
`_rev` and the tests exercise what they claim. *A fixture that fabricates a join
is not evidence* generalises: neither is one that omits the field the predicate
turns on.

## Mutation testing

Every fix reverted, every test replayed.

| # | mutation | reds |
|---|---|---|
| 1 | `_stampOrAbsent` returns `Value(null)` instead of `Value.absent()` | 3 tests, across 2 files — including the resources-re-pull round trip |
| 2 | drop the `releaseStepJoinsForCourse` call | 2 tests |
| 3 | stop passing `existingUserIds` to the ingestion mapper call | 2 tests — the mechanical guard *and* the behavioural one |
| 4 | revert the `deleteNotIn` eligibility guards | 3 tests |
| 5 | release nulls `stepId` only, leaving a stale `courseId` | 2 tests |
| 6 | seed the keep set only for courses that still have resources | 1 test |
| 7 | drop the blank/`_design` resource-id filter | 2 tests — **after a fixture was added; see below** |
| 8 | add `my_library` to `localAuthorityTables` | 5 tests |
| 9 | remove the ingestion call from the walk (the original defect) | 4+ tests |
| 10 | leave `schemaVersion` at 47 | **nothing, until the test was fixed**; 1 test now |

**Mutation 7 survived at the repository level on the first pass, and the answer
was a missing fixture row rather than a redundant conjunct** — exactly what
Phase 143 predicted for a surviving mutation. The repository-level test stayed
green because `MyLibraryMapper.fromDoc` independently rejects a blank or
`_design` id, so the courses-walk filter looked redundant for *row writes*. It
is not: the mapper's check is `isEmpty`, so a whitespace-only `_id` sails
through it and writes a junk row, and only the courses walk's `trim()` filter
stops it. Adding that one element to the malformed-array fixture makes the
mutation red at both levels.

**Mutation 10 was a non-red by accident**, the different and worse kind: a
migration test that reads as coverage while being blind to whether the migration
runs. Fixed rather than recorded.

## Reported, not fixed

1. **The v48 bump destroys resources the user created offline, and resets
   `resourceOffline` on every downloaded resource.** Found by the ground-truth
   pass, and it outranks everything else here. `my_library` is treated as a pure
   CouchDB cache and is not one, twice over:
   `ResourcesRepository.saveLocalResource` writes a row that exists nowhere else
   (no `rev`, no document, no outbox row), and the port never re-derives
   `resourceOffline` on sync-in the way Kotlin does (`insertMyLibrary` sets it
   from `FileUtils.checkFileExist` on every pull, `MyLibrary.kt:263-267`; the
   port's only writers are download completion and the viewer's stale-flag
   repair). So after any bump the bytes are on disk and the table says otherwise,
   and an offline-created resource is gone.
   **This is pre-existing — every prior bump shipped it — but this phase is the
   bump that ships it next.** The fix is to add `my_library` to
   `localAuthorityTables` with a hand-written `_addColumnIfMissing` pair, which
   is a decision about eviction semantics for the whole table and deliberately
   larger than this lane; the auditor's own recommendation was to bump and
   report. Note the two halves interact: fixing `deleteNotIn` (done here) is what
   makes preserving the table safe, because the prune now spares exactly the rows
   preservation is for.
2. **`ShelfSyncRepository._pullShelfCourses` does not ingest course resources**,
   and it is outside this lane's file set. It already skips the embedded exams
   and surveys on the stated grounds that "the `courses` walk runs earlier in the
   same pass over the same documents and owns those tables", and that rationale
   was verified: `DashboardSyncArea` is ordered with `shelf` last, deliberately,
   and `syncAll` iterates declaration order. **One hole:**
   `DashboardSyncNotifier.retry(DashboardSyncArea.shelf)` — the sync centre's
   per-tile retry button — runs the shelf pull with no courses walk, so a course
   newly added to the shelf keeps its steps and gains no resource stamps until
   the next full sync. Nothing is lost or corrupted (the shelf's own `my_library`
   writes leave both columns absent); it is staleness. Fix is one line in that
   file, in the shape of this phase's `_ingestCourseResources`.
3. **The port has no `reconcileHtmlResourceOffline`, anywhere.** Kotlin calls it
   from the course-resource drain (`CoursesRepositoryImpl.kt:852-861`) *and* from
   both `resources`-walk sites via `reconcileHtmlLibraries` (`:660`, `:703`). It
   repairs an HTML resource that a previous install unpacked to disk but whose
   `resourceOffline`/`resourceLocalAddress` were never recorded. Omitting it from
   the ingestion introduces nothing — it leaves an existing port-wide gap where
   it is — but it is now a gap in two walks rather than one, and it is the
   natural companion to item 1's `checkFileExist` half.
4. **`my_library.stepId` is a Kotlin *upload* input, not only a display one.**
   `TeamsRepositoryImpl.getTeamsForUpload:90-110` groups `getByCourseIds` by
   `courseId` then `stepId` and hands the map to `MyTeam.serialize` →
   `MyCourse.serialize:133`, which rebuilds each step's `resources` array from
   it. So in Kotlin a wrong stamp uploads the wrong resources under the wrong
   step. The port has no counterpart today (`teams_repository.dart`'s
   `serializeTeamDocument` carries no `courses` array), so this is latent — but
   whoever ports team-document upload inherits a correctness dependency on the
   stamp, and should read the release-joins reasoning above first.
5. **`ResourcesRepository.getAllStepResources` and `getCourseResources` have no
   caller in `lib/` yet**, by design: the three screens that would call them are
   Lane D's files this round. The *writer* is on a live production path
   (`CoursesRepository.sync` ← `courseSyncProvider` ← `syncAll` and
   `background_entrypoint`), so the table is genuinely populated on a real
   device; the readers are the API the UI phase needs. Named here rather than
   left for a reachability audit to rediscover — and if the UI phase does not
   land, these two methods are the thing to delete rather than to keep as
   substrate.
6. **The markdown image pre-download gap is untouched and neither easier nor
   harder.** `CoursesRepositoryImpl:670,684` calls `DownloadUtils.extractLinks`
   on course and step descriptions and the port wires nothing
   (`PHASE_142_NOTES.md` item 1). This phase touched the same loop in
   `CourseMapper._parseSteps` and deliberately did not widen into it; the slice
   still spans `voices_repository.dart` and `teams_repository.dart` and still
   wants its own round. The one thing that changed in its favour: `ParsedCourse`
   now has a precedent for carrying non-row parse output back to the repository,
   which is the shape an extracted-links list would take.
7. **Phase 145's items 1 and 3–11 are unchanged**, except that item 2 is this
   phase and item 3's premise (`course_detail_screen.dart:204` shows a number the
   port cannot substantiate) is now half-resolved: the port *can* substantiate
   it, and `course_step_resources_test.dart` pins the count against the rows
   held. Whether that slot should show the resource count or Kotlin's
   `test_size` question count remains Lane D's display decision.
