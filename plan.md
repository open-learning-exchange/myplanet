1. **Refactor DictionaryLoad state**:
   - Use `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt` to introduce `sealed interface DictionaryLoad` with `FileMissing`, `AlreadyPopulated`, `Inserted`, and `Failed(val cause: Throwable?)` states. Update `insertDictionaryData` to return this type.

2. **Update DictionaryRepositoryImpl.kt**:
   - Use `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`.
   - Move the `FileUtils.checkFileExist` check inside `withContext(dispatcherProvider.io)`.
   - Update return values to use `DictionaryLoad.FileMissing`, `DictionaryLoad.AlreadyPopulated`, `DictionaryLoad.Inserted`, and `DictionaryLoad.Failed(e)`.

3. **Update DictionaryActivity.kt**:
   - Use `replace_with_git_merge_diff` on `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`.
   - Handle the `DictionaryLoad` result in a `when` expression:
     - `DictionaryLoad.Inserted`, `DictionaryLoad.AlreadyPopulated`: Display count and set click listener.
     - `DictionaryLoad.FileMissing`: Trigger download.
     - `DictionaryLoad.Failed`: Show a Toast with an error message, but do NOT trigger download.
   - Correctly alphabetize `org.ole.planet.myplanet.repository.DictionaryRepository` import.

4. **Fix Unit Tests**:
   - Use `replace_with_git_merge_diff` on `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`.
   - Update assertions to check for `DictionaryLoad` types instead of boolean values.
   - Remove `println` and debug `try/catch` blocks.

5. **Verify Changes**:
   - Use `read_file` to review files.

6. **Test**:
   - Run tests `./gradlew testDefaultDebugUnitTest --no-daemon`.

7. **Pre-commit**:
   - Request review and delete plan.

8. **Submit**:
    - Submit changes.
