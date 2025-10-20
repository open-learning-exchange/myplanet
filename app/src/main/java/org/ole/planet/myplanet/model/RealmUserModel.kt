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
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import org.apache.commons.lang3.StringUtils
import org.json.JSONException
import org.json.JSONObject
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.JsonUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils

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
        return StringUtils.join(rolesList, ",")
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
        @JvmStatic
        fun createGuestUser(username: String?, mRealm: Realm, settings: SharedPreferences): RealmUserModel? {
            val `object` = JsonObject()
            `object`.addProperty("_id", "guest_$username")
            `object`.addProperty("name", username)
            `object`.addProperty("firstName", username)
            val rolesArray = JsonArray()
            rolesArray.add("guest")
            `object`.add("roles", rolesArray)
            var managedGuest: RealmUserModel? = null
            val shouldExecuteTransaction = !mRealm.isInTransaction

            return try {
                if (shouldExecuteTransaction) {
                    mRealm.executeTransaction { realm ->
                        managedGuest = populateUsersTable(`object`, realm, settings)
                    }
                } else {
                    managedGuest = populateUsersTable(`object`, mRealm, settings)
                }
                managedGuest?.let { mRealm.copyFromRealm(it) }
            } catch (err: Exception) {
                if (shouldExecuteTransaction && mRealm.isInTransaction) {
                    mRealm.cancelTransaction()
                }
                err.printStackTrace()
                null
            }
        }

        @JvmStatic
        fun populateUsersTable(jsonDoc: JsonObject?, mRealm: Realm?, settings: SharedPreferences): RealmUserModel? {
            if (jsonDoc == null || mRealm == null) return null
            try {
                val id = JsonUtils.getString("_id", jsonDoc).takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()
                val userName = JsonUtils.getString("name", jsonDoc)
                var user: RealmUserModel? = null

                if (!mRealm.isInTransaction) {
                    mRealm.executeTransaction { realm ->
                        user = realm.where(RealmUserModel::class.java)
                            .equalTo("_id", id)
                            .findFirst()

                        if (user == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
                            val guestUser = realm.where(RealmUserModel::class.java)
                                .equalTo("name", userName)
                                .beginsWith("_id", "guest_")
                                .findFirst()

                            if (guestUser != null) {
                                val tempData = JsonObject()
                                tempData.addProperty("_id", id)
                                tempData.addProperty("name", guestUser.name)
                                tempData.addProperty("firstName", guestUser.firstName)
                                tempData.addProperty("lastName", guestUser.lastName)
                                tempData.addProperty("middleName", guestUser.middleName)
                                tempData.addProperty("email", guestUser.email)
                                tempData.addProperty("phoneNumber", guestUser.phoneNumber)
                                tempData.addProperty("level", guestUser.level)
                                tempData.addProperty("language", guestUser.language)
                                tempData.addProperty("gender", guestUser.gender)
                                tempData.addProperty("birthDate", guestUser.dob)
                                tempData.addProperty("planetCode", guestUser.planetCode)
                                tempData.addProperty("parentCode", guestUser.parentCode)
                                tempData.addProperty("userImage", guestUser.userImage)
                                tempData.addProperty("joinDate", guestUser.joinDate)
                                tempData.addProperty("isShowTopbar", guestUser.isShowTopbar)
                                tempData.addProperty("isArchived", guestUser.isArchived)

                                val rolesArray = JsonArray()
                                guestUser.rolesList?.forEach { role ->
                                    rolesArray.add(role)
                                }
                                tempData.add("roles", rolesArray)
                                guestUser.deleteFromRealm()
                                user = realm.createObject(RealmUserModel::class.java, id)
                                user?.let { insertIntoUsers(tempData, it, settings) }
                            }
                        }

                        if (user == null) {
                            user = realm.createObject(RealmUserModel::class.java, id)
                        }
                        user?.let { insertIntoUsers(jsonDoc, it, settings) }
                    }
                } else {
                    user = mRealm.where(RealmUserModel::class.java)
                        .equalTo("_id", id)
                        .findFirst()

                    if (user == null && id.startsWith("org.couchdb.user:") && userName.isNotEmpty()) {
                        val guestUser = mRealm.where(RealmUserModel::class.java)
                            .equalTo("name", userName)
                            .beginsWith("_id", "guest_")
                            .findFirst()

                        if (guestUser != null) {
                            val tempData = JsonObject()
                            tempData.addProperty("_id", id)
                            tempData.addProperty("name", guestUser.name)
                            tempData.addProperty("firstName", guestUser.firstName)
                            tempData.addProperty("lastName", guestUser.lastName)
                            tempData.addProperty("middleName", guestUser.middleName)
                            tempData.addProperty("email", guestUser.email)
                            tempData.addProperty("phoneNumber", guestUser.phoneNumber)
                            tempData.addProperty("level", guestUser.level)
                            tempData.addProperty("language", guestUser.language)
                            tempData.addProperty("gender", guestUser.gender)
                            tempData.addProperty("birthDate", guestUser.dob)
                            tempData.addProperty("planetCode", guestUser.planetCode)
                            tempData.addProperty("parentCode", guestUser.parentCode)
                            tempData.addProperty("userImage", guestUser.userImage)
                            tempData.addProperty("joinDate", guestUser.joinDate)
                            tempData.addProperty("isShowTopbar", guestUser.isShowTopbar)
                            tempData.addProperty("isArchived", guestUser.isArchived)
                            val rolesArray = JsonArray()
                            guestUser.rolesList?.forEach { role ->
                                rolesArray.add(role)
                            }
                            tempData.add("roles", rolesArray)
                            guestUser.deleteFromRealm()
                            user = mRealm.createObject(RealmUserModel::class.java, id)
                            user?.let { insertIntoUsers(tempData, it, settings) }
                        }
                    }

                    if (user == null) {
                        user = mRealm.createObject(RealmUserModel::class.java, id)
                    }
                    user?.let { insertIntoUsers(jsonDoc, it, settings) }
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
            val newId = JsonUtils.getString("_id", jsonDoc)

            user.apply {
                _rev = JsonUtils.getString("_rev", jsonDoc)
                _id = newId
                name = JsonUtils.getString("name", jsonDoc)
                setRoles(RealmList<String?>().apply {
                    for (i in 0 until rolesArray.size()) {
                        add(JsonUtils.getString(rolesArray, i))
                    }
                })
                userAdmin = JsonUtils.getBoolean("isUserAdmin", jsonDoc)
                val newJoinDate = JsonUtils.getLong("joinDate", jsonDoc)
                if (newJoinDate != 0L || joinDate == 0L) {
                    joinDate = newJoinDate
                }

                val newFirstName = JsonUtils.getString("firstName", jsonDoc)
                if (newFirstName.isNotEmpty() || firstName.isNullOrEmpty()) {
                    firstName = newFirstName
                }

                val newLastName = JsonUtils.getString("lastName", jsonDoc)
                if (newLastName.isNotEmpty() || lastName.isNullOrEmpty()) {
                    lastName = newLastName
                }

                val newMiddleName = JsonUtils.getString("middleName", jsonDoc)
                if (newMiddleName.isNotEmpty() || middleName.isNullOrEmpty()) {
                    middleName = newMiddleName
                }

                val newEmail = JsonUtils.getString("email", jsonDoc)
                if (newEmail.isNotEmpty() || email.isNullOrEmpty()) {
                    email = newEmail
                }

                val newPhoneNumber = JsonUtils.getString("phoneNumber", jsonDoc)
                if (newPhoneNumber.isNotEmpty() || phoneNumber.isNullOrEmpty()) {
                    phoneNumber = newPhoneNumber
                }

                val newLevel = JsonUtils.getString("level", jsonDoc)
                if (newLevel.isNotEmpty() || level.isNullOrEmpty()) {
                    level = newLevel
                }

                val newLanguage = JsonUtils.getString("language", jsonDoc)
                if (newLanguage.isNotEmpty() || language.isNullOrEmpty()) {
                    language = newLanguage
                }

                val newGender = JsonUtils.getString("gender", jsonDoc)
                if (newGender.isNotEmpty() || gender.isNullOrEmpty()) {
                    gender = newGender
                }

                val newDob = JsonUtils.getString("birthDate", jsonDoc)
                if (newDob.isNotEmpty() || dob.isNullOrEmpty()) {
                    dob = newDob
                }

                val newBirthPlace = JsonUtils.getString("birthPlace", jsonDoc)
                if (newBirthPlace.isNotEmpty() || birthPlace.isNullOrEmpty()) {
                    birthPlace = newBirthPlace
                }

                val newAge = JsonUtils.getString("age", jsonDoc)
                if (newAge.isNotEmpty() || age.isNullOrEmpty()) {
                    age = newAge
                }
                planetCode = planetCodes
                parentCode = JsonUtils.getString("parentCode", jsonDoc)
                if (_id?.isEmpty() == true) {
                    password = JsonUtils.getString("password", jsonDoc)
                }
                password_scheme = JsonUtils.getString("password_scheme", jsonDoc)
                iterations = JsonUtils.getString("iterations", jsonDoc)
                derived_key = JsonUtils.getString("derived_key", jsonDoc)
                salt = JsonUtils.getString("salt", jsonDoc)
                isShowTopbar = true
                isArchived = JsonUtils.getBoolean("isArchived", jsonDoc)
                addImageUrl(jsonDoc)
            }

            if (planetCodes.isNotEmpty()) {
                settings.edit { putString("planetCode", planetCodes) }
            }
        }

        @JvmStatic
        fun isUserExists(realm: Realm, name: String?): Boolean {
            return realm.where(RealmUserModel::class.java)
                .equalTo("name", name)
                .not().beginsWith("_id", "guest").count() > 0
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

        @JvmStatic
        fun parseLeadersJson(jsonString: String): List<RealmUserModel> {
            val leadersList = mutableListOf<RealmUserModel>()
            try {
                val jsonObject = JSONObject(jsonString)
                val docsArray = jsonObject.getJSONArray("docs")
                for (i in 0 until docsArray.length()) {
                    val docObject = docsArray.getJSONObject(i)
                    val user = RealmUserModel()
                    user.name = docObject.getString("name")
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

        @JvmStatic
        fun cleanupDuplicateUsers(realm: Realm, onSuccess: () -> Unit) {
            realm.executeTransactionAsync({ mRealm: Realm ->
                val allUsers = mRealm.where(RealmUserModel::class.java).findAll()
                val usersByName = allUsers.groupBy { it.name }

                usersByName.forEach { (_, users) ->
                    if (users.size > 1) {
                        val sortedUsers = users.sortedWith { user1, user2 ->
                            when {
                                user1._id?.startsWith("org.couchdb.user:") == true &&
                                        user2._id?.startsWith("guest_") == true -> -1
                                user1._id?.startsWith("guest_") == true &&
                                        user2._id?.startsWith("org.couchdb.user:") == true -> 1
                                else -> 0
                            }
                        }

                        for (i in 1 until sortedUsers.size) {
                            sortedUsers[i].deleteFromRealm()
                        }
                    }
                }
            }, {
                onSuccess.invoke()
            }) {
            }
        }
    }
}
