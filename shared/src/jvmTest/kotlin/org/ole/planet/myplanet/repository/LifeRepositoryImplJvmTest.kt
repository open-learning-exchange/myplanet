package org.ole.planet.myplanet.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.utils.KeyValueStore

/**
 * Runs the shared [LifeRepositoryImpl] against the real [AppDatabase] on a plain JVM, proving a
 * repository from `:shared` works with no Android framework and no Hilt — just constructors.
 */
class LifeRepositoryImplJvmTest {
    private lateinit var db: AppDatabase
    private lateinit var store: InMemoryKeyValueStore
    private lateinit var repository: LifeRepositoryImpl

    @BeforeTest
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        store = InMemoryKeyValueStore()
        repository = LifeRepositoryImpl(db.myLifeDao(), LifeCache(store, Json))
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedsOnceWithRandomUuidIds() = runTest {
        val seeded = repository.seedMyLifeIfEmpty("user-1", defaults("Calendar", "Tasks", "Notes"))

        assertEquals(listOf("Calendar", "Tasks", "Notes"), seeded.map { it.title })
        assertEquals(listOf(1, 2, 3), seeded.map { it.weight })
        assertTrue(seeded.all { it.isVisible && it.userId == "user-1" })
        assertTrue(seeded.all { UUID_V4.matches(it._id) }, seeded.map { it._id }.toString())
        assertEquals(emptyList(), repository.seedMyLifeIfEmpty("user-1", defaults("Other")))
        assertEquals(3, repository.getMyLifeByUserId("user-1").size)
    }

    @Test
    fun hidingAnItemUpdatesDashboardAndCache() = runTest {
        val seeded = repository.seedMyLifeIfEmpty("user-1", defaults("Calendar", "Tasks", "Notes"))
        val tasksId = seeded.single { it.title == "Tasks" }._id

        repository.updateVisibility(false, tasksId, "user-1")

        val dashboard = repository.getMyLifeForDashboard("user-1", defaults("Unused"))
        assertEquals(listOf("Calendar", "Notes"), dashboard.map { it.title })
        val cached = assertNotNull(store.getString("myLifeCache_user-1", null))
        assertTrue(cached.contains("\"title\":\"Tasks\"") && cached.contains("\"isVisible\":false"), cached)

        repository.updateMyLifeListOrder(repository.getMyLifeByUserId("user-1").reversed(), "user-1")

        assertEquals(listOf("Notes", "Tasks", "Calendar"), repository.getMyLifeByUserId("user-1").map { it.title })
    }

    private fun defaults(vararg titles: String): List<MyLife> =
        titles.map { MyLife("img-$it", null, it) }

    private class InMemoryKeyValueStore : KeyValueStore {
        private val values = mutableMapOf<String, Any>()

        override fun getString(key: String, defaultValue: String?): String? = values[key] as? String ?: defaultValue

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue

        override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue

        override fun getLong(key: String, defaultValue: Long): Long = values[key] as? Long ?: defaultValue

        override fun getFloat(key: String, defaultValue: Float): Float = values[key] as? Float ?: defaultValue

        override fun contains(key: String): Boolean = key in values

        override fun keys(): Set<String> = values.keys.toSet()

        override fun edit(block: KeyValueStore.Editor.() -> Unit) {
            object : KeyValueStore.Editor {
                override fun putString(key: String, value: String?) {
                    if (value == null) values.remove(key) else values[key] = value
                }

                override fun putBoolean(key: String, value: Boolean) {
                    values[key] = value
                }

                override fun putInt(key: String, value: Int) {
                    values[key] = value
                }

                override fun putLong(key: String, value: Long) {
                    values[key] = value
                }

                override fun putFloat(key: String, value: Float) {
                    values[key] = value
                }

                override fun remove(key: String) {
                    values.remove(key)
                }

                override fun clear() {
                    values.clear()
                }
            }.block()
        }
    }

    private companion object {
        val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
