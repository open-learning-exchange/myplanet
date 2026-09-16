# Phase 153 — markdown image prefetch (Lane D)

Brief: `PHASE_142_NOTES.md` § *Reported, not fixed* item 1. Kotlin collects the
images a markdown description references as it ingests the document and renders
them later from a local `file://` base; the port ported both helpers
(`extractImageLinks`, `prependBaseUrlToImages`) and wired **neither**, so
`CourseMarkdownBody` fetched a relative image as authenticated bytes at render
time — which works online and not offline, in an offline-first app.

**Outcome: the courses half shipped with both halves wired and joined by one
key derivation; the voices and teams halves deliberately not built, because the
port has no screen that would render them.** That second sentence is the phase's
main judgement and the rest of these notes exists to justify it.

## What the ground-truth audit changed before a line was written

The mandatory pre-implementation `parity-auditor` pass at `effort: max`
corrected **four premises in the brief I was given**, two of which would have
gone straight into the code:

1. **"Does every sync re-queue the whole accumulated history?"** — the *pref* is
   cleared at the start of every sync (`SyncManager.kt:93`,
   `removeKey("concatenated_links")`). What actually accumulates is the two
   in-memory collectors that repopulate it: `MyCourse.concatenatedLinks`
   (`MyCourse.kt:77`, a companion `HashSet`, process-lifetime) and
   `VoicesRepositoryImpl.concatenatedLinks` (`:33`, an `ArrayList` in an
   `@Singleton`, so it accumulates **duplicates** too). A port that mirrored
   only "clear the pref at sync start" would still have the growth. Different
   bug, different fix.
2. **The API-31 gate on the teams path** (`TeamsRepositoryImpl.kt:1186`) is not
   policy. `DownloadUtils.openDownloadService` carries `@RequiresApi(S)`, which
   is a **lint annotation with no runtime effect**, and its body already
   branches correctly on every API level (`DownloadUtils.kt:210-220`). Six other
   call sites invoke it with no version check at all. The one runtime gate in
   the codebase reads as an artefact of silencing that annotation.
3. **`FileUtils.getIdFromSegments` names the *directory*, not the file** — the
   file and any sub-path come from `getResourceRelativePathFromSegments`, a
   different slice of the same segment list.
4. **A missing file does not render "nothing".** Markwon draws the replacement
   text, and because `prependBaseUrlToImages` emits no `alt` attribute the
   replacement is `HtmlEmptyTagReplacement.IMG_REPLACEMENT`, `U+FFFC OBJECT
   REPLACEMENT CHARACTER`. The auditor traced this through Markwon 4.6.2's own
   sources: `GlideImagesPlugin` calls a bare `load()` with no `.error()`, so
   `onLoadFailed` receives null, `drawable.setResult` is never called, and
   `AsyncDrawableSpan.draw` takes its render-the-text branch.

Point 4 is what settles deviation 5 below: **nothing in the Kotlin falls back to
the network**, so the port's render-time authenticated fetch is a *superset* of
the Kotlin, not a port of it.

## What shipped

One join, written as one derivation, because that is the failure this project
keeps paying for (Phase 74's reactions, Phase 100's verification photo, Phase
116's community feed identifier, Phase 120's submission questions — each time a
writer and a reader disagreed about a key and each half passed its own test).

- **`markdownImageCachePath(String link)`** (`lib/core/utils/markdown_links.dart`)
  — the single derivation. Strips a leading `resources/` (the same strip
  `prependBaseUrlToImages` does, kept in the same file so the two cannot drift),
  percent-decodes each segment, and returns `null` for anything that cannot name
  a local file.
- **`MarkdownImageFiles`** (`lib/core/files/markdown_image_files.dart`) —
  resolves that path under **`ResourceFiles.baseDirectory`**, so a markdown
  image lands in the same `<base>/ole/<id>/<file>` a downloaded resource
  attachment does. Not a convenience: two roots is how a file that downloads
  successfully becomes a file the viewer cannot find, and one root is also one
  test seam.
- **`MarkdownImagePrefetcher`** (`lib/core/files/markdown_image_prefetcher.dart`)
  — downloads what is not already on disk, with the auth header a CouchDB
  attachment needs. Keeps `resources/` on the **URL** while the on-disk path
  drops it, which is the same asymmetry Kotlin has
  (`CoursesRepositoryImpl:671` builds `"$baseUrl/$link"` from the *unstripped*
  link while the render base strips it).
- **`CoursesRepository.sync`** collects from the course description
  (`CoursesRepositoryImpl:670`) and each embedded step's (`:684`), into a `Set`,
  and prefetches **once after the walk** — the position of
  `SyncActivity.onSyncComplete:566`, not per document.
- **`_MarkdownImage`** prefers the local copy through a new
  `markdownImageFileProvider` seam and falls back to the existing authenticated
  fetch on a miss, on a loading lookup, and on a file that vanished between the
  check and the read.

### The defect the key derivation did not cover

Worth its own heading, because it is the phase's one real bug and my own
single-derivation defence walked straight past it. **The disagreement was
upstream of the derivation: the two sides do not share a parser.**

The collector is `extractImageLinks`, a line-for-line port of Kotlin's
`DownloadUtils.extractLinks` regex, whose lazy `(.*?)` takes everything up to
the first `)`. The renderer's link comes from `flutter_markdown_plus`'s
CommonMark parser. Probed against the real parser rather than reasoned about:

| markdown | collector sees | renderer asks for |
|---|---|---|
| `![a](resources/abc/c.png)` | `resources/abc/c.png` | `resources/abc/c.png` |
| `![a](resources/abc/c.png "Title")` | `resources/abc/c.png "Title"` | `resources/abc/c.png` |
| `![a](<resources/abc/c.png>)` | `<resources/abc/c.png>` | `resources/abc/c.png` |

So for a titled or bracketed image the prefetcher requested
`…/c.png "Title"` — a 404 — while the renderer looked up `abc/c.png`, which
nothing had written. No wrong bytes, no crash, nothing in a log: the image
simply stayed online-only, which is the exact condition this phase exists to
remove. **The Phase 100 shape, at one remove.**

**Kotlin does not have this bug, because it does not have a second parser.**
`prependBaseUrlToImages` matches on the *identical* pattern, so Kotlin
downloads and renders the same wrong path and a titled image never appears at
all. Self-consistently broken is not a thing the port can be here, so
`markdownImageDestination` normalises the collected link to the CommonMark
destination — title stripped, pointy brackets unwrapped — and both the cache
path and the download URL go through it. On a link the renderer supplies it is
a no-op.

Found by asking the question the brief told me to ask (*can the writer produce
values the reader's predicate matches?*) and then **running the real parser**
instead of answering it from the regex. The three round-trip tests were written
failing first.

### Where the port deliberately differs from the Kotlin

Recorded here rather than left for a later audit to flag as an undocumented
improvement.

| # | Kotlin | Port | Why |
|---|---|---|---|
| 1 | Accumulates every link ever seen into two never-cleared in-memory collectors, flushed into a `SharedPreferences` JSON list | Keeps **no list at all** | The `courses` `_all_docs` walk re-reads every document every sync, so **the documents are the durable store**. A failed download is retried by the next sync at no storage cost, and there is nothing to grow. |
| 2 | Download path and render path are derived separately, and **disagree** for `img/foo.png` (download flattens to `ole/foo.png`, render preserves `ole/img/foo.png`, so it never renders) | One derivation serves both | The whole point. A link the port declines is declined by *both* sides. |
| 3 | Download side percent-decodes each segment, render side does not | Decodes on both | `a%20b.png` and a literal `a b.png` name one file, and a markdown link to an attachment lands where `ResourceDownloader` would write it. |
| 4 | Queues `<couch>/db/…` URLs **with the `satellite:<PIN>@` userinfo** into `SharedPreferences` | Stores nothing; attaches auth as a header at request time | A persisted credential-bearing URL is the trap `UrlUtils.credentialFreeDbUrl` already exists for. |
| 5 | A missing local file renders `U+FFFC`; nothing retries over the network | Falls back to the authenticated fetch | A **superset**, not a port. Stated as such at the code. |
| 6 | Hands the queue to a foreground service and returns | Awaits the downloads inside `sync()` | Deterministic and testable. Bounded by the skip-if-already-on-disk check, so steady state is one `exists()` per link. |
| 7 | No `..` guard on the download path — `![x](resources/A/../../../evil.sh)` writes outside `ole/` (the auditor verified the canonical path) | Refused by the derivation, before and after decoding | `FileUtils.resolveHtmlEntryFile` already shows the house style; `getSDPathFromUrl` was simply never given it. |
| 8 | The API-31 gate on the teams path | Not ported | See premise 2 above — lint artefact, not policy. |

**One thing the port does *not* improve on, on purpose.** A bare single-segment
link (`![](cover.jpg)`) resolves to `<base>/ole/cover.jpg`, so two courses that
both write `![](cover.jpg)` collide — exactly as they do in the Kotlin, where a
bare filename is one of only two link shapes whose download and render paths
agree. An earlier cut of this phase declined the shape on collision grounds;
that was a **regression against the app being ported**, not a hardening of it,
and the ground-truth audit is what caught it.

## What the second audit found in my own finished, green code

Eight defects, in code that passed format, analyze and 2784 tests. Two of them
were the feature not working; one would have broken background sync outright.
Recorded at this length because the pass is mandatory precisely because of
rounds like this one.

**1. A Latin-1 percent-escape threw out of `sync()` and permanently broke
auto-sync.** `Uri.decodeComponent` throws **two** types: `ArgumentError` for bad
hex, and **`FormatException` for valid hex that is not valid UTF-8**. I caught
only the first. `caf%E9.png` is Latin-1 `café.png` — the shape any pre-UTF-8
attachment name on an older Planet install has — and it threw straight through
`_fetch` → `prefetch` → `CoursesRepository.sync`. Foreground that is a failed
sync whose documents were already written. In the background isolate
`BackgroundTaskRunner` records it as a failed step, `recordLastSync` is skipped,
`run()` returns false, WorkManager retries, `_isDue()` is still true — **one bad
character in one course description would stop auto-sync for as long as that
document existed on the server.** Before this phase `CoursesRepository.sync`
touched no filesystem at all, so this and two sibling throw sites
(`path_provider`'s channel in a headless engine, a file vanishing between
`exists()` and `length()`) were all new failure modes I introduced. Fixed twice
over: the specific catch, and an unconditional per-link `try` in `prefetch` —
the class doc claimed failures were swallowed and the code did not implement it.

**2. The production wiring was pinned by nothing.** `markdownImages` is optional
with a null default and the call site is `_markdownImages?.prefetch(...)`, so
deleting the argument from `coursesRepositoryProvider` was a **silent no-op** —
analyze clean, every prefetch test green (each builds its own repository), the
feature simply absent from the app. That is the ported-tested-green-and-dead
class this phase's brief warned about, reproduced by the phase that was warned.
`test/providers/courses_repository_wiring_test.dart` now drives the real
provider graph and asserts the bytes reach disk; the mutation that deletes the
argument kills it.

**3. `sync()` could hang forever.** `PlanetApi.getBytes` sets
`receiveTimeout: null` — correct for a large user-initiated download, and an
unbounded stall once it runs inside a sync, where `SyncNotifier` refuses a
second attempt while one is running so the sync button never re-enables. Added
a per-link timeout and an overall budget; deviation 6's "steady state is one
`exists()` per link" was true and irrelevant, because a first sync on a fresh
install is not steady state and WorkManager kills the task at ~10 minutes.
Kotlin needs neither budget: the foreground service is how it outlives that.

**4. The local copy never took over until the app restarted.**
`markdownImageFileProvider` had no `.autoDispose`, and a `FutureProvider.family`
caches per argument for the life of the container — so a first render while the
image was still downloading cached `null` for the whole process, and the sync's
file was never picked up. Open a course while the dashboard sync is walking
`courses`, then go offline: the image is on disk and does not render. **The
feature not working, in exactly the situation it exists for.** My own notes
described this wrongly — I wrote that the local branch takes over "on the next
rebuild", and a rebuild re-watches the *cached* instance; there was no rebuild
that fixed it.

**5. A 49-line dartdoc was attached to the wrong function.** The new
`markdownImageDestination` doc ran into the `markdownImageCachePath` block with
no separator, so Dart bound all 71 lines to `markdownImageDestination` and the
single key derivation this phase is built on had **no doc at all**. Neither
`dart format` nor `flutter analyze` catches it. Fixed by ordering the file so
each doc sits on its own function.

**6. A code comment asserted what these very notes disprove.** The prefetcher
said Kotlin's link list "grows for the life of the install"; `SyncManager.kt:93`
clears the pref on every sync, and what grows is the two process-lifetime
collectors that repopulate it. The notes said this correctly and cited the line;
the comment was never updated to match — and **the comment is what a future lane
reads**. Same defect class this file takes credit for catching.

**7. Two wrong Kotlin line citations** carried in from the brief:
`VoicesRepositoryImpl:393` is `val baseUrl`, `extractLinks` is at **393**; and
`TeamsRepositoryImpl:1187` is a closing brace, the call is at **1187**.
Corrected in code and in these notes. (Every other citation on both sides was
independently re-derived and holds.)

**8. `_titleSuffix`'s parenthesised-title branch was unreachable.**
`extractImageLinks`' lazy capture stops at the first `)`, so `![a](p (T))`
yields `p (T` and never a string ending in `)`; the renderer's destinations are
percent-encoded with no whitespace. Removed, with a test pinning that a
parenthesised title is *not* stripped so its absence stays a decision. The
`\s+`-vs-`\s*` distinction the earlier draft of these notes celebrated was
pinned by `photo(1)` — an input no producer can generate — so that test was
replaced with the reachable one, `![a](abc/"T")`. **Two sections of these notes
were bragging about a guard of exactly the kind they claim to remove.**

### Reported by the audit and deliberately not fixed

Five further collector/renderer disagreements remain, all of which fall back to
the network and lose nothing: a `#fragment` (the one case that writes a file
nothing will read, at `<base>/ole/A/b.png#100x50`), an HTML entity
(`b&amp;amp;c.png` vs `b&amp;c.png`), a backslash-escaped paren, a balanced-paren
destination, and a reference-style `![a][ref]` link, which the regex collector
does not see at all. Closing them properly means parsing markdown in the
collector rather than pattern-matching it, which is a different change from this
one. `markdownImageDestination`'s dartdoc names what it does and does not
reconcile rather than implying it covers everything.

## Verification

- **Every claim mutation-tested, twice** — once on the first cut and again on
  everything the second audit's fixes added. Thirty mutations in total across
  the derivation, the destination normaliser, the files helper, the prefetcher,
  the sync collection, the renderer and the provider wiring; each reverted in
  turn and the suite re-run. Twenty-nine were killed by the test named for
  them. A no-op control mutation survived, which is what says the harness can
  distinguish the two.
- **Four survivors were real, and every one of them was a finding about my code
  or my tests rather than a shrug.** Two dead guards (below), one unpinned
  regex rule, and — the one worth repeating — **the test named "prefetches
  once, after the walk rather than per page" could not fail for the property it
  named**: `stubCount(2)` yields a single page, so `calls == 1` held whether the
  prefetch sat after the loop or inside it. Rewritten with a real page boundary,
  and the inside-the-loop mutation now kills it. That test was cited in the
  first draft of these notes as evidence for a claim it did not test.
- **Two mutations survived the first round, and both were real findings about
  my code rather than about the tests.** The raw pre-decode `..`/empty-segment
  check and the `startsWith('/')`/backslash early return were fully **subsumed**
  by the post-decode check — dead guards that read as guards. They are removed;
  the surviving absolute-URL check is now pinned by the one case that only it
  catches, a `data:` URI (an `http://` link fails the per-segment rules anyway,
  on the empty segment after the `//`).
- **One guard is knowingly unpinned**: the `p.isWithin` containment check in
  `MarkdownImageFiles.fileFor` cannot fail, because the derivation already
  refuses everything that could escape. Its mutation survives and no test can
  kill it without reaching past the derivation. Kept as defence-in-depth against
  a later loosening, and the code comment says exactly that. Flagged here so it
  is not mistaken for coverage.
- **A round-trip test, not two half-tests.**
  `test/repository/markdown_image_round_trip_test.dart` starts from a
  **server-shaped course document**, runs the real sync walk and the real
  prefetcher, and asserts the file is found by *the renderer's own lookup*,
  driven with `Uri.parse(link).toString()` — the string
  `flutter_markdown_plus`'s `imageBuilder` produces from the same span. A
  fixture that hand-places a file where the renderer computes it is the
  fabricated join this project keeps catching; it proves nothing.
- **A harness lesson worth keeping.** My first mutation script did the
  substitution without asserting the pattern matched. One mutation silently
  no-op'd on shell escaping and reported `SURVIVED`; re-run with an `assert old
  in s` it was killed immediately, by four tests. **A mutation harness that
  cannot tell "the guard is untested" from "the mutation never applied" reports
  the first and means the second.**

## Reported, not fixed

Each names the file, the change, and why it was not made here.

1. **A voice message's markdown images are collected by nothing, and would be
   dead plumbing if they were.** Kotlin extracts from `news.message`
   (`VoicesRepositoryImpl:393`) and `VoicesAdapter:448` renders it through
   `prependBaseUrlToImages` — the ground-truth audit confirmed that site is
   **live** (`setMessageAndDate:447`, reached from both `onBindViewHolder`
   overloads). The port renders `Text(row.message ?? '')`
   (`voices_screen.dart:183`): no markdown, no images, so a voice posted as
   `**Water** notes ![chart](resources/abc/chart.png)` shows those literal
   characters. **Collecting the links without wiring a renderer would be exactly
   the dead-plumbing shape this phase's brief warned about** (`updateCourseProgress`,
   Phase 119's four uncalled sync writers), so neither half was built. This is
   `PHASE_142_NOTES.md` item 8 — a separate reported defect, and the blocker for
   the voices half of item 1. Fix, in order: swap `voices_screen.dart:183` (and
   the reply/detail message render points) to `CourseMarkdownBody`, then add the
   `extractImageLinks(message)` collection to `VoicesRepository.sync`'s mapper
   path and hand the set to `MarkdownImagePrefetcher` after the walk. Everything
   the second half needs already exists and is tested; it is three lines and a
   test once the renderer is there.
2. **A team/community description's markdown images, same shape, one step
   further back.** Kotlin's `TeamsRepositoryImpl.processDescription:1185` (the `extractLinks` call at `:1187`)
   collects them and `CommunityServicesFragment:52` renders the description as
   markdown at 600×350 above the services list, with a `tvNoDescription` empty
   state. The port's `services_screen.dart` renders **only the list** — there is
   no description region at all to render into
   (`PHASE_142_NOTES.md` item 9). So this needs a UI region built before the
   prefetch has any meaning, which is more than a markdown-image slice.
3. **Kotlin's teams path calls `openDownloadService` once per team document,
   with an empty list, inside a Room transaction.** No `isNotEmpty()` guard at
   `TeamsRepositoryImpl:1193`, and `bulkInsertFromSync` wraps a ~1000-doc page
   in one transaction (`:1265`), so a full `teams` walk performs ~1000
   foreground-service starts and ~2000 `SharedPreferences` round-trips inside a
   database transaction. Recorded because whoever ports item 2 will read that
   method and should collect across the batch and enqueue once, as the
   courses/voices path does — not reproduce it.
4. **Kotlin's `DownloadWorker` never drains the queue it reads.** It reads the
   pending-URL `StringSet` and never removes what it processed — there is no
   `preferences.edit` anywhere in the file — unlike `DownloadService.cleanupProcessedUrls`.
   No port impact today (the port keeps no such queue, deviation 1), but it is
   the kind of asymmetry a later "just port the worker" would inherit.
5. **`markdownImageBytesProvider` is not `autoDispose`.** A description
   rendered online caches its bytes under the resolved URL for the life of the
   container. Genuinely harmless — the same pixels — and *unlike* its sibling
   `markdownImageFileProvider`, which had the same shape and where it was the
   feature not working (finding 4 above). Left alone to keep the diff to what
   the audit showed was broken. `flutter/lib/ui/courses/course_markdown.dart`.
6. **A server-side replacement of an image at the same path is never
   re-fetched**, in either app. `existingFileFor` is `exists() && length() > 0`,
   as Kotlin's `FileUtils.checkFileExist` is; neither carries a rev or mtime
   check. Faithful, and a hole in both. Closing it needs a per-file revision the
   markdown link does not carry.
7. **`prependBaseUrlToImages` still has no caller in `lib/`, and now has a
   documented reason.** It discards the markdown `alt` text entirely, which is
   why the Kotlin's missing-image fallback is a bare `U+FFFC` rather than
   anything readable. The port renders `![alt](path)` spans through
   `flutter_markdown_plus` instead, so it never needs the rewrite. The helper is
   kept as the line-for-line port of a live Kotlin function; that is now stated
   at the code rather than implied.

## Files touched

`lib/core/utils/markdown_links.dart`, `lib/core/files/markdown_image_files.dart`
(new), `lib/core/files/markdown_image_prefetcher.dart` (new),
`lib/repository/courses_repository.dart`, `lib/ui/courses/course_markdown.dart`,
`lib/providers/app_providers.dart` (one provider argument and its import).

Tests: `test/core/utils/markdown_links_test.dart`,
`test/core/files/markdown_image_files_test.dart` (new),
`test/core/files/markdown_image_prefetcher_test.dart` (new),
`test/ui/courses/course_markdown_test.dart` (new),
`test/repository/markdown_image_round_trip_test.dart` (new),
`test/repository/courses_repository_test.dart`.

**No Drift table, no DAO change, no `schemaVersion` bump.** The one constraint
the brief flagged as most likely to bite — `download_queue` living in Lane A's
`app_database.dart`, and keyed on a `MyLibrary` **`resourceId`** that a markdown
image does not have — is avoided rather than worked around: deviation 1 removes
the need for a durable queue at all, so nothing was needed from Lane A and
`schemaVersion` stays at 48.
