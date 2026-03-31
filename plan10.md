1. **Refactor UI fragments and viewmodels**:
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt`
```kotlin
<<<<<<< SEARCH
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, prefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
    }
=======
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping) { url ->
            isServerReachable(url)
        }
    }
>>>>>>> REPLACE
```
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt`
```kotlin
<<<<<<< SEARCH
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
    }
=======
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping) { url ->
            isServerReachable(url)
        }
    }
>>>>>>> REPLACE
```
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt`
```kotlin
<<<<<<< SEARCH
        viewModelScope.launch {
            serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
                MainApplication.isServerReachable(url)
            }
            startSyncManager()
        }
=======
        viewModelScope.launch {
            serverUrlMapper.updateServerIfNecessary(mapping) { url ->
                MainApplication.isServerReachable(url)
            }
            startSyncManager()
        }
>>>>>>> REPLACE
```
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt`
```kotlin
<<<<<<< SEARCH
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, prefData.rawPreferences) { url ->
            isServerReachable(url)
        }
    }
=======
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping) { url ->
            isServerReachable(url)
        }
    }
>>>>>>> REPLACE
```
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt`
```kotlin
<<<<<<< SEARCH
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
    }
=======
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping) { url ->
            isServerReachable(url)
        }
    }
>>>>>>> REPLACE
```
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`
```kotlin
<<<<<<< SEARCH
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
    }
=======
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping) { url ->
            isServerReachable(url)
        }
    }
>>>>>>> REPLACE
```
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`
```kotlin
<<<<<<< SEARCH
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, sharedPrefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
    }
=======
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping) { url ->
            isServerReachable(url)
        }
    }
>>>>>>> REPLACE
```
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt`
```kotlin
<<<<<<< SEARCH
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, prefData.rawPreferences) { url ->
            isServerReachable(url)
        }
    }
=======
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping) { url ->
            isServerReachable(url)
        }
    }
>>>>>>> REPLACE
```
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt`
```kotlin
<<<<<<< SEARCH
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, prefManager.rawPreferences) { url ->
            isServerReachable(url)
        }
    }
=======
    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping) { url ->
            isServerReachable(url)
        }
    }
>>>>>>> REPLACE
```

2. **Run tests**
   - Run tests.

3. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
