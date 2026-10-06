package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
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

// App-side Gson/org.json helpers for UserEntity, kept out of the Room entity.

fun UserEntity.serialize(): JsonObject {
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

private fun UserEntity.getRoles(): JsonArray = buildJsonArray {
    for (s in rolesList ?: emptyList()) {
        add(s)
    }
}.toGson()

fun UserEntity.getRoleAsString(): String {
    return if (rolesList != null) {
        StringUtils.join(rolesList, ",")
    } else {
        ""
    }
}

fun UserEntity.addImageUrl(jsonDoc: JsonObject?) {
    if (jsonDoc?.has("_attachments") == true) {
        val obj = jsonDoc["_attachments"].asJsonObject
        val key1 = obj.entrySet().firstOrNull()?.key
        if (key1 != null) {
            userImage = UrlUtils.getUserImageUrl(id, key1)
        }
    }
}

fun UserEntity.Companion.parseLeadersJson(jsonString: String): List<UserEntity> {
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
