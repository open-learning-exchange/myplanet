package org.ole.planet.myplanet.services

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

@RunWith(JUnit4::class)
class UploadManagerResourceTest {

    @Test
    fun benchmarkBatchProcessing() {
        val totalItems = 10000
        val batchSize = 1000

        // This is a simulated test of the logic structure.
        // We will try to establish baseline vs optimized structure
        val timeBaseline = measureTimeMillis {
            // baseline
            val items = (1..totalItems).toList()
            items.chunked(batchSize).forEach { batch ->
                batch.forEach { item ->
                    // simulate N+1 queries by repeating work N times
                    val query = (1..totalItems).filter { it == item }
                }
            }
        }

        val timeOptimized = measureTimeMillis {
            // optimized
            val items = (1..totalItems).toList()
            items.chunked(batchSize).forEach { batch ->
                // simulate bulk query
                val batchSet = batch.toSet()
                val query = (1..totalItems).filter { it in batchSet }.associateBy { it }
                batch.forEach { item ->
                    val specific = query[item]
                }
            }
        }

        println("Baseline time: $timeBaseline ms")
        println("Optimized time: $timeOptimized ms")
    }
}
