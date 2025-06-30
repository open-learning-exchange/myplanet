package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
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
import org.ole.planet.myplanet.model.RealmMyLibrary.Companion.getMyLibIds
import org.ole.planet.myplanet.model.RealmRemovedLog.Companion.removedIds
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getJsonArray
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.Utilities
import java.io.IOException

class UploadToShelfService(context: Context) {
    private val dbService = DatabaseService(context)
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    lateinit var mRealm: Realm

    fun uploadToShelf(listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync({ realm ->
            val users = realm.where(RealmUserModel::class.java)
                .isNotEmpty("_id").findAll()
            users.forEach { model ->
                try {
                    if (model.id?.startsWith("guest") == true) return@forEach
                    val jsonDoc = apiInterface?.getJsonObject(
                        Utilities.header,
                        "${Utilities.getUrl()}/shelf/${model._id}"
                    )?.execute()?.body()
                    val obj = getShelfData(realm, model.id, jsonDoc)
                    val d = apiInterface?.getJsonObject(
                        Utilities.header,
                        "${Utilities.getUrl()}/shelf/${model.id}"
                    )?.execute()?.body()
                    obj.addProperty("_rev", getString("_rev", d))
                    apiInterface?.putDoc(
                        Utilities.header,
                        "application/json",
                        "${Utilities.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}",
                        obj
                    )?.execute()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, { listener.onSuccess("Sync with server completed successfully") }) { error ->
            listener.onSuccess("Unable to update documents: ${error.localizedMessage}")
        }
    }

    fun uploadSingleUserToShelf(userName: String?, listener: SuccessListener) {
        val apiInterface = client?.create(ApiInterface::class.java)
        mRealm = dbService.realmInstance
        mRealm.executeTransactionAsync({ realm ->
            val model = realm.where(RealmUserModel::class.java)
                .equalTo("name", userName)
                .isNotEmpty("_id")
                .findFirst()
            if (model != null) {
                try {
                    if (model.id?.startsWith("guest") == true) return@executeTransactionAsync
                    val shelfUrl = "${Utilities.getUrl()}/shelf/${model._id}"
                    val jsonDoc = apiInterface?.getJsonObject(Utilities.header, shelfUrl)?.execute()?.body()
                    val shelfObject = getShelfData(realm, model.id, jsonDoc)
                    val revDoc = apiInterface?.getJsonObject(
                        Utilities.header,
                        "${Utilities.getUrl()}/shelf/${model.id}"
                    )?.execute()?.body()
                    shelfObject.addProperty("_rev", getString("_rev", revDoc))
                    val targetUrl = "${Utilities.getUrl()}/shelf/${sharedPreferences.getString("userId", "")}"
                    apiInterface?.putDoc(Utilities.header, "application/json", targetUrl, shelfObject)?.execute()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, { listener.onSuccess("Single user shelf sync completed successfully") }) { error ->
            listener.onSuccess("Unable to update document: ${error.localizedMessage}")
        }
    }

    private fun getShelfData(realm: Realm?, userId: String?, jsonDoc: JsonObject?): JsonObject {
        val myLibs = getMyLibIds(realm, userId)
        val myCourses = getMyCourseIds(realm, userId)
        val myMeetups = getMyMeetUpIds(realm, userId)
        val removedResources = listOf(*removedIds(realm, "resources", userId))
        val removedCourses = listOf(*removedIds(realm, "courses", userId))
        val mergedResourceIds = mergeJsonArray(myLibs, getJsonArray("resourceIds", jsonDoc), removedResources)
        val mergedCourseIds = mergeJsonArray(myCourses, getJsonArray("courseIds", jsonDoc), removedCourses)
        return JsonObject().apply {
            addProperty("_id", sharedPreferences.getString("userId", ""))
            add("meetupIds", mergeJsonArray(myMeetups, getJsonArray("meetupIds", jsonDoc), removedResources))
            add("resourceIds", mergedResourceIds)
            add("courseIds", mergedCourseIds)
        }
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
    }
}
