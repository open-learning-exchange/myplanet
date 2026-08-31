package org.ole.planet.myplanet.ui.settings

import androidx.annotation.StringRes
import org.ole.planet.myplanet.R

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
    val allKnownExtensions: Set<String> = all.dropLast(1).flatMap { it.extensions }.toSet()

    const val OTHER_INDEX: Int = 4

    private val extensionToIndex: Map<String, Int> = buildMap {
        all.forEachIndexed { index, category ->
            category.extensions.forEach { ext ->
                if (!containsKey(ext)) put(ext, index)
            }
        }
    }

    fun indexOf(extension: String): Int = extensionToIndex[extension] ?: OTHER_INDEX
}
