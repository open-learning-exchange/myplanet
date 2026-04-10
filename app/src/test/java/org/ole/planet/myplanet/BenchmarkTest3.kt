package org.ole.planet.myplanet

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Test
import kotlin.system.measureTimeMillis

class BenchmarkTest3 {
    @Test
    fun runBenchmark() {
        val arr = JsonArray()
        for (i in 0 until 10000) {
            val element = JsonObject()
            val doc = JsonObject()
            doc.addProperty("_id", "id_$i")
            element.add("doc", doc)
            arr.add(element)
        }

        // Warmup
        for (i in 0 until 50) {
            testOld(arr)
            testNew(arr)
        }

        val timeOld = measureTimeMillis {
            for (i in 0 until 100) {
                testOld(arr)
            }
        }

        val timeNew = measureTimeMillis {
            for (i in 0 until 100) {
                testNew(arr)
            }
        }

        println("=========================================")
        println("Old time: $timeOld ms")
        println("New time: $timeNew ms")
        println("=========================================")
    }

    private fun testOld(arr: JsonArray) {
        val docs = mutableListOf<JsonObject>()
        for (j in arr) {
            var jsonDoc = j.asJsonObject
            val doc = jsonDoc.getAsJsonObject("doc") ?: jsonDoc
            docs.add(doc)
        }
    }

    private fun testNew(arr: JsonArray) {
        val size = arr.size()
        val docs = ArrayList<JsonObject>(size)
        for (i in 0 until size) {
            var jsonDoc = arr.get(i).asJsonObject
            val doc = jsonDoc.getAsJsonObject("doc") ?: jsonDoc
            docs.add(doc)
        }
    }
}
