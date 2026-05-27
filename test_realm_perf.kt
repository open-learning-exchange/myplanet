import kotlin.system.measureTimeMillis

class RealmOfflineActivity(
    var _id: String = "",
    var loginTime: Long = 0,
    var userName: String = ""
)

fun main() {
    val documentList = mutableListOf<Map<String, Any>>()
    val existingActivitiesMap = mutableMapOf<String, RealmOfflineActivity>()
    val realmActivities = mutableListOf<RealmOfflineActivity>()

    // Simulate DB with 10,000 existing items
    for (i in 1..10000) {
        val act = RealmOfflineActivity("id_$i", 1000L, "user_$i")
        realmActivities.add(act)
        existingActivitiesMap["id_$i"] = act
    }

    // 1000 items to insert/update, 500 existing, 500 new
    for (i in 1..1000) {
        val map = mapOf(
            "_id" to "id_${if (i <= 500) i else 10000 + i}",
            "loginTime" to 1000L,
            "user" to "user_$i"
        )
        documentList.add(map)
    }

    // Old implementation behavior
    var oldTime = measureTimeMillis {
        documentList.forEach { jsonDoc ->
            val serverIdStr = jsonDoc["_id"] as String
            val loginTime = jsonDoc["loginTime"] as Long
            val userName = jsonDoc["user"] as String

            var activities = existingActivitiesMap[serverIdStr]

            // This represents the N+1 query problem!
            if (activities == null && loginTime > 0 && userName.isNotEmpty()) {
                // Mock findFirst query logic
                activities = realmActivities.firstOrNull { it.loginTime == loginTime && it.userName == userName }
            }

            if (activities == null) {
                activities = RealmOfflineActivity(serverIdStr)
                existingActivitiesMap[serverIdStr] = activities
            }

            // Update fields
            activities.loginTime = loginTime
            activities.userName = userName
        }
    }

    // New implementation behavior: batch processing
    // First, let's reset state for fair comparison
    existingActivitiesMap.clear()
    realmActivities.clear()
    for (i in 1..10000) {
        val act = RealmOfflineActivity("id_$i", 1000L, "user_$i")
        realmActivities.add(act)
        existingActivitiesMap["id_$i"] = act
    }

    var newTime = measureTimeMillis {
        // Collect all loginTime/userName pairs for items we didn't find by ID
        val missingActivities = documentList.filter { jsonDoc ->
            val serverIdStr = jsonDoc["_id"] as String
            existingActivitiesMap[serverIdStr] == null
        }

        // Batch query for them (simulating realm.where().or()...findAll())
        val loginTimeUserNamePairs = missingActivities.map {
            Pair(it["loginTime"] as Long, it["user"] as String)
        }

        // Mock Realm query matching multiple conditions
        val foundByLoginTimeAndUser = realmActivities.filter { act ->
            loginTimeUserNamePairs.any { it.first == act.loginTime && it.second == act.userName }
        }

        // Add to our existing map so we don't query again
        foundByLoginTimeAndUser.forEach { act ->
            existingActivitiesMap[act._id] = act
        }

        // Now do the inserts/updates like normal, but no DB queries inside the loop!
        documentList.forEach { jsonDoc ->
            val serverIdStr = jsonDoc["_id"] as String
            val loginTime = jsonDoc["loginTime"] as Long
            val userName = jsonDoc["user"] as String

            var activities = existingActivitiesMap[serverIdStr]

            // Look, no DB query here!
            if (activities == null && loginTime > 0 && userName.isNotEmpty()) {
                // If it wasn't found in the batch query above, it doesn't exist
                activities = foundByLoginTimeAndUser.firstOrNull { it.loginTime == loginTime && it.userName == userName }
            }

            if (activities == null) {
                activities = RealmOfflineActivity(serverIdStr)
                existingActivitiesMap[serverIdStr] = activities
            }

            // Update fields
            activities.loginTime = loginTime
            activities.userName = userName
        }
    }

    println("Old time: $oldTime ms")
    println("New time: $newTime ms")
}
