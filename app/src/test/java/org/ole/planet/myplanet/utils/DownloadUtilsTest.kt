package org.ole.planet.myplanet.utils

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.services.DownloadService
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class DownloadUtilsTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor
    private lateinit var activityManager: ActivityManager

    @Before
    fun setup() {
        context = spyk(ApplicationProvider.getApplicationContext())

        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferences.getStringSet(any(), any()) } returns emptySet()

        activityManager = mockk(relaxed = true)
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { activityManager.getRunningServices(any()) } returns mutableListOf()

        mockkStatic(Utilities::class)
        every { Utilities.toast(any(), any()) } returns Unit

        mockkObject(DownloadService.Companion)

        mockkObject(DownloadUtils, recordPrivateCalls = true)
        every { DownloadUtils["startDownloadWork"](any<Context>(), any<String>(), any<Boolean>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testStartDownloadServiceSafely_whenCanStartForegroundService_startsService() {
        val urls = arrayListOf("url1")
        val fromSync = false

        every { DownloadUtils.canStartForegroundService(any()) } returns true
        every { DownloadService.startService(any(), any(), any()) } returns Unit

        DownloadUtils.openDownloadService(context, urls, fromSync)

        verify(exactly = 1) { DownloadService.startService(context, DownloadService.PENDING_DOWNLOADS_KEY, fromSync) }
    }

    @Test
    fun testStartDownloadServiceSafely_whenException_showsToastAndStartsWork() {
        val urls = arrayListOf("url1")
        val fromSync = false

        every { DownloadUtils.canStartForegroundService(any()) } returns true
        every { DownloadService.startService(any(), any(), any()) } throws RuntimeException("Service error")

        DownloadUtils.openDownloadService(context, urls, fromSync)

        verify(exactly = 1) { DownloadService.startService(context, DownloadService.PENDING_DOWNLOADS_KEY, fromSync) }
        verify(exactly = 1) { Utilities.toast(any(), any()) }
        verify(exactly = 1) { DownloadUtils["startDownloadWork"](context, DownloadService.PENDING_DOWNLOADS_KEY, fromSync) }
    }

    @Test
    fun testStartDownloadServiceSafely_whenExceptionFromSync_doesNotShowToastAndStartsWork() {
        val urls = arrayListOf("url1")
        val fromSync = true

        every { DownloadUtils.canStartForegroundService(any()) } returns true
        every { DownloadService.startService(any(), any(), any()) } throws RuntimeException("Service error")

        DownloadUtils.openDownloadService(context, urls, fromSync)

        verify(exactly = 1) { DownloadService.startService(context, DownloadService.PENDING_DOWNLOADS_KEY, fromSync) }
        verify(exactly = 0) { Utilities.toast(any(), any()) }
        verify(exactly = 1) { DownloadUtils["startDownloadWork"](context, DownloadService.PENDING_DOWNLOADS_KEY, fromSync) }
    }

    @Test
    fun testStartDownloadServiceSafely_whenCannotStart_showsToastAndStartsWork() {
        val urls = arrayListOf("url1")
        val fromSync = false

        every { DownloadUtils.canStartForegroundService(any()) } returns false

        DownloadUtils.openDownloadService(context, urls, fromSync)

        verify(exactly = 0) { DownloadService.startService(any(), any(), any()) }
        verify(exactly = 1) { Utilities.toast(any(), any()) }
        verify(exactly = 1) { DownloadUtils["startDownloadWork"](context, DownloadService.PENDING_DOWNLOADS_KEY, fromSync) }
    }

    @Test
    fun testStartDownloadServiceSafely_whenCannotStartFromSync_doesNotShowToast() {
        val urls = arrayListOf("url1")
        val fromSync = true

        every { DownloadUtils.canStartForegroundService(any()) } returns false

        DownloadUtils.openDownloadService(context, urls, fromSync)

        verify(exactly = 0) { DownloadService.startService(any(), any(), any()) }
        verify(exactly = 0) { Utilities.toast(any(), any()) }
        verify(exactly = 1) { DownloadUtils["startDownloadWork"](context, DownloadService.PENDING_DOWNLOADS_KEY, fromSync) }
    }

    @Test
    fun extractLinks_returnsEmptyListForNullInput() {
        val result = DownloadUtils.extractLinks(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractLinks_returnsEmptyListForEmptyInput() {
        val result = DownloadUtils.extractLinks("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractLinks_returnsEmptyListWhenNoMatchesFound() {
        val text = "This is some text without any image links."
        val result = DownloadUtils.extractLinks(text)
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractLinks_extractsSingleImageLink() {
        val text = "Check out this image: ![alt text](https://example.com/image.png)"
        val result = DownloadUtils.extractLinks(text)
        assertEquals(1, result.size)
        assertEquals("https://example.com/image.png", result[0])
    }

    @Test
    fun extractLinks_extractsMultipleImageLinks() {
        val text = "Here are two images: ![first](https://example.com/1.png) and ![second](https://example.com/2.png)"
        val result = DownloadUtils.extractLinks(text)
        assertEquals(2, result.size)
        assertEquals("https://example.com/1.png", result[0])
        assertEquals("https://example.com/2.png", result[1])
    }

    @Test
    fun extractLinks_ignoresRegularMarkdownLinks() {
        val text = "This is a [regular link](https://example.com) and this is an ![image](https://example.com/img.png)"
        val result = DownloadUtils.extractLinks(text)
        assertEquals(1, result.size)
        assertEquals("https://example.com/img.png", result[0])
    }

    @Test
    fun extractLinks_ignoresEmptyImageLinks() {
        val text = "An empty image link: ![alt]()"
        val result = DownloadUtils.extractLinks(text)
        assertTrue(result.isEmpty())
    }
}
