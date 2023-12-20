package org.ole.planet.myplanet.callback

import com.google.gson.JsonObject

interface UploadAttachmentListener {
    fun onUploaded(`object`: JsonObject?)
    fun onError(message: String?)
}
