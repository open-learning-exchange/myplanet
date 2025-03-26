package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.*
import com.opencsv.CSVWriter
import io.realm.*
import io.realm.annotations.PrimaryKey
import org.json.JSONArray
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.utilities.*
import java.io.File
import java.io.FileWriter
import java.io.IOException

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
    var recurring: String? = null
    var day: String? = null
    var startTime: String? = null
    var endTime: String? = null
    var category: String? = null
    var meetupLocation: String? = null
    var creator: String? = null
    var links: String? = null
    var teamId: String? = null

    companion object {
        private val meetupDataList: MutableList<Array<String>> = mutableListOf()

        @JvmStatic
        fun insert(mRealm: Realm, meetupDoc: JsonObject) {
            insert("", meetupDoc, mRealm)
        }

        fun insert(userId: String?, meetupDoc: JsonObject, mRealm: Realm) {
            if (!mRealm.isInTransaction) {
                mRealm.beginTransaction()
            }
            var myMeetupsDB = mRealm.where(RealmMeetup::class.java)
                .equalTo("id", JsonUtils.getString("_id", meetupDoc)).findFirst()
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
            myMeetupsDB?.creator = JsonUtils.getString("createdBy", meetupDoc)
            myMeetupsDB?.day = JsonUtils.getJsonArray("day", meetupDoc).toString()
            myMeetupsDB?.links = JsonUtils.getJsonObject("link", meetupDoc).toString()
            myMeetupsDB?.teamId = JsonUtils.getString("teams", JsonUtils.getJsonObject("link", meetupDoc))
            mRealm.commitTransaction()

            val csvRow = arrayOf(
                JsonUtils.getString("_id", meetupDoc),
                userId ?: "",
                JsonUtils.getString("_rev", meetupDoc),
                JsonUtils.getString("title", meetupDoc),
                JsonUtils.getString("description", meetupDoc),
                JsonUtils.getLong("startDate", meetupDoc).toString(),
                JsonUtils.getLong("endDate", meetupDoc).toString(),
                JsonUtils.getString("recurring", meetupDoc),
                JsonUtils.getString("startTime", meetupDoc),
                JsonUtils.getString("endTime", meetupDoc),
                JsonUtils.getString("category", meetupDoc),
                JsonUtils.getString("meetupLocation", meetupDoc),
                JsonUtils.getString("createdBy", meetupDoc),
                JsonUtils.getJsonArray("day", meetupDoc).toString(),
                JsonUtils.getJsonObject("link", meetupDoc).toString(),
                JsonUtils.getString("teams", JsonUtils.getJsonObject("link", meetupDoc))
            )
            meetupDataList.add(csvRow)
        }

        @JvmStatic
        fun writeCsv(filePath: String, data: List<Array<String>>) {
            try {
                val file = File(filePath)
                file.parentFile?.mkdirs()
                val writer = CSVWriter(FileWriter(file))
                writer.writeNext(arrayOf("meetupId", "userId", "meetupId_rev", "title", "description", "startDate", "endDate", "recurring", "startTime", "endTime", "category", "meetupLocation", "creator", "day", "links", "teamId"))
                for (row in data) {
                    writer.writeNext(row)
                }
                writer.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
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
                map["Meetup Date"] = TimeUtils.getFormatedDate(meetups.startDate) +
                        " - " + TimeUtils.getFormatedDate(meetups.endDate)
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
            map["Description"] = checkNull(meetups.description)
            return map
        }

        @JvmStatic
        fun getJoinedUserIds(mRealm: Realm): Array<String?> {
            val list: List<RealmMeetup> = mRealm.where(RealmMeetup::class.java).isNotEmpty("userId").findAll()
            val myIds = arrayOfNulls<String>(list.size)
            for (i in list.indices) {
                myIds[i] = list[i].userId
            }
            return myIds
        }

        private fun checkNull(s: String?): String {
            return if (TextUtils.isEmpty(s)) "" else s!!
        }

        @JvmStatic
        fun meetupWriteCsv() {
            writeCsv("${context.getExternalFilesDir(null)}/ole/meetups.csv", meetupDataList)
        }

        @JvmStatic
        fun serialize(meetup: RealmMeetup): JsonObject {
            val json = JsonObject()
            json.addProperty("title", meetup.title)
            json.addProperty("description", meetup.description)
            json.addProperty("startDate", meetup.startDate)
            json.addProperty("endDate", meetup.endDate)
            json.addProperty("startTime", meetup.startTime)
            json.addProperty("endTime", meetup.endTime)
            json.addProperty("recurring", meetup.recurring)
            json.addProperty("meetupLocation", meetup.meetupLocation)
            json.addProperty("creator", meetup.creator)
            json.addProperty("teamId", meetup.teamId)

            if (!meetup.links.isNullOrEmpty()) {
                val linksJson = Gson().fromJson(meetup.links, JsonObject::class.java)
                json.add("links", linksJson)
            }

            return json
        }
    }
}
