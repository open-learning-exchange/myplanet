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

    private fun model(
        id: String,
        title: String,
        description: String? = null,
        author: String? = null,
        tags: List<TagItem> = emptyList()
    ): ResourceListModel =
        ResourceListModel(
            library = mockk<MyLibrary>(relaxed = true) {
                every { titleNormal } returns null
                every { this@mockk.description } returns description
                every { this@mockk.author } returns author
                every { publisher } returns null
            },
            item = ResourceItem(
                id = id,
                title = title,
                description = description,
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
            tags = tags
        )

    @Test
    fun testSearchLocalModels() {
        val models = listOf(
            model("1", "Apple Pie Recipe"),
            model(
                "2",
                "Banana Bread",
                description = "Delicious pastry with cinnamon",
                author = "Baker John",
                tags = listOf(TagItem(id = "t1", name = "Dessert"))
            ),
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
