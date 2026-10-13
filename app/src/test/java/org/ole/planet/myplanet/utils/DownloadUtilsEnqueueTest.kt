package org.ole.planet.myplanet.utils

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.DownloadService
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, application = Application::class)
class DownloadUtilsEnqueueTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        mockkObject(DownloadUtils)
        every { DownloadUtils.canStartForegroundService(any()) } returns true

        mockkObject(DownloadService.Companion)
        every { DownloadService.startService(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun openPriorityDownloadService_mergesPriorityKeys_andLeavesPendingKeysUntouched() {
        val prefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, setOf("a")).commit()

        DownloadUtils.openPriorityDownloadService(context, arrayListOf("b"))

        val storedPriority = prefs.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet())
        val storedPending = prefs.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, null)

        assertEquals(setOf("a", "b"), storedPriority)
        assertEquals(null, storedPending)

        verify(exactly = 1) { DownloadService.startService(context, DownloadService.PRIORITY_DOWNLOADS_KEY, false) }
    }

    @Test
    fun openDownloadService_mergesPendingKeys_andLeavesPriorityKeysUntouched() {
        val prefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, setOf("a")).commit()

        DownloadUtils.openDownloadService(context, arrayListOf("b"), true)

        val storedPending = prefs.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet())
        val storedPriority = prefs.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, null)

        assertEquals(setOf("a", "b"), storedPending)
        assertEquals(null, storedPriority)

        verify(exactly = 1) { DownloadService.startService(context, DownloadService.PENDING_DOWNLOADS_KEY, true) }
    }

    @Test
    fun openDownloadService_duplicateUrl_isNotDuplicated() {
        val prefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, setOf("a")).commit()

        DownloadUtils.openDownloadService(context, arrayListOf("a"), false)

        val storedPending = prefs.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet())
        assertEquals(setOf("a"), storedPending)
    }

    @Test
    fun openDownloadService_nullContext_doesNothing() {
        DownloadUtils.openPriorityDownloadService(null, arrayListOf("a"))
        DownloadUtils.openDownloadService(null, arrayListOf("a"), false)

        verify(exactly = 0) { DownloadService.startService(any(), any(), any()) }
    }
}
