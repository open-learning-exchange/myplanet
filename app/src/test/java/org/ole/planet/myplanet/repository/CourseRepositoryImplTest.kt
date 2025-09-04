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

class CourseRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: CourseRepositoryImpl

    @Before
    fun setup() {
        val context = Application()
        databaseService = DatabaseService(context)
        val config = RealmConfiguration.Builder()
            .inMemory()
            .name("test-realm")
            .schemaVersion(4)
            .allowWritesOnUiThread(true)
            .build()
        Realm.setDefaultConfiguration(config)
        repository = CourseRepositoryImpl(databaseService)
    }

    @After
    fun tearDown() {
        Realm.getDefaultInstance().use { it.executeTransaction { realm -> realm.deleteAll() } }
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

