package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class LocaleUtilsTest {

    @Before
    fun setUp() {
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
    }

    @Test
    fun testOnAttach() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit().putString("Locale.Helper.Selected.Language", "fr").apply()

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
    fun testGetLanguage() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        prefs.edit().putString("Locale.Helper.Selected.Language", "es").apply()

        val language = LocaleUtils.getLanguage(appContext)

        assertEquals("es", language)
    }
}
