package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.Meetup

@Dao
interface MeetupDao {
    @Query("SELECT * FROM meetup WHERE teamId = :teamId AND (isDeletePending = 0 OR isDeletePending IS NULL)")
    suspend fun getByTeamId(teamId: String): List<Meetup>

    @Query("SELECT * FROM meetup WHERE meetupId = :meetupId AND (isDeletePending = 0 OR isDeletePending IS NULL) LIMIT 1")
    suspend fun getByMeetupId(meetupId: String): Meetup?

    @Query("SELECT * FROM meetup WHERE id = :id AND (isDeletePending = 0 OR isDeletePending IS NULL) LIMIT 1")
    suspend fun getById(id: String): Meetup?

    @Query("SELECT * FROM meetup WHERE (id != '' AND id = :id) OR (meetupId IS NOT NULL AND meetupId != '' AND meetupId = :id) LIMIT 1")
    suspend fun getAnyById(id: String): Meetup?

    @Query("SELECT * FROM meetup WHERE meetupId = :meetupId AND userId IS NOT NULL AND userId != '' AND (isDeletePending = 0 OR isDeletePending IS NULL)")
    suspend fun getMembersByMeetupId(meetupId: String): List<Meetup>

    @Query("SELECT * FROM meetup WHERE userId = :userId AND userId != '' AND (isDeletePending = 0 OR isDeletePending IS NULL)")
    suspend fun getByUserId(userId: String): List<Meetup>

    @Query("SELECT * FROM meetup WHERE meetupId IN (:meetupIds) AND (isDeletePending = 0 OR isDeletePending IS NULL)")
    suspend fun getByMeetupIds(meetupIds: List<String>): List<Meetup>

    @Query(
        "SELECT * FROM meetup WHERE meetupId IS NULL OR meetupId = '' OR updated = 1 OR isDeletePending = 1"
    )
    suspend fun getPendingUploads(): List<Meetup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Meetup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Meetup>)

    @Query("DELETE FROM meetup WHERE (id != '' AND id = :id) OR (meetupId IS NOT NULL AND meetupId != '' AND meetupId = :id)")
    suspend fun deleteById(id: String)
}
