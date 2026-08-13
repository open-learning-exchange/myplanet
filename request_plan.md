1. **Fix Inefficiency (a) - Hoist JSON Parsing:**
   - In `News.kt`, add `@Ignore var parsedImagesArray: JsonArray? = null`, `@Ignore var rawImages: String? = null`, and `@Ignore var parsedSharedTeamName: String? = null`.
   - In `VoicesAdapter.kt`, update `preParseNews` to populate these caches. For `parsedImagesArray`, check if `it.rawImages != it.images` and populate using `it.imagesArray`. For `parsedSharedTeamName`, call `JsonUtils.extractSharedTeamName(it)` *after* `parsedViewIn` is computed.
   - Update `onBindViewHolder` (and `PAYLOAD_EDIT_ACTION`) to use `news.parsedSharedTeamName ?: ""` instead of calling `JsonUtils.extractSharedTeamName(news)` directly.
   - Update `loadImage` to use `news.parsedImagesArray` instead of `news.imagesArray`.

2. **Fix Inefficiency (b) - Fine-grained Updates:**
   - In `VoicesAdapter.kt`, update `getChangePayload` to distinguish `PAYLOAD_LABELS_CHANGED` from `PAYLOAD_TEAM_LEADER_CHANGED` when `labels` change.
   - Create `PAYLOAD_IMAGES_CHANGED` for image-related changes (`imageUrls`, `images`, `parsedImageUrls`) instead of grouping them in `PAYLOAD_USER_FETCHED`.
   - In `onBindViewHolder(..., payloads)`, strictly process updates for:
     - `PAYLOAD_LABELS_CHANGED`: only call `labelManager` methods.
     - `PAYLOAD_IMAGES_CHANGED`: only call `loadImage`.
     - `PAYLOAD_EDIT_ACTION`: only call `setMessageAndDate`, `configureEditDeleteButtons`, `showReplyButton` without unnecessarily invoking `loadImage` or `handleChat` unless chat actually changed.
   - Remove redundant full binds (resetViews, loadImage, etc.) from `PAYLOAD_EDIT_ACTION` where they aren't needed.

3. **Fix Inefficiency (c) - O(1) User Lookups instead of O(n):**
   - In `VoicesAdapter.kt`, introduce a `private val userIdPositions = mutableMapOf<String, MutableList<Int>>()`.
   - Override `onCurrentListChanged(previousList: List<News>, currentList: List<News>)` to iterate over `currentList` exactly once, rebuilding the `userIdPositions` map.
   - In `configureUser`, when `getUserFn` returns, replace `currentList.forEachIndexed` with a simple lookup `userIdPositions[userId]?.forEach { index -> safeNotifyItemChanged(index, PAYLOAD_USER_FETCHED) }`.

4. **Compile and Pre-commit:**
   - Use `./gradlew compileDefaultDebugUnitTestSources --no-daemon` to verify compilation.
   - Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
