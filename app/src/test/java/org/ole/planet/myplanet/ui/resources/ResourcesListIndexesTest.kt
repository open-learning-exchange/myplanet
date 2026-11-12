package org.ole.planet.myplanet.ui.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagItem

class ResourcesListIndexesTest {

    private fun model(
        id: String?,
        localAddress: String? = null,
        tags: List<TagItem> = emptyList()
    ): ResourceListModel {
        val library = MyLibrary().apply {
            this.id = id.orEmpty()
            this.resourceLocalAddress = localAddress
        }
        val item = ResourceItem(
            id = id,
            title = id,
            description = null,
            createdDate = 0,
            averageRating = null,
            timesRated = 0,
            resourceId = id,
            isOffline = false,
            _rev = null,
            uploadDate = null,
            filename = null,
            resourceLocalAddress = localAddress
        )
        return ResourceListModel(library, item, tags = tags)
    }

    @Test
    fun `indexes models by id and local address`() {
        val first = model("a", localAddress = "a.mp4")
        val second = model("b", localAddress = "b.mp4")

        val indexes = buildResourcesListIndexes(listOf(first, second))

        assertEquals(first, indexes.modelsById["a"])
        assertEquals(second, indexes.modelsById["b"])
        assertEquals(first, indexes.modelsByLocalAddress["a.mp4"])
        assertEquals(second, indexes.modelsByLocalAddress["b.mp4"])
    }

    @Test
    fun `duplicate ids keep the first model`() {
        val first = model("dup")
        val second = model("dup")

        val indexes = buildResourcesListIndexes(listOf(first, second))

        assertEquals(first, indexes.modelsById["dup"])
    }

    @Test
    fun `duplicate local addresses keep the first model`() {
        val first = model("a", localAddress = "same.mp4")
        val second = model("b", localAddress = "same.mp4")

        val indexes = buildResourcesListIndexes(listOf(first, second))

        assertEquals(first, indexes.modelsByLocalAddress["same.mp4"])
    }

    @Test
    fun `duplicate tag ids across resources keep the first tag`() {
        val firstTag = TagItem(id = "t1", name = "First")
        val secondTag = TagItem(id = "t1", name = "Second")
        val first = model("a", tags = listOf(firstTag))
        val second = model("b", tags = listOf(secondTag))

        val indexes = buildResourcesListIndexes(listOf(first, second))

        assertEquals(firstTag, indexes.tagsById["t1"])
        assertEquals("First", indexes.tagsById["t1"]?.name)
    }

    @Test
    fun `null id and address keys are never indexed`() {
        val nullId = model(id = null, localAddress = null, tags = listOf(TagItem(id = null, name = "No id")))
        val real = model(id = "a", localAddress = "a.mp4")

        val indexes = buildResourcesListIndexes(listOf(nullId, real))

        assertEquals(1, indexes.modelsById.size)
        assertEquals(1, indexes.modelsByLocalAddress.size)
        assertFalse(indexes.modelsById.containsKey(""))
        assertFalse(indexes.modelsByLocalAddress.containsKey(""))
        assertTrue(indexes.tagsById.isEmpty())
        assertEquals(real, indexes.modelsById["a"])
    }
}
