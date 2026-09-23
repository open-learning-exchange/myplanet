package org.ole.planet.myplanet.ui.teams

import android.widget.Button
import androidx.viewpager2.widget.ViewPager2
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnTeamPageListener
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.TestDashboardElementActivity
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TeamDetailFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var teamDao: TeamDao

    @Inject
    lateinit var userDao: UserDao

    @Before
    fun setUp() {
        hiltRule.inject()
        UrlUtils.init(sharedPrefManager)
        runBlocking(Dispatchers.IO) {
            appDatabase.clearAllTables()
            userDao.upsert(UserEntity().apply {
                id = "user_1"
                name = "Test User"
                planetCode = "code"
            })
            teamDao.upsert(MyTeam().apply {
                _id = "team_123"
                name = "Test Team"
                type = "team"
            })
            teamDao.upsert(MyTeam().apply {
                _id = "enterprise_123"
                name = "Enterprise Team"
                type = "enterprise"
            })
        }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            appDatabase.clearAllTables()
        }
    }

    private fun launchFragment(teamId: String, name: String, type: String): TeamDetailFragment {
        val activity = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup().get()
        val fragment = TeamDetailFragment.newInstance(teamId, name, type, true)
        activity.openCallFragment(fragment, "team_detail")
        activity.supportFragmentManager.executePendingTransactions()
        ShadowLooper.idleMainLooper()
        return fragment
    }

    @Test
    fun btnAddDoc_clickOnStandardTeam_resolvesActivePageAndCallsOnAddCourse() {
        val fragment = launchFragment("team_123", "Test Team", "team")
        val btnAddDoc = fragment.requireView().findViewById<Button>(R.id.btn_add_doc)

        btnAddDoc.performClick()
        ShadowLooper.idleMainLooper()

        val viewPager2 = fragment.requireView().findViewById<ViewPager2>(R.id.viewPager2)
        val adapter = viewPager2.adapter
        assertNotNull(adapter)

        val itemId = adapter!!.getItemId(viewPager2.currentItem)
        val activeFragment = fragment.childFragmentManager.findFragmentByTag("f$itemId")

        assertNotNull(activeFragment)
        assertTrue(activeFragment is OnTeamPageListener)
    }

    @Test
    fun btnAddDoc_clickOnEnterpriseTeam_resolvesActivePageAndCallsOnAddDocument() {
        val fragment = launchFragment("enterprise_123", "Enterprise Team", "enterprise")
        val btnAddDoc = fragment.requireView().findViewById<Button>(R.id.btn_add_doc)

        btnAddDoc.performClick()
        ShadowLooper.idleMainLooper()

        val viewPager2 = fragment.requireView().findViewById<ViewPager2>(R.id.viewPager2)
        val adapter = viewPager2.adapter
        assertNotNull(adapter)

        val itemId = adapter!!.getItemId(viewPager2.currentItem)
        val activeFragment = fragment.childFragmentManager.findFragmentByTag("f$itemId")

        assertNotNull(activeFragment)
        assertTrue(activeFragment is OnTeamPageListener)
    }
}
