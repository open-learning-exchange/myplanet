package org.ole.planet.myplanet.services.upload

import android.util.Log
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.repository.UploadRepository
import org.ole.planet.myplanet.repository.UploadedItemResult
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class UploadCoordinatorTest {

    private val uploadRepository: UploadRepository = mockk(relaxed = true)
    private val retryQueue: RetryQueue = mockk(relaxed = true)

    private lateinit var uploadCoordinator: UploadCoordinator

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0

        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mock.url"
        every { UrlUtils.header } returns "mockHeader"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `upload parallelizes batch items bounded by semaphore and preserves result ordering`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val items = (1..10).map { i -> Submission(id = "local-$i", _id = "db-$i") }

        val activeRequests = AtomicInteger(0)
        var maxConcurrentRequests = 0

        coEvery { uploadRepository.putUpload(any(), any()) } coAnswers {
            val current = activeRequests.incrementAndGet()
            if (current > maxConcurrentRequests) {
                maxConcurrentRequests = current
            }
            delay(10)
            activeRequests.decrementAndGet()

            val url = invocation.args[0] as String
            val id = url.substringAfterLast("db-")
            val resp = JsonObject().apply {
                addProperty("id", "remote-$id")
                addProperty("rev", "1-rev")
            }
            Response.success(resp)
        }

        val config = UploadConfig(
            modelClass = Submission::class,
            endpoint = "test_endpoint",
            fetchPendingItems = { items },
            serializer = UploadSerializer.Simple { JsonObject() },
            idExtractor = { it.id },
            dbIdExtractor = { it._id },
            batchSize = 10
        )

        val result = uploadCoordinator.upload(config)

        assertTrue(result is UploadResult.Success)
        val success = result as UploadResult.Success
        assertEquals(10, success.data)
        assertEquals(10, success.items.size)

        // Verify order is strictly preserved: local-1 to local-10
        items.indices.forEach { index ->
            assertEquals("local-${index + 1}", success.items[index].localId)
            assertEquals("remote-${index + 1}", success.items[index].remoteId)
        }

        // Concurrency should saturate Semaphore limit (6)
        assertEquals("expected the semaphore to be saturated", 6, maxConcurrentRequests)
    }

    @Test
    fun `upload handles 409 conflict recovery path`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val item = Submission(id = "local-1", _id = "db-1")

        val conflictResponse = Response.error<JsonObject>(409, "".toResponseBody(null))
        coEvery { uploadRepository.putUpload(any(), any()) } returns conflictResponse

        val existingDoc = JsonObject().apply {
            addProperty("_id", "db-1")
            addProperty("_rev", "2-existing-rev")
        }
        coEvery { uploadRepository.fetchExistingDoc("http://mock.url/test_endpoint/db-1") } returns Response.success(existingDoc)

        val config = UploadConfig(
            modelClass = Submission::class,
            endpoint = "test_endpoint",
            fetchPendingItems = { listOf(item) },
            serializer = UploadSerializer.Simple { JsonObject() },
            idExtractor = { it.id },
            dbIdExtractor = { it._id }
        )

        val result = uploadCoordinator.upload(config)

        assertTrue(result is UploadResult.Success)
        val success = result as UploadResult.Success
        assertEquals(1, success.data)
        assertEquals("local-1", success.items[0].localId)
        assertEquals("db-1", success.items[0].remoteId)
        assertEquals("2-existing-rev", success.items[0].remoteRev)
    }

    @Test
    fun `upload 409 conflict recovery handles network IOException as retryable`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val item = Submission(id = "local-1", _id = "db-1")

        val conflictResponse = Response.error<JsonObject>(409, "".toResponseBody(null))
        coEvery { uploadRepository.putUpload(any(), any()) } returns conflictResponse
        coEvery { uploadRepository.fetchExistingDoc("http://mock.url/test_endpoint/db-1") } throws IOException("Network down during 409 recovery")

        val config = UploadConfig(
            modelClass = Submission::class,
            endpoint = "test_endpoint",
            fetchPendingItems = { listOf(item) },
            serializer = UploadSerializer.Simple { JsonObject() },
            idExtractor = { it.id },
            dbIdExtractor = { it._id }
        )

        val result = uploadCoordinator.upload(config)

        assertTrue(result is UploadResult.Failure)
        val failure = result as UploadResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("local-1", failure.errors[0].itemId)
        assertTrue("Error should be retryable when fetch step throws IOException", failure.errors[0].retryable)
    }

    @Test
    fun `uploadRoom parallelizes batch items bounded by semaphore and preserves result ordering`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val items = (1..10).map { i -> TestRoomItem("room-local-$i", null) }

        val activeRequests = AtomicInteger(0)
        var maxConcurrentRequests = 0

        coEvery { uploadRepository.postUpload(any(), any()) } coAnswers {
            val current = activeRequests.incrementAndGet()
            if (current > maxConcurrentRequests) {
                maxConcurrentRequests = current
            }
            delay(10)
            activeRequests.decrementAndGet()

            val reqBody = invocation.args[1] as JsonObject
            val idx = reqBody.get("idx").asInt
            val resp = JsonObject().apply {
                addProperty("id", "room-remote-$idx")
                addProperty("rev", "1-room-rev")
            }
            Response.success(resp)
        }

        val config = RoomUploadConfig(
            endpoint = "test_room_endpoint",
            modelClassName = "TestRoomItem",
            fetchPendingItems = { items },
            serializer = UploadSerializer.Simple { item ->
                JsonObject().apply {
                    addProperty("idx", item.id.substringAfterLast("room-local-").toInt())
                }
            },
            idExtractor = { it.id },
            batchSize = 10,
            markUploaded = { emptyList() }
        )

        val result = uploadCoordinator.uploadRoom(config)

        assertTrue(result is UploadResult.Success)
        val success = result as UploadResult.Success
        assertEquals(10, success.data)
        assertEquals(10, success.items.size)

        items.indices.forEach { index ->
            assertEquals("room-local-${index + 1}", success.items[index].localId)
            assertEquals("room-remote-${index + 1}", success.items[index].remoteId)
        }

        assertEquals("expected the semaphore to be saturated", 6, maxConcurrentRequests)
    }

    @Test
    fun `uploadRoom handles 409 conflict recovery path`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val item = TestRoomItem("room-local-1", null)

        val conflictResponse = Response.error<JsonObject>(409, "".toResponseBody(null))
        coEvery { uploadRepository.postUpload(any(), any()) } returns conflictResponse

        val existingDoc = JsonObject().apply {
            addProperty("_id", "room-remote-1")
            addProperty("_rev", "3-room-rev")
        }
        coEvery { uploadRepository.fetchExistingDoc("http://mock.url/test_room_endpoint/room-local-1") } returns Response.success(existingDoc)

        val config = RoomUploadConfig(
            endpoint = "test_room_endpoint",
            modelClassName = "TestRoomItem",
            fetchPendingItems = { listOf(item) },
            serializer = UploadSerializer.Simple { JsonObject() },
            idExtractor = { it.id },
            markUploaded = { emptyList() }
        )

        val result = uploadCoordinator.uploadRoom(config)

        assertTrue(result is UploadResult.Success)
        val success = result as UploadResult.Success
        assertEquals(1, success.data)
        assertEquals("room-local-1", success.items[0].localId)
        assertEquals("room-remote-1", success.items[0].remoteId)
        assertEquals("3-room-rev", success.items[0].remoteRev)
    }

    @Test
    fun `uploadRoom 409 conflict recovery handles network IOException as retryable`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val item = TestRoomItem("room-local-1", null)

        val conflictResponse = Response.error<JsonObject>(409, "".toResponseBody(null))
        coEvery { uploadRepository.postUpload(any(), any()) } returns conflictResponse
        coEvery { uploadRepository.fetchExistingDoc("http://mock.url/test_room_endpoint/room-local-1") } throws IOException("Network down during 409 recovery")

        val config = RoomUploadConfig(
            endpoint = "test_room_endpoint",
            modelClassName = "TestRoomItem",
            fetchPendingItems = { listOf(item) },
            serializer = UploadSerializer.Simple { JsonObject() },
            idExtractor = { it.id },
            markUploaded = { emptyList() }
        )

        val result = uploadCoordinator.uploadRoom(config)

        assertTrue(result is UploadResult.Failure)
        val failure = result as UploadResult.Failure
        assertEquals(1, failure.errors.size)
        assertEquals("room-local-1", failure.errors[0].itemId)
        assertTrue("Error should be retryable when fetch step throws IOException", failure.errors[0].retryable)
    }

    @Test
    fun `upload propagates cancellation when coroutine is cancelled`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val item = Submission(id = "local-1")

        coEvery { uploadRepository.postUpload(any(), any()) } coAnswers {
            delay(100)
            Response.success(JsonObject())
        }

        val config = UploadConfig(
            modelClass = Submission::class,
            endpoint = "test_endpoint",
            fetchPendingItems = { listOf(item) },
            serializer = UploadSerializer.Simple { JsonObject() },
            idExtractor = { it.id }
        )

        val job = async {
            uploadCoordinator.upload(config)
        }

        delay(10)
        job.cancel(CancellationException("Test cancellation"))

        try {
            job.await()
            assertTrue("Expected CancellationException but job completed normally", false)
        } catch (e: Exception) {
            assertTrue("Expected CancellationException but got ${e.javaClass.simpleName}", e is CancellationException)
        }
    }

    // --- retry-queue guard (folded in from #16486, fixes #16464) ---

    @Test
    fun `uploadRoom does not queue retries when all uploads succeed`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns successResponse("remote-1", "rev-1") andThen successResponse("remote-2", "rev-2")
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = roomConfig(items)
        uploadCoordinator.uploadRoom(config)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `uploadRoom does not queue retries when failures are non-retryable`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns errorResponse(404)
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = roomConfig(items)
        uploadCoordinator.uploadRoom(config)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `uploadRoom queues retries only for retryable failures`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns errorResponse(500)
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = roomConfig(items)
        uploadCoordinator.uploadRoom(config)
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
    fun `upload queues retryable failures per batch when batch 1 fails retryably and batch 2 succeeds`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val items = listOf(
            sampleItem("local-1"),
            sampleItem("local-2"),
            sampleItem("local-3"),
            sampleItem("local-4")
        )

        coEvery { uploadRepository.postUpload(any(), match { it.get("localId")?.asString in listOf("local-1", "local-2") }) } returns errorResponse(500)
        coEvery { uploadRepository.postUpload(any(), match { it.get("localId")?.asString == "local-3" }) } returns successResponse("remote-3", "rev-3")
        coEvery { uploadRepository.postUpload(any(), match { it.get("localId")?.asString == "local-4" }) } returns successResponse("remote-4", "rev-4")

        val config = legacyConfig(items).copy(batchSize = 2)
        val result = uploadCoordinator.upload(config)
        advanceUntilIdle()

        assertTrue(result is UploadResult.PartialSuccess)
        val partial = result as UploadResult.PartialSuccess
        assertEquals(2, partial.succeeded.size)
        assertEquals(2, partial.failed.size)

        coVerify(exactly = 1) {
            retryQueue.queueFailedOperation(
                uploadType = "Submission",
                error = match { it.itemId == "local-1" },
                payload = match { it.get("localId")?.asString == "local-1" },
                endpoint = "samples",
                httpMethod = "POST",
                dbId = null,
                modelClassName = "Submission"
            )
        }
        coVerify(exactly = 1) {
            retryQueue.queueFailedOperation(
                uploadType = "Submission",
                error = match { it.itemId == "local-2" },
                payload = match { it.get("localId")?.asString == "local-2" },
                endpoint = "samples",
                httpMethod = "POST",
                dbId = null,
                modelClassName = "Submission"
            )
        }
        coVerify(exactly = 0) {
            retryQueue.queueFailedOperation(
                uploadType = any(),
                error = match { it.itemId in listOf("local-3", "local-4") },
                payload = any(),
                endpoint = any(),
                httpMethod = any(),
                dbId = any(),
                modelClassName = any()
            )
        }
    }

    @Test
    fun `upload does not queue retries when failures are non-retryable`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns errorResponse(404)
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = legacyConfig(items)
        uploadCoordinator.upload(config)
        advanceUntilIdle()

        coVerify(exactly = 0) {
            retryQueue.queueFailedOperation(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `upload queues retries only for retryable failures`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        uploadCoordinator = UploadCoordinator(
            uploadRepository = uploadRepository,
            retryQueue = retryQueue,
            dispatcherProvider = TestDispatcherProvider(testDispatcher)
        )

        val items = listOf(sampleItem("local-1"), sampleItem("local-2"))
        coEvery { uploadRepository.postUpload(any(), any()) } returns errorResponse(500)
        coEvery { uploadRepository.markUploaded(any(), any()) } returns emptyList()

        val config = legacyConfig(items)
        uploadCoordinator.upload(config)
        advanceUntilIdle()

        coVerify(exactly = 2) {
            retryQueue.queueFailedOperation(
                uploadType = "Submission",
                error = any(),
                payload = any(),
                endpoint = "samples",
                httpMethod = "POST",
                dbId = null,
                modelClassName = "Submission"
            )
        }
    }

    private data class SampleItem(val id: String, val payload: JsonObject)

    private fun sampleItem(localId: String): SampleItem =
        SampleItem(localId, JsonObject().apply { addProperty("localId", localId) })

    private fun successResponse(id: String, rev: String): Response<JsonObject> =
        Response.success(JsonObject().apply {
            addProperty("id", id)
            addProperty("rev", rev)
        })

    private fun errorResponse(code: Int): Response<JsonObject> =
        Response.error(code, "error".toResponseBody(null))

    private fun roomConfig(items: List<SampleItem>) = RoomUploadConfig<SampleItem>(
        endpoint = "samples",
        modelClassName = "SampleItem",
        fetchPendingItems = { items },
        serializer = UploadSerializer.Simple { it.payload },
        idExtractor = { it.id },
        dbIdExtractor = null,
        markUploaded = { _: List<UploadedItemResult> -> emptyList() }
    )

    @Suppress("UNCHECKED_CAST")
    private fun legacyConfig(items: List<SampleItem>) = UploadConfig<SampleItem>(
        modelClass = Submission::class as kotlin.reflect.KClass<SampleItem>,
        endpoint = "samples",
        fetchPendingItems = { items },
        serializer = UploadSerializer.Simple { it.payload },
        idExtractor = { it.id },
        dbIdExtractor = null
    )

    private data class TestRoomItem(val id: String, val dbId: String?)
}
