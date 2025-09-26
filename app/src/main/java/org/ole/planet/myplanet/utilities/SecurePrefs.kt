package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val FILE_NAME = "secure_prefs"

    private fun prefs(context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            context.deleteSharedPreferences(FILE_NAME)
            createEncryptedPrefs(context)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveCredentials(
        context: Context,
        plainPrefs: SharedPreferences,
        username: String?,
        password: String?
    ) {
        val enc = prefs(context)
        enc.edit {
            putString("loginUserName", username)
            putString("loginUserPassword", password)
        }
        plainPrefs.edit {
            remove("loginUserName")
            remove("loginUserPassword")
        }
    }

    fun getUserName(context: Context, plainPrefs: SharedPreferences): String? {
        val enc = prefs(context)
        var name = enc.getString("loginUserName", null)
        if (name.isNullOrEmpty()) {
            name = plainPrefs.getString("loginUserName", null)
            if (!name.isNullOrEmpty()) {
                enc.edit { putString("loginUserName", name) }
                plainPrefs.edit { remove("loginUserName") }
            }
        }
        return name
    }

    fun getPassword(context: Context, plainPrefs: SharedPreferences): String? {
        val enc = prefs(context)
        var pwd = enc.getString("loginUserPassword", null)
        if (pwd.isNullOrEmpty()) {
            pwd = plainPrefs.getString("loginUserPassword", null)
            if (!pwd.isNullOrEmpty()) {
                enc.edit { putString("loginUserPassword", pwd) }
                plainPrefs.edit { remove("loginUserPassword") }
            }
        }
        return pwd
    }

    fun clearCredentials(context: Context) {
        val enc = prefs(context)
        enc.edit {
            remove("loginUserName")
            remove("loginUserPassword")
        }
    }
}
