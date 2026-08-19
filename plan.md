1. **Update `MapTileUtils.kt`**:
   - Create the `osmdroid` parent directory (`mkdirs()`).
   - Move the `try-catch` inside the `for` loop to ensure per-file isolation.
   - Check if `outFile` exists and has a length > 0 before copying. If so, skip the copy.

2. **Update `MapTileUtilsTest.kt`**:
   - Modify `copyAssets_failsSilentlyWhenDirectoryDoesNotExist` to verify that the directory is created and files are copied (rename to `copyAssets_createsDirectoryAndCopiesSuccessfully`).
   - Modify `copyAssets_skipsRemainingFilesOnPartialFailure` to verify that failure of one file does not stop the next one from being copied (rename to `copyAssets_continuesOnPartialFailure`).
   - Add a test `copyAssets_skipsCopyIfDestinationExistsAndIsNonEmpty`.

3. **Compile and test**:
   - Run `./gradlew testDefaultDebugUnitTest` to make sure tests pass.

4. **Complete pre-commit steps**:
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
