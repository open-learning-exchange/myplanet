package org.ole.planet.myplanet.utils

import android.content.SharedPreferences

/** [KeyValueStore] over one [SharedPreferences] file; every [edit] is a single `apply()`. */
class AndroidKeyValueStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Int = preferences.getInt(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Long = preferences.getLong(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Float = preferences.getFloat(key, defaultValue)

    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun keys(): Set<String> = preferences.all.keys.toSet()

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        val editor = preferences.edit()
        EditorAdapter(editor).block()
        editor.apply()
    }

    private class EditorAdapter(private val editor: SharedPreferences.Editor) : KeyValueStore.Editor {
        override fun putString(key: String, value: String?) {
            editor.putString(key, value)
        }

        override fun putBoolean(key: String, value: Boolean) {
            editor.putBoolean(key, value)
        }

        override fun putInt(key: String, value: Int) {
            editor.putInt(key, value)
        }

        override fun putLong(key: String, value: Long) {
            editor.putLong(key, value)
        }

        override fun putFloat(key: String, value: Float) {
            editor.putFloat(key, value)
        }

        override fun remove(key: String) {
            editor.remove(key)
        }

        override fun clear() {
            editor.clear()
        }
    }
}
