import kotlin.system.measureTimeMillis
import com.google.gson.JsonObject
import com.google.gson.JsonArray

fun main() {
    val jsonArray = JsonArray()
    for (i in 1..1000) {
        val doc = JsonObject()
        doc.addProperty("_id", "id_$i")
        doc.addProperty("_rev", "rev_$i")
        doc.addProperty("user", "user_$i")
        doc.addProperty("loginTime", 1000L)

        val wrapper = JsonObject()
        wrapper.add("doc", doc)
        jsonArray.add(wrapper)
    }

    val time = measureTimeMillis {
        val documentList = ArrayList<JsonObject>(jsonArray.size())
        val ids = mutableListOf<String>()

        for (j in jsonArray) {
            var jsonDoc = j.asJsonObject
            jsonDoc = jsonDoc.getAsJsonObject("doc") ?: jsonDoc
            val id = jsonDoc.get("_id")?.asString ?: ""
            if (!id.startsWith("_design")) {
                documentList.add(jsonDoc)
                if (id.isNotEmpty()) {
                    ids.add(id)
                }
            }
        }

        // Mock existing map check map
        val existingActivitiesMap = mutableMapOf<String, Any>()
        for (i in 1..500) {
            existingActivitiesMap["id_$i"] = Any() // 500 existing
        }

        // Mock the loop that triggers "Realm" lookups
        documentList.forEach { jsonDoc ->
            val serverIdStr = jsonDoc.get("_id")?.asString ?: ""
            var activity = existingActivitiesMap[serverIdStr]

            if (activity == null) {
                // Here is the problem in the real code, we do a findFirst instead of checking if it exists in DB once!
                // if it's null we search again, and then create
            }
        }
    }
    println("Time: $time ms")
}
