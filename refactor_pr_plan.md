# myPlanet Refactor Roadmap — 10 Low-Hanging PR Tasks

Each item is one reviewable PR.  Tasks are grouped by file/feature so they do not touch the same files, which keeps the daily merge round conflict-free.

Themes covered: repository boundaries, cross-feature data leaks, DI cleanup, ViewModel expansion, `DiffUtil`/`ListAdapter`, dispatcher discipline, and leaked long-running listeners.

---

## 1. Move `VoicesActions` / `VoicesFragment` repository calls into `VoicesViewModel`

**Goal:** Stop UI helper classes and `Fragment` lifecycle scopes from talking directly to `VoicesRepository`, `ResourcesRepository`, and `ActivitiesRepository`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt` (lines 128–228)
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt` (lines 49–67, 480–507, 750–767)
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt` (lines 200–205)
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt`

**Concrete changes:**
1. Move `repository.editPost`, `repository.postReply`, and `repository.getNewsById` calls from `VoicesActions` to `VoicesViewModel` coroutine methods (`editPost`, `postReply`, `getNewsById`).
2. Move `activitiesRepository.getOfflineVisitCount` / `getLastVisit` and the `SimpleDateFormat` formatting out of `VoicesActions.showMemberDetails` into `VoicesViewModel` (or into `ActivitiesRepository` returning a formatted string / data object); `VoicesActions` should only build the `MembersDetailFragment` UI from the returned data.
3. Move `resourcesRepository.getLibraryItemsByIds` and `resourcesRepository.downloadResources` out of `VoicesFragment.lifecycleScope` into `VoicesViewModel` (add `downloadReferencedResources`).
4. Remove `voicesRepository` from `VoicesAdapter`; pass the needed actions as lambda functions that invoke `VoicesViewModel`.

**Why:** UI helpers should not own repository coordination or date formatting. This also removes a `Repository` field from an `Adapter` and routes all coroutines through `viewModelScope`.

---

## 2. Move transaction creation in `EnterprisesFinancesFragment` into `EnterprisesFinancesViewModel`

**Goal:** Remove a direct `teamsRepository` call from `Fragment.lifecycleScope`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesFragment.kt` (lines 295–315)
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesViewModel.kt`

**Concrete changes:**
1. Add `createTransaction(...)` and a `transactionCreated: SharedFlow<Result<Unit>>` to `EnterprisesFinancesViewModel`.
2. Call `viewModel.createTransaction(...)` from the dialog positive button and collect `transactionCreated` to show the toast / error.
3. Keep `TeamsRepository` interface unchanged; only the call site moves up one layer.

**Why:** The ViewModel already exposes `transactions`; adding the write side completes the feature boundary and lets the Fragment own only UI.

---

## 3. Move rating summary check in `TakeCourseFragment` into `TakeCourseViewModel`

**Goal:** Remove a direct `ratingsRepository` call from a `Fragment`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt` (lines 390–403)
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`

**Concrete changes:**
1. Inject `RatingsRepository` into `TakeCourseViewModel`.
2. Add `suspend fun hasUserRated(courseId: String, userId: String?): Boolean` (or a `StateFlow`) that calls `ratingsRepository.getRatingSummary("course", cId, userId)`.
3. Replace the `lifecycleScope` block in `TakeCourseFragment` with `viewModel.hasUserRated(...)` and update `TakeCourseViewModelTest` mocks.

**Why:** The Fragment should ask the ViewModel for a decision, not perform a repository query itself.

---

## 4. `ResourcesFragment`: move user lookup to `ResourcesViewModel` and fix `OnLayoutChangeListener` leak

**Goal:** Stop the fragment from directly calling `userRepository.getUserModel()` and from leaking a `RecyclerView` layout listener.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt` (lines 149, 217, 258, 692–703)
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`

**Concrete changes:**
1. Inject `UserRepository` into `ResourcesViewModel` and expose `currentUser: StateFlow<UserEntity?>`.
2. Replace the two `userRepository.getUserModel()` calls in `ResourcesFragment` with `viewModel.currentUser` collection.
3. Hold the `OnLayoutChangeListener` added in `setupViewModeToggle()` in a `val` and `removeOnLayoutChangeListener` it in `onDestroyView()`.

**Why:** User identity is a ViewModel concern; the listener fix removes a long-running reference to the `RecyclerView`.

---

## 5. Clean up leaked long-running listeners across the app

**Goal:** Remove unpaired `addOnBackStackChangedListener` / `addOnLayoutChangeListener` registrations.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyActivity.kt` (lines 63–69, 180–183)
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt` (line 315, `onDestroy` ~953)
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt` (lines 40–43)
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt` (lines 349–353, 584–592)

**Concrete changes:**
1. Store the lambda passed to `addOnBackStackChangedListener` and call `removeOnBackStackChangedListener` in `onDestroy` for `PublicSurveyActivity`, `DashboardActivity`, and `DashboardElementActivity`.
2. Hold the `OnLayoutChangeListener` in a field and `removeOnLayoutChangeListener` in `CoursesFragment.onDestroyView`.

**Why:** Anonymous listeners registered on `FragmentManager` or `RecyclerView` can outlive the UI and hold references to the activity/fragment.

---

## 6. Remove `UserRepository` dependency from `RatingsRepositoryImpl`

**Goal:** Eliminate a cross-feature repository coupling and push user resolution to callers.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt` (constructor, lines 40–51, 157–163)
- `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/RatingSummaryProvider.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt` (line 94–107)
- `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt` (lines 74–111)

**Concrete changes:**
1. Change `submitRating(...)` to accept a `UserEntity` instead of a `String` user id; remove `findUserForRating`.
2. Remove `getCourseRatingSummary()` (or change it to accept `userId` and return `RatingSummary` instead of `RatingSummaryModel`).
3. Update `RatingSummaryProvider` to accept `userId` and call `getRatingSummary("course", courseId, userId)`.
4. Update `RatingsViewModel` to fetch the user and pass it to `submitRating` / `getRatingSummary`.
5. Update `CourseDetailViewModel.refreshRatings` to use `RatingSummaryProvider(courseId, currentUserId)` and stop updating the user from the rating result.
6. Update `RatingsRepositoryImplTest` and `RatingsViewModelTest` / `CourseDetailViewModelTest`.

**Why:** `RatingsRepository` should rate items; it should not be responsible for knowing which user is logged in.

---

## 7. Remove `UserRepository` dependency from `EventsRepositoryImpl`

**Goal:** Same as above for the events domain.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt` (constructor, lines 60–106)
- `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/events/EventsDetailViewModel.kt` (lines 36–53, 92–96)
- `app/src/test/java/org/ole/planet/myplanet/repository/EventsRepositoryImplTest.kt`

**Concrete changes:**
1. Change `getJoinedMembers(meetupId)` to `getJoinedMembers(meetupId, allUsers: List<UserEntity>)`.
2. Change `toggleCurrentUserAttendance(meetupId)` to `toggleAttendance(meetupId, userId: String?)`.
3. `EventsDetailViewModel` fetches the current user and the user list and passes them to the repository.
4. Update tests to pass explicit users.

**Why:** `EventsRepository` should not depend on `UserRepository`; user lookup belongs in the ViewModel / session layer.

---

## 8. Move cross-feature DAO access out of `VoicesRepositoryImpl`

**Goal:** `VoicesRepositoryImpl` should only know about news/voices tables. `MyLibraryDao` and `TeamNotificationDao` belong in their own repositories.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt` (constructor, lines 252–266, 546–549)
- `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt` / `ResourcesRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` / `NotificationsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt` (line 23)
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt` (line 58)

**Concrete changes:**
1. Move `getPrivateImageUrlsCreatedAfter(timestamp)` from `VoicesRepositoryImpl` to `ResourcesRepository`.
2. Move `updateTeamNotification(teamId, count)` from `VoicesRepositoryImpl` to `NotificationsRepository`.
3. Remove `MyLibraryDao` and `TeamNotificationDao` from `VoicesRepositoryImpl` constructor.
4. Update `NewsViewModel` to call `ResourcesRepository.getPrivateImageUrlsCreatedAfter` and `TeamsVoicesViewModel` to call `NotificationsRepository.updateTeamNotification`.
5. Remove these methods from `VoicesRepository` interface.

**Why:** Stops `VoicesRepository` from reaching into library and notification tables — a clear cross-feature data leak.

---

## 9. Break the Dagger cycle by removing `Lazy<UserRepository>` from `SubmissionsRepositoryImpl`

**Goal:** Remove a `Lazy` repository dependency and the implicit cyclic graph it papers over.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (constructor lines 53–56, `getSubmissionDetail` line 250, `getPayloadData` line 721, `getExamUploadPayload` line 729)
- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` (line 243–245)

**Concrete changes:**
1. Remove `private val userRepository: Lazy<UserRepository>` from `SubmissionsRepositoryImpl`.
2. Add a `user: UserEntity?` parameter to `getSubmissionDetail` and `getExamUploadPayload`.
3. `SubmissionDetailViewModel` fetches the user and passes it to `getSubmissionDetail`.
4. `UploadConfigs.ExamResults.serializer` fetches the user via the already-injected `userRepository` and passes it to `getExamUploadPayload(submission, user)`.
5. Update `SubmissionsRepositoryImplTest`.

**Why:** `SubmissionsRepository` should not be in a dependency cycle with `UserRepository`. Passing the user as data removes the hidden cross-domain lookup.

---

## 10. Convert `SurveyFragment` map updates to `ListAdapter` + `DiffUtils.itemCallback`

**Goal:** Replace `notifyDataSetChanged()` with proper `ListAdapter` diffing and remove a direct `userRepository` call from the fragment.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt` (lines 76–92, 170–186)
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt`

**Concrete changes:**
1. Create a `SurveyListItem` data class that holds `StepExam`, `SurveyInfo`, `SurveyFormState`, and `userId`.
2. Combine `surveys`, `surveyInfos`, and `bindingData` in `SurveysViewModel` into a single `List<SurveyListItem>` `StateFlow`.
3. Change `SurveysAdapter` to `ListAdapter<SurveyListItem, ...>` with `DiffUtils.itemCallback` (compare `id` for items, and all displayed fields for contents).
4. In `SurveyFragment`, call `submitList` once and delete the `surveyInfoMap` / `bindingDataMap` + `notifyDataSetChanged` pattern.
5. Expose `currentUserId` from `SurveysViewModel` and remove `userRepository.getUserModel()` from `SurveyFragment.getAdapter`.

**Why:** `notifyDataSetChanged()` defeats `ListAdapter`. A single diffed list also removes the parallel `Map` state and the `userRepository` leak from the fragment.

---

## Merge-order note

The PRs are intentionally file-disjoint, but interface-changing PRs (6, 7, 8, 9) will need small test updates bundled in the same PR.  No two PRs above should edit the same file, so they can land in any order once CI passes.
