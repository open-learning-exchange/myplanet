package org.ole.planet.myplanet.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.RealmMyLibrary
import io.mockk.mockk
import io.mockk.every

class ResourceSearchUtilsTest {


    @Test
    fun testSearchLocalModels() {
        val mockLibrary1 = mockk<RealmMyLibrary>(relaxed = true) {
            every { titleNormal } returns "apple pie recipe"
        }
        val model1 = ResourceListModel(
            library = mockLibrary1,
            item = ResourceItem(id = "1", title = "Apple Pie Recipe", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )
        val mockLibrary2 = mockk<RealmMyLibrary>(relaxed = true) {
            every { titleNormal } returns "banana bread"
        }
        val model2 = ResourceListModel(
            library = mockLibrary2,
            item = ResourceItem(id = "2", title = "Banana Bread", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )
        val mockLibrary3 = mockk<RealmMyLibrary>(relaxed = true) {
            every { titleNormal } returns "apple juice"
        }
        val model3 = ResourceListModel(
            library = mockLibrary3,
            item = ResourceItem(id = "3", title = "Apple Juice", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )

        val models = listOf(model1, model2, model3)

        val resultEmpty = ResourceSearchUtils.searchLocalModels(models, "")
        assertEquals(3, resultEmpty.size)

        val resultApple = ResourceSearchUtils.searchLocalModels(models, "apple")
        assertEquals(2, resultApple.size)
        assertEquals("Apple Pie Recipe", resultApple[0].item.title)
        assertEquals("Apple Juice", resultApple[1].item.title)

        val resultBread = ResourceSearchUtils.searchLocalModels(models, "bread")
        assertEquals(1, resultBread.size)
        assertEquals("Banana Bread", resultBread[0].item.title)

        val resultCaseInsensitive = ResourceSearchUtils.searchLocalModels(models, "BANANA")
        assertEquals(1, resultCaseInsensitive.size)
        assertEquals("Banana Bread", resultCaseInsensitive[0].item.title)
    }
}
