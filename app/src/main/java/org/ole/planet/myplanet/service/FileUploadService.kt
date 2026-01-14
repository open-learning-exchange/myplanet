package org.ole.planet.myplanet.service

import com.google.gson.JsonObject
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.ApiClient
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

open class FileUploadService {
    fun uploadAttachment(id: String, rev: String, personal: RealmMyPersonal, listener: OnSuccessListener) {
        val f = personal.path?.let { File(it) }
        val name = FileUtils.getFileNameFromUrl(personal.path)
        if (f != null) {
            uploadDoc(id, rev, "%s/resources/%s/%s", f, name, listener)
        }
    }

    fun uploadAttachment(id: String, rev: String, personal: RealmMyLibrary, listener: OnSuccessListener) {
        val f = personal.resourceLocalAddress?.let { File(it) }
        val name = FileUtils.getFileNameFromLocalAddress(personal.resourceLocalAddress)
        if (f != null) {
            uploadDoc(id, rev, "%s/resources/%s/%s", f, name, listener)
        }
    }

    fun uploadAttachment(id: String, rev: String, personal: RealmSubmitPhotos, listener: OnSuccessListener) {
        val f = personal.photoLocation?.let { File(it) }
        val name = FileUtils.getFileNameFromUrl(personal.photoLocation)
        if (f != null) {
            uploadDoc(id, rev, "%s/submissions/%s/%s", f, name, listener)
        }
    }

    private fun uploadDoc(id: String, rev: String, format: String, f: File, name: String, listener: OnSuccessListener) {
        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        try {
            val connection = f.toURI().toURL().openConnection()
            val mimeType = connection.contentType
            val body = FileUtils.fullyReadFileToBytes(f)
                .toRequestBody("application/octet-stream".toMediaTypeOrNull())
            val url = String.format(format, UrlUtils.getUrl(), id, name)
            apiInterface?.uploadResource(getHeaderMap(mimeType, rev), url, body)?.enqueue(object : Callback<JsonObject?> {
                override fun onResponse(call: Call<JsonObject?>, response: Response<JsonObject?>) {
                    onDataReceived(response.body(), listener)
                }

                override fun onFailure(call: Call<JsonObject?>, t: Throwable) {
                    listener.onSuccess("Unable to upload resource")
                }
            })
        } catch (e: IOException) {
            e.printStackTrace()
            listener.onSuccess("Unable to upload resource")
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
