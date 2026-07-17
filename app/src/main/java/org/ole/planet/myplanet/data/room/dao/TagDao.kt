package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.RealmTag

@Dao
interface TagDao {
    // Parent tags: name present, not attached; optionally filtered by db.
    @Query(
        "SELECT * FROM tag WHERE (:db IS NULL OR db = :db) " +
            "AND name IS NOT NULL AND name != '' AND isAttached = 0"
    )
    suspend fun getParentTags(db: String?): List<RealmTag>

    @Query("SELECT * FROM tag")
    suspend fun getAll(): List<RealmTag>

    @Query("SELECT * FROM tag WHERE db = :db AND linkId = :linkId")
    suspend fun getByDbAndLinkId(db: String, linkId: String): List<RealmTag>

    @Query("SELECT * FROM tag WHERE db = :db AND linkId IN (:linkIds)")
    suspend fun getByDbAndLinkIds(db: String, linkIds: List<String>): List<RealmTag>

    @Query("SELECT * FROM tag WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<RealmTag>

    @Query("SELECT * FROM tag WHERE name IN (:names)")
    suspend fun getByNames(names: List<String>): List<RealmTag>

    @Query("SELECT * FROM tag WHERE db = :db AND tagId IN (:tagIds)")
    suspend fun getByDbAndTagIds(db: String, tagIds: List<String>): List<RealmTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RealmTag>)
}
