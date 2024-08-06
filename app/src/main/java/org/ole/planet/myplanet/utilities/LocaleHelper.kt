package org.ole.planet.myplanet.utilities

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import org.ole.planet.myplanet.utilities.Constants.SELECTED_LANGUAGE
import java.util.Locale

object LocaleHelper {
    fun onAttach(context: Context): Context? {
        val lang = getPersistedData(context, Locale.getDefault().language)
        return lang?.let { setLocale(context, it) }
    }

    fun onAttach(context: Context, defaultLanguage: String): Context? {
        val lang = getPersistedData(context, defaultLanguage)
        return lang?.let { setLocale(context, it) }
    }

    fun getLanguage(context: Context): String? {
        return getPersistedData(context, Locale.getDefault().language)
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            updateResources(context, language)
        } else updateResourcesLegacy(
            context,
            language
        )
    }

    fun getLocale(context: Context): Locale {
        val language = getLanguage(context) ?: Locale.getDefault().language
        return Locale(language)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String? {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        return preferences.getString(SELECTED_LANGUAGE, defaultLanguage)
    }

    private fun persist(context: Context, language: String?) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = preferences.edit()
        editor.putString(SELECTED_LANGUAGE, language)
        editor.apply()
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    @Suppress("deprecation")
    private fun updateResourcesLegacy(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        configuration.locale = locale
        configuration.setLayoutDirection(locale)
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return context
    }
}