package org.ole.planet.myplanet.utils

import android.content.Context
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import com.google.gson.JsonObject
import java.util.Locale
import org.ole.planet.myplanet.R

object CourseRatingUtils {
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
        average?.text = String.format(Locale.getDefault(), "%.2f", averageRating ?: 0f)
        ratingCount?.text = context.getString(R.string.rating_count_format, totalRatings ?: 0)
        ratingBar?.rating = userRating ?: averageRating ?: 0f
    }
}
