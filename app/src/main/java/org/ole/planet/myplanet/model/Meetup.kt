package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.json.JSONArray
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeUtils

/**
 * Room replacement for the former Realm `Meetup` model. Meetups are both synced (pulled from
 * the server) and uploaded (locally created/edited meetups). All fields are simple scalars, so no
 * type converters are required. Persistence goes through
 * [org.ole.planet.myplanet.data.room.dao.MeetupDao].
 */
@Entity(tableName = "meetup", indices = [Index("meetupId"), Index("teamId"), Index("userId")])
open class Meetup {
    @PrimaryKey
    var id: String = ""
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
    var updated: Boolean = false

    companion object {
        /**
         * Builds an unmanaged meetup from a CouchDB document. When [existingMeetup] is supplied its
         * local-only fields (created date, recurring number, sync/source metadata) are preserved.
         */
        fun fromJson(meetupDoc: JsonObject, userId: String?, existingMeetup: Meetup?): Meetup {
            val meetup = Meetup()
            meetup.id = JsonUtils.getString("_id", meetupDoc)
            meetup.meetupId = JsonUtils.getString("_id", meetupDoc)
            meetup.userId = userId
            meetup.meetupIdRev = JsonUtils.getString("_rev", meetupDoc)
            meetup.title = JsonUtils.getString("title", meetupDoc)
            meetup.description = JsonUtils.getString("description", meetupDoc)
            meetup.startDate = JsonUtils.getLong("startDate", meetupDoc)
            meetup.endDate = JsonUtils.getLong("endDate", meetupDoc)
            meetup.recurring = JsonUtils.getString("recurring", meetupDoc)
            meetup.startTime = JsonUtils.getString("startTime", meetupDoc)
            meetup.endTime = JsonUtils.getString("endTime", meetupDoc)
            meetup.category = JsonUtils.getString("category", meetupDoc)
            meetup.meetupLocation = JsonUtils.getString("meetupLocation", meetupDoc)
            meetup.meetupLink = JsonUtils.getString("meetupLink", meetupDoc)
            meetup.creator = JsonUtils.getString("createdBy", meetupDoc)
            meetup.day = JsonUtils.getJsonArray("day", meetupDoc).toString()
            meetup.link = JsonUtils.getJsonObject("link", meetupDoc).toString()
            meetup.teamId = JsonUtils.getString("teams", JsonUtils.getJsonObject("link", meetupDoc))

            if (existingMeetup != null) {
                meetup.createdDate = existingMeetup.createdDate
                meetup.recurringNumber = existingMeetup.recurringNumber
                meetup.sync = existingMeetup.sync
                meetup.sourcePlanet = existingMeetup.sourcePlanet
                meetup.updated = existingMeetup.updated
            }

            return meetup
        }

        fun getMyMeetUpIds(meetups: List<Meetup>): JsonArray {
            val ids = JsonArray()
            for (meetup in meetups) {
                ids.add(meetup.meetupId)
            }
            return ids
        }

        fun getHashMap(meetups: Meetup): HashMap<String, String> {
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
            val recurringDays = StringBuilder()
            try {
                val ar = JSONArray(meetups.day)
                for (i in 0 until ar.length()) {
                    recurringDays.append(ar[i].toString()).append(", ")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            map["Recurring Days"] = checkNull(recurringDays.toString())
            map["Location"] = checkNull(meetups.meetupLocation)
            map["Link"] = checkNull(meetups.meetupLink)
            map["Description"] = checkNull(meetups.description)
            return map
        }

        private fun checkNull(s: String?): String {
            return s.orEmpty()
        }

        fun serialize(meetup: Meetup): JsonObject {
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
