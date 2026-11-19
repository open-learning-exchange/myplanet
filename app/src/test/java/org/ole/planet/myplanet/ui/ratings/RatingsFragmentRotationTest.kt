package org.ole.planet.myplanet.ui.ratings

import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.ui.dashboard.TestDashboardElementActivity
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@AndroidEntryPoint
class RatingHostFragment : Fragment(), OnRatingChangeListener {
    val changes = mutableListOf<String>()
    override fun onRatingChanged() { changes.add("refresh") }
    override fun onRatingChanged(type: String, id: String) { changes.add("$type:$id") }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class RatingsFragmentRotationTest {

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
    fun ratingAfterRotation_stillRefreshesTheScreenThatOpenedTheDialog() {
        val controller = Robolectric.buildActivity(TestDashboardElementActivity::class.java).setup()
        controller.get().supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, RatingHostFragment(), HOST_TAG).commitNow()
        val host = controller.get().supportFragmentManager.findFragmentByTag(HOST_TAG) as RatingHostFragment
        controller.get().showRatingDialog("course", "c1", "Course", host)
        host.childFragmentManager.executePendingTransactions()

        controller.recreate()

        val newHost = controller.get().supportFragmentManager.findFragmentByTag(HOST_TAG) as RatingHostFragment
        val dialog = newHost.childFragmentManager.findFragmentByTag(RatingsFragment.TAG) as? RatingsFragment
        assertNotNull("the rating dialog should come back after rotation", dialog)
        assertSame(newHost, dialog!!.parentFragment)

        dialog.notifyRatingChanged()

        assertEquals(listOf("course:c1"), newHost.changes)
    }

    private companion object {
        const val HOST_TAG = "ratingHost"
    }
}
