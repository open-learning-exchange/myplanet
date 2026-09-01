package org.ole.planet.myplanet.services

import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import java.lang.reflect.Field
import javax.inject.Provider
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.FileUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadServiceOnDownloadCompleteTest {

    private lateinit var mockPreferences: SharedPreferences

    @Before
    fun setUp() {
        mockPreferences = mockk(relaxed = true)
        mockkObject(DownloadUtils)
        mockkObject(FileUtils)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setDeclaredField(fieldName: String, target: Any, value: Any?) {
        val field = DownloadService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    /**
     * Regression test for #16485: onDownloadComplete must read PRIORITY_DOWNLOADS_KEY once
     * (reusing it for both the remaining-priority count and getRemainingCount, rather than
     * having getRemainingCount re-read it) and parse the url filename once via
     * getFileNameFromUrl instead of twice.
     */
    @Test
    fun `onDownloadComplete reads priority set once and parses filename once`() {
        val service = spyk(DownloadService())

        val url = "http://example.com/resources/my%20file.pdf"
        val decodedFileName = "my file.pdf"

        val pendingSet = setOf("http://example.com/resources/other.pdf")
        val prioritySet = setOf(url)
        every {
            mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet())
        } returns prioritySet
        every {
            mockPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet())
        } returns pendingSet
        every { FileUtils.getFileNameFromUrl(any()) } returns decodedFileName

        // Replace the `by lazy` preferences delegate so the spy resolves to the mock without
        // going through the lateinit preferencesProvider (which the spy intercepts).
        val prefsField = DownloadService::class.java.getDeclaredField("preferences\$delegate")
        prefsField.isAccessible = true
        prefsField.set(service, kotlin.lazyOf(mockPreferences))
        setDeclaredField("originalDownloadUrl", service, url)
        setDeclaredField("isCurrentDownloadPriority", service, true)
        setDeclaredField("processedUrls", service, mutableSetOf<String>())
        setDeclaredField("sessionCompletedCount", service, 2)
        setDeclaredField("outputFile", service, null)

        val mockBroadcast = mockk<BroadcastService>(relaxed = true)
        coEvery { mockBroadcast.sendBroadcast(any()) } just Runs
        setDeclaredField("broadcastService", service, mockBroadcast)

        setDeclaredField("appScope", service, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        val mockNotificationManager = mockk<NotificationManager>(relaxed = true)
        setDeclaredField("notificationManager", service, mockNotificationManager)

        val mockBuilder = mockk<NotificationCompat.Builder>(relaxed = true)
        every { mockBuilder.build() } returns mockk()
        setDeclaredField("notificationBuilder", service, mockBuilder)

        val method = DownloadService::class.java.getDeclaredMethod(
            "onDownloadComplete", String::class.java, Continuation::class.java
        )
        method.isAccessible = true
        val completion = object : Continuation<Any?> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Any?>) {}
        }
        method.invoke(service, url, completion)

        // PRIORITY_DOWNLOADS_KEY should be read exactly once (used for both remainingPriority
        // and the priority portion of getRemainingCount, rather than twice).
        verify(exactly = 1) {
            mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet())
        }
        // PENDING_DOWNLOADS_KEY is still read once (inside getRemainingCount).
        verify(exactly = 1) {
            mockPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet())
        }
        // getFileNameFromUrl should be parsed exactly once per completion (used for both the
        // Download.fileName and the notification content text), not twice.
        verify(exactly = 1) { FileUtils.getFileNameFromUrl(url) }

        // The broadcast Download carries the decoded filename.
        val intentSlot = slot<Intent>()
        coVerify { mockBroadcast.sendBroadcast(capture(intentSlot)) }
        val download = intentSlot.captured.getParcelableExtra<Download>("download")
        assertEquals(decodedFileName, download?.fileName)
        assertEquals(100, download?.progress)
    }
}
