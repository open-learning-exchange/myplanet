package org.ole.planet.myplanet.services.upload

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.UploadRepository
import retrofit2.Response

class BulkDocsUploaderTest {

    private val uploadRepository: UploadRepository = mockk()
    private val url = "http://mock.url/things/_bulk_docs"

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `upload does nothing when items are empty`() = runTest {
        var calls = 0
        BulkDocsUploader.upload(uploadRepository, url, emptyList<Pair<String, JsonObject>>()) { _, _ -> calls++ }

        assertEquals(0, calls)
        coVerify(exactly = 0) { uploadRepository.postJsonArray(any(), any()) }
    }

    @Test
    fun `upload posts all serialized docs as one bulk request`() = runTest {
        val docA = buildJsonObject { put("id", "a") }
        val docB = buildJsonObject { put("id", "b") }
        coEvery { uploadRepository.postJsonArray(url, any()) } returns Response.success(JsonArray(emptyList()))

        BulkDocsUploader.upload(uploadRepository, url, listOf("a" to docA, "b" to docB)) { _, _ -> }

        coVerify(exactly = 1) {
            uploadRepository.postJsonArray(url, match { payload ->
                payload.getValue("docs").jsonArray.size == 2
            })
        }
    }

    @Test
    fun `upload reports Accepted for a successful item and Rejected for an item-level error`() = runTest {
        val bulkResponse = buildJsonArray {
            add(buildJsonObject { put("id", "a"); put("rev", "rev-a") })
            add(buildJsonObject { put("id", "b"); put("error", "conflict") })
        }
        coEvery { uploadRepository.postJsonArray(url, any()) } returns Response.success(bulkResponse)

        val outcomes = mutableMapOf<String, BulkDocsUploader.Outcome>()
        BulkDocsUploader.upload(
            uploadRepository, url,
            listOf("a" to JsonObject(emptyMap()), "b" to JsonObject(emptyMap()))
        ) { item, outcome -> outcomes[item] = outcome }

        assertEquals(true, outcomes["a"] is BulkDocsUploader.Outcome.Accepted)
        assertEquals("rev-a", (outcomes["a"] as BulkDocsUploader.Outcome.Accepted).element.getValue("rev").jsonPrimitive.content)

        val rejected = outcomes["b"] as BulkDocsUploader.Outcome.Rejected
        assertEquals(200, rejected.httpCode)
        assertEquals("conflict", rejected.element.getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `upload reports RequestFailed for every item on a non-2xx response`() = runTest {
        val errorBody = ResponseBody.create(null, "boom")
        coEvery { uploadRepository.postJsonArray(url, any()) } returns Response.error(500, errorBody)

        val outcomes = mutableMapOf<String, BulkDocsUploader.Outcome>()
        BulkDocsUploader.upload(
            uploadRepository, url,
            listOf("a" to JsonObject(emptyMap()), "b" to JsonObject(emptyMap()))
        ) { item, outcome -> outcomes[item] = outcome }

        listOf("a", "b").forEach { key ->
            val failure = outcomes.getValue(key) as BulkDocsUploader.Outcome.RequestFailed
            assertEquals(500, failure.httpCode)
            assertEquals(null, failure.exception)
        }
    }

    @Test
    fun `upload reports RequestFailed with the exception when the request throws`() = runTest {
        val exception = java.io.IOException("network down")
        coEvery { uploadRepository.postJsonArray(url, any()) } throws exception

        val outcomes = mutableMapOf<String, BulkDocsUploader.Outcome>()
        BulkDocsUploader.upload(
            uploadRepository, url,
            listOf("a" to JsonObject(emptyMap()))
        ) { item, outcome -> outcomes[item] = outcome }

        val failure = outcomes.getValue("a") as BulkDocsUploader.Outcome.RequestFailed
        assertEquals(null, failure.httpCode)
        assertEquals(exception, failure.exception)
    }

    @Test
    fun `upload only reports outcomes for items present in the response`() = runTest {
        // Server accepted fewer items than were sent - a partial bulk response.
        val bulkResponse = buildJsonArray {
            add(buildJsonObject { put("id", "a"); put("rev", "rev-a") })
        }
        coEvery { uploadRepository.postJsonArray(url, any()) } returns Response.success(bulkResponse)

        val outcomes = mutableMapOf<String, BulkDocsUploader.Outcome>()
        BulkDocsUploader.upload(
            uploadRepository, url,
            listOf("a" to JsonObject(emptyMap()), "b" to JsonObject(emptyMap()))
        ) { item, outcome -> outcomes[item] = outcome }

        assertEquals(setOf("a"), outcomes.keys)
    }

    @Test
    fun `upload rethrows cancellation instead of reporting RequestFailed`() = runTest {
        val docA = buildJsonObject { put("id", "a") }
        coEvery { uploadRepository.postJsonArray(url, any()) } throws CancellationException("worker stopped")
        var calls = 0

        val thrown = runCatching {
            BulkDocsUploader.upload(uploadRepository, url, listOf("a" to docA)) { _, _ -> calls++ }
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(0, calls)
    }
}
