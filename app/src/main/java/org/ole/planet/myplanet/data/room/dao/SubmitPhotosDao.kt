package org.ole.planet.myplanet.data.room.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Update(entity = SubmitPhotos::class)
    suspend fun markUploadedBatchInternal(updates: List<UploadUpdate>)

    @Transaction
    suspend fun markUploadedBatch(uploads: List<UploadedPhoto>) {
        val updates = uploads.map { UploadUpdate(it.photoId, it.rev, it.remoteId) }
        markUploadedBatchInternal(updates)
    }

    data class UploadedPhoto(val photoId: String, val rev: String, val remoteId: String)

    data class UploadUpdate(
        @ColumnInfo(name = "id") val id: String,
        @ColumnInfo(name = "_rev") val rev: String,
        @ColumnInfo(name = "_id") val remoteId: String,
        @ColumnInfo(name = "uploaded") val uploaded: Boolean = true
    )
}
