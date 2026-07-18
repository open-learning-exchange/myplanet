package org.ole.planet.myplanet.model

import android.text.TextUtils
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.utils.JsonUtils

class RealmStepExamBenchmarkTest {

    @Before
    fun setup() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            val str = firstArg<CharSequence?>()
            str == null || str.isEmpty()
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

        repeat(10) {
            documentList.forEach { jsonDoc ->
                RealmStepExam.insertCourseStepsExams("", "", jsonDoc)
            }
        }

        val end = System.currentTimeMillis()
        println("Benchmark result: ${end - start} ms")
    }
}
