package org.ole.planet.myplanet.utils

import android.content.Context
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import com.google.gson.JsonObject
import java.util.Locale
import org.ole.planet.myplanet.R

internal data class RatingDisplay(
    val averageText: String,
    val totalRatings: Int,
    val barRating: Float
)

object CourseRatingUtils {
    internal fun computeRatingDisplay(
        averageRating: Float?,
        totalRatings: Int?,
        userRating: Float?
    ): RatingDisplay {
        val averageText = String.format(Locale.getDefault(), "%.2f", averageRating ?: 0f)
        val total = totalRatings ?: 0
        val barRating = userRating ?: averageRating ?: 0f
        return RatingDisplay(averageText, total, barRating)
    }

    internal fun parseRating(obj: JsonObject?): Triple<Float?, Int?, Float?> {
        val averageRating = obj?.get("averageRating")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asFloat
        val totalRatings = obj?.get("total")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
            ?.asInt
        val userRating = when {
            obj?.has("ratingByUser") == true -> obj["ratingByUser"].asFloat
            obj?.has("userRating") == true -> obj["userRating"].asFloat
            else -> null
        }
        return Triple(averageRating, totalRatings, userRating)
    }

    fun showRating(
        context: Context,
        ratingSummary: org.ole.planet.myplanet.repository.RatingSummary?,
        average: TextView?,
        ratingCount: TextView?,
        ratingBar: AppCompatRatingBar?
    ) {
        val averageRating = ratingSummary?.averageRating
        val totalRatings = ratingSummary?.totalRatings
        val userRating = ratingSummary?.userRating?.toFloat()

        renderRating(context, averageRating, totalRatings, userRating, average, ratingCount, ratingBar)
    }

    fun showRating(
        context: Context,
        obj: JsonObject?,
        average: TextView?,
        ratingCount: TextView?,
        ratingBar: AppCompatRatingBar?
    ) {
        val (averageRating, totalRatings, userRating) = parseRating(obj)

        renderRating(context, averageRating, totalRatings, userRating, average, ratingCount, ratingBar)
    }

    private fun renderRating(
        context: Context,
        averageRating: Float?,
        totalRatings: Int?,
        userRating: Float?,
        average: TextView?,
        ratingCount: TextView?,
        ratingBar: AppCompatRatingBar?
    ) {
        val display = computeRatingDisplay(averageRating, totalRatings, userRating)
        average?.text = display.averageText
        ratingCount?.text = context.getString(R.string.rating_count_format, display.totalRatings)
        ratingBar?.rating = display.barRating
    }
}
