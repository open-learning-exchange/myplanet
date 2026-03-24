package org.ole.planet.myplanet.services

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.EntryPointAccessors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.utils.ThemeMode

class ThemeManagerTest {
    private lateinit var mockContext: Context
    private lateinit var mockSpm: SharedPrefManager
    private lateinit var mockEntryPoint: AutoSyncEntryPoint

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        val mockAppContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockAppContext

        mockSpm = mockk(relaxed = true)
        mockEntryPoint = mockk(relaxed = true)

        mockkStatic(EntryPointAccessors::class)
        mockkStatic(AppCompatDelegate::class)

        every { EntryPointAccessors.fromApplication(any(), AutoSyncEntryPoint::class.java) } returns mockEntryPoint
        every { mockEntryPoint.sharedPrefManager() } returns mockSpm
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetCurrentThemeMode() {
        every { mockSpm.getRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) } returns ThemeMode.DARK
        val mode = ThemeManager.getCurrentThemeMode(mockContext)
        assertEquals(ThemeMode.DARK, mode)
    }

    @Test
    fun testSetThemeModeLight() {
        ThemeManager.setThemeMode(mockContext, ThemeMode.LIGHT)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.LIGHT) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
    }

    @Test
    fun testSetThemeModeDark() {
        ThemeManager.setThemeMode(mockContext, ThemeMode.DARK)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.DARK) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
    }

    @Test
    fun testSetThemeModeFollowSystem() {
        ThemeManager.setThemeMode(mockContext, ThemeMode.FOLLOW_SYSTEM)
        verify { mockSpm.setRawString("theme_mode", ThemeMode.FOLLOW_SYSTEM) }
        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
    }
}
