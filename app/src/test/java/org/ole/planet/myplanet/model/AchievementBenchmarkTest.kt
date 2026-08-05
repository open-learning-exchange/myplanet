package org.ole.planet.myplanet.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Test
import kotlin.system.measureTimeMillis

class AchievementBenchmarkTest {

    @Test
    fun benchmarkSetAchievements() {
        val jsonArray = JsonArray()
        for (i in 1..2000) {
            val obj = JsonObject()
            obj.addProperty("id", "id_$i")
            obj.addProperty("desc", "desc_$i")
            jsonArray.add(obj)
            // Add some duplicates
            if (i % 2 == 0) {
                jsonArray.add(obj)
            }
        }

        val achievement = Achievement()

        // Warmup
        for (i in 1..3) {
            achievement.setAchievements(jsonArray)
        }

        val time = measureTimeMillis {
            for (i in 1..10) {
                achievement.setAchievements(jsonArray)
            }
        }

        println("Time taken SetAchievements: $time ms")
    }

    @Test
    fun benchmarkSetLinks() {
        val jsonArray = JsonArray()
        for (i in 1..2000) {
            val obj = JsonObject()
            obj.addProperty("id", "id_$i")
            obj.addProperty("desc", "desc_$i")
            jsonArray.add(obj)
            // Add some duplicates
            if (i % 2 == 0) {
                jsonArray.add(obj)
            }
        }

        val achievement = Achievement()

        // Warmup
        for (i in 1..3) {
            achievement.setLinks(jsonArray)
        }

        val time = measureTimeMillis {
            for (i in 1..10) {
                achievement.setLinks(jsonArray)
            }
        }

        println("Time taken SetLinks: $time ms")
    }

    @Test
    fun benchmarkSetOtherInfo() {
        val jsonArray = JsonArray()
        for (i in 1..2000) {
            val obj = JsonObject()
            obj.addProperty("id", "id_$i")
            obj.addProperty("desc", "desc_$i")
            jsonArray.add(obj)
            // Add some duplicates
            if (i % 2 == 0) {
                jsonArray.add(obj)
            }
        }

        val achievement = Achievement()

        // Warmup
        for (i in 1..3) {
            achievement.setOtherInfo(jsonArray)
        }

        val time = measureTimeMillis {
            for (i in 1..10) {
                achievement.setOtherInfo(jsonArray)
            }
        }

        println("Time taken SetOtherInfo: $time ms")
    }

    @Test
    fun benchmarkSetReferences() {
        val jsonArray = JsonArray()
        for (i in 1..2000) {
            val obj = JsonObject()
            obj.addProperty("id", "id_$i")
            obj.addProperty("desc", "desc_$i")
            jsonArray.add(obj)
            // Add some duplicates
            if (i % 2 == 0) {
                jsonArray.add(obj)
            }
        }

        val achievement = Achievement()

        // Warmup
        for (i in 1..3) {
            achievement.setReferences(jsonArray)
        }

        val time = measureTimeMillis {
            for (i in 1..10) {
                achievement.setReferences(jsonArray)
            }
        }

        println("Time taken SetReferences: $time ms")
    }
}
