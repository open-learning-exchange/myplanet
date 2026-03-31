1. **Refactor `ServerUrlMapper` to use `SharedPrefManager`**:
   - `SharedPrefManager` functions as our local preferences repository, containing necessary getters/setters (`getServerPin()`, `setUrlUser()`, `setUrlPwd()`, `setUrlScheme()`, `setUrlHost()`, `setAlternativeUrl()`, `setProcessedAlternativeUrl()`, `setIsAlternativeUrl()`).
   - Modify `ServerUrlMapper`'s constructor to inject `SharedPrefManager`.
   - Update `updateUrlPreferences` to remove the `SharedPreferences.Editor` and `SharedPreferences` arguments, and delegate to `SharedPrefManager` functions.
   - Update `updateServerIfNecessary` to remove the `settings: SharedPreferences` parameter.

2. **Verify ServerUrlMapper**:
   - Verify changes using `cat app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`

3. **Update all call sites to pass only the required arguments**:
   - Update `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/services/ServerReachabilityWorker.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt`
   - Update `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt`
   - Update `app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModelTest.kt`

4. **Run tests**:
   - Run tests.

5. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
