package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import org.junit.Assert.assertEquals
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
        fragment.onTagSelected(TagEntity().apply {
            id = "tag1"
            name = "Education"
        })

        val bundle = Bundle()
        fragment.saveFilterState(bundle)

        val restoredFragment = ResourcesFragment()
        restoredFragment.restoreFilterState(bundle)

        val selected = restoredFragment.getSelectedFilter()
        assertEquals(setOf("Math", "Science"), selected["subjects"])
        assertEquals(setOf("English"), selected["languages"])
        assertEquals(setOf("Grade 1"), selected["levels"])
        assertEquals(setOf("Video"), selected["mediums"])
    }
}
