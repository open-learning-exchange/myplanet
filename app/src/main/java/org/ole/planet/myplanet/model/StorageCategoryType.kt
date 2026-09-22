package org.ole.planet.myplanet.model

/**
 * File-type taxonomy shared by the storage-breakdown scan and the settings screens that render it.
 * Declaration order is part of the contract: it defines the index layout of the `sizes`/`counts`
 * arrays in `StorageBreakdown`, and [OTHER] must stay last as the catch-all.
 *
 * Lives in `model/` so the repository layer can classify files without depending on `ui/`; the
 * user-facing labels for these categories stay in `ui/settings/StorageCategories.kt`.
 */
enum class StorageCategoryType(val extensions: Set<String>) {
    VIDEOS(setOf("mp4", "mkv", "avi", "webm", "mov", "3gp", "flv")),
    AUDIO(setOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus")),
    PDFS(setOf("pdf")),
    IMAGES(setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")),
    OTHER(emptySet());

    companion object {
        val OTHER_INDEX: Int = OTHER.ordinal

        val allKnownExtensions: Set<String> =
            StorageCategoryType.entries.filter { it != OTHER }.flatMap { it.extensions }.toSet()

        private val extensionToIndex: Map<String, Int> = buildMap {
            StorageCategoryType.entries.forEach { category ->
                category.extensions.forEach { ext ->
                    if (!containsKey(ext)) put(ext, category.ordinal)
                }
            }
        }

        fun indexOf(extension: String): Int = extensionToIndex[extension.lowercase()] ?: OTHER_INDEX
    }
}
