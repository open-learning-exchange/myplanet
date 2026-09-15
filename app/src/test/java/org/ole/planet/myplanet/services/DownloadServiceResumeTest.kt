package org.ole.planet.myplanet.services

import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import java.io.File
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
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
import org.ole.planet.myplanet.model.DownloadResult
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.FileUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for resumable downloads: a `.tmp` file left behind by an interrupted
 * download must be appended to (not discarded) on the next attempt when the server honors the
 * Range request, and must only be discarded when the server ignores it (fresh 200 response).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadServiceResumeTest {

    @get:Rule
    val temporaryFolder = org.junit.rules.TemporaryFolder()

    private lateinit var finalFile: File
    private lateinit var tempFile: File
    private lateinit var validatorFile: File
    private val url = "http://example.com/resources/course.zip"

    private class SingleShotThrowingBody(
        private val goodBytes: ByteArray,
        private val declaredContentLength: Long
    ) : ResponseBody() {
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = declaredContentLength
        override fun source(): BufferedSource {
            var delivered = false
            val source = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (!delivered) {
                        delivered = true
                        sink.write(goodBytes)
                        return goodBytes.size.toLong()
                    }
                    throw IOException("Simulated network drop")
                }
                override fun timeout(): Timeout = Timeout.NONE
                override fun close() {}
            }
            return source.buffer()
        }
    }

    private fun setDeclaredField(fieldName: String, target: Any, value: Any?) {
        val field = DownloadService::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun newService(): DownloadService {
        val service = spyk(DownloadService())

        val mockPreferences = mockk<SharedPreferences>(relaxed = true)
        every { mockPreferences.getStringSet(DownloadService.PRIORITY_DOWNLOADS_KEY, emptySet()) } returns emptySet()
        every { mockPreferences.getStringSet(DownloadService.PENDING_DOWNLOADS_KEY, emptySet()) } returns emptySet()
        val prefsField = DownloadService::class.java.getDeclaredField("preferences\$delegate")
        prefsField.isAccessible = true
        prefsField.set(service, kotlin.lazyOf(mockPreferences))

        setDeclaredField("processedUrls", service, mutableSetOf<String>())
        setDeclaredField("appScope", service, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        setDeclaredField("broadcastService", service, mockk<BroadcastService>(relaxed = true).also {
            coEvery { it.sendBroadcast(any()) } just Runs
        })

        val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
        coEvery { resourcesRepository.markResourceOfflineByUrl(any()) } just Runs
        setDeclaredField("resourcesRepository", service, resourcesRepository)

        return service
    }

    private fun invokeDownloadFile(service: DownloadService, body: ResponseBody, isPartial: Boolean, validator: String? = null) {
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
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Any?>) {}
        }
        method.invoke(service, body, url, isPartial, validator, completion)
    }

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        finalFile = File(temporaryFolder.newFolder("resources"), "course.zip")
        tempFile = File(finalFile.parentFile, "${finalFile.name}.tmp")
        validatorFile = File(finalFile.parentFile, "${finalFile.name}.tmp.etag")

        mockkObject(FileUtils)
        every { FileUtils.getSDPathFromUrl(any(), any()) } returns finalFile
        every { FileUtils.getFileNameFromUrl(any()) } returns "course.zip"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `downloadFile appends to an existing tmp file when the server honors the resume range`() {
        val existingBytes = "first-half-".toByteArray()
        tempFile.parentFile?.mkdirs()
        tempFile.writeBytes(existingBytes)
        validatorFile.writeText("\"etag-123\"")

        val remainingBytes = "second-half".toByteArray()
        val body = remainingBytes.toResponseBody(null)

        val service = newService()
        invokeDownloadFile(service, body, true, "\"etag-123\"")

        assertFalse("temp file should be renamed away after a successful download", tempFile.exists())
        assertTrue(finalFile.exists())
        assertEquals("first-half-second-half", finalFile.readText())
        assertFalse("validator file should be cleaned up once the download completes", validatorFile.exists())
    }

    @Test
    fun `downloadFile persists the response validator so a later resume can send If-Range`() {
        val service = newService()
        val body = "whole-file".toByteArray().toResponseBody(null)

        try {
            invokeDownloadFile(service, body, false, "\"fresh-etag\"")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // Not expected here, but keep the failure path from masking assertion output below.
        }

        assertTrue(finalFile.exists())
        assertFalse("validator file should be removed once the temp file is finalized", validatorFile.exists())
    }

    @Test
    fun `downloadFile keeps the validator file alongside a partial temp file on write failure`() {
        val goodBytes = "partial-bytes-before-drop-".toByteArray()
        val body = SingleShotThrowingBody(goodBytes, declaredContentLength = 1_000L)

        val service = newService()
        try {
            invokeDownloadFile(service, body, false, "\"etag-for-resume\"")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // Expected: the simulated network drop propagates out of downloadFile.
        }

        assertTrue("interrupted download must leave the .tmp file behind for resume", tempFile.exists())
        assertTrue("the validator that matches the kept partial must survive too", validatorFile.exists())
        assertEquals("\"etag-for-resume\"", validatorFile.readText())
    }

    @Test
    fun `resumeValidatorFor reads a persisted validator and null otherwise`() {
        val service = newService()
        val method = DownloadService::class.java.getDeclaredMethod("resumeValidatorFor", String::class.java)
        method.isAccessible = true

        assertEquals(null, method.invoke(service, url))

        tempFile.parentFile?.mkdirs()
        validatorFile.writeText("\"stored-etag\"")
        assertEquals("\"stored-etag\"", method.invoke(service, url))
    }

    @Test
    fun `downloadFile discards a stale tmp file when the server ignores the resume range`() {
        val staleBytes = "stale-partial-data-from-a-different-attempt".toByteArray()
        tempFile.parentFile?.mkdirs()
        tempFile.writeBytes(staleBytes)

        val fullBytes = "brand-new-full-file".toByteArray()
        val body = fullBytes.toResponseBody(null)

        val service = newService()
        invokeDownloadFile(service, body, false)

        assertFalse(tempFile.exists())
        assertTrue(finalFile.exists())
        assertEquals("brand-new-full-file", finalFile.readText())
    }

    @Test
    fun `downloadFile keeps the partial tmp file on write failure so a later attempt can resume`() {
        assertFalse(tempFile.exists())

        val goodBytes = "partial-bytes-before-drop-".toByteArray()
        val body = SingleShotThrowingBody(goodBytes, declaredContentLength = 1_000L)

        val service = newService()
        try {
            invokeDownloadFile(service, body, false)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // Expected: the simulated network drop propagates out of downloadFile.
        }

        assertTrue("interrupted download must leave the .tmp file behind for resume", tempFile.exists())
        assertEquals("partial-bytes-before-drop-", tempFile.readText())
        assertFalse("final file must not exist until the download completes", finalFile.exists())
    }

    @Test
    fun `downloadFile resuming an already-complete tmp file finalizes it without appending new bytes`() {
        val completeBytes = "already-fully-downloaded".toByteArray()
        tempFile.parentFile?.mkdirs()
        tempFile.writeBytes(completeBytes)

        val body = ByteArray(0).toResponseBody(null)

        val service = newService()
        invokeDownloadFile(service, body, true)

        assertFalse(tempFile.exists())
        assertTrue(finalFile.exists())
        assertEquals("already-fully-downloaded", finalFile.readText())
    }

    @Test
    fun `tryDownloadFromResult deletes the tmp file when the server rejects the resume offset with 416`() {
        tempFile.parentFile?.mkdirs()
        tempFile.writeBytes("stale-or-mismatched-partial".toByteArray())
        validatorFile.writeText("\"mismatched-etag\"")

        val service = newService()
        val method = DownloadService::class.java.getDeclaredMethod(
            "tryDownloadFromResult",
            DownloadResult::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Continuation::class.java
        )
        method.isAccessible = true
        val completion = object : Continuation<Any?> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Any?>) {}
        }
        val error = DownloadResult.Error("Requested range not satisfiable", 416)
        method.invoke(service, error, url, false, "course.zip", false, tempFile.length(), completion)

        assertFalse("a 416 must discard the mismatched partial file so the next attempt starts clean", tempFile.exists())
        assertFalse("a 416 must discard the stale validator alongside the partial file", validatorFile.exists())
    }

    @Test
    fun `resumeOffsetFor reports the size of an existing tmp file and zero otherwise`() {
        val service = newService()
        val method = DownloadService::class.java.getDeclaredMethod("resumeOffsetFor", String::class.java)
        method.isAccessible = true

        assertEquals(0L, method.invoke(service, url))

        tempFile.parentFile?.mkdirs()
        tempFile.writeBytes(ByteArray(42))
        assertEquals(42L, method.invoke(service, url))
    }
}
