package org.ole.planet.myplanet.service

import android.content.Context
import android.content.SharedPreferences
import io.mockk.any
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.eq
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.utilities.SharedPrefManager

class SyncManagerTest {

    private lateinit var context: Context
    private lateinit var databaseService: DatabaseService
    private lateinit var settings: SharedPreferences
    private lateinit var apiInterface: ApiInterface
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        apiInterface = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { settings.edit() } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `start uses improved sync manager when feature flag enabled`() {
        every { settings.getBoolean(eq(SharedPrefManager.USE_IMPROVED_SYNC), any()) } returns true
        val syncManager = spyk(SyncManager(context, databaseService, settings, apiInterface), recordPrivateCalls = true)
        val improvedSyncManager = mockk<ImprovedSyncManager>(relaxed = true)

        val field = SyncManager::class.java.getDeclaredField("improvedSyncManager")
        field.isAccessible = true
        field.set(syncManager, improvedSyncManager)

        syncManager.start(listener = null, type = "download", syncTables = null)

        verify(exactly = 1) { improvedSyncManager.start(null, "download", null) }
    }

    @Test
    fun `start falls back to legacy sync when feature flag disabled`() {
        every { settings.getBoolean(eq(SharedPrefManager.USE_IMPROVED_SYNC), any()) } returns false
        val syncManager = spyk(SyncManager(context, databaseService, settings, apiInterface), recordPrivateCalls = true)

        every { syncManager["authenticateAndSync"](any<String>(), any()) } returns Unit

        syncManager.start(listener = null, type = "download", syncTables = emptyList())

        verify(exactly = 1) { syncManager["authenticateAndSync"]("download", any()) }
    }
}
