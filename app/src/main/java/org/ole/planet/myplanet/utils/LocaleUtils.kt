package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleUtils {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
    @Volatile private var cachedLanguage: String? = null

    fun preload(context: Context) {
        if (cachedLanguage == null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            cachedLanguage = prefs.getString(SELECTED_LANGUAGE, null) ?: Locale.getDefault().language
        }
    }

    fun onAttach(context: Context): Context {
        val lang = cachedLanguage ?: getPersistedData(context, Locale.getDefault().language)
        return applyLocale(context, lang)
    }

    fun getLanguage(context: Context): String {
        return cachedLanguage ?: getPersistedData(context, Locale.getDefault().language)
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)
        return applyLocale(context, language)
    }

    private fun applyLocale(context: Context, language: String): Context {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        val res = context.resources
        val configuration = Configuration(res.configuration)
        configuration.setLocales(LocaleList(locale))
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(SELECTED_LANGUAGE, defaultLanguage) ?: defaultLanguage
    }

    private fun persist(context: Context, language: String) {
        cachedLanguage = language
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit { putString(SELECTED_LANGUAGE, language) }
    }
}
