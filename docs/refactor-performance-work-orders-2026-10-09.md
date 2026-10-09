# myPlanet performance refactor work orders

date: 2026-10-09 (America/New_York)
base commit: f18c50d25010913a0f85ba9d744705349f13b051
open PRs checked: #18076, #18050, #18045, #18029, #17832, #17811, #17694, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

The open-PR snapshot includes every open PR, irrespective of age, draft status or label. Changed files were paginated through the REST API for PRs whose CLI file lists reached 100 entries; the exclusion set contains 1,308 paths. Recent activity was checked for October 2–9, including #18050 labelled `ready`; every open PR's files are excluded, a stricter rule than checking only recent review-ready or merge-labelled PRs.

These work orders are ordered by expected user benefit divided by the size and risk of the change, based on inspected source rather than measured speedups. Each starts independently from the base commit, owns only its listed editable files, and needs no other task. Run acceptance commands from the repository root; targeted tests and the full `./gradlew testDefaultDebugUnitTest` suite must pass after implementation. This branch contains only this plan; implementation and test execution belong to the executing agents. Recheck open PRs before execution and skip a work order if its editable files acquire an owner; do not expand its scope.

---

### 1. Batch long typing animations through the existing text revealer (roadmap 7+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt:19` emits one growing substring and one callback for every character, with a delay on every iteration. Long replies therefore allocate and render thousands of prefixes; `app/src/main/java/org/ole/planet/myplanet/utils/TextRevealer.kt:12` already provides adaptive chunks capped at eight characters while preserving the approximate per-character duration. Reusing that platform-free animation algorithm also moves roadmap 9 forward without scheduling a core migration.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt` — `VoicesAdapterHelper.createOnAnimateTyping`.
- Edit `app/src/test/java/org/ole/planet/myplanet/utils/TextRevealerTest.kt` — `TextRevealerTest`, adding callback-wrapper coverage beside existing reveal tests.
- Read-only neighbor: `app/src/main/java/org/ole/planet/myplanet/utils/TextRevealer.kt` — `TextRevealer.reveal` and `chunkSize`; leave `handleShareNewsResult` in the edited helper untouched.

steps:
1. Replace the helper's character loop with a call to the existing revealer inside its existing main-dispatcher coroutine, forwarding the update callback.
2. Invoke completion once after a normal reveal; preserve the returned cancellation closure and do not invoke completion after cancellation.
3. Remove imports made unused by deleting the loop; keep the public callback signature unchanged.
4. Extend the existing test class with wrapper cases for empty text, a short reply, a 3,000-character reply and cancellation after several updates; use existing coroutine test support and a test dispatcher provider.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.TextRevealerTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. The long-reply wrapper emits 375 updates, ends with the full response, completes once and stops callbacks when cancelled; short replies retain character-by-character updates. Manually verify that a long AI reply types smoothly and scrolling away does not keep updating its recycled row.

size budget: approximately 65–95 changed lines across 2 editable files; hard cap 140 lines.

out of scope: Do not change reveal timing/chunk constants, adapters, reply fetching or markdown rendering. No new dependencies or animation APIs.

---

### 2. Coalesce inline resource payloads into one preview refresh (roadmap 7+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt:122` processes every payload separately, and both address and status payloads call the preview updater. A combined update resets the views and starts/cancels preview work multiple times for the same final resource, wasting work during downloads and sync refreshes.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt` — the payload overload of `InlineResourceAdapter.onBindViewHolder`.
- Edit `app/src/test/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapterTest.kt` — `InlineResourceAdapterTest`.
- Leave neighboring full binds, `updateStatusAndPreview`, preview-rendering methods and `onViewRecycled` unchanged.

steps:
1. Flatten the supplied payload lists once and collect recognized flags, deduplicating repeated flags within and across payload lists.
2. Update the displayed title once when a title or address flag is present; refresh status/preview once when an address or status flag is present.
3. Preserve the current empty-payload full-bind path and final click-listener update; retain current handling of unrecognized payloads.
4. Add regression coverage for combined address/status flags, duplicate flags and a title-only update; verify one preview refresh for the first two and none for the last. Observe the synchronous view reset as well as loader calls so cancelled duplicate jobs cannot hide a regression.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.courses.InlineResourceAdapterTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. Each merged payload delivery refreshes previews at most once and binds the latest title and click target. Verify that an inline download still transitions from its spinner to the downloaded preview.

size budget: approximately 75–110 changed lines across 2 files; hard cap 140 lines.

out of scope: No preview-cache, file-resolution, coroutine-scope or diff-identity changes. No adapter rewrite or Compose migration.

---

### 3. Cancel obsolete image requests before showing empty-source placeholders (roadmap 7+8)

context: `app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt:10` immediately sets a placeholder for an empty profile source, and `app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt:38` does the same for a general image source. Neither branch clears a previous Glide request on that view, so a recycled row can keep downloading/decoding its old image and later overwrite the placeholder.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt` — `ImageUtils.loadProfileImage` and `loadImage`.
- Edit `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt` — `ImageUtilsTest`.
- Leave the neighboring `loadPlaceholderImage` method and all nonempty-source request options unchanged.

steps:
1. Clear the existing Glide request on the target view before setting its fallback drawable in each null/empty-source branch.
2. Preserve the current distinct fallback drawables, size arguments and nonempty-source loading behavior.
3. Extend the existing Robolectric tests to attach an outstanding request, rebind the same view with both null and empty sources, and verify that request is cleared before its placeholder is installed.
4. Cover both entry points and confirm that an obsolete completion cannot replace the fallback; use existing Glide and test facilities, with no real network requests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ImageUtilsTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. Empty-source rows retain their existing fallback image and have no active obsolete request. Scroll quickly through rows with and without portraits and verify that an old portrait never appears in an empty-source row.

size budget: approximately 50–85 changed lines across 2 files; hard cap 120 lines.

out of scope: No disk-cache policy, crop, image-size, lifecycle-owner or drawable changes. Keep Android image handling at the existing UI boundary.

---

### 4. Prepare medium filters once and short-circuit rejected resources (roadmap 7+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:75` computes all facet predicates before combining them, even when the subject already rejects a resource. `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:90` also canonicalizes each selected medium again for each candidate; hoisting invariant preparation reduces CPU and string allocations on large libraries. Keep these predicates platform-free, moving roadmap 9 forward as reusable filtering logic.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt` — `ResourcesListFilter.matching`, `matchesFacets` and `matchesMedium`.
- Edit `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilterTest.kt` — `ResourcesListFilterTest`.
- Leave neighboring signature tracking, `filterIfChanged`, `reset` and download predicates unchanged.

steps:
1. Prepare canonical selected-medium values and their existing target-type mapping once per `matching` call, outside the per-resource sequence predicates; pass this local preparation into the facet/medium checks.
2. Make the subject, level, language and medium checks return early on failure, preserving their current order, AND/OR rules and case handling.
3. Resolve a resource's raw canonical medium at most once when fallback-medium matching is needed; preserve the explicit-non-book exclusion.
4. Add tests for a failed early facet combined with selected media, recognized and unrecognized media, aliases, empty selections and books versus explicit non-books; assert `apply` and `countMatching` agree and output order is preserved.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.resources.ResourcesListFilterTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. Selected-media canonicalization occurs outside the per-resource loop, and rejected early facets skip later classification. Verify combined library filters return the same resources and matching count as before.

size budget: approximately 85–120 changed lines across 2 files; hard cap 145 lines.

out of scope: No search-ranking, filter-signature, repository, model or resource-string changes. No persistent cache or Android imports in filtering logic.

---

### 5. Cancel superseded initial patient-list loads (roadmap 3+7+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt:95` launches an untracked initial patient-list query, whereas `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt:101` cancels only earlier searches. An initial load can therefore continue beside a new search/sort request and overwrite its results; sharing the existing job ownership removes that wasted work. Keeping query coordination in the ViewModel also supports roadmap 10's hoisted-state direction without scheduling a screen migration.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt` — `HealthViewModel.loadPatients`, `searchPatients` and their existing list-query job state.
- Edit `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthViewModelTest.kt` — `HealthViewModelTest`.
- Leave neighboring `selectPatient`, health-detail refreshes, health saves and their job state unchanged.

steps:
1. Make `loadPatients` cancel and replace the same existing `searchJob` used by `searchPatients`, so only the latest list request remains active.
2. Preserve immediate initial loads, the current query/sort arguments and the 100 ms delayed search spinner; do not add a second debounce.
3. Clean up the delayed spinner child in `finally`, and allow only the owning list-query job to clear loading state; check cancellation before publishing query results.
4. Add controlled suspended-query tests for initial load superseded by search, search superseded by load, and two sorts; verify cancellation, final results and spinner ownership. Retain patient-detail test expectations.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.health.HealthViewModelTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. Only the latest list request publishes, and superseded work stops without hiding an active search spinner. Open the patient picker and immediately change sort/search; the final list must match that latest choice.

size budget: approximately 90–125 changed lines across 2 files; hard cap 145 lines.

out of scope: No patient-detail behavior, repository query, debounce, DI or health-save changes. Preserve existing public methods and state flows.

---

### 6. Stop CSV preview parsing at the fifth row (roadmap 7+8)

context: `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt:66` iterates CSV records before checking whether five displayed rows have already been collected. Iterator advancement can parse records beyond the visible preview, so a large or malformed trailing record adds unnecessary work or suppresses an otherwise valid preview.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt` — `ResourcesPreviewLoader.getCsvPreview`.
- Edit `app/src/test/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoaderTest.kt` — `ResourcesPreviewLoaderTest`.
- Leave neighboring `getAudioPreview`, `getTextPreview`, cache keys, eviction and error-cache behavior unchanged.

steps:
1. Replace CSV iterator traversal with explicit record reads that check the five-record limit before requesting another record, and stop early at EOF.
2. Keep the current parser configuration, row separator, final trimming, IO dispatcher and resource-closing behavior.
3. Add a CSV containing five valid rows followed by an unterminated quoted sixth record; assert the first five rows still produce a preview.
4. Cover empty input, fewer than five records and a multiline quoted record within the first five; preserve caching and missing-file behavior.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ResourcesPreviewLoaderTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. The loader requests at most five logical CSV records and returns the unchanged five-row format, including with malformed trailing input. Verify inline CSV resources still show their first five rows.

size budget: approximately 45–75 changed lines across 2 files; hard cap 100 lines.

out of scope: No parser dependency, CSV dialect, audio/text-preview or cache changes. Do not impose new per-field truncation limits.

---

### 7. Reuse normalized collection names across search and expansion (roadmap 7+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt:113` lowercases every parent tag name on each refresh. Search, selection and expansion refreshes repeatedly allocate the same normalized strings; a small bounded cache can reuse them while preserving the current Locale.ROOT matching rules.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt` — `CollectionsFragment.refreshTagList`, its name-normalization state and the success branch in `onViewCreated`.
- Edit `app/src/test/java/org/ole/planet/myplanet/ui/resources/CollectionsFragmentTest.kt` — `CollectionsFragmentTest`.
- Leave neighboring `reconcileSelections`, selection semantics and `buildTagDataList` expansion behavior unchanged.

steps:
1. Add a fragment-local access-ordered cache capped at 128 entries, keyed by the exact raw name string and storing its Locale.ROOT lowercase form.
2. Normalize the query once per refresh and consult this cache for nonnull names; keep null-name exclusion and the existing empty-query bypass.
3. Clear the cache on every successful tag-data delivery before refreshing, and ensure renamed mutable tags use their current raw name as the key; never key only by tag ID.
4. Add tests for repeated filtered expansion, duplicate/empty tag IDs, a name changed between refreshes, Unicode matching and eviction past 128 distinct names; keep reflected test setup usable without attaching a view.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.resources.CollectionsFragmentTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. Repeated raw names reuse normalized values, retained entries never exceed 128 and renamed tags match their new names. Verify expanding or selecting a collection preserves the active search and selected tags.

size budget: approximately 80–115 changed lines across 2 files; hard cap 140 lines.

out of scope: No tag-model, ViewModel, selection reconciliation or search-debounce changes. Do not replace Locale.ROOT normalization with a matcher that changes Unicode semantics.

---

### 8. Build tag payloads directly in their required JSON representation (roadmap 1+7+8)

context: `app/src/main/java/org/ole/planet/myplanet/model/TagEntity.kt:63` builds a kotlinx JSON array and immediately converts it into a Gson array. That creates two trees for the same tag-ID payload; constructing the required return representation directly removes the intermediate tree and bridge traversal. Removing this extra model-to-utility dependency also simplifies future extraction toward roadmap 9, while retaining the existing Room and Gson contracts.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/model/TagEntity.kt` — `TagEntity.getTagsArray` and newly unused imports.
- Edit `app/src/test/java/org/ole/planet/myplanet/utils/GsonUtilsKotlinxBridgeTest.kt` — `GsonUtilsKotlinxBridgeTest`, adding payload-equivalence coverage.
- Leave neighboring `toTag`, `matches`, entity fields and Room annotations unchanged; leave existing generic bridge tests intact.

steps:
1. Construct the existing Gson return array directly and append each tag's remote ID in input order, explicitly preserving JSON null for null IDs.
2. Remove only imports made unused by eliminating the intermediate kotlinx builder and utility conversion call.
3. In the existing bridge test class, compare direct tag payloads with the former builder/bridge representation for empty input, null IDs, empty IDs, duplicates and Unicode IDs.
4. Confirm that local IDs are never substituted for remote IDs and the return type remains unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.GsonUtilsKotlinxBridgeTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. Tag serialization creates one JSON array and preserves order, duplicates and nulls. Verify a resource/course search with selected tags still records the same tag IDs in its search activity.

size budget: approximately 40–65 changed lines across 2 files; hard cap 90 lines.

out of scope: No entity schema, serializer return-type, generic bridge or repository changes. No new Android imports or global serialization migration.

---

### 9. Skip unchanged team-page diffs (roadmap 7+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt:37` assigns IDs, calculates a diff and rebuilds the current-ID set for every page update. Equal page configurations need none of that work, so an equality guard removes allocations and diff traversal during redundant team-state refreshes without altering tab behavior.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt` — `TeamPagerAdapter.updatePages`.
- Edit `app/src/test/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapterTest.kt` — `TeamPagerAdapterTest`.
- Leave neighboring constructor initialization, `rebuildIds`, `createFragment`, `getItemId` and `containsItem` unchanged.

steps:
1. Return immediately at the beginning of `updatePages` when the supplied list is structurally equal to the current page list.
2. Preserve the existing ID allocation, diff dispatch and assignment order for every unequal list; do not reduce equality to IDs alone.
3. Extend existing tests with equal-but-distinct lists, then removal, reinsertion and reorder updates; assert stable IDs, page order and containment throughout.
4. Verify repeated equal submissions leave the current-ID set instance untouched, proving that the rebuild was skipped without introducing a production test hook.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.teams.TeamPagerAdapterTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. Equal submissions skip diffing/rebuilding; real configuration changes retain stable IDs and correct containment. Verify team refreshes preserve the selected tab and actual membership-driven tab changes still appear.

size budget: approximately 35–60 changed lines across 2 files; hard cap 85 lines.

out of scope: No page-config, fragment, navigation-architecture or ID-lifetime changes. Do not add asynchronous diffing or cache page fragments.

---

### 10. Sort decorated resource titles without copying the decorated list (roadmap 3+7+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:209` builds title-key pairs, then `sortedBy` copies that decorated list before a final projection. Sorting the newly allocated decoration in place removes one list-sized allocation while retaining once-per-item normalization and stable ordering. Keeping sorting in the ViewModel also supports roadmap 10's hoisted-state direction; no screen migration is required.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` — the title branch of `ResourcesViewModel.applyCurrentSortSynchronous`.
- Edit `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt` — `ResourcesViewModelTest`.
- Leave neighboring date/NONE branches, loading, filter delegation and sort-toggle state behavior unchanged.

steps:
1. Build title-key decorations directly into a mutable list pre-sized to the input length, avoiding a later mutable-copy conversion.
2. Stable-sort that fresh list in place using the existing ascending/descending key semantics, then project back to resource models.
3. Keep Locale.ROOT lowercase keys computed once per item, preserve null-title handling and never mutate the input list or its models.
4. Add tests through the existing public title-sort toggle for both directions, equal normalized keys, null/empty titles and Unicode; assert the source order and model identities remain untouched.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.resources.ResourcesViewModelTest`; `./gradlew testDefaultDebugUnitTest`; `git diff --check`. The title branch creates no second decorated-list copy, and ties preserve source order in both directions. Verify ascending/descending title sorting shows the same library order, including blank titles.

size budget: approximately 55–85 changed lines across 2 files; hard cap 110 lines.

out of scope: No persistent sort cache, comparator-time normalization, dispatcher, repository or screen changes. Do not reverse a sorted list, which would reverse equal-key ordering.
