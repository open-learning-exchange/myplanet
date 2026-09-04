# Phase 122 — the four columns Phase 119 deferred

`PHASE_119_NOTES.md` §*Reported, not fixed* named four columns it had traced,
could demonstrate missing, and deliberately did not add, because adding one
needs a `schemaVersion` and it had no number to allocate. This phase has
**46**, and closes them — plus the two pre-existing `MyLibraryMapper` defects
the same section reported and left to their own walk.

| column | table preserved? | how it lands |
|---|---|---|
| `rating.user` | no | `createAll` — the table is dropped and recreated |
| `rating.createdOn` | no | ditto |
| `team_tasks.sync` | **yes** | hand-written `_addColumnIfMissing`, no backfill |
| `team_tasks.link` | **yes** | ditto |

## What an unsynced user loses on this bump

**A rating submitted offline that has not yet been queued into the outbox.**
Nothing else.

Spelling that out, because the two tables answer it differently and the
difference is the whole reason `localAuthorityTables` exists:

- **`team_tasks` is preserved.** A task created offline, an edit made offline,
  and the `isNotified` flag that keeps a deadline reminder once-only all
  survive. The two new columns are added by `ALTER TABLE`, so the rows are
  never dropped. A row that predates v46 lands with `NULL` in both, which is
  handled — see *No backfill* below.
- **`rating` is not preserved**, so the upgrade drops and recreates it. Every
  local rating goes with it, including one still flagged `isUpdated`.

That last loss is **not new to this phase** — every schema bump since the
table existed has done it, and it is the documented drop-and-resync policy —
but it is worse than "the rating is gone", and the second audit pass is what
sharpened it:

- A rating already queued for upload still **uploads**, because `outbox` is
  preserved and carries the whole serialized document. What does not survive
  is the *acknowledgement*: `RatingDao.markUploaded` is the only thing linking
  the outbox entry back to the row, and after the drop there is no row for it
  to update. It writes 0 rows and returns quietly.
- So the failure is duplication, not just loss. Rate a resource offline → the
  payload is queued → an update bumps the schema and `ratings` is dropped →
  connectivity returns, the drain POSTs, CouchDB mints document D1, and the
  acknowledgement lands on nothing → the user reopens the resource before the
  first `ratings` walk, sees no stars because the local row is gone, and rates
  again → a second row, a second POST, document D2. `_summarize` then counts
  both: *2 ratings, average 5*. That is Phase 119's defect 3 re-created by the
  schema bump, and it self-heals only if the walk happens to run before the
  user re-rates.
- The narrow window is real: between "the user tapped a star" and "a sync ran",
  the rating is simply gone, with no warning.

**Reported, not acted on:** by the port's own test — *can a sync give this
back?* — `rating` fails it exactly as `team_tasks` does, and the two are
treated differently. A pending rating is keyed by a locally-minted id that
appears in no `_all_docs` keep set, so nothing can restore it. Adding `rating`
to `localAuthorityTables` would close the window, and the ratings walk issues
no `deleteNotIn` (Phase 119 established that per walk), so preserving it costs
nothing in stale rows that the walk would not overwrite anyway. It is left
alone here because the brief allocated this phase a column change, and table
preservation is a policy decision about the drop-and-resync contract that
belongs to whoever owns that contract — not a defect to fix in passing.

## The migration, and what it preserves

```dart
if (from < 46) {
  await _addColumnIfMissing(m, teamTasks, teamTasks.sync);
  await _addColumnIfMissing(m, teamTasks, teamTasks.link);
}
```

`team_tasks` is stepped over by the drop-and-recreate loop, so `createAll`
never alters it — the same reason `isNotified` needed a hand-written step at
v32, `imageName` at v31 and `reactions` at v42. `_addColumnIfMissing` reads the
running table's columns first, so an install jumping several versions does not
abort on a column it already has.

**No backfill, and it is a decision rather than an omission.** The server's
`sync` names the planet that authored the task, and nothing on an existing row
records it, so there is nothing truthful to write. `link` *could* be
reconstructed as `{"teams": <team_id>}` — that is where `TeamTask.fromJson`
reads `teamId` from — but only for a row whose `link` carried nothing else.
Leaving both `NULL` costs nothing, because `TeamTasksRepository.serialize`
falls back to the `upsertTask` rebuild for a blank column, which is
byte-for-byte what the port uploaded for **every** task before this version. So
no task already on a device changes shape; only a task pulled *after* the
upgrade gains the server's own values.

Three tests cover it, and the third exists because the first two were
**tautological** — which is the finding of this phase's second pass, and it
was found against green code.

`a team task keeps its sync and link across v46` and `the v46 columns are
added, not defaulted, on an old row` both pass with the entire `if (from < 46)`
block deleted. The test database is created at the current `schemaVersion`, so
`team_tasks` already carries both columns before `onUpgrade` runs;
`_addColumnIfMissing` finds them present and skips, and every assertion still
holds. They pin the *rows*, not the *ALTER*.

`v46 adds sync and link to a real pre-v46 team_tasks` rebuilds the table in
its genuine v45 shape first — the live DDL minus the two columns — then
upgrades, and asserts the columns appear, the pre-existing row survives with
`NULL` in both, and the new column is writable rather than merely readable.
Deleting the migration step reddens it.

The hand-written v45 column list is checked against the live table rather than
trusted, so a column added to `TeamTasks` later without a migration step fails
here instead of silently widening the "v45" shape to include it.

**This weakness is not unique to v46.** The same shape applies to the existing
`isNotified` (v32), `imageName` (v31) and `reactions` (v42) preservation tests:
each asserts that a row survives, none asserts that its `ALTER` ran. Left alone
as out of scope, and recorded so the next phase adding a column to a preserved
table knows that copying the existing pattern gets a test that cannot fail.

## The defects, each demonstrated failing first

### 1. An edited server task uploaded this device as its authoring planet

The observable half of the `sync`/`link` gap, and the one worth stating first.

`TeamTask.fromJson` stores both sub-objects verbatim as JSON strings
(`model/TeamTask.kt:45-46`, `gson.toJson(JsonUtils.getJsonObject(…))`) and
`TeamTask.serialize` re-emits them verbatim (`:69-70`). `upsertTask`
(`TeamsRepositoryImpl.kt:764-777`) fills them **only when blank**, and it has
exactly one caller — `createTask` (`:796`); `updateTask`, `assignTask` and
`setTaskCompletion` upsert directly and never touch them. So a task that came
from the server keeps the server's `sync` for the rest of its life, through
every later edit.

The port had no column for either and rebuilt both at serialize time from
`teamId` and the session's planet code. Sync a task authored on planet `gua`,
tick it complete here on planet `this-device`, and the upload carried
`sync: {"type":"local","planetCode":"this-device"}` — the port re-stamping
another planet's task as locally authored. `an edited server task uploads the
authoring planet, not this one` fails on the pre-fix code with exactly that
value.

Three more tests pin the storage: the verbatim strings off a page, the `"{}"`
a document with no `sync` key produces (`JsonUtils.getJsonObject` returns an
*empty object* for a missing key, not null — which is also why `upsertTask`
would not treat it as blank), and the pre-v46 row still uploading its rebuilt
pair.

**Where the port deliberately differs.** The Kotlin performs the fill at create
time; the port performs it at serialize time, because the repository holds no
session and the planet code reaches it at upload time. Nothing else reads
either column, so a value written at create time and one computed at serialize
time are indistinguishable — and the same code path covers a row written before
v46, which is what makes the empty migration safe.

### 2. `my_library_mapper` read the writer's key, never the server's

`insertMyLibrary` reads the document key **`tags`** (`MyLibrary.kt:291`) while
`serializeResource` writes the key back out as **`tag`** (`:100`). The
asymmetry is the Kotlin's, and the port had copied the writer's key into the
reader — so the column was empty on every synced row, forever.

Phase 116 called this inert and Phase 119 sharpened it to "inert only because
nothing else fills the column". Establishing which, as the brief asked: **the
column has exactly one reader, in both apps, and it is a writer of documents.**
`ResourcesRepository.serializeResource` (`resources_repository.dart:190`,
port of `MyLibrary.kt:79-108`) emits `'tag': row.tag`, and that document is
attached to an achievement entry and embedded in a team document. Nothing
filters, queries or displays it — tag filtering runs off the separate `tags`
table (Phase 67). So the consequence is precise and narrow: **an achievement's
attached resource shipped an empty tag list where the Kotlin ships the
resource's tags.** Not inert, but not a screen either.

`reads the document key "tags", which is what Planet writes` and `merges
"tags" into what is already stored` both fail pre-fix.

### 3. `privateFor` stored a stringified map no team-id predicate can match

`MyLibrary.kt:294-298` extracts the nested `privateFor.teams` as a **bare team
id**, and the serializers re-wrap it as `{"teams": <id>}` on the way out
(`:174-178`) — the nesting lives in the document and never in the column. The
port read the key as a string, and Dart's `getString` falls through to
`toString()`, so a document carrying `{"privateFor": {"teams": "team-1"}}`
stored the Dart literal `{teams: team-1}`. Exactly the Phase 104
`SurveyMapper.choices` shape.

The guards ported with it, because three of the Kotlin's four outcomes **leave
the stored value alone** rather than clearing it — `Value.absent()` here:

| document | Kotlin | port |
|---|---|---|
| `private: true`, `privateFor: {teams: "t"}` | `privateFor = "t"` | `Value("t")` |
| `private: false` | assignment skipped | `Value.absent()` |
| no `privateFor` key | assignment skipped | `Value.absent()` |
| `privateFor` not an object | assignment skipped | `Value.absent()` |
| `privateFor: {}` (no `teams`) | `privateFor = null` — this one **does** write | `Value(null)` |

Four tests, one per row after the first; all four fail pre-fix.

### 4. The rating's rater and `createdOn` were dropped on the way in

`insertRatingsFromSync` writes both (`RatingsRepositoryImpl.kt:114-118`) and
`Rating.serializeRating` re-emits them (`Rating.kt:44`, `:51`). The port had no
column, so the walk dropped them; `RatingsUploader._toDoc` rebuilt `user` from
the `users` table and sent `row.parentCode` as `createdOn`.

Now: the walk stores the document's `createdOn` and its embedded rater object
as a JSON string with `_attachments` removed first — which is what
`RatingsRepositoryImpl.kt:98-102` does, and for a reason that survives the
port. The Kotlin's stated motive is SQLite's ~2MB `CursorWindow`; here the
base64 profile photo would simply be re-encoded into every rating row and then
into every upload payload. `submit` snapshots the rater the way `setRatingData`
does (`:159-162`), and the uploader sends the stored pair.

Four tests, three failing pre-fix (the fourth, `drops the embedded
_attachments before storing`, passed vacuously against a null column and is
only meaningful after).

**Be clear about what this buys, because it is less than it looks.** Traced
end to end: in *Kotlin*, `serializeRating` is the only reader of either column,
and it runs only on `isUpdated = 1` rows — whose values `setRatingData` has
just overwritten. So the values `insertRatingsFromSync` writes are never read
back in Kotlin either. The user-visible half of this change is therefore the
**upload**, not the sync-in: a rating now uploads the rater object that was
stored when it was rated, rather than one rebuilt from a `users` row that may
not exist. The sync-in half makes the row a faithful copy of the document and
makes the two writers of the column agree, and that is all it does.

## Deliberate divergences from the Kotlin

1. **The stored rater is credential-free.** `UserEntity.serialize()` writes
   `derived_key`, `salt` and `password_scheme` into every document it builds
   (`UserEntity.kt:66-70`) — and, for a member registered offline whose `_id`
   is `""`, the **plaintext** `password` (`:61-65`). So a Kotlin ratings
   document publishes the rater's password verifier into a database any member
   of the planet can read. `RatingsRepository.raterDocument` keeps the four
   fields Planet actually attributes a rating by (`_id`, `name`, `planetCode`,
   `parentCode`). This narrowing already existed in `RatingsUploader._toDoc`;
   what is new is that faithfully filling the column would have *introduced*
   the leak, so the narrowing is now stated rather than incidental. Same
   judgement `MyLibraryMapper` makes about the satellite PIN in a resource URL.
2. **A stored `user` of `"{}"` falls back to the rebuild.** A document with no
   `user` key stores the empty object, and sending it would name nobody where
   the rebuild can still name the signed-in user. The Kotlin sends the empty
   object.
3. **A null `sync`/`link` rebuilds rather than sending null.** `TeamTask.serialize`
   emits `"sync": null` for a null stored value, and the Retrofit Gson has
   `serializeNulls()`, so Kotlin genuinely puts `null` on the wire. The port
   rebuilds instead — which is what it did for every task before v46, and the
   only reason a column is null here is that the migration left it so.
4. **The fill happens at serialize time, not create time** (divergence 1 under
   defect 1 above).
5. **The stored rater is credential-free too, not only the sent one.**
   `insertRatingsFromSync` reduces the document's `user` object through
   `storableRater` — `_attachments` as the Kotlin does, and `derived_key`,
   `salt`, `password_scheme` and `password` as it does not. Storing the object
   verbatim would keep a copy of every rater-on-the-planet's password verifier
   in this device's database file, one per rating row, for a value nothing
   reads back. Caught by the second audit as an inconsistency rather than a
   bug: divergence 1 argued that these fields must not go into a *document*
   while the same commit wrote them into a *database*, and a threat model that
   distinguishes the two has to say why. There is no why.
6. **An unusable `privateFor.teams` stores null rather than the value or the
   document.** A non-string `teams` (an array, say) would stringify into the
   column through `JsonUtils.getString` — reproducing the Phase 104 defect one
   level below the one being fixed. The Kotlin throws out of `asString` and
   `batchInsertResources`' per-document `try`/`catch` silently drops the whole
   resource. Neither is right, so the port writes the null the Kotlin already
   writes for an object with no `teams` key.
7. **`createdOn`, `parentCode`, `planetCode` and the rater object share one
   source.** The Kotlin builds all four from a single `resolvedUser`
   (`RatingsRepositoryImpl.kt:159-162`), so `createdOn == parentCode` holds by
   construction. The first cut of this phase read `createdOn` and the rater's
   codes from the `users` row while `parentCode` came from the caller's
   session, which breaks the invariant whenever the two disagree — and the
   Phase 119 `tablet_users` walk can arrange exactly that, by rewriting the
   stored row under a session object that is never re-read. The caller's
   values now win everywhere; the row supplies only the identity the caller
   does not carry.
8. **`Ratings.isUpdated` defaults to `true`** in the Drift table where the
   Kotlin entity defaults to `false`. Pre-existing, not touched here, and
   latent rather than live: `insertRatingsFromSync` sets it explicitly. It is
   recorded because a future companion on a sync-in path that omits it would
   silently flag every pulled rating for upload.

## Reported, not fixed

- **`rating` is not a preserved table**, discussed under *What an unsynced user
  loses* above. The clearest single sentence: a rating typed offline and not yet
  swept into the outbox does not survive this or any other schema bump, and
  nothing warns the user.
- **`isLocalOnlyPrivate` is not ported** (`MyLibrary.kt:225`, `:292`). It reads
  as a guard protecting a locally created private team resource from having its
  private fields overwritten by a server document — and it cannot do that, in
  either app: `existing` is only ever resolved by matching the document `_id`
  against the row's primary key, while a locally created resource keeps a
  locally-minted key that `markResourceUploaded` never replaces. So no server
  document can select that row. The guard *is* reachable by a different route —
  a course-step-embedded resource whose `_rev` arrives as `""` — where it
  suppresses one pass and then self-heals. Porting a guard whose name describes
  a case it cannot reach, into a port whose `rev` is null rather than `""` for
  a missing key, would be copying the confusion rather than the behaviour.
  Named here so a later phase does not add it casually.
- **`tag` only ever grows.** `mergedList` is a union in both apps, so a tag
  removed server-side is never removed locally. Newly observable, since before
  this phase the column was always empty. Faithful, and left alone.
- **`TeamTask.serialize`'s assignee fallback differs.** The Kotlin emits `""`
  when the assignee's `users` row is missing (`UploadConfigs.kt:87` resolves the
  user and `TeamTask.kt:67-68` falls back on null); the port branches on an
  empty `row.assignee` and emits `{'_id': …}` for an assignee whose user row has
  not synced yet. Out of scope for a column phase, and it is arguably the port
  that is right — the Kotlin silently drops the assignment from the document.
- **`RatingsUploader`'s `users`-table rebuild is unreachable.** Both writers
  of `rating.user` fill the column, and `ratings` is not preserved, so the
  bump leaves no row behind without one. The rebuild — and the
  `_userDao.getById` that feeds it — is kept as a defence for a future writer
  that forgets the column, and its test pins a row shape production can no
  longer produce. Said plainly here because the first cut's comment claimed it
  covered a live case, which is the Phase 106 shape: a correct mechanism read
  backwards.
- **The `ALTER`-blindness in the older preservation tests** — see *The
  migration* above. `isNotified` (v32), `imageName` (v31) and `reactions`
  (v42) each assert a row survives and none asserts its `ALTER` ran.
- **The `_rev`-after-queue hole Phase 119 reported is still open.** Unchanged
  here: every uploader serializes at enqueue time and `OutboxDrainer` replays
  the stored body, so a row queued offline and then synced still posts without
  its `_rev`. It is an outbox change, not a column one.

## Files touched outside this lane's set

None. Lane B owns the submissions upload and pull, including the
`pendingUploads` query inside `app_database.dart`; this phase's edits to that
file are the `schemaVersion` constant and one `if (from < 46)` block appended
at the end of `onUpgrade`, and nothing in the submissions DAO section. Lane C
owns `lib/ui/notifications/` and `lib/repository/notifications_repository.dart`;
neither is touched. No new l10n string was needed, so `app_en.arb` is untouched
as well.

## The tests

26 new, across five files: 10 in `my_library_mapper_test.dart`, 5 in
`team_tasks_sync_test.dart`, 4 in `ratings_sync_test.dart`, 2 in
`ratings_uploader_test.dart`, 2 in `ratings_repository_test.dart`, and 3
migration tests. 2074 pass, up from 2048.

Three of the nine are the pair Phase 74 and Phase 100 each shipped one half
of: `Value.absent()` is how "the Kotlin skips this assignment" is spelled,
and it only means that if the **write path** honours it. The mapper tests
assert the companion; these drive it through the real `MyLibraryDao.upsertAll`
and assert the row — a stored `privateFor` survives a public document, an
absent value on the insert path takes the column default, and a private
document does still overwrite. Turning the absent into an explicit
`Value(null)` reddens the first of the three, so it is not tautological.

Every document is built in the shape CouchDB actually stores — a task with
`sync`/`link` sub-objects and no `status`, a rating with the rater embedded and
a base64 `_attachments` entry hanging off it, a resource with `tags` plural and
a nested `privateFor` — rather than flattened to the columns the row happens to
have. That is the point: a fixture written from the row's columns cannot see a
key the mapper reads under the wrong name.
