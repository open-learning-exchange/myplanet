# myPlanet performance work orders — 10 independent tasks

Open PRs checked before writing (R3), snapshot 2026-10-09: #18076, #18050, #18045, #18029, #17832, #17811, #17694, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075.

Line citations below were reconfirmed on this branch on 2026-10-16. Re-check the open-PR file list before editing.

Chosen files were absent from the retrieved diffs. #15226 pages seen so far are under `flutter/` only; later pages of that PR were not fully retrieved. Do not touch `flutter/`. Also off-limits even if not listed below: any file those PRs already change, including `SyncRepositoryImpl.kt`, `RatingsRepositoryImpl.kt`, `BaseDashboardFragment.kt`, `DashboardViewModel.kt`, `SurveyFragment.kt`, `SurveysViewModel.kt`, `strings.xml`, calendar layouts, `Meetup.kt`, `TeamCalendarFragment.kt`, `ExamTakingFragment.kt`, `SubmissionViewModel.kt`, `SubmissionsAdapter.kt`, `BaseRecyclerFragment.kt`, `BaseVoicesFragment.kt`, `app/build.gradle`, `gradle/libs.versions.toml`, `ResourcesAdapter.kt`, `ResourcesFragment.kt`, `CoursesViewModel.kt`, `CoursesAdapter.kt`, `TimeUtils.kt`, `VoicesAdapter.kt`, and the team/enterprise adapters.

No verified free-file micro-opt was left for roadmap 2 (navigation), 4 (DI), or 5 (sync/upload consolidation). Those areas are already owned by open PRs or already cached. Every task below is a performance quick win.

Shared constraints for every task: under ~150 changed lines, at most 5 files, no new dependencies, no unused code, no TODOs, no edits outside the listed files, independently mergeable in any order. Extend the existing test file; do not add a new test class. Keep current results identical.

Roadmap this round serves:

1. finish cleaning the data layer
2. introduce global navigation architecture
3. expand viewmodel and use-case layers
4. complete dependency-injection cleanup
5. consolidate sync and upload workflow
6. migrate ui incrementally to compose
7. optimize remaining performance hotspots
8. improve code health and add tests

North star — never scheduled directly, never blocked:

9. kotlin multiplatform: a platform-free kotlin core — repositories, models, sync/upload logic and use cases end up with zero `android.*` imports
10. compose multiplatform: every compose screen from 6 stays portable — state hoisted into viewmodels, no android views inside composables, no direct `R.*`

## Task 1 — Library cover decode size

Roadmap 7. Also 10: the cover bind path is a future Compose screen, so the target size must stay in the binder/view-model data, not inside a composable or an Android view measurement taken at draw time.

Files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesCardBinder.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesCardBinderTest.kt`.

Do not touch: `ResourcesAdapter.kt`, `ImageUtils.kt`, `PdfThumbnailLoader.kt`.

`showMediaPreview` (lines 104–120) loads covers with Glide and no size override. `coverWidthDp` (line 29) and the PDF path (line 90) already know a target width. `showPdfPreview` (lines 123–142) stats with `file.exists()` on the IO dispatcher even though `cachedFileLength` (line 183) is already computed for the same file.

Use the existing cover width for the image and video-poster Glide loads so full-resolution library covers are not decoded into the card. Use the already computed length instead of a second existence stat. Do not change which file is shown, the missing-file fallback, or PDF thumbnail generation.

Done when a cover load requests a bounded size, the PDF path does not stat twice, and `ResourcesCardBinderTest` still passes with one new assertion that a known-missing file is not opened again after a non-positive cached length.

## Task 2 — HTML cover walk repeat

Roadmap 7. Also 9: the cache must stay platform-free Kotlin (no `android.util.LruCache`, no `Context`).

Files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`.

Do not touch: `ResourcesCardBinder.kt`, `InlineResourceAdapter.kt`, `ResourcesAdapter.kt`, `TeamResourcesAdapter.kt`.

`findHtmlCoverImage` (line 123) walks the resource directory to depth 4, stats every file, and returns the first name-hint match or else the largest image. Callers already cache the result, but the first open of a library still pays the walk, and a second call on an unchanged directory walks again.

Cache only a positive name-hint hit, keyed by the directory's canonical path, and reuse it only while that chosen file still exists with the same length. Always walk again for the largest-file fallback, and walk again if the hinted file is gone or its length changed. Walk order and the current tie-break must stay identical. Do not change `previewImageExtensions` or `previewImageNameHints`.

Done when existing walk-order tests pass unchanged, a second call on an unchanged hinted file does not need a new winner, and replacing the hinted file yields the new winner.

## Task 3 — Resource filter case folding

Roadmap 1 and 7. Also 9: `ResourcesListFilter.kt` has no `android.*` imports and must stay that way.

Files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilterTest.kt`.

Do not touch: `ResourcesMediaType.kt`, `ResourcesViewModel.kt`, `ResourcesFragment.kt`.

`matchesFacets` (lines 71–84) and `matchesMedium` (lines 86–111) call `equals(ignoreCase)` and `canonicalMedium` inside the per-item loop. A filter keystroke therefore re-lowercases the selected facets and re-canonicalizes the selected mediums once per library row.

Lowercase the selected subject and level sets once per `matching()` call, and canonicalize each selected medium once per call. Compare item values with ordinary equality after the same normalization. Empty-selection behavior, multi-select OR behavior, and medium aliases must stay the same.

Done when a mixed-case facet selection still matches the same rows, and the per-item path no longer lowercases the criteria.

## Task 4 — Media classification repeat

Roadmap 7. Also 9: do not introduce `android.util.LruCache` or any other `android.*` type. `ResourcesMediaType.kt` must remain portable.

Files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesMediaType.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesMediaTypeTest.kt`.

Do not touch: `FileUtils.kt`, `ResourcesListFilter.kt`.

`classify` (lines 20–29) and `extensionOf` (lines 69–73) lowercase the media type and call `FileUtils.getFileExtension`, which constructs a `File`, on every call. Filter keystrokes and rebinds classify the same library fields again.

Cache the result by the three inputs `classify` reads: local address, remote address, and media type. Bound the cache (a few hundred entries is enough). A cache hit must return the same enum the uncached function would. Do not change the extension rules or the fallback order.

Done when every existing `classify` case still returns the same type, including null-local fallback and extension case, and a second call with the same three fields does not recompute.

## Task 5 — Chat append copy

Roadmap 7. Also 6 and 10: an append must not depend on copying the whole conversation through a list snapshot. State stays in the adapter's message list so a later Compose screen can hoist that list instead of diffing Android view state.

Files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt`.

Do not touch: `ChatDetailFragment.kt`, `ChatMessage.kt`.

`addQuery` (line 119) and `addResponse` (line 127) copy `currentList` and `submitList` the whole conversation for one new row. `prependMessages` (line 142) is a structural front-insert and may keep its single new list.

Append only the new row and notify that row. Keep `scrollToLastItem`, `lastAnimatedPosition`, view types, and copy-to-clipboard behavior. If `ListAdapter` cannot mix a partial insert with `submitList`, keep the append path on a manual list plus a single-row notification, and leave prepend on its current one-list update. Do not copy unchanged message bodies on append.

Done when appending one message does not allocate a full copy of the conversation, scroll-to-last still runs, and `ChatAdapterTest` covers one append and one prepend without a behavior change.

## Task 6 — Exam answer comparison

Roadmap 7 and 8. Also 9: the only Android import is `@VisibleForTesting`. Do not add `android.*` usage. Keep `Locale.getDefault()` out of the per-comparison path.

Files: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt`.

Do not touch: `ExamTakingFragment.kt`, `SubmissionViewModel.kt`.

`checkSelectAnswer` (line 70), `checkMultipleSelectAnswer` (line 77), and `checkTextAnswer` (line 89) lowercase and sort on every comparison, and read `Locale.getDefault()` (lines 72, 83, 91) three times per answer.

Normalize the expected answer once per question and the learner answer once per check. Compare already-normalized values. Multiple-select order independence and text trimming must stay the same. Do not change scoring rules.

Done when existing score tests pass, a repeated check of the same answer does not re-read the default locale inside the comparison, and one new test locks the current case-insensitive and order-independent results.

## Task 7 — Free-space progress spam

Roadmap 7.

Files: `app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt`, `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt`.

Do not touch: `ResourcesRepository.kt`, `ResourcesRepositoryImpl.kt`.

`deleteRecursive` (line 66) calls `setProgress` (line 77) every 10 deleted files. Cleaning a large offline library sends a binder update hundreds of times during one delete walk. The walk itself may stay.

Throttle progress to a few updates per second, or to a much larger file count, and send one final update when the directory finishes. Still skip the `cv` directory, still delete the same files, and still batch the not-offline marks the way the worker already does. Do not change the WorkManager request or the cleanup threshold.

Done when a large delete reports far fewer progress updates than one per 10 files, the final update still fires, and the existing worker test still sees the same files removed.

## Task 8 — Course inline resource rebind stats

Roadmap 7. Also 6: the existence result belongs in bind state, not in a second disk read inside the row, so a later Compose row can take the same flag without touching the filesystem.

Files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapterTest.kt`.

Do not touch: `FileUtils.kt`, `ResourcesRepository.kt`.

Bind (line 171) calls `FileUtils.checkFileExist` and then `resourceFile.exists()` (line 192) on every bind. Those two paths can differ (`getSDPathFromUrl` versus `ole/${id}/${address}`), so do not collapse them without confirming they are the same file. `getPreviewDimensions` (line 294) is not part of this task.

Cache each absolute path's existence result for the adapter lifetime. Drop entries for resource ids that leave the list, following the existing `onCurrentListChanged` cover-cache cleanup. If both checks resolve to the same path, stat once. A rebind of an unchanged row must not stat again. Do not change which preview is shown.

Done when a second bind of the same downloaded row does not stat, a row that leaves the list is not served a stale hit after it returns with a missing file, and `InlineResourceAdapterTest` still passes.

## Task 9 — WebView night-mode DOM walk

Roadmap 7 only. Do not claim roadmap 10: this screen is an Android `WebView`, and the fix stays inside that activity.

Files: `app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/viewer/WebViewActivityTest.kt`.

Do not touch: `FileUtils.kt`, `MarkdownUtils.kt`.

`applyNightMode` (line 243) injects a script that runs `querySelectorAll('*')` (lines 252 and 269) and `getComputedStyle` on every element for both dark and light mode. Opening a long resource forces a style recalc of the whole DOM.

Replace that walk with one injected stylesheet (or one class on the document element) that applies the same dark and light colors. Do not change which pages get night mode, the toggle, or how HTML is loaded. Keep the script or stylesheet string in this activity so the existing test file can assert it contains no per-element query.

Done when dark and light still apply, the injected text has no `querySelectorAll` and no `getComputedStyle`, and `WebViewActivityTest` covers that assertion.

## Task 10 — Library facet rebuild

Roadmap 3 and 7.

Files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt`.

Do not touch: `ResourcesFragment.kt`, `ResourcesListFilter.kt`, `ResourcesMediaType.kt`.

`getFilterFacets` (line 135) rebuilds subject, level, language, and medium maps on every call, lowercasing every facet and calling `canonicalMedium` per row. Reopening the filter sheet repeats the whole walk. The title-sort path is already keyed; do not change it.

Cache the returned map until the library list's ids and facet fields change. A content signature is required, because the caller builds a new list each time. Same libraries must return the same facet sets. A changed subject, level, language, or medium must miss the cache. Do not change facet names or the empty-value filter.

Done when two calls with the same facet fields do not rebuild, a changed field produces the new facets, and the existing `ResourcesViewModelTest` facet cases still pass.

## Self-check

R1: 10 tasks. R2: no shared file. R3: open PRs listed; chosen files were not in the retrieved diffs. R4: every cited file, function, and line range was opened in this repo and reconfirmed on 2026-10-16. R5: each task is one production file plus its existing test, no new dependency, no TODO, no unused code. R6: no implementation code in this plan.
