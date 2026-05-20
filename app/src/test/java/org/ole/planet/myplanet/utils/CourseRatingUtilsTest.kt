package org.ole.planet.myplanet.utils

import android.content.Context
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.ole.planet.myplanet.R

class CourseRatingUtilsTest {

    private val context: Context = mockk(relaxed = true)
    private val average: TextView = mockk(relaxed = true)
    private val ratingCount: TextView = mockk(relaxed = true)
    private val ratingBar: AppCompatRatingBar = mockk(relaxed = true)

    @Test
    fun showRating_withNullObject_setsDefaultValues() {
        every { context.getString(R.string.rating_count_format, 0) } returns "0 ratings"

        CourseRatingUtils.showRating(context, null, average, ratingCount, ratingBar)

        verify { average.text = "0.00" }
        verify { ratingCount.text = "0 ratings" }
        verify { ratingBar.rating = 0f }
    }

    @Test
    fun showRating_withValidObject_setsAverageAndTotal() {
        val obj = JsonObject().apply {
            addProperty("averageRating", 4.5f)
            addProperty("total", 100)
        }
        every { context.getString(R.string.rating_count_format, 100) } returns "100 ratings"

        CourseRatingUtils.showRating(context, obj, average, ratingCount, ratingBar)

        verify { average.text = "4.50" }
        verify { ratingCount.text = "100 ratings" }
        verify { ratingBar.rating = 4.5f }
    }

    @Test
    fun showRating_withUserRating_takesPrecedenceOverAverage() {
        val obj = JsonObject().apply {
            addProperty("averageRating", 4.5f)
            addProperty("total", 100)
            addProperty("userRating", 3.0f)
        }
        every { context.getString(R.string.rating_count_format, 100) } returns "100 ratings"

        CourseRatingUtils.showRating(context, obj, average, ratingCount, ratingBar)

        verify { average.text = "4.50" }
        verify { ratingCount.text = "100 ratings" }
        verify { ratingBar.rating = 3.0f }
    }

    @Test
    fun showRating_withRatingByUser_takesPrecedenceOverAverage() {
        val obj = JsonObject().apply {
            addProperty("averageRating", 4.5f)
            addProperty("total", 100)
            addProperty("ratingByUser", 2.0f)
        }
        every { context.getString(R.string.rating_count_format, 100) } returns "100 ratings"

        CourseRatingUtils.showRating(context, obj, average, ratingCount, ratingBar)

        verify { average.text = "4.50" }
        verify { ratingCount.text = "100 ratings" }
        verify { ratingBar.rating = 2.0f }
    }

    @Test
    fun showRating_withInvalidNumberTypes_setsDefaultValues() {
        val obj = JsonObject().apply {
            addProperty("averageRating", "four")
            addProperty("total", "hundred")
        }
        every { context.getString(R.string.rating_count_format, 0) } returns "0 ratings"

        CourseRatingUtils.showRating(context, obj, average, ratingCount, ratingBar)

        verify { average.text = "0.00" }
        verify { ratingCount.text = "0 ratings" }
        verify { ratingBar.rating = 0f }
    }
}
