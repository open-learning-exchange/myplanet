# Refactor Roadmap — Performance Quick-Wins (10 granular tasks)

> Each task lives in a **different file** so they can be reviewed/merged in the same round without conflicting.
> Scope: performance quick wins, micro-optimizations, removing obvious inefficiencies. No use cases, no Compose, no big rewrites, no unused code.

### Gate Enterprises finances flow collectors to STARTED

`EnterprisesFinancesFragment` collects two hot `StateFlow`s inside a bare `viewLifecycleOwner.lifecycleScope.launch`, so both collectors keep running while the fragment is stopped/backgrounded. The rest of the app already standardized on `collectLatestWhenStarted` / `repeatOnLifecycle(STARTED)`; this is the last ungated collector and a one-file fix.

:codex-file-citation[codex-file-citation]{line_range_start=229 line_range_end=243 path=app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesFragment.kt#L229-L243"}

:::task-stub{title="Lifecycle-gate the transaction/isMember flow collectors in EnterprisesFinancesFragment"}
1. In `EnterprisesFinancesFragment`, wrap the `isMemberFlow` and `viewModel.transactions` collectors so they run only in the STARTED state, reusing the existing `collectLatestWhenStarted` helper used elsewhere in the codebase.
2. Remove the outer bare `viewLifecycleOwner.lifecycleScope.launch { launch { ... }; launch { ... } }` scaffolding in favor of the gated helper.
3. Verify the "add transaction" visibility and no-data states still update on resume.
:::

### Precompute notification HTML instead of parsing per bind

`NotificationsAdapter.ItemViewHolder.bind` calls `Html.fromHtml(...)` on every bind. HTML parsing is comparatively expensive and re-runs on every scroll/rebind for the same item. Parse once when the list item is built (or cache the parsed `CharSequence` on the model/item) so bind only assigns it.

:codex-file-citation[codex-file-citation]{line_range_start=111 line_range_end=114 path=app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt#L111-L114"}

:::task-stub{title="Cache parsed notification text so Html.fromHtml runs once per item, not per bind"}
1. Compute the `Html.fromHtml(formattedText, FROM_HTML_MODE_LEGACY)` result once where the `NotificationListItem.Item` is created (or lazily cache it on the item), instead of inside `bind`.
2. In `bind`, assign the cached `CharSequence` to `binding.title.text` directly.
3. Confirm read/unread styling and selection mode still render correctly.
:::

### Replace full exam-table scan in NotificationsRepository.getSurveyId with a keyed query

`getSurveyId` loads every exam via `examDao.getAll()` and then does an in-memory `firstOrNull { it.name == relatedId }`. Since this runs while building notifications, it becomes a full-table scan per lookup. Add a DAO query that matches by name in SQL.

:codex-file-citation[codex-file-citation]{line_range_start=154 line_range_end=158 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L154-L158"}

:::task-stub{title="Add a by-name exam DAO query and use it in NotificationsRepository.getSurveyId"}
1. Add a suspend query to the exam DAO, e.g. `SELECT id FROM ... WHERE name = :name LIMIT 1`, returning the id (or the row).
2. Replace `examDao.getAll().firstOrNull { it.name == it }?.id` in `getSurveyId` with the new query.
3. Keep the null-relatedId short-circuit unchanged.
:::

### Push the team filter into SQL in TeamsRepository member-admin lookup

At line 1052 the code calls `teamDao.getAll().filter { it.teamId == teamId }`, loading every team/membership doc into memory to keep the ones for a single team. A keyed DAO query (there are already team-id-scoped queries in this DAO) avoids the full scan.

:codex-file-citation[codex-file-citation]{line_range_start=1050 line_range_end=1052 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1050-L1052"}

:::task-stub{title="Use a team-id-scoped DAO query instead of getAll().filter in TeamsRepository admin lookup"}
1. Reuse (or add) a `teamDao` query that returns rows for a given `teamId` directly in SQL.
2. Replace `teamDao.getAll().filter { it.teamId == teamId }.mapNotNull { it.userId }` at line 1052 with the scoped query.
3. Keep this change confined to that single method to avoid touching the rest of the large file.
:::

### Batch health-examination "mark uploaded" updates in one transaction

`markHealthExaminationsUploaded` iterates the id→rev map and issues a separate `markUploaded` UPDATE per entry with no surrounding transaction — N round-trips and N implicit transactions after every health sync. Wrap the loop in a single Room `@Transaction`.

:codex-file-citation[codex-file-citation]{line_range_start=61 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt#L61-L65"}

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=29 path=app/src/main/java/org/ole/planet/myplanet/data/room/dao/HealthExaminationDao.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/data/room/dao/HealthExaminationDao.kt#L28-L29"}

:::task-stub{title="Wrap the per-id markUploaded loop in a single Room transaction"}
1. Add a `@Transaction suspend` method to `HealthExaminationDao` that accepts the id→rev map (or a list of pairs) and loops over the existing `markUploaded` query internally.
2. Change `HealthRepositoryImpl.markHealthExaminationsUploaded` to call the new transactional DAO method instead of looping in the repository.
3. Verify health sync still clears the `isUpdated` flag for each uploaded examination.
:::

### Filter team voices in SQL instead of in-memory matchesTeam

`getTopLevel().filter { matchesTeam(it, teamId) }` loads all top-level news rows and filters them in Kotlin (two call sites use the same pattern). For team-scoped voices this reads far more rows than needed; a team-scoped DAO query trims the working set.

:codex-file-citation[codex-file-citation]{line_range_start=253 line_range_end=256 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L253-L256"}

:::task-stub{title="Add a team-scoped top-level news query and drop the in-memory matchesTeam filter"}
1. Add a `newsDao` query that returns top-level messages already scoped to a team id (mirroring the predicate in `matchesTeam`).
2. Replace the `newsDao.getTopLevel().filter { matchesTeam(it, teamId) }` call site(s) with the scoped query.
3. Keep the non-team (global) code path using the existing top-level query unchanged.
:::

### Cache colors in ProgressGridAdapter instead of resolving per bind

`ProgressGridAdapter.onBindViewHolder` calls `ContextCompat.getColor(...)` up to three times per cell. Progress grids render many small cells, so this is repeated resource resolution on every bind. Resolve the colors once (the codebase already uses the `by lazy` field pattern in `HealthExaminationAdapter` / `ChatHistoryAdapter`).

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=48 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressGridAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressGridAdapter.kt#L27-L48"}

:::task-stub{title="Resolve ProgressGridAdapter cell colors once as lazy fields"}
1. Add `by lazy` (or init-time) fields for the green/yellow/main colors on the adapter, resolved from the injected `context`.
2. Use those fields in `onBindViewHolder` instead of calling `ContextCompat.getColor` each bind.
3. No behavior change — verify completed/in-progress/locked cell backgrounds are identical.
:::

### Replace full survey scan in SurveysRepository.getSurvey fallback with a keyed query

`getSurvey` first tries `examDao.getById(id)`, then falls back to `examDao.getByType("surveys").firstOrNull { it.name == id }`, loading all surveys to match by name in memory. Add a by-type-and-name DAO query for the fallback.

:codex-file-citation[codex-file-citation]{line_range_start=370 line_range_end=373 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L370-L373"}

:::task-stub{title="Add a type+name survey DAO query for the getSurvey fallback"}
1. Add a suspend query, e.g. `SELECT * FROM ... WHERE type = 'surveys' AND name = :name LIMIT 1`, to the exam DAO.
2. Replace `examDao.getByType("surveys").firstOrNull { it.name == id }` in `getSurvey` with the new query.
3. Keep the primary `examDao.getById(id)` path first.
:::

### Replace full user scan in UserRepository.findUserByName with a case-insensitive query

`findUserByName` loads every user via `userDao.getAll()` and filters with `firstOrNull { it.name.equals(name, ignoreCase = true) }`, even though a `userDao.getByName` query already exists. Add a case-insensitive DAO query (`... WHERE name = :name COLLATE NOCASE LIMIT 1`) to avoid loading the whole user table.

:codex-file-citation[codex-file-citation]{line_range_start=113 line_range_end=116 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L113-L116"}

:::task-stub{title="Add a case-insensitive by-name user query and use it in findUserByName"}
1. Add a suspend `getByNameIgnoreCase(name)` query to the user DAO using `COLLATE NOCASE` (or `LOWER(name) = LOWER(:name)`).
2. Replace `userDao.getAll().firstOrNull { it.name.equals(name, ignoreCase = true) }` in `findUserByName` with the new query.
3. Leave the exact-match `getUserByName` path untouched.
:::

### Cache colors in ChatShareTargetAdapter view holders

Both `GroupViewHolder.bind` and `ChildViewHolder.bind` call `ContextCompat.getColor(...)` on every bind for text/background colors that never change. Resolve them once so bind only sets already-resolved ints.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/d0425c2a3a345247b4c30b0b02c736381abfb8fb/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt#L49-L66"}

:::task-stub{title="Resolve ChatShareTargetAdapter row colors once instead of per bind"}
1. Resolve the `daynight_textColor` and `multi_select_grey` colors once (lazy adapter field or cached in the holder from `itemView.context`).
2. Use the cached values in `GroupViewHolder.bind` and `ChildViewHolder.bind` instead of calling `ContextCompat.getColor` each time.
3. No visual change expected — verify expanded/collapsed rows still look identical.
:::

## Testing

- After each task, run the CI unit-test command `./gradlew testDefaultDebugUnitTest`; the repositories and adapters touched here have existing tests under `app/src/test/`.
- For the new DAO queries (tasks 3, 5, 6, 8, 9), add a focused DAO/repository unit test asserting the keyed query returns the same result the old in-memory filter produced (including the case-insensitive match for task 9).
- For the adapter tasks (2, 7, 10), a quick manual scroll check plus existing adapter tests are sufficient; no behavior should change.
