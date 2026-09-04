package org.ole.planet.myplanet.model

import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.InputStream
import org.apache.commons.lang3.StringUtils
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin

@Entity(tableName = "users", indices = [Index("_id"), Index("name"), Index("planetCode")])
open class UserEntity(
    @PrimaryKey @JvmField var id: String = "",
    @JvmField var _id: String? = null,
    @JvmField var _rev: String? = null,
    var name: String? = null,
    var rolesList: List<String>? = null,
    var userAdmin: Boolean? = null,
    var joinDate: Long = 0,
    var firstName: String? = null,
    var lastName: String? = null,
    var middleName: String? = null,
    var email: String? = null,
    var planetCode: String? = null,
    var parentCode: String? = null,
    var phoneNumber: String? = null,
    var password_scheme: String? = null,
    var iterations: String? = null,
    var derived_key: String? = null,
    var level: String? = null,
    var language: String? = null,
    var gender: String? = null,
    var salt: String? = null,
    var dob: String? = null,
    var age: String? = null,
    var birthPlace: String? = null,
    var userImage: String? = null,
    var key: String? = null,
    var iv: String? = null,
    var password: String? = null,
    var isUpdated: Boolean = false,
    var isShowTopbar: Boolean = false,
    var isArchived: Boolean = false
) {
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
            jsonObject.addDocumentOrigin()
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
                java.util.Base64.getEncoder().encodeToString(bytes)
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

    fun setRoles(roles: List<String>?) {
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
            val obj = jsonDoc["_attachments"].asJsonObject
            val key1 = obj.entrySet().firstOrNull()?.key
            if (key1 != null) {
                userImage = UrlUtils.getUserImageUrl(id, key1)
            }
        }
    }

    fun isManager(): Boolean {
        val hasManagerRole = rolesList?.any { it.equals("manager", ignoreCase = true) } == true
        return hasManagerRole || userAdmin ?: false
    }

    fun isLeader(): Boolean {
        return rolesList?.any { it.equals("leader", ignoreCase = true) } == true
    }

    fun isGuest(): Boolean {
        val hasGuestId = _id?.startsWith("guest_") == true
        val hasGuestRole = rolesList?.any { it.equals("guest", ignoreCase = true) } == true
        return hasGuestId || (hasGuestRole && rolesList?.any { it.equals("learner", ignoreCase = true) } != true)
    }

    override fun toString(): String {
        return "$name"
    }

    companion object {
        fun parseLeadersJson(jsonString: String): List<UserEntity> {
            val leadersList = mutableListOf<UserEntity>()
            try {
                val jsonObject = JSONObject(jsonString)
                val docsArray = jsonObject.getJSONArray("docs")
                for (i in 0 until docsArray.length()) {
                    val docObject = docsArray.getJSONObject(i)
                    val user = UserEntity()
                    user.name = docObject.getString("name")
                    user.id = if (!docObject.isNull("_id")) {
                        docObject.getString("_id")
                    } else {
                        "org.couchdb.user:${user.name}"
                    }
                    user.rolesList = mutableListOf()
                    if (!docObject.isNull("firstName")) {
                        user.firstName = docObject.getString("firstName")
                    }
                    if (!docObject.isNull("lastName")) {
                        user.lastName = docObject.getString("lastName")
                    }
                    if (!docObject.isNull("email")) {
                        user.email = docObject.getString("email")
                    }
                    leadersList.add(user)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return leadersList
        }
    }
}

val UserEntity.effectiveId: String? get() = _id?.takeIf { it.isNotEmpty() } ?: id
