# Phase 144 — the voices upload sweep and the missing author (Lane C)

Two items from Phase 140's *Reported, not fixed* list — 4 and 5, both of which
that lane had withdrawn from an over-strong earlier claim. The ground-truth
`parity-auditor` pass then found a third defect bigger than either, and it is
the one to read first.

## What landed

| Fix | Where | Class |
|---|---|---|
| A periodic voices upload sweep, on both sync paths | `dashboard_sync_provider.dart`, `background_entrypoint.dart` | the safety net Kotlin has and the port did not (Phase 134) |
| A send already on the wire is not re-enqueued | `voices_uploader.dart` | append replayed as a duplicate |
| Every locally authored voice names its author | `voices_provider.dart` | sync-in rewriting a locally-authored column |
| The session is awaited, not read | `voices_provider.dart` | `.valueOrNull` on an unwatched provider |
| **A composed community post is addressed to the community** | `voices_provider.dart` | **reachability — the writer produced no value the reader matches** |
| A team post carries `''`, not the author's planet code | `voices_provider.dart` | two port writers disagreeing about one field |
| `authorJson` drops a null-valued key instead of writing `null` | `voices_repository.dart` | faithfulness to Gson's `serializeNulls` |

**26 new tests** across three new files and two existing ones, counted from the
runner. **20 mutations, 20 caught** — the list is at the bottom. Two
`parity-auditor` passes at `effort: max` ran, one on the Kotlin ground truth
before implementing and one on the finished green code.

## The third defect: a community post nobody could see

Found by the ground-truth pass, verified by hand against the Kotlin, and
demonstrated failing before the fix.

`VoicesFragment.btnSubmit` (`ui/voices/VoicesFragment.kt:137-148`) builds
**four** keys before it calls `createNews`:

```kotlin
map["viewInId"] = "${user?.planetCode ?: ""}@${user?.parentCode ?: ""}"
map["viewInSection"] = "community"
map["messageType"] = "sync"
map["messagePlanetCode"] = user?.planetCode ?: ""
```

`VoicesScreen._compose` called `createPost(message)` with the message alone. So
`_viewInJson(id: null, …)` returned `"[]"`, and `isVisibleToUser` fell straight
through its `viewIn == null || viewIn.isEmpty` guard to `false` — the
`viewableBy == 'community'` shortcut above it does not fire either, because a
locally composed post has no `viewableBy`. **The post the user just wrote was
listed by nobody**: not in this app's community feed, and not on Planet, because
`serialize` omits the `viewIn` key entirely for an empty list, so the uploaded
document had no audience at all and a pull-back could not repair it.

**Two other readers key on the same entry, and both were wrong for a
port-composed post.** `VoicesAdapter.canShare` is
`news?.isCommunityNews != true` (`VoicesAdapter.kt:699`), so the port was
offering a "share to community" action on a post Kotlin already treats as being
*in* the community. And `getCommunityVoiceDates` — the challenge dialog's "post
five community voices" counter from Phase 81 — filters on the same predicate,
so **a user could post five voices from the port's own community screen and the
challenge would still read zero**. Neither was reachable from the defect's own
symptom; both are now pinned by *a composed post counts as a community voice*.

This is the Phase 113 shape exactly — *can the writer produce values the
reader's predicate matches?* — and the tell was the same one: every existing
community-feed test builds its rows through `cacheDocuments`, i.e. from a
server document, so nothing had ever asked the writer and the reader about each
other. `communityViewerIdentifier` was already in the file, three functions
above the writer that needed it, ported and used only by the *reader*.

Both halves of the identifier are interpolated unguarded, as Kotlin does: a
user missing a code writes `"@"`, `"learning@"` or `"@earth"`, and an empty or
`"@"` id is the planet-wide wildcard on both sides. So a guest-shaped account
still reaches everyone rather than nobody, which a `trim`-and-skip would have
broken. Pinned by *a user with no planet codes still addresses the community*.

## Job 1 — the sweep

**Kotlin has no write-time upload for a voice at all.** Composing one writes a
`news` row and stops; the document reaches the server when
`UploadManager.uploadNews()` next runs, from `AutoSyncWorker:130` and
`UserDataWorker:40`, off nothing the post did. `getNewsForUpload()`
(`VoicesRepositoryImpl.kt:36-48`) is `newsDao.getAll()` minus rows whose
`userId` starts with `guest` — that five-character prefix is the *only* filter:
no empty-message test, no `docType`, no `replyTo IS NULL`, no `chat` test.
Replies, comments and chat shares all go up on every sweep.

The port inverted the relation: `VoicesActions` enqueues at write time — better
when it runs — and nothing swept. `VoicesActions.queuePending` returns 0 when
`serverConfigProvider` is null, and every caller reports success regardless, so
a voice composed before the server was configured sat on the handset until the
same user happened to make some *other* voices write, because `queuePending` is
an unscoped sweep that then rescues it incidentally. That path is now a test —
*a voice composed with no server configured still goes out* — driven the way a
user reaches it rather than by seeding the end state.

`queuePendingVoices()` runs from `syncAll` immediately before
`queuePendingSubmissions()`, which is the order both Kotlin workers have
(`AutoSyncWorker:130` before `:136`; `UserDataWorker:40` before `:48`). It
queues only: the unscoped drain at the end of `queuePendingSubmissions` carries
these rows out in the same pass, and *syncAll delivers a voice nothing enqueued*
pins that coupling end to end rather than trusting the adjacency.
`sweepPendingVoices` is the headless half, called from `drainOutbox` and
deliberately not from `syncSteps` — those run only for an `autoSync` task, with
auto-sync enabled, and only when the interval is due, and
`BackgroundWorkCoordinator` cancels the `autoSync` job outright for a user who
turns auto-sync off, so a step there would never run for exactly the user most
likely to have an undelivered post.

Swallowed, and in its own `try`, following `UserDataWorker`'s per-arm
`runCatching` rather than `AutoSyncWorker`'s single shared `try` (where an
earlier arm throwing skips `uploadNews` silently — a Kotlin weakness, not a
behaviour to reproduce). It is not hypothetical: `queuePending` reads device
identity before it enqueues anything, and `PlatformDeviceIdentitySource.read`
rethrows on an engine with no channel and no primed cache.

### It cannot double-post — and one protection was missing

Three protections, and only the first is Kotlin's:

1. `markUploaded` stamps `_id`/`_rev` and clears `isEdited`, taking the row out
   of `pendingUploads` — the port of `markNewsUploaded`.
2. `OutboxRepository.enqueue` keys on `(uploadType, itemId)`, so a sweep over an
   already-queued row refreshes it rather than adding a second.
3. **New here:** `queuePending` now skips a row whose send is in flight.

The third was a real gap, not a precaution. `enqueue` deliberately puts an
`in_progress` row back to `pending` so a payload edited mid-flight is not lost,
and `markCompleted` is `deleteIfInProgress` — so the send that succeeds moments
later deletes nothing, the row survives `pending` with the same body, and the
next drain posts a **second** `news` document. That reset is right for derived
state, whose handler rebuilds; a voice with no `_id` is an append.
`SubmissionsUploader` and `AdoptedSurveysUploader` both guard with
`isInFlight`; `VoicesUploader` did not, and `dashboard_sync_provider` already
*asserted* the guard existed in a doc comment. Reachable before this phase (any
second voices write re-enqueues everything undelivered, including a row whose
POST is on the wire) and systematic with the sweep.

### The predicate divergence, decided rather than shipped silently

`getNewsForUpload()` returns every non-guest row and re-sends it on every sync.
Because `serializeNews` writes `_id`/`_rev` when non-null and `_bulk_docs`
defaults to `new_edits=true`, that is an *update*, not a duplicate — but it
churns a revision per post per sync. `VoicesRepository.pendingUploads` returns
only rows that were never delivered or have been edited since.

**Kept narrow, deliberately.** Every local mutation the port has sets
`isEdited` — `editPost`, `shareToCommunity`, the un-share branch of
`deletePost`, `toggleReaction` — so nothing a user can do leaves a changed row
outside the set. What the narrow predicate gives up is Kotlin's *incidental*
repair of a server-side divergence, which no reader on either side depends on.
The claim about those four mutations was a comment nothing pinned; it now has
a test (*every local mutation puts a delivered post back in the queue*), and
mutating either of the two it did not already cover turns it red.

## Job 2 — the author

A `news` document carries **no** top-level `userId` or `userName`. I read the
whole of `serializeNews` (`VoicesRepositoryImpl.kt:421-453`) to be sure: the
nested `user` object is the sole author identity, and
`buildNewsFromJson:386-389` reads the local row's `user`, `userId` *and*
`userName` back out of that one object — unconditionally, so a document without
it does not merely fail to set them, it **erases** what the row had. Kotlin sets
it on every write path, because all four go through `News.createNews:181`.

`VoicesRepository.authorJson` landed with the chat share in Phase 140 and the
other three writers never used it, so every ordinary post, team post and reply
the port authored uploaded anonymous and lost its author here at the next
sync-in. One argument each.

Tested as pairs, never as halves: each test drives the writer, takes the
document the **outbox is actually holding**, and feeds that back through
`cacheDocuments`. The erasure test additionally calls `markUploaded` first —
without a server `_id` on the local row a pulled document lands *beside* it
rather than over it, so an author-less pull looks harmless until the post has
actually been delivered, which is the only state that matters.

### The session read, found while wiring it

All four `VoicesActions` writers read `ref.read(sessionProvider).valueOrNull` on
a provider this file never watches, so the user was null until something else
resolved it and the composed post was dropped with no error, no snackbar and no
row — latent only because the router holds a `ref.listen`. Now awaited, with the
`await` inside the enclosing `try`, because a future can reject where
`valueOrNull` could not.

**My first draft of that comment named the wrong screen**, and the
implementation-audit pass caught it: it said `TeamVoicesScreen` touches
`sessionProvider` nowhere at all. It does, transitively and load-bearingly — it
watches `teamMembershipsProvider`, which watches the session, and its compose
FAB only renders once a membership resolves. The screen with the live window is
`VoicesScreen`, whose FAB is **ungated** and renders while
`communityFeedProvider` is still loading. Corrected at the code. A citation is
not a reading, even when the citation is to the port.

## `messagePlanetCode` on a team post: `''`, not the author's

The brief asked whether `user.planetCode` was defensible in the interim. It is
not, and the port had already decided otherwise once.

`TeamsVoicesFragment.kt:74-81` writes `team?.teamPlanetCode ?: ""` — the
**team's** planet, not the author's. `MyTeam.kt:86` reads that field with
`JsonUtils.getString`, which returns `""` for an absent key, so a team document
that omits it yields `""` in Kotlin too. They coincide only for a team created
on the user's own planet (`TeamsRepositoryImpl.kt:170,177,699`); for a team
pulled from a parent or sibling planet they differ, and the port was stamping a
planet code it had no basis for. `''` is the faithful interim value, it is the
only value the port can produce without the `teamPlanetCode` column (Lane B's
`tables.dart`, not mine), and it is what the port's own chat-share writer
already sends for exactly this reason (`chat_repository.dart:306-310`). Phase
140's complaint that the port had "two writers addressing the same team with two
different values for one field" is closed by making the second agree with the
first.

## Reported, not fixed

Each names the file, the change, and why it was not made here.

1. **An enterprise voice post is labelled `team`.** Kotlin writes
   `getEffectiveTeamType()` (`BaseTeamFragment.kt:94-96`) — the team's `type`,
   so `"team"` **or `"enterprise"`**; the port hardcodes `'team'`. The fix is a
   `teamType` argument on `createTeamPost` (my file) passed from
   `lib/ui/teams/team_voices_screen.dart:89-101` (**not my file**), which
   already has the `team` in scope and `TeamRow.type` on it. Adding the
   parameter without the caller would ship a dead argument, so both halves want
   one lane.
2. **`addComment` writes an unauthored row, and the sweep now delivers it
   reliably.** `voices_repository.dart:844` claims to port
   `TeamsRepositoryImpl.addComment`; **there is no such Kotlin method** —
   `grep -rn "fun addComment" app/src` is empty, and `messageType = "comment"`
   appears nowhere in the Kotlin (Phase 74 records inline comments as a
   port-original from unmerged issue #15112). The row carries no `user`, and
   its caller, `lib/ui/teams/inline_comments.dart:131` (**not my file**), never
   queues it. **Decided, not overlooked:** comments stay in `pendingUploads`.
   They were already uploaded incidentally by the unscoped write-time
   `queuePending`, so the sweep changes reliability, not the class of document
   reaching CouchDB; excluding them would make comments device-local, a bigger
   behaviour change with no ground truth to check it against. The author gap is
   one argument on `addComment` plus one at that call site — the same shape as
   Job 2, in a file this lane does not own. The stale doc comment should go with
   it.
3. **`voice_thread_screen._reply` has the `.valueOrNull` defect this phase fixed
   four instances of.** `lib/ui/voices/voice_thread_screen.dart:67` reads the
   session and returns early; the screen does not watch it. The reply is now
   safe *inside* `VoicesActions.postReply`, but the screen's own early return
   still drops the tap before it gets there. One-line fix in a file this lane
   does not own.
4. **`authorJson` omits `iterations`, which `UserEntity.serialize()` writes.**
   `UserRow.iterations` exists (`tables.dart:31`) and `UserMapper` already ports
   the blank/NaN-to-10 fallback (`user_mapper.dart:257`). Deliberately left out
   rather than added: it is a PBKDF2 parameter and a voices post is a public
   document, so adding it wants the same explicit judgement Phase 140 applied to
   the credential branch, not a silent completion of the field list. Recorded so
   "matches `serialize()` field for field" is not read as exhaustive.
5. **Three duplicate/loss vectors in Kotlin's `uploadNews()` that the port must
   not copy**, all found by the ground-truth pass:
   (a) `UploadManager.kt:485-487` pairs responses to documents **positionally
   and loops over the response length**, so a short response silently leaves the
   tail unmarked and the next sweep re-creates them — `uploadTeams` ten lines up
   detects exactly this and logs it;
   (b) `RetryQueueWorker.kt:208-233` re-POSTs a failed news create and **never
   writes the id back**, so a post composed offline can reach CouchDB twice;
   (c) a per-document `{"error":"conflict"}` is queued as retryable, the retry
   PUTs the same stale `_rev`, and `RetryQueueWorker.kt:234-238` treats the 409
   as "already synced" — **the edit is discarded with a success log**.
   The port's outbox has none of these shapes. Worth a line in
   `docs/kotlin-to-flutter-migration.md` (**Lane A's file**) so a future harvest
   does not port them as parity.
6. **`markNewsUploaded` does not write back the message it uploaded.**
   `uploadNews` appends `![](resources/<id>/<file>)` per image to the *outgoing*
   message only (`UploadManager.kt:426,460,464`); the write-back
   (`VoicesRepositoryImpl.kt:50-69`) sets `imageUrls`, `_id`, `_rev` and
   `images` and leaves `message` alone, so a sweep before the next pull re-PUTs
   the stripped message over the server's. A Kotlin bug; the port has no image
   upload path yet, so there is nothing to diverge from — recorded before
   someone ports the asymmetry along with the feature.
7. **`uploadNewsActivities()` is dead code in Kotlin.** `uploadNews` ends by
   calling it, but `NewsLogDao.insert` has **zero callers** in `app/src/main` —
   the `news_log` table is never written. Do not build one for the port.
8. **The port prunes `news`; Kotlin never does.**
   `voices_repository.dart:1057` runs `deleteNotIn(docIds)` after a complete
   walk, where `TransactionSyncManager.kt:220-223` calls `insertNewsList` and
   nothing else — there is no delete on that table anywhere in the Kotlin.
   `NewsDao.deleteNotIn` spares rows with a null `docId`, so an undelivered post
   is safe; a delivered-but-locally-edited row whose server copy was removed is
   not. Left alone: removing a prune is a behaviour change of its own and this
   lane's brief is the delivery path. Belongs in the deviations list.
9. **The `sharedBy` asymmetry recorded in two places does not exist.**
   `docs/kotlin-to-flutter-migration.md:1183-1184` and
   `lib/data/local/news_mapper.dart:87-89` both say `sharedBy` is "read from the
   nested `news` object but written at the top level by `News.createNews`".
   `createNews:165` sets a **column**, not a document key; the only place it
   reaches a document is `serializeNews`'s nested object (`:449`), and
   `buildNewsFromJson` reads it from the same nested object (`:417`). Kotlin is
   symmetric. The port's behaviour is right and only the stated reason is wrong
   — which is worth correcting, because someone could "fix" a non-existent
   asymmetry on the strength of it.
10. **Phase 140's items 1, 2, 3 and 6 are still open**, and all four are in
    files this round assigned elsewhere (`tables.dart`, `app_database.dart`,
    the migration doc). Item 1's `teamPlanetCode` column is what would let
    `createTeamPost` and the chat share both send the *right* value rather than
    agreeing on `''`.

## Mutations run

Each was applied alone to green code and reverted; all 20 were caught, and the
test that caught each is named. Two of them exist because the mutation found a
claim nothing pinned (13 and 14/15), which is the Phase 122 practice.

| Mutation | Caught by |
|---|---|
| the sync-path sweep is not called from `syncAll` | 4 sweep tests |
| the in-flight guard is removed | a re-enqueue mid-flight does not post a second voice |
| the headless sweep is not called from `drainOutbox` | the headless path calls the voices sweep |
| the headless sweep moves behind the drain | the same |
| the sync-path sweep drops its `try`/`catch` | a sweep that throws does not fail the sync |
| the headless sweep drops its `try`/`catch` | a throwing sweep is swallowed |
| identity is read with nothing to queue | the sweep reads device identity only when it has rows |
| `createPost` passes no author | 3 author tests |
| `createTeamPost` passes no author | a team voice post names its author |
| `postReply` passes no author | a reply names its author |
| the session is read rather than awaited | 4 tests |
| `authorJson` carries the credential branch | the author object carries no credentials |
| `queuePending` reports the pending count | a send already on the wire is neither counted nor re-queued |
| `toggleReaction` does not flag the row | every local mutation puts a delivered post back in the queue |
| the un-share branch does not flag the row | the same, plus an existing community test |
| `authorJson` writes explicit nulls | a field the user has not filled in is absent, not null |
| `createPost` does not address the community | 3 community tests |
| `createPost` keeps the default `messageType` | the composed post addresses the community on the wire |
| `createTeamPost` claims the author planet code | a team post carries no planet code it cannot know |

## Files touched

`lib/repository/voices_repository.dart`, `lib/repository/voices_uploader.dart`,
`lib/providers/voices_provider.dart`, `lib/providers/dashboard_sync_provider.dart`,
`lib/background_entrypoint.dart`, and the tests for them.
`lib/core/**` was **not** touched: `OutboxDrainScope` lives at
`lib/ui/outbox_drain_scope.dart` and needed no step — the drain it triggers is
unscoped, so it already carries whatever the sweeps queued.
No schema change, no `app_database.dart`, no `tables.dart`.
