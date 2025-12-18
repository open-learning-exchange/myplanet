package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import java.util.UUID
import javax.inject.Inject

class DashboardRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val courseRepository: CourseRepository,
    private val teamRepository: TeamRepository,
    databaseService: DatabaseService
) : RealmRepository(databaseService), DashboardRepository {

    override suspend fun getMyLibrary(userId: String): List<RealmMyLibrary> {
        return libraryRepository.getMyLibrary(userId)
    }

    override suspend fun getMyCoursesFlow(userId: String): Flow<List<RealmMyCourse>> {
        return courseRepository.getMyCoursesFlow(userId)
    }

    override suspend fun getMyTeamsFlow(userId: String): Flow<List<RealmMyTeam>> {
        return teamRepository.getMyTeamsFlow(userId)
    }

    override suspend fun getMyLife(userId: String, settings: SharedPreferences): List<RealmMyLife> {
        return withRealmAsync { realmInstance ->
            val rawMylife: List<RealmMyLife> = RealmMyLife.getMyLifeByUserId(realmInstance, settings)
            rawMylife.filter { it.isVisible }.map { realmInstance.copyFromRealm(it) }
        }
    }

    override suspend fun setUpMyLife(userId: String?, settings: SharedPreferences) {
        databaseService.executeTransactionAsync { realm ->
            val realmObjects = RealmMyLife.getMyLifeByUserId(realm, settings)
            if (realmObjects.isEmpty()) {
                val myLifeListBase = getMyLifeListBase(userId)
                var ml: RealmMyLife
                var weight = 1
                for (item in myLifeListBase) {
                    ml = realm.createObject(RealmMyLife::class.java, UUID.randomUUID().toString())
                    ml.title = item.title
                    ml.imageId = item.imageId
                    ml.weight = weight
                    ml.userId = item.userId
                    ml.isVisible = true
                    weight++
                }
            }
        }
    }

    override suspend fun countLibrariesNeedingUpdate(userId: String?): Int {
        if (userId == null) return 0

        val results = queryList(RealmMyLibrary::class.java) {
            equalTo("isPrivate", false)
        }
        return filterLibrariesNeedingUpdate(results)
            .count { it.userId?.contains(userId) == true }
    }

    private fun filterLibrariesNeedingUpdate(results: Collection<RealmMyLibrary>): List<RealmMyLibrary> {
        return results.filter { it.needToUpdate() }
    }

    private fun getMyLifeListBase(userId: String?): List<RealmMyLife> {
        val myLifeList: MutableList<RealmMyLife> = ArrayList()
        myLifeList.add(RealmMyLife("ic_myhealth", userId, context.getString(R.string.myhealth)))
        myLifeList.add(RealmMyLife("my_achievement", userId, context.getString(R.string.achievements)))
        myLifeList.add(RealmMyLife("ic_submissions", userId, context.getString(R.string.submission)))
        myLifeList.add(RealmMyLife("ic_my_survey", userId, context.getString(R.string.my_survey)))
        myLifeList.add(RealmMyLife("ic_references", userId, context.getString(R.string.references)))
        myLifeList.add(RealmMyLife("ic_calendar", userId, context.getString(R.string.calendar)))
        myLifeList.add(RealmMyLife("ic_mypersonals", userId, context.getString(R.string.mypersonals)))
        return myLifeList
    }
}
