package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel

class ResourcesSearchUtilsTest {


    @Test
    fun testSearchLocalModels() {
        val model1 = ResourceListModel(
            library = mockk { every { titleNormal } returns null },
            item = ResourceItem(id = "1", title = "Apple Pie Recipe", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )
        val model2 = ResourceListModel(
            library = mockk { every { titleNormal } returns null },
            item = ResourceItem(id = "2", title = "Banana Bread", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )
        val model3 = ResourceListModel(
            library = mockk { every { titleNormal } returns null },
            item = ResourceItem(id = "3", title = "Apple Juice", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )

        val models = listOf(model1, model2, model3)

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
    fun testSearchMultiWordQueryUsesAllParts() {
        val model1 = ResourceListModel(
            library = mockk { every { titleNormal } returns null },
            item = ResourceItem(id = "1", title = "Apple Pie Recipe", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )
        val model2 = ResourceListModel(
            library = mockk { every { titleNormal } returns null },
            item = ResourceItem(id = "2", title = "Apple Crumble", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )

        val models = listOf(model1, model2)

        val result = ResourcesSearchUtils.searchLocalModels(models, "apple recipe")
        assertEquals(1, result.size)
        assertEquals("Apple Pie Recipe", result[0].item.title)
    }

    @Test
    fun testSearchDiacriticsNormalized() {
        val model1 = ResourceListModel(
            library = mockk { every { titleNormal } returns null },
            item = ResourceItem(id = "1", title = "Café Menu", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )
        val model2 = ResourceListModel(
            library = mockk { every { titleNormal } returns null },
            item = ResourceItem(id = "2", title = "Caffe Latte", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )

        val models = listOf(model1, model2)

        val result = ResourcesSearchUtils.searchLocalModels(models, "cafe")
        assertEquals(1, result.size)
        assertEquals("Café Menu", result[0].item.title)
    }
}
