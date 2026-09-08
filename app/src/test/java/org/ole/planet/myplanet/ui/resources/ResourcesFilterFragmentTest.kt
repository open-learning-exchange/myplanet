package org.ole.planet.myplanet.ui.resources

import android.app.Application
import android.view.View
import android.widget.ImageView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnFilterListener
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ResourcesFilterFragmentTest {

    private class TestFilterListener(
        private val data: Map<String, Set<String>>,
        private val selectedFilter: Map<String, Set<String>> = emptyMap()
    ) : OnFilterListener {
        var lastFilteredSubjects: Set<String>? = null
        var lastFilteredLanguages: Set<String>? = null
        var lastFilteredMediums: Set<String>? = null
        var lastFilteredLevels: Set<String>? = null
        var filterCallCount = 0

        override fun filter(
            subjects: MutableSet<String>,
            languages: MutableSet<String>,
            mediums: MutableSet<String>,
            levels: MutableSet<String>
        ) {
            filterCallCount++
            lastFilteredSubjects = HashSet(subjects)
            lastFilteredLanguages = HashSet(languages)
            lastFilteredMediums = HashSet(mediums)
            lastFilteredLevels = HashSet(levels)
        }

        override suspend fun getData(): Map<String, Set<String>> = data

        override fun getSelectedFilter(): Map<String, Set<String>> = selectedFilter
    }

    private fun setupFragment(
        data: Map<String, Set<String>> = mapOf(
            "subjects" to setOf("Math", "Science"),
            "languages" to setOf("English", "Spanish"),
            "mediums" to setOf("PDF", "Video"),
            "levels" to setOf("Grade 1", "Grade 2")
        ),
        selectedFilter: Map<String, Set<String>> = emptyMap()
    ): Pair<ResourcesFilterFragment, TestFilterListener> {
        val activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents)

        val fragment = ResourcesFilterFragment()
        val listener = TestFilterListener(data, selectedFilter)
        fragment.setListener(listener)

        fragment.show(activity.supportFragmentManager, "filter_dialog")
        activity.supportFragmentManager.executePendingTransactions()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        return Pair(fragment, listener)
    }

    @Test
    fun `test fragment inflates and iv_close is present`() {
        val (fragment, _) = setupFragment()
        val view = fragment.view
        assertNotNull(view)
        val ivClose = view?.findViewById<ImageView>(R.id.iv_close)
        assertNotNull(ivClose)
    }

    @Test
    fun `test selecting subject filters only by subject`() {
        val (fragment, listener) = setupFragment()
        val view = fragment.requireView()

        val listSub = view.findViewById<ListView>(R.id.list_sub)
        assertNotNull(listSub.adapter)
        assertEquals(2, listSub.adapter.count)

        // Click first subject "Math"
        fragment.onItemClick(listSub, listSub, 0, 0L)

        assertEquals(ResourcesFilterFragment.FilterCategory.SUBJECTS, fragment.activeCategory)
        assertEquals(setOf("Math"), listener.lastFilteredSubjects)
        assertTrue(listener.lastFilteredLanguages!!.isEmpty())
        assertTrue(listener.lastFilteredMediums!!.isEmpty())
        assertTrue(listener.lastFilteredLevels!!.isEmpty())
    }

    @Test
    fun `test changing category header resets previous category filter`() {
        val (fragment, listener) = setupFragment()
        val view = fragment.requireView()

        val listSub = view.findViewById<ListView>(R.id.list_sub)
        fragment.onItemClick(listSub, listSub, 0, 0L)
        assertEquals(setOf("Math"), fragment.selectedSubs)
        assertEquals(ResourcesFilterFragment.FilterCategory.SUBJECTS, fragment.activeCategory)

        // Change category to Mediums by clicking mediumsLayout
        val mediumsLayout = view.findViewById<View>(R.id.mediums_layout)
        mediumsLayout.performClick()

        // Subjects must be reset
        assertTrue(fragment.selectedSubs.isEmpty())
        assertFalse(listSub.isItemChecked(0))
        assertEquals(ResourcesFilterFragment.FilterCategory.MEDIUMS, fragment.activeCategory)

        // Filter listener must have received reset (empty) filter
        assertTrue(listener.lastFilteredSubjects!!.isEmpty())
        assertTrue(listener.lastFilteredMediums!!.isEmpty())
    }

    @Test
    fun `test selecting item in different category resets previous category`() {
        val (fragment, listener) = setupFragment()
        val view = fragment.requireView()

        val listSub = view.findViewById<ListView>(R.id.list_sub)
        val listMedium = view.findViewById<ListView>(R.id.list_medium)

        // Select "Math" in Subjects
        fragment.onItemClick(listSub, listSub, 0, 0L)
        assertEquals(setOf("Math"), fragment.selectedSubs)

        // Directly select "PDF" in Mediums
        fragment.onItemClick(listMedium, listMedium, 0, 0L)

        // Subjects must be reset and only Mediums active
        assertTrue(fragment.selectedSubs.isEmpty())
        assertFalse(listSub.isItemChecked(0))
        assertEquals(setOf("PDF"), fragment.selectedMeds)
        assertTrue(listMedium.isItemChecked(0))
        assertEquals(ResourcesFilterFragment.FilterCategory.MEDIUMS, fragment.activeCategory)

        assertTrue(listener.lastFilteredSubjects!!.isEmpty())
        assertEquals(setOf("PDF"), listener.lastFilteredMediums)
    }

    @Test
    fun `test collapsing and expanding same category preserves selection`() {
        val (fragment, listener) = setupFragment()
        val view = fragment.requireView()

        val subjectsLayout = view.findViewById<View>(R.id.subjects_layout)
        val listSub = view.findViewById<ListView>(R.id.list_sub)

        // Expand subjects & select "Math"
        subjectsLayout.performClick()
        fragment.onItemClick(listSub, listSub, 0, 0L)
        assertEquals(setOf("Math"), fragment.selectedSubs)

        // Collapse subjects
        subjectsLayout.performClick()
        assertEquals(setOf("Math"), fragment.selectedSubs)

        // Re-expand subjects (same category, not category change)
        subjectsLayout.performClick()
        assertEquals(setOf("Math"), fragment.selectedSubs)
        assertEquals(ResourcesFilterFragment.FilterCategory.SUBJECTS, fragment.activeCategory)
        assertEquals(setOf("Math"), listener.lastFilteredSubjects)
    }

    @Test
    fun `test unchecking all items resets active category to NONE`() {
        val (fragment, listener) = setupFragment()
        val view = fragment.requireView()

        val listSub = view.findViewById<ListView>(R.id.list_sub)

        // Check "Math"
        fragment.onItemClick(listSub, listSub, 0, 0L)
        assertEquals(setOf("Math"), fragment.selectedSubs)
        assertEquals(ResourcesFilterFragment.FilterCategory.SUBJECTS, fragment.activeCategory)

        // Uncheck "Math"
        fragment.onItemClick(listSub, listSub, 0, 0L)
        assertTrue(fragment.selectedSubs.isEmpty())
        assertEquals(ResourcesFilterFragment.FilterCategory.NONE, fragment.activeCategory)
        assertTrue(listener.lastFilteredSubjects!!.isEmpty())
    }

    @Test
    fun `test initList with pre-existing filter sets active category and preserves it`() {
        val (fragment, _) = setupFragment(
            selectedFilter = mapOf("mediums" to setOf("Video"))
        )

        assertEquals(ResourcesFilterFragment.FilterCategory.MEDIUMS, fragment.activeCategory)
        assertEquals(setOf("Video"), fragment.selectedMeds)
        assertTrue(fragment.selectedSubs.isEmpty())
        assertTrue(fragment.selectedLang.isEmpty())
        assertTrue(fragment.selectedLvls.isEmpty())
    }
}
