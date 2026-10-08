package org.ole.planet.myplanet.services

import androidx.appcompat.app.AppCompatDelegate
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.ThemeMode

class ThemeManagerTest {
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
    fun testGetCurrentThemeMode() {
        every { mockSpm.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) } returns ThemeMode.DARK
        val mode = themeManager.getCurrentThemeMode()
        assertEquals(ThemeMode.DARK, mode)
    }

    @Test
    fun testSetThemeModeLight() {
        themeManager.setThemeMode(ThemeMode.LIGHT)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.LIGHT) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
    }

    @Test
    fun testSetThemeModeDark() {
        themeManager.setThemeMode(ThemeMode.DARK)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.DARK) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
    }

    @Test
    fun testSetThemeModeFollowSystem() {
        themeManager.setThemeMode(ThemeMode.FOLLOW_SYSTEM)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
    }
}
