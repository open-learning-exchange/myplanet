package org.ole.planet.myplanet.ui.resources

import android.content.Context
import java.util.Locale
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.utils.FileUtils

enum class ResourcesMediaType {
    BOOK,
    VIDEO,
    AUDIO,
    PDF;

    companion object {
        private val videoExtensions = setOf("mp4", "mov", "mkv", "webm", "avi", "3gp")
        private val audioExtensions = setOf("mp3", "aac", "wav", "ogg", "m4a")
        private val nonBookExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "html", "htm", "txt")

        fun classify(library: MyLibrary): ResourcesMediaType {
            val extension = extensionOf(library)
            val mediaType = library.mediaType?.lowercase().orEmpty()

            return when {
                extension == "pdf" || mediaType.contains("pdf") -> PDF
                extension in videoExtensions || mediaType.startsWith("video") || mediaType == "mp4" -> VIDEO
                extension in audioExtensions || mediaType.startsWith("audio") || mediaType == "mp3" -> AUDIO
                else -> BOOK
            }
        }

        fun isExplicitNonBook(library: MyLibrary): Boolean {
            val extension = extensionOf(library)
            val mediaType = library.mediaType?.lowercase().orEmpty()

            return mediaType.startsWith("image") ||
                    mediaType.contains("html") ||
                    mediaType.startsWith("text/") ||
                    extension in nonBookExtensions
        }

        fun canonicalMedium(medium: String): String {
            val lower = medium.lowercase(Locale.ROOT).trim()
            return when {
                lower.contains("pdf") -> "pdf"
                lower.contains("audio") || lower == "mp3" -> "audio"
                lower.contains("video") || lower == "mp4" -> "video"
                lower.contains("image") || lower.contains("graphic") -> "image"
                lower.contains("html") -> "html"
                lower.contains("book") || lower == "epub" || lower == "textbook" -> "book"
                else -> medium.trim()
            }
        }

        fun displayName(context: Context, medium: String): String {
            val canonical = canonicalMedium(medium)
            return when (canonical.lowercase(Locale.ROOT)) {
                "pdf" -> context.getString(R.string.filter_pdfs)
                "video" -> context.getString(R.string.filter_videos)
                "audio" -> context.getString(R.string.filter_audio)
                "image" -> context.getString(R.string.storage_images)
                "html" -> context.getString(R.string.medium_html)
                "book" -> context.getString(R.string.filter_books)
                "other" -> context.getString(R.string.other)
                else -> canonical.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }

        private fun extensionOf(library: MyLibrary): String {
            val address = library.resourceLocalAddress?.takeIf { it.isNotBlank() }
                ?: library.resourceRemoteAddress?.takeIf { it.isNotBlank() }
            return FileUtils.getFileExtension(address)
        }
    }
}
