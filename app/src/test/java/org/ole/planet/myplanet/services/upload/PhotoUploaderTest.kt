package org.ole.planet.myplanet.services.upload

import android.util.Log
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
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
        coVerify(exactly = 0) { uploadRepository.postUpload(any(), any()) }
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
            "photo-$i" to JsonObject().apply { addProperty("index", i) }
        }
        coEvery { submissionsRepository.getUnuploadedPhotos() } returns photos

        val activeRequests = AtomicInteger(0)
        var maxConcurrentRequests = 0

        coEvery { uploadRepository.postUpload(any(), any()) } coAnswers {
            val current = activeRequests.incrementAndGet()
            if (current > maxConcurrentRequests) {
                maxConcurrentRequests = current
            }
            delay(10.milliseconds)
            activeRequests.decrementAndGet()

            val reqJson = invocation.args[1] as JsonObject
            val idx = reqJson.get("index").asInt
            val resp = JsonObject().apply {
                addProperty("id", "doc-$idx")
                addProperty("rev", "1-rev")
            }
            Response.success(resp)
        }

        val result = photoUploader.uploadSubmitPhotos(null)

        assertEquals("Uploaded 10 of 10 photos", result)
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
            "local-photo-1" to JsonObject()
        )
        coEvery { submissionsRepository.getUnuploadedPhotos() } returns photos

        val responseObj = JsonObject().apply {
            addProperty("id", "remote-1")
            addProperty("rev", "2-abc")
        }
        coEvery { uploadRepository.postUpload(any(), any()) } returns Response.success(responseObj)

        val slot = slot<List<PhotoUpload>>()
        coEvery { submissionsRepository.markPhotosUploadedBatch(capture(slot)) } returns Unit

        val result = photoUploader.uploadSubmitPhotos(null)

        assertEquals("Uploaded 1 of 1 photos", result)
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
            "photo-1" to JsonObject().apply { addProperty("index", 1) },
            "photo-2" to JsonObject().apply { addProperty("index", 2) },
            "photo-3" to JsonObject().apply { addProperty("index", 3) }
        )
        coEvery { submissionsRepository.getUnuploadedPhotos() } returns photos

        coEvery { uploadRepository.postUpload(any(), any()) } coAnswers {
            val reqJson = invocation.args[1] as JsonObject
            val idx = reqJson.get("index").asInt
            if (idx == 2) {
                throw IOException("Network error for photo-2")
            }
            val resp = JsonObject().apply {
                addProperty("id", "doc-$idx")
                addProperty("rev", "1-rev")
            }
            Response.success(resp)
        }

        val result = photoUploader.uploadSubmitPhotos(null)

        assertEquals("Uploaded 2 of 3 photos", result)

        val expectedMarks = listOf(
            PhotoUpload("photo-1", "1-rev", "doc-1"),
            PhotoUpload("photo-3", "1-rev", "doc-3")
        )
        coVerify(exactly = 1) { submissionsRepository.markPhotosUploadedBatch(expectedMarks) }
    }

    private fun newUploader(testScope: kotlinx.coroutines.test.TestScope): PhotoUploader = PhotoUploader(
        submissionsRepository = submissionsRepository,
        dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScope.testScheduler)),
        scope = testScope,
        uploadRepository = uploadRepository
    )

    private fun photoFile(): File = File.createTempFile("photo", ".jpg").apply { deleteOnExit() }

    private fun okAttachment(rev: String) = Response.success(JsonObject().apply {
        addProperty("ok", true)
        addProperty("id", "doc-1")
        addProperty("rev", rev)
    })

    @Test
    fun `uploadSubmitPhotos uploads the image before marking the photo uploaded, even without a listener`() = runTest {
        photoUploader = newUploader(this)
        val file = photoFile()

        coEvery { submissionsRepository.getUnuploadedPhotos() } returns listOf("photo-1" to JsonObject())
        coEvery { submissionsRepository.getPhotosByIds(arrayOf("photo-1")) } returns listOf(
            SubmitPhotos().apply { id = "photo-1"; photoLocation = file.path }
        )
        coEvery { uploadRepository.postUpload(any(), any()) } returns Response.success(JsonObject().apply {
            addProperty("id", "doc-1")
            addProperty("rev", "1-rev")
        })
        coEvery { uploadRepository.uploadAttachment(any(), any(), any(), any(), any()) } returns okAttachment("2-rev")

        val result = photoUploader.uploadSubmitPhotos(null)

        assertEquals("Uploaded 1 of 1 photos", result)
        coVerifyOrder {
            submissionsRepository.recordPhotoRemoteDoc("photo-1", "1-rev", "doc-1")
            uploadRepository.uploadAttachment(
                file = match { it.path == file.path },
                destinationFormat = "%s/submissions/%s/%s",
                id = "doc-1",
                rev = "1-rev",
                name = file.name
            )
            submissionsRepository.markPhotosUploadedBatch(listOf(PhotoUpload("photo-1", "2-rev", "doc-1")))
        }
    }

    @Test
    fun `uploadSubmitPhotos leaves photo pending when the image upload fails`() = runTest {
        photoUploader = newUploader(this)
        val file = photoFile()

        coEvery { submissionsRepository.getUnuploadedPhotos() } returns listOf("photo-1" to JsonObject())
        coEvery { submissionsRepository.getPhotosByIds(arrayOf("photo-1")) } returns listOf(
            SubmitPhotos().apply { id = "photo-1"; photoLocation = file.path }
        )
        coEvery { uploadRepository.postUpload(any(), any()) } returns Response.success(JsonObject().apply {
            addProperty("id", "doc-1")
            addProperty("rev", "1-rev")
        })
        coEvery { uploadRepository.uploadAttachment(any(), any(), any(), any(), any()) } throws IOException("network dropped")

        val result = photoUploader.uploadSubmitPhotos(null)

        assertEquals("Uploaded 0 of 1 photos", result)
        coVerify { submissionsRepository.recordPhotoRemoteDoc("photo-1", "1-rev", "doc-1") }
        coVerify(exactly = 0) { submissionsRepository.markPhotosUploadedBatch(any()) }
    }

    @Test
    fun `uploadSubmitPhotos re-sends only the image when the doc was created on an earlier run`() = runTest {
        photoUploader = newUploader(this)
        val file = photoFile()

        coEvery { submissionsRepository.getUnuploadedPhotos() } returns listOf("photo-1" to JsonObject())
        coEvery { submissionsRepository.getPhotosByIds(arrayOf("photo-1")) } returns listOf(
            SubmitPhotos().apply {
                id = "photo-1"
                _id = "doc-1"
                _rev = "1-rev"
                photoLocation = file.path
            }
        )
        coEvery { uploadRepository.uploadAttachment(any(), any(), any(), any(), any()) } returns okAttachment("2-rev")

        photoUploader.uploadSubmitPhotos(null)

        coVerify(exactly = 0) { uploadRepository.postUpload(any(), any()) }
        coVerify { uploadRepository.uploadAttachment(any(), any(), "doc-1", "1-rev", any()) }
        coVerify { submissionsRepository.markPhotosUploadedBatch(listOf(PhotoUpload("photo-1", "2-rev", "doc-1"))) }
    }
}
