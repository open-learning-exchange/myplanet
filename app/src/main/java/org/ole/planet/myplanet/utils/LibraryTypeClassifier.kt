package org.ole.planet.myplanet.utils

import org.ole.planet.myplanet.model.MyLibrary

enum class LibraryType {
    BOOK,
    VIDEO,
    AUDIO,
    PDF
}

object LibraryTypeClassifier {
    private val videoExtensions = setOf("mp4", "mov", "mkv", "webm", "avi", "3gp")
    private val audioExtensions = setOf("mp3", "aac", "wav", "ogg", "m4a")
    private val nonBookExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "html", "htm", "txt")

    fun classify(library: MyLibrary): LibraryType {
        val address = library.resourceLocalAddress?.takeIf { it.isNotBlank() }
            ?: library.resourceRemoteAddress?.takeIf { it.isNotBlank() }

        val extension = FileUtils.getFileExtension(address)
        val mediaType = library.mediaType?.lowercase().orEmpty()

        return when {
            extension == "pdf" || mediaType.contains("pdf") -> LibraryType.PDF
            extension in videoExtensions || mediaType.startsWith("video") || mediaType == "mp4" -> LibraryType.VIDEO
            extension in audioExtensions || mediaType.startsWith("audio") || mediaType == "mp3" -> LibraryType.AUDIO
            else -> LibraryType.BOOK
        }
    }

    fun isExplicitNonBook(library: MyLibrary): Boolean {
        val address = library.resourceLocalAddress?.takeIf { it.isNotBlank() }
            ?: library.resourceRemoteAddress?.takeIf { it.isNotBlank() }
        val extension = FileUtils.getFileExtension(address)
        val mediaType = library.mediaType?.lowercase().orEmpty()

        return mediaType.startsWith("image") ||
                mediaType.contains("html") ||
                mediaType.startsWith("text/") ||
                extension in nonBookExtensions
    }
}
