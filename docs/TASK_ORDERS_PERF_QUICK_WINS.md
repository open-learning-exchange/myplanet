# Task-generation brief — myPlanet refactor round: performance quick wins

date: 2026-09-04
base commit: 9ff1273 (master)
open PRs checked (50): #8367, #8370, #8371, #8398, #8410, #8418, #8431, #8461,
#8467, #8480, #8481, #8483, #8498, #8507, #8530, #8536, #8543, #8550, #8553,
#8571, #8573, #8581, #8582, #8591, #8595, #8596, #8603, #8613, #8614, #8615,
#8618, #8623, #8625, #8636, #8638, #8641, #8647, #8648, #8650, #8651, #8652,
#8653, #8654, #8655, #8656, #8657, #8658, #8659
open-PR file overlap: the `files` list of every open PR above was fetched via
`gh api`; all 916 touched files were excluded from candidacy, and every file
cited below was individually re-confirmed absent from that set before citing.
Every cited path was opened and read on the base commit before inclusion.

Roadmap reminder: 1 data layer · 2 navigation · 3 viewmodel/use-case · 4 DI ·
5 sync/upload consolidation · 6 compose migration · 7 performance · 8 code
health/tests · 9 KMP core (north star) · 10 CMP screens (north star).

---

### 1. stop rebuilding the download notification per file and per tick (roadmap 5+7)

context: DownloadService.updateNotificationForBatchDownload() (DownloadService.kt:189-202) is called once per downloaded file from the queue loop (line 151) and each call re-runs DownloadUtils.createChannels(this) and constructs a brand-new NotificationCompat.Builder. Separately, sendNotification() (lines 451-480) fires on every 500 ms progress tick from copyStreamWithProgress() (lines 428-434), and every tick re-reads both SharedPreferences string sets via getRemainingCount() (lines 171-176) and re-allocates NotificationManagerCompat.from(this) (line 460).
files: app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt (functions processDownloadQueue, updateNotificationForBatchDownload, sendNotification, getRemainingCount). Do NOT touch DownloadWorker.kt or utils/DownloadUtils.kt — same notification code paths, different owners.
steps:
1. create the channel and the NotificationCompat.Builder once per queue session (first file of processDownloadQueue), then mutate the existing builder instead of recreating it per file.
2. compute the remaining count once per file (it only changes in cleanupProcessedUrls), not inside the 500 ms tick.
3. hoist the NotificationManagerCompat.from(this).areNotificationsEnabled() check to once per file.
4. keep every notification ID, channel ID, string and sub-text format byte-identical.
5. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; a multi-file batch download still shows the ongoing notification with correct "N completed, M remaining" sub-text and per-file percentage.
size budget: ~40 changed lines, 1 file
out of scope: no notification redesign, no channel/ID changes, no behavior change to failure or completion paths.

---

### 2. move the achievement CV file copy off the main thread (roadmap 7)

context: EditAchievementFragment.kt:177 launches on the main dispatcher and line 178 calls computeCvFilename() (defined at lines 464-482), which performs contentResolver.openInputStream(uri) and input.copyTo(output) at lines 473-475 — copying a user-picked PDF of arbitrary size on the UI thread. Debug builds run StrictMode detectAll (MainApplication.kt:227-241), so this logs disk-read/write violations on every CV save.
files: app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt (computeCvFilename + its single call site at line 178). The superclass BaseContainerFragment already injects dispatcherProvider (BaseContainerFragment.kt:56-57) — reuse it; do NOT modify BaseContainerFragment.
steps:
1. mark computeCvFilename() as suspend.
2. wrap the uri-check/file-copy body in withContext(dispatcherProvider.io) using the inherited dispatcherProvider.
3. keep the existing deleteCv early-return and the resumeFileName fallback on the calling dispatcher (they do no IO).
4. remove the now-unneeded try/catch Toast only if it becomes unreachable; otherwise keep behavior identical.
5. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; saving an achievement with a newly picked CV still copies the file into the ole/cv directory and shows the "achievement saved" toast.
size budget: ~15 changed lines, 1 file
out of scope: no changes to AchievementViewModel, no changes to CV naming or delete semantics.

---

### 3. run the independent team-courses queries concurrently (roadmap 3+7)

context: TeamCoursesViewModel.loadCourses() (TeamCoursesViewModel.kt:29-37) awaits three suspending repository calls strictly sequentially: getTeamCourseIds (line 31), getCoursesByIds (line 32, depends on the ids), and getTeamCreator (line 33, independent of both). On a cold Room cache the creator query adds a full round-trip of latency to every team-courses tab open for no reason.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt (loadCourses only). Leave TeamsRepository and CoursesRepository interfaces and impls alone — open PRs own those.
steps:
1. wrap the body in coroutineScope { }.
2. start val idsDeferred = async { teamsRepository.getTeamCourseIds(teamId) } and val creatorDeferred = async { teamsRepository.getTeamCreator(teamId) } together.
3. load courses with coursesRepository.getCoursesByIds(idsDeferred.await()), then compute canRemove from creatorDeferred.await() exactly as today.
4. keep the emitted TeamCoursesUiState contents and ordering identical.
5. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; team courses tab still lists the same courses and remove-button visibility still matches team-creator status.
size budget: ~12 changed lines, 1 file
out of scope: no repository or DAO changes, no new caching layer, no changes to addCourses/removeCourse/getAvailableCourses.

---

### 4. run the independent join-request queries concurrently (roadmap 3+7)

context: RequestsViewModel.fetchMembers() (RequestsViewModel.kt:34-41) awaits getRequestedMembers (line 36), getJoinedMemberCount (line 37) and getUserModel (line 38) sequentially although none depends on another; only isTeamLeader (line 39) needs the user. Every open of the members/requests tab pays three serialized Room round-trips where one concurrent batch suffices.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt (fetchMembers only). Do NOT touch MembersFragment.kt, RequestsFragment.kt or any TeamsRepository file.
steps:
1. wrap the launch body in coroutineScope { }.
2. async all three of getRequestedMembers, getJoinedMemberCount and getUserModel.
3. await them and call isTeamLeader with the awaited user id, then emit the same RequestsUiState as today.
4. keep respondToRequest's optimistic-update logic untouched.
5. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; requests list, joined-member count and leader-gated buttons render exactly as before.
size budget: ~12 changed lines, 1 file
out of scope: no repository changes, no changes to the accept/decline flow.

---

### 5. parse each news item's images JSON once in downloadReferencedResources (roadmap 7)

context: News.imagesArray re-parses the images JSON string with Gson on every access (News.kt:84-86 — no caching in the getter). VoicesViewModel.downloadReferencedResources() (VoicesViewModel.kt:218-235) dereferences news?.imagesArray twice per news item (lines 221-222), so a feed of N posts with images triggers 2N full JSON parses on the view-model path every time the feed loads.
files: app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt (downloadReferencedResources only). Do NOT touch model/News.kt here (task 6 owns it) and do NOT touch ui/voices/VoicesAdapter.kt — an open PR owns it, and its parsedImagesArray/rawImages caching pattern (News.kt:77-80) is what the adapter already uses.
steps:
1. inside the loop, hoist val images = news?.imagesArray once per item.
2. use the local for both the isEmpty() == false check and the get(0) access.
3. keep the resourceIds dedup set and the repository calls unchanged.
4. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; opening the voices feed still enqueues downloads for referenced resources exactly as before.
size budget: ~4 changed lines, 1 file
out of scope: no News model changes, no adapter changes, no new caching API.

---

### 6. delete the dead, re-parsing News.messageWithoutMarkdown getter (roadmap 1+8)

context: News.messageWithoutMarkdown (News.kt:112-120) iterates imagesArray — itself a fresh Gson parse per access (lines 84-86) — and string-replaces every image's markdown out of the message. A repository-wide search of app/src/main and app/src/test shows zero call sites: it is dead code that also carries a hidden per-call parse cost for any future caller. Removing it shrinks the data-layer surface (roadmap 1) and deletes untested code (roadmap 8).
files: app/src/main/java/org/ole/planet/myplanet/model/News.kt (remove lines 112-120 only). Do NOT touch the imagesArray getter, calculateSortDate(), or any serialized property — repository files that read them belong to open PRs.
steps:
1. confirm again with grep -rn "messageWithoutMarkdown" app/ that no call site exists.
2. delete the getter and any import that becomes unused.
3. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; app compiles with no references to the removed getter.
size budget: ~10 changed lines, 1 file
out of scope: no other getter removal, no Room schema changes, no proguard/R8 changes.

---

### 7. run the community-tab init queries concurrently (roadmap 3+7)

context: CommunityTabViewModel's init block (CommunityTabViewModel.kt:30-46) awaits getParentCode, getCommunityName, getPlanetType and userSessionManager.getUserModel() strictly sequentially (lines 33-36), although all four are independent. The community tab's first frame is delayed by four serialized suspends on every cold start of the tab.
files: app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt (init block only). Leave ConfigurationsRepositoryImpl.kt and UserSessionManager.kt alone.
steps:
1. wrap the init launch body in coroutineScope { }.
2. async all four reads.
3. await them and emit the identical CommunityTabState.
4. keep the StateFlow null-initial contract unchanged (state stays null until all four resolve).
5. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; community tab still shows the correct community name, planet code and type.
size budget: ~14 changed lines, 1 file
out of scope: no repository changes, no state-shape changes, no caching.

---

### 8. fetch the formatted app size concurrently with the storage breakdown (roadmap 7)

context: StorageBreakdownViewModel.loadStorageBreakdown() (StorageBreakdownViewModel.kt:71-87) awaits totalMemoryCapacity (line 73), getStorageBreakdown (line 74) and getFormattedAppSize() (line 83) sequentially. The app-size computation (PackageManager stats) is independent of both Room reads, so the settings storage screen serializes three suspends that could be two phases.
files: app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownViewModel.kt (loadStorageBreakdown only). Leave SettingsRepository impls and FileUtils.kt alone.
steps:
1. wrap the body in coroutineScope { }.
2. start val appSizeDeferred = async { settingsRepository.getFormattedAppSize() } before the breakdown work.
3. keep the order: total capacity first (it is the input to getStorageBreakdown), then the breakdown, then emit with appSizeDeferred.await().
4. keep the emitted StorageBreakdownState fields identical.
5. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; storage settings screen still shows correct app size, used/total bytes and category breakdown.
size budget: ~12 changed lines, 1 file
out of scope: no repository changes, no UI changes, no polling/caching additions.

---

### 9. hoist the LayoutInflater out of the services-link inflation loop (roadmap 7)

context: CommunityServicesFragment.setRecyclerView() (CommunityServicesFragment.kt:81-124) calls LayoutInflater.from(activity) inside links.forEach (line 85) for every rendered service link. LayoutInflater.from resolves the context theme and service on each call; the community services section re-runs this on every fragment view creation.
files: app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesFragment.kt (setRecyclerView only). Do NOT touch the click-handler coroutine at lines 100-114 or BaseTeamFragment.
steps:
1. resolve val inflater = LayoutInflater.from(parent.context) once before the loop (parent is non-null after the early return at line 82).
2. use the hoisted inflater for every button_single inflation.
3. keep view padding, order of addView calls and all listeners identical.
4. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; community services tab renders the same link buttons in the same order and each still routes to WebViewActivity or TeamDetailFragment.
size budget: ~4 changed lines, 1 file
out of scope: no layout XML changes, no RecyclerView conversion, no padding/dp changes.

---

### 10. cache the step-label format pattern in TakeCourseFragment (roadmap 7)

context: TakeCourseFragment.setStepText() (TakeCourseFragment.kt:188-190) builds its label with String.format(Locale.getDefault(), "${getString(R.string.step)} %d/%d", currentStep, totalSteps) — a resource lookup plus pattern-string concatenation on every call. It runs on each page selection (updateStepDisplay line 196), each next tap (line 342) and each previous tap (line 353), i.e. per swipe through a course.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt (setStepText plus one new private field). Do NOT touch CourseStepFragment.kt, CoursesPagerAdapter or TakeCourseViewModel.
steps:
1. add a private lazy field holding the pattern "${getString(R.string.step)} %d/%d" resolved once per fragment instance (locale changes recreate the activity — SyncActivity.kt:552-553 — so the cache cannot go stale).
2. use the cached pattern in setStepText, keeping Locale.getDefault() as the format locale.
3. run the unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; course stepper header still shows "Step N/M" (localized) correctly while paging.
size budget: ~5 changed lines, 1 file
out of scope: no new string resources, no ViewModel changes, no navigation-logic changes.
