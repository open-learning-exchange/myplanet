package org.ole.planet.myplanet.model

import androidx.core.net.toUri
import java.io.File
import java.io.InputStream
import org.ole.planet.myplanet.MainApplication.Companion.context

// App-side java.io / ContentResolver helpers for UserEntity, kept out of the Room entity.

fun UserEntity.encodeImageToBase64(imagePath: String?): String? {
    if (imagePath.isNullOrEmpty()) return null
    if (imagePath.startsWith("http://", ignoreCase = true) || imagePath.startsWith("https://", ignoreCase = true)) {
        return null
    }
    return try {
        val inputStream: InputStream? = if (imagePath.startsWith("content://")) {
            val uri = imagePath.toUri()
            context.contentResolver.openInputStream(uri)
        } else {
            val file = File(imagePath)
            if (!file.isFile) return null
            file.inputStream()
        }

        inputStream?.use {
            val bytes = it.readBytes()
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
