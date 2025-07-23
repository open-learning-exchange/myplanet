package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.realm.Realm
import io.realm.RealmResults
import javax.inject.Singleton
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserRepository(
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences,
        apiInterface: ApiInterface
    ): UserRepository {
        return UserRepositoryImpl(databaseService, preferences, apiInterface)
    }

    @Provides
    @Singleton
    fun provideLibraryRepository(
        databaseService: DatabaseService,
        apiInterface: ApiInterface
    ): LibraryRepository {
        return LibraryRepositoryImpl(databaseService, apiInterface)
    }

    @Provides
    @Singleton
    fun provideCourseRepository(
        databaseService: DatabaseService,
        apiInterface: ApiInterface
    ): CourseRepository {
        return CourseRepositoryImpl(databaseService, apiInterface)
    }

    @Provides
    @Singleton
    fun provideTeamRepository(
        databaseService: DatabaseService
    ): TeamRepository {
        return TeamRepositoryImpl(databaseService)
    }
}

// User Repository
interface UserRepository {
    suspend fun getUserProfile(): String?
    suspend fun saveUserData(data: String)
    fun getRealm(): Realm
    fun getCurrentUser(): RealmUserModel?
}

class UserRepositoryImpl(
    private val databaseService: DatabaseService,
    private val preferences: SharedPreferences,
    private val apiInterface: ApiInterface
) : UserRepository {

    override suspend fun getUserProfile(): String? {
        return preferences.getString("user_profile", null)
    }

    override suspend fun saveUserData(data: String) {
        preferences.edit().putString("user_profile", data).apply()
    }

    override fun getRealm(): Realm {
        return databaseService.realmInstance
    }

    override fun getCurrentUser(): RealmUserModel? {
        return databaseService.realmInstance.where(RealmUserModel::class.java).findFirst()
    }
}

// Library Repository
interface LibraryRepository {
    fun getAllLibraryItems(): List<RealmMyLibrary>
    fun getLibraryItemById(id: String): RealmMyLibrary?
    fun getOfflineLibraryItems(): List<RealmMyLibrary>
    fun getMyLibraryByUserId(realm: Realm, settings: SharedPreferences?): List<RealmMyLibrary>
    fun getLibraryList(realm: Realm, userId: String?): List<RealmMyLibrary>
    fun getAllLibraryList(realm: Realm): List<RealmMyLibrary>
}

class LibraryRepositoryImpl(
    private val databaseService: DatabaseService,
    private val apiInterface: ApiInterface
) : LibraryRepository {

    override fun getAllLibraryItems(): List<RealmMyLibrary> {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java).findAll()
    }

    override fun getLibraryItemById(id: String): RealmMyLibrary? {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("id", id)
            .findFirst()
    }

    override fun getOfflineLibraryItems(): List<RealmMyLibrary> {
        return databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("resourceOffline", true)
            .findAll()
    }

    override fun getMyLibraryByUserId(realm: Realm, settings: SharedPreferences?): List<RealmMyLibrary> {
        val libs = realm.where(RealmMyLibrary::class.java).findAll()
        val userId = settings?.getString("userId", "--")
        val ids = TeamRepositoryImpl(databaseService).getResourceIdsByUser(userId, realm)
        return libs.filter { it.userId?.contains(userId) == true || ids.contains(it.resourceId) }
    }

    override fun getLibraryList(realm: Realm, userId: String?): List<RealmMyLibrary> {
        val l = realm.where(RealmMyLibrary::class.java).equalTo("isPrivate", false).findAll()
        return getLibraries(l).filter { it.userId?.contains(userId) == true }
    }

    override fun getAllLibraryList(realm: Realm): List<RealmMyLibrary> {
        val l = realm.where(RealmMyLibrary::class.java).equalTo("resourceOffline", false).findAll()
        return getLibraries(l)
    }

    private fun getLibraries(l: RealmResults<RealmMyLibrary>): List<RealmMyLibrary> {
        val libraries = mutableListOf<RealmMyLibrary>()
        for (lib in l) {
            if (lib.needToUpdate()) {
                libraries.add(lib)
            }
        }
        return libraries
    }
}

// Course Repository
interface CourseRepository {
    fun getAllCourses(): List<RealmMyCourse>
    fun getCourseById(id: String): RealmMyCourse?
    fun getEnrolledCourses(): List<RealmMyCourse>
}

class CourseRepositoryImpl(
    private val databaseService: DatabaseService,
    private val apiInterface: ApiInterface
) : CourseRepository {

    override fun getAllCourses(): List<RealmMyCourse> {
        return databaseService.realmInstance.where(RealmMyCourse::class.java).findAll()
    }

    override fun getCourseById(id: String): RealmMyCourse? {
        return databaseService.realmInstance.where(RealmMyCourse::class.java)
            .equalTo("courseId", id)
            .findFirst()
    }

    override fun getEnrolledCourses(): List<RealmMyCourse> {
        return databaseService.realmInstance.where(RealmMyCourse::class.java)
            .equalTo("userId", getCurrentUserId())
            .findAll()
    }

    private fun getCurrentUserId(): String {
        return databaseService.realmInstance.where(RealmUserModel::class.java)
            .findFirst()?.id ?: ""
    }
}

// Team Repository
interface TeamRepository {
    fun getMyTeamsByUserId(realm: Realm, settings: SharedPreferences?): RealmResults<RealmMyTeam>
    fun getResourceIds(teamId: String?, realm: Realm): MutableList<String>
    fun getResourceIdsByUser(userId: String?, realm: Realm): MutableList<String>
}

class TeamRepositoryImpl(
    private val databaseService: DatabaseService
) : TeamRepository {

    override fun getMyTeamsByUserId(realm: Realm, settings: SharedPreferences?): RealmResults<RealmMyTeam> {
        val userId = settings?.getString("userId", "--") ?: "--"
        val list = realm.where(RealmMyTeam::class.java)
            .equalTo("userId", userId)
            .equalTo("docType", "membership")
            .findAll()
        val teamIds = list.map { it.teamId }.toTypedArray()
        return realm.where(RealmMyTeam::class.java)
            .`in`("_id", teamIds)
            .findAll()
    }

    override fun getResourceIds(teamId: String?, realm: Realm): MutableList<String> {
        val teams = realm.where(RealmMyTeam::class.java).equalTo("teamId", teamId).findAll()
        val ids = mutableListOf<String>()
        for (team in teams) {
            if (!team.resourceId.isNullOrBlank()) {
                ids.add(team.resourceId!!)
            }
        }
        return ids
    }

    override fun getResourceIdsByUser(userId: String?, realm: Realm): MutableList<String> {
        val list = realm.where(RealmMyTeam::class.java)
            .equalTo("userId", userId)
            .equalTo("docType", "membership")
            .findAll()
        val teamIds = mutableListOf<String>()
        for (team in list) {
            team.teamId?.let { teamIds.add(it) }
        }
        val l2 = realm.where(RealmMyTeam::class.java)
            .`in`("teamId", teamIds.toTypedArray())
            .equalTo("docType", "resourceLink")
            .findAll()
        val ids = mutableListOf<String>()
        for (team in l2) {
            team.resourceId?.let { ids.add(it) }
        }
        return ids
    }
}