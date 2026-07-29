package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.Locale

object LocaleUtils {
    private const val SELECTED_LANGUAGE = "Locale.Helper.Selected.Language"
    private const val SELECTED_TEXT_SCALE = "Locale.Helper.Selected.TextScale"
    const val DEFAULT_TEXT_SCALE = 1.0f
    @Volatile private var cachedLanguage: String? = null
    @Volatile private var cachedTextScale: Float? = null
    @Volatile private var cachedPrefs: SharedPreferences? = null

    fun preload(context: Context) {
        if (cachedLanguage == null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            cachedPrefs = prefs
            cachedLanguage = prefs.getString(SELECTED_LANGUAGE, null) ?: Locale.getDefault().language
        }
    }

    // Reads directly through SharedPreferences (not the Hilt-backed SharedPrefManager): this
    // runs from Application/Activity attachBaseContext, which fires before Hilt's singleton
    // component exists for the Application itself.
    fun onAttach(context: Context): Context {
        val lang = cachedLanguage ?: getPersistedData(context, Locale.getDefault().language)
        val textScale = cachedTextScale ?: getPersistedTextScale(context)
        return applyConfiguration(context, lang, textScale)
    }

    fun getLanguage(context: Context): String {
        return cachedLanguage ?: getPersistedData(context, Locale.getDefault().language)
    }

    fun getTextScale(context: Context): Float {
        return cachedTextScale ?: getPersistedTextScale(context)
    }

    fun setLocale(context: Context, language: String): Context {
        persist(context, language)
        return applyConfiguration(context, language, getTextScale(context))
    }

    fun setTextScale(context: Context, textScale: Float): Context {
        persistTextScale(context, textScale)
        return applyConfiguration(context, getLanguage(context), textScale)
    }

    private fun applyConfiguration(context: Context, language: String, textScale: Float): Context {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        val res = context.resources
        val configuration = Configuration(res.configuration)
        configuration.setLocales(LocaleList(locale))
        configuration.setLayoutDirection(locale)
        configuration.fontScale = textScale

        return context.createConfigurationContext(configuration)
    }

    private fun getPersistedData(context: Context, defaultLanguage: String): String {
        val preferences = cachedPrefs ?: PreferenceManager.getDefaultSharedPreferences(context).also { cachedPrefs = it }
        return preferences.getString(SELECTED_LANGUAGE, defaultLanguage) ?: defaultLanguage
    }

    private fun getPersistedTextScale(context: Context): Float {
        val preferences = cachedPrefs ?: PreferenceManager.getDefaultSharedPreferences(context).also { cachedPrefs = it }
        return preferences.getFloat(SELECTED_TEXT_SCALE, DEFAULT_TEXT_SCALE).also { cachedTextScale = it }
    }

    private fun persist(context: Context, language: String) {
        cachedLanguage = language
        val preferences = cachedPrefs ?: PreferenceManager.getDefaultSharedPreferences(context).also { cachedPrefs = it }
        preferences.edit { putString(SELECTED_LANGUAGE, language) }
    }

    private fun persistTextScale(context: Context, textScale: Float) {
        cachedTextScale = textScale
        val preferences = cachedPrefs ?: PreferenceManager.getDefaultSharedPreferences(context).also { cachedPrefs = it }
        preferences.edit { putFloat(SELECTED_TEXT_SCALE, textScale) }
    }
}
