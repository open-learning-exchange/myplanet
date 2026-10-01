package org.ole.planet.myplanet.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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

    companion object
}
