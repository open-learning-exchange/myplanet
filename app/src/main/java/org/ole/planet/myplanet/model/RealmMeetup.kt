package org.ole.planet.myplanet.model

import org.ole.planet.myplanet.utilities.JsonUtils
import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Case
import io.realm.Realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.json.JSONArray
import org.ole.planet.myplanet.utilities.TimeUtils

open class RealmMeetup : RealmObject() {
    @PrimaryKey
    var id: String? = null
    var userId: String? = null
    var meetupId: String? = null
    var meetupIdRev: String? = null
    var title: String? = null
    var description: String? = null
    var startDate: Long = 0
    var endDate: Long = 0
    var recurring: String? = "none"
    var day: String? = null
    var startTime: String? = null
    var endTime: String? = null
    var category: String? = null
    var meetupLocation: String? = null
    var meetupLink: String? = null
    var creator: String? = null
    var link: String? = null
    var teamId: String? = null
    var createdDate: Long = 0
    var recurringNumber: Int = 10
    var sync: String? = null
    var sourcePlanet: String? = null

    companion object {
        @JvmStatic
        fun insert(mRealm: Realm, meetupDoc: JsonObject) {
            insert("", meetupDoc, mRealm)
        }

        fun insert(userId: String?, meetupDoc: JsonObject, mRealm: Realm) {
            var myMeetupsDB = mRealm.where(RealmMeetup::class.java)
                .equalTo("meetupId", JsonUtils.getString("_id", meetupDoc)).findFirst()
            if (myMeetupsDB == null) {
                myMeetupsDB = mRealm.createObject(RealmMeetup::class.java, JsonUtils.getString("_id", meetupDoc))
            }
            myMeetupsDB?.meetupId = JsonUtils.getString("_id", meetupDoc)
            myMeetupsDB?.userId = userId
            myMeetupsDB?.meetupIdRev = JsonUtils.getString("_rev", meetupDoc)
            myMeetupsDB?.title = JsonUtils.getString("title", meetupDoc)
            myMeetupsDB?.description = JsonUtils.getString("description", meetupDoc)
            myMeetupsDB?.startDate = JsonUtils.getLong("startDate", meetupDoc)
            myMeetupsDB?.endDate = JsonUtils.getLong("endDate", meetupDoc)
            myMeetupsDB?.recurring = JsonUtils.getString("recurring", meetupDoc)
            myMeetupsDB?.startTime = JsonUtils.getString("startTime", meetupDoc)
            myMeetupsDB?.endTime = JsonUtils.getString("endTime", meetupDoc)
            myMeetupsDB?.category = JsonUtils.getString("category", meetupDoc)
            myMeetupsDB?.meetupLocation = JsonUtils.getString("meetupLocation", meetupDoc)
            myMeetupsDB?.meetupLink = JsonUtils.getString("meetupLink", meetupDoc)
            myMeetupsDB?.creator = JsonUtils.getString("createdBy", meetupDoc)
            myMeetupsDB?.day = JsonUtils.getJsonArray("day", meetupDoc).toString()
            myMeetupsDB?.link = JsonUtils.getJsonObject("link", meetupDoc).toString()
            myMeetupsDB?.teamId = JsonUtils.getString("teams", JsonUtils.getJsonObject("link", meetupDoc))
        }

        @JvmStatic
        fun getMyMeetUpIds(realm: Realm?, userId: String?): JsonArray {
            val meetups = realm?.where(RealmMeetup::class.java)?.isNotEmpty("userId")
                ?.equalTo("userId", userId, Case.INSENSITIVE)?.findAll()
            val ids = JsonArray()
            for (lib in meetups ?: emptyList()) {
                ids.add(lib.meetupId)
            }
            return ids
        }

        @JvmStatic
        fun getHashMap(meetups: RealmMeetup): HashMap<String, String> {
            val map = HashMap<String, String>()
            map["Meetup Title"] = checkNull(meetups.title)
            map["Created By"] = checkNull(meetups.creator)
            map["Category"] = checkNull(meetups.category)
            try {
                map["Meetup Date"] = TimeUtils.getFormattedDate(meetups.startDate) +
                        " - " + TimeUtils.getFormattedDate(meetups.endDate)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            map["Meetup Time"] = checkNull(meetups.startTime) + " - " + checkNull(meetups.endTime)
            map["Recurring"] = checkNull(meetups.recurring)
            var recurringDays = ""
            try {
                val ar = JSONArray(meetups.day)
                for (i in 0 until ar.length()) {
                    recurringDays += ar[i].toString() + ", "
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            map["Recurring Days"] = checkNull(recurringDays)
            map["Location"] = checkNull(meetups.meetupLocation)
            map["Link"] = checkNull(meetups.meetupLink)
            map["Description"] = checkNull(meetups.description)
            return map
        }

        private fun checkNull(s: String?): String {
            return if (TextUtils.isEmpty(s)) "" else s!!
        }

        @JvmStatic
        fun serialize(meetup: RealmMeetup): JsonObject {
            val `object` = JsonObject()
            if (!meetup.meetupId.isNullOrEmpty()) `object`.addProperty("_id", meetup.meetupId)
            if (!meetup.meetupIdRev.isNullOrEmpty()) `object`.addProperty("_rev", meetup.meetupIdRev)
            `object`.addProperty("title", meetup.title)
            `object`.addProperty("description", meetup.description)
            `object`.addProperty("startDate", meetup.startDate)
            `object`.addProperty("endDate", meetup.endDate)
            `object`.addProperty("startTime", meetup.startTime)
            `object`.addProperty("endTime", meetup.endTime)
            `object`.addProperty("recurring", meetup.recurring)
            `object`.addProperty("meetupLocation", meetup.meetupLocation)
            `object`.addProperty("meetupLink", meetup.meetupLink)
            `object`.addProperty("createdBy", meetup.creator)
            `object`.addProperty("teamId", meetup.teamId)
            `object`.addProperty("category", meetup.category)
            `object`.addProperty("createdDate", meetup.createdDate)
            `object`.addProperty("recurringNumber", meetup.recurringNumber)
            `object`.addProperty("sourcePlanet", meetup.sourcePlanet)
            `object`.addProperty("sync", meetup.sync)

            if (!meetup.link.isNullOrEmpty()) {
                val linksJson = JsonUtils.gson.fromJson(meetup.link, JsonObject::class.java)
                `object`.add("link", linksJson)
            }

            return `object`
        }
    }
}
