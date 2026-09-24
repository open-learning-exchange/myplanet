package org.ole.planet.myplanet.ui.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeDefaultsTest {

    private val labelResolver: (Int) -> String = { "res_$it" }

    @Test
    fun `defaultItems returns expected count and ordered imageIds`() {
        val items = LifeDefaults.defaultItems("user_123", labelResolver)

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
    fun `defaultItems applies userId and sets isVisible to true for all items`() {
        val userId = "user_456"
        val items = LifeDefaults.defaultItems(userId, labelResolver)

        assertTrue(items.all { it.userId == userId })
        assertTrue(items.all { it.isVisible })
    }

    @Test
    fun `defaultItems handles null userId`() {
        val items = LifeDefaults.defaultItems(null, labelResolver)

        assertTrue(items.all { it.userId == null })
    }
}
