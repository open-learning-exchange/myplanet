# Phase 147 — a voice post's images, and four smaller voices defects (Lane B)

Five items from the previous rounds' *Reported, not fixed* lists:
`PHASE_144_NOTES.md` items 1, 2 and 3, and `PHASE_142_NOTES.md` items 2 and 4.
Every one named the file and the change, which is the pattern those lists exist
for — the round was briefed almost entirely from them and needed no discovery
phase to start.

## What landed

| Fix | Where | Class |
|---|---|---|
| **A voice post's images reach the server** | `voices_uploader.dart`, `voices_repository.dart`, `voice_images.dart`, `voice_image_picker.dart`, the three composers | **the feature was absent in both directions** |
| An enterprise voice post is labelled `enterprise` | `voices_provider.dart`, `team_voices_screen.dart` | two writers disagreeing about one field |
| A comment names its author | `voices_repository.dart`, `inline_comments.dart` | sync-in erasing a locally-authored column |
| The reply tap is not dropped before the composer opens | `voice_thread_screen.dart` | `.valueOrNull` on an unwatched provider |
| The challenge tally buckets by the device's day | `voices_repository.dart` | UTC where Kotlin uses `'localtime'` |
| Three stale or false claims corrected at the code | `voices_uploader.dart`, `voices_repository.dart` | a correction has to reach every copy |

**37 tests across five new files and four existing ones. 26 mutations, 26
caught — three only after the test they exposed was rewritten.** Two
`parity-auditor` passes at `effort: max` ran, one on the Kotlin ground truth
before implementing and one on the finished green code. **The second pass
found two behavioural defects and five wrong claims**, all fixed here; the
biggest is worth its own section.

**Five defects were in code this phase itself wrote**, found after the first
cut was green and all of them in the image slice. They are worth reading before
the rest, because three would only ever have shown up in production and the
fourth was caught by a test that already existed:

- **Two picks with the same filename shared one slot.** The second overwrote
  the first and both `imageUrls` entries resolved to the same bytes — one image
  lost, silently. Kotlin cannot reach this; its entries carry two different
  absolute source paths, and the collision is the price of keying on the name.
- **And the de-duplication that fixed it keyed on the wrong string.** The slot
  is `<newsId>/<_segment(name)>`, and `_segment` takes the basename, so
  `a/photo.jpg` and `b/photo.jpg` are two distinct raw names that reduce to one
  file. A guard keyed on the raw name let exactly the case it existed to
  prevent straight through. `VoiceImages.storedNameFor` is now the key.
- **Cleanup could fail a post the server had already accepted.**
  `markUploaded` deletes the delivered slot *after* the news document lands, so
  anything escaping it fails the outbox row for a post that is on the server —
  and the next drain POSTs a second copy, the exact duplicate the handler's
  id/rev guard exists to prevent. `on Exception` was not enough:
  `getApplicationDocumentsDirectory` on an engine with no `path_provider`
  channel throws a **`FlutterError`**, which is an `Error`, and headless
  WorkManager engines are where this drains. **Caught by the pre-existing
  `markUploaded` test**, not by anything this phase wrote.
- **A blank planet code was omitted where Kotlin sends it.** `createImage`
  guards each of `addedBy`/`resideOn`/`sourcePlanet` with `?.let`, so a
  **null** code omits the key and an empty one is still written; the first cut
  tested `isNotEmpty` and dropped a key Kotlin sends for a user whose code is
  blank rather than absent. Read off the Kotlin by hand rather than taken from
  the ground-truth report — which had the fields and the order right, and this
  is the kind of detail a summary flattens.
- **A transient attachment failure abandoned the whole post.** The handler
  wrapped every image failure in `NetworkError(null, …)`, and `OutboxDrainer`
  reads `(code ?? 0) < 500` as *permanent* — so one dropped connection
  mid-attachment discarded the post, its text included, after a single attempt.
  It now returns the underlying result and lets the drainer's existing
  retryable-versus-permanent rule decide; a missing file has no result and
  stays permanent, which is right because a retry cannot recreate bytes.
  **Adding a failure path means deciding how the outbox classifies it** — a
  synthetic error is not a neutral wrapper.

## Job 1 — the gap was much larger than the fix, in both directions

`PHASE_142_NOTES.md` filed upstream `a182edd` as *not ported*: it changes the
mime type Kotlin sends when PUTting a voice image, and the port had no
attachment upload to attach a mime type to. That was right, and it understated
the problem twice.

### The ground-truth pass changed the design before a line was written

The brief — and my own first reading — assumed the bytes are an attachment on
the **news** document, the shape `SubmitPhotosUploader` and `TeamsUploader`
already use. They are not. `UploadManager.kt:303-361` does something else
entirely, per image:

1. POST `createImage`'s document (`:126-142`) to `<base>/db/**resources**`;
2. PUT the bytes to `<base>/db/resources/<resourceId>/<fileName>`, with the
   revision in an **`If-Match` header** and no `?rev=` query
   (`FileUploader.getHeaderMap`, `:80-88`);
3. append `"\n![](resources/$resourceId/$fileName)"` to the **outgoing**
   message and record `{resourceId, filename, markdown}` in the post's
   `images` array;
4. and only *then* POST the news document.

So the order is load-bearing rather than incidental: the news document carries
what the image uploads produced, and neither the markdown nor the `images`
entry can exist before the resource documents do. A port that had followed the
sibling uploaders' shape would have PUT the bytes onto `news/<id>/<name>` and
been wrong in a way every test built on the same assumption would have
confirmed. **This is the strongest argument yet for the mandatory
ground-truth pass**: the design error was in the brief, in the two nearest
in-tree precedents, and in my first reading of all three.

### And there was no writer at all

The reachability questions — *who writes this table, can the writer produce
values the reader's predicate matches, does anything navigate here* — answered
badly. `NewsEntries.imageUrls` had **zero writers with a non-empty value** in
`lib/`: `createPost`, `postReply` and `editPost` all accepted an `imageUrls`
parameter, **no caller anywhere passed one**, no screen offered an attach
affordance, and `NewsMapper` merely preserved whatever was already there
(`news_mapper.dart:103`).

Porting only the uploader would therefore have produced exactly what Phases
113, 116 and 119 keep finding: ported, tested, green and dead. The brief's own
framing gives this away in hindsight — it cites Phase 100's *bytes on disk and
the row that points at them need one key*, which only has a referent if
something puts bytes on disk. So the slice is both halves: a picker seam, the
composer affordance, and the two-step upload behind it.

### Bytes and row share one key, derived once

Kotlin stores the picker's **absolute device path** in `imageUrls`
(`BaseVoicesFragment.kt:153-156`) and reopens it at upload time
(`UploadManager.kt:322`). That works there because the upload is a worker
running minutes later on the same filesystem. It does not transfer to a port
whose write-back is a durable outbox that may drain after a process death or an
app update — and on iOS the documents-directory path changes between launches,
invalidating every absolute path ever recorded.

So `VoiceImages` owns a slot at `<newsId>/<fileName>`, mirroring
`TeamAttachments` and `SubmitPhotosFiles` (including the `_segment` traversal
guard), and **the repository that mints the news id writes the bytes**. One
side derives the key and nothing hands it across a boundary. The JSON entry
keeps Kotlin's exact two-key shape (`imageUrl`, `fileName`) so `editPost`'s
removal predicate still matches, but the *lookup* is by `fileName` — the path
is recorded for faithfulness, not relied on.

An image whose bytes cannot be written is **dropped from `imageUrls`** rather
than recorded: an entry with no file behind it would make the uploader take a
"missing file" branch on every drain and the post would upload referencing
nothing. Losing the image visibly beats a document that claims an attachment it
does not have.

### Three Kotlin defects deliberately not ported, each pinned

The ground-truth pass found more than the two the brief named. All are in
*Reported, not fixed* in full; the three that touch code I wrote are:

- **A refused upload is ignored and the post goes up anyway.**
  `UploadManager.kt:313-319` calls `.body()` on the resource POST with no
  `isSuccessful` test, and `:327-331` **discards the attachment PUT's
  `Response` entirely**. Retrofit does not throw on a non-2xx, so a 413 or a
  409 changes nothing: the markdown is appended, the `images` entry is written,
  the document uploads, and `markNewsUploaded` clears `imageUrls`. The post
  permanently references an attachment that does not exist and the only local
  pointer to the file is gone, with nothing logged. The port fails the
  operation instead, so the outbox row stays `pending` with `imageUrls` intact
  and the drain retries the whole post. Pinned by *a refused attachment leaves
  the post queued with its image*.
- **The second sweep strips the images off a delivered post.**
  `UploadManager.kt:345` overwrites the serialized `images` with an array
  rebuilt from `imageUrls` — which `markNewsUploaded` has just cleared. So the
  next sweep sends `images: []`, the server document loses its image metadata,
  and the write-back carries the empty array back over the local column too;
  the resource document and its attachment survive with nothing pointing at
  them. Because `getNewsForUpload` is an unfiltered `SELECT * FROM news`, this
  also hits every voice the device merely *pulled down* — one handset can strip
  the image metadata off the whole community feed. The port returns the payload
  untouched when nothing is pending. Pinned by *re-sending a delivered post
  does not strip its images*, and that test is the reason mutation M7 stopped
  surviving: the first version asserted `images` was empty on a post that never
  had any, which the mutation also satisfied. **A test whose expected value is
  the mutation's output is not a test.**
- **An empty resource id still produces a PUT.** `JsonUtils.getString` defaults
  to `""`, so a failed create yields a PUT to `resources//<name>` with
  `If-Match: ""` and markdown `![](resources//<name>)`. The port refuses.

### What the implementation audit changed

**An undeliverable image retried forever, and grew the outbox by a dead row per
sync.** The first cut treated a missing file as a reason to fail the whole
operation, on the reasoning that refusing is safer than Kotlin's silent loss
and that the row would end up "abandoned after `maxAttempts`, which is visible
rather than silent". Both halves were wrong. `_ImageUploadFailure` with no
result becomes `NetworkError(null, …)`, which `OutboxDrainer` classes
**permanent** — abandoned after *one* attempt, not `maxAttempts`. And
`OutboxDao.findOpen` matches only `pending`/`in_progress`, so the next
`queuePending` does not see the abandoned row and `enqueue` inserts a **fresh**
one with `attemptCount: 0`. `queuePendingVoices` runs on every sync, so the
post never arrives, nothing surfaces it (`watchPendingCount` counts only
`pending`), the `outbox` table — a preserved table — grows without bound, and
on a permanently refused attachment each cycle leaves another orphan
`resources` document on the server. **The port's correct refusal is what made
Kotlin's single leak unbounded.**

The rule now: a failure the drainer would call permanent drops that image and
sends the post without it; a retryable one still fails the operation so the
outbox retries. `_ImageUploadFailure.isPermanent` classifies with the *same*
expression the drainer uses, so the two cannot disagree about what is worth
attempting again. The image was already lost; blocking the post loses the text
as well.

**Every uploaded image was named `scaled_<original>` on Planet.** Android's
`ImageResizer.resizeImageIfNeeded` returns the original path only when
`maxWidth == null && maxHeight == null && imageQuality == 100`; otherwise it
re-encodes and writes `"/scaled_" + name`. The picker asks for 1280×1280 at
quality 85 — deliberately, because these cross the connection myPlanet exists
to work around — so **every** pick came back prefixed, and that one name is the
`resources` document's `title` and `filename`, the attachment name in the PUT
URL, and the `![](…)` path in the message. Kotlin sends the bare basename.
Invisible to every test, because both test files fake the picker.

**And the fix for the first defect needed its own test rewritten.** The
assertion "the dead entry is dropped, so no later sweep retries it" ran on the
success path, where `markUploaded` clears `imageUrls` anyway — so it passed
with the drop removed. It only bites when the news POST *also* fails, which is
what it now drives.

Five claims were wrong and are corrected at the code: a citation to
`BaseVoicesFragment.kt:63-67` for an affordance that is the *result handler*
there (`addImage` is `:131-134`); a pointer to a wrong claim on a DAO method
that does not carry it, past the two that do; "Port of
`getCommunityVoiceDates`" for a Kotlin method called
`getCommunityVoiceDateCount` that returns an `Int`; two comments still calling
`delivered` "the document that actually went out" after the same commit's other
comment says it deliberately is not; and a stated reason for that choice
("re-queues forever") that is wrong — it re-queues once, and the real damage is
the second send PUTting the un-augmented message over the server's.

**One race closed.** `TeamVoicesScreen`'s FAB was gated on the membership
stream while the team came from a separate future, so a tap in that window
composed an enterprise's post as `messageType: ''` — the mislabelling Job 2
exists to fix. The comment claimed Kotlin sends `""` there too; it does not,
because `TeamsVoicesFragment` only enables its submit button once the team has
resolved. The FAB now waits for both.

## Job 2 — an enterprise post is labelled `enterprise`

`TeamsVoicesFragment.kt:80` writes `map["messageType"] =
getEffectiveTeamType()`, which `BaseTeamFragment.kt:94-96` resolves to the nav
argument, else `team?.type`, else `""`. Enterprises are a team *type*, not a
separate feature (Phase 99), so an enterprise's discussion posts are
`"enterprise"` on Planet and the port's hardcoded `'team'` mislabelled every
one.

Two details worth keeping. The fallback is **`''`, not `'team'`** — `MyTeam`
reads the field with `JsonUtils.getString`, so a team document that omits
`type` yields `""` in Kotlin too, and substituting `'team'` would invent a value
Kotlin never sends. And this closes the same "two writers, one field" shape
Phase 144 closed for `messagePlanetCode`: the port's chat-share writer already
sent `target?.teamType ?? ''` (`chat_repository.dart:359`), so `createTeamPost`
was the one disagreeing.

The port has no `teamType` nav argument to prefer, because its team screens
resolve the team directly rather than through Kotlin's tab pager — the same
reason Phase 142 gave for `getEffectiveTeamName`. `team?.type ?? ''` is the
whole chain.

`teamType` is **required**, not defaulted, and the round did both halves:
Phase 144 declined to add the argument without the caller precisely because a
dead parameter is not a fix.

## Job 3 — the comment author, and a doc comment naming a method that does not exist

`voices_repository.dart` claimed to port `TeamsRepositoryImpl.addComment`.
There is no such Kotlin method: `grep -rn "fun addComment" app/src` is empty and
`messageType = "comment"` appears nowhere in the Kotlin. Inline comments are a
port-original from unmerged issue #15112 (Phase 74). The comment is corrected at
the code, because a citation to a method that does not exist is how a future
audit reaches a wrong verdict.

The row carried no `user`, which for a `news` document is the whole author
identity — there is no top-level `userId`/`userName` on the wire, and
`NewsMapper.fromDoc` reads all three local columns back out of the nested
object *unconditionally*, so a document without it does not merely fail to set
them, it **erases** what the row had.

The call site also wrote `user.id` where the three voice writers write
`user.couchId ?? user.id`. Since `authorJson` puts the `couchId` in the author
object's `_id`, and `fromDoc` copies that into `userId`, the two halves named
different users and the first sync silently rewrote the column. Both fixed;
pinned separately, so either regressing turns a different test red.

Phase 144's decision that comments stay in `pendingUploads` is **not**
reversed: they were already uploaded incidentally by the unscoped write-time
`queuePending`, so excluding them would make comments device-local — a bigger
behaviour change with no ground truth to check it against.

## Job 4 — the fifth-plus `.valueOrNull`

`voice_thread_screen._reply` read `sessionProvider` without watching it and
returned early, so the tap was dropped **before the composer opened** — no
dialog, no snackbar, no row. Phase 144 made `VoicesActions.postReply` itself
safe, which left the screen's own guard as the last thing between the tap and a
reply, and a guard that always fires is not a guard.

Awaited, with the `await` inside the `try`, because a future can reject where
`valueOrNull` could not. Grepped the whole file as the brief asked: the other
`.valueOrNull` reads are on providers the screen *watches*, which is fine.

## Job 5 — the challenge tally, and why the test needs a seam

Kotlin buckets in SQL with `strftime('%Y-%m-%d', time/1000, 'unixepoch',
'localtime')` (`NewsDao.kt:61,68`) — the **device's** day. `_formatDate` built
the date from a UTC `DateTime`, so two posts either side of local midnight
collapsed into one bucket where Kotlin counts two, and it is the *count* the
five-day threshold compares against.

**CI runs in UTC, where the two implementations are identical and no test could
fail on the defect.** `VoicesRepository.deviceUtcOffset` is the seam that lets a
test pin a non-UTC device; in production it reads the offset *at that instant*,
so it follows DST the way `'localtime'` does. Three tests: Kathmandu's +05:45
(a 45-minute offset also catches an implementation that rounds to hours), the
two-posts-one-day case, and Lima's -05:00 so a sign error cannot pass.

Phase 142 stated its limit honestly — the bucketing divergence was certain but
it had not constructed a case where the *count* crosses the threshold. The
second test is that case.

The same pass corrected a false claim in the same dartdoc: it said "the Kotlin
filters `isCommunitySection` in memory after the DAO query", and the Kotlin
filters it **in SQL**. `PHASE_142_NOTES.md` item 5 reported that claim against
`app_database.dart`; it was in `voices_repository.dart` too, and only the copy
in the reported file had been noticed.

## Reported, not fixed

Each names the file, the change, and why it was not made here.

1. **The port's challenge tally excludes replies; Kotlin's counts them.**
   `NewsDao.getInTimeRange`/`getInTimeRangeForUser`
   (`flutter/lib/data/local/app_database.dart:3402-3427`, **Lane A's file**)
   both add `_isTopLevel(r)`, which the Kotlin SQL has no equivalent of — its
   only predicates are the time window, the optional `userId`, and the
   `viewIn LIKE`. Kotlin's `postReply` copies the parent's `viewIn` verbatim
   (`VoicesRepositoryImpl.kt:319`), so replying to a community voice makes that
   day count there and not here. Same defect class as Job 5 and strictly larger
   — it drops whole days rather than moving them. The fix is deleting
   `_isTopLevel(r)` from both queries; the dartdoc on both ("all top-level
   community voices") goes with it. **Severity: medium**, and note the
   direction: the port under-counts, so a learner is told they have not done
   something they have.
2. **The same false "filters in memory" claim, and two others, are still in
   `app_database.dart`.** `PHASE_142_NOTES.md` item 5 reported all three
   (`getInTimeRange`'s "Port of `NewsDao.getInTimeRange`", which does not
   exist; "all top-level *community* voices", which its query does not
   implement; and the in-memory claim). Still Lane A's file. I fixed only the
   copy in mine.
3. **Editing a post that has an image drops the markdown from the server
   document.** The local `message` never gains the `![](...)` line — Kotlin's
   write-back leaves `message` alone (`VoicesRepositoryImpl.kt:49-68`) and the
   port matches — so an edit re-sends the plain text and the server loses the
   image reference, though the `images` array survives. Kotlin has the same
   asymmetry and reaches it on *every* sweep rather than only on an edit
   (`PHASE_144_NOTES.md` item 6), so the port is strictly better but not
   correct. Not fixed because both repairs are inventions with no ground truth:
   writing the augmented message back locally would show the user raw markdown
   in their own composer, and re-appending from the `images` array at send time
   would double-append if the user pasted the line themselves. **Wants a
   decision, not a patch.**
4. **The edit composer cannot add an image.** Kotlin's `editPost` accepts
   `newImages` and its edit dialog carries the same `addNewsImage` button;
   `showVoiceComposer` is called without `allowImages` from
   `voices_screen._edit`. Wiring it needs the second send to carry the existing
   `images` alongside the new ones, which is item 3's question. Deliberate, and
   pinned by *the edit composer offers no attach affordance* so it cannot be
   switched on without the rest.
5. **A retry after a partial image upload orphans the resource documents
   already created.** If the second of two images fails, the first one's
   resource document and attachment are on the server with nothing referencing
   them, and the retry re-uploads both as fresh documents. Kotlin has this too
   and worse — unbounded, one orphan per image per sync forever, because
   `imageUrls` is never cleared on that path (`UploadManager.kt:349-353`).
   Here it is bounded by the drop rule above — a permanently refused image is
   forgotten after one cycle, so at most one orphan per image rather than one
   per sync. (An earlier draft of this line said "bounded by the outbox's
   `maxAttempts`", which was wrong twice over and is what the implementation
   audit's largest finding turned on.) A *retryable* failure can still leave
   one orphan per attempt until the outbox abandons the row. Fixing that
   properly means
   recording the resource ids across attempts, which is a column on `news` and
   a schema bump — **Lane A's `tables.dart`/`app_database.dart` and a number I
   do not have.**
6. **`RetryQueueWorker.kt:200-233` never writes the id back after a successful
   news retry**, so the next `uploadNews()` re-POSTs the post *and* re-uploads
   every image as new resource documents. And `:234-238` treats a 409 on a
   retried edit as "already synced" and discards it with a success log. Kotlin
   defects with no port counterpart — the outbox records `_id`/`_rev` in the
   handler — recorded so a future harvest does not port them as parity. Belongs
   in `docs/kotlin-to-flutter-migration.md` (**Lane D's file**), alongside
   `PHASE_144_NOTES.md` item 5's three.
7. **`FileUtils.getFileNameFromUrl` corrupts some attachment names in Kotlin.**
   It `URLDecoder.decode`s the local path's last segment, so `photo+1.jpg`
   becomes `photo 1.jpg` (no longer matching the file on disk) and `100%.jpg`
   throws and yields `""` — a PUT to `resources/<id>/` and markdown
   `![](resources/<id>/)`. Kotlin also interpolates the name into the URL
   unencoded, unlike `TeamsUploader.kt:143`. The port encodes with
   `Uri.encodeComponent` and never decodes, so it has neither; recorded because
   "match Kotlin's naming" would be the wrong instinct here.
8. **`getEffectiveTeamId()` has no blank guard where its two siblings do**
   (`BaseTeamFragment.kt:98-100`), so a caller passing an empty `"teamId"`
   argument produces `viewInId = ""` and `News.getViewInJson` emits `[]` — a
   team post visible to nobody. The port resolves the team id from the route,
   so it cannot reach this. A Kotlin bug, not a port gap.
9. **The challenge window bounds are UTC-anchored while the day bucketing is
   local**, in both apps. `startTime`/`endTime` are compared against raw millis
   (`DashboardViewModel.kt:284-286`), so a post near a boundary can be inside
   the window and bucketed into a day outside it. Job 5 makes the port match
   Kotlin exactly, including this; changing it would be a divergence. Kotlin
   has a larger version of the same confusion: the dialog shows from Dec 1 2024
   to Jan 15 2025 but only counts voices posted before Dec 16 2024, so voices
   posted in the campaign's last month never move the counter.
10. **`uploadNewsActivities()` is dead code in Kotlin** and `NewsLogDao.insert`
    has zero callers in `app/src/main` — re-confirmed this round
    (`PHASE_144_NOTES.md` item 7). Do not build a `news_log` for the port.
11. **`PHASE_144_NOTES.md` items 4, 8, 10, 11, 12 and 13 are still open.** The
    `authorJson` `iterations` question, the `news` prune Kotlin does not have,
    the sweep decoding the whole table every 15 minutes (needs a `NewsDao`
    predicate query in **Lane A's `app_database.dart`**), the reachability
    test's bounds, `recoverStuck` not running on foreground resume, and
    Phase 140's `teamPlanetCode` column. None are in files this lane owns
    except the prune, which is a behaviour change of its own.

## Files I took beyond the brief's list

Declared for the integrator. None is owned by another lane this round.

- `flutter/lib/ui/voices/voice_composer.dart` — the attach affordance and the
  `VoiceComposition` return type. A voices UI file; unowned.
- `flutter/lib/core/files/voice_images.dart`,
  `flutter/lib/core/system/voice_image_picker.dart` — both **new**, so they
  cannot collide.
- `flutter/test/l10n/placeholder_integrity_test.dart` — the pinned
  human-reviewed counts, +1 per locale for `addImage`. Forced by the ARB
  change; the test's own convention is that each move gets a paragraph saying
  what it was and why it is recovery rather than generation.
- `flutter/lib/l10n/app_{en,ar,es,fr,ne,so}.arb` — one key, `addImage`, with
  the **human translations recovered from `values-*/strings.xml`** in all five
  languages rather than generated. Per `CLAUDE.md`, recovery is strictly better
  than machine translation and should be exhausted first; this one was already
  shipping in the Android app.

I did **not** touch `app_database.dart`, `tables.dart`, the outbox files, or
any Lane A/C/D file, and I needed **no schema bump** — `imageUrls` already
existed and the two-step upload adds no column.

## Mutations run

Each applied alone to green code and reverted; the anchor was asserted **unique**
first, because `PHASE_144_NOTES.md` records a mutation that matched the wrong
sweep and reported a false negative.

| # | Mutation | Caught by |
|---|---|---|
| M1 | the handler skips the image step (the pre-fix behaviour) | 8 of 10 image tests |
| M2 | the attachment goes to `news`, not `resources` | *the bytes are PUT onto a resources document* |
| M3 | the rev is dropped from the attachment PUT | *the bytes are PUT onto a resources document* |
| M4 | the mime type is hardcoded instead of read from the name | *the mime type is detected from the name* |
| M5 | a refused attachment is ignored, as Kotlin ignores it | *a refused attachment leaves the post queued* |
| M6 | the derived body is passed as `delivered` | *a delivered post keeps its images* |
| M7 | `images` rebuilt even with nothing pending | *re-sending a delivered post does not strip its images* |
| M8 | the bytes are filed under a key the uploader does not use | 8 tests |
| M9 | the delivered slot is never cleaned up | *a delivered post keeps its images* |
| M10 | the community composer stops offering the affordance | *the community composer hands the image to createPost* |
| M11 | the community composer drops the images it collected | *the community composer hands the image to createPost* |
| M12 | the reply composer drops its images | *a reply hands the image to postReply* |
| M13 | the edit composer gains the affordance it should not have | *the edit composer offers no attach affordance* |
| M14 | `addComment` stops writing the `user` column | *a comment names its author* + the round trip |
| M15 | the comment's `userId` reverts to the bare local id | *the comment is filed under the id its author object carries* |
| M16 | colliding filenames are not disambiguated | *two picks with the same name do not share one slot* |
| M17 | de-duplicate on the raw name instead of the stored one | *two names that reduce to one slot do not collide* |
| M18 | an empty planet code is omitted instead of sent | *a blank planet code is sent, not omitted* |
| M19 | the `await` moves outside the `try` | *a rejecting session drops the tap instead of throwing* |
| M20 | the offset is dropped (the pre-fix UTC bucketing) | all three challenge-day tests |
| M21 | the offset is subtracted instead of added | all three challenge-day tests |
| M22 | the team screen falls back to `'team'` instead of `''` | *a team with no type sends the empty string* |
| M23 | a permanent image failure blocks the post again | *bytes vanished…* + *a permanently refused attachment…* |
| M24 | a retryable image failure is treated as permanent | *a transient attachment failure is retried* + *…leaves the post queued* |
| M25 | the dead `imageUrls` entry is left on the row | *a dead image is forgotten even when the post itself fails* |
| M26 | the picker's `scaled_` prefix reaches Planet | *the picker's own rename does not reach Planet* |

**M25 survived too, and for a third distinct reason**: its assertion ran on the
success path, where `markUploaded` clears `imageUrls` regardless, so the
property it named was true whether or not the code under test did anything. It
now drives the failing-post path, the only one where the drop is observable.
**Ask where the claim is *observable*, not just where it is true.**

**M7 and M13 survived their first run**, and both were the test's fault rather
than a missing predicate — which is the whole reason to mutate. M7's assertion
was that `images` is empty on a post that never had an image, which the
mutation's output also satisfies; it now drives upload-then-edit, the sequence
that actually reaches Kotlin's stripping bug. M13's test built its own
`showVoiceComposer(context)` call instead of tapping the card's edit button, so
it could not see the line it exists to forbid. **A test whose expected value is
the mutation's output, and a test that reimplements the call site it is
guarding, both read as coverage and are not.**

## Reported by the implementation audit, not fixed

Beyond the numbered list above.

12. **Kotlin refuses a duplicate pick with `image_already_added`**
    (`BaseVoicesFragment.kt:171-176`, `ReplyActivity.kt:241-246`); the port has
    neither the guard nor the string. It cannot implement Kotlin's version as
    written, because that guard keys on `XFile.path` and `PickedVoiceImage`
    keeps only bytes and a name — and Kotlin's own guard would miss here
    anyway, since each pick lands at a fresh UUID cache path. A content hash
    would work. Left out because it is a new affordance rather than a parity
    gap, and `_uniqueFilename` already stops the two picks from colliding.
13. **`flutter/ios/Runner/Info.plist` declares no
    `NSPhotoLibraryUsageDescription`**, so `pickMultiImage` would terminate the
    app on iOS. Pre-existing rather than introduced here — there is no
    `NSCameraUsageDescription` either, so Phase 51's `PhotoCapture` has the
    same gap, and the port targets the Android app — but this phase adds a
    second `image_picker` entry point, so it is one more caller depending on it.
14. **The image-upload handler landed in the wrong commit.** `1868806`, whose
    message describes only the four smaller defects, carries
    `voices_uploader.dart` +253 and the new `voice_images.dart`; `7c97837`
    ("feat: upload a voice post's images to CouchDB") carries only the
    UI/picker half. Not rewritten, because the branch is already pushed and the
    integrator merges it — but anyone bisecting or reading the log for the
    image feature will look in the wrong place.
