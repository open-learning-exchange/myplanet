package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.RealmCourseProgress
import kotlin.system.measureTimeMillis
import org.ole.planet.myplanet.utils.DispatcherProvider
import dagger.Lazy
import kotlinx.coroutines.CoroutineDispatcher

class ProgressRepositoryImplPerfTest {
    private lateinit var repository: ProgressRepositoryImpl

    @Before
    fun setup() {
        val mockDispatcher = mockk<DispatcherProvider>(relaxed = true)
        val mockLazy = mockk<Lazy<CoursesRepository>>(relaxed = true)
        val mockCoroutineDispatcher = mockk<CoroutineDispatcher>(relaxed = true)
        repository = ProgressRepositoryImpl(mockk(relaxed = true), mockCoroutineDispatcher, mockDispatcher, mockLazy)
    }

    @Test
    fun testBulkInsertPerformance() {
        val mockRealm = mockk<Realm>(relaxed = true)
        val jsonArray = JsonArray()

        for (i in 1..1000) {
            val doc = JsonObject().apply {
                addProperty("_id", "doc$i")
                addProperty("courseId", "course${i % 10}")
                addProperty("userId", "user${i % 5}")
                addProperty("stepNum", i % 20)
                addProperty("passed", true)
            }
            jsonArray.add(JsonObject().apply { add("doc", doc) })
        }

        val mockQuery = mockk<RealmQuery<RealmCourseProgress>>(relaxed = true)
        every { mockRealm.where(RealmCourseProgress::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("courseId", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("userId", any<String>()) } returns mockQuery
        every { mockQuery.equalTo("stepNum", any<Int>()) } returns mockQuery
        every { mockQuery.beginGroup() } returns mockQuery
        every { mockQuery.isNull("_id") } returns mockQuery
        every { mockQuery.or() } returns mockQuery
        every { mockQuery.equalTo("_id", any<String>()) } returns mockQuery
        every { mockQuery.endGroup() } returns mockQuery
        every { mockQuery.findFirst() } returns null

        every { mockRealm.createObject(RealmCourseProgress::class.java, any<String>()) } returns RealmCourseProgress()

        val time = measureTimeMillis {
            repository.bulkInsertFromSync(mockRealm, jsonArray)
        }
        println("Bulk insert took $time ms")
    }
}
