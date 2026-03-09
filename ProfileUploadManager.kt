package org.ole.planet.myplanet.services

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

// Just a standalone test
fun main() = runBlocking {
    val items = (1..100).map { Pair(it.toString(), JsonObject()) }

    // N+1 Query Simulation
    val timeN1 = measureTimeMillis {
        withContext(Dispatchers.IO) {
            items.chunked(10).forEach { batch ->
                batch.forEach { (photoId, _) ->
                    // Simulate processing
                    Thread.sleep(1) // Simulate API Call

                    // Simulate N+1 query
                    val photo = simulateFindRealmSubmitPhotos(photoId)
                    if (photo != null) {
                        simulateUploadAttachment(photo)
                    }
                }
            }
        }
    }

    println("N+1 Time: $timeN1 ms")

    // Batch Simulation
    val timeBatch = measureTimeMillis {
        withContext(Dispatchers.IO) {
            items.chunked(10).forEach { batch ->
                val idsToFetch = mutableListOf<String>()
                batch.forEach { (photoId, _) ->
                    // Simulate processing
                    Thread.sleep(1) // Simulate API Call
                    if (photoId != null) {
                        idsToFetch.add(photoId)
                    }
                }

                // Simulate batched query
                val photos = simulateBatchFindRealmSubmitPhotos(idsToFetch)
                photos.forEach { photo ->
                    simulateUploadAttachment(photo)
                }
            }
        }
    }
    println("Batch Time: $timeBatch ms")
}

data class RealmSubmitPhotos(val id: String)

fun simulateFindRealmSubmitPhotos(id: String?): RealmSubmitPhotos? {
    Thread.sleep(5) // Simulate Realm query overhead per item
    return id?.let { RealmSubmitPhotos(it) }
}

fun simulateBatchFindRealmSubmitPhotos(ids: List<String>): List<RealmSubmitPhotos> {
    Thread.sleep(5 + ids.size.toLong() * 0.1.toLong()) // Simulate batched query overhead
    return ids.map { RealmSubmitPhotos(it) }
}

fun simulateUploadAttachment(photo: RealmSubmitPhotos) {
    // simulate processing
    Thread.sleep(1)
}
