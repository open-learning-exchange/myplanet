package org.ole.planet.myplanet.ui.resources

import android.view.View
import android.widget.LinearLayout
import com.google.android.material.chip.Chip
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.TestDashboardElementActivity
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ResourcesFragmentActionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var tagDao: TagDao

    @Before
    fun setUp() {
        hiltRule.inject()
        UrlUtils.init(sharedPrefManager)
        runBlocking(Dispatchers.IO) {
            appDatabase.clearAllTables()
        }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            appDatabase.clearAllTables()
        }
    }

    private fun launchFragment(): ResourcesFragment {
        val activity = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup().get()
        val fragment = ResourcesFragment()
        activity.openCallFragment(fragment, "library")
        activity.supportFragmentManager.executePendingTransactions()
        return fragment
    }

    @Test
    fun testSortCapsule_showsSortSheet() {
        val fragment = launchFragment()

        fragment.requireView().findViewById<View>(R.id.btn_capsule_sort).performClick()
        fragment.childFragmentManager.executePendingTransactions()

        val sheet = fragment.childFragmentManager.fragments.firstOrNull { it is ResourcesSortFragment }
        assertNotNull(sheet)
    }

    @Test
    fun testFilterCapsule_showsFilterSheet() {
        runBlocking {
            tagDao.upsertAll(
                listOf(
                    TagEntity().apply {
                        id = "tag_science"
                        name = "Science"
                        db = "resources"
                        isAttached = false
                    }
                )
            )
        }

        val fragment = launchFragment()

        fragment.requireView().findViewById<View>(R.id.btn_capsule_filters).performClick()
        fragment.childFragmentManager.executePendingTransactions()

        val sheet = fragment.childFragmentManager.fragments.firstOrNull { it is ResourcesFilterFragment }
        assertNotNull(sheet)
    }

    @Test
    fun testFilterBadge_reflectsSelectedTagCount() {
        val fragment = launchFragment()
        val badge = fragment.requireView().findViewById<View>(R.id.tv_capsule_filter_badge)

        assertEquals(View.GONE, badge.visibility)

        fragment.onTagSelected(TagEntity().apply {
            id = "test_tag"
            name = "Test Tag"
        })
        fragment.childFragmentManager.executePendingTransactions()

        assertEquals(View.VISIBLE, badge.visibility)
    }

    @Test
    fun testClearAllFilters_clearsTagsAndHidesBadge() {
        val fragment = launchFragment()
        val badge = fragment.requireView().findViewById<View>(R.id.tv_capsule_filter_badge)

        fragment.onTagSelected(TagEntity().apply {
            id = "test_tag"
            name = "Test Tag"
        })
        assertTrue(fragment.searchTags.isNotEmpty())

        fragment.clearAllFilters()

        assertTrue(fragment.searchTags.isEmpty())
        assertEquals(View.GONE, badge.visibility)
    }

    @Test
    fun testActiveFacetChips_renderingAndRemoval() {
        val fragment = launchFragment()
        fragment.filter(
            subjects = mutableSetOf("Math"),
            languages = mutableSetOf("English"),
            mediums = mutableSetOf("pdf"),
            levels = mutableSetOf("Primary")
        )

        val chipRow = fragment.requireView().findViewById<LinearLayout>(R.id.chip_filter_row)
        val chips = (0 until chipRow.childCount).mapNotNull { chipRow.getChildAt(it) as? Chip }
        assertEquals(4, chips.size)

        val mathChip = chips.first { it.text == "Math" }
        mathChip.performCloseIconClick()

        assertTrue(!fragment.subjects.contains("Math"))
        val updatedChips = (0 until chipRow.childCount).mapNotNull { chipRow.getChildAt(it) as? Chip }
        assertEquals(3, updatedChips.size)
    }
}
