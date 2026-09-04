package org.ole.planet.myplanet.repository

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.TimeProvider

class SyncRepositoryImplTest {

    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var timeProvider: TimeProvider
    private lateinit var repository: SyncRepositoryImpl

    private var storedStringMap = mutableMapOf<String, String>()
    private var storedLongMap = mutableMapOf<String, Long>()

    @Before
    fun setup() {
        sharedPrefManager = mockk(relaxed = true)
        timeProvider = mockk(relaxed = true)

        storedStringMap.clear()
        storedLongMap.clear()

        every { sharedPrefManager.getRawString(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<String>()
            storedStringMap[key] ?: default
        }
        every { sharedPrefManager.getRawLong(any(), any()) } answers {
            val key = firstArg<String>()
            val default = secondArg<Long>()
            storedLongMap[key] ?: default
        }
        every { sharedPrefManager.setRawString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            storedStringMap[key] = value
        }
        every { sharedPrefManager.setRawLong(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<Long>()
            storedLongMap[key] = value
        }

        repository = SyncRepositoryImpl(
            context = mockk(relaxed = true),
            apiInterface = mockk(relaxed = true),
            dispatcherProvider = mockk(relaxed = true),
            resourcesRepository = mockk(relaxed = true),
            coursesRepository = mockk(relaxed = true),
            eventsRepository = mockk(relaxed = true),
            teamsSyncRepository = mockk(relaxed = true),
            transactionSyncManager = mockk(relaxed = true),
            syncTimeLogger = mockk(relaxed = true),
            sharedPrefManager = sharedPrefManager,
            timeProvider = timeProvider
        )
    }

    @Test
    fun `getCachedShelvesWithData returns stored list when cache is within 6 hours`() {
        val now = 1000000000000L
        val cacheTime = now - (5 * 60 * 60 * 1000L) // 5 hours ago
        every { timeProvider.now() } returns now

        storedLongMap["shelves_cache_time"] = cacheTime
        storedStringMap["shelves_with_data"] = "shelf1,shelf2,shelf3"

        val result = repository.getCachedShelvesWithData()

        assertEquals(listOf("shelf1", "shelf2", "shelf3"), result)
    }

    @Test
    fun `getCachedShelvesWithData returns empty list when cache is older than 6 hours`() {
        val now = 1000000000000L
        val cacheTime = now - (6 * 60 * 60 * 1000L + 1L) // 6 hours and 1 millisecond ago
        every { timeProvider.now() } returns now

        storedLongMap["shelves_cache_time"] = cacheTime
        storedStringMap["shelves_with_data"] = "shelf1,shelf2,shelf3"

        val result = repository.getCachedShelvesWithData()

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `getCachedShelvesWithData returns empty list when cache time is not set`() {
        val now = 1000000000000L
        every { timeProvider.now() } returns now

        val result = repository.getCachedShelvesWithData()

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `cacheShelvesWithData stores comma joined string and current timestamp`() {
        val now = 1000000000000L
        every { timeProvider.now() } returns now

        val shelves = listOf("shelf_a", "shelf_b", "shelf_c")
        repository.cacheShelvesWithData(shelves)

        assertEquals("shelf_a,shelf_b,shelf_c", storedStringMap["shelves_with_data"])
        assertEquals(now, storedLongMap["shelves_cache_time"])
    }

    @Test
    fun `roundtrip caching and retrieving preserves shelf list`() {
        val now = 1000000000000L
        every { timeProvider.now() } returns now

        val inputShelves = listOf("shelf_1", "shelf_2", "shelf_3")
        repository.cacheShelvesWithData(inputShelves)

        val retrievedShelves = repository.getCachedShelvesWithData()

        assertEquals(inputShelves, retrievedShelves)
    }
}
