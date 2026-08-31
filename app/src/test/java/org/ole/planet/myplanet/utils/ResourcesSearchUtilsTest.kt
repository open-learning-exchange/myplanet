package org.ole.planet.myplanet.utils

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagItem

class ResourcesSearchUtilsTest {

    @Test
    fun testSearchLocalModels() {
        val model1 = ResourceListModel(
            library = mockk<MyLibrary>(relaxed = true) {
                every { titleNormal } returns null
                every { description } returns null
                every { author } returns null
                every { publisher } returns null
            },
            item = ResourceItem(
                id = "1",
                title = "Apple Pie Recipe",
                description = null,
                createdDate = 0L,
                averageRating = null,
                timesRated = 0,
                resourceId = null,
                isOffline = false,
                _rev = null,
                uploadDate = null,
                filename = null
            ),
            rating = null,
            tags = emptyList()
        )
        val model2 = ResourceListModel(
            library = mockk<MyLibrary>(relaxed = true) {
                every { titleNormal } returns null
                every { description } returns "Delicious pastry with cinnamon"
                every { author } returns "Baker John"
                every { publisher } returns null
            },
            item = ResourceItem(
                id = "2",
                title = "Banana Bread",
                description = "Delicious pastry with cinnamon",
                createdDate = 0L,
                averageRating = null,
                timesRated = 0,
                resourceId = null,
                isOffline = false,
                _rev = null,
                uploadDate = null,
                filename = null
            ),
            rating = null,
            tags = listOf(TagItem(id = "t1", name = "Dessert"))
        )
        val model3 = ResourceListModel(
            library = mockk<MyLibrary>(relaxed = true) {
                every { titleNormal } returns null
                every { description } returns null
                every { author } returns null
                every { publisher } returns null
            },
            item = ResourceItem(
                id = "3",
                title = "Apple Juice",
                description = null,
                createdDate = 0L,
                averageRating = null,
                timesRated = 0,
                resourceId = null,
                isOffline = false,
                _rev = null,
                uploadDate = null,
                filename = null
            ),
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

        val resultDescription = ResourcesSearchUtils.searchLocalModels(models, "cinnamon")
        assertEquals(1, resultDescription.size)
        assertEquals("Banana Bread", resultDescription[0].item.title)

        val resultAuthor = ResourcesSearchUtils.searchLocalModels(models, "Baker")
        assertEquals(1, resultAuthor.size)
        assertEquals("Banana Bread", resultAuthor[0].item.title)

        val resultTag = ResourcesSearchUtils.searchLocalModels(models, "Dessert")
        assertEquals(1, resultTag.size)
        assertEquals("Banana Bread", resultTag[0].item.title)
    }

    @Test
    fun testSearchListRanking() {
        data class TestItem(val id: String, val title: String, val body: String)

        val item1 = TestItem("1", "Physics Advanced", "General science mechanics")
        val item2 = TestItem("2", "Advanced Physics", "Theory of relativity")
        val item3 = TestItem("3", "Quantum Computing", "Advanced quantum physics notes")

        val list = listOf(item1, item2, item3)

        val result = ResourcesSearchUtils.searchList(
            list = list,
            query = "Physics",
            primarySelector = { it.title },
            secondarySelectors = listOf({ it.body })
        )

        assertEquals(3, result.size)
        assertEquals("1", result[0].id)
        assertEquals("2", result[1].id)
        assertEquals("3", result[2].id)
    }
}
