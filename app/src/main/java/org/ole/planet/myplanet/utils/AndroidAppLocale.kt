package org.ole.planet.myplanet.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppLocale @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppLocale {
    override fun setLanguage(languageCode: String) {
        LocaleUtils.setLocale(context, languageCode)
    }
}
