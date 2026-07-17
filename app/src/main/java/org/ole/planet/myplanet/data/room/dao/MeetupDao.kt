package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.RealmMeetup

@Dao
interface MeetupDao {
    @Query("SELECT * FROM meetup WHERE teamId = :teamId")
    suspend fun getByTeamId(teamId: String): List<RealmMeetup>

    @Query("SELECT * FROM meetup WHERE meetupId = :meetupId LIMIT 1")
    suspend fun getByMeetupId(meetupId: String): RealmMeetup?

    @Query("SELECT * FROM meetup WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): RealmMeetup?

    @Query("SELECT * FROM meetup WHERE meetupId = :meetupId AND userId IS NOT NULL AND userId != ''")
    suspend fun getMembersByMeetupId(meetupId: String): List<RealmMeetup>

    @Query("SELECT * FROM meetup WHERE userId = :userId AND userId != ''")
    suspend fun getByUserId(userId: String): List<RealmMeetup>

    @Query("SELECT * FROM meetup WHERE meetupId IN (:meetupIds)")
    suspend fun getByMeetupIds(meetupIds: List<String>): List<RealmMeetup>

    // Pending uploads: meetup was created locally (no server id yet) or was edited locally.
    @Query(
        "SELECT * FROM meetup WHERE meetupId IS NULL OR meetupId = '' OR updated = 1"
    )
    suspend fun getPendingUploads(): List<RealmMeetup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RealmMeetup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RealmMeetup>)
}
