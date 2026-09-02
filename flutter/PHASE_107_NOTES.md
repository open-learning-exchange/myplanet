# Phase 107 — one member, one row: the user identity rule and the health key it protects

Phase 105's parity audit found this and logged it as needing a round of its
own. Its words:

> `UserMapper.fromDoc` keys the cached row on the document `_id`
> unconditionally, where `buildUserFromJson` reuses the existing row through
> `getUserByAnyId` and keeps its local `id` (`UserRepositoryImpl.kt:297-308`).

Everything below was demonstrated failing against the pre-fix code first, one
defect at a time. Four defects, all in this lane's files.

## 1. The identity rule

Kotlin's `buildUserFromJson` (`UserRepositoryImpl.kt:292-316`) resolves the row
a document belongs to **before** writing it:

```kotlin
val id = JsonUtils.getString("_id", jsonDoc).takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
val existingUser = getUserByAnyId(id)          // WHERE id = :id OR _id = :id LIMIT 1
val user = existingUser ?: … ?: UserEntity().apply { this.id = id }
applyJsonToUser(jsonDoc, user, settings)       // reassigns `id` only when blank
```

The `_id` half of that lookup is the point: a document finds a row through its
`_id` **column** as readily as through its key, and the row it finds keeps its
own `id`. `UserMapper.fromDoc` had no lookup at all — it wrote
`id: Value(couchId)` unconditionally, and `insertOnConflictUpdate` keys on the
primary key, so a document whose `_id` sat in some row's `couchId` **inserted a
second row** instead of updating the first.

The member that happens to is the member registered on this device.
`become_member_screen` mints `id = '<millis>'` with no `couchId`
(`become_member_screen.dart:258`); the health screens mint `key`/`iv` on that
row; `UserDao.updateUserSecurityData` records the server-assigned `couchId` on
that same row when the upload lands. The first online sign-in then produced a
duplicate keyed on `org.couchdb.user:<name>` — carrying `derived_key` and
`salt`, and **no `key`/`iv`**.

`fromDoc` now takes the row it is updating; `UserRepository._cacheUserDoc`
performs the lookup (skipped for a document with no `_id`, matching Kotlin,
where the id it searches by is a fresh UUID that can never match) and re-reads
the row by the key it was written under, which is what `upsertUser` does
(`getById(entity.id)`). That re-read replaces a `getByName(username)`, and the
replacement matters on its own: a name carries no unique constraint, two
planets can hold an `ada`, and `LIMIT 1` was picking the session user by scan
order.

*Failing-first:* "the online login reuses the local row instead of adding one"
reports 2 rows; "the health key and iv survive the transition" reports a
`null` key on a row that answers to the member; the same assertion inside the
end-to-end health test fails at step 5.

### What the consequence actually is — a correction to Phase 105's wording

Phase 105 wrote that `getById(couchId)` "matches both — `limit(1)` then picks
by scan order, which for this slice can resolve the patient to the row
*without* the `key`/`iv`". Measured: it does not, today. Both rows are matched
by `WHERE id = ? OR _id = ?`, `_id` has no index, so SQLite full-scans and
returns the **older** row — the member's real one — first. The end-to-end test
in `health_repository_test.dart` was written to catch a failed decryption and
does not: with the fix reverted it fails on the row count at step 5, and step 6
still decrypts.

So state it as it is. What was **deterministically** broken:

* two rows for one member, so every list built from the users table showed them
  twice — the login account picker (`getSavedUsers`), the health patient
  picker, the survey recipient list;
* one of those rows carries no `key`/`iv`, and it is reachable by the CouchDB
  id the health screens address patients with (`patientIdOf` prefers `couchId`);
* the profile fields and the queued photo below.

What was **latent**: which of the two rows a lookup resolves to. SQLite
promises no order without `ORDER BY`; an index on `_id`, a `VACUUM`, or rowid
reuse after a delete flips it, and the health records the wrong row cannot
decrypt are not recoverable from anywhere — the `key`/`iv` are generated on the
device and never uploaded, which is why `users` is a preserved table. A
duplicate row is not a cosmetic defect when one of the two is the only thing
standing between a patient and their medical history, but it is honest to say
the loss had not happened yet rather than that it had.

## 2. A document that omits a field wiped the stored one

`applyJsonToUser` guards twelve fields with
`if (new.isNotEmpty() || old.isNullOrEmpty()) field = new` — `joinDate` with
`if (newJoinDate != 0L || joinDate == 0L)` — and assigns the rest
unconditionally. `fromDoc` wrote every field unconditionally, which on an
*update* is a `Value(null)` over a stored value: the same shape as the Phase 56
security-data fix and the Phase 74 reactions round trip. A member who filled in
their profile offline and then signed in online lost every field the `_users`
document does not carry.

The guarded set, in Kotlin's order: `joinDate`, `firstName`, `lastName`,
`middleName`, `email`, `phoneNumber`, `level`, `language`, `gender`,
`dob` (`birthDate`), `birthPlace`, `age`. Unguarded, deliberately: `_rev`,
`_id`, `name`, `roles`, `isUserAdmin`, `planetCode`, `parentCode`,
`password_scheme`, `iterations`, `derived_key`, `salt`, `isArchived`. An absent
`Value` is what "keep the stored one" means to `insertOnConflictUpdate` — the
column stays out of the `DO UPDATE SET` list — and on an insert there is no
stored value, so the incoming one is written either way.

`password` is ported too, with its guard: `if (_id?.isEmpty() == true)` reads
`_id` **after** `_id = newId` is assigned, so the plaintext password is taken
only for a document with no `_id`, which is the guest shape.

*Failing-first:* with only the two guard helpers reverted to
`return Value(incoming)`, "a field the document omits keeps its stored value"
fails and nothing else does.

## 3. An unsynced profile photo was erased by a re-login

`UserEntity.addImageUrl` (`UserEntity.kt:158-166`) writes `userImage` **only**
when the document has a non-empty `_attachments`. `fromDoc` wrote
`Value(_attachmentName(doc))` unconditionally, so a document without
attachments nulled the column — and the value it nulled can be a local file
path that the user uploader has queued and not yet sent. The photo was gone
before anything could read it.

*Failing-first:* with only `_imageName` reverted to `Value(name)`, "an unsynced
profile photo survives a document without attachments" fails and nothing else
does.

## 4. The two adjacent health items are one rule — and both are ported

The round offered these as separate, optional items. They are not separable.

`HealthExamination.serialize` keys the document on `userId`, not on the row's
own id, and **omits `_id` entirely** while `userId` is null or empty
(`HealthExamination.kt:96`). `HealthExaminationDao.getUpdated()` is
`WHERE isUpdated = 1 AND userId != ''` — and in SQL `userId != ''` is false for
NULL too, so Kotlin excludes a blank `userId` along with a missing one. The
guard is what makes serialize's omit-`_id` branch unreachable from the upload
path: a POST to `/health` with no `_id` makes CouchDB mint a fresh document, so
one examination would become a **new record on every drain**.

The port had taken neither half — `_id` from `row.id`, and
`userId.isNotNull()`. Porting only the `_id` half would have opened exactly
that hazard, which is why both are here.

**The legacy-row decision, stated deliberately.** Phase 105 left this because
changing `_id` re-keys documents an older build already wrote. Where the two
ids differ:

* Examinations written by the current build have `userId == id`
  (`createExamination` defaults `userId` to the row's own id, the invariant
  `_docToCompanion` maintains for every synced row). No change.
* The **profile row** is where they differ. `createPojo`
  (`HealthExaminationActivity.kt:372-378`) writes `_id` = the patient id the
  screen was opened with and `userId` = `user?._id`, the patient's CouchDB id;
  `app_providers.dart:396-405` rewrites that `userId` to the couch id once the
  account uploads (Kotlin's counterpart is `HealthRepositoryImpl.kt:78`). So
  after the fix the port uploads that row under the couch id, which is both
  what Kotlin uploads it under and the id the sync-in direction would key the
  row on.
* Examination rows written by a **pre-Phase-105** port build carried the
  *patient's* id in `userId`, so they would now upload under the patient's
  profile-document id and overwrite it. That is the real exposure, and it is
  the reason to be explicit: those rows exist only on a development device, the
  port is not shipped (`app/` remains the shipping app), and Phase 105
  established that such rows could not be recorded successfully in the first
  place — they were the invisible ones. Against that, leaving the port keying
  documents on `row.id` diverges from the specification permanently and in a
  direction that cannot be corrected later without the same re-keying.

Ported, therefore, and this note is the record of why.

One divergence in the same code found and **deliberately kept**:
`saveHealthProfileBlob` writes `userId: Value(user?.couchId ?? userId)` where
Kotlin writes `user?._id` with no fallback. Kotlin's profile row for a member
whose account has not uploaded therefore has a null `userId`, is excluded by
`getUpdated()`, and **never uploads at all**. The port's fallback keys it on the
device-local id instead, which is what the port already does for examinations.
Matching Kotlin here would mean deleting a working upload path for exactly the
member class this phase is about, so the fallback stays and the divergence is
recorded here instead.

## Found and left

* **The guest-user migration is unported and unportable as things stand.**
  `buildUserFromJson`'s middle branch — when no row matches and the id starts
  `org.couchdb.user:` with a non-empty name, adopt the guest row of the same
  name (`getGuestUserByName`, `_id` starting `guest_`), delete it, and reuse it
  under the new id — has nothing to adopt: no port path creates a guest row.
  Worth knowing before guest login lands, because the port's own guest tests
  are inconsistent with Kotlin's: `home_screen`/`settings_screen`/
  `voices_screen` check `user.id.startsWith('guest')` while
  `UserEntity.isGuest()` and `insertUsersFromSync` check
  `_id?.startsWith("guest_")`, and `validateUsername` checks
  `couchId?.startsWith('guest')`. Three spellings of one predicate.
* **A guarded field the document omits stores `null` where Kotlin stores `''`.**
  `JsonUtils.getString` returns `""`, so Kotlin's `field = new` writes an empty
  string; the port writes `null`. Unchanged: it is how every mapper in the port
  already reads a missing string, and normalising it is a port-wide change, not
  a one-file one.
* **`serialize` writes `creatorId` from `creatorId`; Kotlin writes it from
  `profileId`** (`addString(object, "creatorId", health.profileId)` — note the
  field, not a typo in this note). The two are equal for every row either app
  authors (`saveData` sets both to `health.userKey`), so this only shows up for
  a synced document whose server-side `creatorId` differs, where Kotlin
  overwrites it on re-upload and the port preserves it. Left: the port's
  behaviour is the defensible one and nothing reads the difference.
* **`serialize` includes an empty-string `profileId`/`gender`/`bp` where
  `JsonUtils.addString` omits it.** The port's guards are `!= null`, Kotlin's
  are "non-null and non-empty". Not chased here — it is a sweep across every
  `serialize` in the port rather than a health-only fix.

## For the integrator

* **No schema change.** Drift stays at v44. `app_database.dart` is edited but
  only two query bodies (`HealthExaminationDao.getUpdated`, plus the comment on
  `UserDao.getById`); no table, column or index was touched. I considered
  asking for an index on `users._id` — it would make the duplicate-row lookup
  cheap — and did not, because an index there is precisely what would flip
  SQLite's scan order on a device that still carries a duplicate from an older
  dev build, turning the latent wrong-row resolution into the real one. The
  duplicates have to be reconciled before that index is safe.
* **No new `app_en.arb` keys.** Nothing user-facing changed.
* Files touched: `lib/data/local/user_mapper.dart`,
  `lib/repository/user_repository.dart`, `lib/repository/health_repository.dart`,
  `lib/data/local/app_database.dart`, and the three test files.
