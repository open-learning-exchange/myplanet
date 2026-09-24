package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.UserEntity

@Dao
interface MeetupDao {
    @Query("SELECT * FROM meetup WHERE teamId = :teamId")
    suspend fun getByTeamId(teamId: String): List<Meetup>

    @Query("SELECT * FROM meetup WHERE teamId IN (:teamIds)")
    suspend fun getByTeamIdsInternal(teamIds: List<String>): List<Meetup>

    suspend fun getByTeamIds(teamIds: List<String>): List<Meetup> {
        return teamIds.chunked(900).flatMap { chunk -> getByTeamIdsInternal(chunk) }
    }

    @Query("SELECT * FROM meetup WHERE meetupId = :meetupId LIMIT 1")
    suspend fun getByMeetupId(meetupId: String): Meetup?

    @Query("SELECT * FROM meetup WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Meetup?

    @Query(
        """
        SELECT DISTINCT u.* FROM users u
        INNER JOIN meetup m ON (u.id = m.userId OR u._id = m.userId)
        WHERE m.meetupId = :meetupId AND m.userId IS NOT NULL AND m.userId != ''
        """
    )
    suspend fun getJoinedMembersByMeetupId(meetupId: String): List<UserEntity>

    @Query("SELECT * FROM meetup WHERE userId = :userId AND userId != ''")
    suspend fun getByUserId(userId: String): List<Meetup>

    @Query("SELECT * FROM meetup WHERE meetupId IN (:meetupIds)")
    suspend fun getByMeetupIdsInternal(meetupIds: List<String>): List<Meetup>

    suspend fun getByMeetupIds(meetupIds: List<String>): List<Meetup> {
        if (meetupIds.isEmpty()) return emptyList()
        return meetupIds.distinct().chunked(900).flatMap { chunk -> getByMeetupIdsInternal(chunk) }
    }

    // Pending uploads: meetup was created locally (no server id yet) or was edited locally.
    @Query(
        "SELECT * FROM meetup WHERE meetupId IS NULL OR meetupId = '' OR updated = 1"
    )
    suspend fun getPendingUploads(): List<Meetup>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Meetup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Meetup>)
}
