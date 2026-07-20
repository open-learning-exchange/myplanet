package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.Certification

@Dao
interface CertificationDao {
    // courseIds is a JSON array string; a substring match mirrors Realm's contains("courseIds", id).
    @Query("SELECT COUNT(*) FROM certification WHERE courseIds LIKE '%' || :courseId || '%'")
    suspend fun countByCourseId(courseId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Certification>)
}
