package org.ole.planet.myplanet.utils

/**
 * A named key-value settings file, so shared code reads and writes preferences without the
 * platform's preferences API. Reads return [defaultValue] when the key is absent. Writes are
 * applied in memory at once and persisted asynchronously. The Android implementation is
 * [AndroidKeyValueStore].
 */
interface KeyValueStore {
    fun getString(key: String, defaultValue: String?): String?

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun getInt(key: String, defaultValue: Int): Int

    fun getLong(key: String, defaultValue: Long): Long

    fun getFloat(key: String, defaultValue: Float): Float

    fun contains(key: String): Boolean

    /** Every key currently stored. */
    fun keys(): Set<String>

    /** Applies every change made in [block] as a single write. */
    fun edit(block: Editor.() -> Unit)

    fun putString(key: String, value: String?) = edit { putString(key, value) }

    fun putBoolean(key: String, value: Boolean) = edit { putBoolean(key, value) }

    fun putInt(key: String, value: Int) = edit { putInt(key, value) }

    fun putLong(key: String, value: Long) = edit { putLong(key, value) }

    fun putFloat(key: String, value: Float) = edit { putFloat(key, value) }

    fun remove(key: String) = edit { remove(key) }

    /**
     * A batch of changes. As with the platform editor, [clear] runs before the batch's puts
     * and removes whatever order they were made in, and a null string removes its key.
     */
    interface Editor {
        fun putString(key: String, value: String?)

        fun putBoolean(key: String, value: Boolean)

        fun putInt(key: String, value: Int)

        fun putLong(key: String, value: Long)

        fun putFloat(key: String, value: Float)

        fun remove(key: String)

        fun clear()
    }
}
