1. **Move interfaces from `ChatRepository.kt` to `VoicesRepository.kt`**:
   - Run bash commands:
     `sed -i '/getPlanetNewsMessages/d' app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt`
     `sed -i '/insertNewsFromJson/d' app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt`
     `sed -i '/insertNewsList/d' app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt`
     `sed -i '/serializeNews/d' app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt`
   - Run bash commands to append the functions to `VoicesRepository.kt`:
     `sed -i '/^}$/d' app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt`
     `cat << 'INNER_EOF' >> app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt`
     `    suspend fun getPlanetNewsMessages(planetCode: String?): List<RealmNews>`
     `    suspend fun insertNewsFromJson(doc: com.google.gson.JsonObject)`
     `    suspend fun insertNewsList(docs: List<com.google.gson.JsonObject>)`
     `    fun serializeNews(news: RealmNews): com.google.gson.JsonObject`
     `}`
     `INNER_EOF`

2. **Verify repository interface updates**:
   - `grep -n 'getPlanetNewsMessages' app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt` to ensure the methods have been correctly transferred.

3. **Move implementation from `ChatRepositoryImpl.kt` to `VoicesRepositoryImpl.kt`**:
   - `replace_with_git_merge_diff` to remove the implementations of `getPlanetNewsMessages`, `insertNewsFromJson`, `insertNewsList`, `insertNewsToRealm`, `serializeNews`, `addViewIn`, `saveConcatenatedLinksToPrefs` and the `concatenatedLinks` val from `ChatRepositoryImpl.kt`. Also remove the `SharedPrefManager` dependency.
   - `replace_with_git_merge_diff` to add these implementations to `VoicesRepositoryImpl.kt` and add `SharedPrefManager` dependency.

4. **Verify repository implementation updates**:
   - `grep -n 'getPlanetNewsMessages' app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt` to ensure implementation is successfully transferred.

5. **Update dependencies and callers**:
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` to add `private val voicesRepository: VoicesRepository` to `@Inject constructor(...)` and update `chatRepository.serializeNews` to `voicesRepository.serializeNews`.
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt` to add `private val voicesRepository: VoicesRepository` to `@Inject constructor(...)` and update `chatRepository.insertNewsList` to `voicesRepository.insertNewsList`.
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` to change `private val chatRepository: ChatRepository` to `private val voicesRepository: VoicesRepository` and update `chatRepository::serializeNews` to `voicesRepository::serializeNews`.
   - `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt` to update `chatRepository.getPlanetNewsMessages` to `voicesRepository.getPlanetNewsMessages`. `voicesRepository` is already injected in this file.

6. **Verify updates to dependencies**:
   - `grep -n 'voicesRepository.serializeNews' app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`
   - `grep -n 'voicesRepository.insertNewsList' app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`
   - `grep -n 'voicesRepository.getPlanetNewsMessages' app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`

7. **Verify**:
   - Run `./gradlew testDefaultDebugUnitTest` to run the tests and verify the code compiles and passes tests.

8. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.**
