# Phase 134 — the submissions safety-net sweep

Lane A of a four-lane round. Branch `claude/pending-uploads-sweep-h8n2`, PR
#16936.

Closes what Phase 132's implementation audit called the round's highest-value
item: **the port had no `queuePending` sweep in any sync or background path**.

Two `parity-auditor` passes at `effort: max` ran per the standing rule — one on
the Kotlin ground truth before any code, one aimed at this lane's own finished,
already-green code. The ground-truth pass changed three decisions in this
phase and corrected one claim in my brief; both are folded in below rather than
appended.

## The gap, and why green tests could not see it

Kotlin reaches the server for a finished answer sheet **twice**: from the
profile dialog's dismissal (`UserInformationFragment:303` →
`SubmissionsUploader:84`) and from `uploadManager.uploadSubmissions()`, which
`AutoSyncWorker:136`, `UserDataWorker:48` and `ServerReachabilityWorker:196`
run with no precondition on the sheet, the user or the pull. A missed dismissal
costs nothing there.

The port had only *write-time* call sites — `take_survey_screen:276-278`,
`user_information_screen:471`, `take_exam_screen:542-547` and
`submissions_screen:186-193`. (Four, not five: `teams_screen:200-202` is
`teamLogUploaderProvider.queuePending`, a different uploader.) `background_entrypoint`'s
`'submissions'` step is the **pull**, and `OutboxDrainScope` only drains rows
that already exist. So a sheet whose one enqueue call never ran sat on the
handset as a `complete, isUpdated, !uploaded` row **with no outbox row**, and
stayed there until the same user happened to finish some *other* submission —
because `queuePending` is an unscoped sweep that then rescues it incidentally.

Reproduce it by answering a team survey, tapping Submit, and force-stopping the
app on "Your information". Phase 132 made two such windows reachable for team
surveys (process death with the profile screen open; the `if (!mounted) return`
before the push), but the gap is pre-existing and port-wide: it applies equally
to an exam attempt whose queue call throws.

Every layer had passing tests. `submissions_uploader_test` drove `queuePending`
directly; `dashboard_sync_provider_test` drove `syncAll`; nothing asked whether
the second knew about the first. **The failing test written first here is the
whole phase in one line:** seed a `complete, !uploaded` submission with no
outbox row, run `syncAll()`, and assert the sheet reached the server. On the
pre-fix code it came back `uploaded == false`.

## What landed

* **`dashboard_sync_provider.dart`** — `DashboardSyncNotifier.queuePendingSubmissions()`,
  called from `syncAll()` after the shelf push and the challenge write, ahead
  of every area. Queues, then drains the whole queue.
* **`background_entrypoint.dart`** — top-level `sweepPendingSubmissions(...)`,
  called from the runner's `drainOutbox` closure, ahead of the drain that
  carries it and ahead of the pulls.
* **`submissions_uploader.dart`** — `queuePending`'s `userId` is now nullable,
  and it skips an item whose send is already on the wire.
* **`outbox_repository.dart`** — `isInFlight`, which is what that skip asks.
* **`test/providers/pending_submissions_sweep_test.dart`** — 14 tests, plus the
  two `planetPrefsProvider`/`deviceIdentitySourceProvider` overrides
  `dashboard_sync_provider_test.dart` now needs.

### Five decisions, each a reading of the Kotlin

**Both paths, and the foreground one is not a guess.** `SyncManager.startFullSync`
sweeps nothing — its only push steps are `pushCurrentUserShelf()`
(`SyncManager:155`) and `syncNotificationReads()` (`:200`). The reason the
foreground sync centre gets the step anyway is `UserDataWorker:48`, which is
*not* a background job in the sense its name suggests: the dashboard sync
button reaches `SyncActivity.continueSyncProcess` with `forceSync`, which fires
`isServerReachable(url, "upload")` **and** `startUpload("")`
(`SyncActivity:813-815`) — the pull and, through `uploadBulkData()` →
`enqueueUserDataUpload(UPLOAD_TYPE_BULK)`, that worker. So Kotlin's manual sync
launches pull and sweep as siblings, which is exactly the relation
`queuePendingSubmissions()` has to the areas. The port had already made this
call twice before, for the same reason: `_uploadMyPlanetActivities` and
`_queueSearchActivities` are `AutoSyncWorker`/`UserDataWorker` calls living in
this notifier, and say so in their own doc comments.

**Unscoped.** `SubmissionDao.getPendingSubmissions` (`SubmissionDao:41`) carries
no `userId` predicate, and `uploadSubmissions()` takes no user — the owner is
resolved per row (`UploadConfigs:258`), from the row. So the port's `userId` is
only the outbox row's session tag, and it became nullable: a handset whose
session has gone is precisely the one with somebody else's finished sheet
stranded on it, and a shared handset is the normal deployment for this app.
Nothing reads `OutboxEntries.userId` back (`tables.dart:368`), so a null tag
costs the row nothing.

**Ungated — and the first cut got half of that wrong.** Unlike the two telemetry
steps beside it, the foreground sweep does *not* wait for `successCount > 0`:
Kotlin's manual sweep runs concurrently with the pull and inspects nothing about
it, and `ServerReachabilityWorker` sweeps with **no pull at all**. A failed pull
pass is not a reason to leave a finished sheet on the device. The test that pins
this is worth having because in the widget harness every one of the sixteen
areas fails, so a `successCount` gate would have made the sweep look correct and
never run.

The **headless** half was gated, though, and only the implementation audit
caught it. Put in `syncSteps` beside `shelf_push`, it sat behind three
conditions `BackgroundTaskRunner.run` applies before it reaches the steps
(`background_task_runner.dart:102-136`): the task must not be `maintenance`,
`autoSyncEnabled` must be true, and the interval must be due. And
`BackgroundWorkCoordinator` cancels the `autoSync` job outright when the user
turns auto-sync off (`background_work_coordinator.dart:27-30`), so **for that
user only `maintenance` ever fires and the step would never have run at all** —
their safety net would have been "open the sync centre and press Sync", which
is not a net. Kotlin's closest sweeper is `ServerReachabilityWorker`, scheduled
by `NetworkMonitorWorker.start` from `MainApplication:520-522` with no reference
to any sync setting or cadence, sweeping on **network reconnection**: precisely
the case this exists for, the handset offline when the sheet was finished and
the network back later. The sweep moved to the `drainOutbox` closure, which runs
ahead of all three gates and on every invocation. Citing that worker as the
authority for *ungated* and then filing the code behind the port's own cadence
gate is the kind of contradiction a doc comment makes easy and a test does not.

**Its own `try`, not a shared one — deliberately unlike Kotlin.**
`AutoSyncWorker:121-138` runs the sweep as the sixteenth statement of one
sequential `try`, and three predecessors can throw out of it
(`uploadAchievement`, `uploadNews`, `uploadTeams` each fetch outside their own
guard). When one does, `:136` never runs *and* `setLastSync` at `:138` never
runs. That is a Kotlin weakness rather than a behaviour to reproduce;
`UserDataWorker`'s per-step `runCatching` is the shape followed here.

**Ahead of the pulls, and Kotlin agrees by construction.** Kotlin's submissions
pull is not in the full sync at all — it lives in `HeavyTableSyncWorker`
(`HeavyTableSyncWorker:39-41`), which `startFullSync` only *schedules* at its
end (`SyncManager:209`), while the sweep runs during the pass. The port's
headless path does have a `'submissions'` pull step, so sweep-then-pull is the
safe order as well as the faithful one, and it survives the move into
`drainOutbox` — which the runner also calls before the steps. A source-reading
test pins it.

**The first version of that reasoning was wrong, and it was mine.** Both doc
comments said a pull "would take a locally edited sheet out of
`pendingUploads`", full stop. `upsertDocuments` keys every row on the server
`_id` (`submissions_repository.dart:1670-1672`, `id: id`), so a **locally
authored** sheet — sha1 local id — has a pulled document land *beside* it as a
separate row, touching nothing. Kotlin does exactly the same
(`SubmissionsRepositoryImpl:669-670`), at the cost of two rows per logical
submission after upload-then-pull. What the order actually protects is the
narrower case of a sheet that **arrived from the server and was then edited
locally**: survey resume's `markComplete` sets `uploaded: false,
isUpdated: true` on a row whose primary key *is* the server `_id`, so a pull
running first overwrites both and the edit never uploads. The claim is now
stated in that narrower form and pinned by its own test (*a pull clears the
local edit on a server-originated sheet*) rather than asserted in prose — a
justification nothing exercises is how the four misread-Kotlin findings got
in.

### Drained, not merely queued, and drained whole

The one place the port cannot copy Kotlin's shape verbatim: Kotlin's sweep
*posts*, while `queuePending` only enqueues, and the drain trigger is app
resume or the next headless invocation. Without a drain the fix would convert
"deferred until the user finishes another submission" into "deferred until the
next resume" — better, bounded, and still not what Kotlin does.

The first cut scoped that drain to `SubmissionsUploader.type`, reasoning that a
sweep should not become a whole-queue flush nothing asked for. **The
implementation audit showed that was wrong twice over.** `OutboxDrainer.drain`
satisfies a *joining* caller without doing its work
(`outbox_drainer.dart:79-82`, `return existing.then((_) => const [])`), and
`outboxDrainerProvider` is a cached provider, so `_inFlight` is app-wide: a
resume drain arriving during a submissions-only pass would return having sent
nothing, and every queued health record, rating and team write would wait for
the *next* resume. And the premise was false anyway — Kotlin's manual sync **is**
a whole-queue flush, `UserDataWorker`'s `UPLOAD_TYPE_BULK` running eleven upload
arms of which `uploadSubmissions()` is one. Both paths now drain unscoped: the
foreground explicitly, the headless one by leaning on the drain the runner
already makes. A test pins it by queueing an unrelated `uploadType` before the
sync and asserting it left.

In the headless path this also fixes an ordering the runner imposes:
`BackgroundTaskRunner` drains **before** it runs the steps
(`background_task_runner.dart:100`), so a row swept in a step would otherwise
wait for the next invocation. Sweeping inside `drainOutbox` puts the queue write
immediately before the drain that carries it.

### Why it cannot double-post — and the one way it could

The brief was right that a double POST is worse than a deferred one, and the
ground-truth pass established what actually protects Kotlin: **nothing but the
query predicate and one UPDATE.** `SubmissionDao:44` sets `_id`, `_rev` and
`isUpdated = 0` after a successful send, which drops the row out of
`status = 'complete' AND (isUpdated = 1 OR _id IS NULL OR _id = '')`. There is
no lock, no lease and no dedup key; the single `MainApplication.isSyncRunning`
flag covers only the `AutoSyncWorker` ↔ `ServerReachabilityWorker` pair, and
`SubmissionsUploader.checkAvailableServer` — the dismissal path — neither sets
nor reads it while it spends up to 15 seconds probing URLs before it reads the
table. **Kotlin therefore really can double-post**, and the auditor named the
input: answer a team survey's last question, dismiss the profile dialog, and
tap the dashboard sync button inside that probe window.

The port inherits that protection (`markUploaded` is the port of the same
UPDATE) and adds one Kotlin lacks: `OutboxRepository.enqueue` keys on
`(uploadType, itemId)`, so a sweep over an already-queued row refreshes it
instead of adding a second.

**And the implementation audit found a way through it anyway — the most
valuable thing this phase produced.** `enqueue` puts an `in_progress` row back
to `pending`, deliberately, so a payload edited mid-flight is not lost with the
row `markCompleted` deletes; and `markCompleted` is `deleteIfInProgress`. So the
send that succeeds moments later deletes **nothing**, the row survives as
`pending` carrying the same body, and the next drain posts it again. Planet ends
up with two answer sheets and the orphan carries a `_rev` no device knows.

That reset is correct for *derived state* — the shelf, whose handler rebuilds
from the database, so a replay re-sends current truth. A submission is an
**append** (`submissions_uploader.dart:10` says so) and replaying an append is a
duplicate, not an edit. The distinction was already written down in
`OutboxHandler`'s doc comment; nothing had acted on it.

It was newly reachable because of this phase: before, no credentialed path
paired an enqueue with a drain except `submissions_screen`'s modal, which
nothing races. Putting a drain in `syncAll` means the pair now runs on every
sync while the app is interactive — tap Sync, then finish a survey while the
POST is on the wire, and `take_survey_screen` calls the same `queuePending`.
Demonstrated failing first (`posts = 2`), then fixed: `queuePending` skips an
item whose send is in flight, via a new `OutboxRepository.isInFlight`.

The cost is the narrow window where the sheet is edited *while* its POST is in
flight: `markUploaded` clears `isUpdated` on success, so that edit leaves the
pending set. Kotlin loses it identically — `SubmissionDao:44` clears `isUpdated`
in the same statement — and a later edit is a `_rev`-carrying PUT rather than a
new document, so a lost update is recoverable where a duplicate document is not.

Because every enqueue of `uploadType = 'submissions'` goes through
`queuePending`, the fix covers all four write-time sites as well as both sweeps.

## Every test was mutation-tested

Phase 122's practice, and it earned its keep: the first pass found one claim
(the sweep sitting behind the challenge write) that **no test pinned at all**,
so a test was added for it. Nine mutations, each failing exactly the tests that
should notice:

| mutation | test that failed |
|---|---|
| drop `queuePendingSubmissions()` from `syncAll` | the three foreground delivery/ordering tests |
| drop the in-flight skip in `queuePending` | *a re-enqueue mid-flight does not post a second document* |
| move the sweep from `drainOutbox` into `syncSteps` | *the headless path calls the sweep from drainOutbox* |
| the headless sweep rethrows | *a throwing sweep does not escape* |
| re-scope the sync's drain to `submissions` | *the sync drains the whole queue, not just submissions* |
| scope `pendingUploads` to one user in SQL | *delivers another user's sheet…*, *the sweep runs with no signed-in user* |
| re-scope `queuePending` with a Dart `.where` on `userId` | *the sweep runs with no signed-in user* |
| remove `recordSyncChallengeAction` from `syncAll` | *the sweep runs before the first table pull* |
| move the sweep after the `'submissions'` pull | *the headless path calls the sweep from drainOutbox* |

Three test weaknesses the audit named and this pass fixed. The reachability
test's "nothing calls the sweep" assertion was **vacuous**: it searched from
`syncSteps:` to end of file, which includes the function's own declaration, so
the name always occurred. It is now bounded to the runner's argument list and
asserts the *call site* — before the drain, before the pull, and before
`syncSteps:` so a future move back into the gated step list fails. The two
throwing-sweep tests asserted nothing about the throw actually happening —
`_ThrowingIdentitySource` only throws when `pendingUploads()` returned rows, so
a fixture that stopped setting `isUpdated` would have left them green; it counts
its reads now and the tests assert the count. And **nothing tested the
cross-user handset**, which is the entire case the unscoped design exists for:
a `pendingUploads()` re-scoped to the session in SQL would have left every other
test passing. There is now a test that seeds member B's sheet, syncs as member
A, and asserts both the delivery and that the document is attributed to B.

## A harness trap, arriving through a new door

`outboxDrainerProvider` builds every registered handler, and several of those
uploaders reach `planetPrefsProvider`, which is `UnimplementedError` by
default. The first cut of these tests therefore had the sweep's drain throw,
its own `catch` swallow it, and the test report the gap as **unfixed** — five
failures that looked like the fix not working. `planetPrefsProvider` is
overridden with `PlanetPrefs(await SharedPreferences.getInstance())`, the shape
`leaders_screen_test` already uses. Same family as Phase 75's, and the reason
it is worth writing down again: a swallowed exception plus an
`UnimplementedError` provider is indistinguishable from a broken fix.

**And it bit the sibling file silently.** `dashboard_sync_provider_test.dart`
builds its containers without that override, so once `syncAll` gained the
sweep its two `syncAll` tests were running a `queuePendingSubmissions` that
threw `UnimplementedError` and was swallowed — still green, no longer covering
the second half of the pass. Both overrides are added there with a comment
saying why. **Adding a step to a shared entry point silently narrows every
existing test of it**; the tests that break are the honest case.

## What the two audit passes cost and returned

Worth recording as evidence for the standing rule, since it is now four rounds
of it. The **ground-truth** pass changed three decisions before any code
existed: the foreground placement's real justification (`UserDataWorker:48` is
reached from the dashboard sync button, not from `startFullSync`, which sweeps
nothing), the ungated shape, and the deliberate departure from Kotlin's shared
sequential `try`. The **implementation** pass, aimed at code that was already
green with eleven passing tests, found a duplicate CouchDB document, a headless
half that never ran for anyone with auto-sync off, a drain scope that would
starve the resume drain, three false citations in my own doc comments, a
vacuous assertion in my own reachability test, two tests that could pass without
the thing they tested happening, and the absence of any test for the design's
central claim. **None of that was visible from a green suite.**

## Reported, not fixed

### The sweep now runs `pendingUploads` on every sync, and that predicate is not Kotlin's

The single most important consequence of this change, and the item the next
round should take. Kotlin's sweep query is
`status = 'complete' AND (isUpdated = 1 OR _id IS NULL OR _id = '')`
(`SubmissionDao:41`). The port's `SubmissionDao.pendingUploads`
(`app_database.dart:2438`) is `isUpdated = true` minus a guest-exam arm minus
the `public_%` arm — **there is no status test at all**, deliberately, because
the port merges Kotlin's two upload configs into one uploader and an exam
finishes at `requires grading` rather than `complete`.

That was a defensible trade while `pendingUploads` only ever ran from four
deliberate write-time sites. It now runs on every sync, for every row on the
device, so two rows the Kotlin sweep never sends are sent sooner and more
reliably:

* **the adoption marker** — `createSurveyAdoptionSubmission`
  (`submissions_repository.dart:736,743`) writes `status: ''` with
  `isUpdated: true`, so the port uploads a document Kotlin has no sweep for.
  Phase 129 recorded the divergence and Phase 132 repeated it; it is still not
  in `docs/kotlin-to-flutter-migration.md`'s deviations list.
* **a `pending` free-form draft** — `createDraft`
  (`submissions_repository.dart:87,89`) writes `status: 'pending'` with
  `isUpdated: true`.

Neither is *new*: both were already uploaded by the next write-time
`queuePending` call, since that sweep was already unscoped. What changed is
**when**, not **what** — the set of documents that eventually reach the server
is identical. But it is now systematic rather than incidental, so if the port
should not be sending them, this is the round to decide it. The fix is a status
predicate in `pendingUploads` that admits `complete`, `requires grading` and
the exam arm's own condition while excluding `''` and `pending` — one method in
`app_database.dart` (**Lane B's this round**) with a matching read of
`submissions_repository.dart` (**Lane C's**). Not editable from here, and it is
a behaviour decision rather than an obvious repair.

### A permanently refused sheet accretes one `abandoned` outbox row per sweep

Found by the implementation audit, pre-existing, and now once per sync rather
than once per user write. `OutboxDao.findOpen` deliberately ignores an
`abandoned` row so a fresh enqueue is never blocked by one
(`app_database.dart:2062-2065`), so each sweep **inserts a new row** with a new
id and five fresh attempts. `clearAbandoned` has one caller in the whole tree
(`health_uploader.dart:78`) and `cleanup()` has none, and `outbox` is in
`localAuthorityTables`, so a schema bump does not clear it either. The auditor
demonstrated five sweeps producing five `abandoned` rows and five doomed POSTs.

The request count is at parity — Kotlin re-POSTs once per sweep too, since its
row still matches the predicate — so what is port-only is the unbounded table.
**Not fixed here, deliberately.** The fix is a policy call about the failure
record (clear an item's abandoned rows on re-enqueue and lose the diagnostic, or
bound the table in `cleanup()` and give it a caller), it applies to all fifteen
uploaders rather than submissions, and doing it inside `queuePending` alone
would fix one of them while leaving the shape everywhere else. It wants its own
diff.

The most likely trigger is worth naming because it is a **port-only document
shape**: `createDraft` (`submissions_repository.dart:67-106`) is the submissions
list's own New-submission button, which `collapseSubmissionsByParent`'s comment
(`:1938-1941`) states has no Kotlin writer, and it produces `parentId: ''`,
`parent` as a bare string, `status: 'pending'`. If Planet refuses that shape,
any 4xx is classified `permanent` (`outbox_drainer.dart:163`) and the device
writes one dead row and makes one doomed POST per sync for the life of the
install — silently, because `pendingUploadCountProvider` counts only `pending`.

### Kotlin sends a half-finished exam attempt; the port does not

The direction of the predicate divergence the notes above missed, and the
audit's correction to it. Kotlin's *second* sweep config, `ExamResults`
(`SubmissionDao:40`), has **no status and no `isUpdated` test** —
`type = 'exam' AND parentId IS NOT NULL AND userId IS NOT NULL AND (_id IS NULL
OR _id = '')` — so a half-finished exam attempt goes up on every Kotlin sweep.
The port sets `isUpdated: false` at `_openExamSession`
(`submissions_repository.dart:399-401`) and only flips it at `requires grading`
(`:579`). Start a course exam, answer two of five questions, close the app: the
Kotlin handset's next sweep POSTs a `pending` submission carrying those two
answers and it appears in Planet's course report; the port posts nothing.

The port's own comment at `:568-579` argues for this and calls it deliberate —
but `docs/kotlin-to-flutter-migration.md`'s *Deliberate deviations* section does
not list it, and by this project's rules an undocumented improvement is a
finding. That section is also missing the two divergences above and the retry
one below. Lane C owns `docs/`-adjacent work this round; whoever holds the
deviations list should take all four.

Relatedly, **the port's sweep is one arm of three.** `UserDataWorker` sweeps
`uploadSubmissions()` at `:48`, `uploadExamResult` (the `ExamResults` config) at
`:70-78`, and `uploadSubmitPhotos` at `:93-101`. The port has ported the first.
`submitPhotosUploaderProvider.queuePending` still has exactly one caller,
`take_exam_screen.dart:546`, so the certified-exam verification photo has no
safety net at all — the same gap submissions had, in a smaller place.
Deliberately not bundled: photos are a different upload config with a two-step
POST-then-attach handler, and one `uploadType` per phase is how this stayed
reviewable.

### The port sends the adoption marker Kotlin never sends, and not the adopted survey Kotlin does

An inversion worth naming as a pair. `SubmissionsUploader.kt:83-84` runs
`uploadAdoptedSurveys()` **immediately before** the `uploadSubmissions()` this
phase ports, and `UploadConfigs.kt:186-195` POSTs the `StepExam` to `exams`. The
port's `SurveysRepository.adoptSurvey` (`surveys_repository.dart:78-138`) writes
the adopted `Surveys` row locally and enqueues nothing; there is no `exams`
uploader in the tree. So an adopted team survey exists only on the adopting
handset — while a `submissions` document with `status: ''`, which no Kotlin
config selects, reaches the server. `surveys_repository.dart` is Lane C's.

### Kotlin's retry queue re-POSTs; the port does not, and that is undocumented

`RetryQueueWorker.processOperationInternal` re-POSTs a queued payload and on
success calls only `retryQueue.markCompleted(operation.id)`
(`RetryQueueWorker:231`, `RetryQueue:64-67`) — the `submissions` row still has
`_id = NULL` and `isUpdated = 1`, so **the next Kotlin sweep POSTs it again**.
Concrete input: a survey POST that gets a 502, a successful retry drain, then
any sync — two CouchDB documents. The port's `SubmissionsUploader.handler`
calls `markUploaded` on every successful send including a retried one, so it
does not have this bug. That is the port being *better* than Kotlin, which by
this project's own rules is a documented-deviation decision, not a silent
improvement. It belongs in `docs/kotlin-to-flutter-migration.md` (outside this
lane's set).

### The foreground sync centre has no `submissions` pull at all

`DashboardSyncArea` has sixteen areas and none of them pulls `submissions`,
while the headless path has had a `'submissions'` step since it was written.
This is the Phase 119 class — a walk one path runs and the other does not — and
it means a submission uploaded on handset A never reaches handset B through a
manual sync, only through a background one. Adding an area is more than a
one-line change: it needs a `SyncNotifier`, an entry in the enum's two
`switch`es, and a label in the sync-centre UI, which is outside this set. Named
here because I own `dashboard_sync_provider.dart` and deliberately did not
widen this phase into it.

For the same reason, the port has **no sweep for `submit_photos` either**. The
certified-exam verification photo has one write-time site
(`take_exam_screen:547`) and no safety net, exactly as submissions had. It is a
smaller loss — Kotlin's `uploadSubmissions` does not carry photos either, they
have their own `PhotoUploader` — and it wants the same treatment: find the
Kotlin path that sweeps `Photos` and port it beside this one. Deliberately not
bundled: `submit_photos` is a different upload config with a two-step
POST-then-attach handler, and one uploadType per phase is how this stayed
reviewable.

### Two Kotlin oddities a future porter should not copy

* **`UploadConfig.additionalUpdates` is dead code.** It is declared
  (`UploadConfig:34`), assigned by the `Submissions` config alone
  (`UploadConfigs:263-265`) and never invoked anywhere. Its body
  (`submission.isUpdated = false`) happens to duplicate what
  `SubmissionDao.markUploaded` does in SQL, so nothing is broken — but a port
  that treats it as a second in-memory clearing step is porting a no-op.
* **`SyncManager.start`'s `type` and `syncTables` parameters are never read**
  (`SyncManager:89-98`). `"upload"` and `"sync"` produce the identical
  `startFullSync()`. A port that branches on a sync "type" is inventing a
  distinction the Kotlin does not have.

### `hasPendingOfflineSubmissions` is wider than the query it guards

`ServerReachabilityWorker:194` pre-checks
`SELECT COUNT(*) FROM submissions WHERE (isUpdated = 1 OR _id IS NULL OR _id = '')`
(`SubmissionDao:21`) — the upload predicate with the `status = 'complete'`
conjunct dropped, so a strict superset. It can never suppress a needed upload,
only trigger a sweep that finds nothing. Not ported here, and it should not be
"tightened" if it ever is: that would be a behaviour change dressed as a fix,
and inverting the relation would silently gate off real uploads. The sibling
`hasPendingExamResults` (`SubmissionDao:22`) is mismatched with *its* config in
the opposite direction — neither predicate is a subset of the other.

### `PublicSurveyActivity` leaves an armed row in Kotlin

It uploads through `surveysRepository.submitPublicSurvey`
(`PublicSurveyActivity:136`) and never clears the local row, which
`saveExamAnswer` left at `status = 'complete'`, `_id = NULL`. On a configured
device Kotlin's next sweep POSTs it again, to the authenticated
`/submissions`. The port cannot reproduce this — `markPublicSubmitted` clears
`isUpdated`, and `pendingUploads` excludes the `public_%` owner besides — so
this is recorded as a Kotlin defect the port already avoids rather than
anything to port.
