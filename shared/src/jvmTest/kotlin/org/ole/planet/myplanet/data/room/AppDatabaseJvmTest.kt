package org.ole.planet.myplanet.data.room

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.ole.planet.myplanet.model.Rating

/** Builds the real [AppDatabase] on a plain JVM, proving the shared Room code works off Android. */
class AppDatabaseJvmTest {
    private lateinit var db: AppDatabase

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun ratingRoundTripsThroughDao() = runTest {
        val rating = Rating().apply {
            id = "r1"
            type = "resource"
            item = "lib-1"
            userId = "user-1"
            rate = 4
        }

        db.ratingDao().upsert(rating)

        val stored = assertNotNull(db.ratingDao().findById("r1"))
        assertEquals(4, stored.rate)
        assertEquals(listOf("r1"), db.ratingDao().getByType("resource").map { it.id })
    }
}
