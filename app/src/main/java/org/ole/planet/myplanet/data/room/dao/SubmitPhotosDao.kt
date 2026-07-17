package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.RealmSubmitPhotos

@Dao
interface SubmitPhotosDao {
    @Query("SELECT * FROM submit_photos WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<RealmSubmitPhotos>

    @Query("SELECT * FROM submit_photos WHERE id IN (:ids)")
    suspend fun getByIds(ids: Array<String>): List<RealmSubmitPhotos>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: RealmSubmitPhotos)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE submit_photos SET uploaded = 1, _rev = :rev, _id = :remoteId WHERE id = :photoId")
    suspend fun markUploaded(photoId: String, rev: String, remoteId: String): Int
}
