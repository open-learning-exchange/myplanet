1. **Modify `ImageViewerUtils.kt`**:
   - I will use `replace_with_git_merge_diff` to modify `app/src/main/java/org/ole/planet/myplanet/utils/ImageViewerUtils.kt`.
   - Add a private property: `private var activeDialog: Dialog? = null`.
   - Update `showZoomableImage` to clear the previous `activeDialog` (if any), and set the new one.
   - Refactor `showZoomableImage` to inflate using `DialogZoomableImageBinding.inflate(LayoutInflater.from(context))` instead of `findViewById`. Add `import org.ole.planet.myplanet.databinding.DialogZoomableImageBinding`.
   - Ensure the image loading logic can distinguish URLs (starts with "http://" or "https://", ignoring case) vs files. If it's a URL, load it directly as string (`load(imagePath)`). If it's not a URL, load from `File` with fallback to string.
   - Remove unused imports (`android.view.LayoutInflater`, `android.widget.ImageView`, `com.github.chrisbanes.photoview.PhotoView` since we will use binding).

2. **Modify `MarkdownUtils.kt`**:
   - I will use `replace_with_git_merge_diff` to modify `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt`.
   - Delete `currentZoomDialog` property.
   - Delete the private `showZoomableImage` function entirely.
   - In `CustomImageSpan.onClick(widget: View)`, change `showZoomableImage(widget.context, url)` to `ImageViewerUtils.showZoomableImage(widget.context, url)`.
   - Remove unused imports (e.g. `android.app.Dialog`, `android.graphics.Color`, `android.view.LayoutInflater`, `android.widget.ImageView`, `androidx.core.graphics.drawable.toDrawable`, `com.bumptech.glide.load.engine.DiskCacheStrategy`, `com.github.chrisbanes.photoview.PhotoView`, `org.ole.planet.myplanet.R`).

3. **Verification**:
   - Verify changes using `git diff` via `run_in_bash_session`.
   - Execute Gradle tasks (`./gradlew testDefaultDebugUnitTest --tests "*Utils*"`) to verify compilation and basic unit tests.

4. **Pre-commit**:
   - Complete pre commit steps to make sure proper testing, verifications, reviews and reflections are done.

5. **Submit**:
   - Call the `submit` tool.
