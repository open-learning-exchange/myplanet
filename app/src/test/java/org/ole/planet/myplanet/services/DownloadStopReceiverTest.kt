package org.ole.planet.myplanet.services

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.impl.WorkManagerImpl
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.di.CoreDependenciesEntryPoint
import org.ole.planet.myplanet.di.getBroadcastService
import org.ole.planet.myplanet.model.Download
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, application = Application::class)
class DownloadStopReceiverTest {

    private lateinit var context: Application
    private val broadcastService = mockk<BroadcastService>(relaxed = true)
    private val workManager = mockk<WorkManagerImpl>(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val entryPoint = mockk<CoreDependenciesEntryPoint>()
        every { entryPoint.applicationScope() } returns CoroutineScope(Dispatchers.Unconfined)
        mockkStatic(EntryPointAccessors::class)
        every { EntryPointAccessors.fromApplication(any(), CoreDependenciesEntryPoint::class.java) } returns entryPoint
        mockkStatic("org.ole.planet.myplanet.di.ServiceDependenciesEntryPointKt")
        every { getBroadcastService(any()) } returns broadcastService
        mockkStatic(WorkManagerImpl::class)
        every { WorkManagerImpl.getInstance(any()) } returns workManager
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `stop action stops the download service and worker and closes any open progress dialog`() {
        DownloadStopReceiver().onReceive(context, Intent(DownloadStopReceiver.ACTION_STOP_DOWNLOADS))

        val stopped = shadowOf(context).nextStoppedService
        assertEquals(DownloadService::class.java.name, stopped?.component?.className)
        verify(exactly = 1) { workManager.cancelAllWorkByTag(DownloadWorker.WORK_TAG) }
        coVerify {
            broadcastService.sendBroadcast(match { intent ->
                val download = intent.getParcelableExtra<Download>("download")
                intent.action == DownloadService.MESSAGE_PROGRESS && download?.failed == true && download.message == null
            })
        }
    }

    @Test
    fun `stop action drops user-queued downloads so they don't restart with the next service start`() {
        val prefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, setOf("user-priority"))
            .putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, setOf("user-pending", "sync-pending"))
            .putStringSet(DownloadService.SYNC_DOWNLOADS_KEY, setOf("sync-pending"))
            .commit()

        DownloadStopReceiver().onReceive(context, Intent(DownloadStopReceiver.ACTION_STOP_DOWNLOADS))

        assertEquals(emptySet<String>(), prefs.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, null))
        assertEquals(setOf("sync-pending"), prefs.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, null))
    }

    @Test
    fun `unrelated actions leave the download queues alone`() {
        val prefs = context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(DownloadService.PENDING_DOWNLOADS_KEY, setOf("user-pending")).commit()

        DownloadStopReceiver().onReceive(context, Intent("some.other.action"))

        assertEquals(setOf("user-pending"), prefs.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, null))
    }

    @Test
    fun `unrelated actions are ignored`() {
        DownloadStopReceiver().onReceive(context, Intent("some.other.action"))

        assertNull(shadowOf(context).nextStoppedService)
        verify(exactly = 0) { workManager.cancelAllWorkByTag(any()) }
        coVerify(exactly = 0) { broadcastService.sendBroadcast(any()) }
    }

    @Test
    fun `stop action targets this receiver`() {
        val action = DownloadStopReceiver.stopAction(context)
        val intent = shadowOf(action.actionIntent).savedIntent

        assertEquals(DownloadStopReceiver.ACTION_STOP_DOWNLOADS, intent.action)
        assertEquals(DownloadStopReceiver::class.java.name, intent.component?.className)
    }
}
