package org.ole.planet.myplanet.utilities

import android.content.Context
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import com.google.gson.JsonObject
import java.util.Locale
import org.ole.planet.myplanet.R

object CourseRatingUtils {
    @JvmStatic
    fun showRating(
        context: Context,
        obj: JsonObject?,
        average: TextView?,
        ratingCount: TextView?,
        ratingBar: AppCompatRatingBar?
    ) {
        average?.text = String.format(Locale.getDefault(), "%.2f", obj?.get("averageRating")?.asFloat)
        ratingCount?.text = context.getString(R.string.rating_count_format, obj?.get("total")?.asInt)
        if (obj?.has("ratingByUser") == true) {
            ratingBar?.rating = obj["ratingByUser"].asInt.toFloat()
        }
    }
}
