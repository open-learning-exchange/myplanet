import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.system.measureTimeMillis

fun main() {
    val arr = JsonArray()
    for (i in 0 until 100000) {
        val element = JsonObject()
        val doc = JsonObject()
        doc.addProperty("_id", "id_$i")
        element.add("doc", doc)
        arr.add(element)
    }

    // Warmup
    for (i in 0 until 10) {
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

fun testOld(arr: JsonArray) {
    val docs = mutableListOf<JsonObject>()
    for (j in arr) {
        var jsonDoc = j.asJsonObject
        val doc = jsonDoc.getAsJsonObject("doc") ?: jsonDoc
        docs.add(doc)
    }
}

fun testNew(arr: JsonArray) {
    val size = arr.size()
    val docs = ArrayList<JsonObject>(size)
    for (i in 0 until size) {
        var jsonDoc = arr.get(i).asJsonObject
        val doc = jsonDoc.getAsJsonObject("doc") ?: jsonDoc
        docs.add(doc)
    }
}
