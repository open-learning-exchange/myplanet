1. **Create `VoicesEditActions.kt` Interface**
   - Use `write_file` to create `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesEditActions.kt` containing an interface `VoicesEditActions` with methods:
     - `suspend fun editPost(newsId: String, message: String, imagesToRemove: Set<String>, newImages: List<String>?): News?`
     - `suspend fun postReply(message: String, news: News, currentUser: UserEntity, imageList: List<String>?)`
     - `suspend fun getNewsById(id: String): News?`

2. **Verify File Creation**
   - Use `list_files` or `read_file` to verify that `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesEditActions.kt` was created and its contents are correct.

3. **Update `VoicesActions.kt`**
   - Use `replace_with_git_merge_diff` to replace `VoicesRepository` with `VoicesEditActions` in `VoicesActions.kt` (`import`, `showEditAlert` and `handlePositiveButton`).

4. **Verify `VoicesActions.kt` Updates**
   - Use `read_file` to verify the updates to `VoicesActions.kt`.

5. **Update `VoicesAdapter.kt`**
   - Use `replace_with_git_merge_diff` to replace `VoicesRepository` with `VoicesEditActions` in `VoicesAdapter.kt` (constructor and method calls).

6. **Verify `VoicesAdapter.kt` Updates**
   - Use `read_file` to verify the updates to `VoicesAdapter.kt`.

7. **Update Call Sites (`VoicesFragment`, `TeamsVoicesFragment`, `ReplyActivity`)**
   - Use `replace_with_git_merge_diff` on `VoicesFragment.kt`, `TeamsVoicesFragment.kt`, and `ReplyActivity.kt`.
   - Replace `voicesRepository = voicesRepository` with an anonymous object:
     ```kotlin
     voicesEditActions = object : VoicesEditActions {
         override suspend fun editPost(newsId: String, message: String, imagesToRemove: Set<String>, newImages: List<String>?): News? {
             return voicesRepository.editPost(newsId, message, imagesToRemove, newImages)
         }
         override suspend fun postReply(message: String, news: News, currentUser: UserEntity, imageList: List<String>?) {
             voicesRepository.postReply(message, news, currentUser, imageList)
         }
         override suspend fun getNewsById(id: String): News? {
             return voicesRepository.getNewsById(id)
         }
     }
     ```

8. **Verify Call Sites Updates**
   - Use `read_file` to verify the updates in all three call site files.

9. **Run unit tests**
   - Use `run_in_bash_session` to run `export ANDROID_HOME=/opt/android-sdk; unset ANDROID_SDK_ROOT; ./gradlew testDefaultDebugUnitTest` to ensure compilation and test passage.

10. **Pre-commit steps**
    - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
