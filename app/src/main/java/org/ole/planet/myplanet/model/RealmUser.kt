package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import org.apache.commons.lang3.StringUtils
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.VersionUtils

@RealmClass(name = "RealmUserModel")
open class RealmUser : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    var rolesList: RealmList<String?>? = null
    var userAdmin: Boolean? = null
    var joinDate: Long = 0
    var firstName: String? = null
    var lastName: String? = null
    var middleName: String? = null
    var email: String? = null
    var planetCode: String? = null
    var parentCode: String? = null
    var phoneNumber: String? = null
    var password_scheme: String? = null
    var iterations: String? = null
    var derived_key: String? = null
    var level: String? = null
    var language: String? = null
    var gender: String? = null
    var salt: String? = null
    var dob: String? = null
    var age: String? = null
    var birthPlace: String? = null
    var userImage: String? = null
    var key: String? = null
    var iv: String? = null
    var password: String? = null
    var isUpdated = false
    var isShowTopbar = false
    var isArchived = false

    fun serialize(): JsonObject {
        val jsonObject = JsonObject()
        if (_id?.isNotEmpty() == true) {
            jsonObject.addProperty("_id", _id)
            jsonObject.addProperty("_rev", _rev)
        }
        jsonObject.addProperty("name", name)
        jsonObject.add("roles", getRoles())
        if (_id?.isEmpty() == true) {
            jsonObject.addProperty("password", password)
            jsonObject.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            jsonObject.addProperty("uniqueAndroidId", VersionUtils.getAndroidId(context))
            jsonObject.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
        } else {
            jsonObject.addProperty("derived_key", derived_key)
            jsonObject.addProperty("salt", salt)
            jsonObject.addProperty("password_scheme", password_scheme)
        }
        jsonObject.addProperty("isUserAdmin", userAdmin)
        jsonObject.addProperty("joinDate", joinDate)
        jsonObject.addProperty("firstName", firstName)
        jsonObject.addProperty("lastName", lastName)
        jsonObject.addProperty("middleName", middleName)
        jsonObject.addProperty("email", email)
        jsonObject.addProperty("language", language)
        jsonObject.addProperty("level", level)
        jsonObject.addProperty("type", "user")
        jsonObject.addProperty("gender", gender)
        jsonObject.addProperty("phoneNumber", phoneNumber)
        jsonObject.addProperty("birthDate", dob)
        jsonObject.addProperty("age", age)
        try {
            jsonObject.addProperty("iterations", iterations?.takeIf { it.isNotBlank() }?.toInt() ?: 10)
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            jsonObject.addProperty("iterations", 10)
        }
        jsonObject.addProperty("parentCode", parentCode)
        jsonObject.addProperty("planetCode", planetCode)
        jsonObject.addProperty("birthPlace", birthPlace)
        jsonObject.addProperty("isArchived", isArchived)

        val base64Image = encodeImageToBase64(userImage)

        if (!base64Image.isNullOrEmpty()) {
            val attachmentObject = JsonObject()
            val imageData = JsonObject()
            imageData.addProperty("content_type", "image/jpeg")
            imageData.addProperty("data", base64Image)

            attachmentObject.add("img", imageData)
            jsonObject.add("_attachments", attachmentObject)
        }

        return jsonObject
    }

    fun encodeImageToBase64(imagePath: String?): String? {
        if (imagePath.isNullOrEmpty()) return null
        return try {
            val inputStream: InputStream? = if (imagePath.startsWith("content://")) {
                val uri = imagePath.toUri()
                context.contentResolver.openInputStream(uri)
            } else {
                File(imagePath).inputStream()
            }

            inputStream?.use {
                val bytes = it.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getRoles(): JsonArray {
        val ar = JsonArray()
        for (s in rolesList ?: emptyList())    {
            ar.add(s)
        }
        return ar
    }

    fun setRoles(roles: RealmList<String?>?) {
        rolesList = roles
    }

    fun getRoleAsString(): String {
        return if (rolesList != null) {
            StringUtils.join(rolesList, ",")
        } else {
            ""
        }
    }

    fun getFullName(): String {
        return "$firstName $lastName"
    }

    fun getFullNameWithMiddleName(): String {
        return "$firstName ${middleName ?: ""} $lastName"
    }

    fun addImageUrl(jsonDoc: JsonObject?) {
        if (jsonDoc?.has("_attachments") == true) {
            val element = JsonParser.parseString(jsonDoc["_attachments"].asJsonObject.toString())
            val obj = element.asJsonObject
            val entries = obj.entrySet()
            for ((key1) in entries) {
                userImage = UrlUtils.getUserImageUrl(id, key1)
                break
            }
        }
    }

    fun isManager(): Boolean {
        val roles = getRoles()
        val isManager = roles.toString().lowercase(Locale.ROOT).contains("manager") || userAdmin ?: false
        return isManager
    }

    fun isLeader(): Boolean {
        val roles = getRoles()
        return roles.toString().lowercase(Locale.ROOT).contains("leader")
    }

    fun isGuest(): Boolean {
        val hasGuestId = _id?.startsWith("guest_") == true
        val hasGuestRole = rolesList?.any { it?.lowercase() == "guest" } == true
        return hasGuestId || (hasGuestRole && rolesList?.any { it?.lowercase() == "learner" } != true)
    }

    override fun toString(): String {
        return "$name"
    }

    companion object {
    }
}
