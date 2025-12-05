package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.TransactionSyncManager
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@ExperimentalCoroutinesApi
class SyncManagerTest {

    private lateinit var syncManager: SyncManager
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockDatabaseService = mockk<DatabaseService>(relaxed = true)
    private val mockSettings = mockk<SharedPreferences>(relaxed = true)
    private val mockApiInterface = mockk<ApiInterface>(relaxed = true)
    private val mockImprovedSyncManager = mockk<Lazy<ImprovedSyncManager>>(relaxed = true)
    private val mockTransactionSyncManager = mockk<TransactionSyncManager>(relaxed = true)
    private val mockRealm = mockk<Realm>(relaxed = true)
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        syncManager = SyncManager(
            mockContext,
            mockDatabaseService,
            mockSettings,
            mockApiInterface,
            mockImprovedSyncManager,
            mockTransactionSyncManager,
            testScope
        )
        every { mockDatabaseService.realmInstance } returns mockRealm
    }

    @After
    fun tearDown() {
    }

    @Test
    fun `startFullSync should close realm instance even when exception occurs`() = runTest {
        // Arrange
        val exception = RuntimeException("Test exception")
        every { mockTransactionSyncManager.authenticate() } returns true
        every { mockDatabaseService.withRealm<Unit>(any()) } throws exception

        // Act
        syncManager.start(null, "sync")

        // Assert
        verify { mockDatabaseService.withRealm<Unit>(any()) }
    }
}
