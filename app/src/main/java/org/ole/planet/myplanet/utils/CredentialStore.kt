package org.ole.planet.myplanet.utils

/**
 * The signed-in user's saved login and the app's string cipher, so shared code doesn't reach
 * for the platform keystore. The Android implementation is [AndroidCredentialStore].
 */
interface CredentialStore {
    /** The saved login name, or null when none is stored. */
    fun getUserName(): String?

    /** The saved login password, or null when none is stored. */
    fun getPassword(): String?

    /** Saves the login; a null value removes that half of it. */
    fun saveCredentials(userName: String?, password: String?)

    fun encryptString(text: String): String

    /** The plain text of [encryptedText], or null when it can't be decrypted. */
    fun decryptString(encryptedText: String): String?
}
