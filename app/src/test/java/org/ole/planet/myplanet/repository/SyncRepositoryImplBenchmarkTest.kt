package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryImplBenchmarkTest {

    @Test
    fun benchmarkLoopWithAndWithoutSize() = runTest {
        val size = 1000000
        val responseRows = JsonArray()
        for (i in 0 until size) {
            val doc = JsonObject()
            doc.addProperty("test", i)
            val row = JsonObject()
            row.add("doc", doc)
            responseRows.add(row)
        }

        // Warmup
        for (j in 0 until responseRows.size()) {
            val rowObj = responseRows[j].asJsonObject
            if (rowObj.has("doc")) {
                val doc = rowObj.getAsJsonObject("doc")
            }
        }

        System.gc()
        Thread.sleep(200)

        val startTimeWith = System.nanoTime()
        for (j in 0 until responseRows.size()) {
            val rowObj = responseRows[j].asJsonObject
            if (rowObj.has("doc")) {
                val doc = rowObj.getAsJsonObject("doc")
            }
        }
        val durationWith = System.nanoTime() - startTimeWith

        System.gc()
        Thread.sleep(200)

        val startTimeWithout = System.nanoTime()
        val numRows = responseRows.size()
        for (j in 0 until numRows) {
            val rowObj = responseRows[j].asJsonObject
            if (rowObj.has("doc")) {
                val doc = rowObj.getAsJsonObject("doc")
            }
        }
        val durationWithout = System.nanoTime() - startTimeWithout

        println("Duration with responseRows.size() in loop: ${durationWith / 1000000.0} ms")
        println("Duration with extracted size: ${durationWithout / 1000000.0} ms")
    }
}
