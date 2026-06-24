### Reuse BaseRecyclerFragment adapters
BaseRecyclerFragment currently recreates its adapter during initial load, rating refreshes, and post-add refreshes. Reusing a single adapter instance and pushing data updates through it is a small change that reduces full list rebinds and lowers the risk of merge conflicts across many feature fragments.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=92 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L74-L92"}

:codex-file-citation[codex-file-citation]{line_range_start=106 line_range_end=110 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L106-L110"}

:codex-file-citation[codex-file-citation]{line_range_start=178 line_range_end=182 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L178-L182"}

:::task-stub{title="Reuse adapters in BaseRecyclerFragment"}
1. Keep one adapter instance per fragment view instead of replacing the adapter after each refresh path.
2. Update refresh flows to resubmit data and empty-state changes through the existing adapter.
3. Verify the highest-traffic fragments inheriting this base class still preserve selection and scroll behavior.
:::

### Make ResourcesAdapter selection constant-time
ResourcesAdapter stores selected rows in a mutable list and repeatedly scans it during binding, checkbox toggles, and select-all updates. Converting selection tracking to an ID-backed set is a low-risk micro-optimization that removes repeated linear checks in one of the largest list surfaces.

:codex-file-citation[codex-file-citation]{line_range_start=33 line_range_end=37 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L33-L37"}

:codex-file-citation[codex-file-citation]{line_range_start=113 line_range_end=158 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L113-L158"}

:codex-file-citation[codex-file-citation]{line_range_start=164 line_range_end=176 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L164-L176"}

:::task-stub{title="Optimize ResourcesAdapter selection bookkeeping"}
1. Replace the mutable selected item list with a selected ID set plus lightweight lookups during bind.
2. Keep the existing DiffUtil-based list updates and narrow selection payload handling to only affected rows.
3. Recheck select-all, multi-select, and add-to-library flows after the bookkeeping change.
:::

### Swap VoicesLabelAdapter full refresh for targeted updates
VoicesLabelAdapter already uses ListAdapter and DiffUtils.itemCallback, but it still calls notifyDataSetChanged when the selected label changes. Replacing that with targeted previous/current row updates is a tiny isolated win that is easy to review.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelAdapter.kt#L12-L20"}

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelAdapter.kt#L47-L50"}

:::task-stub{title="Target VoicesLabelAdapter row updates"}
1. Track the previously selected label index and update only the old and new rows when selection changes.
2. Keep the existing ListAdapter and DiffUtils setup unchanged.
3. Confirm chip highlighting still works for the default All state and repeated taps.
:::

### Scope VoicesAdapter invalidations to payloads
VoicesAdapter still invalidates whole visible ranges for team-leader, current-user, and membership state changes. This screen is busy enough that replacing broad notifyItemRangeChanged calls with payload-based or narrower updates should pay off quickly without changing feature behavior.

:codex-file-citation[codex-file-citation]{line_range_start=144 line_range_end=175 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L144-L175"}

:codex-file-citation[codex-file-citation]{line_range_start=214 line_range_end=237 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L214-L237"}

:::task-stub{title="Narrow VoicesAdapter refresh ranges"}
1. Identify which fields actually depend on team-leader, current-user, and non-team-member flags.
2. Replace full-range invalidations with targeted item payloads or only the rows whose controls change.
3. Recheck reply badge, edit/delete controls, and label actions after the narrower updates.
:::

### Move DictionaryActivity Realm work behind the existing data-layer pattern
DictionaryActivity still mixes direct Realm queries, asynchronous transactions, and synchronous search logic inside the Activity. Migrating this screen to the existing DatabaseService and RealmRepository-style access pattern is a contained data-layer cleanup that also removes obvious UI-thread data work.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L60-L66"}

:codex-file-citation[codex-file-citation]{line_range_start=79 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L79-L96"}

:codex-file-citation[codex-file-citation]{line_range_start=109 line_range_end=133 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L109-L133"}

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=75 path=app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt#L49-L75"}

:codex-file-citation[codex-file-citation]{line_range_start=149 line_range_end=199 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L149-L199"}

:::task-stub{title="Refactor DictionaryActivity data access"}
1. Move dictionary reads and writes behind the existing DatabaseService helper pattern and avoid raw Realm work in click handlers.
2. Keep the change local to dictionary data loading and lookup paths instead of widening it into a larger architecture rewrite.
3. Verify first-load import, record count, and word lookup still behave the same offline.
:::

### Finish dispatcher cleanup in utility entry points
Several utility-style entry points still hard-code Dispatchers.Main or Dispatchers.IO even though the project already has DispatcherProvider. Cleaning up these remaining call sites is a small DI and threading win that will make later refactors easier to test.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt#L39-L56"}

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=63 path=app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt#L55-L63"}

:codex-file-citation[codex-file-citation]{line_range_start=127 line_range_end=146 path=app/src/main/java/org/ole/planet/myplanet/utils/CameraUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/CameraUtils.kt#L127-L146"}

:codex-file-citation[codex-file-citation]{line_range_start=168 line_range_end=210 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L168-L210"}

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/utils/DispatcherProvider.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DispatcherProvider.kt#L6-L18"}

:::task-stub{title="Replace remaining hard-coded dispatchers"}
1. Swap remaining hard-coded dispatcher usage in shared utility entry points to the project’s DispatcherProvider.
2. Keep the change limited to existing call sites that already hop threads rather than introducing new abstractions.
3. Recheck toast, guest dialog, image save, and server reachability flows after the cleanup.
:::

### Remove redundant IO context hops around simple delegations
Some repository methods add an extra IO context switch even when they mostly delegate to collaborators that already own their own threading. Trimming these wrappers is a low-risk micro-optimization that also clarifies where dispatcher ownership actually lives.

:codex-file-citation[codex-file-citation]{line_range_start=1159 line_range_end=1165 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1159-L1165"}

:codex-file-citation[codex-file-citation]{line_range_start=1219 line_range_end=1229 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1219-L1229"}

:codex-file-citation[codex-file-citation]{line_range_start=218 line_range_end=220 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L218-L220"}

:codex-file-citation[codex-file-citation]{line_range_start=11 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepositoryImpl.kt#L11-L16"}

:::task-stub{title="Trim redundant withContext wrappers"}
1. Review methods that only delegate to another repository, manager, or helper and remove unnecessary outer IO context switches.
2. Keep wrappers only where a method performs real local blocking work itself.
3. Recheck the affected call paths for behavior changes and cancellation handling.
:::

### Make ProcessUserDataActivity WorkManager observers one-shot
ProcessUserDataActivity attaches lifecycle-wide WorkManager observers for two one-time upload requests and leaves them active until the Activity is destroyed. Converting those to one-shot observation keeps long-running listeners from hanging around after completion with very little code churn.

:codex-file-citation[codex-file-citation]{line_range_start=196 line_range_end=213 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L196-L213"}

:codex-file-citation[codex-file-citation]{line_range_start=216 line_range_end=241 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L216-L241"}

:::task-stub{title="Use one-shot WorkManager observation"}
1. Replace lifecycle-long observers for one-time upload work with a one-shot completion observation pattern.
2. Ensure success and dismiss logic still runs exactly once for each upload mode.
3. Verify repeated uploads do not stack duplicate observers in the same Activity instance.
:::

### Debounce the remaining search boxes that still fire on every keystroke
ResourcesFragment already delays search work by 300 ms, but SurveyFragment and SubmissionsFragment still trigger filtering immediately on every text change. Aligning those screens to the same lightweight debounce pattern will cut avoidable list work without introducing new architecture.

:codex-file-citation[codex-file-citation]{line_range_start=271 line_range_end=305 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L271-L305"}

:codex-file-citation[codex-file-citation]{line_range_start=96 line_range_end=105 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt#L96-L105"}

:codex-file-citation[codex-file-citation]{line_range_start=75 line_range_end=83 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsFragment.kt#L75-L83"}

:::task-stub{title="Debounce survey and submission search input"}
1. Reuse the same short debounce behavior already present in resources search for the remaining immediate-filter text watchers.
2. Keep the work scoped to the existing fragments and their current ViewModel entry points.
3. Verify fast typing, backspacing, and empty-state behavior after the debounce is added.
:::

### Stop resubmitting unchanged submission lists for metadata-only updates
SubmissionsFragment recombines submissions, exam metadata, and submission counts, then resubmits the list every time any of those inputs changes. The adapter already has payload hooks for exam and count changes, so separating metadata refreshes from list resubmission is an easy performance win that also reinforces the current DiffUtil setup.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=72 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsFragment.kt#L60-L72"}

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt#L44-L50"}

:codex-file-citation[codex-file-citation]{line_range_start=63 line_range_end=87 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt#L63-L87"}

:codex-file-citation[codex-file-citation]{line_range_start=122 line_range_end=148 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt#L122-L148"}

:::task-stub{title="Split submission list diffs from metadata refreshes"}
1. Submit a new list only when submission rows actually change, not when exam names or count maps are merely refreshed.
2. Use the adapter’s existing payload path for exam-title and submission-count updates.
3. Verify row clicks, count badges, and survey versus exam navigation still match the current behavior.
:::
