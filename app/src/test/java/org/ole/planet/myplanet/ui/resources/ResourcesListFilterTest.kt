package org.ole.planet.myplanet.ui.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TagItem

class ResourcesListFilterTest {

    private fun model(id: String, title: String, subject: List<String>? = null, level: List<String>? = null, language: String? = null, mediaType: String? = null, isOffline: Boolean = false, isLocallyOffline: Boolean = false, tags: List<TagItem> = emptyList()): ResourceListModel {
        val library = MyLibrary().apply {
            this.id = id
            this.subject = subject
            this.level = level
            this.language = language
            this.mediaType = mediaType
        }
        val item = ResourceItem(
            id = id,
            title = title,
            description = null,
            createdDate = 0,
            averageRating = null,
            timesRated = 0,
            resourceId = id,
            isOffline = isOffline,
            _rev = null,
            uploadDate = null,
            filename = null
        )
        return ResourceListModel(library, item, tags = tags, isLocallyOffline = isLocallyOffline)
    }

    private val noFilters = ResourcesFilterCriteria(
        searchQuery = "",
        searchTags = emptyList(),
        subjects = emptySet(),
        levels = emptySet(),
        languages = emptySet(),
        mediums = emptySet(),
        downloadFilterIndex = 0
    )

    @Test
    fun `apply filters by search text`() {
        val models = listOf(
            model(id = "1", title = "Algebra Basics"),
            model(id = "2", title = "Chemistry 101")
        )
        val filter = ResourcesListFilter()

        val result = filter.apply(models, noFilters.copy(searchQuery = "algebra"), emptySet())

        assertEquals(1, result.size)
        assertEquals("1", result[0].item.id)
    }

    @Test
    fun `apply filters by selected tag`() {
        val models = listOf(
            model(id = "1", title = "A", tags = listOf(TagItem(id = "t1", name = "Math"))),
            model(id = "2", title = "B", tags = listOf(TagItem(id = "t2", name = "Science"))),
            model(id = "3", title = "C", tags = listOf(TagItem(id = "t3", name = "History"), TagItem(id = "t2", name = "Science")))
        )
        val filter = ResourcesListFilter()

        val result = filter.apply(models, noFilters.copy(searchTags = listOf(TagEntity().apply { id = "t1" })), emptySet())

        assertEquals(1, result.size)
        assertEquals("1", result[0].item.id)

        val multiTagResult = filter.apply(models, noFilters.copy(searchTags = listOf(TagEntity().apply { id = "t1" }, TagEntity().apply { id = "t3" })), emptySet())
        assertEquals(2, multiTagResult.size)
        assertEquals(setOf("1", "3"), multiTagResult.map { it.item.id }.toSet())
    }

    @Test
    fun `apply filters by subject, level, language and medium facets`() {
        val matching = model(
            id = "1", title = "A",
            subject = listOf("Math"), level = listOf("Grade 1"),
            language = "English", mediaType = "Video"
        )
        val nonMatching = model(
            id = "2", title = "B",
            subject = listOf("Science"), level = listOf("Grade 2"),
            language = "Spanish", mediaType = "Audio"
        )
        val filter = ResourcesListFilter()

        val result = filter.apply(
            listOf(matching, nonMatching),
            noFilters.copy(
                subjects = setOf("Math"),
                levels = setOf("Grade 1"),
                languages = setOf("English"),
                mediums = setOf("Video")
            ),
            emptySet()
        )

        assertEquals(1, result.size)
        assertEquals("1", result[0].item.id)
    }

    @Test
    fun `apply filters to downloaded-only when download filter index is 1`() {
        val downloaded = model(id = "1", title = "A", isOffline = true)
        val notDownloaded = model(id = "2", title = "B", isOffline = false)
        val locallyOffline = model(id = "3", title = "C", isLocallyOffline = true)
        val filter = ResourcesListFilter()

        val result = filter.apply(
            listOf(downloaded, notDownloaded, locallyOffline),
            noFilters.copy(downloadFilterIndex = 1),
            locallyOfflineIds = setOf("2")
        )

        assertEquals(setOf("1", "2", "3"), result.map { it.item.id }.toSet())
    }

    @Test
    fun `apply filters to not-downloaded-only when download filter index is 2`() {
        val downloaded = model(id = "1", title = "A", isOffline = true)
        val notDownloaded = model(id = "2", title = "B", isOffline = false)
        val filter = ResourcesListFilter()

        val result = filter.apply(listOf(downloaded, notDownloaded), noFilters.copy(downloadFilterIndex = 2), emptySet())

        assertEquals(listOf("2"), result.map { it.item.id })
    }

    @Test
    fun `filterIfChanged returns null when criteria are unchanged since the last call`() {
        val models = listOf(model(id = "1", title = "A"))
        val filter = ResourcesListFilter()
        val criteria = noFilters.copy(searchQuery = "a")

        val first = filter.filterIfChanged(models, criteria, emptySet())
        val second = filter.filterIfChanged(models, criteria, emptySet())

        assertEquals(listOf("1"), first?.map { it.item.id })
        assertNull(second)
    }

    @Test
    fun `filterIfChanged treats different tag order as the same criteria`() {
        val models = listOf(model(id = "1", title = "A", tags = listOf(TagItem(id = "a", name = "A"))))
        val filter = ResourcesListFilter()
        val tagA = TagEntity().apply { id = "a" }
        val tagB = TagEntity().apply { id = "b" }

        val first = filter.filterIfChanged(models, noFilters.copy(searchTags = listOf(tagA, tagB)), emptySet())
        val second = filter.filterIfChanged(models, noFilters.copy(searchTags = listOf(tagB, tagA)), emptySet())

        assertEquals(1, first?.size)
        assertNull(second)
    }

    @Test
    fun `filterIfChanged recomputes once any criterion changes`() {
        val models = listOf(model(id = "1", title = "A"), model(id = "2", title = "B"))
        val filter = ResourcesListFilter()

        filter.filterIfChanged(models, noFilters.copy(searchQuery = "a"), emptySet())
        val second = filter.filterIfChanged(models, noFilters.copy(searchQuery = "b"), emptySet())

        assertEquals(listOf("2"), second?.map { it.item.id })
    }

    @Test
    fun `filterIfChanged recomputes when the locally offline ids change`() {
        val models = listOf(model(id = "1", title = "A"), model(id = "2", title = "B"))
        val filter = ResourcesListFilter()
        val downloadedOnly = noFilters.copy(downloadFilterIndex = 1)

        val first = filter.filterIfChanged(models, downloadedOnly, setOf("1"))
        val afterDownload = filter.filterIfChanged(models, downloadedOnly, setOf("1", "2"))

        assertEquals(listOf("1"), first?.map { it.item.id })
        assertEquals(listOf("1", "2"), afterDownload?.map { it.item.id })
    }

    @Test
    fun `reset forces the next filterIfChanged call to recompute`() {
        val models = listOf(model(id = "1", title = "A"))
        val filter = ResourcesListFilter()
        val sameCriteria = noFilters.copy(searchQuery = "a")

        filter.filterIfChanged(models, sameCriteria, emptySet())
        filter.reset()
        val afterReset = filter.filterIfChanged(models, sameCriteria, emptySet())

        assertEquals(listOf("1"), afterReset?.map { it.item.id })
    }

    @Test
    fun `countMatching counts the models passing the given criteria`() {
        val models = listOf(
            model(id = "1", title = "A", subject = listOf("Math")),
            model(id = "2", title = "B", subject = listOf("Science"))
        )
        val filter = ResourcesListFilter()

        assertEquals(1, filter.countMatching(models, noFilters.copy(subjects = setOf("Math")), emptySet()))
        assertEquals(2, filter.countMatching(models, noFilters, emptySet()))
    }

    @Test
    fun `countMatching does not suppress the next filterIfChanged call`() {
        val models = listOf(
            model(id = "1", title = "A", subject = listOf("Math")),
            model(id = "2", title = "B", subject = listOf("Science"))
        )
        val filter = ResourcesListFilter()
        val sameCriteria = noFilters.copy(subjects = setOf("Math"))

        filter.countMatching(models, sameCriteria, emptySet())
        val filtered = filter.filterIfChanged(models, sameCriteria, emptySet())

        assertEquals(listOf("1"), filtered?.map { it.item.id })
    }
}
