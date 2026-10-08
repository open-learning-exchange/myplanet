package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.CourseActivity

@Dao
interface CourseActivityDao {
    @Query("SELECT * FROM course_activity WHERE _rev IS NULL AND type != 'sync'")
    suspend fun getPendingUploads(): List<CourseActivity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: CourseActivity)

    /** Returns the number of rows updated (0 means the local row was gone). */
    @Query("UPDATE course_activity SET _id = :remoteId, _rev = :rev WHERE id = :localId")
    suspend fun markUploaded(localId: String, remoteId: String, rev: String): Int
}
