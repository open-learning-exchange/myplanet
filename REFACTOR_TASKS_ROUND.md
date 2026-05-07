### Pre-size Glide avatar loads in list adapters

`MembersAdapter` and `UsersAdapter` bind avatars on every `onBindViewHolder` without `.override(w, h)`, forcing Glide to measure the target view per-bind in a hot scrolling path. The placeholder/error bitmaps are already small circular avatars, so a fixed override pre-resolves dimensions, removes a layout pass, and lets Glide reuse cache keys consistently.

:codex-file-citation[codex-file-citation]{line_range_start=97 line_range_end=103 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt#L97-L103"}
:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt#L45-L51"}

:::task-stub{title="Add Glide .override() to MembersAdapter and UsersAdapter avatar loads"}
1. In `MembersAdapter.bind()`, add `.override(R.dimen.member_avatar_size, R.dimen.member_avatar_size)` (or a hardcoded px value matching the layout) before `.circleCrop()`.
2. In `UsersAdapter.ViewHolder.bindView()`, apply the same override to the `binding.userProfile` Glide load.
3. Run the app, scroll the members and users lists, and confirm avatars render at the same visual size with no regression.
:::

### Pre-size Glide load in UserProfileFragment.loadProfileImage

The single profile-photo load goes through Glide without `.override()`, so Glide has to wait for the `ImageView` to be measured before kicking off decode/cache lookup. Because the avatar size is fixed by the layout, an explicit override removes that synchronization and gives a snappier first paint on the user profile screen.

:codex-file-citation[codex-file-citation]{line_range_start=232 line_range_end=266 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L232-L266"}

:::task-stub{title="Add explicit .override() to UserProfileFragment profile-image Glide load"}
1. Add `.override(R.dimen.profile_image_size, R.dimen.profile_image_size)` (or matching px constant) on the `Glide.with(this).load(profileImageUrl)` chain before `.circleCrop()`.
2. Verify the placeholder and error drawables still render at the same on-screen size.
3. Manually open the profile screen and confirm there is no visible blur/upscale regression.
:::

### Replace hardcoded Dispatchers in DashboardActivity

`DashboardActivity` references `Dispatchers.IO` and `Dispatchers.Main` directly inside lifecycle code, bypassing the existing `DispatcherProvider` that the rest of the codebase wires through Hilt. Switching to the injected provider unblocks future test seams and keeps dispatcher selection consistent across the activity.

:codex-file-citation[codex-file-citation]{line_range_start=160 line_range_end=170 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L160-L170"}
:codex-file-citation[codex-file-citation]{line_range_start=595 line_range_end=605 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L595-L605"}

:::task-stub{title="Inject DispatcherProvider into DashboardActivity and replace direct Dispatchers refs"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to `DashboardActivity`.
2. Replace `withContext(Dispatchers.IO)` at the notification-manager init site with `withContext(dispatcherProvider.io)`.
3. Replace `withContext(Dispatchers.Main)` in the system-notification receiver block with `withContext(dispatcherProvider.main)`.
4. Remove now-unused `kotlinx.coroutines.Dispatchers` imports.
:::

### Replace hardcoded Dispatchers.IO in TeamsTasksFragment

`TeamsTasksFragment` launches a child coroutine with a fully-qualified `kotlinx.coroutines.Dispatchers.IO` and then hops back via `Dispatchers.Main`, instead of going through the project's `DispatcherProvider`. The fragment already has Hilt wiring, so the swap is mechanical and removes a hard dependency on the global dispatcher.

:codex-file-citation[codex-file-citation]{line_range_start=228 line_range_end=240 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt#L228-L240"}

:::task-stub{title="Use injected DispatcherProvider in TeamsTasksFragment assignee lookup"}
1. Inject `DispatcherProvider` into `TeamsTasksFragment`.
2. Change `lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO)` to `lifecycleScope.launch(dispatcherProvider.io)`.
3. Change the inner `withContext(kotlinx.coroutines.Dispatchers.Main)` to `withContext(dispatcherProvider.main)`.
4. Drop the fully-qualified `kotlinx.coroutines.Dispatchers` references.
:::

### Replace hardcoded Dispatchers.IO in MarkdownDialogFragment, AchievementFragment, and SettingsActivity

Three small UI components still pin `Dispatchers.IO`/`Dispatchers.Main` directly in `lifecycleScope.launch(...)` and `withContext(...)` blocks. Each is a self-contained one-or-two-line swap to the injected `DispatcherProvider`, with no behavioral change.

:codex-file-citation[codex-file-citation]{line_range_start=147 line_range_end=153 path=app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt#L147-L153"}
:codex-file-citation[codex-file-citation]{line_range_start=98 line_range_end=110 path=app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt#L98-L110"}
:codex-file-citation[codex-file-citation]{line_range_start=215 line_range_end=280 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt#L215-L280"}

:::task-stub{title="Swap hardcoded Dispatchers for DispatcherProvider in three UI components"}
1. Inject `DispatcherProvider` into `MarkdownDialogFragment`, `AchievementFragment`, and `SettingsActivity`.
2. Replace each `lifecycleScope.launch(Dispatchers.IO)` and `withContext(Dispatchers.IO)` with `dispatcherProvider.io`.
3. Replace each `withContext(Dispatchers.Main)` with `dispatcherProvider.main`.
4. Remove the now-unused `import kotlinx.coroutines.Dispatchers` lines from all three files.
:::

### Drop redundant Dispatchers.Main on lifecycleScope in LoginActivity

`lifecycleScope` is already main-bound on activities, so passing `Dispatchers.Main` to `lifecycleScope.launch(...)` adds an extra dispatch hop with no benefit. Removing the explicit dispatcher matches the rest of the codebase and is a pure micro-win.

:codex-file-citation[codex-file-citation]{line_range_start=602 line_range_end=608 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L602-L608"}

:::task-stub{title="Remove redundant Dispatchers.Main from lifecycleScope.launch in LoginActivity"}
1. Change `lifecycleScope.launch(Dispatchers.Main) { ... }` at the cited site to `lifecycleScope.launch { ... }`.
2. Confirm `Dispatchers` is still imported elsewhere in the file; if not, drop the import.
3. Smoke-test login flow on a device — no behavior should change.
:::

### Cache the EntryPoint lookup in MainApplication.createLog

`createLog` calls `EntryPointAccessors.fromApplication(...)` twice in the same coroutine just to read two dependencies from the same entry-point interface, doing duplicate reflection-backed lookups on every log write. A single resolution into a local `val` is a one-line tightening that removes obvious wasted work on a hot logging path.

:codex-file-citation[codex-file-citation]{line_range_start=113 line_range_end=125 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L113-L125"}

:::task-stub{title="Resolve CoreDependenciesEntryPoint once per createLog call"}
1. Replace the two `EntryPointAccessors.fromApplication(context, CoreDependenciesEntryPoint::class.java)` calls with a single `val entryPoint = EntryPointAccessors.fromApplication(context, CoreDependenciesEntryPoint::class.java)`.
2. Read both `userSessionManager` and `sharedPrefManager` off that local.
3. Run the app and trigger a path that calls `createLog` (e.g., a sync or download action) to verify logs still write.
:::

### Remove OnGlobalLayoutListener leak in DashboardActivity

`DashboardActivity` adds an `OnGlobalLayoutListener` to `binding.root.viewTreeObserver` in its init flow but never removes it, so the listener keeps the activity alive across rotations and during finish if the dashboard is re-entered. Adding a paired `removeOnGlobalLayoutListener` in `onDestroy` is a self-contained leak fix.

:codex-file-citation[codex-file-citation]{line_range_start=278 line_range_end=283 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L278-L283"}

:::task-stub{title="Unregister DashboardActivity OnGlobalLayoutListener in onDestroy"}
1. Keep the existing `onGlobalLayoutListener` field reference in `DashboardActivity`.
2. In `onDestroy`, call `binding.root.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalLayoutListener)` before `super.onDestroy()`.
3. Null-guard the binding/listener to handle early-destroy paths.
:::

### Guard against duplicate systemNotificationReceiver registration in DashboardActivity

`registerSystemNotificationReceiver` is called from `initializeDashboard()` which itself can run more than once across configuration changes and re-entry. The current code can register the same receiver twice and only `unregisterSystemNotificationReceiver` once, leaking a registration. A simple `isRegistered` flag plus idempotent unregister fixes it without restructuring the activity.

:codex-file-citation[codex-file-citation]{line_range_start=587 line_range_end=643 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L587-L643"}

:::task-stub{title="Make systemNotificationReceiver registration idempotent in DashboardActivity"}
1. Add a `private var systemNotificationReceiverRegistered = false` flag.
2. In `registerSystemNotificationReceiver`, return early if the flag is `true`; otherwise register and set the flag.
3. In `unregisterSystemNotificationReceiver`, guard the `unregisterReceiver` call with the flag and reset it after.
4. Wrap the `unregisterReceiver` in a try/catch on `IllegalArgumentException` for safety.
:::

### Move heavy initialization out of UserProfileFragment.onCreateView

`onCreateView` does dependency wiring, RecyclerView setup, Flow observation, and two ViewModel data loads inline before returning the inflated view. Inflation should stay cheap; moving the side-effect-heavy calls into `onViewCreated` and structured coroutines removes blocking work from the inflate path without changing screen behavior.

:codex-file-citation[codex-file-citation]{line_range_start=160 line_range_end=171 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L160-L171"}

:::task-stub{title="Move UserProfileFragment side-effects from onCreateView to onViewCreated"}
1. Keep only `_binding = FragmentUserProfileBinding.inflate(...)` and `return binding.root` in `onCreateView`.
2. Move `initializeDependencies()`, click listeners, `setupStatsRecycler()`, `observeUserProfile()`, `viewModel.loadUserProfile(...)`, and `viewModel.getOfflineVisits()` into `onViewCreated`.
3. Verify the profile screen still renders identical state after navigation and rotation.
:::

### Move VoicesAdapter.preParseNews out of submitList into onBindViewHolder

`submitList` walks the entire incoming list and calls `preParseNews` on every item up front, even though most items will never be visible. The `parsed*` fields are already cached on `RealmNews`, so deferring parsing to `onBindViewHolder` (and to `parentNews` in `init`, which already happens) keeps the cached behavior while turning O(n) eager parsing into amortized lazy work as items scroll into view.

:codex-file-citation[codex-file-citation]{line_range_start=120 line_range_end=135 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L120-L135"}
:codex-file-citation[codex-file-citation]{line_range_start=489 line_range_end=515 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-WPKg8/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L489-L515"}

:::task-stub{title="Defer VoicesAdapter preParseNews from submitList loop to onBindViewHolder"}
1. Remove the `list?.forEach { preParseNews(it) }` calls from both `submitList` overloads.
2. Call `preParseNews(news)` near the top of `onBindViewHolder` for the bound item only (keep the existing `parsed*`/`raw*` cache check so repeated binds are free).
3. Leave the existing `init { preParseNews(parentNews) }` and reply-thread `preParseNews(parentNews)` calls untouched.
4. Scroll a long Voices list and confirm content renders the same; quick-scroll to confirm no jank regression.
:::
