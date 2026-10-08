package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.addDocumentOrigin
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.toKotlinx

/**
 * Room replacement for the former `Meetup` model. Meetups are both synced (pulled from
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
            val kDoc = meetupDoc.toKotlinx().jsonObject
            val meetup = Meetup()
            meetup.id = JsonUtils.getString("_id", kDoc)
            meetup.meetupId = JsonUtils.getString("_id", kDoc)
            meetup.userId = userId
            meetup.meetupIdRev = JsonUtils.getString("_rev", kDoc)
            meetup.title = JsonUtils.getString("title", kDoc)
            meetup.description = JsonUtils.getString("description", kDoc)
            meetup.startDate = JsonUtils.getLong("startDate", kDoc)
            meetup.endDate = JsonUtils.getLong("endDate", kDoc)
            meetup.recurring = JsonUtils.getString("recurring", kDoc)
            meetup.startTime = JsonUtils.getString("startTime", kDoc)
            meetup.endTime = JsonUtils.getString("endTime", kDoc)
            meetup.category = JsonUtils.getString("category", kDoc)
            meetup.meetupLocation = JsonUtils.getString("meetupLocation", kDoc)
            meetup.meetupLink = JsonUtils.getString("meetupLink", kDoc)
            meetup.creator = JsonUtils.getString("createdBy", kDoc)
            meetup.day = JsonUtils.getJsonArray("day", kDoc).toString()
            val kLink = JsonUtils.getJsonObject("link", kDoc)
            meetup.link = kLink.toString()
            meetup.teamId = JsonUtils.getString("teams", kLink)

            if (existingMeetup != null) {
                meetup.createdDate = existingMeetup.createdDate
                meetup.recurringNumber = existingMeetup.recurringNumber
                meetup.sync = existingMeetup.sync
                meetup.sourcePlanet = existingMeetup.sourcePlanet
                meetup.updated = existingMeetup.updated
            }

            return meetup
        }

        fun getMyMeetUpIds(meetups: List<Meetup>): JsonArray = buildJsonArray {
            for (meetup in meetups) {
                add(meetup.meetupId)
            }
        }.toGson()

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
            val linksJson = if (!meetup.link.isNullOrEmpty()) {
                GsonUtils.gson.fromJson(meetup.link, JsonObject::class.java)
            } else null
            val `object` = buildJsonObject {
                if (!meetup.meetupId.isNullOrEmpty()) put("_id", meetup.meetupId)
                if (!meetup.meetupIdRev.isNullOrEmpty()) put("_rev", meetup.meetupIdRev)
                put("title", meetup.title)
                put("description", meetup.description)
                put("startDate", meetup.startDate)
                put("endDate", meetup.endDate)
                put("startTime", meetup.startTime)
                put("endTime", meetup.endTime)
                put("recurring", meetup.recurring)
                put("meetupLocation", meetup.meetupLocation)
                put("meetupLink", meetup.meetupLink)
                put("createdBy", meetup.creator)
                put("teamId", meetup.teamId)
                put("category", meetup.category)
                put("createdDate", meetup.createdDate)
                put("recurringNumber", meetup.recurringNumber)
                put("sourcePlanet", meetup.sourcePlanet)
                put("sync", meetup.sync)
                if (!meetup.link.isNullOrEmpty()) put("link", linksJson?.toKotlinx() ?: JsonNull)
            }.toGson()

            `object`.addDocumentOrigin()
            return `object`
        }
    }
}
