# Phase 142 — harvest master, and the chat-share deviations (Lane A)

Two jobs:

1. Harvest the 40 commits `master` drifted by (`0650e67..af7db0d`, versionName
   0.70.0 → 0.70.15).
2. Record the six chat-share deviations Phase 140 could not add itself in
   `docs/kotlin-to-flutter-migration.md`.

Outcome: **one Follow ported**, three triage verdicts corrected (right answer,
wrong reason), one reachability finding that changes what the Follow *means*,
and the six deviations written up after re-deriving each from source.

## The batch

Every one of the 40 subjects is `smoother X` or `less X is more`. That is the
house style for a refactor and says nothing about blast radius: Phase 126 found
a new sync step and a wire-format change on every uploaded document under
exactly that phrasing, and Phase 133's 39-commit batch of near-identical titles
still held two real Follows. So `harvest-triage` ran a first pass over two
halves of twenty, and then **every commit was re-read by hand** — not only the
Follows, because the failure mode here is a *wrong* "no port impact", and that
verdict is the one nobody re-checks.

### Follow (1)

**`9e402fb` — `all: smoother markdown utils image url resolving`.** The whole
diff is one character pair: `MarkdownUtils.prependBaseUrlToImages` went from
`result.append("<img src=$fullUrl width=$width height=$height/>")` to
`src=\"$fullUrl\"`. Unquoted, a path containing a space runs into the following
`width=` attribute and the tag is mis-parsed — upstream added a
`prependBaseUrlToImages_handles_url_with_spaces` test to say so.
`flutter/lib/core/utils/markdown_links.dart:49` was doc-commented as the
line-for-line port and still emitted the pre-fix form.

Ported: the quote, the doc comment that states the emitted shape, the five test
expectations, and the upstream spaces case.

**But the Follow means less than it looks like, and that is the finding.**
Neither `prependBaseUrlToImages` nor `extractImageLinks` has a **caller
anywhere in `flutter/lib`** — the only occurrences outside the file are two
comments (`take_course_screen.dart:632`, `course_markdown.dart:15`). The port
renders a step description with `CourseMarkdownBody(data: step.description!)`,
which handles the `![alt](path)` spans itself and fetches a relative path as
authenticated bytes (Phases 76–77), so it never needs the rewrite. The change
is therefore *alignment*, not a user-visible fix, and it is labelled that way at
the code and in the commit message. What it exposed instead is a genuine gap,
recorded under *Reported, not fixed* item 1.

### Verdicts that were right for the wrong reason (3)

The brief asked for this specifically — "a correct verdict reached by a wrong
route is one coin-flip from a wrong one" — and it paid for itself three times.

**`1004e90` — `life: smoother health examination dao batch updating`.** The
first pass called the new null-rev branch equivalent to the old one because the
old query used `COALESCE`. It did not. `HealthExaminationDao`'s single-row
marker is `UPDATE health_examinations SET _rev = :rev, isUpdated = 0 WHERE _id
= :id` with **no `COALESCE`**, so `markUploaded(id, null)` wrote `_rev = NULL`
— erasing the revision a CouchDB document needs for its next update. The
post-fix code routes null-rev ids to a new `SET isUpdated = 0 WHERE _id IN
(:ids)` and leaves `_rev` alone. That is a real fix in the stuck-write class,
not a batching optimisation. The reason it is not a Follow: the port's
`health_uploader.dart:62-68` returns a `NetworkError` when the response `rev`
is not a `String` and never reaches `markUploaded`, so it cannot write a null
revision at all. (Its behaviour then differs from the post-fix Kotlin — the port
retries where Kotlin clears `isUpdated` and keeps the old `_rev` — and the
port's is the safer of the two.) The sibling commit `f271072`, notifications,
*did* use `COALESCE(:rev, rev)` pre-fix and genuinely was equivalent; conflating
the two is what produced the wrong reason.

**`83ebafe` — `sync: smoother retry repository dao operating`.** The first pass
said "same outcome for the same inputs". Not so: the commit unifies **two**
Kotlin failure paths that disagreed. `RetryRepositoryImpl.markFailed` set
`status` to `abandoned`-or-`pending` explicitly; `updateAttempt` set
`abandoned` only when attempts were exhausted and otherwise **left the status
untouched**, so a row that `markInProgress` had moved to `in_progress` stayed
there and `RetryDao.getPending()` (`WHERE status = 'pending'`) skipped it until
`recoverStuckOperations()` (`SET status='pending' WHERE status='in_progress'`)
swept it. Work was delayed, not lost, but the two paths were not the same
function. The new single `UPDATE` always writes `abandoned`-or-`pending`. No
port impact because `OutboxRepository.recordFailure` (`outbox_repository.dart:194-206`)
already writes `abandoned ? statusAbandoned : statusPending` unconditionally.
It also advances `nextAttemptAt` on an abandoned row where the post-fix Kotlin
preserves the old value; immaterial, because an abandoned row is never selected
by time and `enqueue` never reuses one.

**`5589dfc` — `enterprises: smoother reports name resolving`.** Read as an
extraction of the same call. It is not an extraction at all:
`BaseTeamFragment.getEffectiveTeamName()` **pre-existed** (`:90-92`, unchanged
by this commit, already used by `TeamDetailFragment` and `TeamsVoicesFragment`),
and the commit switches three `EnterprisesReportsFragment` call sites onto it
from `teamsRepository.getTeamNameFromPrefs()`. Those read different things: a
*global preference* versus `requireArguments().getString("teamName")?.takeIf {
it.isNotBlank() } ?: team?.name ?: ""`. So the CSV filename, the CSV content
(`exportReportsAsCsv(teamId, name)`) and the report card title stop depending on
whichever team was last written to prefs. No port impact:
`team_reports_screen.dart:123-127` already uses `team?.name ?? ''`, the resolved
team row — the tail of the post-fix chain. The port has no nav-argument
preference because it resolves the team directly rather than through Kotlin's
tab pager.

### Two Kotlin behaviour changes the port already matched (2)

Both are mime-type detection, and in both the port already implements the
*post-fix* method — worth stating, because "the port already does the right
thing" is a different verdict from "nothing changed".

- **`0d0b62c`** — `UploadRepositoryImpl.uploadAttachment` swaps
  `file.toURI().toURL().openConnection().contentType` (byte-sniffing through a
  `file://` connection) for `URLConnection.guessContentTypeFromName(file.name)`.
  The port's uploaders are already name-based: `personals_uploader.dart` uses
  `lookupMimeType`, `teams_uploader.dart` has `_contentTypeFor(imageName)`, and
  `achievements_uploader.dart` / `submit_photos_uploader.dart` hardcode theirs.
- **`a182edd`** — the same swap for a voice post's image attachment, to
  `FileUtils.getMimeType(fileName) ?: "application/octet-stream"`. Filed as
  **not ported** rather than followed, because the port has no voices
  attachment upload to attach a mime type to. See *Reported, not fixed* item 2.

### No port impact (34)

Read in full, not by `--stat`. Grouped by why.

**Provably equivalent collection rewrites.** `9411c5a`
(`map{}.filter{isNotBlank}` → `mapNotNull{ takeIf{} }`), `3407901`
(`filter{}.mapNotNull{}` → one `mapNotNull{ if … else null }`), `c3acfe3`
(`(questions?.size ?: 0) > 0` → `!questions.isNullOrEmpty()`), `13a77a2`
(`mapNotNull{}.distinct()` → `mapNotNullTo(LinkedHashSet()).toList()`; both keep
the first occurrence in encounter order), `fc80af6` (the same shape in the chat
share dialog's expanded-group set — checked specifically because
`ChatShareTargetItem.title` is non-null `String`, so `mapNotNull` cannot drop an
entry `map` would have kept), `4f76988` (two grouping passes → one
`HashMap` accumulator; `stats.latestVisit?.let{max} ?: logTime` reproduces
`maxOfOrNull { it.time ?: 0 }`), `c54d074` (hoists `news?.imagesArray` into a
local before the emptiness check), `e00b131` (`findHtmlCoverImage` streams
instead of materialising the list; the hint match is still the first in
`walkTopDown` order and the size fallback still keeps the first maximum, because
`length > maxBytes` is strictly greater where `maxByOrNull` also keeps the
first).

**Capacity hints and caches with identical output.** `b8f6eeb`
(`mutableListOf()` → `ArrayList(succeeded.size)`), `f6bd405` and `cff47d8`
(cache a `getString` / a format pattern instead of re-resolving per bind),
`4cccda5` (memoise `getModelsMap()`'s JSON parse, cleared in `onDestroyView`).

**Pure delegation and DI seams.** `069341a` (`apiInterface.postDoc(header,
"application/json", url, body)` → `uploadRepository.postUpload(url, body)`,
which is that exact call — read at `UploadRepositoryImpl:40-45`), `e0dc808`
(`UploadManager.uploadTeams`'s body moved into a new `TeamsUploader`; diffed
old against new, identical control flow, same 500-threshold retry, same batch
size), `9245ac9` (injected `@ApplicationScope` instead of
`MainApplication.applicationScope`), `18c474b` (a `ServerReachabilityProvider`
seam instead of the static call; same 15s timeout, same primary/alternative
order, same `?: false`), `5f410ba` (`UserSessionManager` → `UserRepository`,
where the former's `getUserModel()` was a one-line delegate to the latter),
`9468627` and `f536ab6` (an injectable `now`), `f6557b0` (`getOlePath(context)`
computed in the fragment and threaded in), `4152484` (`VersionUtils.getVersionName(context)`
→ `BuildConfig.VERSION_NAME`).

**Extractions whose arithmetic was checked, not assumed.** `aa1e552` — the new
`reportTotals(report)` is `sales + otherIncome`, `wages + otherExpenses`, the
difference, and `profitLoss + beginningBalance`, which is what the adapter
computed inline. This one was checked rather than skimmed because Phase 99
ported all nine of that card's value rows. `1932832` — `effectiveId` is
`_id?.takeIf { it.isNotEmpty() } ?: id`, identical to the three inline
conditionals it replaces.

**A mapping table that had to be compared, not counted.** `3c2a859` moves the
notification group header from a pre-resolved `label: String` to a
`labelResFor(type): Int` looked up at bind time. Both the removed
`typeLabelFor` and the added `LABEL_RES_BY_TYPE` were read side by side: the
same seven types map to the same seven string resources, the same
`notification_group_other` fallback, the same `lowercase(Locale.ROOT)`.

**Guard removals proved to be no-ops.** `e1b2ea1` drops
`if (leadersString.isNotEmpty())` around `parseLeadersJson`, which try/catches
`JSONException` and returns an empty list — so `""` yielded `emptyList()` either
way. `b89fe9e` deletes an `if (!processTimes.containsKey(startKey)) return`
subsumed by the `?: return` on the next line.

**`android.text.TextUtils.isEmpty(x)` → `x.isNullOrEmpty()`.** `ec90931` and
`c2566d0`. Identical semantics. Named here because the name collides with the
port's `lib/core/utils/text_utils.dart`, which is an unrelated accent-folding
helper (Phase 78) — neither diff touches search, ranking or normalisation.

**Logging and UI state shape.** `8b9e45f` (`printStackTrace()` → `Log.e`, six
sites, no control flow moved), `e27f0fc` (five `StateFlow`s consolidated into
one `SubmissionDetailUiState` with the same defaults and the same
`map`/`stateIn` chain), `7f21245`
(`lowercase(Locale.getDefault()).endsWith(".gif")` →
`endsWith(".gif", ignoreCase = true)`).

**A real Kotlin fix the port already has.** `af7db0d` — the achievement id was
`userModel?.id + "@" + userModel?.planetCode`, string concatenation on nullable
receivers, which could save an achievement under the literal `"null@null"`; the
fragment now returns early when the id is null or blank.
`achievements_provider.dart:39` already guards with
`if (user == null) return`. **And the `.valueOrNull` on that line was checked
rather than assumed to be the documented trap**: `achievementEntryProvider`
watches `sessionProvider`, and `edit_achievement_screen.dart:250` watches
`achievementEntryProvider`, so the session is resolved before the save button
can be tapped. Not a defect. (Recorded because the reflex of grepping a screen
for `.valueOrNull` is right, and the reflex of assuming every hit is the bug is
not.)

**`b9baf4c` — the one that touches the chat-share reader.** `Date().time` →
`System.currentTimeMillis()` twice, and `News.createNews`'s
`if (map.containsKey("news")) { val newsObj = map["news"] … }` becomes
`map["news"]?.let { newsObj -> … }`. Those differ for a key present with a null
value: the old code entered the block and passed null to
`gson.fromJson`; the new one skips it. Unreachable from either app —
`buildShareMap` and `buildChatShareMap` always write the key — and the port's
`createFromShareMap` handles the case a third way (`_decodeObject(...) ?? {}`,
so the `news*` columns get `''` rather than staying null). Left alone: no
caller can produce the input.

## What was merged

`origin/master` merged clean into the branch — **no conflicts**, and master
touched nothing under `flutter/` or `docs/`, so no lockfile or generated-source
regeneration was needed. The merge does bring 94 files of real `app/` change
onto the branch, so `build.yml` and `test.yml` run on the push. That is the
`paths-ignore` filter working as designed, not leaking.

**No version bump was needed and none was made.** Master is at `0.70.15`; the
port declares `0.70.11+7011` and `ConfigurationsRepository.defaultAppVersion =
'0.70.11'`. Same minor, four patches behind, which is exactly what
`test/version_parity_test.dart` is written to tolerate — `automerge.yml` moves
the Kotlin patch hourly and the first cut's equality rule turned every
pull-request run red. The test was **not** tightened.

## Job 2 — the six chat-share deviations

Written up in `docs/kotlin-to-flutter-migration.md`, in the *deliberate
deviations* list where a reader looks, immediately before `## Write-back`.

Each was re-derived from source rather than transcribed from
`PHASE_140_NOTES.md`, because three consecutive rounds a brief written from the
previous round's notes carried an error a lane caught only by reading the
ground truth. All six held. What the re-derivation added:

- The localised `viewInSection` is confirmed at
  `ChatHistoryAdapter.kt:62-66,165-172` — `context.getString(R.string.teams)`
  and its siblings are what reach `buildShareMap`.
- **The null-conversation claim was settled by running Gson, not reading it.**
  The hand-off says the writer emits the string `"null"` and the reader's
  `fromJson("null", JsonArray::class.java)` raises `JsonSyntaxException`, caught
  by `News.kt`'s inner `catch`, leaving the column null. Reading the Gson source
  suggests instead an uncaught `ClassCastException` from
  `Primitives.wrap(classOfT).cast(...)` over a `JsonNull`, which would escape
  both `catch (e: JsonSyntaxException)` blocks and crash `createNews`. That
  reading is **wrong**: compiled and run against Gson 2.10 and 2.13.1, the
  actual throw is `JsonSyntaxException: Expected a com.google.gson.JsonArray but
  was com.google.gson.JsonNull; at path $`. The hand-off is right and the
  inference is not. (The same run also confirms Phase 140's other Gson claim:
  `Gson()` is html-safe by default, so a title of `2+2=4` is encoded as
  `{"title":"2+2\u003d4"}` — there is no literal `=` left in the payload, which
  is why `News.kt:187`'s `newsObj?.replace("=", ":")` is a no-op in Kotlin
  rather than the corruption it looks like. Dart's `jsonEncode` emits `=` raw,
  so porting that line would have rewritten every `=` in the user's own title
  and transcript.)
- The dropped tap is `ChatHistoryFragment.kt:166-170` —
  `if (chatId.isNotEmpty() && viewInId.isNotEmpty()) { share }`, **no else**.
- The always-rendered community row is `shareDataMap`, a static `mapOf` built in
  `by lazy` (`:60-68`), reached at `:172` with `shareTargets.community`
  whether or not it resolved; a null target makes `viewInId` `""`, which the
  gate above then discards. So the full four-step flow ends in silence.
- The unchanged checkmark cache is `ChatHistoryFragment.kt:225-233`:
  `sharedNewsMessages = sharedNewsMessages + result.news` and then
  `adapter.updateCachedData(user, sharedNewsMessages, sharedViewInIds)` — the
  third argument is not recomputed.
- Kotlin has no success snackbar: `ShareChatResult.Shared` calls
  `notifyChatShared`, which is `notifyItemChanged(position, PAYLOAD_CHAT_SHARED)`
  (`ChatHistoryAdapter.kt:84-89`). Only `AlreadyShared` shows a message — the
  *refused* share speaks and the successful one is silent.

## The gate

```
dart run build_runner build          # no Drift change in the merge; run anyway
dart format --output=none --set-exit-if-changed lib test   # 477 files, 0 changed
flutter analyze                      # No issues found!
flutter test                         # 2574 tests, all pass
```

Kotlin CI runs too, and must: the merge brings 94 files of `app/` change onto
the branch, so both flavours build and `testDefaultDebugUnitTest` runs across
its two shards.

## The chat-share ground-truth audit (`parity-auditor`, `effort: max`)

Aimed at my own six claims *before* they were trusted, because a hand-off note has
seeded an error into the next round three times running. **All six held.** Five needed
amending anyway, and the amendments are in the tracker:

- **Claim 1 named one consequence where there are two.** Besides `isVisibleToUser`, the
  challenge dialog's "post five community voices" tally reads the section value —
  `NewsDao.countDistinctCommunityVoiceDates`/`…ForUser` are
  `WHERE … AND viewIn LIKE '%"section":"community"%'`, reached through
  `getCommunityVoiceDateCount`. So a localised share is uncounted there too, in **both**
  apps, since the port's `isCommunityNews` compares the same literal. My sentence
  "nothing anywhere reads the value except that `"community"` comparison" was true to the
  letter and misleading in effect; corrected to name both.
- **Claim 2 gained two limits and a better provenance.** The audit re-ran the Gson probe
  independently on **2.14.0**, the version `libs.versions.toml` actually pins (I had run
  2.10 and 2.13.1) — same `JsonSyntaxException`. It also established that the *null*-list
  premise may be unreachable from Kotlin's own writers, since
  `insertChatsBatchInternal` and `addConversation` both always assign a list; the
  reachable case is the *empty* list, which is byte-identical in both apps. And "same end
  state" is true of the local column but **not the wire**: `serializeNews` uploads
  `"conversations": null` where the port uploads `[]`.
- **Claims 3, 5 and 6 were understated.** `ChatShareOutcome.unavailable` has four routes,
  not the one I named. Kotlin's post-share branch is not merely stale but **wholly
  inert** — the `newsList` it writes is never read and the rebound row holds no shared
  state — and re-entering the screen refreshes the map too, because `refreshChatSignal`
  is a `MutableSharedFlow(replay = 1)` emitting in `init`. So the successful share has no
  observable effect at all.
- **A seventh deviation existed and was recorded nowhere but the Dart.**
  `News.kt`'s `newsObj?.replace("=", ":")`. Now in the tracker, because an omission with
  no note is the one most likely to be mistaken for an oversight.

### One audit finding overturned

The audit reported that the port's challenge tally "would count a team-only post toward
*post five community voices*", because `NewsDao.getInTimeRange` in
`app_database.dart` filters on top-level and time window with no community predicate
where the Kotlin's `countDistinctCommunityVoiceDates` has the `viewIn LIKE`.

**That is wrong, and it is wrong in the direction this project keeps warning about** — a
chain read to its conclusion in one layer without walking the next.
`VoicesRepository.getCommunityVoiceDates` filters the returned rows with
`isCommunityNews(row)` before adding a date, and that helper parses `viewIn` and compares
`section` case-insensitively against `community`. The predicate is not missing, it moved
to Dart — and parsing beats the Kotlin's raw substring `LIKE`. No defect. Recorded here
because an audit's finding gets the same treatment as a brief's claim.

Verifying it did surface something real but much narrower, which is in *Reported, not
fixed* below: the two apps bucket a post into a *day* differently.

## Reported, not fixed

Each names the file, the change, and why it was not made here.

1. **The port collects no markdown image links and pre-downloads none.** Kotlin
   calls `DownloadUtils.extractLinks` from three repositories —
   `CoursesRepositoryImpl:670` and `:684` (course and step descriptions),
   `VoicesRepositoryImpl:394`, `TeamsRepositoryImpl:1183` — to queue a
   markdown image for download, and renders it later from a `file://` base
   through `prependBaseUrlToImages` (`CommunityServicesFragment:52`,
   `VoicesAdapter:448`, `CourseStepFragment:125`, `CourseDetailViewModel:65`).
   The port ports both helpers and wires **neither**; `CourseMarkdownBody`
   fetches a relative path as authenticated bytes at render time, which works
   online and not offline. Grepped for an alternative path under
   `flutter/lib/repository`: there is none. This is an offline-first app, so it
   is worth its own slice rather than a corner of a harvest. Severity: medium —
   images are missing offline, nothing is lost or corrupted. Note the direction
   of the claim: Kotlin genuinely does pre-download and the port genuinely does
   not; what is *not* claimed is that the port's images fail online.
2. **A voice post's image never reaches the server.** `a182edd` changes the mime
   type Kotlin sends when PUTting a voice image as a CouchDB attachment; the
   port's `flutter/lib/repository/voices_uploader.dart` (**Lane C's file this
   round**) POSTs the news JSON with an `images` array of pending references and
   never PUTs the bytes at all. So the upstream fix has nothing to align to, and
   the gap underneath it is larger than the fix. Fix: the two-step attachment
   upload, with `_contentTypeFor`-style name-based detection to match post-fix
   Kotlin.
3. **A dartdoc in `flutter/lib/providers/chat_provider.dart` states something
   Phase 140's own notes withdrew.** `ChatShareActions`' header (`:408-411`)
   ends "…and the reason a shared chat cannot sit undelivered on the device".
   Phase 140 withdrew exactly that sentence in its item 4: `share` skips the
   enqueue when `serverConfigProvider` is null and still reports success, and
   nothing sweeps `VoicesRepository.pendingUploads`. Comment-only change, one
   sentence, in a file no lane owns this round — not taken because "one file,
   one owner" is cheap to honour and a stale comment costs the next lane only if
   it is believed. Suggested replacement: "…the shape `VoicesActions` uses. It
   is not a delivery guarantee: see `PHASE_140_NOTES.md` item 4."
4. **The challenge tally buckets a post by UTC day; Kotlin uses the device's local
   day.** Kotlin counts distinct days in SQL with
   `strftime('%Y-%m-%d', time / 1000, 'unixepoch', 'localtime')`; the port's
   `VoicesRepository._formatDate` builds the date from
   `DateTime.fromMillisecondsSinceEpoch(millis, isUtc: true)`. A community post made near
   midnight is therefore attributed to different days by the two apps, so the "posted on
   five distinct days" progress can differ by one. Fix: format in local time in
   `_formatDate` (`flutter/lib/repository/voices_repository.dart`, no lane owns it).
   Severity: low, and stated with a limit — the divergence in bucketing is certain, but I
   have not constructed a case where the *count* actually crosses the five-day threshold.
5. **Two stale citations in `app_database.dart`'s challenge queries** (**Lane B's file**,
   so not touched). `getInTimeRange`'s dartdoc says "Port of `NewsDao.getInTimeRange`",
   and Kotlin has no such method — the counterpart is
   `countDistinctCommunityVoiceDates`. The same comment describes the result as "all
   top-level *community* voices", which its query does not implement; the community
   filter is the caller's `isCommunityNews`. And `getCommunityVoiceDates`' comment says
   "The Kotlin filters `isCommunitySection` in memory after the DAO query", which the
   current Kotlin does not — it filters in SQL. Behaviour is correct in all three cases;
   only the comments mislead, and a comment that names a Kotlin method which does not
   exist is how a future audit reaches a wrong verdict.
6. **Phase 140's items 1, 2, 4, 5 and 6 are still open** and all land in other
   lanes' files: `teamPlanetCode` on `Teams` (Lane B's `tables.dart` +
   `app_database.dart`, and a schema bump this lane has no number for),
   `NewsDao.getByNewsId`/`getPlanetMessages` (Lane B's `app_database.dart`), the
   missing periodic voices upload sweep and `authorJson` on `createPost` /
   `createTeamPost` / `postReply` (Lane C's `voices_provider.dart` and
   `dashboard_sync_provider.dart`). Item 3 is what this lane closed.

## Checked and found to be nothing

Recorded so the next round does not spend the same time twice.

- **`couchId ?? id` versus Kotlin's new `effectiveId`.** `1932832` defines
  `effectiveId` as `_id?.takeIf { it.isNotEmpty() } ?: id` — empty is treated as
  absent. The port's eleven `couchId ?? id` sites use `??`, which would keep an
  empty string, and Phase 140 had just fixed the *null* sibling of this in
  `chatShareTargetsProvider`. It is not a divergence: `user_mapper.dart:56`
  writes `couchId: Value(couchId.isEmpty ? null : couchId)`, normalising empty
  to null at the only place a `users` row's `couchId` is written, so `??` and
  `takeIf { isNotEmpty }` agree on every reachable value.
- **`achievements_provider.dart:39`'s `ref.read(sessionProvider).valueOrNull`.**
  Not the documented trap here; the enclosing screen watches a provider that
  watches the session. Reasoning above under `af7db0d`.
