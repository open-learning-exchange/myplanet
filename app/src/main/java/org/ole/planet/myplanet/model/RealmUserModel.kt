package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.opencsv.CSVWriter
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.apache.commons.lang3.StringUtils
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID

open class RealmUserModel : RealmObject() {
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
            jsonObject.addProperty("iterations", iterations?.toInt())
        } catch (e: Exception) {
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
                val uri = Uri.parse(imagePath)
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
        return StringUtils.join(rolesList, ",")
    }

    fun getFullName(): String {
        return "$firstName $lastName"
    }

    fun addImageUrl(jsonDoc: JsonObject?) {
        if (jsonDoc?.has("_attachments") == true) {
            val element = JsonParser.parseString(jsonDoc["_attachments"].asJsonObject.toString())
            val obj = element.asJsonObject
            val entries = obj.entrySet()
            for ((key1) in entries) {
                userImage = Utilities.getUserImageUrl(id, key1)
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
        return _id?.startsWith("guest_") == true
    }

    override fun toString(): String {
        return "$name"
    }

    companion object {
        private val userDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        fun createGuestUser(username: String?, mRealm: Realm, settings: SharedPreferences): RealmUserModel? {
            val `object` = JsonObject()
            `object`.addProperty("_id", "guest_$username")
            `object`.addProperty("name", username)
            `object`.addProperty("firstName", username)
            val rolesArray = JsonArray()
            rolesArray.add("guest")
            `object`.add("roles", rolesArray)
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            return populateUsersTable(`object`, mRealm, settings)
        }

        @JvmStatic
        fun populateUsersTable(jsonDoc: JsonObject?, mRealm: Realm?, settings: SharedPreferences): RealmUserModel? {
            if (jsonDoc == null || mRealm == null) return null

            try {
                val id = JsonUtils.getString("_id", jsonDoc).takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()

                var user: RealmUserModel? = null

                if (!mRealm.isInTransaction) {
                    mRealm.executeTransaction { realm ->
                        user = realm.where(RealmUserModel::class.java)
                            .equalTo("_id", id)
                            .findFirst() ?: realm.createObject(RealmUserModel::class.java, id)

                        insertIntoUsers(jsonDoc, user!!, settings)
                    }
                } else {
                    user = mRealm.where(RealmUserModel::class.java)
                        .equalTo("_id", id)
                        .findFirst() ?: mRealm.createObject(RealmUserModel::class.java, id)
                    insertIntoUsers(jsonDoc, user!!, settings)
                }
                return user
            } catch (err: Exception) {
                err.printStackTrace()
            }
            return null
        }

        private fun insertIntoUsers(jsonDoc: JsonObject?, user: RealmUserModel, settings: SharedPreferences) {
            if (jsonDoc == null) return

            val planetCodes = JsonUtils.getString("planetCode", jsonDoc)
            val rolesArray = JsonUtils.getJsonArray("roles", jsonDoc)

            user.apply {
                _rev = JsonUtils.getString("_rev", jsonDoc)
                _id = JsonUtils.getString("_id", jsonDoc)
                name = JsonUtils.getString("name", jsonDoc)
                setRoles(RealmList<String?>().apply {
                    for (i in 0 until rolesArray.size()) {
                        add(JsonUtils.getString(rolesArray, i))
                    }
                })
                userAdmin = JsonUtils.getBoolean("isUserAdmin", jsonDoc)
                joinDate = JsonUtils.getLong("joinDate", jsonDoc)
                firstName = JsonUtils.getString("firstName", jsonDoc)
                lastName = JsonUtils.getString("lastName", jsonDoc)
                middleName = JsonUtils.getString("middleName", jsonDoc)
                planetCode = planetCodes
                parentCode = JsonUtils.getString("parentCode", jsonDoc)
                email = JsonUtils.getString("email", jsonDoc)
                if (_id?.isEmpty() == true) {
                    password = JsonUtils.getString("password", jsonDoc)
                }
                phoneNumber = JsonUtils.getString("phoneNumber", jsonDoc)
                password_scheme = JsonUtils.getString("password_scheme", jsonDoc)
                iterations = JsonUtils.getString("iterations", jsonDoc)
                derived_key = JsonUtils.getString("derived_key", jsonDoc)
                salt = JsonUtils.getString("salt", jsonDoc)
                dob = JsonUtils.getString("birthDate", jsonDoc)
                birthPlace = JsonUtils.getString("birthPlace", jsonDoc)
                age = JsonUtils.getString("age", jsonDoc)
                gender = JsonUtils.getString("gender", jsonDoc)
                language = JsonUtils.getString("language", jsonDoc)
                level = JsonUtils.getString("level", jsonDoc)
                isShowTopbar = true
                addImageUrl(jsonDoc)
                isArchived = JsonUtils.getBoolean("isArchived", jsonDoc)
            }

            if (planetCodes.isNotEmpty()) {
                settings.edit().putString("planetCode", planetCodes).apply()
            }

            userDataList.add(arrayOf(
                user.userAdmin.toString(),
                user._id.toString(),
                user.name.toString(),
                user.firstName.toString(),
                user.lastName.toString(),
                user.email.toString(),
                user.phoneNumber.toString(),
                user.planetCode.toString(),
                user.parentCode.toString(),
                user.password_scheme.toString(),
                user.iterations.toString(),
                user.derived_key.toString(),
                user.salt.toString(),
                user.level.toString(),
                user.language.toString(),
                user.gender.toString(),
                user.dob.toString(),
                user.birthPlace.toString(),
                user.userImage.toString(),
                user.isArchived.toString()
            ))
        }

        @JvmStatic
        fun isUserExists(realm: Realm, name: String?): Boolean {
            return realm.where(RealmUserModel::class.java).equalTo("name", name).count() > 0
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("userAdmin", "_id", "name", "firstName", "lastName", "email", "phoneNumber", "planetCode", "parentCode", "password_scheme", "iterations", "derived_key", "salt", "level"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun userWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/userData.csv", userDataList)
        }


        fun updateUserDetails(realm: Realm, userId: String?, firstName: String?, lastName: String?,
        middleName: String?, email: String?, phoneNumber: String?, level: String?, language: String?,
        gender: String?, dob: String?, onSuccess: () -> Unit) {
            realm.executeTransactionAsync({ mRealm ->
                val user = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                if (user != null) {
                    user.firstName = firstName
                    user.lastName = lastName
                    user.middleName = middleName
                    user.email = email
                    user.phoneNumber = phoneNumber
                    user.level = level
                    user.language = language
                    user.gender = gender
                    user.dob = dob
                    user.isUpdated = true
                }
            }, {
                onSuccess.invoke()
                Utilities.toast(context, "User details updated successfully")
            }) {
                Utilities.toast(context, "User details update failed")
            }
        }
    }
}
