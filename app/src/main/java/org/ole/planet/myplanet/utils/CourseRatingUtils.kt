package org.ole.planet.myplanet.utils

import android.content.Context
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
            ?.takeIf { isNumber(it) }
            ?.let { gsonAsFloat(it) }
        val totalRatings = obj?.get("total")
            ?.takeIf { isNumber(it) }
            ?.let { KJson.gsonAsInt(it) }
        val userRating = when {
            obj?.containsKey("ratingByUser") == true -> gsonAsFloat(obj["ratingByUser"])
            obj?.containsKey("userRating") == true -> gsonAsFloat(obj["userRating"])
            else -> null
        }
        return Triple(averageRating, totalRatings, userRating)
    }

    /** Gson's `isJsonPrimitive && isNumber`: a primitive that is neither a string, a boolean nor `null`. */
    private fun isNumber(element: JsonElement): Boolean =
        element is JsonPrimitive && element !is JsonNull && !element.isString && element.booleanOrNull == null

    /**
     * Gson's `JsonElement.getAsFloat()`: a primitive's text parsed like `Float.parseFloat` (so a
     * boolean or non-numeric string throws `NumberFormatException`), a one-element array as its
     * element; throws for `null`, an object or any other array.
     */
    private fun gsonAsFloat(element: JsonElement?): Float = when (element) {
        null -> throw NullPointerException("JsonElement is null")
        is JsonNull -> throw UnsupportedOperationException("JsonNull")
        is JsonPrimitive -> element.content.toFloat()
        is JsonObject -> throw UnsupportedOperationException("JsonObject")
        is JsonArray -> if (element.size == 1) gsonAsFloat(element[0]) else throw IllegalStateException("Array must have size 1, but has size ${element.size}")
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
