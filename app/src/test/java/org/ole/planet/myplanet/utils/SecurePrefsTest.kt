package org.ole.planet.myplanet.utils

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class SecurePrefsTest {

    private lateinit var context: Context
    private val plainPrefsFileName = "mock_plain_store"
    private val encryptedPrefsFileName = "mock_encrypted_store"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ShadowLog.reset()
    }

    @Test
    fun testMigrationOnlyMigratesSensitiveKeys() {
        val plainPrefs = context.getSharedPreferences(plainPrefsFileName, Context.MODE_PRIVATE)
        val encryptedPrefs = context.getSharedPreferences(encryptedPrefsFileName, Context.MODE_PRIVATE)

        plainPrefs.edit()
            .putString("loginUserName", "testUser")
            .putString("loginUserPassword", "testPass")
            .putString("nonSensitive", "nonSensitiveValue")
            .commit()

        SecurePrefs.performMigration(plainPrefs, encryptedPrefs)

        assertEquals("testUser", encryptedPrefs.getString("loginUserName", null))
        assertEquals("testPass", encryptedPrefs.getString("loginUserPassword", null))
        assertNull(encryptedPrefs.getString("nonSensitive", null))
    }

    @Test
    fun decryptStringReturnsNullAndLogsTaggedErrorOnGarbageInput() {
        val result = SecurePrefs.decryptString(context, "!!!not-valid-base64-or-ciphertext!!!")

        assertNull(result)
        val logged = ShadowLog.getLogsForTag("SecurePrefs")
        assertTrue(logged.any { it.type == Log.ERROR && it.msg!!.contains("decrypt") })
    }

    @Test
    fun testWarmUpSurvivesUnavailableSecureStorage() {
        clearCachedPrimitives()
        SecurePrefs.warmUp(context)
    }

    private fun clearCachedPrimitives() {
        listOf("cachedAead", "cachedSecureStore").forEach { name ->
            SecurePrefs::class.java.getDeclaredField(name).apply {
                isAccessible = true
                set(SecurePrefs, null)
            }
        }
    }
}
