package org.ole.planet.myplanet.utils

/**
 * Injectable access to localized string resources, so data-layer code can build user-facing
 * messages without holding a platform context. The Android implementation is
 * [AndroidStringProvider].
 */
interface StringProvider {
    fun getString(resId: Int): String

    fun getString(resId: Int, vararg formatArgs: Any): String

    fun getQuantityString(resId: Int, quantity: Int, vararg formatArgs: Any): String
}
