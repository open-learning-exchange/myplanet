package org.ole.planet.myplanet.data.api

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ChatApiServiceTest {

    private lateinit var apiInterface: ApiInterface
    private lateinit var chatApiService: ChatApiService

    @Before
    fun setUp() {
        apiInterface = mockk()
        // Inject a mockContext but don't hold it as a class field since it's never used by public methods
        chatApiService = ChatApiService(apiInterface, mockk())

        // Note: mockkObject(UrlUtils) makes UrlUtils a global singleton mock.
        // Parallel tests would be flaky due to this shared state.
        // It's a limitation due to the production code using the static/singleton UrlUtils directly.
        mockkObject(UrlUtils)
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
    }

    @Test
    fun fetchAiProviders_whenHostUrlIsBlank_returnsNull() = runTest {
        every { UrlUtils.hostUrl } returns ""

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test
    fun fetchAiProviders_whenApiCheckFails_returnsNull() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        coEvery { apiInterface.checkAiProviders("https://example.com/checkProviders/") } returns Response.error(400, "Error".toResponseBody("application/json".toMediaTypeOrNull()))

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test
    fun fetchAiProviders_whenResponseBodyIsNull_returnsNull() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        coEvery { apiInterface.checkAiProviders("https://example.com/checkProviders/") } returns Response.success(null)

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test
    fun fetchAiProviders_whenResponseBodyStringIsBlank_returnsNull() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"

        // Mock ResponseBody to explicitly return "" on string() call.
        // Because string() consumes the stream, a regular toResponseBody() with ""
        // doesn't correctly simulate the specific line `if (responseString.isNullOrBlank())`
        // cleanly as the body exists and is not null. We mock it to explicitly test the `responseString.isNullOrBlank()` branch.
        val mockResponseBody = mockk<ResponseBody>()
        every { mockResponseBody.string() } returns "   " // Returns a blank string

        coEvery { apiInterface.checkAiProviders("https://example.com/checkProviders/") } returns Response.success(mockResponseBody)

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test
    fun fetchAiProviders_whenApiCheckSucceeds_returnsParsedMap() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        val jsonResponse = """{"openai": true, "gemini": false}"""
        coEvery { apiInterface.checkAiProviders("https://example.com/checkProviders/") } returns Response.success(jsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))

        val result = chatApiService.fetchAiProviders()

        assertEquals(2, result?.size)
        assertEquals(true, result?.get("openai"))
        assertEquals(false, result?.get("gemini"))
    }

    @Test
    fun fetchAiProviders_whenExceptionThrown_returnsNull() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        coEvery { apiInterface.checkAiProviders("https://example.com/checkProviders/") } throws RuntimeException("Network error")

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sendChatRequest_whenHostUrlIsBlank_throwsIllegalArgumentException() = runTest {
        every { UrlUtils.hostUrl } returns ""
        val content = "{}".toRequestBody("application/json".toMediaTypeOrNull())

        chatApiService.sendChatRequest(content)
    }

    @Test
    fun sendChatRequest_whenHostUrlIsNotBlank_returnsResponse() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        val content = "{}".toRequestBody("application/json".toMediaTypeOrNull())
        val chatResponse = ChatResponse()
        coEvery { apiInterface.chatGpt("https://example.com/", content) } returns Response.success(chatResponse)

        val result = chatApiService.sendChatRequest(content)

        assertTrue(result.isSuccessful)
        assertEquals(chatResponse, result.body())
    }
}
