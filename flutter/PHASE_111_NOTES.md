# Phase 111 — the legacy health row, the 409 that eats a record, and making it visible

Phase 107 found the defect and handed the decision on: a pre-Phase-105
examination row collides with the patient's profile document, and the loser of
that collision takes a **409** the outbox classifies as permanent, so a
clinician's reading never reaches the server and nothing anywhere says so. It
named two honest options — a one-time data repair needing a schema number, or
accepting the exposure — and recommended accepting.

The integrator chose the repair and allocated `schemaVersion` **45**. This is
that repair, plus the observability half, which is independent of it and
matters more.

## 1. What actually goes wrong

`HealthRepository.serialize` keys the uploaded document on the row's `userId`,
not on its own id — `if (!health.userId.isNullOrEmpty()) object.addProperty("_id", health.userId)`
(`HealthExamination.kt:96`). Two rows with the same `userId` therefore claim
the same CouchDB document.

Before Phase 105 the examination form wrote the **patient's** id into a new
row's `userId` (`createExamination(userId: _userId, …)`), so on a device that
recorded an examination then, `health_examinations` holds:

| row | `id` | `userId` | serialized `_id` |
|---|---|---|---|
| profile | `org.couchdb.user:ada` | `org.couchdb.user:ada` | `org.couchdb.user:ada` |
| examination | `health-1725000000000000` | `org.couchdb.user:ada` | `org.couchdb.user:ada` |

`saveHealthProfileBlob` writes before `createExamination`, so on the drain the
profile is POSTed first and wins. The examination gets a 409.
`OutboxDrainer._send` treats every `code < 500` as permanent, so the operation
is abandoned on its first attempt, `markUploaded` never runs, `isUpdated` stays
true, and the row sits on the handset looking, to every screen, exactly like a
record that was filed.

`health_legacy_conflict_test.dart` holds that whole path against a fake CouchDB
that enforces `_id` uniqueness the way the real one does — the assertion is the
**record loss** (`couch.documents.keys` is `[patientId]` and nothing else), not
the status code.

## 2. The repair, and how narrow it is

```sql
UPDATE health_examinations SET user_id = id
 WHERE is_updated = 1
   AND user_id IS NOT NULL AND user_id <> id
   AND profile_id IS NULL
   AND _id IS NULL AND _rev IS NULL
   AND id NOT IN (SELECT id FROM users)
   AND id NOT IN (SELECT _id FROM users WHERE _id IS NOT NULL)
```

`user_id = id` is not an invention: it is what Phase 105 made every new
examination do (`createExamination`: `userId ?? id`), what `_docToCompanion`
maintains for every synced row (`userId: doc['_id']`), and what Kotlin's
`saveData` writes (`_id` and `userId` are the same `generateIv()`). A repaired
row is indistinguishable from one recorded today and posts as its own document.

**Phase 107 declined to write an id-prefix heuristic and was right to.** The
rule here is not one. `profile_id IS NULL` is a *positive* identification, and
it comes from the port's own history rather than from the shape of a string:
Phase 105's headline finding was that the pre-Phase-105 form "wrote no
`profileId`, which is the only link `getPatientHealthRecords` looks examinations
up by" (commit `a5dd24d`). Every row from the server carries the `profileId`
Planet stored; every row written since Phase 105 carries `health.userKey`. Only
the broken generation has none.

Each remaining conjunct excludes something real, and there is a test for each:

* `is_updated = 1` — a clean row is never queued, so it is not in the
  collision. Phase 107 drew the same boundary.
* `user_id <> id` — the post-Phase-105 shape and every synced row have them
  equal.
* `_id IS NULL AND _rev IS NULL` — a row that already uploaded owns a revision
  of the document its old `userId` names. Re-keying it while keeping that
  `_rev` would post a revision of a document that does not exist under the new
  id: **the same 409, newly caused by us.** Those rows are left alone. See the
  limits below.
* `id NOT IN (users)` — a profile row's id is the patient's user-row key
  (`saveHealthProfileBlob`), and `UserDao.getById` matches `_id` as well as
  `id`, so both columns are checked. This is the conjunct that protects the
  profile row of a member registered *on this device*, whose `id` is a local
  `'<millis>'` and whose `userId` is legitimately the CouchDB id it was later
  given. Re-keying that one would publish the health profile under a
  millisecond timestamp no server can resolve to a person — precisely the
  mistake `saveHealthProfileBlob`'s own comment records having already been
  made once.

**Confidence.** High that the predicate matches nothing it should not — every
excluded shape is pinned by a test in `migration_test.dart`. Moderate that it
catches *every* legacy row, and the gap is deliberate: a legacy row that
already uploaded is excluded, because there is no safe repair for it. That row
overwrote the patient's profile document on the server with an examination, and
a migration cannot unpick that; it needs a sync-in, which is a different
problem.

**What a user with pending local health records loses to this bump: nothing.**
`health_examinations` and `users` are both in `localAuthorityTables`, so the
upgrade steps over them — the drop-and-recreate loop is for CouchDB caches
only. The repair changes one column on rows that could not upload anyway, and
`the repair does not cost the record it repairs` asserts the reading, its
ciphertext and its `isUpdated` flag all survive. There is no shape change, so
`tables.dart` is untouched and there is no `_addColumnIfMissing` step to write.

## 3. Making the 409 visible — the half that is not about legacy rows

The repair only reaches rows that already exist. A conflict can still happen
tomorrow: the profile row's `_rev` goes stale when another device updates the
same patient, and `cacheDocuments` deliberately skips a locally-edited row
(`if (current?.isUpdated == true) continue`), so a dirty profile row never
learns the new revision. Every one of those ends the same way — abandoned,
silent.

**Kotlin has nothing to port here.** `uploadHealthData`
(`HealthRepositoryImpl.kt:111-141`) reads `res.body()?.has("id")`, which is
false for a 409, returns null, and `markHealthExaminationsUploaded` simply
does not include the row — leaving `isUpdated` set so the next sync tries
again, forever, with the same stale `_rev`. Nothing is logged; the `catch` only
runs for a throw. So Kotlin is not *handling* the conflict either. It differs
in one way worth recording: **Kotlin retries indefinitely where the port
abandons.** In practice the port also retries, because
`OutboxRepository.enqueue` only looks for an *open* operation and an abandoned
row is not one — so each save queues the doomed record afresh and leaves one
more abandoned row behind. The rows accumulate; `cleanup()` exists and has no
caller.

What was missing was not retrying but *saying so*. `OutboxDao` gains
`abandoned(uploadType)` and `forItem(uploadType, itemId)` — before this,
nothing selected an abandoned row at all, and keeping the row **was** the whole
of what `OutboxOutcome.abandoned`'s doc comment calls "inspectable rather than
silently discarded". `rejectedHealthRecordCountProvider` counts them by
**distinct `itemId`**, not by row, so the number is of stranded records rather
than of attempts, and `MyHealthScreen` opens with a caution card when it is
non-zero: *"N health records could not be sent to the server. They are still
stored on this device."* One new `app_en.arb` key, `healthRecordsRejected`.

It is a `FutureProvider`, not a stream, and that is not a style preference: a
live drift query held open for the life of the screen leaves a pending timer
that fails *every* widget test in `my_health_screen_test.dart` with "A Timer is
still pending even after the widget tree was disposed". The screen re-reads it
on build, and pull-to-refresh invalidates it — which is when a clinician would
look.

This banner is the port's own addition; Kotlin has no counterpart, because it
has no permanent-failure state to report. It is kept anyway on the grounds the
brief states: a record that silently never reaches the server is the worst
failure this app can produce, and a health record is the worst one to produce
it with.

## 4. `creatorId` — a Kotlin quirk, now ported

`HealthExamination.kt:109-110`:

```kotlin
JsonUtils.addString(`object`, "profileId", health.profileId)
JsonUtils.addString(`object`, "creatorId", health.profileId)
```

Both wire fields come from `profileId`. It reads like a copy-paste slip and may
be one, but it is the shape Planet has always been sent by myPlanet, and the
brief's question — does "equal for every row either app writes" stay true? —
has a *no* in it. Both apps set the two columns to the same `health.userKey`
when authoring (`HealthExaminationActivity.kt:248-249`, and the port's form),
and Kotlin's own serializer guarantees a myPlanet-authored server document has
them equal. But `_docToCompanion` copies the two independently from the
document, so a record **Planet itself wrote** with `creatorId ≠ profileId`,
pulled in and edited on the device, went back out with the port preserving the
server's `creatorId` where Kotlin replaces it. That is the one case the field
is load-bearing, and it is the one where the two apps disagreed. `serialize`
now writes `profileId` into both, including the null case — `addString` skips a
null, so a row with no `profileId` sends no `creatorId` at all.

## 5. `getUpdated()` — already correct, no change

The other audit item was `getUpdated()`'s `userId.isNotNull()` against Kotlin's
`userId != ''`. It has already been fixed and pinned: the port reads
`isUpdated.equals(true) & userId.isNotNull() & userId.equals('').not()`
(`app_database.dart`), whose doc comment spells out that `userId != ''` is
false for NULL as well, so Kotlin excludes a blank `userId` along with a
missing one. `health_repository_test.dart:1013` and `:1035` pin both halves.
The audit item was stale.

## 6. Files touched, for the integrator

Owned by this lane, except where noted:

* `lib/data/local/app_database.dart` — `schemaVersion` 45, the repair step,
  `OutboxDao.forItem`/`abandoned`.
* `lib/repository/health_repository.dart` — the `creatorId` serializer.
* `lib/providers/health_provider.dart` — `rejectedHealthRecordCountProvider`.
* `lib/ui/health/my_health_screen.dart` — the caution banner, and the refresh
  handler now invalidating it.
* `test/data/local/migration_test.dart`, `test/ui/health/my_health_screen_test.dart`,
  and the new `test/repository/health_legacy_conflict_test.dart`.
* **`lib/l10n/app_en.arb` — one key, `healthRecordsRejected`, inserted directly
  after `healthRecordNotAvailable`.** Lane A owns `lib/l10n/`; this is the only
  line of theirs this branch touches, and it is a pure insertion.
* **`lib/data/local/tables.dart` is NOT touched.** The repair needs no column.
