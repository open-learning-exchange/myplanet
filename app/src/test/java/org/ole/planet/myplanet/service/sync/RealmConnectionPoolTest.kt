package org.ole.planet.myplanet.service.sync

import android.content.Context
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.utils.instrumentation.RealmInstrumentation
import org.ole.planet.myplanet.utils.instrumentation.RealmInstrumentation.givenARealmInstance
import org.ole.planet.myplanet.utils.instrumentation.RealmInstrumentation.givenAMockDatabaseService
import org.ole.planet.myplanet.utils.instrumentation.RealmInstrumentation.givenAMockRealm
import org.ole.planet.myplanet.utils.instrumentation.TestConstants.MAX_CONNECTIONS
import org.ole.planet.myplanet.utils.instrumentation.TestConstants.TEST_TIMEOUT

@OptIn(ExperimentalCoroutinesApi::class)
class RealmConnectionPoolTest {

    private lateinit var mockContext: Context
    private lateinit var mockDatabaseService: DatabaseService
    private lateinit var testDispatcher: StandardTestDispatcher
    private lateinit var connectionPool: RealmConnectionPool
    private lateinit var mockRealm: Realm

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        mockContext = givenAMockContext()
        mockRealm = givenAMockRealm()
        mockDatabaseService = givenAMockDatabaseService(testDispatcher)
        RealmInstrumentation.init(mockContext)

        val config = RealmPoolConfig(maxConnections = MAX_CONNECTIONS)
        connectionPool = RealmConnectionPool(mockContext, mockDatabaseService, config)
    }

    @After
    fun tearDown() {
        Realm.compactRealm(Realm.getDefaultConfiguration()!!)
    }

    @Test
    fun `useRealm should complete successfully`() = runTest(testDispatcher) {
        val result = connectionPool.useRealm { realm ->
            "success"
        }
        assertEquals("success", result)
    }

    @Test
    fun `connection pool should handle multiple concurrent requests`() = runTest(testDispatcher) {
        val numConcurrentRequests = MAX_CONNECTIONS + 2
        val results = mutableListOf<String>()
        val jobs = List(numConcurrentRequests) {
            launch {
                val result = connectionPool.useRealm { realm ->
                    delay(100) // Simulate some work
                    "success"
                }
                results.add(result)
            }
        }
        jobs.joinAll()
        assertEquals(numConcurrentRequests, results.size)
        assertTrue(results.all { it == "success" })
    }

    @Test
    fun `connection pool should not exceed max connections when using advanceTimeBy`() = runTest(testDispatcher) {
        val numConcurrentRequests = MAX_CONNECTIONS + 2
        val activeConnections = mutableListOf<Realm>()

        givenARealmInstance(mockRealm) {
            val jobs = List(numConcurrentRequests) {
                launch {
                    connectionPool.useRealm { realm ->
                        activeConnections.add(realm)
                        delay(2000)
                        activeConnections.remove(realm)
                    }
                }
            }

            advanceTimeBy(100)
            assertTrue(activeConnections.size <= MAX_CONNECTIONS)
            jobs.joinAll()
        }
    }

    private fun givenAMockContext(): Context {
        return org.mockito.Mockito.mock(Context::class.java)
    }
}
