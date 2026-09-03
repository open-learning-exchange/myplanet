package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1], application = Application::class)
class SecurePrefsTest {

    private lateinit var context: Context
    private val plainPrefsFileName = "mock_plain_store"
    private val encryptedPrefsFileName = "mock_encrypted_store"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
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

        // Explicitly call the actual production migration logic
        SecurePrefs.performMigration(plainPrefs, encryptedPrefs)

        // Verify EncryptedPrefs got the sensitive data
        assertEquals("testUser", encryptedPrefs.getString("loginUserName", null))
        assertEquals("testPass", encryptedPrefs.getString("loginUserPassword", null))
        assertNull(encryptedPrefs.getString("nonSensitive", null))
    }
}
