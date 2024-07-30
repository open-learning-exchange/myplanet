package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import android.text.TextUtils
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
import java.util.Locale
import java.util.UUID

open class RealmUserModel : RealmObject() {
    @JvmField
    @PrimaryKey
    var id: String? = null
    @JvmField
    var _id: String? = null
    @JvmField
    var _rev: String? = null
    @JvmField
    var name: String? = null
    @JvmField
    var rolesList: RealmList<String?>? = null
    @JvmField
    var userAdmin: Boolean? = null
    @JvmField
    var joinDate: Long = 0
    @JvmField
    var firstName: String? = null
    @JvmField
    var lastName: String? = null
    @JvmField
    var middleName: String? = null
    @JvmField
    var email: String? = null
    @JvmField
    var planetCode: String? = null
    @JvmField
    var parentCode: String? = null
    @JvmField
    var phoneNumber: String? = null
    @JvmField
    var password_scheme: String? = null
    @JvmField
    var iterations: String? = null
    @JvmField
    var derived_key: String? = null
    @JvmField
    var level: String? = null
    @JvmField
    var language: String? = null
    @JvmField
    var gender: String? = null
    @JvmField
    var salt: String? = null
    @JvmField
    var dob: String? = null
    @JvmField
    var birthPlace: String? = null
    @JvmField
    var userImage: String? = null
    @JvmField
    var key: String? = null
    @JvmField
    var iv: String? = null
    @JvmField
    var password: String? = null
    @JvmField
    var isUpdated = false
    @JvmField
    var isShowTopbar = false
    @JvmField
    var isArchived = false

    fun serialize(): JsonObject {
        val `object` = JsonObject()
        if (_id?.isNotEmpty() == true) {
            `object`.addProperty("_id", _id)
            `object`.addProperty("_rev", _rev)
        }
        `object`.addProperty("name", name)
        `object`.add("roles", getRoles())
        if (_id?.isEmpty() == true) {
            `object`.addProperty("password", password)
            `object`.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            `object`.addProperty("uniqueAndroidId", VersionUtils.getAndroidId(context))
            `object`.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(context))
        } else {
            `object`.addProperty("derived_key", derived_key)
            `object`.addProperty("salt", salt)
            `object`.addProperty("password_scheme", password_scheme)
        }
        `object`.addProperty("isUserAdmin", userAdmin)
        `object`.addProperty("joinDate", joinDate)
        `object`.addProperty("firstName", firstName)
        `object`.addProperty("lastName", lastName)
        `object`.addProperty("middleName", middleName)
        `object`.addProperty("email", email)
        `object`.addProperty("language", language)
        `object`.addProperty("level", level)
        `object`.addProperty("type", "user")
        `object`.addProperty("gender", gender)
        `object`.addProperty("phoneNumber", phoneNumber)
        `object`.addProperty("birthDate", dob)
        try {
            `object`.addProperty("iterations", iterations?.toInt())
        } catch (e: Exception) {
            `object`.addProperty("iterations", 10)
        }
        `object`.addProperty("parentCode", parentCode)
        `object`.addProperty("planetCode", planetCode)
        `object`.addProperty("birthPlace", birthPlace)
        `object`.addProperty("isArchived", isArchived)
        return `object`
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
            try {
                var id = JsonUtils.getString("_id", jsonDoc)
                if (id.isEmpty()) id = UUID.randomUUID().toString()
                var user = mRealm?.where(RealmUserModel::class.java)?.equalTo("_id", id)?.findFirst()
                if (user == null) {
                    user = mRealm?.createObject(RealmUserModel::class.java, id)
                }
                insertIntoUsers(jsonDoc, user, settings)
                return user
            } catch (err: Exception) {
                err.printStackTrace()
            }
            return null
        }

        @JvmStatic
        fun isUserExists(realm: Realm, name: String?): Boolean {
            return realm.where(RealmUserModel::class.java).equalTo("name", name).count() > 0
        }

        private fun insertIntoUsers(jsonDoc: JsonObject?, user: RealmUserModel?, settings: SharedPreferences) {
            if (user != null) {
                user._rev = JsonUtils.getString("_rev", jsonDoc)
                user._id = JsonUtils.getString("_id", jsonDoc)
                user.name = JsonUtils.getString("name", jsonDoc)
                val array = JsonUtils.getJsonArray("roles", jsonDoc)
                val roles = RealmList<String?>()
                for (i in 0 until array.size()) {
                    roles.add(JsonUtils.getString(array, i))
                }
                user.setRoles(roles)
                user.userAdmin = JsonUtils.getBoolean("isUserAdmin", jsonDoc)
                user.joinDate = JsonUtils.getLong("joinDate", jsonDoc)
                user.firstName = JsonUtils.getString("firstName", jsonDoc)
                user.lastName = JsonUtils.getString("lastName", jsonDoc)
                user.middleName = JsonUtils.getString("middleName", jsonDoc)
                user.planetCode = JsonUtils.getString("planetCode", jsonDoc)
                user.parentCode = JsonUtils.getString("parentCode", jsonDoc)
                user.email = JsonUtils.getString("email", jsonDoc)
                if (user._id?.isEmpty() == true) {
                    user.password = JsonUtils.getString("password", jsonDoc)
                }
                user.phoneNumber = JsonUtils.getString("phoneNumber", jsonDoc)
                user.password_scheme = JsonUtils.getString("password_scheme", jsonDoc)
                user.iterations = JsonUtils.getString("iterations", jsonDoc)
                user.derived_key = JsonUtils.getString("derived_key", jsonDoc)
                user.salt = JsonUtils.getString("salt", jsonDoc)
                user.dob = JsonUtils.getString("birthDate", jsonDoc)
                user.birthPlace = JsonUtils.getString("birthPlace", jsonDoc)
                user.gender = JsonUtils.getString("gender", jsonDoc)
                user.language = JsonUtils.getString("language", jsonDoc)
                user.level = JsonUtils.getString("level", jsonDoc)
                user.isShowTopbar = true
                user.addImageUrl(jsonDoc)
                user.isArchived = JsonUtils.getBoolean("isArchived", jsonDoc)
                if (!TextUtils.isEmpty(JsonUtils.getString("planetCode", jsonDoc))) {
                    settings.edit().putString("planetCode", JsonUtils.getString("planetCode", jsonDoc)).apply()
                }
                if (!TextUtils.isEmpty(JsonUtils.getString("parentCode", jsonDoc))) {
                    settings.edit().putString("parentCode", JsonUtils.getString("parentCode", jsonDoc)).apply()
                }

                val csvRow = arrayOf(
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
                )

                userDataList.add(csvRow)
            }
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
