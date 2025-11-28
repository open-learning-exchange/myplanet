package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocaleHelper {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
    var currentLanguage: String? = null
        private set

    fun onAttach(context: Context): Context {
        val defaultLanguage = Locale.getDefault().language
        return setLocale(context, defaultLanguage, persist = false)
    }

    suspend fun loadPersistedLocale(context: Context): String = withContext(Dispatchers.IO) {
        getPersistedData(context, Locale.getDefault().language)
    }

    fun getLanguage(context: Context): String {
        return getPersistedData(context, Locale.getDefault().language)
    }

    fun setLocale(context: Context, language: String, persist: Boolean = true): Context {
        currentLanguage = language
        if (persist) {
            persist(context, language)
        }

        val locale = Locale(language)
        Locale.setDefault(locale)

        val res = context.resources
        val configuration = Configuration(res.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        return context.createConfigurationContext(configuration)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(SELECTED_LANGUAGE, defaultLanguage) ?: defaultLanguage
    }

    private fun persist(context: Context, language: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit { putString(SELECTED_LANGUAGE, language) }
    }
}
