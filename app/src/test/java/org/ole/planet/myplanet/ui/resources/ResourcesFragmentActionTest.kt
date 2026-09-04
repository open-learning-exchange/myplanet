package org.ole.planet.myplanet.ui.resources

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.DashboardElementActivity
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@AndroidEntryPoint
class TestResourcesFragmentActivity : DashboardElementActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        val container = FrameLayout(this).apply { id = R.id.fragment_container }
        setContentView(container)
        navigationView = BottomNavigationView(this)
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ResourcesFragmentActionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    lateinit var tagDao: TagDao

    @Before
    fun setUp() {
        hiltRule.inject()
        UrlUtils.init(sharedPrefManager)
    }

    @Test
    fun testCollectionsButton_showsCollectionsFragmentAndDismissesCardFilter() {
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

        val activity = Robolectric.buildActivity(TestResourcesFragmentActivity::class.java).setup().get()
        val fm = activity.supportFragmentManager

        val fragment = ResourcesFragment()
        activity.openCallFragment(fragment, "library")
        fm.executePendingTransactions()

        val cardFilter = fragment.requireView().findViewById<View>(R.id.card_filter)
        cardFilter.visibility = View.VISIBLE

        val btnCollections = fragment.requireView().findViewById<View>(R.id.btn_collections)
        btnCollections.performClick()
        fragment.childFragmentManager.executePendingTransactions()

        assertEquals(View.GONE, cardFilter.visibility)
        val dialog = fragment.childFragmentManager.fragments.firstOrNull { it is CollectionsFragment }
        assertNotNull(dialog)
    }

    @Test
    fun testClearTagsButton_clearsTagsAndDismissesCardFilter() {
        val activity = Robolectric.buildActivity(TestResourcesFragmentActivity::class.java).setup().get()
        val fm = activity.supportFragmentManager

        val fragment = ResourcesFragment()
        activity.openCallFragment(fragment, "library")
        fm.executePendingTransactions()

        fragment.searchTags.add(TagEntity().apply {
            id = "test_tag"
            name = "Test Tag"
        })

        val cardFilter = fragment.requireView().findViewById<View>(R.id.card_filter)
        cardFilter.visibility = View.VISIBLE

        val btnClearTags = fragment.requireView().findViewById<View>(R.id.btn_clear_tags)
        btnClearTags.performClick()

        assertEquals(View.GONE, cardFilter.visibility)
        assertTrue(fragment.searchTags.isEmpty())
    }

    @Test
    fun testCollectionsButton_whenNoCollections_dismissesDialog() {
        val activity = Robolectric.buildActivity(TestResourcesFragmentActivity::class.java).setup().get()
        val fm = activity.supportFragmentManager

        val fragment = ResourcesFragment()
        activity.openCallFragment(fragment, "library")
        fm.executePendingTransactions()

        val cardFilter = fragment.requireView().findViewById<View>(R.id.card_filter)
        cardFilter.visibility = View.VISIBLE

        val btnCollections = fragment.requireView().findViewById<View>(R.id.btn_collections)
        btnCollections.performClick()
        fragment.childFragmentManager.executePendingTransactions()

        assertEquals(View.GONE, cardFilter.visibility)
        val dialog = fragment.childFragmentManager.fragments.firstOrNull { it is CollectionsFragment }
        assertEquals(null, dialog)
    }
}
