package org.ole.planet.myplanet.repository

import android.app.Application
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import java.io.File
import java.nio.file.Files

class CourseRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: CourseRepositoryImpl
    private lateinit var tempDir: File
    private lateinit var realmConfig: RealmConfiguration

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("realm-test-").toFile().apply {
            deleteOnExit()
        }
        val context = object : Application() {
            override fun getFilesDir(): File = tempDir
        }
        databaseService = DatabaseService(context)
        realmConfig = RealmConfiguration.Builder()
            .inMemory()
            .name("test-realm")
            .schemaVersion(4)
            .allowWritesOnUiThread(true)
            .build()
        Realm.setDefaultConfiguration(realmConfig)
        repository = CourseRepositoryImpl(databaseService)
    }

    @After
    fun tearDown() {
        Realm.getInstance(realmConfig).use { realm ->
            realm.executeTransaction { it.deleteAll() }
        }
        tempDir.deleteRecursively()
    }

    @Test
    fun getAllCourses_returnsAllInsertedCourses() = runBlocking {
        databaseService.executeTransactionAsync { realm ->
            val course1 = RealmMyCourse().apply {
                id = "1"
                courseId = "c1"
                courseTitle = "Course 1"
            }
            val course2 = RealmMyCourse().apply {
                id = "2"
                courseId = "c2"
                courseTitle = "Course 2"
            }
            realm.copyToRealmOrUpdate(course1)
            realm.copyToRealmOrUpdate(course2)
        }

        val courses = repository.getAllCourses()
        assertEquals(2, courses.size)
        assertEquals(setOf("c1", "c2"), courses.map { it.courseId }.toSet())
    }
}

