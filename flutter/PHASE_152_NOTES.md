# Phase 152 — a 409 recovered, not merely survived (Lane C)

Phase 148 made a 409 **safe**: one row, one POST, terminal, re-armed only by a
changed request. Its own *Reported, not fixed* item 1 called the recovery arm
"the single highest-value follow-up here". This is that phase.

**The headline is not the arm; it is that porting Kotlin's arm literally would
have lost data in every uploader but one.**

---

## What Kotlin does, read rather than cited

`UploadCoordinator.kt:169-204` answers a 409 by GETting the document that
already exists, adopting its `_rev`, and reporting the upload as a **success**.
Three details the brief's summary rounds off, all confirmed against source by
the ground-truth `parity-auditor` pass:

* **"Runs the same `afterUpload` callback" is structurally true and
  operationally hollow.** `afterUpload` is `null` for every config in the tree
  (`UploadConfig.kt:31-32` and the two call sites are the only occurrences).
  What actually happens is that the recovered item joins `succeeded`, so
  `updateDatabaseBatch` (`:241-257`) runs the config's `persistUploaded` —
  the `markUploaded` a port has to reproduce.
* **The field names differ between the two paths, correctly.** The accepted
  path reads `id`/`rev` (a CouchDB *write* response); the recovery path reads
  the literals `_id`/`_rev` (`:177-182`) because a document *read* returns the
  document. `config.responseHandler` is ignored on the recovery path; nothing
  turns on it today, since the only `Custom` handler (`Meetups`,
  `UploadConfigs.kt:178`) is declared identically to `Standard`.
* **The arm is not broken for appends — it is unreachable for them.** The GET
  URL is `$baseUrl/${config.endpoint}/${preparedItem.dbId ?: preparedItem.localId}`,
  and `localId` is always `{ it.id }`, the Room primary key. Every POST-only
  config that can 409 (`TeamTask`, `Meetups`, `AdoptedSurveys`, `Feedback`) is
  one where `item.id` *is* the document `_id` by construction; every config
  where they would differ emits no `_id`, so CouchDB mints one and a conflict
  cannot occur. There is no broken half to copy.

Also confirmed, and deliberately **not** ported, per the brief:
`RetryQueueWorker.kt:234-238` treats a 409 as "already synced", deletes the
queue row and logs success, leaving the subject row untouched — a user's edit
discarded with a success log. It is reachable in practice, because
`UploadCoordinator` marks the recovery GET's `IOException` case
`retryable = true, httpCode = 409` (`:197`), so a 409 whose recovery hit a
network blip lands in that queue and is then thrown away. `:230-233` likewise
never reads `response.body()`, so a retried news create leaves `_id` null and
the next sync POSTs a second document.

Six further 409 sites exist and **none** should be followed:
`UploadManager.kt:369-372` and `TeamsUploader.kt:73-75` drop a `_bulk_docs`
per-doc conflict silently; `HealthRepositoryImpl.kt:110-124` swallows it
(a 409 body is null, so `has("id")` is false and nothing is logged);
`AchievementUploader.kt:32-42` is an `if (response.isSuccessful)` with no
`else`.

**So say it plainly, because the next round will read this file and not the
Kotlin:** of the port's twenty uploaders, only six correspond to a Kotlin path
that runs through `UploadCoordinator` at all. For the rest this arm is **new
behaviour, not restored parity**, and the *update* arm below has no Kotlin
precedent anywhere.

---

## The correction that reframed the phase

Kotlin's arm never compares the document it fetched with the one it was
sending. **A 409 does not mean "your write is already there."** It means a
document with this `_id` exists and the `_rev` supplied — or omitted — is not
its current revision, so *these bytes were not stored*.

Adopt-and-report-success is therefore only equivalent to delivery when the
document already on the server **is** the document being sent. Ported
wholesale it would have:

| Uploader | What adopting loses |
|---|---|
| `health` | the clinician's examination or profile edit, with `isUpdated` cleared — and `cacheDocuments` skips a dirty row only *while* it is dirty, so the local copy is then overwritten too. **Both copies gone.** |
| `submissions` | answers a learner added to an already-uploaded sheet (`uploaded: true, isUpdated: false`) |
| `voices` | the edited post *and* the images: `markUploaded` clears `imageUrls` and deletes the local bytes (`voices_repository.dart:1053, 1062`) |
| `achievements` | a new ledger entry, plus the resume attachment is then PUT against somebody else's ledger |
| `user` | a name or photo edit, on a handler that already holds a rev fetched seconds earlier |
| `teams` | a report edit — or, on a tombstone, the delete is reported done while the document is still there *and* the row is handed back to `deleteNotIn` |

The payload answers the question well enough to act on, and the two answers
need **opposite** handling. That split is the phase.

### The rule, stated plainly

> **An update is re-sent under the revision the GET reports. A create is
> neither re-sent nor adopted — the refusal stands — unless the uploader can
> prove the content identical.**

* **Update** — the payload carries a `_rev`, so this device has published this
  document before and is sending a change to it. Fetch the current revision,
  re-send the same content under it. The write lands.
* **Create** — no `_rev`, so as far as this device knows the document has never
  been published and the one on the server is content it has never seen.
  Re-sending would overwrite that; adopting would report a delivery that did
  not happen. Neither is safe in general, so the default is **neither**: the
  refusal stands, terminal, and `OutboxDao.abandoned` keeps reporting the row
  as stranded — the honest answer.
* **`adoptExisting`** is the opt-in to Kotlin's original semantics for a
  create, and exactly **one** uploader sets it. `adopted_surveys`'s clone is a
  pure function of the survey and the team, and its id (`'${surveyId}_$teamId'`)
  makes two leaders author the same document, so taking the winner's revision
  loses nothing. Nothing else in the port can make that claim.

Note what the create default preserves: `health_legacy_conflict_test.dart`,
which pins Phase 148's behaviour for a legacy examination colliding with the
patient's profile, **passes unchanged** — with the fake now serving real
document reads, so it pins the create-arm refusal rather than a missing stub.

### Why this does not weaken Phase 148's policy

The policy: *an `outbox` item owns exactly one row, for ever; a terminal row is
a memo, and nothing re-asks the identical question unattended. What re-arms it
is a changed request.*

The re-send **is** a changed request — it carries a `_rev` the first one did
not, fetched from the server between the two. That is the recovery route the
policy already names, taken inline instead of waiting for a pull to supply the
same revision. Four things keep it inside the policy rather than around it:

1. **Bounded.** One GET and at most one re-send per drain attempt. A second 409
   is returned as the refusal it is and `classifyStatus` makes it terminal
   exactly as before.
2. **Never an identical re-ask.** If the revision the server reports is the one
   the payload already carried, the re-send is skipped and the refusal stands.
3. **Cannot duplicate a document.** The arm fires only for a request naming its
   own `_id`, so a re-send is an update of that id. `documentUrlUnder` returns
   null when the payload names no document, which is the structural guard: an
   append with a server-minted id gets no arm at all.
4. **A throwing fetch cannot re-arm anything** — see the next section.

### The one place this phase had to *defend* the policy, not extend it

`OutboxDrainer._send` wraps a handler in a catch-all and treats a throw as
**transient** — correctly, since a throw is not evidence the server refused
anything. But here the server *has* refused something: the 409 is already in
hand. An unguarded recovery whose GET threw would propagate out of the handler
and relabel a terminal rejection as a retryable failure, re-offering the row on
every sweep until its attempts ran out. **That is Phase 148's accretion,
re-entering through the recovery arm.**

`ConflictRecovery.send` therefore catches around the fetch and returns the
original 409. `PlanetApi` returns a `NetworkException` rather than throwing, so
this is not reachable through it — it was found because
`health_legacy_conflict_test.dart`'s fake CouchDB is a `Mock` with only
`postJsonObject` stubbed, so the new GET threw and five Phase 148 tests went
from `abandoned` to `pending`. **A test that broke for the "wrong" reason was
right about the shape.**

### The trade the update arm makes

Re-sending under the server's current revision is last-write-wins: a concurrent
edit on another device is overwritten. That is the port's existing stance
elsewhere — `cacheDocuments` skips a locally dirty row
(`health_repository.dart:772`), so the local copy is already authoritative —
and it is the better of the two available mistakes, because the alternative
loses the *local* edit silently in a queue whose whole purpose is delivering
local writes.

**One case is named rather than left to fall out of the rule.** A team
tombstone is `{_id, _rev, _deleted: true}` (`teams_provider.dart:295`), so it
is an update, and re-sending deletes a document whose latest content this
device has never seen. Accepted deliberately: the user asked for the membership
to be removed, and a revision bump elsewhere does not revoke that instruction.

---

## Where the arm lives, and why not the drainer

Phase 148's lesson — *one rule at the right layer beat twenty special cases* —
was the design question the brief posed. The answer is that the **policy** is
in one place and the **call** cannot be.

`OutboxDrainer._send` sees only a `NetworkResult`. It cannot know the document
URL (it is `row.endpoint` for `user`, `${row.endpoint}/${payload['_id']}` for
most, `${row.endpoint}/achievements/${payload['_id']}` for `achievements`),
cannot know the verb, and — decisively — cannot run the handler's DAO write.
The outbox repository owns rows and policy, not HTTP or domain DAOs.

So `ConflictRecovery` sits beside `OutboxHandler` in `outbox_drainer.dart` and
wraps only the **send**. Every uploader supplies exactly two things — a
document URL and its own send as a closure — and because a recovered result
returns *through* the wrap, each handler's existing
`if (result case NetworkSuccess…)` branch runs on it unchanged. **No uploader
duplicates its own `markUploaded`, and none can forget to run it.** That is the
property that made a per-call-site arm acceptable.

### The trap that would have shipped a green, dead arm

**The document id is the payload's, not the outbox row's.** Phase 148's item 1
reads as though `row.itemId` is the document id for all six of its "deterministic
`_id`" uploaders. It is not. `outbox.itemId` is the *local* row key; the CouchDB
`_id` is whatever the serializer chose, and of the eleven candidates **seven**
differ in the reachable case. `health` is the sharpest: `serialize` writes the
patient's id as `_id` (`health_repository.dart:845-849`) while `itemId` is the
examination row's. A row-keyed GET would 404, the arm would return the original
409, and nothing would be recovered — silently, with every test green.

`ConflictRecovery.documentUrlUnder(sendUrl, payload)` exists so that derivation
is written once, and returns null rather than guessing.

---

## Armed, and not

**Armed (11 types across 10 uploaders):** `health`, `teams` (5 types),
`feedback`, `achievements`, `adopted_surveys` (`adoptExisting: true`),
`submissions`, `voices`, `events`, `team_tasks`, `ratings`, `user`.

Reachability was verified per uploader against its *pending predicate*, not
just its serializer — that check is what caught `course_progress` below.

**`adopted_surveys` also lost its bespoke arm.** It was a working port of
Kotlin's, ~45 lines, with **no test coverage at all** — the sweep tests drive
`queuePending` and the conflict branch was reachable from nothing. It now uses
the shared arm and has four tests.

---

## Reported, not fixed

1. **`course_progress` cannot conflict, and that is a bug wearing a
   reachability costume.** `CourseProgressDao.getPendingUploads` is
   `couchId IS NULL`, so a row the server has acknowledged is never re-offered.
   Two consequences: the arm was reverted (an inert arm claiming a recovery
   that cannot happen is worse than none — pinned by *a progress row that
   reached the server is never re-offered*), and `_toDoc`'s `_id`/`_rev` branch
   is **dead on the upload path**, which means **a later edit to an
   acknowledged progress row never uploads at all**. Changing the predicate is
   a sync-semantics decision, not a one-liner. Severity: medium. The
   ground-truth audit called this one reachable; it read the serializer and not
   the predicate. *A citation is not a reading* — including an auditor's.
2. **`team_log` is armed by nothing and probably should not be.**
   `pendingTeamLogUploads` is `uploaded = false` and `markUploaded` sets it
   true, so only a failed-then-pulled row qualifies. Left alone; the same
   predicate question as item 1 sits under it.
3. **`myplanet_activities_uploader.dart` is not an outbox uploader at all** —
   no `queuePending`, no `handler`; it posts directly (`:100`, `:146`). It is a
   genuine read-modify-write merge posting with a fetched `_rev` (`:128`), a
   409 there is unhandled, and `lastUsageUploaded` is not advanced (`:152`) so
   it self-heals on the next interval. It is the one place in the port where
   Kotlin's "re-GET and merge again" is exactly right, and it has nothing.
   Severity: low, because it self-heals. Not in the outbox, so outside this
   arm's reach by construction.
4. **`HealthExaminationDao.markUploaded(id, null)` still erases a stored
   `_rev`** — `app_database.dart:3962-3968`, **Lane A's file**. Phase 148's
   citation of `:3812-3819` has drifted; so have its citations of the correct
   `rev == null ? const Value.absent() : Value(rev)` pattern, which is live at
   **`:1208`** (`UserDao.markUploaded`) and **`:4765`** (`AchievementDao`), not
   `:1198`/`:2066`. **The health arm did not need it and does not touch it**: a
   recovery never passes a null rev — it either has a `String` from the GET,
   guarded `is String && isNotEmpty`, or it does not recover. Reported as the
   brief asks; Lane A owns the fix.
5. **`HealthRepository.cacheDocuments` skipping a dirty row
   (`health_repository.dart:772`) matters less than it did.** Phase 148 item 3
   called the pull-supplies-the-revision route "structurally unavailable for
   health"; the update arm now supplies the revision directly, so a stale-rev
   health write recovers without it. The underlying merge question is
   unchanged, but it is no longer the only route.
6. **`chat` and `public_survey` are unreachable for this arm and stay that
   way.** `chat`'s `_id`/`_rev` are nested inside `data` (`chat_uploader.dart:105-106`)
   so the top-level document has none, and `public_survey` posts to a Planet
   REST route with no document to GET. Phase 148's items 4 and 9 (the chat
   endpoint pointing at CouchDB rather than Planet's chat service) are
   untouched and still open.
7. **The eight append uploaders are deliberately unarmed** — `personals`,
   `chat`, `submit_photos`, `search_activity`, the four `activities` types,
   `public_survey`. A 409 is impossible for them (server-minted ids), and a
   `documentUrl` there would be a lie a future reader acts on.
8. **`UploadConfig.additionalUpdates` is declared (`UploadConfig.kt:34`),
   assigned once (`UploadConfigs.kt:263-265`) and never invoked** by
   `UploadCoordinator`. Kotlin-side dead code found while reading the ground
   truth; noted for whoever next audits that file.
9. **Phase 148's items 5, 6, 7, 8, 10, 11, 12 remain open**; none is in this
   lane's set.

---

## Tests

Gate green: `dart format` clean, `flutter analyze` clean, **2790 tests pass**
(2735 before).

New: `test/repository/conflict_recovery_test.dart` (16) pins the rule itself —
both arms, the `adoptExisting` opt-in, the three guards, and `documentUrlUnder`.
`test/repository/adopted_surveys_uploader_test.dart` (4) is new because that
uploader's own 409 arm had **no coverage at all** before this phase.

One end-to-end test per armed uploader, added to its existing file. Each drives
the payload production actually builds — seed the row, `queuePending`, decode
the operation's stored payload — rather than a hand-written one, because a
fixture that cannot reach the branch it names reads as coverage. Where that was
not practical the payload is passed directly and the test says so.

`test/repository/health_legacy_conflict_test.dart` gains
*a stale profile revision is recovered instead of stranded* — the phase's
headline through the real drain path, counting POSTs and GETs against a fake
CouchDB that enforces `_id` uniqueness. Its fake also gained a **document read**;
without it the new arm's fetch threw, and the five Phase 148 tests in that file
would have passed because of `ConflictRecovery`'s throw guard rather than
because a create-conflict stands as a refusal. That distinction is the whole
point of those tests, so the fake serves documents now.

### Mutations

Eighteen, each applied alone with the suite run in between, then reverted.
Every one failed the test it should.

| Mutation | Tests killed |
|---|---:|
| health derives the doc URL from `row.itemId` instead of `payload['_id']` | 1 |
| no split — always re-send, ignoring the create case | 9 |
| **no split — always adopt, i.e. Kotlin's arm ported literally** | **22** |
| drop the identical-re-ask guard (`rev == sentRev`) | 1 |
| drop the null-`documentUrl` guard, so an append is recoverable | 1 |
| drop the throwing-fetch guard | 1 |
| `adoptExisting` ignored (always false) | 3 |
| `adoptExisting` forced true everywhere | 5 |
| un-arm each of the ten armed uploaders, one at a time | 1–2 each |

The third row is the one worth keeping: **porting Kotlin's arm as written fails
twenty-two tests in this port.** That is the phase's finding stated as a number.
