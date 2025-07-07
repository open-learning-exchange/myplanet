package org.ole.planet.myplanet.utilities.extensions

import android.content.Context
import android.content.SharedPreferences
import java.math.BigInteger
import android.content.Intent
import org.ole.planet.myplanet.ui.viewer.AudioPlayerActivity

fun SharedPreferences.baseUrl(): String {
    var url = if (getBoolean("isAlternativeUrl", false)) {
        getString("processedAlternativeUrl", "")
    } else {
        getString("couchdbURL", "")
    }
    if (url != null && url.endsWith("/db")) {
        url = url.removeSuffix("/db")
    }
    return url ?: ""
}

fun SharedPreferences.dbUrl(): String {
    val base = baseUrl()
    return if (base.endsWith("/db")) base else "$base/db"
}

fun SharedPreferences.userName(): String = getString("name", "") ?: ""

fun String.baseUrl(): String = if (endsWith("/db")) removeSuffix("/db") else this

fun String.dbUrl(): String = if (endsWith("/db")) this else "$this/db"

fun String?.orNA(): String = if (this.isNullOrEmpty()) "N/A" else this

fun String.toHex(): String = String.format("%x", BigInteger(1, toByteArray()))

fun Long.relativeTime(): String {
    val timeNow = System.currentTimeMillis()
    return if (this < timeNow) {
        android.text.format.DateUtils.getRelativeTimeSpanString(this, timeNow, 0).toString()
    } else "Just now"
}

fun Context.openAudioFile(path: String?) {
    val intent = Intent(this, AudioPlayerActivity::class.java)
    intent.putExtra("isFullPath", true)
    intent.putExtra("TOUCHED_FILE", path)
    startActivity(intent)
}
