package org.ole.planet.myplanet.ui.user

import android.os.Bundle
import android.os.Looper
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
import org.robolectric.shadows.ShadowDialog

@AndroidEntryPoint
class ProfileHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.FullscreenTheme)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = R.id.fragment_container })
    }
}

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class UserProfileFragmentRotationTest {

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
    fun rotation_reopensEditProfileWithTheUnsavedEdits() {
        val controller = Robolectric.buildActivity(ProfileHostActivity::class.java).setup()
        controller.get().supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, UserProfileFragment(), TAG).commitNow()
        val fragment = controller.get().supportFragmentManager.findFragmentByTag(TAG) as UserProfileFragment
        fragment.requireView().findViewById<android.view.View>(R.id.btEditProfile).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowDialog.getLatestDialog()
        dialog.findViewById<EditText>(R.id.firstName).setText("Edited First")
        dialog.findViewById<EditText>(R.id.phoneNumber).setText("555")
        dialog.findViewById<RadioGroup>(R.id.rdGender).check(R.id.rbFemale)
        dialog.findViewById<TextView>(R.id.dateOfBirth).text = "Mar 4, 2001"
        fragment.date = "2001-03-04T00:00:00.000Z"

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()

        val restored = ShadowDialog.getLatestDialog()
        assertNotNull(restored)
        assertTrue("edit profile should be open again after rotation", restored.isShowing)
        assertEquals("Edited First", restored.findViewById<EditText>(R.id.firstName).text.toString())
        assertEquals("555", restored.findViewById<EditText>(R.id.phoneNumber).text.toString())
        assertEquals(R.id.rbFemale, restored.findViewById<RadioGroup>(R.id.rdGender).checkedRadioButtonId)
        assertEquals("Mar 4, 2001", restored.findViewById<TextView>(R.id.dateOfBirth).text.toString())
        val newFragment = controller.get().supportFragmentManager.findFragmentByTag(TAG) as UserProfileFragment
        assertEquals("2001-03-04T00:00:00.000Z", newFragment.date)
    }

    private companion object {
        const val TAG = "profile"
    }
}
