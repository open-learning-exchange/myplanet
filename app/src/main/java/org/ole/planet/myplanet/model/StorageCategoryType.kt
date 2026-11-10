package org.ole.planet.myplanet.model

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
