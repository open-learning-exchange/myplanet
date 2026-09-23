package org.ole.planet.myplanet.model

import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.io.InputStream
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.commons.lang3.StringUtils
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utils.DOCUMENT_ORIGIN
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

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
        val iterationsValue = try {
            iterations?.takeIf { it.isNotBlank() }?.toInt() ?: 10
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            10
        }
        val base64Image = encodeImageToBase64(userImage)

        return buildJsonObject {
            if (_id?.isNotEmpty() == true) {
                put("_id", _id)
                put("_rev", _rev)
            }
            put("name", name)
            put("roles", getRoles().toKotlinx())
            if (_id?.isEmpty() == true) {
                put("password", password)
                put("androidId", NetworkUtils.getUniqueIdentifier())
                put("app", DOCUMENT_ORIGIN)
                put("uniqueAndroidId", VersionUtils.getAndroidId(context))
                put("customDeviceName", NetworkUtils.getCustomDeviceName(context))
            } else {
                put("derived_key", derived_key)
                put("salt", salt)
                put("password_scheme", password_scheme)
            }
            put("isUserAdmin", userAdmin)
            put("joinDate", joinDate)
            put("firstName", firstName)
            put("lastName", lastName)
            put("middleName", middleName)
            put("email", email)
            put("language", language)
            put("level", level)
            put("type", "user")
            put("gender", gender)
            put("phoneNumber", phoneNumber)
            put("birthDate", dob)
            put("age", age)
            put("iterations", iterationsValue)
            put("parentCode", parentCode)
            put("planetCode", planetCode)
            put("birthPlace", birthPlace)
            put("isArchived", isArchived)

            if (!base64Image.isNullOrEmpty()) {
                put("_attachments", buildJsonObject {
                    put("img", buildJsonObject {
                        put("content_type", "image/jpeg")
                        put("data", base64Image)
                    })
                })
            }
        }.toGson()
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

    private fun getRoles(): JsonArray = buildJsonArray {
        for (s in rolesList ?: emptyList()) {
            add(s)
        }
    }.toGson()

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
