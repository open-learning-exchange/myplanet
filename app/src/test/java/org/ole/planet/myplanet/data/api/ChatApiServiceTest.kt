package org.ole.planet.myplanet.data.api

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class ChatApiServiceTest {

    private lateinit var planetApi: PlanetApi
    private lateinit var chatApiService: ChatApiService

    @Before
    fun setUp() {
        planetApi = mockk()
        // Inject a mockContext but don't hold it as a class field since it's never used by public methods
        chatApiService = ChatApiService(planetApi, TestDispatcherProvider(UnconfinedTestDispatcher()))

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
        coEvery { planetApi.checkAiProviders("https://example.com/checkProviders/") } returns ApiResponse.error(400, "Error")

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test
    fun fetchAiProviders_whenResponseBodyIsNull_returnsNull() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        coEvery { planetApi.checkAiProviders("https://example.com/checkProviders/") } returns ApiResponse.success(null)

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test
    fun fetchAiProviders_whenResponseBodyStringIsBlank_returnsNull() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"

        coEvery { planetApi.checkAiProviders("https://example.com/checkProviders/") } returns ApiResponse.success("   ")

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test
    fun fetchAiProviders_whenApiCheckSucceeds_returnsParsedMap() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        val jsonResponse = """{"openai": true, "gemini": false}"""
        coEvery { planetApi.checkAiProviders("https://example.com/checkProviders/") } returns ApiResponse.success(jsonResponse)

        val result = chatApiService.fetchAiProviders()

        assertEquals(2, result?.size)
        assertEquals(true, result?.get("openai"))
        assertEquals(false, result?.get("gemini"))
    }

    @Test
    fun fetchAiProviders_whenExceptionThrown_returnsNull() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        coEvery { planetApi.checkAiProviders("https://example.com/checkProviders/") } throws RuntimeException("Network error")

        val result = chatApiService.fetchAiProviders()

        assertNull(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sendChatRequest_whenHostUrlIsBlank_throwsIllegalArgumentException() = runTest {
        every { UrlUtils.hostUrl } returns ""
        val content = UploadBody.TextContent("{}", "application/json")

        chatApiService.sendChatRequest(content)
    }

    @Test
    fun sendChatRequest_whenHostUrlIsNotBlank_returnsResponse() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        val content = UploadBody.TextContent("{}", "application/json")
        val chatResponse = ChatResponse()
        coEvery { planetApi.chatGpt("https://example.com/", content) } returns ApiResponse.success(chatResponse)

        val result = chatApiService.sendChatRequest(content)

        assertTrue(result.isSuccessful)
        assertEquals(chatResponse, result.body)
    }
}
