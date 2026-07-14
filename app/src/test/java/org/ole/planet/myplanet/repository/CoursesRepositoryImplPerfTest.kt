package org.ole.planet.myplanet.repository

import io.realm.Realm
import io.realm.RealmConfiguration
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.ole.planet.myplanet.model.RealmStepExam
import java.util.UUID
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
class CoursesRepositoryImplPerfTest {
    private lateinit var realm: Realm

    @Before
    fun setup() {
        Realm.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val config = RealmConfiguration.Builder().inMemory().name("test-realm").build()
        Realm.setDefaultConfiguration(config)
        realm = Realm.getInstance(config)

        // Add some dummy data
        realm.executeTransaction { r ->
            for (i in 1..5000) {
                val exam = r.createObject(RealmStepExam::class.java, UUID.randomUUID().toString())
                exam.stepId = "step_${i % 10000}"
            }
        }
    }

    @After
    fun tearDown() {
        realm.close()
    }

    @Test
    fun testOrVsDirectIn() {
        val stepIds = (1..5000).map { "step_$it" }

        val timeOr = measureTimeMillis {
            val allExams = mutableListOf<RealmStepExam>()
            val query = realm.where(RealmStepExam::class.java)
            stepIds.chunked(1000).forEachIndexed { index, chunk ->
                if (index > 0) query.or()
                query.`in`("stepId", chunk.toTypedArray())
            }
            allExams.addAll(query.findAll())
            println("OR strategy found ${allExams.size}")
        }

        val timeDirectIn = measureTimeMillis {
            val allExams = mutableListOf<RealmStepExam>()
            val query = realm.where(RealmStepExam::class.java)
            query.`in`("stepId", stepIds.toTypedArray())
            allExams.addAll(query.findAll())
            println("Direct IN strategy found ${allExams.size}")
        }

        val timeChunkedList = measureTimeMillis {
            val allExams = mutableListOf<RealmStepExam>()
            stepIds.chunked(1000).forEach { chunk ->
                val query = realm.where(RealmStepExam::class.java)
                query.`in`("stepId", chunk.toTypedArray())
                allExams.addAll(query.findAll())
            }
            println("Chunked list strategy found ${allExams.size}")
        }

        println("Time OR: $timeOr ms")
        println("Time Direct IN: $timeDirectIn ms")
        println("Time Chunked List: $timeChunkedList ms")
    }
}
