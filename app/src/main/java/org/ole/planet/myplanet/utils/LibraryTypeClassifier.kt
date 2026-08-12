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

    fun classify(library: MyLibrary): LibraryType {
        val extension = FileUtils.getFileExtension(
            library.resourceLocalAddress ?: library.resourceRemoteAddress
        ).lowercase()
        val mediaType = library.mediaType?.lowercase().orEmpty()

        return when {
            extension == "pdf" -> LibraryType.PDF
            extension in videoExtensions || mediaType.startsWith("video") -> LibraryType.VIDEO
            extension in audioExtensions || mediaType.startsWith("audio") -> LibraryType.AUDIO
            else -> LibraryType.BOOK
        }
    }
}
