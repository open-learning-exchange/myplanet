1. **Optimize N+1 Query in `UploadManager.kt`:**
   - In `UploadManager.uploadNews()`, `databaseService.executeTransactionAsync` is invoked inside the inner `forEach` loop, which leads to opening and committing a Realm transaction for each individual successful news upload.
   - Refactor this by creating a `successfulUpdates` list for the batch. During the loop iteration, simply append the necessary metadata (`id`, `newsResponse.body()`, and `imagesArray`) to `successfulUpdates`.
   - After the loop for the batch finishes, execute a single `databaseService.executeTransactionAsync` block. Use the `in` operator to fetch all `RealmNews` objects in one query, group them in a map by `id`, and update them in O(1) time.
   - This prevents N transactions per batch and speeds up the database writes significantly.

2. **Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done:**
   - Run compilation checks (`./gradlew compileDefaultDebugKotlin --offline`) to verify that the refactored Kotlin code is syntactically correct and doesn't break dependent code.

3. **Submit the PR:**
   - Title: `⚡ Optimize N+1 Query in uploadNews`
   - Description containing What, Why, and Measured Improvement (or rationale).
