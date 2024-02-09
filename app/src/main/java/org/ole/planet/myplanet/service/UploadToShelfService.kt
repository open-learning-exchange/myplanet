package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMeetup.Companion.getMyMeetUpIds
import org.ole.planet.myplanet.model.RealmMyCourse.Companion.getMyCourseIds
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmMyHealthPojo.Companion.serialize
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getMyLibIds
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.removedIds
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Response
import java.io.IOException
import java.util.Date

class UploadToShelfService(context: Context) {
    private val dbService: DatabaseService
    private val sharedPreferences: SharedPreferences
    private var mRealm: Realm? = null

    init {
        sharedPreferences =
            context.getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
        dbService = DatabaseService(context)
    }

    fun uploadUserData(listener: SuccessListener) {
        val apiInterface = client!!.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm!!.executeTransactionAsync({ realm: Realm ->
            val userModels: List<RealmUserModel> = realm.where(
                RealmUserModel::class.java
            ).isEmpty("_id").or().equalTo("updated", true).findAll()
            Utilities.log("USER LIST SIZE + " + userModels.size)
            for (model in userModels) {
                try {
                    var res = apiInterface.getJsonObject(
                        Utilities.getHeader(),
                        Utilities.getUrl() + "/_users/org.couchdb.user:" + model.name
                    ).execute()
                    if (res.body() == null) {
                        val obj = model.serialize()
                        res = apiInterface.putDoc(
                            null,
                            "application/json",
                            Utilities.getUrl() + "/_users/org.couchdb.user:" + model.name,
                            obj
                        ).execute()
                        if (res.body() != null) {
                            val id = res.body()!!.get("id").asString
                            val rev = res.body()!!.get("rev").asString
                            res = apiInterface.getJsonObject(
                                Utilities.getHeader(),
                                Utilities.getUrl() + "/_users/" + id
                            ).execute()
                            if (res.body() != null) {
                                model._id = id
                                model._rev = rev
                                model.password_scheme = getString("password_scheme", res.body())
                                model.derived_key = getString("derived_key", res.body())
                                model.salt = getString("salt", res.body())
                                model.iterations = getString("iterations", res.body())
                                if (saveKeyIv(apiInterface, model, obj)) updateHealthData(
                                    realm,
                                    model
                                )
                            }
                        }
                    } else if (model.isUpdated) {
                        Utilities.log("UPDATED MODEL " + model.serialize())
                        val obj = model.serialize()
                        res = apiInterface.putDoc(
                            null,
                            "application/json",
                            Utilities.getUrl() + "/_users/org.couchdb.user:" + model.name,
                            obj
                        ).execute()
                        if (res.body() != null) {
                            Utilities.log(Gson().toJson(res.body()))
                            val rev = res.body()!!["rev"].asString
                            model._rev = rev
                            model.isUpdated = false
                        } else {
                            Utilities.log(res.errorBody()!!.string())
                        }
                    } else {
                        Utilities.toast(
                            MainApplication.context,
                            "User " + model.name + " already exist"
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }, { uploadToshelf(listener) }) { uploadToshelf(listener) }
    }

    private fun updateHealthData(realm: Realm, model: RealmUserModel) {
        val list: List<RealmMyHealthPojo> = realm.where(RealmMyHealthPojo::class.java
        ).equalTo("_id", model.id).findAll()
        for (p in list) {
            p.userId = model._id
        }
    }

    @Throws(IOException::class)
    fun saveKeyIv(apiInterface: ApiInterface, model: RealmUserModel, obj: JsonObject): Boolean {
        val table =
            "userdb-" + Utilities.toHex(model.planetCode) + "-" + Utilities.toHex(model.name)
        val header = "Basic " + Base64.encodeToString(
            (obj["name"].asString + ":" + obj["password"].asString).toByteArray(),
            Base64.NO_WRAP
        )
        val ob = JsonObject()
        var keyString = generateKey()
        var iv: String? = generateIv()
        if (!TextUtils.isEmpty(model.iv)) iv = model.iv
        if (!TextUtils.isEmpty(model.key)) keyString = model.key
        ob.addProperty("key", keyString)
        ob.addProperty("iv", iv)
        ob.addProperty("createdOn", Date().time)
        var success = false
        while (!success) {
            val response: Response<*> = apiInterface.postDoc(
                header,
                "application/json",
                Utilities.getUrl() + "/" + table,
                ob
            ).execute()
            if (response.body() != null) {
                model.key = keyString
                model.iv = iv
                success = true
            } else {
                success = false
            }
        }
        changeUserSecurity(model, obj)
        return true
    }

    fun uploadHealth() {
        val apiInterface = client!!.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm!!.executeTransactionAsync { realm: Realm ->
            val myHealths: List<RealmMyHealthPojo> = realm.where(
                RealmMyHealthPojo::class.java
            ).equalTo("isUpdated", true).notEqualTo("userId", "").findAll()
            for (pojo in myHealths) {
                try {
                    val res = apiInterface.postDoc(
                        Utilities.getHeader(),
                        "application/json",
                        Utilities.getUrl() + "/health",
                        serialize(pojo)
                    ).execute()
                    if (res.body() != null && res.body()!!.has("id")) {
                        pojo.set_rev(res.body()!!["rev"].asString)
                        pojo.isUpdated = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun uploadToshelf(listener: SuccessListener) {
        val apiInterface = client!!.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm!!.executeTransactionAsync(
            { realm: Realm ->
                val users = realm.where(
                    RealmUserModel::class.java
                ).isNotEmpty("_id").findAll()
                for (model in users) {
                    try {
                        if (model.id!!.startsWith("guest")) continue
                        val jsonDoc = apiInterface.getJsonObject(
                            Utilities.getHeader(),
                            Utilities.getUrl() + "/shelf/" + model._id
                        ).execute().body()
                        val `object` = getShelfData(realm, model.id, jsonDoc)
                        Utilities.log("JSON " + Gson().toJson(jsonDoc))
                        val d = apiInterface.getJsonObject(
                            Utilities.getHeader(),
                            Utilities.getUrl() + "/shelf/" + model.id
                        ).execute().body()
                        `object`.addProperty("_rev", getString("_rev", d))
                        apiInterface.putDoc(
                            Utilities.getHeader(),
                            "application/json",
                            Utilities.getUrl() + "/shelf/" + sharedPreferences.getString(
                                "userId",
                                ""
                            ),
                            `object`
                        ).execute().body()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            { listener.onSuccess("Sync with server completed successfully") }) { err: Throwable? ->
            listener.onSuccess(
                "Unable to update documents."
            )
        }
    }

    private fun getShelfData(realm: Realm?, userId: String?, jsonDoc: JsonObject?): JsonObject {
        val myLibs = getMyLibIds(realm!!, userId)
        val myCourses = getMyCourseIds(realm, userId)
        val myMeetups = getMyMeetUpIds(realm, userId)
        val removedResources = listOf(*removedIds(realm, "resources", userId!!))
        val removedCourses = listOf(*removedIds(realm, "courses", userId))
        val mergedResourceIds =
            mergeJsonArray(myLibs, getJsonArray("resourceIds", jsonDoc), removedResources)
        val mergedCoueseIds = mergeJsonArray(myCourses, getJsonArray("courseIds", jsonDoc), removedCourses)
        val `object` = JsonObject()
        `object`.addProperty("_id", sharedPreferences.getString("userId", ""))
        `object`.add("meetupIds", mergeJsonArray(myMeetups, getJsonArray("meetupIds", jsonDoc), removedResources))
        `object`.add("resourceIds", mergedResourceIds)
        `object`.add("courseIds", mergedCoueseIds)
        return `object`
    }

    private fun mergeJsonArray(array1: JsonArray?, array2: JsonArray, removedIds: List<String>): JsonArray {
        val array = JsonArray()
        array.addAll(array1)
        for (e in array2) {
            if (!array.contains(e) && !removedIds.contains(e.asString)) {
                array.add(e)
            }
        }
        return array
    }

    companion object {
        var instance: UploadToShelfService? = null
            get() {
                if (field == null) {
                    field = UploadToShelfService(MainApplication.context)
                }
                return field
            }
            private set

        private fun changeUserSecurity(model: RealmUserModel, obj: JsonObject) {
            val table = "userdb-" + Utilities.toHex(model.planetCode) + "-" + Utilities.toHex(model.name)
            val header = "Basic " + Base64.encodeToString((obj["name"].asString + ":" + obj["password"].asString).toByteArray(), Base64.NO_WRAP)
            val apiInterface = client!!.create(ApiInterface::class.java)
            var response: Response<JsonObject?>
            try {
                response = apiInterface.getJsonObject(header, Utilities.getUrl() + "/" + table + "/_security").execute()
                if (response.body() != null) {
                    val jsonObject = response.body()
                    val members = jsonObject!!.getAsJsonObject("members")
                    val rolesArray: JsonArray = if (members.has("roles")) {
                        members.getAsJsonArray("roles")
                    } else {
                        JsonArray()
                    }
                    rolesArray.add("health")
                    members.add("roles", rolesArray)
                    jsonObject.add("members", members)
                    response = apiInterface.putDoc(header, "application/json", Utilities.getUrl() + "/" + table + "/_security", jsonObject).execute()
                    if (response.body() != null) {
                        Utilities.log("Update security  " + Gson().toJson(response.body()))
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
