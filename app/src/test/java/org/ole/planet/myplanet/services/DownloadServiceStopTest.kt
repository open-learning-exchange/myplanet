package org.ole.planet.myplanet.services

import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.Continuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.FileUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DownloadServiceStopTest {

    @get:Rule
    val temporaryFolder = org.junit.rules.TemporaryFolder()

    private lateinit var finalFile: File
    private lateinit var tempFile: File
    private val url = "http://example.com/resources/video.mp4"
    private val broadcastService = mockk<BroadcastService>(relaxed = true)

    /** A body that never ends, like a large file on a slow link: only a stop can end the copy. */
    private class EndlessBody : ResponseBody() {
        var bytesServed = 0L
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = -1L
        override fun source(): BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                sink.write(ByteArray(1024))
                bytesServed += 1024
                return 1024
            }
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() {}
        }.buffer()
    }

    private fun setDeclaredField(fieldName: String, target: Any, value: Any?) {
        val field = DownloadService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun newService(): DownloadService {
        val service = spyk(DownloadService())
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val prefsField = DownloadService::class.java.getDeclaredField("preferences\$delegate")
        prefsField.isAccessible = true
        prefsField.set(service, kotlin.lazyOf(prefs))
        setDeclaredField("appScope", service, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        coEvery { broadcastService.sendBroadcast(any()) } just Runs
        setDeclaredField("broadcastService", service, broadcastService)
        val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
        coEvery { resourcesRepository.markResourceOfflineByUrl(any()) } just Runs
        setDeclaredField("resourcesRepository", service, resourcesRepository)
        return service
    }

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        finalFile = File(temporaryFolder.newFolder("resources"), "video.mp4")
        tempFile = File(finalFile.parentFile, "${finalFile.name}.tmp")

        mockkObject(FileUtils)
        every { FileUtils.getSDPathFromUrl(any(), any()) } returns finalFile
        every { FileUtils.getFileNameFromUrl(any()) } returns "video.mp4"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `downloadFile stops streaming once the download job is cancelled and keeps the partial file`() {
        tempFile.parentFile?.mkdirs()
        tempFile.writeBytes(ByteArray(2048))
        val body = EndlessBody()
        val stoppedJob = Job().apply { cancel() }

        val method = DownloadService::class.java.getDeclaredMethod(
            "downloadFile",
            ResponseBody::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            Continuation::class.java
        )
        method.isAccessible = true
        val completion = object : Continuation<Any?> {
            override val context = stoppedJob
            override fun resumeWith(result: Result<Any?>) {}
        }

        val thrown = try {
            method.invoke(newService(), body, url, true, null, completion)
            null
        } catch (e: InvocationTargetException) {
            e.targetException
        }

        assertTrue("a stopped download must end with cancellation, got $thrown", thrown is CancellationException)
        assertEquals("no bytes may be read after Stop", 0L, body.bytesServed)
        assertTrue("the partial file is kept so the next attempt resumes", tempFile.exists())
        assertEquals(2048L, tempFile.length())
        assertFalse("a stopped download must not be finalized", finalFile.exists())
        coVerify(exactly = 0) { broadcastService.sendBroadcast(any()) }
    }
}
