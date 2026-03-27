package org.ole.planet.myplanet

import org.junit.Test
import kotlin.system.measureTimeMillis

class PerformanceTest {

    @Test
    fun explainOptimization() {
        println("The current implementation loops through `findAll()`:")
        println("`val list = realm.where(RealmHealthExamination::class.java).equalTo(\"_id\", id).findAll()`")
        println("`for (p in list) { p.userId = userId }`")
        println()
        println("However, `.equalTo(\"_id\", id)` uses the primary key (`_id`), which means there is exactly ONE (or zero) record matching this query.")
        println("Using `findFirst()` avoids creating a RealmResults collection, avoids creating a RealmResults iterator, and directly retrieves the single object to update.")
        println("Alternatively, `list.setString(\"userId\", userId)` executes a native bulk update in core Realm, bypassing the JNI boundary of iterating and updating through Kotlin.")
    }
}
