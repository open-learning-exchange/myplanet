package org.ole.planet.myplanet.services

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.utils.ThemeMode
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import javax.inject.Inject
import androidx.appcompat.app.AppCompatActivity

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeManagerIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sharedPrefManager: SharedPrefManager

    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        hiltRule.inject()
        // Ensure MainApplication.context is set, similar to NetworkUtilsTest
        org.ole.planet.myplanet.MainApplication.context = ApplicationProvider.getApplicationContext()

        val context = ApplicationProvider.getApplicationContext<Context>()

        // Initialize ThemeManager with FOLLOW_SYSTEM initially to have a known state
        ThemeManager.setThemeMode(context, ThemeMode.FOLLOW_SYSTEM)

        activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().start().resume().get()
    }

    @Test
    fun testShowThemeDialogAndSelectLightMode() {
        ThemeManager.showThemeDialog(activity)

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog?
        assertNotNull("Dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull("Dialog should have a list view", listView)

        // Select LIGHT mode (index 0)
        listView.performItemClick(listView.adapter.getView(0, null, listView), 0, listView.adapter.getItemId(0))

        assertEquals(ThemeMode.LIGHT, ThemeManager.getCurrentThemeMode(activity))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun testShowThemeDialogAndSelectDarkMode() {
        ThemeManager.showThemeDialog(activity)

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog?
        assertNotNull("Dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull("Dialog should have a list view", listView)

        // Select DARK mode (index 1)
        listView.performItemClick(listView.adapter.getView(1, null, listView), 1, listView.adapter.getItemId(1))

        assertEquals(ThemeMode.DARK, ThemeManager.getCurrentThemeMode(activity))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppCompatDelegate.getDefaultNightMode())
    }

    @Test
    fun testShowThemeDialogAndSelectSystemMode() {
        ThemeManager.showThemeDialog(activity)

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog?
        assertNotNull("Dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull("Dialog should have a list view", listView)

        // Select SYSTEM mode (index 2)
        listView.performItemClick(listView.adapter.getView(2, null, listView), 2, listView.adapter.getItemId(2))

        assertEquals(ThemeMode.FOLLOW_SYSTEM, ThemeManager.getCurrentThemeMode(activity))
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.getDefaultNightMode())
    }
}
