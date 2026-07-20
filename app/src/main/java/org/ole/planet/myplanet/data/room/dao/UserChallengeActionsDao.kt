package org.ole.planet.myplanet.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.ole.planet.myplanet.model.UserChallengeActions

@Dao
interface UserChallengeActionsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: UserChallengeActions)

    @Query("SELECT COUNT(*) FROM user_challenge_actions WHERE userId = :userId AND actionType = :actionType")
    suspend fun countByUserAndType(userId: String, actionType: String): Int
}
