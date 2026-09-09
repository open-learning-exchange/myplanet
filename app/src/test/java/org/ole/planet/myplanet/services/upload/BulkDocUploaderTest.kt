package org.ole.planet.myplanet.services.upload

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.repository.UploadRepository
import retrofit2.Response

class BulkDocUploaderTest {

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
        BulkDocUploader.upload(uploadRepository, url, emptyList<Pair<String, JsonObject>>()) { _, _ -> calls++ }

        assertEquals(0, calls)
        coVerify(exactly = 0) { uploadRepository.postUploadArray(any(), any()) }
    }

    @Test
    fun `upload posts all serialized docs as one bulk request`() = runTest {
        val docA = JsonObject().apply { addProperty("id", "a") }
        val docB = JsonObject().apply { addProperty("id", "b") }
        coEvery { uploadRepository.postUploadArray(url, any()) } returns Response.success(JsonArray())

        BulkDocUploader.upload(uploadRepository, url, listOf("a" to docA, "b" to docB)) { _, _ -> }

        coVerify(exactly = 1) {
            uploadRepository.postUploadArray(url, match { payload ->
                payload.getAsJsonArray("docs").size() == 2
            })
        }
    }

    @Test
    fun `upload reports Accepted for a successful item and Rejected for an item-level error`() = runTest {
        val bulkResponse = JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "a"); addProperty("rev", "rev-a") })
            add(JsonObject().apply { addProperty("id", "b"); addProperty("error", "conflict") })
        }
        coEvery { uploadRepository.postUploadArray(url, any()) } returns Response.success(bulkResponse)

        val outcomes = mutableMapOf<String, BulkDocUploader.Outcome>()
        BulkDocUploader.upload(
            uploadRepository, url,
            listOf("a" to JsonObject(), "b" to JsonObject())
        ) { item, outcome -> outcomes[item] = outcome }

        assertEquals(true, outcomes["a"] is BulkDocUploader.Outcome.Accepted)
        assertEquals("rev-a", (outcomes["a"] as BulkDocUploader.Outcome.Accepted).element.get("rev").asString)

        val rejected = outcomes["b"] as BulkDocUploader.Outcome.Rejected
        assertEquals(200, rejected.httpCode)
        assertEquals("conflict", rejected.element.get("error").asString)
    }

    @Test
    fun `upload reports RequestFailed for every item on a non-2xx response`() = runTest {
        val errorBody = ResponseBody.create(null, "boom")
        coEvery { uploadRepository.postUploadArray(url, any()) } returns Response.error(500, errorBody)

        val outcomes = mutableMapOf<String, BulkDocUploader.Outcome>()
        BulkDocUploader.upload(
            uploadRepository, url,
            listOf("a" to JsonObject(), "b" to JsonObject())
        ) { item, outcome -> outcomes[item] = outcome }

        listOf("a", "b").forEach { key ->
            val failure = outcomes.getValue(key) as BulkDocUploader.Outcome.RequestFailed
            assertEquals(500, failure.httpCode)
            assertEquals(null, failure.exception)
        }
    }

    @Test
    fun `upload reports RequestFailed with the exception when the request throws`() = runTest {
        val exception = java.io.IOException("network down")
        coEvery { uploadRepository.postUploadArray(url, any()) } throws exception

        val outcomes = mutableMapOf<String, BulkDocUploader.Outcome>()
        BulkDocUploader.upload(
            uploadRepository, url,
            listOf("a" to JsonObject())
        ) { item, outcome -> outcomes[item] = outcome }

        val failure = outcomes.getValue("a") as BulkDocUploader.Outcome.RequestFailed
        assertEquals(null, failure.httpCode)
        assertEquals(exception, failure.exception)
    }

    @Test
    fun `upload only reports outcomes for items present in the response`() = runTest {
        // Server accepted fewer items than were sent - a partial bulk response.
        val bulkResponse = JsonArray().apply {
            add(JsonObject().apply { addProperty("id", "a"); addProperty("rev", "rev-a") })
        }
        coEvery { uploadRepository.postUploadArray(url, any()) } returns Response.success(bulkResponse)

        val outcomes = mutableMapOf<String, BulkDocUploader.Outcome>()
        BulkDocUploader.upload(
            uploadRepository, url,
            listOf("a" to JsonObject(), "b" to JsonObject())
        ) { item, outcome -> outcomes[item] = outcome }

        assertEquals(setOf("a"), outcomes.keys)
    }
}
