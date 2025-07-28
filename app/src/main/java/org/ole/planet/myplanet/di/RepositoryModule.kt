package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.realm.Realm
import javax.inject.Singleton
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmUserModel

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
    fun getLibraryList(userId: String?): List<RealmMyLibrary>
    fun getMyLibraries(userId: String?): List<RealmMyLibrary>
    fun getOurLibraries(userId: String?): List<RealmMyLibrary>
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

    override fun getLibraryList(userId: String?): List<RealmMyLibrary> {
        val libs = databaseService.realmInstance.where(RealmMyLibrary::class.java)
            .equalTo("isPrivate", false)
            .findAll()
        val libraryList = mutableListOf<RealmMyLibrary>()
        for (item in libs) {
            if (item.needToUpdate() && item.userId?.contains(userId) == true) {
                libraryList.add(item)
            }
        }
        return libraryList
    }

    override fun getMyLibraries(userId: String?): List<RealmMyLibrary> {
        val libs = databaseService.realmInstance.where(RealmMyLibrary::class.java).findAll()
        return RealmMyLibrary.getMyLibraryByUserId(userId, libs.toList())
    }

    override fun getOurLibraries(userId: String?): List<RealmMyLibrary> {
        val libs = databaseService.realmInstance.where(RealmMyLibrary::class.java).findAll()
        return RealmMyLibrary.getOurLibrary(userId, libs.toList())
    }
}

// Course Repository
interface CourseRepository {
    fun getAllCourses(): List<RealmMyCourse>
    fun getCourseById(id: String): RealmMyCourse?
    fun getEnrolledCourses(): List<RealmMyCourse>
    fun getMyCourses(userId: String?): List<RealmMyCourse>
    fun getOurCourses(userId: String?): List<RealmMyCourse>
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

    override fun getMyCourses(userId: String?): List<RealmMyCourse> {
        val results = databaseService.realmInstance.where(RealmMyCourse::class.java).findAll()
        return RealmMyCourse.getMyCourseByUserId(userId, results.toList())
    }

    override fun getOurCourses(userId: String?): List<RealmMyCourse> {
        val results = databaseService.realmInstance.where(RealmMyCourse::class.java).findAll()
        return RealmMyCourse.getOurCourse(userId, results.toList())
    }

    private fun getCurrentUserId(): String {
        return databaseService.realmInstance.where(RealmUserModel::class.java)
            .findFirst()?.id ?: ""
    }
}
