package org.ole.planet.myplanet.utils

import android.content.Context
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.repository.RatingSummary

class CourseRatingUtilsTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun computeRatingDisplay_withNullValues_returnsDefaultDisplay() {
        val display = CourseRatingUtils.computeRatingDisplay(null, null, null)
        assertEquals("0.00", display.averageText)
        assertEquals(0, display.totalRatings)
        assertEquals(0f, display.barRating)
    }

    @Test
    fun computeRatingDisplay_withValidAverageAndTotal_returnsFormattedDisplay() {
        val display = CourseRatingUtils.computeRatingDisplay(4.5f, 100, null)
        assertEquals("4.50", display.averageText)
        assertEquals(100, display.totalRatings)
        assertEquals(4.5f, display.barRating)
    }

    @Test
    fun computeRatingDisplay_withUserRating_userRatingBeatsAverageForBarNotText() {
        val display = CourseRatingUtils.computeRatingDisplay(4.5f, 100, 3.0f)
        assertEquals("4.50", display.averageText)
        assertEquals(100, display.totalRatings)
        assertEquals(3.0f, display.barRating)
    }

    @Test
    fun parseRating_withNullObject_returnsNulls() {
        val (avg, total, user) = CourseRatingUtils.parseRating(null)
        assertNull(avg)
        assertNull(total)
        assertNull(user)
    }

    @Test
    fun parseRating_withInvalidNumberTypes_returnsNullsForAverageAndTotal() {
        val obj = JsonObject().apply {
            addProperty("averageRating", "four")
            addProperty("total", "hundred")
        }
        val (avg, total, user) = CourseRatingUtils.parseRating(obj)
        assertNull(avg)
        assertNull(total)
        assertNull(user)
    }

    @Test
    fun parseRating_ratingByUser_takesPrecedenceOverUserRating() {
        val obj = JsonObject().apply {
            addProperty("ratingByUser", 2.0f)
            addProperty("userRating", 3.0f)
        }
        val (_, _, user) = CourseRatingUtils.parseRating(obj)
        assertEquals(2.0f, user)
    }

    @Test
    fun parseRating_withUserRatingOnly_returnsUserRating() {
        val obj = JsonObject().apply {
            addProperty("userRating", 3.0f)
        }
        val (_, _, user) = CourseRatingUtils.parseRating(obj)
        assertEquals(3.0f, user)
    }

    @Test
    fun ratingSummary_withUserRating_computesCorrectBarRating() {
        val ratingSummary = RatingSummary(
            existingRating = null,
            averageRating = 4.2f,
            totalRatings = 50,
            userRating = 5
        )
        val display = CourseRatingUtils.computeRatingDisplay(
            ratingSummary.averageRating,
            ratingSummary.totalRatings,
            ratingSummary.userRating?.toFloat()
        )
        assertEquals("4.20", display.averageText)
        assertEquals(50, display.totalRatings)
        assertEquals(5.0f, display.barRating)
    }

    @Test
    fun showRating_jsonObjectOverload_smokeTest() {
        val context: Context = mockk(relaxed = true)
        val average: TextView = mockk(relaxed = true)
        val ratingCount: TextView = mockk(relaxed = true)
        val ratingBar: AppCompatRatingBar = mockk(relaxed = true)
        every { context.getString(R.string.rating_count_format, 100) } returns "100 ratings"

        val obj = JsonObject().apply {
            addProperty("averageRating", 4.5f)
            addProperty("total", 100)
        }

        CourseRatingUtils.showRating(context, obj, average, ratingCount, ratingBar)

        verify { average.text = "4.50" }
        verify { ratingCount.text = "100 ratings" }
        verify { ratingBar.rating = 4.5f }
    }

    @Test
    fun showRating_ratingSummaryOverload_smokeTest() {
        val context: Context = mockk(relaxed = true)
        val average: TextView = mockk(relaxed = true)
        val ratingCount: TextView = mockk(relaxed = true)
        val ratingBar: AppCompatRatingBar = mockk(relaxed = true)
        every { context.getString(R.string.rating_count_format, 50) } returns "50 ratings"

        val ratingSummary = RatingSummary(
            existingRating = null,
            averageRating = 4.2f,
            totalRatings = 50,
            userRating = 5
        )

        CourseRatingUtils.showRating(context, ratingSummary, average, ratingCount, ratingBar)

        verify { average.text = "4.20" }
        verify { ratingCount.text = "50 ratings" }
        verify { ratingBar.rating = 5.0f }
    }
}
