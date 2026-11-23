package org.ole.planet.myplanet.ui.user

import android.widget.TextView
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
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LearnerRegistrationActivityRotationTest {

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
    fun rotation_keepsThePickedBirthDate() {
        val controller = Robolectric.buildActivity(LearnerRegistrationActivity::class.java).setup()
        controller.get().dob = "2001-02-03"
        controller.get().findViewById<TextView>(R.id.txt_dob).text = "2001-02-03"

        controller.recreate()

        assertEquals("2001-02-03", controller.get().dob)
        assertEquals("2001-02-03", controller.get().findViewById<TextView>(R.id.txt_dob).text.toString())
    }
}
