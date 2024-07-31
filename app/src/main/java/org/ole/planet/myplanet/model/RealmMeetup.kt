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
    @JvmField
    var userId: String? = null
    @JvmField
    var meetupId: String? = null
    @JvmField
    var meetupIdRev: String? = null
    @JvmField
    var title: String? = null
    @JvmField
    var description: String? = null
    @JvmField
    var startDate: Long = 0
    @JvmField
    var endDate: Long = 0
    @JvmField
    var recurring: String? = null
    @JvmField
    var day: String? = null
    @JvmField
    var startTime: String? = null
    @JvmField
    var endTime: String? = null
    @JvmField
    var category: String? = null
    @JvmField
    var meetupLocation: String? = null
    @JvmField
    var creator: String? = null
    @JvmField
    var links: String? = null
    @JvmField
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
    }
}
