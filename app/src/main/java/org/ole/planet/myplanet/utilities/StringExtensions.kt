package org.ole.planet.myplanet.utilities

import android.util.Patterns
import java.math.BigInteger

fun CharSequence?.isValidEmail(): Boolean {
    return !this.isNullOrEmpty() && Patterns.EMAIL_ADDRESS.matcher(this).matches()
}

fun String?.checkNA(): String {
    return if (this.isNullOrEmpty()) "N/A" else this
}

fun String?.toHex(): String {
    return this?.toByteArray()?.let { String.format("%x", BigInteger(1, it)) } ?: ""
}
