package org.ole.planet.myplanet.services.upload

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UploadedItemResult
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class UploadCoordinatorTest {

    private val context: Context = mockk(relaxed = true)
    private val uploadRepository: UploadRepository = mockk(relaxed = true)
    private val retryQueue: RetryQueue = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var coordinator: UploadCoordinator

    private data class SampleItem(val id: String, val payload: JsonObject)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { UrlUtils.header } returns "mockHeader"
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        coordinator = UploadCoordinator(
            uploadRepository,
            context,
            retryQueue,
            TestDispatcherProvider(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun sampleItem(localId: String): SampleItem =
        SampleItem(localId, JsonObject().apply { addProperty("localId", localId) })

    private fun successResponse(id: String, rev: String): retrofit2.Response<JsonObject> =
        retrofit2.Response.success(JsonObject().apply {
            addProperty("id", id)
            addProperty("rev", rev)
        })

    private fun errorResponse(code: Int): retrofit2.Response<JsonObject> =
        retrofit2.Response.error(code, okhttp3.ResponseBody.create(null, "error"))

    @Test
    fun `uploadRoom does not queue retries when all uploads succeed`() = testScope.runTest {
        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns successResponse("remote-1", "rev-1") andThen successResponse("remote-2", "rev-2")
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = roomConfig(items)
        coordinator.uploadRoom(config)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `uploadRoom does not queue retries when failures are non-retryable`() = testScope.runTest {
        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns errorResponse(404)
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = roomConfig(items)
        coordinator.uploadRoom(config)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `uploadRoom queues retries only for retryable failures`() = testScope.runTest {
        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns errorResponse(500)
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = roomConfig(items)
        coordinator.uploadRoom(config)
        advanceUntilIdle()

        coVerify(exactly = 2) {
            retryQueue.queueFailedOperation(
                uploadType = "SampleItem",
                error = any(),
                payload = any(),
                endpoint = "samples",
                httpMethod = "POST",
                dbId = null,
                modelClassName = "SampleItem"
            )
        }
    }

    @Test
    fun `upload does not queue retries when failures are non-retryable`() = testScope.runTest {
        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns errorResponse(404)
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = legacyConfig(items)
        coordinator.upload(config)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `upload queues retries only for retryable failures`() = testScope.runTest {
        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns errorResponse(500)
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = legacyConfig(items)
        coordinator.upload(config)
        advanceUntilIdle()

        coVerify(exactly = 2) {
            retryQueue.queueFailedOperation(
                uploadType = "SampleItem",
                error = any(),
                payload = any(),
                endpoint = "samples",
                httpMethod = "POST",
                dbId = null,
                modelClassName = "SampleItem"
            )
        }
    }

    private fun roomConfig(items: List<SampleItem>) = RoomUploadConfig<SampleItem>(
        endpoint = "samples",
        modelClassName = "SampleItem",
        fetchPendingItems = { items },
        serializer = UploadSerializer.Simple { it.payload },
        idExtractor = { it.id },
        dbIdExtractor = null,
        markUploaded = { _: List<UploadedItemResult> -> emptyList() }
    )

    private fun legacyConfig(items: List<SampleItem>) = UploadConfig<SampleItem>(
        modelClass = SampleItem::class,
        endpoint = "samples",
        fetchPendingItems = { items },
        serializer = UploadSerializer.Simple { it.payload },
        idExtractor = { it.id },
        dbIdExtractor = null
    )
}
