package org.ole.planet.myplanet.ui.teams

import android.os.Bundle
import android.os.Looper
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@AndroidEntryPoint
class TeamDetailHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.FullscreenTheme)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = R.id.fragment_container })
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class TeamDetailFragmentRotationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    @Before
    fun setUp() {
        hiltRule.inject()
        UrlUtils.init(sharedPrefManager)
    }

    @Test
    fun rotation_remembersTheTabEachTeamWasOn() {
        val controller = Robolectric.buildActivity(TeamDetailHostActivity::class.java).setup()
        val fragment = TeamDetailFragment.newInstance("team1", "Team", "local", isMyTeam = true)
        controller.get().supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, fragment, TAG).commitNow()
        lastPages(fragment)["team1"] = TeamPageConfig.TasksPage.id
        shadowOf(Looper.getMainLooper()).idle()

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()

        val restored = controller.get().supportFragmentManager.findFragmentByTag(TAG) as TeamDetailFragment
        assertEquals(TeamPageConfig.TasksPage.id, lastPages(restored)["team1"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun lastPages(fragment: TeamDetailFragment) =
        TeamDetailFragment::class.java.getDeclaredField("teamLastPage").apply { isAccessible = true }
            .get(fragment) as MutableMap<String, String>

    private companion object {
        const val TAG = "teamDetail"
    }
}
