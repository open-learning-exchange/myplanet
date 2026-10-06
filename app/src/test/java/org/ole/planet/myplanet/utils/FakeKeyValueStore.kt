package org.ole.planet.myplanet.utils

import com.google.gson.Gson
import org.ole.planet.myplanet.services.SharedPrefManager

/**
 * In-memory [KeyValueStore] for tests. Like the platform editor, an [edit] batch applies its
 * `clear()` first and a null string removes its key. [editCount] counts batches, so a test can
 * assert that related keys were written together.
 */
class FakeKeyValueStore(initial: Map<String, Any> = emptyMap()) : KeyValueStore {
    val values: MutableMap<String, Any> = initial.toMutableMap()

    var editCount: Int = 0
        private set

    override fun getString(key: String, defaultValue: String?): String? = values[key] as String? ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as Boolean? ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int = values[key] as Int? ?: defaultValue

    override fun getLong(key: String, defaultValue: Long): Long = values[key] as Long? ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float = values[key] as Float? ?: defaultValue

    override fun contains(key: String): Boolean = key in values

    override fun keys(): Set<String> = values.keys.toSet()

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        val editor = FakeEditor().apply(block)
        editCount++
        if (editor.clearRequested) values.clear()
        editor.changes.forEach { (key, value) ->
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    private class FakeEditor : KeyValueStore.Editor {
        val changes = LinkedHashMap<String, Any?>()
        var clearRequested = false

        override fun putString(key: String, value: String?) {
            changes[key] = value
        }

        override fun putBoolean(key: String, value: Boolean) {
            changes[key] = value
        }

        override fun putInt(key: String, value: Int) {
            changes[key] = value
        }

        override fun putLong(key: String, value: Long) {
            changes[key] = value
        }

        override fun putFloat(key: String, value: Float) {
            changes[key] = value
        }

        override fun remove(key: String) {
            changes[key] = null
        }

        override fun clear() {
            clearRequested = true
        }
    }
}

/** In-memory [CredentialStore]; "encryption" prefixes the text with `enc:` so tests can see it. */
class FakeCredentialStore(
    var savedUserName: String? = null,
    var savedPassword: String? = null
) : CredentialStore {
    override fun getUserName(): String? = savedUserName

    override fun getPassword(): String? = savedPassword

    override fun saveCredentials(userName: String?, password: String?) {
        savedUserName = userName
        savedPassword = password
    }

    override fun encryptString(text: String): String = "enc:$text"

    override fun decryptString(encryptedText: String): String? = encryptedText.removePrefix("enc:")
}

/** A real [SharedPrefManager] over in-memory stores, for tests that want actual read-back. */
fun fakeSharedPrefManager(
    store: FakeKeyValueStore = FakeKeyValueStore(),
    defaultStore: FakeKeyValueStore = FakeKeyValueStore(),
    credentialStore: CredentialStore = FakeCredentialStore(),
    gson: Gson = Gson()
): SharedPrefManager = SharedPrefManager(store, defaultStore, credentialStore, gson)
