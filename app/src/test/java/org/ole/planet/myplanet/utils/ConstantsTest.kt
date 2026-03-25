package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ConstantsTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().clear().commit()
    }

    @Test
    fun testShelfDataList() {
        val shelfDataList = Constants.shelfDataList
        assertEquals(4, shelfDataList.size)

        assertEquals("resourceIds", shelfDataList[0].key)
        assertEquals("resources", shelfDataList[0].type)
        assertEquals("resourceId", shelfDataList[0].categoryKey)

        assertEquals("meetupIds", shelfDataList[1].key)
        assertEquals("meetups", shelfDataList[1].type)
        assertEquals("meetupId", shelfDataList[1].categoryKey)

        assertEquals("courseIds", shelfDataList[2].key)
        assertEquals("courses", shelfDataList[2].type)
        assertEquals("courseId", shelfDataList[2].categoryKey)

        assertEquals("myTeamIds", shelfDataList[3].key)
        assertEquals("teams", shelfDataList[3].type)
        assertEquals("teamId", shelfDataList[3].categoryKey)
    }

    @Test
    fun testLabels() {
        val labels = Constants.LABELS
        assertEquals(3, labels.size)
        assertEquals("offer", labels["Offer"])
        assertEquals("help", labels["Help wanted"])
        assertEquals("advice", labels["Request for advice"])
    }

    @Test
    fun testClassList() {
        val classList = Constants.classList
        assertEquals(16, classList.size)
        assertEquals(RealmNews::class.java, classList["news"])
        assertEquals(RealmTag::class.java, classList["tags"])
        assertEquals(RealmOfflineActivity::class.java, classList["login_activities"])
        assertEquals(RealmRating::class.java, classList["ratings"])
        assertEquals(RealmSubmission::class.java, classList["submissions"])
        assertEquals(RealmMyCourse::class.java, classList["courses"])
        assertEquals(RealmAchievement::class.java, classList["achievements"])
        assertEquals(RealmFeedback::class.java, classList["feedback"])
        assertEquals(RealmMyTeam::class.java, classList["teams"])
        assertEquals(RealmTeamTask::class.java, classList["tasks"])
        assertEquals(RealmMeetup::class.java, classList["meetups"])
        assertEquals(RealmHealthExamination::class.java, classList["health"])
        assertEquals(RealmCertification::class.java, classList["certifications"])
        assertEquals(RealmTeamLog::class.java, classList["team_activities"])
        assertEquals(RealmCourseProgress::class.java, classList["courses_progress"])
        assertEquals(RealmNotification::class.java, classList["notifications"])
    }

    @Test
    fun testShowBetaFeature() {
        assertFalse(Constants.showBetaFeature("", context))

        sharedPreferences.edit().putBoolean("beta_function", true).commit()
        assertTrue(Constants.showBetaFeature("", context))
    }

    @Test
    fun testIsBetaWifiFeatureEnabled() {
        assertFalse(Constants.isBetaWifiFeatureEnabled(context))

        sharedPreferences.edit().putBoolean("beta_function", true).commit()
        assertFalse(Constants.isBetaWifiFeatureEnabled(context))

        sharedPreferences.edit().putBoolean(Constants.KEY_SYNC, true).commit()
        assertTrue(Constants.isBetaWifiFeatureEnabled(context))

        sharedPreferences.edit().putBoolean("beta_function", false).commit()
        assertFalse(Constants.isBetaWifiFeatureEnabled(context))
    }

    @Test
    fun testAutoSynFeature() {
        assertFalse(Constants.autoSynFeature("some_key", context))

        sharedPreferences.edit().putBoolean("some_key", true).commit()
        assertTrue(Constants.autoSynFeature("some_key", context))

        sharedPreferences.edit().putBoolean("some_key", false).commit()
        assertFalse(Constants.autoSynFeature("some_key", context))

        assertFalse(Constants.autoSynFeature(null, context))
        sharedPreferences.edit().putBoolean(null, true).commit()
        assertTrue(Constants.autoSynFeature(null, context))
    }
}
