package org.ole.planet.myplanet.model

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Locale
import java.util.UUID

class RealmUserModel : RealmObject {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var _id: String? = null
    var _rev: String? = null
    var name: String? = null
    var rolesList: RealmList<String> = realmListOf()
    var userAdmin: Boolean = false
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
    var birthPlace: String? = null
    var userImage: String? = null
    var key: String? = null
    var iv: String? = null
    var password: String? = null
    var isUpdated: Boolean = false
    var isShowTopbar: Boolean = false
    var isArchived: Boolean = false

    fun serialize(): JsonObject {
        return JsonObject().apply {
            if (!_id.isNullOrEmpty()) {
                addProperty("_id", _id)
                addProperty("_rev", _rev)
            }
            addProperty("name", name)
            add("roles", getRoles())
            if (_id.isNullOrEmpty()) {
                addProperty("password", password)
                addProperty("androidId", NetworkUtils.getUniqueIdentifier())
                addProperty("uniqueAndroidId", VersionUtils.getAndroidId(MainApplication.context))
                addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
            } else {
                addProperty("derived_key", derived_key)
                addProperty("salt", salt)
                addProperty("password_scheme", password_scheme)
            }
            addProperty("isUserAdmin", userAdmin)
            addProperty("joinDate", joinDate)
            addProperty("firstName", firstName)
            addProperty("lastName", lastName)
            addProperty("middleName", middleName)
            addProperty("email", email)
            addProperty("language", language)
            addProperty("level", level)
            addProperty("type", "user")
            addProperty("gender", gender)
            addProperty("phoneNumber", phoneNumber)
            addProperty("birthDate", dob)
            try {
                addProperty("iterations", iterations?.toInt())
            } catch (e: Exception) {
                e.printStackTrace()
                addProperty("iterations", 10)
            }
            addProperty("parentCode", parentCode)
            addProperty("planetCode", planetCode)
            addProperty("birthPlace", birthPlace)
            addProperty("isArchived", isArchived)
        }
    }

    private fun getRoles(): JsonArray {
        return JsonArray().apply {
            rolesList.forEach { add(it) }
        }
    }

    fun setRoles(roles: List<String>) {
        rolesList.clear()
        rolesList.addAll(roles)
    }

    fun getRoleAsString(): String = rolesList.joinToString(",")

    fun getFullName(): String = "$firstName $lastName"

    fun addImageUrl(jsonDoc: JsonObject?) {
        jsonDoc?.get("_attachments")?.asJsonObject?.let { attachments ->
            attachments.entrySet().firstOrNull()?.let { (key) ->
                userImage = Utilities.getUserImageUrl(id, key)
            }
        }
    }

    fun isManager(): Boolean = getRoles().toString().lowercase(Locale.ROOT).contains("manager") || userAdmin

    fun isLeader(): Boolean = getRoles().toString().lowercase(Locale.ROOT).contains("leader")

    fun isGuest(): Boolean = _id?.startsWith("guest_") == true

    override fun toString(): String = name ?: ""

    companion object {
        private val userDataList: MutableList<Array<String>> = mutableListOf()

        suspend fun createGuestUser(username: String?, realm: Realm, settings: SharedPreferences): RealmUserModel? {
            val jsonObject = JsonObject().apply {
                addProperty("_id", "guest_$username")
                addProperty("name", username)
                addProperty("firstName", username)
                add("roles", JsonArray().apply { add("guest") })
            }

            return populateUsersTable(jsonObject, realm, settings)
        }

        suspend fun populateUsersTable(jsonDoc: JsonObject?, realm: Realm, settings: SharedPreferences): RealmUserModel? {
            return try {
                val id = JsonUtils.getString("_id", jsonDoc).ifEmpty { UUID.randomUUID().toString() }

                realm.write {
                    val user = query<RealmUserModel>("_id == $0", id).first().find()
                        ?: RealmUserModel().apply { this._id = id }

                    copyToRealm(user.apply {
                        insertIntoUsers(jsonDoc, this, settings)
                    })
                }
            } catch (err: Exception) {
                err.printStackTrace()
                null
            }
        }

        fun isUserExists(realm: Realm, name: String?): Boolean {
            return realm.query<RealmUserModel>("name == $0", name ?: "").count().find() > 0
        }

        private fun insertIntoUsers(jsonDoc: JsonObject?, user: RealmUserModel, settings: SharedPreferences) {
            user.apply {
                _rev = JsonUtils.getString("_rev", jsonDoc)
                name = JsonUtils.getString("name", jsonDoc)

                val rolesArray = JsonUtils.getJsonArray("roles", jsonDoc)
                val roles = mutableListOf<String>()
                for (i in 0 until rolesArray.size()) {
                    JsonUtils.getString(rolesArray, i).let { roles.add(it) }
                }
                setRoles(roles)

                userAdmin = JsonUtils.getBoolean("isUserAdmin", jsonDoc)
                joinDate = JsonUtils.getLong("joinDate", jsonDoc)
                firstName = JsonUtils.getString("firstName", jsonDoc)
                lastName = JsonUtils.getString("lastName", jsonDoc)
                middleName = JsonUtils.getString("middleName", jsonDoc)
                planetCode = JsonUtils.getString("planetCode", jsonDoc)
                parentCode = JsonUtils.getString("parentCode", jsonDoc)
                email = JsonUtils.getString("email", jsonDoc)
                if (_id.isNullOrEmpty()) {
                    password = JsonUtils.getString("password", jsonDoc)
                }
                phoneNumber = JsonUtils.getString("phoneNumber", jsonDoc)
                password_scheme = JsonUtils.getString("password_scheme", jsonDoc)
                iterations = JsonUtils.getString("iterations", jsonDoc)
                derived_key = JsonUtils.getString("derived_key", jsonDoc)
                salt = JsonUtils.getString("salt", jsonDoc)
                dob = JsonUtils.getString("birthDate", jsonDoc)
                birthPlace = JsonUtils.getString("birthPlace", jsonDoc)
                gender = JsonUtils.getString("gender", jsonDoc)
                language = JsonUtils.getString("language", jsonDoc)
                level = JsonUtils.getString("level", jsonDoc)
                isShowTopbar = true
                addImageUrl(jsonDoc)
                isArchived = JsonUtils.getBoolean("isArchived", jsonDoc)

                JsonUtils.getString("planetCode", jsonDoc).let {
                    settings.edit().putString("planetCode", it).apply()
                }
                JsonUtils.getString("parentCode", jsonDoc).let {
                    settings.edit().putString("parentCode", it).apply()
                }
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

        suspend fun updateUserDetails(realm: Realm, userId: String?, firstName: String?, lastName: String?,
            middleName: String?, email: String?, phoneNumber: String?, level: String?, language: String?,
            gender: String?, dob: String?, onSuccess: () -> Unit
        ) {
            try {
                realm.write {
                    query<RealmUserModel>("id == $0", userId ?: "").first().find()?.apply {
                        this.firstName = firstName
                        this.lastName = lastName
                        this.middleName = middleName
                        this.email = email
                        this.phoneNumber = phoneNumber
                        this.level = level
                        this.language = language
                        this.gender = gender
                        this.dob = dob
                        this.isUpdated = true
                    }?.also {
                        copyToRealm(it)
                        onSuccess()
                        Utilities.toast(MainApplication.context, "User details updated successfully")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Utilities.toast(MainApplication.context, "User details update failed")
            }
        }

        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                CSVWriter(FileWriter(file)).use { writer ->
                    writer.writeNext(arrayOf(
                        "userAdmin", "_id", "name", "firstName", "lastName",
                        "email", "phoneNumber", "planetCode", "parentCode",
                        "password_scheme", "iterations", "derived_key", "salt", "level"
                    ))
                    data.forEach { writer.writeNext(it) }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        fun userWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/userData.csv", userDataList)
        }
    }
}
