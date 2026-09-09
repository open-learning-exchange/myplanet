# Phase 148 — the outbox's permanent-refusal policy (Lane C)

Two jobs, both reported by three consecutive rounds and left because they are
policy calls rather than repairs:

1. A health examination whose upload answers `id` without `rev` is abandoned on
   its first attempt and then re-POSTs for ever
   (`PHASE_142_NOTES.md` *Reported, not fixed* item 6, and the `1004e90`
   amendment it points at).
2. A permanent refusal accretes one dead outbox row and one doomed POST per
   sweep, unbounded, across twenty-odd uploaders
   (`PHASE_138_NOTES.md` → "The abandoned-row accretion Phase 134 reported now
   has a twentieth instance").

**They are one defect, and the honest answer was to unify them.** Job 1 is not
a health bug; it is the *only* class of refusal where re-sending can create a
second document, reached through a branch fourteen uploaders share. Job 2 is
the same loop reached through a status code. Both are fixed by one rule at the
repository layer, with **no uploader edited**.

---

## The policy, stated plainly

> **An `outbox` item owns exactly one row, for ever. A terminal row is a memo:
> the request it holds has been answered, and nothing re-asks the identical
> question unattended. What re-arms it is a *changed request* — a different
> payload, endpoint or verb — or a person explicitly asking for a retry.**

Concretely:

| What happened | Class | Row | Re-armed by an identical sweep? |
|---|---|---|---|
| 5xx, 0, transport failure, handler threw | `transient` | retried under the backoff; abandoned after `maxAttempts` | **yes** — nothing about it was a verdict on the request |
| 401, 403, 408, 429 | `transient` | as above | **yes** — about the caller or the moment, not the bytes |
| any other 4xx (400, 404, 409, 412, …) | `rejected` | abandoned on the first attempt | **no** |
| a handler's own verdict on a **2xx** (`NetworkError(null, …)`) | `indeterminate` | abandoned on the first attempt | **no** |
| a payload that will not parse | `rejected` | abandoned; never even sent | no (and no POST either way) |

And the invariant that makes it bounded: **one row per `(uploadType, itemId)`**.
`enqueue` reuses the item's row whatever its status instead of minting a new
one, and collapses any surplus an older build left behind — which matters,
because `outbox` is a preserved table and those rows survive every schema bump.

### Why `indeterminate` is not retried, against the auditor's own suggestion

The ground-truth `parity-auditor` pass argued a handler's "unusable response"
verdict "should be retryable at most". I disagree, from that same report's
paragraph (a): eleven uploaders are **appends** with server-assigned ids, so a
duplicate created by a retry is undetectable afterwards — nothing on the device
knows the id the server minted. A `NetworkError(null, …)` follows a **2xx**:
the transport says the write landed. Retrying it is the one move that can file
a second copy of a document that is already there. Where a retry would be safe
(a deterministic `_id`, so a re-POST merely 409s) the *right* answer is not a
retry but the 409-recovery arm — see *Reported, not fixed* item 1. So: not
re-sent, and the diagnostic kept.

### Why not `cleanup()`

Phase 138 offered two options: "bound `cleanup()` and give it a caller, or
clear an item's abandoned rows on re-enqueue and lose the diagnostic". Both
lose something. Reusing the row is a third: the table is bounded by the number
of distinct refused items rather than by time, and the diagnostic
`OutboxDao.abandoned` reads is exactly the row that is being reused, so nothing
is lost. `cleanup()` still has no caller, deliberately — deleting an abandoned
row is deleting the only record the port keeps of a write the server refused,
which is what Phase 107 added `abandoned()` to stop.

---

## What each job actually was

### Job 1 — and a correction to the claim it was filed under

Phase 142's amendment said `id` without `rev` "is what CouchDB returns for
`?batch=ok`". **Neither this port nor the Kotlin app sends `batch=ok`
anywhere** — `grep -rn "batch=ok" flutter/lib app/src/main/java` is empty. And
`PlanetApi._request` checks the status *before* converting the body
(`planet_api.dart:249-253`), so a CouchDB error body can never reach a
handler's success branch, and a 2xx that will not decode becomes a
`NetworkException` (retryable) rather than this. The only route in is a 2xx
that decodes to a JSON object without a usable `rev` — which for CouchDB means
`batch=ok`, which is not sent. So **the health branch is defensive, and the
route the notes named does not exist**; the comment at the code now says so
instead of repeating it.

Two things make the fix worth having anyway.

**The class is reachable elsewhere, though not for the reason first written
here.** `chat_uploader.dart:69-87` branches on an application-level
`data['status'] != 'Success'` and on `data['couchdb']['id']` — the shape
Planet's chat service answers with. An earlier revision of this section said
the uploader posts to that service. It does not:
`ChatUploader.endpointFor` is `credentialFreeDbUrl(config)/chat`, a **CouchDB**
database path, while Kotlin's chat POST goes to `UrlUtils.hostUrl` —
`<scheme>://<host>:5000/` or `<scheme>://<host>/ml/`
(`UrlUtils.kt:102-106`, `ChatApiService.kt:54-57`) — a different service
altogether. CouchDB answers `{ok, id, rev}`, which has no `status`, so *every*
outbox chat upload takes that null-code branch. The branch is therefore
reachable in production; what it reveals is a pre-existing endpoint defect,
recorded under *Reported, not fixed*. Under this policy such a chat is POSTed
once instead of on every subsequent chat write — better, and still broken.

**The loop Job 1 describes is reachable without any unusual response at all.**
An earlier revision of this section had the mechanism wrong, in a way worth
recording because it is the kind of claim the next round would build on.
Examinations do **not** share a document: `createExamination` defaults `userId`
to the row's own generated id (`health_repository.dart:96-99`) and
`health_provider.dart:468-471` passes none, so each examination is its own
CouchDB document and cannot conflict with a previous one. What *is* keyed on
the patient is the **profile** row — `saveHealthProfileBlob` writes
`id = patientId, userId = user.couchId` (`:273-315`) — and it is re-saved on
every examination save. So a 409 on the first attempt is reachable there, and
on a legacy row whose `userId` was set to the patient. Either way
`cacheDocuments` skips a locally dirty row (`health_repository.dart:772`), so
the health sync-in is structurally unable to refresh that `rev` and the loop
cannot self-heal. That is why the fix had to cover plain 4xx rejections and not
only the null-code branch.

**What the port cannot copy from Kotlin here.** Kotlin gates on `has("id")`,
tolerates a null rev, and clears `isUpdated` while leaving `_rev` alone
(`HealthRepositoryImpl.kt:103-131`, `HealthExaminationDao.kt:36-46` — the
partition `1004e90` added). The port's `HealthExaminationDao.markUploaded`
writes `rev: Value(rev)`, so passing the null we have would **erase** the
revision — the pre-`1004e90` bug exactly. `app_database.dart` is Lane A's file
this round, so the port keeps the row dirty and lets the memo stop the
re-POST. Reported below.

### Job 2 — and a correction to "one POST per sync"

Phase 138 said the accretion fires "once per *sync*". It is right for four
upload types and wrong as a general statement. Of twenty-six types:

* **per sync** (`dashboard_sync_provider.syncAll` and `background_entrypoint`):
  `submissions`, `news` (voices), `adoptedSurvey`, the four `activities` types
  via `recordSyncActivity`, and `searchActivity` when the sync moved anything.
  The four `activities` types are *also* swept from every resource open,
  download and course visit (`activities_provider.dart:84`, `:107`,
  `:131-134`), so their real cadence is closer to `teamLog`'s.
* **per user write**: `health`, `personal`, `feedback`, `rating`,
  `achievement`, `chat`, `meetup`, `teamTask`, `courseProgress`,
  `submitPhoto`, `user`.
* **per screen mount**: `teamLog` — every mount of the team detail screen,
  a far higher rate than a sync.
* **bounded already** (one-shot enqueue, never swept): the five `teams*` types
  and `public_survey`. The *row* is bounded there; the local `isUpdated` is
  still left permanently set, which
  `teams_uploader.dart:15-19` documents as freezing the row at its local
  version.

So **twenty** of twenty-six re-offered for ever — the three lists above sum
to 20, and 26 minus the 6 bounded ones agrees. (An earlier revision of this
file said twenty-two, from neither count. A number that is not the sum of the
rows above it is a number nobody added up.) The cadence differs; the
unboundedness did not.

---

## Kotlin, and how much of it is safe to cite

Phase 144's three findings were re-verified and all three hold, so none of that
retry path was ported. Two further readings mattered more:

**Kotlin's queue never abandons an *item*, only an operation.**
`RetryQueue.queueFailedOperation` returns early on `!error.retryable`
(`RetryQueue.kt:36-39`) — "non-retryable" means *do not queue*, not *do not
retry*. `UploadCoordinator` passes only succeeded items to `persistUploaded`,
so a refused item's local flag is untouched and the next sweep re-offers it.
The port's outbox made that verdict terminal, and that single difference is the
origin of every symptom in Job 2. The memo is the port's structural equivalent
of Kotlin's early return: applied at re-enqueue instead of at admission,
because the port has a row where Kotlin has none.

**The port's own citation of `UploadCoordinator` on 409 was a misreading, and a
test name pinned it.** `outbox_drainer.dart` said *"`UploadCoordinator` treats
`code >= 500` as retryable and everything else — 409 conflict, 400, 401 — as
permanent."* `UploadCoordinator.kt:169-204` intercepts a 409 **before** that
rule, GETs the document, adopts its `_rev` and reports **success**;
`retryable = false` appears only when the recovery GET itself fails. The test
`'a 409 conflict is permanent, matching UploadCoordinator'` has been renamed
and now says what it actually pins. `adopted_surveys_uploader.dart:219-242` is
the one place the port has that recovery arm.

**Kotlin's 401 rule and the port's are the same rule reached differently.**
Kotlin's `retryable = response.code() >= 500` (`UploadCoordinator.kt:211`)
makes a 401 non-retryable — and then Kotlin's sweep re-reads the live table and
re-posts anyway. The port's sweep re-*enqueues*, so reaching the same outcome
needs 401 classified transient here. `outbox_drainer.dart`'s `onlyTypes`
workaround existed because a 401 abandoned deliverable writes; it stays, but as
belt-and-braces rather than the only defence. This is the shape Phase 103
recorded: a workaround on one path is evidence the rule is wrong, not that the
path is special.

---

## The changes

**`flutter/lib/repository/outbox_repository.dart`**
* New `OutboxRefusal { transient, rejected, indeterminate }` — three reasons,
  two behaviours, so the policy can be *stated* rather than inferred from an
  int comparison.
* `OutboxRepository.classifyStatus(int?)`, the single reading of a status code,
  used by the drainer when it fails and by `enqueue` when it decides whether to
  re-ask. `retryableStatuses = {401, 403, 404, 408, 429}` — 404 belongs there
  because a 404 on a CouchDB *write* is the database being absent, never the
  document, and like a bad credential it is repaired on the server without the
  request changing. What stays terminal (400, 409, 413, 415, 422) is about the
  bytes, and each of those has a local repair that *does* change them. That is
  what keeps the memo from stranding a write whose cause was fixed elsewhere.
* `noUsableResponse = -1`, recorded in `outbox.httpCode` for a terminal refusal
  that carries no status of its own. Without it a handler's verdict on a 2xx
  and a transport failure both stored `NULL` and the two need opposite
  treatment. No schema change: the column is already `integer().nullable()`.
* `enqueue` looks up the item's row whatever its status, via the existing
  `OutboxDao.forItem`, instead of `findOpen`; `_soleRowFor` collapses surplus
  rows from older builds.
* `markFailed` takes `refusal:` in place of `permanent:`.
* New `rearm(uploadType, itemId)` — the deliberate override of the memo.

**`flutter/lib/repository/outbox_drainer.dart`**
* `permanent = (code ?? 0) < 500` replaced by the classifier, with `code == null`
  read as `indeterminate`. That reading is load-bearing and it is a *reading*:
  `PlanetApi` builds a `NetworkError` only from a response it received and
  substitutes `0` for a missing status, so every transport-authored error
  carries an int and a null can only have come from a handler.

**`flutter/lib/repository/health_uploader.dart`**
* The `no rev` comment corrected (no `batch=ok`; what Kotlin does; why
  `markUploaded(id, null)` is not the answer here).
* `queuePending(retryRefused:)` for the banner's Retry button.

**`flutter/lib/repository/personals_uploader.dart`** — a deterministic
`uploadedAt`, added by the second audit pass. This is the one other
`*_uploader.dart` touched, and it is touched because the policy requires it:
without it the memo is inert for that type (see above).

**Outside the lane's own set, and named because the brief asks:**
`flutter/lib/providers/health_provider.dart` (thread `retryRefused`; correct a
dartdoc that described the accretion as intended behaviour),
`flutter/lib/ui/health/my_health_screen.dart` (pass `retryRefused: true`;
correct the comment that said `enqueue` writes a fresh pending row), and
`flutter/lib/repository/personals_repository.dart` (one dartdoc, which claimed
`serialize` "remains deterministic" — it did not). The first two were required:
without them the memo silently turns Retry into a no-op, and that button sits
next to a count of stranded records. None of the three is any other lane's this
round. **`voices_uploader.dart` was not opened for edit**, and no uploader
other than `health` and `personals` was touched at all.

## Tests

Gate green: `dart format` clean, `flutter analyze` clean, **2658 tests pass**
(2631 before).

Every claim was mutation-tested: the fix reverted, the suite run, the named
test confirmed failing, the fix restored. Where a mutation survived, the
fixture was strengthened rather than the clause deleted.

**`test/repository/outbox_repository_test.dart`** (27, was 19)
* `a rejected item is not re-armed by the identical request` — the rewrite of
  `an abandoned item does not block a fresh enqueue`, whose assertion
  (`expect(second, isNot(first))`) *was* the accretion, written down as
  intended behaviour.
* `a changed request re-arms the same row`, `a reconfigured endpoint re-arms a
  rejected row` — the two recovery routes.
* `an exhausted transient failure is re-armed unchanged` — the half that must
  not regress: bounding rows must not bound retries.
* `a rejection without a status is still terminal`, `a transport failure keeps
  a null status and stays retryable` — the pair `noUsableResponse` exists to
  separate. Reverting the sentinel makes the first fail.
* `surplus rows an older build left are collapsed to one` — the retroactive
  repair, seeded with four hand-written abandoned rows.
* `classifyStatus splits the caller from the request`.

**`test/repository/outbox_drainer_test.dart`** (21, was 14)
* `a rejected request is asked once, however many sweeps run` — **five** sweeps,
  because one repeat is not a demonstration of unboundedness. Asserts one POST
  and one row.
* `an unusable 2xx response is not asked again either` — Job 1's class, five
  sweeps, one send.
* `a transient failure keeps being offered across sweeps` — three sweeps, three
  sends, one row.
* `a 401 is retried`, `403, 408 and 429 are retried for the same reason`.
* `a handler's verdict on a 2xx body is not a server refusal` — pins the null
  code → `indeterminate` → `noUsableResponse` chain.
* `a rejected row is re-armed by an edited payload`.
* `a 409 conflict is permanent, matching UploadCoordinator` renamed to
  `a 409 rejects the request the payload made`, with the misreading it carried
  written out at the test.

**`test/repository/personals_uploader_test.dart`** (12, was 10) — added by the
second audit pass, and both fail on the pre-fix code
* `the payload a sweep builds is stable across sweeps` — the invariant the
  whole policy rests on, pinned for the uploader that broke it.
* `a refused note is POSTed once, however many sweeps run` — the consequence,
  end to end.

**`test/ui/health/my_health_screen_test.dart`** (22, unchanged count)
* `the caution offers a retry that re-queues the record` — rewritten so it
  *can* fail: the abandoned row now carries the request production builds.

**`test/repository/health_legacy_conflict_test.dart`** (9, was 7)
* `a record refused ten times leaves one row and one POST` — replaces
  `a record refused twice is one stranded record, not two`, which asserted
  `abandoned.length == 2`. Ten sweeps against a fake CouchDB that enforces
  `_id` uniqueness, counting real POSTs.
* `an accepted response with no revision is never re-sent` — Job 1 end to end:
  the fake answers `{id}` with no `rev`, the document *is* on the server, five
  sweeps produce one POST, the row carries `noUsableResponse`, and the local
  `rev` is untouched.
* `the clinician tapping Retry does override the memo` — a sweep does not
  re-ask, `retryRefused: true` does.

Thirteen mutations in total, each reverted one at a time with the suite run in
between. The first nine, before the second audit pass:
`enqueue` back to `findOpen` (11 failures), the drainer back to
`(code ?? 0) < 500` (2), the `noUsableResponse` sentinel dropped (4),
`sameRequest` ignoring the endpoint (1), `rearm` made a no-op (1), the
surplus-row collapse removed (1), 404 out of `retryableStatuses` (1), the memo
widened to every terminal row (2), and `markFailed` ignoring the terminal
refusal (15). Every one failed the test it should.

**One thing deliberately has no behavioural pin, and it is worth saying so.**
Reverting the drainer's `code == null → indeterminate` to
`code == null → rejected` fails *nothing*, because the two classes share a
behaviour: both are terminal and both store `noUsableResponse`. The enum has
three values because there are three *reasons* and the distinction changes what
a future reader should do — `rejected` is repaired by editing the request,
`indeterminate` must never be re-sent because the write may already have
landed. Only `transient` is separated by behaviour, and that separation is
pinned by six tests. A reader who deletes `indeterminate` and folds it into
`rejected` will not break the suite; they will break the reasoning, and the
notes and the dartdoc are what stand in the way.

The four added by the second audit pass: the personals `uploadedAt`
(2 failures), the claimed-row guard in `_soleRowFor` (1, after the fixture was
corrected — see above), the second sentinel (1), the `_soleRowFor` open-row
preference (1), the `rearm` guard (2), and `retryRefused: true` removed from
`my_health_screen.dart` (1).

One mutation is worth recording because it survived at first. Reverting
`_soleRowFor` to `findOpen` left `an unusable 2xx response is not asked again
either` **passing**, because the first cut of that test counted outbox rows
rather than sends and the drainer's `due()` never re-offers an abandoned row —
the accretion is one row *and one POST per sweep*, and only the POST count
distinguishes the two policies. The test now counts sends. Same reason the
health tests count `couch.postCount`.

---

## What the second audit pass changed

The mandatory pass over this lane's own finished, already-green code found
seven defects and seven wrong claims in this file. Four of the defects were
fixed here; the pattern held for a fifth consecutive round — **an audit of the
ground truth does not audit the implementation.**

**The worst of them defeated the whole policy for one uploader, and it was the
worst possible uploader to lose.** `PersonalsUploader.queuePending` built its
payload with `PersonalsRepository.serialize(row)`, whose `uploadDate` defaults
to `DateTime.now()`. The memo compares the request being enqueued against the
one already recorded, so a payload that is not a pure function of the local row
never matches: for `personals` the memo was inert and a refused note was POSTed
on every sweep exactly as before. And `personals` is an **append** with a
server-minted id — the class where the duplicate this policy exists to prevent
cannot be detected afterwards, and where the handler's own comment says so.
Fixed by passing the note's creation date, which is the honest value: Kotlin's
`Date().time` is the moment of the POST, which a durable queue cannot know at
enqueue time. **A payload that is not a pure function of its row silently
disables the memo**, so `personals_uploader_test.dart` now pins the property
directly, and it is the first thing to check when adding an uploader.

Also fixed: `_soleRowFor`'s surplus-row `deleteById` could drop a row a drain
had claimed (it is the one delete in `OutboxDao` that is not status-scoped, and
the others are scoped for exactly that reason); `markFailed` stored one
sentinel for both terminal reasons, so `rejected` read back as `indeterminate`
and the 409-recovery follow-up below would have found the distinction already
collapsed — there are two sentinels now; and the drainer's "a null code means
the send succeeded" comment is a universal that three handlers falsify by
returning one *before making any request*.

**And the widget test that looked like it covered the Retry override could not
fail.** `my_health_screen_test.dart`'s helper seeded the abandoned row with
`payload: '{}'` while the production sweep serializes a real document, so
`sameRequest` was false either way and the test passed with or without
`retryRefused: true`. It now seeds
`jsonEncode(HealthRepository.serialize(row))`, and removing the flag fails it.
The same trap in miniature appeared in this phase's *own* new test for the
claimed-surplus-row guard: it seeded the claimed row as the *older* of the two,
and `_soleRowFor` keeps the first open row in `createdAt` order, so the guard
was never reached and the mutation survived. **The fixture was the reason, not
the clause** — the ages are swapped now and it fails as it should.

Three of the audit's findings were argued and **not** taken; they are stated as
trades under *Reported, not fixed* rather than silently accepted.

---

## Reported, not fixed

Each names the file, the change, and why it was not made here.

1. **Take a 409 out of the failure path entirely — the port has the arm, for
   one uploader.** `UploadCoordinator.kt:169-204` answers a 409 by GETting the
   document, adopting its `_rev` and reporting **success**;
   `adopted_surveys_uploader.dart:219-242` is a working port of exactly that,
   and its own comment describes the accretion this phase fixed. Six uploaders
   send a deterministic `_id` and could adopt it directly — `health` (`_id` is
   the patient's user id), `teams`, `achievements`, `user` (which already
   read-then-writes its `_rev`), `feedback`, and `adopted_surveys` (done);
   five more (`course_progress`, `ratings`, `submissions`, `events`, `voices`)
   could once `couchId` is set. This phase makes a 409 *safe* — one row, one
   POST, recoverable by a pull that changes the payload — but recovery is
   strictly better than safety, and it is per-uploader work with a new GET on
   each. Severity: medium. It is the single highest-value follow-up here.
2. **`HealthExaminationDao.markUploaded(id, null)` erases a stored `_rev`.**
   `app_database.dart:3812-3819` writes `rev: Value(rev)`. Kotlin repaired the
   identical defect in `1004e90` by partitioning null-rev ids into
   `SET isUpdated = 0 WHERE _id IN (:ids)`, with a test
   (`markUploaded_rowsWithoutRev_clearsIsUpdatedAndPreservesExistingRev`). The
   port cannot align its health handler with Kotlin's `has("id")` gate until
   that DAO is fixed; the correct pattern is one table over at
   `app_database.dart:1035` (`rev == null ? const Value.absent() : Value(rev)`).
   **Lane A's file this round.** Phase 142 item 7 reported the dead-code
   sibling `HealthRepository.markUploadedBatch`, which has the same bug and
   still has zero callers. (The `rev == null ? const Value.absent() :
   Value(rev)` pattern is at `app_database.dart:1198` and `:2066`; an earlier
   revision of this file cited `:1035`, which is inside `UserDao.search`.)
3. **`HealthRepository.cacheDocuments` skips a locally dirty row**
   (`health_repository.dart:772`, `if (current?.isUpdated == true) continue;`),
   and nothing else writes `health_examinations.rev`. So the one self-healing
   route this phase relies on — a pull supplies the revision, the payload
   changes, the memo re-arms — is **structurally unavailable for health**,
   which is why the banner's Retry had to be wired instead. Every other
   uploader whose sync-in writes `rev` unconditionally does get that route.
   Fixing it means merging the server's `_rev` into a dirty row without
   touching its edited fields; that is a repository change with a real
   conflict-resolution question inside it, not a one-liner. Severity: medium.
4. **`chat_uploader.dart` conflates two different failures under one null
   code.** `:69-87` returns `NetworkError(null, …)` both when the API answers
   200 with `{"status":"error"}` (a **rejection**: the message was not stored)
   and when a successful answer carries no `couchdb.id` (**indeterminate**).
   Under this policy both are terminal and not re-sent, so behaviour is
   identical today and nothing is broken — but chat is the one uploader where
   this branch is reachable in production, and if that API's error is ever
   *transient* (a rate limit answered as 200) the message is now stranded where
   it used to be retried on every chat write. The fix is for that handler to
   return a status code it believes, not for the drainer to guess. I did not
   change it because I have no way to verify what the chat API's `status`
   values mean. Severity: low-medium.
5. **`pendingUploadCountProvider` (`app_providers.dart:686-688`) has zero
   readers**, in `lib` *and* `test`, and its dartdoc says "for the UI to
   surface". The only user-facing signal the outbox has is the health banner,
   for one of twenty-six upload types. Kotlin has a settings-screen queue
   snapshot and a clear (`SettingsViewModel.kt:52,60`). A general "writes
   waiting / writes refused" surface is worth a slice; the memo makes it more
   useful, since an abandoned row now means something durable rather than
   "the last of N attempts".
6. **`OutboxRepository.cancel` leaves a terminal row behind.** It is
   `deletePending`, deliberately status-scoped so a cancel cannot race a drain
   — but a subject that no longer exists should not keep a memo, and on a type
   whose ids are reused a stale memo would suppress a genuinely new write.
   Nothing in the port reuses ids today, which is why it was left. One line if
   someone decides the diagnostic for a deleted subject is worthless.
7. **`teams_uploader.dart:9` has `import 'dart:developer';` below the relative
   imports**, against the project's ordering. `flutter analyze` does not flag
   it. Cosmetic; not touched because the file needed no change for this policy
   and "one file, one owner" is cheap to honour.
8. **The five `teams*` types and `public_survey` are bounded by having no
   sweep, not by anything this phase did**, and their *local* state is still
   left wrong on a refusal — `teams_uploader.dart:15-19` documents a
   permanently-set `isUpdated` freezing the row at its local version and
   exempting it from stale-row cleanup for good. The memo does not help there
   because nothing re-offers the row in the first place. Worth its own look.
9. **`ChatUploader` posts to the wrong service, and the port's live chat path
   does too.** `endpointFor` is `credentialFreeDbUrl(config)/chat` — CouchDB —
   while the handler branches on `data['status'] == 'Success'` and
   `data['couchdb']['id']`, the shape Planet's chat service returns. Kotlin
   sends chat to `UrlUtils.hostUrl`, `<scheme>://<host>:5000/` or
   `<scheme>://<host>/ml/` (`UrlUtils.kt:102-106`,
   `ChatApiService.kt:54-57`), a different host and port. CouchDB answers
   `{ok, id, rev}` with no `status`, so every outbox chat upload takes the
   "not Success" branch. The same wrong base is on the live path at
   `chat_repository.dart:354`. **Severity: high** — this is chat write-back not
   working, not a policy question — but it is a chat slice, not an outbox one,
   and fixing it needs the Planet chat API's real contract rather than a guess.
   Found by the second audit pass while checking a claim this file made about
   chat, which is the second time this round that checking my own citation
   found something bigger than the citation.
10. **Three handlers return a null code before sending anything**, so the
   drainer reads them as `indeterminate` ("the write may already have landed")
   when nothing was sent: `user_uploader.dart:126` and `:143-147`,
   `achievements_uploader.dart:63`. The effect is the same today — terminal
   either way — and the first is genuinely terminal, since the local row is
   gone. But `'User document carries no _rev; cannot update'` is about the
   *server's* current state and a later attempt could succeed, and `user` has
   no payload-changing recovery route (`UserMapper.toDoc` emits no `_rev`;
   the handler fetches it at send time), so a profile edit can now sit
   abandoned where it used to be re-attempted. The clean fix is for those
   handlers to say what they mean with `OutboxRepository.notSent` or a
   retryable code rather than let the drainer guess — three one-line uploader
   edits, which is per-uploader special-casing this lane deliberately avoided.
   Reachability is low (a `_users` GET always carries `_rev`).
11. **403-as-transient and 404-as-transient are trades, not certainties.**
   CouchDB has a second 403 — `validate_doc_update` answering
   `{forbidden: …}` — which is a permanent property of the document's bytes;
   and `public_survey`'s endpoint is a Planet REST route
   (`surveys_repository.dart:633-638`), where a 404 means the team or survey
   does not exist. Both are read as transient here. The reasoning is at the
   constant and worth repeating: reading them as terminal strands a write with
   nothing able to re-arm it, because neither repair changes the request, while
   reading them as transient costs wasted requests and leaves the row bounded
   at one. Wasted requests are the cheaper mistake. If Planet is ever confirmed
   to ship `validate_doc_update` on a database the port writes to, revisit —
   and note that would want a *recovery* arm, not a reclassification.
12. **A pre-Phase-148 abandoned row with a null `httpCode` gets one more
   attempt on upgrade.** The old drainer stored `NULL` for a handler's verdict
   on a 2xx, which is indistinguishable from a transport failure, so
   `classifyStatus` reads it as transient and re-arms it once. It self-heals
   immediately — the next failure writes the sentinel — and `outbox` is
   preserved, so these rows do arrive on upgraded installs. Pinned by a test
   rather than fixed, because the alternative is treating every legacy
   null-code row as terminal, which would strand transport failures.
13. **Phase 142's items 1, 3, 4, 5, 7–10 and Phase 138's other items are still
   open**; none is in this lane's set.
