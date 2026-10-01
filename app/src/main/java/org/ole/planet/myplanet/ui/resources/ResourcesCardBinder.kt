package org.ole.planet.myplanet.ui.resources

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LibraryType
import org.ole.planet.myplanet.utils.PdfThumbnailLoader
import org.ole.planet.myplanet.utils.Utilities

data class CoverBindParams(
    val context: Context,
    val ivPreview: ImageView,
    val ivTypeIcon: ImageView,
    val isOffline: Boolean,
    val address: String?,
    val libraryId: String?,
    val externalFilesDir: File?,
    val coverWidthDp: Int,
    val dispatcherProvider: DispatcherProvider,
    val htmlCoverCache: MutableMap<String, File?>? = null,
    val fileLengthCache: MutableMap<String, Long?>? = null
)

object ResourcesCardBinder {

    @ColorRes
    fun typeColorRes(type: LibraryType): Int = when (type) {
        LibraryType.PDF -> R.color.type_pdf
        LibraryType.VIDEO -> R.color.type_video
        LibraryType.AUDIO -> R.color.type_audio
        LibraryType.BOOK -> R.color.type_book
    }

    @DrawableRes
    fun typeIconRes(type: LibraryType): Int = when (type) {
        LibraryType.PDF -> R.drawable.ic_type_pdf
        LibraryType.VIDEO -> R.drawable.ic_type_video
        LibraryType.AUDIO -> R.drawable.ic_type_audio
        LibraryType.BOOK -> R.drawable.ic_type_book
    }

    @StringRes
    fun typeLabelRes(type: LibraryType): Int = when (type) {
        LibraryType.PDF -> R.string.filter_pdfs
        LibraryType.VIDEO -> R.string.filter_videos
        LibraryType.AUDIO -> R.string.filter_audio
        LibraryType.BOOK -> R.string.filter_books
    }

    fun setCoverColor(view: View, type: LibraryType) {
        val background = view.background?.mutate()
        if (background is GradientDrawable) {
            background.setColor(ContextCompat.getColor(view.context, typeColorRes(type)))
        }
    }

    fun showTypeIconOnly(context: Context, ivPreview: ImageView, ivTypeIcon: ImageView) {
        Glide.with(context).clear(ivPreview)
        ivPreview.setImageDrawable(null)
        ivPreview.visibility = View.GONE
        ivTypeIcon.visibility = View.VISIBLE
    }

    suspend fun bindCover(params: CoverBindParams) {
        if (!params.isOffline || params.address.isNullOrBlank() || params.libraryId.isNullOrBlank() || params.externalFilesDir == null) {
            showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
            return
        }

        val file = FileUtils.getLibraryFile(params.externalFilesDir, params.libraryId, params.address)
        val mimeType = Utilities.getMimeType(params.address)
        when {
            mimeType?.startsWith("image") == true || mimeType?.startsWith("video") == true -> {
                showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
                showMediaPreview(params, file)
            }
            mimeType?.contains("pdf") == true -> {
                showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
                val targetWidthPx = (params.coverWidthDp * params.context.resources.displayMetrics.density).toInt()
                showPdfPreview(params, file, targetWidthPx)
            }
            mimeType?.contains("html") == true -> {
                showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
                val resourceDir = File(params.externalFilesDir, "ole/${params.libraryId}")
                showHtmlPreview(params, params.libraryId, resourceDir)
            }
            else -> {
                showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
            }
        }
    }

    private suspend fun showMediaPreview(
        params: CoverBindParams,
        file: File
    ) {
        if (cachedFileLength(file, params.dispatcherProvider, params.fileLengthCache) == null) {
            showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
            return
        }
        params.ivTypeIcon.visibility = View.GONE
        params.ivPreview.visibility = View.VISIBLE
        Glide.with(params.context)
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .placeholder(R.drawable.ole_logo)
            .error(R.drawable.ole_logo)
            .into(params.ivPreview)
    }

    private suspend fun showPdfPreview(
        params: CoverBindParams,
        file: File,
        targetWidthPx: Int
    ) {
        val exists = withContext(params.dispatcherProvider.io) { file.exists() }
        if (!exists) {
            showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
            return
        }
        Glide.with(params.context).clear(params.ivPreview)
        val bitmap = PdfThumbnailLoader.firstPageBitmap(file, params.dispatcherProvider, targetWidthPx)

        if (bitmap != null) {
            params.ivTypeIcon.visibility = View.GONE
            params.ivPreview.visibility = View.VISIBLE
            params.ivPreview.setImageBitmap(bitmap)
        } else {
            showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
        }
    }

    private suspend fun showHtmlPreview(
        params: CoverBindParams,
        libraryId: String,
        resourceDir: File
    ) {
        val coverImage = if (params.htmlCoverCache != null && params.htmlCoverCache.containsKey(libraryId)) {
            params.htmlCoverCache[libraryId]
        } else {
            withContext(params.dispatcherProvider.io) { FileUtils.findHtmlCoverImage(resourceDir) }.also {
                params.htmlCoverCache?.set(libraryId, it)
            }
        }
        if (coverImage != null) {
            showMediaPreview(params, coverImage)
        } else {
            showTypeIconOnly(params.context, params.ivPreview, params.ivTypeIcon)
        }
    }

    fun buildMetaLine(
        context: Context,
        type: LibraryType,
        language: String?,
        fileSize: Long? = null
    ): String {
        val parts = mutableListOf<String>()
        parts.add(context.getString(typeLabelRes(type)))
        if (fileSize != null) {
            parts.add(FileUtils.formatSize(context, fileSize))
        }
        language?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }

    suspend fun cachedFileLength(
        file: File,
        dispatcherProvider: DispatcherProvider,
        fileLengthCache: MutableMap<String, Long?>? = null
    ): Long? {
        val path = file.path
        if (fileLengthCache != null && fileLengthCache.containsKey(path)) return fileLengthCache[path]
        val length = withContext(dispatcherProvider.io) { if (file.exists()) file.length() else null }
        fileLengthCache?.set(path, length)
        return length
    }
}
