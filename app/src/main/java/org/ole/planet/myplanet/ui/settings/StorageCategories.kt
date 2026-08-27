package org.ole.planet.myplanet.ui.settings

import androidx.annotation.StringRes
import org.ole.planet.myplanet.R

/**
 * Single source of truth for the storage-breakdown categories and their file-extension sets.
 * Both [StorageBreakdownFragment] and [StorageCategoryDetailFragment] read from here instead of
 * re-declaring the extension sets or threading them through Bundle arguments.
 */
data class StorageCategory(
    @StringRes val nameRes: Int,
    val extensions: Set<String>
)

object StorageCategories {

    val all: List<StorageCategory> = listOf(
        StorageCategory(R.string.storage_videos, setOf("mp4", "mkv", "avi", "webm", "mov", "3gp", "flv")),
        StorageCategory(R.string.storage_audio, setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus")),
        StorageCategory(R.string.storage_pdfs, setOf("pdf")),
        StorageCategory(R.string.storage_images, setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")),
        StorageCategory(R.string.storage_other, emptySet())
    )

    /** Union of every explicitly-mapped extension (i.e. excluding the "other" catch-all). */
    val allKnownExtensions: Set<String> = all.dropLast(1).flatMap { it.extensions }.toSet()

    /** Index of the "other" catch-all category, which matches any extension not in [allKnownExtensions]. */
    const val OTHER_INDEX: Int = 4

    private val extensionToIndex: Map<String, Int> = buildMap {
        all.forEachIndexed { index, category ->
            category.extensions.forEach { ext ->
                if (!containsKey(ext)) put(ext, index)
            }
        }
    }

    /** Returns the category index for [extension], falling back to the "other" category. */
    fun indexOf(extension: String): Int = extensionToIndex[extension] ?: OTHER_INDEX
}
