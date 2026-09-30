package org.ole.planet.myplanet.ui.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeItemDefaultsTest {

    private val labelResolver: (Int) -> String = { "res_$it" }

    @Test
    fun `forUser returns expected count and ordered imageIds`() {
        val items = LifeItemDefaults.forUser("user_123", labelResolver)

        assertEquals(7, items.size)

        val expectedImageIds = listOf(
            "ic_myhealth",
            "my_achievement",
            "ic_submissions",
            "ic_my_survey",
            "ic_references",
            "ic_calendar",
            "ic_mypersonals"
        )

        assertEquals(expectedImageIds, items.map { it.imageId })
    }

    @Test
    fun `forUser applies userId and sets isVisible to true for all items`() {
        val userId = "user_456"
        val items = LifeItemDefaults.forUser(userId, labelResolver)

        assertTrue(items.all { it.userId == userId })
        assertTrue(items.all { it.isVisible })
    }

    @Test
    fun `forUser handles null userId`() {
        val items = LifeItemDefaults.forUser(null, labelResolver)

        assertTrue(items.all { it.userId == null })
    }
}
