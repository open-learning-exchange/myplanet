package org.ole.planet.myplanet.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import dagger.hilt.android.EntryPointAccessors
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
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.utils.ThemeMode
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = dagger.hilt.android.testing.HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeManagerTest {
    private lateinit var activityController: ActivityController<AppCompatActivity>
    private lateinit var activity: AppCompatActivity
    private lateinit var mockSpm: SharedPrefManager
    private lateinit var mockEntryPoint: CoreDependenciesEntryPoint

    @Before
    fun setUp() {
        activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        activity = activityController.get()

        mockSpm = mockk(relaxed = true)
        mockEntryPoint = mockk(relaxed = true)

        mockkStatic(EntryPointAccessors::class)
        mockkStatic(AppCompatDelegate::class)

        every { EntryPointAccessors.fromApplication(any(), CoreDependenciesEntryPoint::class.java) } returns mockEntryPoint
        every { mockEntryPoint.sharedPrefManager() } returns mockSpm
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
        unmockkAll()
    }

    @Test
    fun testGetCurrentThemeMode() {
        every { mockSpm.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) } returns ThemeMode.DARK
        val mode = ThemeManager.getCurrentThemeMode(activity)
        assertEquals(ThemeMode.DARK, mode)
    }

    @Test
    fun testSetThemeModeLight() {
        ThemeManager.setThemeMode(activity, ThemeMode.LIGHT)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.LIGHT) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
    }

    @Test
    fun testSetThemeModeDark() {
        ThemeManager.setThemeMode(activity, ThemeMode.DARK)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.DARK) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
    }

    @Test
    fun testSetThemeModeFollowSystem() {
        ThemeManager.setThemeMode(activity, ThemeMode.FOLLOW_SYSTEM)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
    }

    @Test
    fun testShowThemeDialog() {
        every { mockSpm.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) } returns ThemeMode.LIGHT

        ThemeManager.showThemeDialog(activity)

        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)

        val listView = dialog.listView
        assertNotNull(listView)
        assertEquals(3, listView.count)

        // Simulate clicking 'Dark' mode
        listView.performItemClick(listView.getChildAt(1), 1, listView.getItemIdAtPosition(1))

        verify { mockSpm.setRawString("theme_mode", ThemeMode.DARK) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
    }
}
