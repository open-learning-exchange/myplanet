package org.ole.planet.myplanet.repository

import io.realm.RealmResults
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmOfflineActivity

interface DashboardRepository {
    suspend fun getMyLibrary(userId: String): List<RealmMyLibrary>
    suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>>
    suspend fun getMyTeamsFlow(userId: String): Flow<List<RealmMyTeam>>
    suspend fun getMyLife(userId: String, settings: android.content.SharedPreferences): List<RealmMyLife>
    suspend fun setUpMyLife(userId: String?, settings: android.content.SharedPreferences)
    suspend fun countLibrariesNeedingUpdate(userId: String?): Int
}
