package org.ole.planet.myplanet.utils

/** Switches the app's display language. The Android implementation is [AndroidAppLocale]. */
interface AppLocale {
    /** Persists [languageCode] (e.g. "en", "ar") as the app language and applies it as the default locale. */
    fun setLanguage(languageCode: String)
}
