package org.ole.planet.myplanet.ui.components

import android.app.Application
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.ThemeManager
import org.ole.planet.myplanet.utils.ThemeMode
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ThemeModeDialogTest {
    private lateinit var mockSpm: SharedPrefManager
    private lateinit var themeManager: ThemeManager

    @Before
    fun setUp() {
        mockSpm = mockk(relaxed = true)
        mockkStatic(AppCompatDelegate::class)
        themeManager = ThemeManager(mockSpm)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testShowThemeModeDialog() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            every { mockSpm.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) } returns ThemeMode.LIGHT

            showThemeModeDialog(activity, themeManager)

            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertNotNull(dialog)
            assertTrue(dialog.isShowing)

            val listView = dialog.listView
            assertNotNull(listView)
            assertEquals(3, listView.count)
            assertEquals(0, listView.checkedItemPosition)

            listView.performItemClick(null, 1, listView.getItemIdAtPosition(1))

            verify { mockSpm.setRawString("theme_mode", ThemeMode.DARK) }
            verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun testShowThemeModeDialogCancel() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            every { mockSpm.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) } returns ThemeMode.LIGHT

            showThemeModeDialog(activity, themeManager)

            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertNotNull(dialog)
            assertTrue(dialog.isShowing)

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()

            verify(exactly = 0) { mockSpm.setRawString(any(), any()) }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
