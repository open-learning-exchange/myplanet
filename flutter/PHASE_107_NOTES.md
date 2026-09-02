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

---

# A second round, after the parity audit

A `parity-auditor` pass confirmed all five claims above, corrected two of them,
reviewed the fix, and found three gaps in it plus fourteen divergences nobody
had claimed. Three more defects are fixed here, each demonstrated failing
first; the rest are recorded below, because several are outside this lane's
files and two are more severe than anything this phase set out to fix.

The two corrections are worth keeping. On the headline: the audit reached the
same conclusion the measurement above did — SQLite full-scans an unindexed
`users` and returns the older, key-bearing row, so the undecryptable-records
harm was **latent**. But it named the harm that was *live* and that this phase
had described only as "a duplicate name", and it is worse than cosmetic:

> **An online login stopped refreshing the member's row.** Everything
> `applyJsonToUser` writes unguarded — `rolesList`, `derived_key`, `salt`,
> `password_scheme`, `iterations`, `isArchived`, `userAdmin`, `_rev` — landed
> on the duplicate, while the session kept resolving the original.

So: a manager grants a role, resets a password, or archives an account in
Planet; the member signs in online on this device; role-gated UI stays locked,
and `loginOffline` goes on verifying against the **stale** `derived_key`/`salt`
— the old password keeps working offline and the new one fails. That is the
defect the fix actually removed, and it needed no scan-order luck at all.

## 5. The health profile row uploaded under a millisecond timestamp

The second correction overturns a call made above. This note argued that
`saveHealthProfileBlob`'s `userId: Value(user?.couchId ?? userId)` fallback was
a harmless improvement on Kotlin's `pojo?.userId = user?._id` and should stay.
It is not harmless, and the reason is the rule this phase had just finished
describing: **`userId` is what decides whether `getUpdated()` selects the row
at all.**

Kotlin writes null for a member whose account has not uploaded, `userId != ''`
withholds the row, and nothing is sent until the account has a server identity
— at which point `app_providers`' `updateUserId` stamps the CouchDB id in and
the row uploads correctly. The port's fallback wrote the device-local
`'<millis>'` id, which is neither null nor empty, so the row *was* selected and
POSTed to `/health` keyed on a millisecond timestamp that no server and no
other device can resolve to a person. Two apps, one recording nothing and one
recording an unaddressable document, and the port's was the wrong one.

Both `saveHealthProfileBlob` and `saveHealthProfile` now write
`Value(user?.couchId)`, and both **re-stamp** it on every later save, which is
what `updateUserHealthProfile` does unconditionally
(`HealthRepositoryImpl.kt:231`) and the port's update branches did not.

*Failing-first:* "a member with no couchId gets a null userId, so nothing
uploads" finds the local id in the column and a row in `getUpdated()`. The
third test in that group ("a later profile save re-stamps the couchId") passes
either way — `patientIdOf` already prefers the couch id, so the value was
already right on that path — and is a regression guard, not evidence.

## 6. A rejected manager login cached the account anyway

`checkManagerAndInsert` (`LoginSyncManager.kt:167-175`) tests
`isManager(jsonDoc)` and returns **before** `saveUser`. The port wrote the row
and rejected afterwards, leaving behind an account the Kotlin declines to keep.
The check moved ahead of the cache, and it now reads the document rather than
the stored row — `UserMapper.docIsManager`, a port of
`LoginSyncManager.isManager(jsonDoc)`, because at that point there is
deliberately no row to read.

*Failing-first:* "a rejected manager login caches nothing" finds a row.

## 7. A test that had stopped testing, again

`user_mapper_test`'s "is null when there are no attachments" asserted
`companion.userImage.value, isNull` — and `Value.absent().value` is *also*
null, so it passed identically before and after the fix in §3 and could not see
the distinction that fix exists to make. Both attachment tests now assert
`.present`, which fails on the pre-fix unconditional write (verified). Twelve
companion-level tests join them, covering the identity resolution, the
`generateLocalId` fallback, the `password` guard, and the six guarded fields
the repository-level tests do not reach (`email`, `middleName`, `gender`,
`dob`, `age`, and the empty-stored-value branch).

This is the third phase in a row to find a fixture or assertion that had
absorbed a bug instead of reporting it (Phase 103's achievements uploader,
Phase 105's `seedHealthRecord`). The tell here was different and worth
naming: **an assertion that reads `.value` off a drift companion cannot
distinguish "wrote null" from "wrote nothing"**, and that distinction is the
entire subject of every preserve-the-stored-value fix this port keeps making.
Assert `.present`.

## The gap this phase's own fix leaves open

**Legacy health rows are now a live question rather than a deferred one, and
the integrator has to decide it.** §4 argued that re-keying documents on
`userId` was safe because the port is unshipped. The audit sharpened the
outcome and I was wrong about its shape: a pre-Phase-105 examination row
(`id = 'health-<micros>'`, `userId = <patientId>`) does not merely overwrite
the patient's profile document — whichever of the two documents is POSTed
second gets a **409 conflict**, which is not in the outbox's retryable class,
so that record never reaches the server and nothing logs it. `saveHealthProfileBlob`
writes before `createExamination`, so the profile usually wins and the
examination is the record that vanishes.

There is no in-code fix for this: the ambiguity is in the stored rows, not in
`serialize`, and telling a legacy examination row apart from a profile row
needs a heuristic on the id prefix that I am not willing to write. The two
honest options are a one-time data repair in a migration step
(`UPDATE health_examinations SET user_id = id` for the rows that predate
Phase 105) — which needs a schema number I have not allocated — or accepting
the exposure.

**My recommendation is to accept it**, and the reason is specific rather than
general: for such a row to matter it must be `isUpdated = true` *and* have
`userId != id`, and Phase 105 established that the rows written that way were
the ones the port **could not record successfully at all** — invisible to the
screen, and destroying the profile row on the second save. They exist, if
anywhere, on a development emulator that has since been wiped. If the
integrator knows of a device carrying them, ask me for the repair and allocate
a number; the statement is one line and `health_examinations` is a preserved
table, so a bump runs it without losing anything else.

**Duplicate rows an older dev build already wrote are not healed either.**
Kotlin has `cleanupDuplicateUsers` (`UserRepositoryImpl.kt:837-856`: group
`getDuplicateUsers()` by name, sort `org.couchdb.user:` ahead of `guest_`,
`deleteByIds` the rest), called from `BecomeMemberActivity.kt:204`. The port
has neither it nor `UserDao.getDuplicateUsers`/`deleteByIds`. Deliberately not
written here: its caller is `become_member_screen.dart`, which this lane does
not own, and an uncalled repository method is exactly the smell Phase 90 went
back and corrected. It wants the round that also fixes the two registration
defects below, since that is the same file.

## From the audit — found, verified, and outside this lane

The first two are the severe ones. Both are in `_buildNewUserDoc`
(`user_repository.dart`, which *is* my file) but neither can be fixed usefully
without `become_member_screen.dart`, which is not, so they are reported rather
than half-fixed.

* **A member registered by the port cannot use the app.** `createMember` sends
  `roles: ["learner"]` (`UserRepositoryImpl.kt:522`); the port sends
  `<String>[]` and stores `rolesList: const []`
  (`become_member_screen.dart:265`). `home_screen.dart:351-358` then renders
  `InactiveDashboardScreen` for `rolesList.isEmpty && !userAdmin`, so
  registering through the port's own become-member screen and logging in gives
  "User not activated, please contact administrator" with no path forward on
  the device. The string `learner` appears nowhere in `flutter/lib`. Fixing
  only the document half leaves the member inactive until their first *online*
  login, which is why both halves belong in one change.
* **The profile the member just typed never reaches Planet.**
  `_buildNewUserDoc` sends `name`/`password`/`type`/`roles`/`isUserAdmin`/
  `joinDate`/`planetCode`/`parentCode`. `createMember`
  (`UserRepositoryImpl.kt:501-526`) also sends firstName, lastName,
  middleName, email, language, level, phoneNumber, birthDate, gender and
  `betaEnabled`. The port's local row is written `isUpdated: false`, and
  `pendingSyncUsers` matches only a blank `couchId` or the dirty flag, so once
  `uploadNewUser` fills `couchId` nothing ever ships those columns. Register
  "Ada Lovelace, ada@example.org" and the Planet account has no name and no
  email, and no other device ever learns them. The values are all on the local
  row, so `_buildNewUserDoc` reading it by `localId` is the shape of the fix.
* **Nothing re-queues a dirty health row.** Kotlin re-scans `getUpdated()` on
  every `AutoSyncWorker` (`:113`) and `UserDataWorker` (`:54`) run, and runs
  `getUpdatedForUser` during become-member
  (`ProcessUserDataActivity.kt:175`). In the port `HealthUploader.queuePending`
  has exactly one caller — `onSaved` after a save
  (`health_provider.dart:485`) — `HealthQueue.queuePending` returns 0 when
  `serverConfigProvider` is null, and the sync centre wires health as **pull
  only** (`dashboard_sync_provider.dart:252`). An examination whose save-time
  enqueue was skipped stays on the handset indefinitely, and
  `getUpdatedForUser` has no caller at all. This one matters more after §5,
  which deliberately makes more rows wait for a server identity: something has
  to come back for them.
* **`applyJsonToUser`'s `planetCode` preference write is unported.**
  `UserRepositoryImpl.kt:273-275` pushes the *document's* `planetCode` into
  `SharedPreferences` on every login, and `getPlanetCode()` feeds a
  submission's `source`, the survey filter, the voices planet code and the
  become-member document. The port only stores the configured community code.
  They differ for a user whose `_users` document names a different community
  than the server they signed in against. A pure mapper cannot do this; it
  needs a side effect in `_cacheUserDoc`, and `UserRepository`'s constructor
  is `(api, userDao)` — adding `PlanetPrefs` touches `app_providers.dart`.
* **The become-member → login handoff is dead code.**
  `become_member_screen.dart:229-236` passes
  `extra: {username, password, isNewMember}`; nothing reads `state.extra`, and
  `isNewMember` has one grep hit — its own write. Kotlin's
  `LoginActivity.handleAutoLogin` (`:214-232`) auto-submits after 500 ms. The
  port's new member has to retype both.
* **`user_uploader.dart:171-175` describes behaviour the code does not have.**
  Its comment says clearing a stale local file path stops `readImageBytes`
  re-embedding the photo; `markUploaded` writes only `couchId`, `rev` and
  `isUpdated`. So `userImage` keeps the picker path after a successful upload
  and every later profile edit re-embeds the same JPEG. Note this is the
  *other* end of §3: the path must survive a re-login (fixed) and must **not**
  survive a successful upload (not fixed). Both belong to whoever owns
  `user_uploader.dart`.
* **`JsonUtils.getString` coerces where Kotlin refuses.** Kotlin returns `""`
  unless the primitive `isString` (`JsonUtils.kt:57-61`); the port stringifies
  anything, so a document with a numeric `age: 42` yields `""` in Kotlin and
  `"42"` in the port. `json_utils.dart` is not this lane's file.
* **`serialize`'s remaining field-level differences**, beyond the `creatorId`
  source already noted above: `JsonUtils.addFloat`/`addInteger`/`addLong` omit
  **zeros**, so Kotlin drops `temperature`/`pulse`/`height`/`weight`/`date`
  when they are 0 while the port always sends them; and Kotlin sends
  `"data": null` where the port omits the key. Left as a deliberate
  improvement (a real reading of 0 is worth sending) rather than ported, but
  now written down.
* **`cacheDocuments` skips rows with `isUpdated == true`
  (`health_repository.dart:754`) — an undocumented improvement.** Kotlin's
  `bulkInsertFromSync` (`HealthRepositoryImpl.kt:81-92`) upserts
  unconditionally with `isUpdated = false`, so a server copy overwrites a
  pending local edit and clears its dirty flag. The port is right and Kotlin
  loses data; it belongs in *Faithful quirks* (inverted) in
  `docs/kotlin-to-flutter-migration.md`, which this round is keeping off.
  **For the integrator to fold in**, alongside Phase 105's own deferred entry.

## For the integrator — updated

* **Still no schema change**, and still no number requested — but read *The gap
  this phase's own fix leaves open* first: there is one repair statement that
  would need a number if the integrator judges any dev device to be carrying
  pre-Phase-105 health rows. My recommendation is that it does not.
* **No new `app_en.arb` keys.**
* Files touched, all within this lane: `lib/data/local/user_mapper.dart`,
  `lib/repository/user_repository.dart`,
  `lib/repository/health_repository.dart`, `lib/data/local/app_database.dart`
  (two query bodies only), and three test files.
* The follow-up worth scheduling next is the become-member round: `learner`,
  the profile fields on the creation document, `cleanupDuplicateUsers` with
  its caller, and the dead auto-login handoff are one file's worth of work and
  the first of them means a member the port registers cannot currently use it.
