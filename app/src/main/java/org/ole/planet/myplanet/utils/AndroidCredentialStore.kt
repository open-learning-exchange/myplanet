package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.di.AppPreferences

/**
 * [CredentialStore] backed by [SecurePrefs]; [preferences] is the app settings file SecurePrefs
 * migrates legacy plain-text credentials out of.
 */
@Singleton
class AndroidCredentialStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:AppPreferences private val preferences: SharedPreferences
) : CredentialStore {
    override fun getUserName(): String? = SecurePrefs.getUserName(context, preferences)

    override fun getPassword(): String? = SecurePrefs.getPassword(context, preferences)

    override fun saveCredentials(userName: String?, password: String?) {
        SecurePrefs.saveCredentials(context, preferences, userName, password)
    }

    override fun encryptString(text: String): String = SecurePrefs.encryptString(context, text)

    override fun decryptString(encryptedText: String): String? = SecurePrefs.decryptString(context, encryptedText)
}
