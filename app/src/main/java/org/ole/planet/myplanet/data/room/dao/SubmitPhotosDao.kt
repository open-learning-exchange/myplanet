package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.ole.planet.myplanet.model.SubmitPhotos

@Dao
interface SubmitPhotosDao {
    @Query("SELECT * FROM submit_photos WHERE uploaded = 0")
    suspend fun getUnuploaded(): List<SubmitPhotos>

    @Query("SELECT * FROM submit_photos WHERE id IN (:ids)")
    suspend fun getByIds(ids: Array<String>): List<SubmitPhotos>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: SubmitPhotos)

    @Query("UPDATE submit_photos SET uploaded = 1, _rev = :rev, _id = :remoteId WHERE id = :photoId")
    suspend fun markUploaded(photoId: String, rev: String, remoteId: String): Int

    @Transaction
    suspend fun markUploadedBatch(uploads: List<UploadedPhoto>) {
        uploads.forEach { (photoId, rev, remoteId) -> markUploaded(photoId, rev, remoteId) }
    }

    data class UploadedPhoto(val photoId: String, val rev: String, val remoteId: String)
}
