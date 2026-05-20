package org.ole.planet.myplanet.services

import com.google.gson.JsonObject
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.UrlUtils

data class UploadDocParams(
    val id: String,
    val rev: String,
    val format: String,
    val f: File,
    val name: String,
    val listener: OnSuccessListener
)

open class FileUploader(
    private val apiInterface: ApiInterface,
    private val scope: CoroutineScope
) {
    fun uploadAttachment(id: String, rev: String, personal: RealmMyPersonal, listener: OnSuccessListener) {
        val f = personal.path?.let { File(it) }
        val name = FileUtils.getFileNameFromUrl(personal.path)
        if (f != null) {
            uploadDoc(UploadDocParams(id, rev, "%s/resources/%s/%s", f, name, listener))
        }
    }

    fun uploadAttachment(id: String, rev: String, personal: RealmMyLibrary, listener: OnSuccessListener) {
        val f = personal.resourceLocalAddress?.let { File(it) }
        val name = FileUtils.getFileNameFromLocalAddress(personal.resourceLocalAddress)
        if (f != null) {
            uploadDoc(UploadDocParams(id, rev, "%s/resources/%s/%s", f, name, listener))
        }
    }

    fun uploadAttachment(id: String, rev: String, personal: RealmSubmitPhotos, listener: OnSuccessListener) {
        val f = personal.photoLocation?.let { File(it) }
        val name = FileUtils.getFileNameFromUrl(personal.photoLocation)
        if (f != null) {
            uploadDoc(UploadDocParams(id, rev, "%s/submissions/%s/%s", f, name, listener))
        }
    }

    private fun uploadDoc(params: UploadDocParams) {
        scope.launch {
            try {
                val connection = params.f.toURI().toURL().openConnection()
                val mimeType = connection.contentType
                val body = FileUtils.fullyReadFileToBytes(params.f)
                    .toRequestBody("application/octet-stream".toMediaTypeOrNull())
                val url = String.format(params.format, UrlUtils.getUrl(), params.id, params.name)

                try {
                    val response = apiInterface.uploadResource(getHeaderMap(mimeType, params.rev), url, body)
                    onDataReceived(response.body(), params.listener)
                } catch (t: Exception) {
                    params.listener.onSuccess("Unable to upload resource")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                params.listener.onSuccess("Unable to upload resource")
            }
        }
    }

    private fun onDataReceived(`object`: JsonObject?, listener: OnSuccessListener) {
        if (`object` != null) {
            if (JsonUtils.getBoolean("ok", `object`)) {
                listener.onSuccess("Uploaded successfully")
                return
            }
        }
        listener.onSuccess("Unable to upload resource")
    }

    companion object {
        @JvmStatic
        fun getHeaderMap(mimeType: String, rev: String): Map<String, String> {
            val hashMap: MutableMap<String, String> = HashMap()
            hashMap["Authorization"] = UrlUtils.header
            hashMap["Content-Type"] = mimeType
            hashMap["If-Match"] = rev
            return hashMap
        }
    }
}
