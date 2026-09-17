package org.ole.planet.myplanet.utils

import android.content.Context
import org.ole.planet.myplanet.R

object MediumUtils {
    fun getMediumDisplayName(context: Context, medium: String): String {
        return when (medium.lowercase().trim()) {
            "pdf" -> context.getString(R.string.filter_pdfs)
            "video" -> context.getString(R.string.filter_videos)
            "audio" -> context.getString(R.string.filter_audio)
            "image", "graphic/pictures" -> context.getString(R.string.storage_images)
            "text/html" -> context.getString(R.string.medium_text_html)
            "html" -> context.getString(R.string.medium_html)
            "book", "books" -> context.getString(R.string.filter_books)
            "other" -> context.getString(R.string.other)
            else -> medium
        }
    }
}
