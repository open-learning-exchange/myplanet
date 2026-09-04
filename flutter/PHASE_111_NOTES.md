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
   AND EXISTS (SELECT 1 FROM health_examinations AS p
                WHERE p.id = health_examinations.user_id)
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
* `id NOT IN (users)` — a profile row's id is the patient's user-row key
  (`saveHealthProfileBlob`), and `UserDao.getById` matches `_id` as well as
  `id`, so both columns are checked. This is the conjunct that protects the
  profile row of a member registered *on this device*, whose `id` is a local
  `'<millis>'` and whose `userId` is legitimately the CouchDB id it was later
  given. Re-keying that one would publish the health profile under a
  millisecond timestamp no server can resolve to a person — precisely the
  mistake `saveHealthProfileBlob`'s own comment records having already been
  made once.
* `_rev IS NULL` — a row that already uploaded owns a revision, and **nothing
  on the row records which document it is a revision of.** See below; this one
  had a wrong justification in the first cut of these notes. `_id IS NULL`
  rides along and excludes nothing: `couchId` is only ever written by
  `_docToCompanion`, whose rows have `userId == id` and are already out. It is
  kept to say plainly that a row with a server identity is not repairable.
* `EXISTS (… p.id = user_id)` — the collision must actually exist. **This is
  the conjunct the first cut lacked, and its absence was the phase's own worst
  defect.** See below.

### The false positive a `parity-auditor` pass found, and I had claimed could not exist

The first cut of these notes said *"High that the predicate matches nothing it
should not."* That was wrong, and the shape it missed is reachable through the
port's own ordinary write path rather than through anything exotic.

`saveHealthProfileBlob` resolves the profile row with `getByIdOrUserId`, whose
predicate is `id = ? OR userId = ?` — and a legacy examination row satisfies
the second half, because its `userId` **is** the patient's id. The else-branch
then keeps `id: Value(existing.id)` and writes the encrypted `MyHealth` blob
into it. So on a device that has a legacy examination row and no separate
profile row, the first save on a post-Phase-105 build turns the examination row
**into** the patient's profile row, keeping its `health-…` id. Measured, with
the profile row absent or written second:

```
PROBE[legacy-first]    [{id: health-1725000000000000, user_id: org.couchdb.user:ada, hasdata: 1}]
PROBE[no-profile-row]  [{id: health-1725000000000000, user_id: org.couchdb.user:ada, hasdata: 1}]
```

One row, holding the health profile, keyed on an examination id. Every other
conjunct of the repair matches it — `is_updated = 1`, `user_id <> id`,
`profile_id IS NULL`, no `_id`/`_rev`, and `id NOT IN (users)` most of all. The
repair would have re-keyed it and published the patient's emergency contact,
special needs and `userKey` under a millisecond timestamp: **the exact harm
this phase exists to prevent, caused by the phase.**

The `EXISTS` conjunct is the narrowing. It says the row must be colliding with
something: some row is keyed on the `user_id` about to be replaced. A converted
row is the only row for its patient, so it is excluded; the true positive keeps
its profile row, so it is not. `leaves a legacy row that became the profile row
alone` fails on the pre-fix predicate with
`Actual: 'health-1725000000000005'`.

The cost of the narrowing, stated plainly: a legacy examination row whose
patient has no profile row at all is no longer repaired. It does not 409 — it
posts under the patient's id and quietly becomes their profile document on the
server, which is a different and older bug. Repairing that shape needs to know
which of the two rows is the examination, and after the conversion above the
answer is destroyed rather than merely unknown.

**Confidence, restated.** The predicate is now pinned against eight shapes in
`migration_test.dart`, including the one that caught me. What I will not claim
again is that no ninth exists: the reason this one was invisible is that I
reasoned about the rows a *migration* would see rather than about what the
app's own write paths do to those rows between Phase 105 shipping and the
upgrade running.

### What the `_rev` exclusion actually excludes

The first cut justified it as *"a row that already uploaded owns a revision of
the document its old `userId` names."* It does not. Before Phase 107,
`serialize` keyed the upload on the row's **own `id`** — commit `713c5ad` is
the line that changed it:

```
-      if (row.id.isNotEmpty) '_id': row.id,
+      if (docId.isNotEmpty) '_id': docId,     // docId = row.userId ?? ''
```

and `markUploaded` writes `rev` and `is_updated`, never `couchId`. So an old
`_rev` may belong to the `health-…` document or to the patient's, and nothing
on the row says which. For half of them the repair is exactly right and
excluding them is what keeps them broken. They are still left alone, because a
guess here writes a revision of a document that may not exist — but the reason
is *ambiguity*, not the confident story the first cut told. **These rows remain
a silent loss path**, and the banner in §3 will now report them without being
able to fix them.

The first cut also said such a row *"overwrote the patient's profile document
on the server with an examination."* It did not: under the pre-107 keying it
created its own document under `health-…` and overwrote nothing.

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
in one way worth recording, and the first cut of these notes understated it.

**Kotlin retries automatically and free; the port did not retry at all unless
the clinician re-saved.** `HealthQueue.queuePending` has exactly one caller —
`onSaved` on the examination form. The health screen's sync button and the sync
centre both run `HealthSyncNotifier`, which is `HealthRepository.sync`, a
**pull**. And `OutboxDrainer` only selects `pending` rows, so no resume-drain or
background job ever touches an abandoned one. Kotlin's `uploadHealth` runs from
`AutoSyncWorker` and `UserDataWorker` on every sync and re-attempts everything
still flagged `isUpdated`. So the port's abandonment was not merely louder than
Kotlin's silence — for a transient 4xx (a momentarily wrong PIN earns a 401,
which `code < 500` calls permanent) it was **worse**. That is the divergence
worth naming, not the abandonment itself.

Hence the banner carries a **Retry**, using the existing `retry` key: it calls
`queuePending` and drains. `enqueue` ignores the abandoned row and writes a
fresh pending one, so it is a real re-attempt rather than a nudge. Wiring the
same re-queue into the health sync area would be the fuller fix — Kotlin's sync
does both directions — but that is a shared surface and this is a parallel
round; logged rather than taken.

What was missing was not retrying but *saying so*. `OutboxDao` gains
`abandoned(uploadType)` and `forItem(uploadType, itemId)` — before this,
nothing selected an abandoned row at all, and keeping the row **was** the whole
of what `OutboxOutcome.abandoned`'s doc comment calls "inspectable rather than
silently discarded". `rejectedHealthRecordCountProvider` counts them by
**distinct `itemId`**, not by row, so the number is of stranded records rather
than of attempts, and `MyHealthScreen` opens with a caution card when it is
non-zero: *"N health records could not be sent to the server. They are still
stored on this device."* One new `app_en.arb` key, `healthRecordsRejected`.
The card renders on the no-patient branch too — "no patient resolves" is
exactly the state a clinician might be in while wondering where a reading went
— and the count is device-wide rather than per-patient, which the wording says.

**The banner had to be able to clear, and in the first cut it could not.** An
abandoned row is never reused, `markCompleted` deletes only the row that
succeeded, and `cleanup()` has no caller — so a record refused once would have
been reported as stranded for the life of the install, *including after the v45
repair delivered it*, with no action offered and nothing true about the
warning. `HealthUploader.handler` now withdraws the abandoned rows for an item
when the server accepts it, which is the only event that makes an old refusal
untrue. `a delivered record stops being reported as stranded` runs the real
upgrade sequence — refused first, repaired second — which is what the earlier
test missed by migrating before the first drain.

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

`HealthExamination.kt:110-111`:

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
  `OutboxDao.forItem`/`abandoned`/`clearAbandonedFor`.
* `lib/repository/health_uploader.dart`, and two methods on
  `lib/repository/outbox_repository.dart` (`clearAbandoned`). Neither file is
  named in another lane's set; the outbox change is purely additive.
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
