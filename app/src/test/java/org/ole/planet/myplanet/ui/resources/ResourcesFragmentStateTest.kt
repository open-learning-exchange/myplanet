package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.TagEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ResourcesFragmentStateTest {

    @Test
    fun `test save and restore filter state`() {
        val fragment = ResourcesFragment()
        fragment.subjects = mutableSetOf("Math", "Science")
        fragment.languages = mutableSetOf("English")
        fragment.levels = mutableSetOf("Grade 1")
        fragment.mediums = mutableSetOf("Video")
        fragment.selectedDownloadFilterIndex = 1
        fragment.searchTags = mutableListOf(
            TagEntity().apply {
                id = "tag1"
                name = "Education"
            },
            TagEntity().apply {
                id = "tag2"
                name = "Science & Tech"
            }
        )

        val bundle = Bundle()
        fragment.saveFilterState(bundle)

        val restoredFragment = ResourcesFragment()
        restoredFragment.restoreFilterState(bundle)

        val selected = restoredFragment.getSelectedFilter()
        assertEquals(setOf("Math", "Science"), selected["subjects"])
        assertEquals(setOf("English"), selected["languages"])
        assertEquals(setOf("Grade 1"), selected["levels"])
        assertEquals(setOf("Video"), selected["mediums"])
        assertEquals(1, restoredFragment.selectedDownloadFilterIndex)

        assertEquals(2, restoredFragment.searchTags.size)
        assertEquals("tag1", restoredFragment.searchTags[0].id)
        assertEquals("Education", restoredFragment.searchTags[0].name)
        assertEquals("tag2", restoredFragment.searchTags[1].id)
        assertEquals("Science & Tech", restoredFragment.searchTags[1].name)
    }

    @Test
    fun `test restoreFilterState with null bundle does not clear or alter initial state`() {
        val fragment = ResourcesFragment()
        fragment.subjects = mutableSetOf("Math")
        fragment.restoreFilterState(null)
        assertEquals(setOf("Math"), fragment.subjects)
        assertTrue(fragment.searchTags.isEmpty())
        assertEquals(0, fragment.selectedDownloadFilterIndex)
    }
}
