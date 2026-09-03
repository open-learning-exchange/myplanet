# Phase 120 — the submissions and notifications sync-in shape

Phase 116's D5, D6 and D7. All three are the same shape, the one this port keeps
paying for: **a writer and a reader disagreeing about a key**, with each half
passing its own test.

- **D5 / D6** — the submissions pull read three columns from keys the document
  does not have, stored two more as Dart map literals, and then deleted the
  learner's own work.
- **D7** — `resolveType` was ported and never called, so every server
  notification was mislabelled and unactionable.

Two `parity-auditor` passes at `effort: max`: one on the Kotlin ground truth
before implementing, one on the finished diff. The first overturned two things I
was about to do and found four defects Phase 116 does not mention; the second is
recorded at the end.

---

## D5 / D6 — what the pull produces versus what the reader expected

`upsertRoomSubmissionsFromSync` (`SubmissionsRepositoryImpl.kt:662-739`) is the
whole specification. Three of the columns it fills are **derived**, not read:

| column | Kotlin source | the port read |
|---|---|---|
| `userId` | `normalizeSubmissionUserId(doc.user._id)` `:680` | top-level `userId` |
| `uploaded` | `_rev.isNotEmpty()` `:694` | top-level `uploaded` |
| `teamId` | `doc.team._id` `ifBlank` `doc.user.membershipDoc.teamId` `:699` | top-level `teamId` |
| `isUpdated` | hard `false` `:700` | top-level `isUpdated` |
| `user` | `gson.toJson(doc.user)` minus `_attachments` `:678,689` | `Map.toString()` |
| `parent` | `gson.toJson(doc.parent)` `:698` | `Map.toString()` |

`serializeSubmission` (`:813-862`) emits no `userId` at all — I grepped the
tree, and `"userId"` appears as a JSON key for `CourseProgress`, `ApkLog` and
`MyTeam` documents, never for a submission. So the key the port's sync-in read
was one only the port's own uploader wrote. **The pair agreed with itself and
disagreed with Planet**: a submission made on Planet web, or on a second
handset, arrived with a null `userId` and could not appear in `watchForUser`;
one made here appeared, which is why nothing noticed.

`teamId` is not in Phase 116 and is a live round trip in both directions:
`getPendingSubmissionsForUpload` (`:871-876`) rebuilds `membershipDoc` from the
column and `serializeSubmission:851-855` writes it back into `user`. The port
broke the read half, so `submissionsForTeam` and
`SurveysRepository._teamSubmissionSurveyIds` saw nothing from the server.

`uploaded` reading a stored key meant every synced submission rendered "not
turned in".

`user`/`parent` as `Map.toString()` stored `{_id: exam-1, name: Week 1 quiz}` —
not JSON. `jsonDecode` threw on it everywhere it was read back, silently in
`SurveysRepository._parentSurveyId` (so team-survey adoption saw nothing), the
list drew it as the tile title, and a re-upload replaced Planet's objects with
the literal. Same shape as Phase 104's `SurveyMapper.choices`.

### The prune was worse than reported

**Kotlin does not prune submissions on a sync.** `TransactionSyncManager.kt:277`
is the entire `"submissions"` arm — `bulkInsertFromSync(arr)`, nothing else —
and the only `*NotIn` in `app/src/main` is `myLibraryDao.deleteStalePublicNotIn`.
The port ran `deleteNotIn(savedIds)` after a complete walk and
`deleteNotIn(const [])` when `total_rows == 0`.

Phase 116 said this pruned the learner's submissions "once `markUploaded` clears
`isUpdated`". It is worse: `deleteNotIn` spared only `isUpdated == true`, and
**three** port writers create rows with `isUpdated: false` from the start —
`_openExamSession`, `getOrCreateSurveySubmission`, and both `markPublicSubmitted`
and `markUploaded`. So an exam attempt *in progress*, and every pending survey
row the dashboard prompt reads, were deleted with their answers and their
`submission_questions` by the next sync, before any upload. Start an exam,
answer one question, tap the sync icon: the attempt is gone.

Both calls are removed, and `SubmissionDao.deleteNotIn` is deleted rather than
left behind — a destructive method with no caller is an invitation.

### Removing the prune exposes a duplicate, and that needed fixing too

This is the part the ground-truth audit caught and I had not seen. A locally
authored row keeps its **sha1 primary key** after `markUploaded` stamps the
CouchDB id onto it, so the next walk inserts the same document again under its
`_id`. Kotlin has the identical duplicate — its local key is a UUID and
`markUploaded` is `UPDATE ... WHERE id = :localId` (`SubmissionDao.kt:44`) — and
hides it in the list rather than deleting anything:

```kotlin
for (group in filtered.groupBy { it.parentId }.values) {
    val newest = group.maxByOrNull { it.lastUpdateTime } ?: continue
    uniqueRawSubmissions.add(newest); submissionCountMap[newest.id] = group.size
}                                             // SubmissionViewModel.kt:67-73
```

The port had no collapse, so dropping the prune on its own would have shown each
submitted exam twice. `collapseSubmissionsByParent` ports it, with the `(N)`
badge `SubmissionsAdapter.updateSubmissionCount` shows. **One deliberate
departure:** a row with no `parentId` is never grouped. Kotlin's `groupBy` folds
every null together, but Kotlin has no writer that leaves it null — the port's
`createDraft`, behind the list's own New submission button, does, and folding
those would hide one ad-hoc draft behind another.

### The writer half

Fixing only the reader would have regressed the port's own submissions, so
`serialize` changed with it:

- the non-standard top-level `userId` is gone;
- `user` is an object carrying `_id` (`_userDocument`), so the owner survives
  the round trip the way `normalizeSubmissionUserId(user._id)` expects. Kotlin
  resolves the live `UserEntity` and uploads `UserEntity.serialize()`; this
  repository has no user lookup, so it supplies `_id` from the row over the
  stored blob. The row's normalized id is written **last**, so it wins over the
  raw `ada@lea` a synced blob carries;
- `membershipDoc` rides along from `row.teamId`, overwriting rather than
  deferring, as `:851-855` does — without it a team submission lost its team on
  every round trip;
- `parent`/`user` are omitted rather than sent as null (Kotlin's `if` has no
  `else`, `:840-857`), and `parentId`/`type`/`status` take Kotlin's `""`,
  `"survey"`, `"pending"` defaults (`:827-832`).

### Two things the audit told me *not* to do

- **`answers.examId` stays `parentId.split('@').first`.** Kotlin writes the
  whole `parentId` (`:727`) — but it never queries that column (only an
  `@Index`; `AnswerDao` has no query on it), because it reaches the exam through
  the answer's question row. The port *does* query it:
  `courses_providers.dart:528` is `examIndex[answer.examId]` against bare exam
  ids. "Making it faithful" would have silently zeroed every synced attempt's
  per-step mistake count.
- **The raw notification type stays stored** — see D7.

### Display, because D6 changed what is on screen

Fixing the storage shape turns the tile title from `{_id: exam-1, name: …}` into
`{"_id":"exam-1","name":…}`, which is not a fix. Kotlin never draws the blob:
`SubmissionsAdapter.kt:78` draws `examTitle`, from `examsMap[parentId]?.name`
(`SubmissionViewModel.kt:89`). `submissionDisplayTitle` reads the same name out
of the blob the row already carries, and falls back to the raw column for
`createDraft`'s plain typed title. `submissionSubmitterName` is
`getNormalizedSubmitterName` (`:316-323`) — the detail screen was printing the
whole `user` column under "Submitted by".

---

## D7 — the notification type

**Kotlin stores the raw server type and resolves it once, on the way out.**
`parseNotification:374` writes `type = rawType`; the only production call to
`resolveType` in the app is `NotificationsViewModel.kt:359`, inside
`formatNotification`, whose `Notification(type = resolvedType)` is what
`handleNotificationClick` (`:115-135`), `buildNotificationGroups` (`:265-268`)
and `typeLabelFor` all switch on.

So the port's repository was right to store the raw value, and **storing the
resolved one would have been a regression**: `NotificationDao.markSummaryAsRead`
matches the stored column, and its `summary_`-prefixed ids come from the system
tray (`NotificationUtils.kt:190`) carrying raw types.

The three readers now call `resolvedNotificationType(row)`; nothing about
storage changed.

`resolveNotificationType` is a fresh transcription, because the port's
`NotificationParser.resolveType` was **not** the faithful port Phase 116 called
it. Four differences, all now closed:

1. `type == 'team'` where Kotlin tests `lowerType`; `resolveType("Team", …)`
   returned `"Team"`. Kotlin's own test pins this
   (`NotificationsRepositoryImplTest.kt:703-705`).
2. Same for `newTask`/`newResource`: `"NEWTASK"` returned `"NEWTASK"`.
3. `subType` was returned verbatim; Kotlin lowercases it.
4. **The entire final `when` block was replaced by `return type`.** That block
   is the only path to `voice_reply`: there is no explicit `replyMessage` arm
   anywhere, so a reply notification reaches `voice_reply` *only* through
   `lower.contains("replied to your")`. With `return type`, every
   `replyMessage` document stayed `replyMessage`, grouped as Other, took the
   default bell, and resolved to no destination.

### The duplicate helpers, and which copy was right

The port had **two** copies of `extractTeamSubtype`/`extractRelatedId`/
`extractIdFromLink`: the repository's privates and `NotificationParser`'s
statics. The tested copy was the wrong one.

| helper | Kotlin | repository | `NotificationParser` |
|---|---|---|---|
| `extractTeamSubtype` | `rawType != "team"`, exact | ✔ | case-insensitive ✗ |
| `extractRelatedId` | `doc.get("item")?.asString`, no trim | ✔ | trims to null ✗ |
| `extractIdFromLink` | `link.trim('/')` — a **char** trim | ✔ | `link.trim()` + drops empty segments ✗ |

`extractIdFromLink('/teams/view//abc')` is `""` in Kotlin and in the repository's
copy, `"abc"` in the parser's. `extractRelatedId('team', null, {'item': '  '})`
is `"  "` in Kotlin, `null` in the parser's.

So "call the ported parser instead of the privates" — the obvious fix — would
have been a regression in three places. `NotificationParser` is deleted, the
repository's privates are promoted to top-level functions, and its test file
moved to `test/repository/notification_parsing_test.dart` pointed at them, with
the divergences above pinned.

### Two destination gaps in the same neighbourhood

Wiring `resolveType` in is not enough to make a `newTask` tap work, because the
resolver's own arms diverge from `NotificationsFragment.kt:142-148`:

```kotlin
val teamId = resolve(relatedId) ?: relatedId; openTeam(teamId, navigateToPage)
```

The `?: relatedId` is load-bearing: an uncached task or join request still opens
the team using the id the notification carried. The port returned null, so the
tap did nothing — and a server task's row is **never** cached, because there is
no `tasks` sync walk yet (Phase 116's D16). Also ported:
`getJoinRequestTeamId`'s `removePrefix("join_request_")` (`:169-177`).

---

## Failing-first evidence

Every defect was demonstrated failing on the pre-fix code.

`test/repository/submissions_sync_round_trip_test.dart` — **8 of 9 failed**
before the fix (the ninth is the self-consistent pair, and still passes):

```
reaches the list under the signed-in learner        Expected: ['planet-1']  Actual: []
stores parent and user as JSON                      Actual: '{_id: exam-1, name: Week 1 quiz}'
re-uploads Planet objects rather than string form   Actual: '{_id: exam-1, name: Week 1 quiz}'
counts as turned in                                 Expected: true  Actual: false
takes its team from the embedded membership doc     Expected: 'team-9'  Actual: null
normalizes a planet-suffixed owner id               Expected: 'org.couchdb.user:ada'  Actual: null
the uploaded document carries the owner…            Actual: null (no `user` object at all)
an uploaded submission survives the next full walk  Expected: not null  Actual: null
```

`test/repository/notification_type_round_trip_test.dart` — the API additions
were landed first with the readers untouched, so the four defect tests fail on
the real pre-fix readers while the two guards pass:

```
a join request is routed to the team it names       Expected: destination  Actual: null
a join request is grouped under Join Requests       Expected: 'join_request'  Actual: 'notification'
a server task notification is routed to its tasks   Expected: destination  Actual: null
a voice reply is recognized from its message        Expected: destination  Actual: null
```

### Round-trip evidence that local state survives

Both rules this port has already paid for are covered:

- *a sync-in must not rewrite locally authored state* — `bulkInsertFromSync`'s
  `needsSync`/`isRead` preservation is unchanged and still covered; the
  submissions pull writes `isUpdated: false` only onto rows it is inserting from
  a server document. Three tests drive the real `sync()` against a mocked walk
  and assert that an uploaded attempt, an untouched draft, and an empty server
  database all leave local rows and their answers intact.
- *`deleteNotIn` after a partial walk destroys data* — there is now no prune at
  all, matching Kotlin, and the notifications walk's deliberate absence of one
  is untouched.

### The guards left behind

| file | catches |
|---|---|
| `test/repository/submissions_sync_round_trip_test.dart` | a writer and a reader disagreeing about a submission key, across `serialize` and `upsertDocuments`; a prune reappearing; the duplicate the collapse hides |
| `test/repository/notification_type_round_trip_test.dart` | a reader switching on the raw server type, across the pull and all three readers |
| `test/repository/notification_parsing_test.dart` | the four `resolveType` divergences and the three extractor divergences, each pinned |

---

## Reported, not fixed

Everything below is real, verified against the Kotlin, and outside this lane's
defect. Named so the next phase can cite them.

1. **`parent.questions` is dropped on upload.** `serializeSubmission:840-841`
   prefers the *live* exam — `StepExam.serializeExam(exam, questions)`, whose
   `:92` adds `questions` — over the stored blob. The port always sends the
   blob, which for a local exam attempt is `{_id, _rev, name, courseId,
   totalMarks}`. The port's own pull then reads `parent['questions']` to fill
   `submission_questions`, so a submission uploaded here and pulled on a second
   device yields a detail screen of answers with no questions. Needs an exam +
   questions lookup in `serialize`, which this repository has no DAO for.
2. **`pendingUploads` is scoped the opposite way to Kotlin.**
   `SubmissionDao.kt:41` is `status = 'complete' AND (isUpdated = 1 OR _id IS
   NULL OR _id = '')` — every user's completed submissions on the handset, which
   is what makes the bulk-survey and team-survey flows work. The port's is
   `userId = ? AND isUpdated = true`: only the signed-in user, but any status.
3. **`formatNotification`'s message rewriting is unported.**
   `NotificationsViewModel.kt:360-402` rewrites the row's text per resolved type
   — `resource` → "You have N resources not downloaded", `storage` → "Storage
   running low: 8%", `join_request` → a bold prefix, `task` → title + date +
   team — and renders it through `Html.fromHtml` as the row **title**. The port
   shows `message` raw as a subtitle, so its own resource notification displays
   the bare digit `"7"`. Its own phase: it needs an HTML-capable renderer, and
   the six ARB keys it wants (`resourceNotification`, `storageNotification`,
   `taskNotification`, `joinRequestNotification`) are already taken in
   `app_en.arb` by the *titles*.
4. **A quirk for whoever ports that.** `loadNotifications:78-84` partitions
   task and join-request notifications on the **stored raw** type, before
   resolution — so a `newTask` document never enters `taskNotifications`, gets
   no team-name lookup, and renders without the `<b>Team</b>:` prefix even
   though it is grouped, iconed and routed as `task`. Resolving before
   partitioning would be an undocumented improvement, not a port.
5. **Kotlin stores `""` where the port stores `null`** for `_rev`, `parentId`
   and `userId`, and `"{}"` (not null) for `user`/`parent`, because
   `JsonUtils.getJsonObject` returns an empty object for a missing key. Inert
   against every port reader, but it is why `serializeSubmission:849`'s
   `isNotEmpty()` test passes on an absent user and Kotlin uploads
   `"user": {}`.
6. **`insertSubmission` (`:657-660`) has no production caller** in the Kotlin
   tree — only the interface and the impl. Its `_attachments` early return is
   the same rule as the live filter, so nothing is missing; recorded so a later
   phase does not port a dead entry point.
7. `deleteNotIn` on the **`submit_photos`** table does not exist and should not
   be added — photo documents POST to the `submissions` endpoint
   (`UploadConfigs.kt:224-235`) and come back through this walk, where the
   top-level `_attachments` filter is what keeps them out of the list.

---

## Files touched outside this lane's set

All defects, none tidying. Lane A owns the rest of `lib/repository/`; Lane C owns
`lib/l10n/`, `test/l10n/`, `tool/`. Nothing here is theirs.

| file | why |
|---|---|
| `lib/data/local/app_database.dart` | deleted `SubmissionDao.deleteNotIn`, the destructive method the D5 fix stops calling |
| `lib/ui/notifications/notification_destination.dart` | the D7 reader, plus the `?: relatedId` and `join_request_` gaps |
| `lib/ui/notifications/notification_grouping.dart` | the D7 reader |
| `lib/ui/notifications/notification_parser.dart` | **deleted** — dead, and its copies of three helpers disagreed with Kotlin |
| `lib/ui/notifications/notifications_screen.dart` | the D7 reader (icon and title) |
| `lib/providers/notifications_provider.dart` | the D7 reader (`toggleExpansion`'s default) |
| `lib/ui/submissions/submissions_screen.dart` | the collapse removing the prune exposes, and the title D6 changes |
| `lib/ui/submissions/submission_detail_screen.dart` | the same title, and "Submitted by" |
| `test/ui/notification_destination_test.dart` | asserted the pre-fix null return |
| `test/ui/notification_parser_test.dart` | **moved** to `test/repository/notification_parsing_test.dart` |
| `test/repository/submissions_repository_test.dart` | three tests written against the non-standard document shape |

No schema change: `Submissions` already has every column the Kotlin pull writes
and `Notifications` already carries `subType`. `schemaVersion` stays 45.
