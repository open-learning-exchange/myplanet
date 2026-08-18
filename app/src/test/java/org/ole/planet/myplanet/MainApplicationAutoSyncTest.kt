package org.ole.planet.myplanet

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.SharedPrefManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // WorkManager testing often requires newer SDKs for accurate background simulation in Robolectric
class MainApplicationAutoSyncTest {

    private lateinit var mainApplication: MainApplication
    private lateinit var mockSharedPrefManager: SharedPrefManager

    @Before
    fun setup() {
        mainApplication = ApplicationProvider.getApplicationContext()
        mockSharedPrefManager = mockk(relaxed = true)
        mainApplication.sharedPrefManager = mockSharedPrefManager

        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(mainApplication, config)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `applyAutoSyncSettings schedules work when auto sync is true and interval is valid`() {
        every { mockSharedPrefManager.getAutoSync() } returns true
        every { mockSharedPrefManager.getAutoSyncInterval() } returns 15 * 60 // 15 mins

        mainApplication.applyAutoSyncSettings()

        val workManager = WorkManager.getInstance(mainApplication)
        val workInfos = workManager.getWorkInfosForUniqueWork("autoSyncWork").get()

        assertTrue(workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)

        // Also assert that the interval is properly used by inspecting the WorkSpec if needed.
        val workManagerImpl = workManager as WorkManagerImpl
        val workSpec = workManagerImpl.workDatabase.workSpecDao().getWorkSpec(workInfos[0].id.toString())
        assertEquals((15 * 60 * 1000).toLong(), workSpec?.intervalDuration)
    }

    @Test
    fun `applyAutoSyncSettings cancels work when auto sync is false`() {
        // Enqueue some work first to simulate existing work
        every { mockSharedPrefManager.getAutoSync() } returns true
        every { mockSharedPrefManager.getAutoSyncInterval() } returns 15 * 60
        mainApplication.applyAutoSyncSettings()

        val workManager = WorkManager.getInstance(mainApplication)
        var workInfos = workManager.getWorkInfosForUniqueWork("autoSyncWork").get()
        assertTrue(workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)

        // Now change setting and apply again
        every { mockSharedPrefManager.getAutoSync() } returns false
        mainApplication.applyAutoSyncSettings()

        // Verify it's cancelled
        workInfos = workManager.getWorkInfosForUniqueWork("autoSyncWork").get()
        assertTrue(workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.CANCELLED, workInfos[0].state)
    }
}
