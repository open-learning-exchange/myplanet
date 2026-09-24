# TASK-GENERATION BRIEF — myPlanet refactor round (Performance Quick Wins)

## Open Pull Requests Exclusions
The following currently open pull requests on the `open-learning-exchange/myplanet` repository were analyzed. None of the files modified in the 10 tasks below overlap with any files touched by these open PRs:
* **PR #17435**: resources: added filter labels indications (fixes #16922)
* **PR #17430**: Remove global MainApplication.listener cross-talk on team screens
* **PR #17356**: Refactor PublicSurveyActivity to PublicSurveyViewModel
* **PR #17254**: teams: refactored resources to match library (fixes #16927)
* **PR #17187**: resources: refactored filter logic (fixes #16282)
* **PR #16624**: search: smoother offline full text search matching (fixes #16615)
* **PR #16623**: teams: smoother interactive team tasks status and board handling (fixes #16616)
* **PR #16594**: actions: smoother size labeller fetching (fixes #16344)
* **PR #15951**: teams: smoother repository update requesting (fixes #15568)
* **PR #15825**: local event task reminders workmanager notifications (address #15115)
* **PR #15824**: gamification achievement hub offline badges streaks (address #15114)
* **PR #15820**: teams: smoother task and meetup comment threads managing (fixes #15112)
* **PR #15808**: sync: intelligent incremental sync via couchdb changes feed (fixes #15807)
* **PR #15559**: exam: redesign UI with elapsed timer and cards (fixes #15558)
* **PR #15267**: prevent download popup dialog cropping when text size is large (fixes #15263)
* **PR #15266**: prevent team calendar cropping in landscape mode by using NestedScrollView (fixes #15265)
* **PR #15226**: feat(flutter): Flutter/Dart port of myPlanet (phases 1–28)
* **PR #15108**: fix event calendar marking (fixes #15107)
* **PR #14883**: team: add leaderboard tab (fixes #14880)
* **PR #14650**: survey: smoother submissions display (fixes #14619)
* **PR #14427**: Course streak
* **PR #13928**: Add baseline profile module and installer (fixes #13927)
* **PR #13848**: all: introduce Course/Grade models and wire into UI (fixes #13802)
* **PR #13657**: course: Archive course My Courses library (fixes #13559)
* **PR #13604**: teams: Add sort by completeness option in Survey section (fixes #13590)
* **PR #13415**: voices: Add emoji reactions (fixes #13357)
* **PR #13355**: Add P2P resource sharing (Wi‑Fi P2P) (fixes #13353)
* **PR #13287**: profile: no char limit for edit texts (fixes #13283)
* **PR #10993**: Voices video
* **PR #8175**: roboscript update (fixes #7986)

---

## 10 Independent Refactoring Tasks

### Task 1: Optimize MimeType Resolution Performance and Robustness
* **Roadmap Number**: Serves **7** (optimize performance hotspots) and advances **9** (Kotlin Multiplatform by decoupling from Android platform APIs).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`
* **Target Line Numbers**: Line 233 (`getMimeType`) and Line 197 (`getFileExtension`).
* **Problem Description**: `FileUtils.getMimeType` uses `MimeTypeMap.getFileExtensionFromUrl(filePath)` to extract the extension. This platform API expects a standard URL format and frequently fails or returns empty when dealing with local paths containing spaces, hash symbols, or other special characters. Since `FileUtils` already defines a highly robust custom helper `getFileExtension(filePath)` (Line 197), using the platform API is redundant and prone to parsing errors.
* **Refactoring Steps**:
  1. Modify `getMimeType(filePath: String?)` to call `getFileExtension(filePath)` directly to extract the file extension.
  2. Map the extracted extension through `MimeTypeMap.getSingleton().getMimeTypeFromExtension` or a common map fallback.
  3. Ensure no new dependencies are added and that no TODO comments are left behind.
* **Verification & Testing**:
  1. Run the existing test suite: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.FileUtilsTest"`.
  2. Verify that mime-type mapping continues to work for standard extensions (`.pdf`, `.mp4`, `.epub`).

---

### Task 2: Eliminate On-Scroll Click Listener Allocations in UserArrayAdapter
* **Roadmap Number**: Serves **7** (optimize remaining performance hotspots / scrolling micro-optimizations).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt`
* **Target Line Numbers**: Line 80 (inside `onBindViewHolder`).
* **Problem Description**: In `UserArrayAdapter`, `setOnClickListener` is registered directly inside `onBindViewHolder` on line 80. This allocates a new lambda callback instance on every scroll binding pass of any list item, generating garbage collection churn.
* **Refactoring Steps**:
  1. Refactor the click listener setup by moving the `setOnClickListener` invocation out of `onBindViewHolder`.
  2. Place the listener within the `ViewHolder` constructor or `init` block of the `UserViewHolder` class.
  3. Use the `bindingAdapterPosition` property inside the click listener to safely retrieve the current item index and trigger the listener interface.
* **Verification & Testing**:
  1. Run the unit test suite: `./gradlew testDefaultDebugUnitTest`.
  2. Verify user selection flows function correctly and no click listener crashes occur.

---

### Task 3: Eliminate On-Scroll Click Listener Allocations in ChatAdapter
* **Roadmap Number**: Serves **7** (optimize remaining performance hotspots / scrolling micro-optimizations).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt`
* **Target Line Numbers**: Line 191 (inside `onBindViewHolder`).
* **Problem Description**: In `ChatAdapter`, the message item container sets its click listener inside `onBindViewHolder` at line 191. On long chats, scrolling down creates continuous instantiation of click listeners.
* **Refactoring Steps**:
  1. Move the `setOnClickListener` definition from `onBindViewHolder` to the initialization block of `ChatViewHolder`.
  2. Within the listener, access the item using `getItem(bindingAdapterPosition)` after ensuring the position is not `RecyclerView.NO_POSITION`.
* **Verification & Testing**:
  1. Run the unit test suite: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.chat.ChatAdapterTest"`.
  2. Verify message interactions, context menus, and selections operate smoothly.

---

### Task 4: Eliminate On-Scroll Click Listener Allocations in CheckboxAdapter
* **Roadmap Number**: Serves **7** (optimize remaining performance hotspots / scrolling micro-optimizations).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/components/CheckboxAdapter.kt`
* **Target Line Numbers**: Line 39 (inside `onBindViewHolder`).
* **Problem Description**: The `CheckboxAdapter` sets a click listener on the checked text view inside `onBindViewHolder` on line 39. This adapter is used repeatedly for multiple multi-select lists throughout the app, creating unneeded allocations.
* **Refactoring Steps**:
  1. Relocate the click listener allocation for the checkbox container to the `ViewHolder`'s `init` or constructor block.
  2. Resolve the clicked item position dynamically via `bindingAdapterPosition` within the handler.
* **Verification & Testing**:
  1. Run the unit test suite: `./gradlew testDefaultDebugUnitTest`.
  2. Manually test screen options containing checkboxes to ensure check/uncheck states preserve accuracy.

---

### Task 5: Eliminate On-Scroll Click Listener Allocations in ServerAddressAdapter
* **Roadmap Number**: Serves **7** (optimize remaining performance hotspots / scrolling micro-optimizations).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt`
* **Target Line Numbers**: Line 86 (inside `onBindViewHolder`).
* **Problem Description**: During sync setup, the list of known server addresses allocates a click listener inside `onBindViewHolder` at line 86.
* **Refactoring Steps**:
  1. Relocate the list item click handler from `onBindViewHolder` into the `ViewHolder` initialization phase.
  2. Extract the associated server address details inside the callback by evaluating the current `bindingAdapterPosition`.
* **Verification & Testing**:
  1. Run the unit test suite: `./gradlew testDefaultDebugUnitTest`.
  2. Verify that selecting custom servers from the address lists in login/sync setup continues to select the correct servers.

---

### Task 6: Eliminate Allocation Churn in HealthUsersAdapter View Binding
* **Roadmap Number**: Serves **7** (optimize remaining performance hotspots / scrolling micro-optimizations).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt`
* **Target Line Numbers**: Line 42 (inside `ViewHolder.bind` method).
* **Problem Description**: In `HealthUsersAdapter`, the click listener is bound inside the `ViewHolder`'s custom `bind` method on line 42, which runs during the recycler view's binding phase. This allocates a callback instance for every member rendering pass on the health dashboard.
* **Refactoring Steps**:
  1. Relocate the listener assignment from `bind(member)` into the `init` block of the `ViewHolder`.
  2. Safely resolve the associated member entity via `getItem(bindingAdapterPosition)` within the listener.
* **Verification & Testing**:
  1. Run the unit test suite: `./gradlew testDefaultDebugUnitTest`.
  2. Verify that selecting a health system user loads their medical dashboard cleanly without errors.

---

### Task 7: Convert Sha256Utils Utility Class to Kotlin Object Singleton
* **Roadmap Number**: Serves **7** (optimize performance hotspots) and advances **9** (Kotlin Multiplatform by providing platform-agnostic helper architectures).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt`
* **Target Line Numbers**: Line 7 (class definition) and references like `ConfigurationsRepositoryImpl.kt` Line 434.
* **Problem Description**: `Sha256Utils` is declared as a plain Kotlin `class` that hosts a companion object. Because of this, consumers instantiate it dynamically (e.g., `Sha256Utils().getCheckSumFromFile(f)`). Since the class holds no instance state, allocating new objects is unnecessary and creates overhead during sync.
* **Refactoring Steps**:
  1. Convert the `class Sha256Utils` declaration into a Kotlin `object Sha256Utils`.
  2. Remove the internal `companion object` block and elevate `HEX_CHARS` to a top-level private constant or keep it directly within the `object`.
  3. Update usage references in `ConfigurationsRepositoryImpl.kt` and `Sha256UtilsTest.kt` to call `Sha256Utils.getCheckSumFromFile` statically.
* **Verification & Testing**:
  1. Run the unit test class: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.Sha256UtilsTest"`.
  2. Verify that SHA-512 file checksum verification behaves identically.

---

### Task 8: Eliminate On-Scroll Click Listener Allocations in LifeAdapter
* **Roadmap Number**: Serves **7** (optimize remaining performance hotspots / scrolling micro-optimizations).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`
* **Target Line Numbers**: Line 62 (`holder.imageView.setOnClickListener`) and Line 74 (`holder.visibility.setOnClickListener`).
* **Problem Description**: Inside `onBindViewHolder`, click listeners for both the item's main image and the visibility toggle button are instantiated on every pass (lines 62 and 74). This creates substantial allocation overhead on the central hub dashboard of the application.
* **Refactoring Steps**:
  1. Relocate the click handlers for both `rowLifeBinding.itemImageView` and `rowLifeBinding.visibilityImageButton` into the `init` block of `LifeViewHolder`.
  2. Inside these listeners, obtain the current item by validating `bindingAdapterPosition` and call `getItem(position)`.
  3. Bind the clean item values to `transactionFragment` and `updateVisibility` without re-assigning the listener object.
* **Verification & Testing**:
  1. Run the unit test suite: `./gradlew testDefaultDebugUnitTest`.
  2. Verify dashboard navigation and widget show/hide toggle actions.

---

### Task 9: Optimize Interaction Handlers in SubmissionsAdapter
* **Roadmap Number**: Serves **7** (optimize remaining performance hotspots / scrolling micro-optimizations).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt`
* **Target Line Numbers**: Line 93 (inside `updateSubmissionCount` called by `bind`).
* **Problem Description**: In `SubmissionsAdapter`, `itemView.setOnClickListener` is defined inside `updateSubmissionCount` on line 93, which gets evaluated during binding. This creates layout handler re-allocations on scroll.
* **Refactoring Steps**:
  1. Relocate the click listener allocation for the item root (`itemView`) into the `init` block of the `SubmissionsViewHolder`.
  2. Within the listener body, resolve the item using `getItem(bindingAdapterPosition)` and check its status/count properties dynamically to determine whether to trigger `showAllSubmissions` or `openSubmissionDetail`/`openSurvey`.
  3. Ensure `updateSubmissionCount` strictly configures UI elements (visibility, label texts) without altering event listeners.
* **Verification & Testing**:
  1. Run the unit test suite: `./gradlew testDefaultDebugUnitTest`.
  2. Verify that exam, quiz, and survey submissions are correctly opened.

---

### Task 10: Eliminate On-Scroll Click Listener Allocations in UsersAdapter
* **Roadmap Number**: Serves **7** (optimize remaining performance hotspots / scrolling micro-optimizations).
* **Target File**: `app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt`
* **Target Line Numbers**: Line 31 (inside `onBindViewHolder`).
* **Problem Description**: In `UsersAdapter`, the click handler for selecting a profile item is instantiated during `onBindViewHolder` at line 31.
* **Refactoring Steps**:
  1. Convert `ViewHolder` inside `UsersAdapter` to an `inner class` (or pass the item listener into its constructor).
  2. Move the `setOnClickListener` declaration on `holder.itemView` out of `onBindViewHolder` into the `init` block of the `ViewHolder`.
  3. Obtain the clicked item position dynamically via `bindingAdapterPosition` within the callback to notify the profile selection listener.
* **Verification & Testing**:
  1. Run the unit test suite: `./gradlew testDefaultDebugUnitTest`.
  2. Verify that tapping on any user profile on the community page loads the user's dashboard seamlessly.
