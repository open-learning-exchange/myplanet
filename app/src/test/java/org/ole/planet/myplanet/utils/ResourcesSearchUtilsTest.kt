package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel

class ResourcesSearchUtilsTest {

    private fun model(id: String, title: String): ResourceListModel =
        ResourceListModel(
            library = mockk { every { titleNormal } returns null },
            item = ResourceItem(
                id = id, title = title, description = null, createdDate = 0L,
                averageRating = null, timesRated = 0, resourceId = null, isOffline = false,
                _rev = null, uploadDate = null, filename = null,
            ),
            rating = null,
            tags = emptyList(),
        )

    @Test
    fun testSearchLocalModels() {
        val models = listOf(
            model("1", "Apple Pie Recipe"),
            model("2", "Banana Bread"),
            model("3", "Apple Juice"),
        )

        val resultEmpty = ResourcesSearchUtils.searchLocalModels(models, "")
        assertEquals(3, resultEmpty.size)

        val resultApple = ResourcesSearchUtils.searchLocalModels(models, "apple")
        assertEquals(2, resultApple.size)
        assertEquals("Apple Pie Recipe", resultApple[0].item.title)
        assertEquals("Apple Juice", resultApple[1].item.title)

        val resultBread = ResourcesSearchUtils.searchLocalModels(models, "bread")
        assertEquals(1, resultBread.size)
        assertEquals("Banana Bread", resultBread[0].item.title)

        val resultCaseInsensitive = ResourcesSearchUtils.searchLocalModels(models, "BANANA")
        assertEquals(1, resultCaseInsensitive.size)
        assertEquals("Banana Bread", resultCaseInsensitive[0].item.title)
    }

    @Test
    fun testSearchPrefixRanksBeforeContains() {
        val models = listOf(
            model("1", "Green Apple Care"),
            model("2", "Apple Pie"),
        )

        val result = ResourcesSearchUtils.searchLocalModels(models, "apple")
        assertEquals(2, result.size)
        assertEquals("Apple Pie", result[0].item.title)
        assertEquals("Green Apple Care", result[1].item.title)
    }

    @Test
    fun testSearchMultiWordQueryUsesAllParts() {
        val models = listOf(
            model("1", "Apple Pie Recipe"),
            model("2", "Apple Crumble"),
        )

        val result = ResourcesSearchUtils.searchLocalModels(models, "apple recipe")
        assertEquals(1, result.size)
        assertEquals("Apple Pie Recipe", result[0].item.title)
    }

    @Test
    fun testSearchDiacriticsNormalized() {
        val models = listOf(
            model("1", "Café Menu"),
            model("2", "Caffe Latte"),
        )

        val result = ResourcesSearchUtils.searchLocalModels(models, "cafe")
        assertEquals(1, result.size)
        assertEquals("Café Menu", result[0].item.title)
    }
}
