package org.ole.planet.myplanet.datamanager

import com.google.gson.JsonObject
import okhttp3.MediaType
import okhttp3.RequestBody
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException

open class FileUploadService {
    fun uploadAttachment(id: String, rev: String, personal: RealmMyPersonal, listener: SuccessListener) {
        val f = personal.path?.let { File(it) }
        val name = FileUtils.getFileNameFromUrl(personal.path)
        if (f != null) {
            uploadDoc(id, rev, "%s/resources/%s/%s", f, name, listener)
        }
    }

    fun uploadAttachment(id: String, rev: String, personal: RealmMyLibrary, listener: SuccessListener) {
        val f = personal.resourceLocalAddress?.let { File(it) }
        val name = FileUtils.getFileNameFromUrl(personal.resourceLocalAddress)
        if (f != null) {
            uploadDoc(id, rev, "%s/resources/%s/%s", f, name, listener)
        }
    }

    fun uploadAttachment(id: String, rev: String, personal: RealmSubmitPhotos, listener: SuccessListener) {
        val f = personal.photoLocation?.let { File(it) }
        val name = FileUtils.getFileNameFromUrl(personal.photoLocation)
        if (f != null) {
            uploadDoc(id, rev, "%s/submissions/%s/%s", f, name, listener)
        }
    }

    private fun uploadDoc(id: String, rev: String, format: String, f: File, name: String, listener: SuccessListener) {
        val apiInterface = ApiClient.client?.create(ApiInterface::class.java)
        try {
            val connection = f.toURI().toURL().openConnection()
            val mimeType = connection.contentType
            val body = RequestBody.create(MediaType.parse("application/octet"), FileUtils.fullyReadFileToBytes(f))
            val url = String.format(format, Utilities.getUrl(), id, name)
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

    private fun onDataReceived(`object`: JsonObject?, listener: SuccessListener) {
        if (`object` != null) {
            if (JsonUtils.getBoolean("ok", `object`)) {
                listener.onSuccess("Uploaded successfully")
                return
            }
        }
        listener.onSuccess("Unable to upload resource")
    }

    companion object {
        fun getHeaderMap(mimeType: String, rev: String): Map<String, String> {
            val hashMap: MutableMap<String, String> = HashMap()
            hashMap["Authorization"] = Utilities.header
            hashMap["Content-Type"] = mimeType
            hashMap["If-Match"] = rev
            return hashMap
        }
    }
}
