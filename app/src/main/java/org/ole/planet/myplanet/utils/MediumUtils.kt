package org.ole.planet.myplanet.utils

import android.content.Context
import java.util.Locale
import org.ole.planet.myplanet.R

object MediumUtils {
    private val KNOWN_CANONICALS = setOf("pdf", "video", "audio", "image", "html", "text/html", "book", "other")

    fun getCanonicalMedium(medium: String): String {
        val lower = medium.lowercase(Locale.ROOT).trim()
        return when {
            lower.contains("pdf") -> "pdf"
            lower.contains("video") || lower == "mp4" -> "video"
            lower.contains("audio") || lower == "mp3" -> "audio"
            lower.contains("image") || lower.contains("graphic") -> "image"
            lower.contains("html") -> "html"
            lower.contains("book") || lower == "epub" || lower == "textbook" -> "book"
            else -> medium.trim()
        }
    }

    fun isKnownMedium(medium: String): Boolean {
        val canonical = getCanonicalMedium(medium)
        return KNOWN_CANONICALS.contains(canonical.lowercase(Locale.ROOT))
    }

    fun getMediumDisplayName(context: Context, medium: String): String {
        val canonical = getCanonicalMedium(medium)
        return when (canonical.lowercase(Locale.ROOT)) {
            "pdf" -> context.getString(R.string.filter_pdfs)
            "video" -> context.getString(R.string.filter_videos)
            "audio" -> context.getString(R.string.filter_audio)
            "image" -> context.getString(R.string.storage_images)
            "html" -> context.getString(R.string.medium_html)
            "text/html" -> context.getString(R.string.medium_text_html)
            "book" -> context.getString(R.string.filter_books)
            "other" -> context.getString(R.string.other)
            else -> canonical.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}
