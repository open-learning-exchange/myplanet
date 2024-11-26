package org.ole.planet.myplanet.model

import com.google.gson.*
import com.opencsv.CSVWriter
import io.realm.kotlin.Realm
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.json.JSONArray
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utilities.*
import java.io.File
import java.io.FileWriter
import java.io.IOException

class RealmMeetup : RealmObject {
    @PrimaryKey
    var id: String = ""
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

        fun insert(realm: Realm, meetupDoc: JsonObject, userId: String? = null) {
            realm.writeBlocking {
                val myMeetupsDB = query<RealmMeetup>(RealmMeetup::class, "id == $0", JsonUtils.getString("_id", meetupDoc))
                    .first()
                    .find() ?: copyToRealm(RealmMeetup().apply {
                    id = JsonUtils.getString("_id", meetupDoc)
                })

                myMeetupsDB.apply {
                    this.userId = userId
                    meetupId = JsonUtils.getString("_id", meetupDoc)
                    meetupIdRev = JsonUtils.getString("_rev", meetupDoc)
                    title = JsonUtils.getString("title", meetupDoc)
                    description = JsonUtils.getString("description", meetupDoc)
                    startDate = JsonUtils.getLong("startDate", meetupDoc)
                    endDate = JsonUtils.getLong("endDate", meetupDoc)
                    recurring = JsonUtils.getString("recurring", meetupDoc)
                    startTime = JsonUtils.getString("startTime", meetupDoc)
                    endTime = JsonUtils.getString("endTime", meetupDoc)
                    category = JsonUtils.getString("category", meetupDoc)
                    meetupLocation = JsonUtils.getString("meetupLocation", meetupDoc)
                    creator = JsonUtils.getString("createdBy", meetupDoc)
                    day = JsonUtils.getJsonArray("day", meetupDoc).toString()
                    links = JsonUtils.getJsonObject("link", meetupDoc).toString()
                    teamId = JsonUtils.getString("teams", JsonUtils.getJsonObject("link", meetupDoc))
                }

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
        }

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

        fun getMyMeetUpIds(realm: Realm, userId: String): JsonArray {
            val meetups = realm.query<RealmMeetup>(RealmMeetup::class, "userId == $0", userId).find()
            val ids = JsonArray()
            for (lib in meetups) {
                lib.meetupId?.let { ids.add(it) }
            }
            return ids
        }

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
            map["Meetup Time"] = "${checkNull(meetups.startTime)} - ${checkNull(meetups.endTime)}"
            map["Recurring"] = checkNull(meetups.recurring)
            var recurringDays = ""
            try {
                val ar = JSONArray(meetups.day)
                for (i in 0 until ar.length()) {
                    recurringDays += "${ar[i]}, "
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            map["Recurring Days"] = checkNull(recurringDays)
            map["Location"] = checkNull(meetups.meetupLocation)
            map["Description"] = checkNull(meetups.description)
            return map
        }

        fun getJoinedUserIds(realm: Realm): Array<String?> {
            val list = realm.query<RealmMeetup>(RealmMeetup::class, "userId != null").find()
            return list.mapNotNull { it.userId }.toTypedArray()
        }

        private fun checkNull(s: String?): String {
            return s ?: ""
        }

        fun meetupWriteCsv() {
            writeCsv("${MainApplication.context.getExternalFilesDir(null)}/ole/meetups.csv", meetupDataList)
        }
    }
}
