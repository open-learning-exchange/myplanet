package org.ole.planet.myplanet.services

import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.utils.ThemeMode
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeManagerTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var mockSpm: SharedPrefManager
    private lateinit var themeManager: ThemeManager

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        activity = activityController.get()

        mockSpm = mockk(relaxed = true)
        mockkStatic(AppCompatDelegate::class)

        themeManager = ThemeManager(mockSpm)
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
        unmockkAll()
    }

    @Test
    fun testGetCurrentThemeMode() {
        every { mockSpm.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) } returns ThemeMode.DARK
        val mode = themeManager.getCurrentThemeMode()
        assertEquals(ThemeMode.DARK, mode)
    }

    @Test
    fun testSetThemeModeLight() {
        themeManager.setThemeMode( ThemeMode.LIGHT)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.LIGHT) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
    }

    @Test
    fun testSetThemeModeDark() {
        themeManager.setThemeMode( ThemeMode.DARK)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.DARK) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
    }

    @Test
    fun testSetThemeModeFollowSystem() {
        themeManager.setThemeMode( ThemeMode.FOLLOW_SYSTEM)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
    }

    @Test
    fun testShowThemeDialog() {
        every { mockSpm.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) } returns ThemeMode.LIGHT

        themeManager.showThemeDialog(activity)

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Use ShadowDialog.getLatestDialog() as androidx.appcompat.app.AlertDialog
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)

        val listView = dialog.listView
        assertNotNull(listView)
        assertEquals(3, listView.count)

        // Simulate clicking 'Dark' mode using explicitly position and ID without relying on null view layout
        listView.performItemClick(null, 1, listView.getItemIdAtPosition(1))

        verify { mockSpm.setRawString("theme_mode", ThemeMode.DARK) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
    }
}
