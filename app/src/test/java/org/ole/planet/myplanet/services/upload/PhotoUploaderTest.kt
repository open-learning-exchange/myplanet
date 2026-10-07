package org.ole.planet.myplanet.services.upload

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.model.SubmitPhotos
import org.ole.planet.myplanet.repository.PhotoUpload
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoUploaderTest {

    private val submissionsRepository: SubmissionsRepository = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)

    private lateinit var photoUploader: PhotoUploader

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        mockkObject(FileUtils)
        every { FileUtils.getFileNameFromUrl(any()) } answers {
            firstArg<String?>()?.substringAfterLast('/') ?: ""
        }

        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { UrlUtils.header } returns "mockHeader"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `uploadSubmitPhotos returns early when no photos to upload`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        photoUploader = PhotoUploader(
            submissionsRepository = submissionsRepository,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            scope = this,
            uploadRepository = uploadRepository
        )

        coEvery { submissionsRepository.getUnuploadedPhotos() } returns emptyList()

        val result = photoUploader.uploadSubmitPhotos(null)

        assertEquals("No photos to upload", result)
        coVerify(exactly = 0) { uploadRepository.postJson(any(), any()) }
        coVerify(exactly = 0) { submissionsRepository.markPhotosUploadedBatch(any()) }
    }

    @Test
    fun `uploadSubmitPhotos parallelizes batch POSTs bounded by semaphore and calls markPhotosUploadedBatch`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        photoUploader = PhotoUploader(
            submissionsRepository = submissionsRepository,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            scope = this,
            uploadRepository = uploadRepository
        )

        val photos = (1..10).map { i ->
            "photo-$i" to buildJsonObject { put("index", i) }
        }
        coEvery { submissionsRepository.getUnuploadedPhotos() } returns photos

        val activeRequests = AtomicInteger(0)
        var maxConcurrentRequests = 0

        coEvery { uploadRepository.postJson(any(), any()) } coAnswers {
            val current = activeRequests.incrementAndGet()
            if (current > maxConcurrentRequests) {
                maxConcurrentRequests = current
            }
            delay(10)
            activeRequests.decrementAndGet()

            val reqJson = invocation.args[1] as JsonObject
            val idx = reqJson.getValue("index").jsonPrimitive.int
            val resp = buildJsonObject {
                put("id", "doc-$idx")
                put("rev", "1-rev")
            }
            Response.success(resp)
        }

        val result = photoUploader.uploadSubmitPhotos(null)

        assertNull(result)
        assertEquals("expected the semaphore to be saturated", 6, maxConcurrentRequests)

        val expectedMarks = (1..10).map { i -> PhotoUpload("photo-$i", "1-rev", "doc-$i") }
        coVerify(exactly = 1) { submissionsRepository.markPhotosUploadedBatch(expectedMarks) }
    }

    @Test
    fun `uploadSubmitPhotos passes PhotoUpload with response id mapped to remoteId`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        photoUploader = PhotoUploader(
            submissionsRepository = submissionsRepository,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            scope = this,
            uploadRepository = uploadRepository
        )

        val photos = listOf(
            "local-photo-1" to JsonObject(emptyMap())
        )
        coEvery { submissionsRepository.getUnuploadedPhotos() } returns photos

        val responseObj = buildJsonObject {
            put("id", "remote-1")
            put("rev", "2-abc")
        }
        coEvery { uploadRepository.postJson(any(), any()) } returns Response.success(responseObj)

        val slot = slot<List<PhotoUpload>>()
        coEvery { submissionsRepository.markPhotosUploadedBatch(capture(slot)) } returns Unit

        val result = photoUploader.uploadSubmitPhotos(null)

        assertNull(result)
        val capturedList = slot.captured
        assertEquals(1, capturedList.size)
        val captured = capturedList.single()
        assertEquals("local-photo-1", captured.photoId)
        assertEquals("2-abc", captured.rev)
        assertEquals("remote-1", captured.remoteId)
    }

    @Test
    fun `uploadSubmitPhotos preserves failure isolation on exception`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        photoUploader = PhotoUploader(
            submissionsRepository = submissionsRepository,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            scope = this,
            uploadRepository = uploadRepository
        )

        val photos = listOf(
            "photo-1" to buildJsonObject { put("index", 1) },
            "photo-2" to buildJsonObject { put("index", 2) },
            "photo-3" to buildJsonObject { put("index", 3) }
        )
        coEvery { submissionsRepository.getUnuploadedPhotos() } returns photos

        coEvery { uploadRepository.postJson(any(), any()) } coAnswers {
            val reqJson = invocation.args[1] as JsonObject
            val idx = reqJson.getValue("index").jsonPrimitive.int
            if (idx == 2) {
                throw IOException("Network error for photo-2")
            }
            val resp = buildJsonObject {
                put("id", "doc-$idx")
                put("rev", "1-rev")
            }
            Response.success(resp)
        }

        val result = photoUploader.uploadSubmitPhotos(null)

        assertNull(result)

        val expectedMarks = listOf(
            PhotoUpload("photo-1", "1-rev", "doc-1"),
            PhotoUpload("photo-3", "1-rev", "doc-3")
        )
        coVerify(exactly = 1) { submissionsRepository.markPhotosUploadedBatch(expectedMarks) }
    }

    @Test
    fun `uploadSubmitPhotos invokes uploadAttachment for successful photos when listener is provided`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        photoUploader = PhotoUploader(
            submissionsRepository = submissionsRepository,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
            scope = this,
            uploadRepository = uploadRepository
        )

        val photos = listOf(
            "photo-1" to buildJsonObject { put("index", 1) }
        )
        coEvery { submissionsRepository.getUnuploadedPhotos() } returns photos

        val responseObj = buildJsonObject {
            put("id", "doc-1")
            put("rev", "1-rev")
        }
        coEvery { uploadRepository.postJson(any(), any()) } returns Response.success(responseObj)

        val submitPhoto = SubmitPhotos().apply {
            id = "photo-1"
            photoLocation = "/sdcard/photo1.jpg"
        }
        coEvery { submissionsRepository.getPhotosByIds(arrayOf("photo-1")) } returns listOf(submitPhoto)

        val mockAttachmentResp = buildJsonObject { put("ok", true) }
        coEvery { uploadRepository.uploadAttachmentJson(any(), any(), any(), any(), any()) } returns Response.success(mockAttachmentResp)

        val listener: OnSuccessListener = mockk(relaxed = true)

        val result = photoUploader.uploadSubmitPhotos(listener)

        assertNull(result)
        val expectedMarks = listOf(PhotoUpload("photo-1", "1-rev", "doc-1"))
        coVerify(exactly = 1) { submissionsRepository.markPhotosUploadedBatch(expectedMarks) }

        testScheduler.advanceUntilIdle()

        coVerify {
            uploadRepository.uploadAttachmentJson(
                file = match { it.path == "/sdcard/photo1.jpg" },
                destinationFormat = "%s/submissions/%s/%s",
                id = "doc-1",
                rev = "1-rev",
                name = "photo1.jpg"
            )
        }
    }
}
