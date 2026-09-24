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

object ResourceCardHelper {

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

    suspend fun bindCover(
        context: Context,
        ivPreview: ImageView,
        ivTypeIcon: ImageView,
        isOffline: Boolean,
        address: String?,
        libraryId: String?,
        externalFilesDir: File?,
        coverWidthDp: Int,
        dispatcherProvider: DispatcherProvider,
        htmlCoverCache: MutableMap<String, File?>? = null,
        fileLengthCache: MutableMap<String, Long?>? = null
    ) {
        if (!isOffline || address.isNullOrBlank() || libraryId.isNullOrBlank() || externalFilesDir == null) {
            showTypeIconOnly(context, ivPreview, ivTypeIcon)
            return
        }

        val file = FileUtils.getLibraryFile(externalFilesDir, libraryId, address)
        val mimeType = Utilities.getMimeType(address)
        when {
            mimeType?.startsWith("image") == true -> {
                showTypeIconOnly(context, ivPreview, ivTypeIcon)
                showImagePreview(context, ivPreview, ivTypeIcon, file, dispatcherProvider, fileLengthCache)
            }
            mimeType?.startsWith("video") == true -> {
                showTypeIconOnly(context, ivPreview, ivTypeIcon)
                showVideoPreview(context, ivPreview, ivTypeIcon, file, dispatcherProvider, fileLengthCache)
            }
            mimeType?.contains("pdf") == true -> {
                showTypeIconOnly(context, ivPreview, ivTypeIcon)
                val targetWidthPx = (coverWidthDp * context.resources.displayMetrics.density).toInt()
                showPdfPreview(context, ivPreview, ivTypeIcon, file, targetWidthPx, dispatcherProvider)
            }
            mimeType?.contains("html") == true -> {
                showTypeIconOnly(context, ivPreview, ivTypeIcon)
                val resourceDir = File(externalFilesDir, "ole/$libraryId")
                showHtmlPreview(context, ivPreview, ivTypeIcon, libraryId, resourceDir, dispatcherProvider, htmlCoverCache, fileLengthCache)
            }
            else -> {
                showTypeIconOnly(context, ivPreview, ivTypeIcon)
            }
        }
    }

    suspend fun showImagePreview(
        context: Context,
        ivPreview: ImageView,
        ivTypeIcon: ImageView,
        file: File,
        dispatcherProvider: DispatcherProvider,
        fileLengthCache: MutableMap<String, Long?>?
    ) {
        if (cachedFileLength(file, dispatcherProvider, fileLengthCache) == null) {
            showTypeIconOnly(context, ivPreview, ivTypeIcon)
            return
        }
        ivTypeIcon.visibility = View.GONE
        ivPreview.visibility = View.VISIBLE
        Glide.with(context)
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .placeholder(R.drawable.ole_logo)
            .error(R.drawable.ole_logo)
            .into(ivPreview)
    }

    suspend fun showVideoPreview(
        context: Context,
        ivPreview: ImageView,
        ivTypeIcon: ImageView,
        file: File,
        dispatcherProvider: DispatcherProvider,
        fileLengthCache: MutableMap<String, Long?>?
    ) {
        if (cachedFileLength(file, dispatcherProvider, fileLengthCache) == null) {
            showTypeIconOnly(context, ivPreview, ivTypeIcon)
            return
        }
        ivTypeIcon.visibility = View.GONE
        ivPreview.visibility = View.VISIBLE
        Glide.with(context)
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .placeholder(R.drawable.ole_logo)
            .error(R.drawable.ole_logo)
            .into(ivPreview)
    }

    suspend fun showPdfPreview(
        context: Context,
        ivPreview: ImageView,
        ivTypeIcon: ImageView,
        file: File,
        targetWidthPx: Int,
        dispatcherProvider: DispatcherProvider
    ) {
        val exists = withContext(dispatcherProvider.io) { file.exists() }
        if (!exists) {
            showTypeIconOnly(context, ivPreview, ivTypeIcon)
            return
        }
        Glide.with(context).clear(ivPreview)
        val bitmap = PdfThumbnailLoader.firstPageBitmap(file, dispatcherProvider, targetWidthPx)

        if (bitmap != null) {
            ivTypeIcon.visibility = View.GONE
            ivPreview.visibility = View.VISIBLE
            ivPreview.setImageBitmap(bitmap)
        } else {
            showTypeIconOnly(context, ivPreview, ivTypeIcon)
        }
    }

    suspend fun showHtmlPreview(
        context: Context,
        ivPreview: ImageView,
        ivTypeIcon: ImageView,
        libraryId: String,
        resourceDir: File,
        dispatcherProvider: DispatcherProvider,
        htmlCoverCache: MutableMap<String, File?>?,
        fileLengthCache: MutableMap<String, Long?>?
    ) {
        val coverImage = if (htmlCoverCache != null && htmlCoverCache.containsKey(libraryId)) {
            htmlCoverCache[libraryId]
        } else {
            withContext(dispatcherProvider.io) { FileUtils.findHtmlCoverImage(resourceDir) }.also {
                htmlCoverCache?.set(libraryId, it)
            }
        }
        if (coverImage != null) {
            showImagePreview(context, ivPreview, ivTypeIcon, coverImage, dispatcherProvider, fileLengthCache)
        } else {
            showTypeIconOnly(context, ivPreview, ivTypeIcon)
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
