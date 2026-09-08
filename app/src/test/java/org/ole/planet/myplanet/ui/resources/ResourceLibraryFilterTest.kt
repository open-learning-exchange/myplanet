package org.ole.planet.myplanet.ui.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.ResourceItem
import org.ole.planet.myplanet.model.ResourceListModel
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TagItem

class ResourceLibraryFilterTest {

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

    private fun criteria(searchQuery: String = "", searchTags: List<TagEntity> = emptyList(),
        subjects: Set<String> = emptySet(), levels: Set<String> = emptySet(), languages: Set<String> = emptySet(), mediums: Set<String> = emptySet(), downloadFilterIndex: Int = 0
    ) = ResourceFilterCriteria(searchQuery, searchTags, subjects, levels, languages, mediums, downloadFilterIndex)

    @Test
    fun `apply filters by search text`() {
        val models = listOf(
            model(id = "1", title = "Algebra Basics"),
            model(id = "2", title = "Chemistry 101")
        )
        val filter = ResourceLibraryFilter()

        val result = filter.apply(models, criteria(searchQuery = "algebra"), emptySet())

        assertEquals(1, result.size)
        assertEquals("1", result[0].item.id)
    }

    @Test
    fun `apply filters by selected tag`() {
        val models = listOf(
            model(id = "1", title = "A", tags = listOf(TagItem(id = "t1", name = "Math"))),
            model(id = "2", title = "B", tags = listOf(TagItem(id = "t2", name = "Science")))
        )
        val filter = ResourceLibraryFilter()

        val result = filter.apply(models, criteria(searchTags = listOf(TagEntity().apply { id = "t1" })), emptySet())

        assertEquals(1, result.size)
        assertEquals("1", result[0].item.id)
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
        val filter = ResourceLibraryFilter()

        val result = filter.apply(
            listOf(matching, nonMatching),
            criteria(
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
        val filter = ResourceLibraryFilter()

        val result = filter.apply(
            listOf(downloaded, notDownloaded, locallyOffline),
            criteria(downloadFilterIndex = 1),
            locallyOfflineIds = setOf("2")
        )

        assertEquals(setOf("1", "2", "3"), result.map { it.item.id }.toSet())
    }

    @Test
    fun `apply filters to not-downloaded-only when download filter index is 2`() {
        val downloaded = model(id = "1", title = "A", isOffline = true)
        val notDownloaded = model(id = "2", title = "B", isOffline = false)
        val filter = ResourceLibraryFilter()

        val result = filter.apply(listOf(downloaded, notDownloaded), criteria(downloadFilterIndex = 2), emptySet())

        assertEquals(listOf("2"), result.map { it.item.id })
    }

    @Test
    fun `filterIfChanged returns null when criteria are unchanged since the last call`() {
        val models = listOf(model(id = "1", title = "A"))
        val filter = ResourceLibraryFilter()
        val criteria = criteria(searchQuery = "a")

        val first = filter.filterIfChanged(models, criteria, emptySet())
        val second = filter.filterIfChanged(models, criteria, emptySet())

        assertEquals(listOf("1"), first?.map { it.item.id })
        assertNull(second)
    }

    @Test
    fun `filterIfChanged treats different tag order as the same criteria`() {
        val models = listOf(model(id = "1", title = "A", tags = listOf(TagItem(id = "a", name = "A"))))
        val filter = ResourceLibraryFilter()
        val tagA = TagEntity().apply { id = "a" }
        val tagB = TagEntity().apply { id = "b" }

        val first = filter.filterIfChanged(models, criteria(searchTags = listOf(tagA, tagB)), emptySet())
        val second = filter.filterIfChanged(models, criteria(searchTags = listOf(tagB, tagA)), emptySet())

        assertEquals(1, first?.size)
        assertNull(second)
    }

    @Test
    fun `filterIfChanged recomputes once any criterion changes`() {
        val models = listOf(model(id = "1", title = "A"), model(id = "2", title = "B"))
        val filter = ResourceLibraryFilter()

        filter.filterIfChanged(models, criteria(searchQuery = "a"), emptySet())
        val second = filter.filterIfChanged(models, criteria(searchQuery = "b"), emptySet())

        assertEquals(listOf("2"), second?.map { it.item.id })
    }

    @Test
    fun `reset forces the next filterIfChanged call to recompute`() {
        val models = listOf(model(id = "1", title = "A"))
        val filter = ResourceLibraryFilter()
        val sameCriteria = criteria(searchQuery = "a")

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
        val filter = ResourceLibraryFilter()

        assertEquals(1, filter.countMatching(models, criteria(subjects = setOf("Math")), emptySet()))
        assertEquals(2, filter.countMatching(models, criteria(), emptySet()))
    }

    @Test
    fun `countMatching does not suppress the next filterIfChanged call`() {
        val models = listOf(
            model(id = "1", title = "A", subject = listOf("Math")),
            model(id = "2", title = "B", subject = listOf("Science"))
        )
        val filter = ResourceLibraryFilter()
        val sameCriteria = criteria(subjects = setOf("Math"))

        filter.countMatching(models, sameCriteria, emptySet())
        val filtered = filter.filterIfChanged(models, sameCriteria, emptySet())

        assertEquals(listOf("1"), filtered?.map { it.item.id })
    }
}
