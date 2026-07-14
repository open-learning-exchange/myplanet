package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.junit.Test
import org.ole.planet.myplanet.model.RealmCertification

class CoursesRepositoryImplPerformanceTest {
    @Test
    fun testBulkInsertCertificationsPerformance() {
        val realm = mockk<Realm>(relaxed = true)
        val query = mockk<RealmQuery<RealmCertification>>(relaxed = true)
        val results = mockk<RealmResults<RealmCertification>>(relaxed = true)

        every { realm.where(RealmCertification::class.java) } returns query
        every { query.equalTo("_id", any<String>()) } returns query
        every { query.`in`("_id", any<Array<String>>()) } returns query
        every { query.findAll() } returns results
        every { query.findFirst() } returns null
        every { results.iterator() } returns mutableListOf<RealmCertification>().iterator()
        every { realm.createObject(RealmCertification::class.java, any<String>()) } returns RealmCertification()

        val coursesRepository = CoursesRepositoryImpl(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

        val jsonArray = JsonArray()
        for (i in 0 until 5000) {
            val doc = JsonObject()
            doc.addProperty("_id", "cert_$i")
            doc.addProperty("name", "Name $i")
            doc.add("courseIds", JsonArray())

            val wrapper = JsonObject()
            wrapper.add("doc", doc)
            jsonArray.add(wrapper)
        }

        val startTime = System.currentTimeMillis()
        coursesRepository.bulkInsertCertificationsFromSync(realm, jsonArray)
        val endTime = System.currentTimeMillis()

        println("Execution time: ${endTime - startTime} ms")
    }
}
