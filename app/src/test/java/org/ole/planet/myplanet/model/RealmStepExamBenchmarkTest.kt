package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.realm.Realm
import io.realm.RealmQuery
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils

class RealmStepExamBenchmarkTest {

    private lateinit var mockRealm: Realm

    @Before
    fun setup() {
        mockRealm = mockk(relaxed = true)
        every { mockRealm.isInTransaction } returns true
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = firstArg<CharSequence?>()
            str == null || str.length == 0
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testBenchmarkInsertCourseStepsExams() {
        val jsonArray = JsonArray()
        for (i in 0 until 500) {
            val obj = JsonObject()
            val doc = JsonObject()
            doc.addProperty("_id", "exam_$i")
            doc.addProperty("name", "Exam $i")
            doc.add("questions", JsonArray())
            obj.add("doc", doc)
            jsonArray.add(obj)
        }

        // Mocking behavior
        val mockQuery = mockk<RealmQuery<RealmStepExam>>(relaxed = true)
        every { mockRealm.where(RealmStepExam::class.java) } returns mockQuery
        every { mockQuery.equalTo("id", any<String>()) } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmStepExam::class.java, any<String>()) } answers {
            RealmStepExam().apply { id = secondArg() as String }
        }

        val start = System.currentTimeMillis()

        val documentList = ArrayList<JsonObject>(jsonArray.size())
        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = JsonUtils.getJsonObject("doc", jsonDoc)
            val id = JsonUtils.getString("_id", jsonDoc)
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
            }
        }

        for (i in 0 until 10) { // run 10 times to get more pronounced time
            documentList.forEach { jsonDoc ->
                RealmStepExam.insertCourseStepsExams("", "", jsonDoc, mockRealm)
            }
        }

        val end = System.currentTimeMillis()
        println("Benchmark result: ${end - start} ms")
    }
}
