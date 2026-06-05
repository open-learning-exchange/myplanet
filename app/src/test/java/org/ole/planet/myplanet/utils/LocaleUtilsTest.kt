package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class LocaleUtilsTest {

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        prefs = PreferenceManager.getDefaultSharedPreferences(appContext)

        val cachedLanguageField = LocaleUtils::class.java.getDeclaredField("cachedLanguage")
        cachedLanguageField.isAccessible = true
        cachedLanguageField.set(LocaleUtils, null)

        val cachedPrefsField = LocaleUtils::class.java.getDeclaredField("cachedPrefs")
        cachedPrefsField.isAccessible = true
        cachedPrefsField.set(LocaleUtils, null)
    }

    @After
    fun tearDown() {
        val cachedLanguageField = LocaleUtils::class.java.getDeclaredField("cachedLanguage")
        cachedLanguageField.isAccessible = true
        cachedLanguageField.set(LocaleUtils, null)

        val cachedPrefsField = LocaleUtils::class.java.getDeclaredField("cachedPrefs")
        cachedPrefsField.isAccessible = true
        cachedPrefsField.set(LocaleUtils, null)

        prefs.edit().clear().apply()
    }

    @Test
    fun testOnAttachColdStart() {
        prefs.edit().putString("Locale.Helper.Selected.Language", "ar").apply()

        val spiedContext = spyk(appContext)
        val dummyContext = mockk<Context>()

        every { spiedContext.createConfigurationContext(any()) } returns dummyContext

        val resultContext = LocaleUtils.onAttach(spiedContext)

        assertEquals(dummyContext, resultContext)
        verify {
            spiedContext.createConfigurationContext(withArg { config ->
                assertEquals("ar", config.locales[0].language)
                assertEquals(View.LAYOUT_DIRECTION_RTL, config.layoutDirection)
            })
        }
    }

    @Test
    fun testOnAttachFastPath() {
        prefs.edit().putString("Locale.Helper.Selected.Language", "fr").apply()

        // Populate cache
        LocaleUtils.preload(appContext)

        // Clear pref to verify it reads from cache, not prefs
        prefs.edit().clear().apply()

        val spiedContext = spyk(appContext)
        val dummyContext = mockk<Context>()

        every { spiedContext.createConfigurationContext(any()) } returns dummyContext

        val resultContext = LocaleUtils.onAttach(spiedContext)

        assertEquals(dummyContext, resultContext)
        verify {
            spiedContext.createConfigurationContext(withArg { config ->
                assertEquals("fr", config.locales[0].language)
            })
        }
    }

    @Test
    fun testGetLanguageWithPreference() {
        prefs.edit().putString("Locale.Helper.Selected.Language", "es").apply()

        val language = LocaleUtils.getLanguage(appContext)

        assertEquals("es", language)
    }

    @Test
    fun testGetLanguageFallbackToDefault() {
        val defaultLanguage = Locale.getDefault().language
        val language = LocaleUtils.getLanguage(appContext)

        assertEquals(defaultLanguage, language)
    }

    @Test
    fun testSetLocale() {
        val spiedContext = spyk(appContext)
        val dummyContext = mockk<Context>()

        every { spiedContext.createConfigurationContext(any()) } returns dummyContext

        val resultContext = LocaleUtils.setLocale(spiedContext, "de")

        assertEquals(dummyContext, resultContext)
        assertEquals("de", prefs.getString("Locale.Helper.Selected.Language", null))

        verify {
            spiedContext.createConfigurationContext(withArg { config ->
                assertEquals("de", config.locales[0].language)
            })
        }
    }
}
