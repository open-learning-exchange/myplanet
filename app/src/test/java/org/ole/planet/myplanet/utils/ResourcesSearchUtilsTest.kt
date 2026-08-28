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
            library = mockk(relaxed = true) { every { titleNormal } returns null; every { description } returns null; every { author } returns null; every { publisher } returns null },
            item = ResourceItem(id = "1", title = "Apple Pie Recipe", description = null, createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )
        val model2 = ResourceListModel(
            library = mockk(relaxed = true) { every { titleNormal } returns null; every { description } returns "Delicious pastry with cinnamon"; every { author } returns "Baker John"; every { publisher } returns null },
            item = ResourceItem(id = "2", title = "Banana Bread", description = "Delicious pastry with cinnamon", createdDate = 0L, averageRating = null, timesRated = 0, resourceId = null, isOffline = false, _rev = null, uploadDate = null, filename = null),
            rating = null,
            tags = emptyList()
        )
        val model3 = ResourceListModel(
            library = mockk(relaxed = true) { every { titleNormal } returns null; every { description } returns null; every { author } returns null; every { publisher } returns null },
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

        val resultDescription = ResourcesSearchUtils.searchLocalModels(models, "cinnamon")
        assertEquals(1, resultDescription.size)
        assertEquals("Banana Bread", resultDescription[0].item.title)

        val resultAuthor = ResourcesSearchUtils.searchLocalModels(models, "Baker")
        assertEquals(1, resultAuthor.size)
        assertEquals("Banana Bread", resultAuthor[0].item.title)
    }

    @Test
    fun testSearchCourseSteps() {
        val step1 = org.ole.planet.myplanet.model.CourseStep(
            id = "s1",
            courseId = "c1",
            stepTitle = "Introduction to Fractions",
            description = "Welcome to math basics"
        )
        val step2 = org.ole.planet.myplanet.model.CourseStep(
            id = "s2",
            courseId = "c1",
            stepTitle = "Advanced Geometry",
            description = "Transcript: Today we discuss Euclidean triangles and angles"
        )
        val steps = listOf(step1, step2)

        val resultTitle = ResourcesSearchUtils.searchCourseSteps(steps, "Fractions")
        assertEquals(1, resultTitle.size)
        assertEquals("s1", resultTitle[0].id)

        val resultTranscript = ResourcesSearchUtils.searchCourseSteps(steps, "Euclidean")
        assertEquals(1, resultTranscript.size)
        assertEquals("s2", resultTranscript[0].id)
    }
}
