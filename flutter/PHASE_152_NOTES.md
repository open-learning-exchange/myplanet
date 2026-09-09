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

Six further 409 sites exist and **none** should be followed — but they do not
all fail the same way, and an earlier revision of this section flattened them:

* `HealthRepositoryImpl.kt:110-124` and `AchievementUploader.kt:32-42` really
  do swallow it silently (a 409 body is null, so `has("id")` is false and
  nothing is logged; and an `if (response.isSuccessful)` with no `else`).
* `TeamsUploader.kt:73-75` drops it too, but at `queueTeamRetry:118-119` — the
  bulk response's `httpCode` is 200, so `retryable` is false and it is never
  queued.
* **News is not swallowed, and saying so was wrong.**
  `UploadManager.kt:369-372` passes a **non-null exception**, and
  `queueNewsRetry`'s rule is `exception != null || httpCode >= 500`, so a
  per-doc conflict *is* queued into `RetryQueue` — and then discarded by
  `RetryQueueWorker.kt:234-238` two paragraphs up. Queued-then-thrown-away.
  Found by the implementation audit opening a citation this file made; the
  verdict on the port is unchanged, the reading of Kotlin was not.

**So say it plainly, because the next round will read this file and not the
Kotlin:** **six of the eleven uploaders armed here** correspond to a Kotlin
path that runs through `UploadCoordinator` — `feedback`, `adopted_surveys`
(`exams`), `submissions`, `events` (`meetups`), `team_tasks` (`tasks`) and
`ratings`. The other five (`health`, `teams`, `achievements`, `user`, `voices`)
have no config in `UploadConfigs.kt` at all, so for them this arm is **new
behaviour, not restored parity**, and the *update* arm below has no Kotlin
precedent anywhere.

*(An earlier revision of this sentence read "of the port's twenty uploaders,
only six" — the armed count wearing the wrong denominator. Across all twenty
uploader files about **eleven** have a coordinator counterpart, the append
uploaders included, so as written it told a reader the activity, search and
photo uploaders have none. Backwards, and caught by the implementation audit.)*

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
   exactly as before — **but only a second 409.** If the re-send answers a 5xx,
   a transport failure or a throw, the drainer classifies *that*, and it is
   `transient`: the item is retried, and because the stored payload still holds
   the stale `_rev`, each of the five attempts repeats POST → GET → POST. Up to
   fifteen requests for one write before abandonment. **Deliberate, and the
   reason the re-send is not wrapped the way the fetch is:** after a failed
   fetch a definitive 409 is still in hand, so letting it escape would relabel
   a refusal; after a failed re-send the last thing the server said was
   "unavailable", not "refused", and a write the server never answered is what
   `transient` exists for. The cost is bounded requests; the alternative is
   stranding a deliverable write on a server hiccup. The implementation audit
   read this as a defect; I disagree, for that reason, and the claim it was
   right about — that my note overstated "terminal exactly as before" — is
   fixed here and at the code.
2. **Never an identical re-ask.** If the revision the server reports is the one
   the payload already carried, the re-send is skipped and the refusal stands.
3. **Cannot duplicate a document.** A re-send is an update of a named `_id`,
   and an append with a server-minted id cannot 409 at all.
   `documentUrlUnder` returns null when the payload names no document, so its
   eight callers are safe by construction — but three build the URL by hand
   and `send` does not verify the `_id`, so this is a **caller convention, not
   a structure**. See *Reported, not fixed* item 3.
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
edit on another device is overwritten.

**The justification I first gave for that was too narrow, and the correction is
worth more than the original claim.** I cited `cacheDocuments` skipping a
locally dirty row (`health_repository.dart:772`) as "the port's existing stance
elsewhere". It is not: that mechanism is health-only. Most sync-ins have no
dirty-row guard at all and end `isUpdated: false`
(`submissions_repository.dart:1725-1774`), making the **server** authoritative
on the way in — the opposite stance.

The real reason last-write-wins was *already* the port's outcome is Phase 148's
own recovery route: **a sync-in that writes `rev` unconditionally re-arms the
memo with a changed request.** A pull hands the row the server's revision, the
next sweep's payload therefore differs from the refused one, the memo re-arms,
and the local content goes over the server's anyway — one sync later. **This arm
changes the latency of that outcome, not the outcome.** Health is the single
uploader where that route is closed, which is exactly why the `cacheDocuments`
citation belongs there and only there, and why health needs the arm most.

That reframing is not a way of dismissing the concern; it relocates it. Where
the server's document holds content the payload cannot reconstruct, "one sync
later" is still a loss, and this arm still makes it sooner. See *Reported, not
fixed* item 1.

**One case is named rather than left to fall out of the rule.** A team
tombstone is `{_id, _rev, _deleted: true}` (`teams_provider.dart:295`), so it
is an update, and re-sending deletes a document whose latest content this
device has never seen. Accepted deliberately: the user asked for the membership
to be removed, and a revision bump elsewhere does not revoke that instruction.
Adopting instead would report the delete as done while the document is still on
the server *and* hand the row back to `deleteNotIn`. Both halves are pinned —
a tombstone recovered under a fresh revision, and a tombstone for a document
another device already deleted, where the read 404s and the refusal stands
exactly as it did before this arm existed. The second also shows the arm does
not disturb the handler's `_deleted` early return, which tolerates a response
carrying no `rev`.

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

It percent-encodes the id with `Uri.encodeComponent`, which is the port's
established convention for a CouchDB document id (`personals_uploader.dart:151`,
`submit_photos_uploader.dart:131`, `teams_uploader.dart:123`,
`teams_repository.dart:707`, `voices_uploader.dart:331`, and the arm this
phase replaced). **One consequence is worth stating because it is new:** for
`health` the id is a user id like `org.couchdb.user:ada`, so the URL carries
`%3A` where `user_uploader.dart:80` deliberately leaves the `_users` prefix's
colon literal and encodes only the name. CouchDB percent-decodes a path
segment, so both resolve to the same document — but that is a *checked
assumption*, not something the suite proves, and it is the kind of thing that
would fail exactly as a green dead arm.

---

## Armed, and not

**Armed — 11 uploader files, 15 upload types:** `health`, `teams` (5 types),
`feedback`, `achievements`, `adopted_surveys` (`adoptExisting: true`),
`submissions`, `voices`, `events`, `team_tasks`, `ratings`, `user`.

**Unarmed — 9 files, 11 types**, in three groups: six append uploaders where a
409 is impossible (`personals`, `chat`, `submit_photos`, `search_activity`,
`activities`'s four types, `public_survey` — **9 types**, not the eight both my
first draft and the ground-truth audit said); `course_progress` and `team_log`,
unreachable by their pending predicates (items 1 and 2 below); and
`myplanet_activities`, which is not an outbox uploader at all (item 3).

The types reconcile: 15 + 9 + 1 + 1 = 26, which is Phase 148's count of outbox
upload types, and 11 + 9 = 20 uploader files. *Counted, not remembered — a
number that is not the sum of the rows above it is a number nobody added up.*

Reachability was verified per uploader against its *pending predicate*, not
just its serializer — that check is what caught `course_progress` below.

**`adopted_surveys` also lost its bespoke arm.** It was a working port of
Kotlin's, ~45 lines, with **no test coverage at all** — the sweep tests drive
`queuePending` and the conflict branch was reachable from nothing. It now uses
the shared arm and has four tests.

---

## What the second audit pass changed

The mandatory pass over this lane's own finished, already-green code found
twelve items. **Five were real defects in the code or its tests, four were
wrong claims in this file, and three I argued and did not take** — the same
split, and the same lesson, as the five previous rounds: *an audit of the
ground truth does not audit the implementation.*

**The one that mattered most defeated a test I had already mutation-tested.**
`voices_uploader_test.dart` asserted `newsRev ?? rev` was `isNotNull` — but the
fixture's own `markUploaded` had set `rev` two lines earlier, so it held whether
or not the handler ran. The auditor proved it by commenting out `markUploaded`
and watching the test pass. My own eighteen mutations missed it because
un-arming the uploader still killed the *other* assertions in the same test:
**a test can be killed by a mutation and still contain an assertion that cannot
fail.** It now pins `rev`, `docId`, `isEdited` and the empty `imageUrls` — the
half this uploader's own comment calls the highest-stakes in the port — and
the skip-`markUploaded` mutation kills two tests.

**Second: the health arm's rationale re-seeded a claim Phase 148 was written to
retract.** My comment said a second examination for the same patient "conflicts
by construction". It does not — `createExamination` defaults `userId` to the
row's own id and the one production caller passes none, so each examination is
its own document. `PHASE_148_NOTES.md:107-111` says exactly that. **Fifth round
running in which a brief or a note propagated an inherited error**, and this
time the error was one *this project had already corrected in writing*. The
comment now names the profile blob, which is the reachable case, and says why
health needs the arm most (its pull cannot supply the revision).

The test was wrong in the same direction and worse: it seeded
`createExamination(userId: 'user-1')`, a shape **no production caller
produces**, and that fabricated shape was the only reason `_id` differed from
the row id — i.e. the only reason the "derive the URL from `itemId`" mutation
died. It now drives `saveHealthProfileBlob`, where the two genuinely differ,
and the mutation still dies. **A fixture that cannot arise is a mutation that
was never really killed.**

Also fixed: the GET was issued **before** the create/update decision, so every
create-conflict on the five always-`_id` uploaders spent an authenticated
round-trip to learn a revision that was then discarded (the decision turns on
`payload['_rev']`, known before any request); and the dangling
`_adoptExistingDocument` reference in
`survey_clone_survives_schema_bump_test.dart`.

Three findings were argued and **not** taken — the bounded-request reading
above, and items 2 and 3 below.

## Reported, not fixed

1. **`feedback` loses a reply only the server has — and it did before this
   phase.** `messages` is an append array both Planet's web UI and the handset
   write, and `FeedbackMapper.fromDoc:27-31,50-54` deliberately keeps the local
   array on a pending reply while taking the server's `rev`, leaving
   `isUploaded = false`. So an admin reply absent locally is destroyed by the
   next send. The implementation audit read this as destruction the recovery
   arm introduces; **it is not, and the distinction is the whole reason this is
   reported rather than reverted.** Both halves are now pinned in
   `feedback_uploader_test.dart`: the re-send carries only the local array, and
   a plain **pull** already drops the admin reply locally and leaves the row
   swept with a changed revision — so Phase 148's memo re-arms it and the same
   loss lands one ordinary sync later with no 409 and no arm involved. The arm
   changes the latency, not the outcome. The fix is a real merge for that
   column, which is a feedback-slice decision with a conflict-resolution
   question inside it. Severity: medium-high, and older than this phase.
2. **The same shape is *constructible* for `submissions` but I could not prove
   it reachable, so I did not act on it.** A submission document is where
   Planet's grading lands (`grade`, `status`, per-answer marks, all read at
   `submissions_repository.dart:1761-1763` and all re-sent by `serialize`), so
   a re-send under the fetched revision would replace a graded document with
   the handset's ungraded copy. But `upsertDocuments` ends
   `isUpdated: false`, so a pulled submission is not swept; making it swept
   again needs a local edit after the grade arrives, and the retake path
   deletes and re-creates rather than reusing the row. The survey-resume path
   (`:239-245`) does reuse an uploaded row's id, which is the closest thing to
   a route. **A chain that ends "so the port loses X" is a defect only once the
   chain is walked** — Phase 135's rule — and I could not walk this one. Worth
   a targeted look by someone who knows the grading round trip.
3. **`ConflictRecovery`'s "cannot duplicate a document" guarantee is a caller
   convention, not a structure.** `documentUrlUnder` returns null when the
   payload names no document, so its eight callers are safe by construction —
   but three build the URL by hand (`user`, `achievements`,
   `adopted_surveys`) and `send` does not verify that `payload['_id']` exists
   when handed a `documentUrl`. `user` is safe because its verb is a PUT to a
   fixed document URL, not because of anything in the arm. A future POST-verb
   uploader passing a hand-built URL with no `_id` would re-send an append. I
   did not add the assertion because the honest guard is a type that carries
   both the URL and the id together, which is a wider refactor than this phase
   should take on its own; the risk is now written at the code.
4. **`course_progress` cannot conflict, and that is a bug wearing a
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
5. **`team_log` is armed by nothing and probably should not be.**
   `pendingTeamLogUploads` is `uploaded = false` and `markUploaded` sets it
   true, so only a failed-then-pulled row qualifies. Left alone; the same
   predicate question as item 1 sits under it.
6. **`myplanet_activities_uploader.dart` is not an outbox uploader at all** —
   no `queuePending`, no `handler`; it posts directly (`:100`, `:146`). It is a
   genuine read-modify-write merge posting with a fetched `_rev` (`:128`), a
   409 there is unhandled, and `lastUsageUploaded` is not advanced (`:152`) so
   it self-heals on the next interval. It is the one place in the port where
   Kotlin's "re-GET and merge again" is exactly right, and it has nothing.
   Severity: low, because it self-heals. Not in the outbox, so outside this
   arm's reach by construction.
7. **`HealthExaminationDao.markUploaded(id, null)` still erases a stored
   `_rev`** — `app_database.dart:3962-3968`, **Lane A's file**. Phase 148's
   citation of `:3812-3819` has drifted; so have its citations of the correct
   `rev == null ? const Value.absent() : Value(rev)` pattern, which is live at
   **`:1208`** (`UserDao.markUploaded`) and **`:4765`** (`AchievementDao`), not
   `:1198`/`:2066`. **The health arm did not need it and does not touch it**: a
   recovery never passes a null rev — it either has a `String` from the GET,
   guarded `is String && isNotEmpty`, or it does not recover. Reported as the
   brief asks; Lane A owns the fix.
8. **`HealthRepository.cacheDocuments` skipping a dirty row
   (`health_repository.dart:772`) matters less than it did.** Phase 148 item 3
   called the pull-supplies-the-revision route "structurally unavailable for
   health"; the update arm now supplies the revision directly, so a stale-rev
   health write recovers without it. The underlying merge question is
   unchanged, but it is no longer the only route.
9. **`chat` and `public_survey` are unreachable for this arm and stay that
   way.** `chat`'s `_id`/`_rev` are nested inside `data` (`chat_uploader.dart:105-106`)
   so the top-level document has none, and `public_survey` posts to a Planet
   REST route with no document to GET. Phase 148's items 4 and 9 (the chat
   endpoint pointing at CouchDB rather than Planet's chat service) are
   untouched and still open.
10. **The six append uploaders (nine types) are deliberately unarmed** —
   `personals`, `chat`, `submit_photos`, `search_activity`, `activities`'s
   four types, `public_survey`. A 409 is impossible for them (server-minted
   ids), and a `documentUrl` there would be a lie a future reader acts on.
11. **`UploadConfig.additionalUpdates` is declared (`UploadConfig.kt:34`),
   assigned once (`UploadConfigs.kt:263-265`) and never invoked** by
   `UploadCoordinator`. Kotlin-side dead code found while reading the ground
   truth; noted for whoever next audits that file.
12. **A voices retry now re-uploads the images — exposure, not a new defect.**
   `voices_uploader.dart:134` runs `_withImages` before every send and
   `pendingImagesFor` is cleared only by `markUploaded`. A 409 whose re-send
   answers a 5xx makes the row retryable (item 1 of the policy list), and the
   retry POSTs a second set of `resources` documents plus attachments. The
   message is rebuilt from the stored payload so it is not doubled; the
   orphaned `private: true` resource documents are. The same path already
   existed via a direct 5xx, so the arm widens the trigger rather than
   creating the leak. Found by the implementation audit.
13. **`app_database.dart:390` still names `_adoptExistingDocument`**, the method
   this phase deleted, as the justification for the v47 `needs_sync` backfill
   accepting one false positive. The behaviour survives (the shared arm with
   `adoptExisting: true` plus the handler's `markUploaded`), so this is
   documentation only. CLAUDE.md's carve-out would allow making an existing
   statement true even in another lane's file, but `app_database.dart` is
   **Lane A's this round and named explicitly in the brief**, and Lane A is
   editing that file — so the sibling reference in
   `survey_clone_survives_schema_bump_test.dart` is fixed here and this one is
   reported instead. One line.
14. **Phase 148's items 5, 6, 7, 8, 10, 11, 12 remain open**; none is in this
   lane's set.

---

## Tests

Gate green: `dart format` clean, `flutter analyze` clean, **2769 tests pass**
(2735 before — the 34 added reconcile: 16 in the new
`conflict_recovery_test.dart`, 4 in the new `adopted_surveys_uploader_test.dart`,
and one each in eleven existing uploader test files plus
`health_legacy_conflict_test.dart`, plus two for the tombstone paths). *Counted from the run, not remembered.*

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
| un-arm each of the eleven armed uploaders, one at a time | 1–2 each |
| adopt instead of re-sending, checked against the tombstone path | 2 |

The third row is the one worth keeping: **porting Kotlin's arm as written fails
twenty-two tests in this port.** That is the phase's finding stated as a number.
