package org.ole.planet.myplanet.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.GeneralSecurityException

object SecurePrefs {
    private const val ENCRYPTED_PREFS_FILE_NAME = "secure_store_v2"
    private const val PLAIN_PREFS_FILE_NAME = "secure_store"
    private const val LEGACY_FILE_NAME = "secure_prefs"
    private const val KEYSET_NAME = "master_keyset"
    private const val PREF_FILE_NAME = "master_key_preference"
    private const val MASTER_KEY_URI = "android-keystore://master_key"

    @Volatile private var cachedAead: Aead? = null
    @Volatile private var cachedSecureStore: SharedPreferences? = null

    init {
        try {
            AeadConfig.register()
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    // Tink 1.20.0 deprecates getPrimitive(Class), but Registry.getPrimitiveWrapper seems unavailable in this environment.
    private fun buildAead(context: Context): Aead {
        return AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(Aead::class.java)
    }

    private fun getAead(context: Context): Aead {
        return cachedAead ?: synchronized(this) {
            cachedAead ?: buildAead(context.applicationContext).also { cachedAead = it }
        }
    }
    
    fun warmUp(context: Context) {
        if (cachedAead == null) getAead(context)
        if (cachedSecureStore == null) getSecureStore(context)
    }

    @Suppress("DEPRECATION")
    private fun buildSecureStore(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val plainPrefs = context.getSharedPreferences(PLAIN_PREFS_FILE_NAME, Context.MODE_PRIVATE)
            if (plainPrefs.all.isNotEmpty()) {
                encryptedPrefs.edit(commit = true) {
                    plainPrefs.all.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Set<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                putStringSet(key, value as Set<String>)
                            }
                        }
                    }
                }
                plainPrefs.edit(commit = true) { clear() }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    context.deleteSharedPreferences(PLAIN_PREFS_FILE_NAME)
                }
            }
            encryptedPrefs
        } catch (e: Exception) {
            android.util.Log.w("SecurePrefs", "Failed to create EncryptedSharedPreferences, falling back to plain text", e)
            context.getSharedPreferences(PLAIN_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getSecureStore(context: Context): SharedPreferences {
        return cachedSecureStore ?: synchronized(this) {
            cachedSecureStore ?: buildSecureStore(context.applicationContext).also { cachedSecureStore = it }
        }
    }

    @Suppress("DEPRECATION")
    private fun getLegacyEncryptedPrefs(context: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                LEGACY_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // If creation fails, maybe file is corrupted or key is lost.
            null
        }
    }

    fun encryptString(context: Context, text: String): String {
        val aead = getAead(context)
        return encrypt(aead, text)
    }

    fun decryptString(context: Context, encryptedText: String): String? {
        val aead = getAead(context)
        return decrypt(aead, encryptedText)
    }

    private fun encrypt(aead: Aead, text: String): String {
        val bytes = aead.encrypt(text.toByteArray(Charsets.UTF_8), null)
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun decrypt(aead: Aead, encryptedText: String): String? {
        return try {
            val bytes = Base64.decode(encryptedText, Base64.DEFAULT)
            val decrypted = aead.decrypt(bytes, null)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveCredentials(
        context: Context,
        plainPrefs: SharedPreferences,
        username: String?,
        password: String?
    ) {
        try {
            val aead = getAead(context)
            val store = getSecureStore(context)
            store.edit {
                if (username != null) putString("loginUserName", encrypt(aead, username))
                else remove("loginUserName")

                if (password != null) putString("loginUserPassword", encrypt(aead, password))
                else remove("loginUserPassword")
            }
            // Clear legacy if it exists
             getLegacyEncryptedPrefs(context)?.edit {
                remove("loginUserName")
                remove("loginUserPassword")
             }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        plainPrefs.edit {
            remove("loginUserName")
            remove("loginUserPassword")
        }
    }

    fun getUserName(context: Context, plainPrefs: SharedPreferences): String? {
        var name: String? = null
        try {
            val aead = getAead(context)
            val store = getSecureStore(context)
            val encrypted = store.getString("loginUserName", null)

            if (encrypted != null) {
                name = decrypt(aead, encrypted)
            }

            if (name == null) {
                val legacy = getLegacyEncryptedPrefs(context)
                name = legacy?.getString("loginUserName", null)
                if (!name.isNullOrEmpty()) {
                    store.edit { putString("loginUserName", encrypt(aead, name)) }
                    legacy?.edit { remove("loginUserName") }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (name.isNullOrEmpty()) {
            name = plainPrefs.getString("loginUserName", null)
            if (!name.isNullOrEmpty()) {
                try {
                     val aead = getAead(context)
                     val store = getSecureStore(context)
                     store.edit { putString("loginUserName", encrypt(aead, name)) }
                     plainPrefs.edit { remove("loginUserName") }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return name
    }

    fun getPassword(context: Context, plainPrefs: SharedPreferences): String? {
        var pwd: String? = null
        try {
            val aead = getAead(context)
            val store = getSecureStore(context)
            val encrypted = store.getString("loginUserPassword", null)

            if (encrypted != null) {
                pwd = decrypt(aead, encrypted)
            }

            if (pwd == null) {
                val legacy = getLegacyEncryptedPrefs(context)
                pwd = legacy?.getString("loginUserPassword", null)
                if (!pwd.isNullOrEmpty()) {
                    store.edit { putString("loginUserPassword", encrypt(aead, pwd)) }
                    legacy?.edit { remove("loginUserPassword") }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (pwd.isNullOrEmpty()) {
            pwd = plainPrefs.getString("loginUserPassword", null)
            if (!pwd.isNullOrEmpty()) {
                try {
                     val aead = getAead(context)
                     val store = getSecureStore(context)
                     store.edit { putString("loginUserPassword", encrypt(aead, pwd)) }
                     plainPrefs.edit { remove("loginUserPassword") }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return pwd
    }

    fun clearCredentials(context: Context) {
        try {
            val store = getSecureStore(context)
            store.edit {
                remove("loginUserName")
                remove("loginUserPassword")
            }
             getLegacyEncryptedPrefs(context)?.edit {
                remove("loginUserName")
                remove("loginUserPassword")
            }
        } catch (e: Exception) {
             e.printStackTrace()
        }
    }
}
