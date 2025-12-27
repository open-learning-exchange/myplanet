package org.ole.planet.myplanet.utilities

import java.math.BigInteger

fun String?.checkNA(): String {
    return if (this.isNullOrEmpty()) "N/A" else this
}

fun String?.toHex(): String {
    return this?.toByteArray()?.let { String.format("%x", BigInteger(1, it)) } ?: ""
}
