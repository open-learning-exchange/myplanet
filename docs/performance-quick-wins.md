# Performance quick wins — work orders

date: 2026-11-10
base commit: 2093a6224b221079f1413e2ab1f22cebec8cf736
open PRs checked: full file lists for #18555, #18554, #18551, #18540, #18539, #18455 (updated within the last week; none tagged review ready or merge). Also listed, file lists not retrieved: #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075. Tasks below avoid every file from the six verified lists, and avoid areas those older titles name (team calendar, event calendar, exam UI, teams repository, sync, ratings, survey-submission UI, gradle wrapper, WebViewActivity, download binder).

Each task is independently mergeable. Do not edit a file named by another task. Evidence is from the base commit above.

---
### 1. skip expired challenge queries on dashboard open (roadmap 7)
context: DashboardActivity.kt:204 calls DashboardViewModel.evaluateChallengeDialog on every dashboard init, after notifications are checked.
The prompt is allowed only for a non-guest user, only when serverUrl is in validUrls, and only when today is after 2024-11-30 and before 2025-01-16.
Those checks sit at DashboardViewModel.kt:313-317, after five repository calls have already been started. Line 299 launches progressRepository.fetchCourseData before the date is considered.
On 2026-11-10 that window is closed, so every home open still pays for course, voice, and survey reads that cannot show a dialog.
This does not move roadmap 9 or 10: the viewmodel stays Android-bound, and this is not a Compose screen.
files: app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt, function evaluateChallengeDialog (lines 286-346).
Leave DashboardActivity.kt alone. Leave ProgressRepository, VoicesRepository, CoursesRepository, and SubmissionsRepository alone.
steps:
1. At the top of evaluateChallengeDialog, before viewModelScope.launch, compute the same shouldPrompt predicate the function already uses: not isGuest, today after 2024-11-30, today before 2025-01-16, and serverUrl in validUrls.
2. If that predicate is false, return immediately. Do not start the coroutine, and do not call fetchCourseData, getCommunityVoiceDateCount, getCourseTitleById, hasPendingSurvey, or hasUserCompletedSync.
3. Leave the existing query block at lines 298-333 in place for a device clock that is still inside the window, including the later hasUserCompletedSync call that runs only when prereqsMet is true.
4. Do not change ChallengeDialogData fields or the event emit.
5. Do not delete evaluateChallengeDialog. DashboardActivity.kt:204 still calls it.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open the dashboard as a signed-in user and as a guest. No challenge dialog appears, and notifications plus bottom navigation still load as before.
size budget: ~15 changed lines, 1 file
out of scope: do not retune the 2024-2025 dates, do not remove the query path, and do not migrate this screen to Compose.
---
### 2. batch earned-exam pass lookups when a course opens (roadmap 1+7)
context: TakeCourseViewModel.kt:67 calls ProgressRepositoryImpl.saveEarnedExamPasses before the blocking step can be shown.
saveEarnedExamPasses (ProgressRepositoryImpl.kt:166-174) then calls submissionsRepository.getSubmissionsByParentId once per step that is not already passed.
That repository method hydrates answers (SubmissionsRepositoryImpl.kt:365-366) because isPassingAttempt reads attempt.answers.
passedStepNums is already one query. requiredExamsByStepId (lines 192-197) still scans exams with firstOrNull once per required step.
getExamSubmissionsByUser (SubmissionsRepository.kt:81) and getAnswersBySubmissionIds (SubmissionsRepository.kt:82) already exist, and the DAO method does not hydrate answers.
The progress file still has other Android calls, so this task does not finish roadmap 9.
files: app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt, functions saveEarnedExamPasses and requiredExamsByStepId.
Do not edit TakeCourseViewModel.kt, SubmissionDao.kt, SubmissionsRepository.kt, SubmissionsRepositoryImpl.kt, or Answer.kt.
steps:
1. After the existing early returns, collect parent ids of the form "${exam.id}@$courseId" only for steps whose step number is not already in passedStepNums.
2. Load that user's exam rows once with the existing getExamSubmissionsByUser. Keep rows whose status is exactly "complete" and whose parentId is in the collected set.
3. Load answers for those row ids once with getAnswersBySubmissionIds. Group by Answer.submissionId and assign answers as a MutableList before isPassingAttempt, matching the assignment shape in hydrateSubmissions.
4. Keep the current pass rule: exam.isPassingAttempt(attempt.grade, answers non-empty and every answer grade == 1), then the existing saveCourseProgress call.
5. In requiredExamsByStepId, walk the exam list once and keep the first exam per stepId that is in requiredStepIds and has noOfQuestions > 0. Do not use associateBy. It keeps the last exam, and firstOrNull keeps the first.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open a course that has passing-required steps, including one already passed and one not passed. The blocking step index and any newly saved pass match the pre-change behavior, with one exam-submission query and one answer query instead of one submission query per step.
size budget: ~45 changed lines, 1 file
out of scope: no new DAO query, no status widening to "requires grading", and no change to the getBlockingStepIndex return rule.
---
### 3. parallelize independent course-detail reads (roadmap 1+7)
context: CoursesRepositoryImpl.getCourseDetailModel (lines 130-179) loads the course through a Flow, then inside the map runs five independent suspend reads one after another.
Those reads are getUserModel, getCourseExamCount, getCourseOnlineResources, getCourseOfflineResources, and getCourseSteps (lines 134-138).
Question counts need the step ids from getCourseSteps. The rating summary needs the user id.
Opening course detail therefore waits on the sum of those reads.
The repository already imports android.content.Context, so this task does not finish roadmap 9. The edited function must not add any new android.* use.
files: app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt, function getCourseDetailModel only.
Do not edit getCourseStepData, getMyCourses, TakeCourseViewModel.kt, or any DAO.
steps:
1. After the null-course return, wrap the rest of the map body in coroutineScope. The flow map lambda is already a suspend context.
2. Start the five independent reads with async and await them before building step ids.
3. After user and rawSteps are available, start question-count assembly and getRatingSummary together when userId is non-null. Rating still stays null when userId is null.
4. Build the same CourseDetailModel fields, in the same meaning: course, user, ratingSummary, examCount, resources, downloadedResources, steps.
5. If a child call throws, let await surface that failure. Do not catch and swallow it. Add only the coroutine imports this needs. Do not wrap the reads in a Room transaction.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open a course detail screen. Title, exam count, online resources, downloaded resources, steps, question counts, and rating match the pre-change screen, with no new stuck spinner.
size budget: ~30 changed lines, 1 file
out of scope: do not parallelize getCourseStepData, and do not change userId.contains matching in getMyCourses.
---
### 4. overlap user, course, steps, and progress on course open (roadmap 3+7)
context: TakeCourseViewModel.loadCourse (lines 117-127) awaits getUserModel, then getCourseById, then getCourseSteps, then getCourseProgress, then getBlockingStepIndex.
The user read does not depend on the course read. After the course is non-null, steps and progress do not depend on each other.
getBlockingStepIndex (line 66) calls saveEarnedExamPasses and needs the step list, so it must stay last.
Course open is on the critical path and currently sums those waits.
This is viewmodel orchestration, not a Compose migration, so it does not move roadmap 10.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt, function loadCourse only.
Do not edit ProgressRepositoryImpl.kt, CoursesRepositoryImpl.kt, or the body of getBlockingStepIndex.
steps:
1. Inside the existing try, start getUserModel and getCourseById with async so they run together.
2. If the course is null, set TakeCourseUiState.NotFound and return without fetching steps or progress.
3. Start getCourseSteps(courseId) and getCourseProgress(listOf(courseId), userModel?.id) together. Progress still receives the same nullable user id.
4. Await both, then call the existing getBlockingStepIndex(steps, userModel, courseId).
5. Emit the same TakeCourseUiState.Success fields: course, steps, userModel, progress, blockingStepIndex. Keep the existing catch that prints the stack trace. Import coroutineScope and async. Add no dependency.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open an existing course and a missing course id. The existing course shows the same steps, progress, and blocking step. The missing id still shows not-found and does not stay on Loading.
size budget: ~20 changed lines, 1 file
out of scope: do not batch the exam-pass queries in this task, and do not rewrite the screen in Compose.
---
### 5. index library models instead of scanning the full list (roadmap 7)
context: ResourcesFragment stores the visible library in allResourceModels (line 86) and replaces that list at lines 212 and 249.
Later taps scan the whole list. onSelectedListChange (line 574) and onResourceClicked (line 602) find by item.id.
onSingleResourceDownloaded (line 596) finds by item.resourceLocalAddress. onTagClicked(TagItem) (line 583) flatMaps every tag and then finds by tag id.
On a large library each tap walks every row, and the tag path also allocates the flattened tag list.
This fragment is not a Compose screen, so the task does not move roadmap 10.
files: app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt only.
Do not edit ResourcesAdapter.kt, ResourcesCardBinder.kt, ResourcesListFilter.kt, ResourcesViewModel.kt, or BaseResourceFragment.kt. Those neighbors are either owned by open pull requests or belong to other tasks.
steps:
1. Add one private replace function that assigns allResourceModels and rebuilds three maps: first model by item.id, first model by item.resourceLocalAddress, and first tag by tag id.
2. Keep the first match. Do not use associateBy. It keeps the last duplicate, and the current find calls return the first.
3. Call that replace function from both assignment sites, line 212 and line 249, so a refresh cannot leave the maps stale.
4. Replace the four scans with map lookups. An absent tag id and an absent local address must still no-op, as they do today.
5. Leave selectedItems, chip rendering, download refresh, and filter scheduling behavior unchanged.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
In My Library, multi-select rows, open a resource, click a tag chip, and finish a download. The same resources stay selected, the same detail opens, the same tag is added, and the downloaded row still marks offline.
size budget: ~40 changed lines, 1 file
out of scope: no adapter rebind changes, and no filter-predicate changes.
---
### 6. cache downloaded-file hits while course-step resources scroll (roadmap 7)
context: InlineResourceAdapter.updateStatusAndPreview (lines 164-167) calls FileUtils.checkFileExist on a background dispatcher for every bind, then stats the concrete resource file again at line 187.
htmlCoverCache (line 56) already avoids repeating the HTML cover walk.
onCurrentListChanged (lines 82-91) drops that cover cache when the item disappears or resourceLocalAddress changes, and onDetachedFromRecyclerView clears it.
Downloaded rows still pay a filesystem stat on every rebind while the learner scrolls the step.
The adapter is a RecyclerView, so this does not move roadmap 10.
files: app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt, the existence check in updateStatusAndPreview plus the existing list-change and detach hooks.
Do not edit FileUtils.kt, ResourcesPreviewLoader.kt, or PdfThumbnailLoader.kt.
steps:
1. Add a cache beside htmlCoverCache that records only a positive exists result, keyed by resource id together with resourceLocalAddress.
2. On bind, if that key is cached, skip both checkFileExist and the second exists stat.
3. Do not cache a miss. A file that appears after a download must still be detected on the next bind.
4. Drop a key in onCurrentListChanged when the item is gone or resourceLocalAddress changes, and clear the whole cache in onDetachedFromRecyclerView next to htmlCoverCache.clear().
5. Leave preview decoding, Glide overrides, click handling, and the HTML cover cache behavior unchanged.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open a course step that has both downloaded and not-yet-downloaded resources. Scroll away and back. Downloaded previews still show, and a resource that finishes downloading still flips to the downloaded state without restarting the screen.
size budget: ~25 changed lines, 1 file
out of scope: do not cache HTML cover walks inside FileUtils, and do not change thumbnail size. FileUtilsTest calls findHtmlCoverImage twice on the same directory, so a path-only cache there would be wrong.
---
### 7. cache voice reaction JSON the way images are cached (roadmap 7)
context: News.imagesArray (lines 95-114) parses the images column once and stores an ImagesCache keyed by the raw string, then deepCopy on a hit.
reactionsJson (lines 125-130), reactionsMap (lines 133-144), and pendingReactionsMap (lines 147-156) parse on every get.
updateReaction (line 158) reads reactionsMap, setReaction reads reactionsMap again (line 173), and updateReaction then reads pendingReactionsMap (line 162).
One reaction tap parses the same JSON three times, and every voice row does it again on rebind.
News still imports android.util.Log, Room, and FileUtils, so this does not finish roadmap 9. The new cache fields must be plain Kotlin and must not add an android.* type.
files: app/src/main/java/org/ole/planet/myplanet/model/News.kt, the three reaction getters and the writers that assign reactions and pendingReactions.
Do not edit voices adapters, VoicesRepository, or imagesArray.
steps:
1. Add @Ignore cache holders, matching the imagesCache fields at lines 91-93, keyed by the raw reactions string and the raw pendingReactions string.
2. On get, return the cached parse when the raw string is unchanged.
3. reactionsJson returns a mutable JsonObject today. Return a deepCopy on a cache hit, as imagesArray does at line 101, so a caller cannot corrupt the cache.
4. reactionsMap may return the cached immutable map. setReaction already copies lists before it mutates them.
5. Assigning reactions or pendingReactions must make the next get miss, because the raw key changed. Do not remove the imagesArray deepCopy.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
On a voice post, add a reaction, remove it, and reopen the list. The same emoji counts show, pending reactions still apply, and images on the post are unchanged.
size budget: ~40 changed lines, 1 file
out of scope: do not change the reaction toggle rule, and do not persist the cache columns through Room.
---
### 8. precompute chat sort and revision keys (roadmap 7+9)
context: ChatRepositoryImpl.sortChats (lines 144-147) calls createdDate.toLongOrNull and updatedDate.toLongOrNull inside the sort comparator.
A history open therefore parses those strings once per comparison, not once per chat.
getLatestRev (lines 150-152) calls split("-") inside maxByOrNull for every revision compared.
ChatRepositoryImpl has no android.* import; the only Android-adjacent import is androidx.annotation.VisibleForTesting.
Keeping these two functions free of android.* is the KMP chat core (roadmap 9). Do not add an android.* import.
files: app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt, functions sortChats and getLatestRev.
Do not edit ChatDao, ChatViewModel, or any chat adapter.
steps:
1. In sortChats, map each chat once to maxOf(createdDate?.toLongOrNull() ?: 0L, updatedDate?.toLongOrNull() ?: 0L), then sort that decorated list descending.
2. Equal keys must keep today's stable order from sortedByDescending.
3. In getLatestRev, parse the numeric prefix once per revision with indexOf('-') and substring, not split, then select with maxByOrNull on that key.
4. Keep maxByOrNull's first-maximum tie break. A null revision still counts as 0.
5. Do not change the empty-user early return in getChatHistoryForUser.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open chat history for a user with several chats, and resolve the latest rev of a document that has more than one rev. History order is unchanged, and the stored latest rev string is the same document rev as before.
size budget: ~25 changed lines, 1 file
out of scope: no pagination changes, and no new chat dependency.
---
### 9. lowercase user sort keys once per sort (roadmap 7+9)
context: UserRepositoryImpl.getUsersSortedBy (lines 170-172) loads every user through getAllUsers and then calls sortUsers.
sortUsers (lines 339-360) defines value() as value.orEmpty().lowercase() and calls it from inside the comparator for name, firstName, lastName, middleName, email, planetCode, parentCode, level, language, gender, dob, age, and birthPlace.
A name sort of the health or member list therefore allocates a lowercased string on every comparison.
joinDate, isUpdated, and isArchived do not allocate that way.
sortUsers itself makes no android.* call. Keep it that way so the function can move with the user repository into the KMP core (roadmap 9). Do not try to strip the file's other android imports in this task.
files: app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt, function sortUsers only.
Do not edit UserDao, HealthViewModel, or MembersAdapter.
steps:
1. For each string-field branch, compute value.orEmpty().lowercase() once per user, then sort the decorated list.
2. Use the same default-locale lowercase() the comparator uses today. Do not switch to Locale.ROOT. That would change sort order for some locales.
3. Leave the joinDate, isUpdated, and isArchived branches as direct field sorts.
4. The else branch must still return the input list in its existing order.
5. Ascending and descending must match today's sortedBy and sortedByDescending, including stability when two keys are equal.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open a user list sorted by name ascending, then descending, then by join date. Order matches the pre-change list, including mixed-case names.
size budget: ~35 changed lines, 1 file
out of scope: no SQL ORDER BY, and no change to which users are loaded.
---
### 10. enlarge the checksum read buffer (roadmap 7+9)
context: Sha256Utils.getCheckSumFromFile reads the whole file through a ByteArray(DEFAULT_BUFFER_SIZE) loop. That constant is 8 KiB, so a large APK or resource pays one read per 8 KiB while MessageDigest is updated.
ConfigurationsRepositoryImpl.checkCheckSum (lines 256-257) is a caller: it hashes the resolved download and compares the hex string.
Sha256Utils has zero android.* imports. A larger buffer, with the algorithm left alone, is what the platform-free checksum core keeps (roadmap 9).
The digest is SHA-512 despite the class name. Checksum equality depends on that algorithm.
files: app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt, function getCheckSumFromFile only.
Do not edit ConfigurationsRepositoryImpl.kt.
steps:
1. Replace the 8 KiB DEFAULT_BUFFER_SIZE array with a 64 KiB buffer.
2. Keep MessageDigest.getInstance("SHA-512").
3. Keep the existing hex encoding and the null return on any exception.
4. Do not reuse one MessageDigest instance across calls. Each call must still create its own.
5. Do not add an android.* import, and do not rename the class or the method.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Checksum one known local file before and after the buffer change. The hex string is identical, and a missing file still returns null.
size budget: ~2 changed lines, 1 file
out of scope: do not switch the algorithm to SHA-256, and do not add a new call site that hashes on the main thread.

copilot grok-4.7 high
