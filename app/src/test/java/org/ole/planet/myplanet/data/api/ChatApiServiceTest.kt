package org.ole.planet.myplanet.data.api

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ChatApiServiceTest {

    private lateinit var apiInterface: ApiInterface
    private lateinit var chatApiService: ChatApiService
    private lateinit var sharedPrefManager: SharedPrefManager

    @Before
    fun setUp() {
        apiInterface = mockk()
        // Inject a mockContext but don't hold it as a class field since it's never used by public methods
        sharedPrefManager = mockk(relaxed = true)
        every { sharedPrefManager.rawPreferences } returns mockk(relaxed = true)
        every { sharedPrefManager.getUserName() } returns "neta"
        chatApiService = ChatApiService(apiInterface, mockk(), TestDispatcherProvider(UnconfinedTestDispatcher()), sharedPrefManager)

        // Note: mockkObject(UrlUtils) makes UrlUtils a global singleton mock.
        // Parallel tests would be flaky due to this shared state.
        // It's a limitation due to the production code using the static/singleton UrlUtils directly.
        mockkObject(UrlUtils)
        mockkObject(SecurePrefs)
        every { SecurePrefs.getUserName(any(), any()) } returns "neta"
        every { SecurePrefs.getPassword(any(), any()) } returns "secret"
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
        unmockkObject(SecurePrefs)
        unmockkStatic(Log::class)
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
    fun fetchAiProviders_whenServerReturnsProviderArray_mapsNameToConfigured() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        val jsonResponse = """
            {"providers":[
                {"name":"openai","label":"OpenAI","configured":true},
                {"name":"claude","label":"Claude","configured":false},
                {"name":"perplexity","label":"Perplexity","configured":false},
                {"name":"deepseek","label":"DeepSeek","configured":true}
            ]}
        """.trimIndent()
        coEvery { apiInterface.checkAiProviders("https://example.com/checkProviders/") } returns Response.success(jsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))

        val result = chatApiService.fetchAiProviders()

        assertEquals(mapOf("openai" to true, "claude" to false, "perplexity" to false, "deepseek" to true), result)
    }

    @Test
    fun fetchAiProviders_whenProviderEntryIsMalformed_skipsItAndTreatsMissingConfiguredAsFalse() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        val jsonResponse = """{"providers":[{"label":"No name","configured":true},{"name":"gemini"},"junk"]}"""
        coEvery { apiInterface.checkAiProviders("https://example.com/checkProviders/") } returns Response.success(jsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))

        val result = chatApiService.fetchAiProviders()

        assertEquals(mapOf("gemini" to false), result)
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
        coEvery { apiInterface.chatGpt("https://example.com/", content, any()) } returns Response.success(chatResponse)

        val result = chatApiService.sendChatRequest(content)

        assertTrue(result.isSuccessful)
        assertEquals(chatResponse, result.body())
    }

    @Test
    fun sendChatRequest_sendsSignedInUserBasicAuth() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        val content = "{}".toRequestBody("application/json".toMediaTypeOrNull())
        coEvery { apiInterface.chatGpt(any(), any(), any()) } returns Response.success(ChatResponse())

        chatApiService.sendChatRequest(content)

        coVerify { apiInterface.chatGpt("https://example.com/", content, "Basic bmV0YTpzZWNyZXQ=") } // neta:secret
    }

    @Test
    fun sendChatRequest_whenStoredCredentialsBelongToAnotherUser_sendsNoAuth() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        every { sharedPrefManager.getUserName() } returns "someoneElse"
        val content = "{}".toRequestBody("application/json".toMediaTypeOrNull())
        coEvery { apiInterface.chatGpt(any(), any(), any()) } returns Response.success(ChatResponse())

        chatApiService.sendChatRequest(content)

        coVerify { apiInterface.chatGpt("https://example.com/", content, null) }
    }

    @Test
    fun sendChatRequest_whenNoStoredPassword_sendsNoAuth() = runTest {
        every { UrlUtils.hostUrl } returns "https://example.com/"
        every { SecurePrefs.getPassword(any(), any()) } returns null
        val content = "{}".toRequestBody("application/json".toMediaTypeOrNull())
        coEvery { apiInterface.chatGpt(any(), any(), any()) } returns Response.success(ChatResponse())

        chatApiService.sendChatRequest(content)

        coVerify { apiInterface.chatGpt("https://example.com/", content, null) }
    }
}
