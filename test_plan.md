1. **Add cache map to `ChatHistoryAdapter`:**
   - I will add a private variable `private var cachedSharedViewInIds = emptyMap<String, Set<String>>()` in `ChatHistoryAdapter.kt`.

2. **Update `updateCachedData` to pre-index viewIn IDs:**
   - I will modify `updateCachedData` to build `cachedSharedViewInIds` whenever the `sharedNews` list changes.
   - It will group `newsList` by `newsId`, iterate over them, and extract viewer IDs from the JSON array in `viewIn`.
   - Empty lists will clear the index.
   - Multiple `newsId` entries will have their viewer IDs merged.
   - Malformed JSON parsing will be handled by catching exceptions and returning an empty list.

3. **Update `getSharedViewInIds`:**
   - I will update `getSharedViewInIds` to simply lookup the `chatId` in `cachedSharedViewInIds`. If not found, it returns `emptySet()`.

4. **Add tests for `ChatHistoryAdapter`:**
   - I will create `ChatHistoryAdapterTest.kt` in `app/src/test/java/org/ole/planet/myplanet/ui/chat/`.
   - The tests will cover:
     - Null chat IDs.
     - Malformed `viewIn` JSON parsing.
     - Duplicate news IDs and verifying viewer IDs are merged correctly.
     - Empty news list clearing the cache index.
     - Verify correct initialization of the cache.

5. **Run Pre-commit steps:**
   - I will run all the necessary tests and checks before committing to ensure the codebase remains sound and verified.

6. **Submit Code:**
   - I will submit the updated code with an appropriate message.
