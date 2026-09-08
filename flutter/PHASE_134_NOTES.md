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

The port had only *write-time* call sites — `take_survey_screen:278`,
`user_information_screen:471`, `take_exam_screen:544`,
`submissions_screen:190`, `teams_screen:202`. `background_entrypoint`'s
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
  called from `syncAll()` immediately after `pushCurrentUserShelf()`.
* **`background_entrypoint.dart`** — top-level `submissionsPushStep(...)`, wired
  into `syncSteps` after `shelf_push` and **before** the pulls.
* **`submissions_uploader.dart`** — `queuePending`'s `userId` is now nullable.
* **`test/providers/pending_submissions_sweep_test.dart`** — 10 tests.

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

**Ungated.** Unlike the two telemetry steps beside it, this does *not* wait for
`successCount > 0`. Kotlin's manual sweep runs concurrently with the pull and
inspects nothing about it, and `ServerReachabilityWorker` sweeps with **no pull
at all**. A failed pull pass is not a reason to leave a finished sheet on the
device. This is also why the test that pins it is worth having: in the widget
harness every one of the sixteen areas fails, so a `successCount` gate would
have made the sweep look correct and never run.

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
headless path does have a `'submissions'` pull step, and
`SubmissionsRepository.upsertDocuments` writes `isUpdated: false` over rows it
touches (as `bulkInsertFromSync` does, `SubmissionsRepositoryImpl:686`), so
sweep-then-pull is the safe order as well as the faithful one. Both port paths
agree on it, and a source-reading test pins the order in the headless list.

### Drained, not merely queued

The one place the port cannot copy Kotlin's shape verbatim: Kotlin's sweep
*posts*, while `queuePending` only enqueues, and the drain trigger is app
resume or the next headless invocation. Without a drain the fix would convert
"deferred until the user finishes another submission" into "deferred until the
next resume" — better, bounded, and still not what Kotlin does.

So each sweep is followed by `drain(onlyTypes: {SubmissionsUploader.type})`.
Every write-time call site already pairs the two (`submissions_screen:186-193`);
the `onlyTypes` scope is what keeps a sweep from turning into a whole-queue
flush nothing asked for. In the headless path this also fixes an ordering the
runner imposes: `BackgroundTaskRunner` drains **before** it runs the steps
(`background_task_runner.dart:100`), so a row swept in a step would otherwise
wait for the next invocation.

### Why it cannot double-post

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

The port inherits the same protection and adds one Kotlin lacks:
`SubmissionsUploader.handler`'s `markUploaded` is the port of that UPDATE, and
`OutboxRepository.enqueue` keys on `(uploadType, itemId)`, so a sweep over an
already-queued row refreshes it instead of adding a second — with
`OutboxDrainer`'s single-flight guard and its status-scoped claim keeping two
drains off one row. The test drives the write-time enqueue *and* the sweep over
one submission and asserts one row, one POST, and a second sweep that posts
nothing.

## Every test was mutation-tested

Phase 122's practice, and worth the ten minutes: a migration test that cannot
fail reads as coverage. Five mutations, each failing exactly the tests that
should notice and no others:

| mutation | test that failed |
|---|---|
| drop `queuePendingSubmissions()` from `syncAll` | the three foreground delivery/ordering tests |
| the headless step returns `false` on throw | *a throwing push step does not request an OS retry* |
| remove `submissionsPushStep` from `syncSteps` | *the headless path builds the step…* |
| move the step after the `'submissions'` pull | *the headless path builds the step…* |
| re-scope `queuePending` with `.where((r) => r.userId == userId)` | *the push step sweeps with no signed-in user* |

The reachability test is the one worth explaining. `submissionsPushStep` is
exercised directly, which is precisely the Phase 113 shape: ported, tested,
green and possibly built by nobody. `executeBackgroundTask` needs a Flutter
binding, real preferences and a WorkManager engine, so its `syncSteps` list
cannot be constructed in a unit test — the test reads
`lib/background_entrypoint.dart` instead and asserts the step is built *and*
built before the pull, the way `version_parity_test` reads `app/build.gradle`.

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
