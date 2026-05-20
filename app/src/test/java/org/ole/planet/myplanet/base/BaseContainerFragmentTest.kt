package org.ole.planet.myplanet.base

import android.content.Context
import android.widget.TextView
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class BaseContainerFragmentTest {

    private lateinit var fragment: BaseContainerFragment
    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        fragment = object : BaseContainerFragment() {}
        mockContext = mockk(relaxed = true)

        // Mock fragment methods
        val fragmentSpy = io.mockk.spyk(fragment)
        every { fragmentSpy.requireContext() } returns mockContext
        fragment = fragmentSpy
    }

    @Test
    fun `setRatings should not crash when json is null`() {
        // Just verify it doesn't crash, as the logic checks for null
        fragment.setRatings(null)
    }

    @Test
    fun `setRatings should call CourseRatingUtils and set average rating when json is provided`() {
        val jsonObject = JsonObject()
        jsonObject.addProperty("averageRating", 4.5)

        // Setup mock variables
        val ratingTextView = mockk<TextView>(relaxed = true)

        // Setup fragment with the mocks
        fragment.rating = ratingTextView

        fragment.setRatings(jsonObject)

        // Using "4.50" because CourseRatingUtils formats it as "%.2f"
        verify { ratingTextView.text = "4.50" }
    }
}
