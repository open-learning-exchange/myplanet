package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.NetworkResult
import org.ole.planet.myplanet.data.api.ApiResponse
import org.ole.planet.myplanet.data.api.PlanetApi
import org.ole.planet.myplanet.data.api.UploadBody
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class UploadRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatcherProvider = object : DispatcherProvider {
        override val main = testDispatcher
        override val mainImmediate = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
        override val unconfined = testDispatcher
    }

    private lateinit var planetApi: PlanetApi
    private lateinit var repository: UploadRepositoryImpl

    @Before
    fun setUp() {
        planetApi = mockk(relaxed = true)
        repository = UploadRepositoryImpl(planetApi, dispatcherProvider)

        val spm = mockk<org.ole.planet.myplanet.services.SharedPrefManager>(relaxed = true)
        every { spm.getUrlUser() } returns "user"
        every { spm.getUrlPwd() } returns "pass"
        UrlUtils.init(spm)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.encodeToString(any(), any()) } returns "encoded_credentials"
    }

    @After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    @Test
    fun `postUpload calls postDoc on PlanetApi`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val kotlinxData = kotlinx.serialization.json.JsonObject(emptyMap())
        val expectedResponse = ApiResponse.success(kotlinxData)
        coEvery { planetApi.postDoc(any(), eq("application/json"), eq(url), eq(kotlinxData)) } returns expectedResponse

        val result = repository.postUpload(url, data)

        assertEquals(true, result is NetworkResult.Success)
        coVerify(exactly = 1) { planetApi.postDoc(any(), eq("application/json"), eq(url), eq(kotlinxData)) }
    }

    @Test
    fun `postUploadArray calls postDocArray on PlanetApi`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val kotlinxData = kotlinx.serialization.json.JsonObject(emptyMap())
        val expectedResponse = ApiResponse.success(kotlinx.serialization.json.JsonArray(emptyList()))
        coEvery { planetApi.postDocArray(any(), eq("application/json"), eq(url), eq(kotlinxData)) } returns expectedResponse

        val result = repository.postUploadArray(url, data)

        assertEquals(true, result is NetworkResult.Success)
        coVerify(exactly = 1) { planetApi.postDocArray(any(), eq("application/json"), eq(url), eq(kotlinxData)) }
    }

    @Test
    fun `putUpload calls putDoc on PlanetApi`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val kotlinxData = kotlinx.serialization.json.JsonObject(emptyMap())
        val expectedResponse = ApiResponse.success(kotlinxData)
        coEvery { planetApi.putDoc(any(), eq("application/json"), eq(url), eq(kotlinxData)) } returns expectedResponse

        val result = repository.putUpload(url, data)

        assertEquals(true, result is NetworkResult.Success)
        coVerify(exactly = 1) { planetApi.putDoc(any(), eq("application/json"), eq(url), eq(kotlinxData)) }
    }

    @Test
    fun `fetchExistingDoc calls getJsonObject on PlanetApi`() = runTest {
        val url = "testUrl"
        val expectedResponse = ApiResponse.success(kotlinx.serialization.json.JsonObject(emptyMap()))
        coEvery { planetApi.getJsonObject(any(), eq(url)) } returns expectedResponse

        val result = repository.fetchExistingDoc(url)

        assertEquals(true, result is NetworkResult.Success)
        coVerify(exactly = 1) { planetApi.getJsonObject(any(), eq(url)) }
    }

    @Test
    fun `uploadResource builds a request body with the given mime type and calls PlanetApi`() = runTest {
        val file = java.io.File.createTempFile("test", "png")
        file.writeText("test content")
        file.deleteOnExit()

        val bodySlot = io.mockk.slot<UploadBody>()
        val expectedResponse = ApiResponse.success(kotlinx.serialization.json.JsonObject(emptyMap()))
        coEvery { planetApi.uploadResource(any(), eq("testUrl"), capture(bodySlot)) } returns expectedResponse

        val result = repository.uploadResource(
            headerMap = mapOf("Authorization" to "mock"),
            url = "testUrl",
            file = file,
            mimeType = "image/png"
        )

        assertEquals(true, result is NetworkResult.Success)
        val captured = bodySlot.captured as UploadBody.FileContent
        assertEquals("image/png", captured.contentType)
        assertEquals(file.path, captured.path)
        coVerify(exactly = 1) { planetApi.uploadResource(any(), eq("testUrl"), any()) }
    }

    @Test
    fun `uploadAttachment calls uploadResource on PlanetApi`() = runTest {
        val file = java.io.File.createTempFile("test", "txt")
        file.writeText("test content")
        file.deleteOnExit()

        val expectedResponse = ApiResponse.success(kotlinx.serialization.json.JsonObject(emptyMap()))
        coEvery { planetApi.uploadResource(any(), any(), any()) } returns expectedResponse

        val result = repository.uploadAttachment(
            file = file,
            destinationFormat = "%s/%s/%s",
            id = "doc-1",
            rev = "rev-1",
            name = "file.txt"
        )

        assertEquals(true, result is NetworkResult.Success)
        coVerify(exactly = 1) { planetApi.uploadResource(any(), any(), any()) }
    }

    @Test
    fun `uploadAttachment resolves correct mime type for pdf, jpg, png and extensionless files`() = runTest {
        val testCases = listOf(
            "test_file.pdf" to "application/pdf",
            "test_file.jpg" to "image/jpeg",
            "test_file.png" to "image/png"
        )

        for ((fileName, expectedMime) in testCases) {
            val suffix = fileName.substring(fileName.lastIndexOf("."))
            val prefix = fileName.substring(0, fileName.lastIndexOf("."))
            val file = java.io.File.createTempFile(prefix, suffix)
            file.writeText("test content")
            file.deleteOnExit()

            val slot = io.mockk.slot<Map<String, String>>()
            coEvery { planetApi.uploadResource(capture(slot), any(), any()) } returns ApiResponse.success(kotlinx.serialization.json.JsonObject(emptyMap()))

            repository.uploadAttachment(
                file = file,
                destinationFormat = "%s/%s/%s",
                id = "doc-1",
                rev = "rev-1",
                name = fileName
            )

            assertEquals(expectedMime, slot.captured["Content-Type"])
        }

        val extensionlessFile = java.io.File(System.getProperty("java.io.tmpdir"), "extensionless_test_file")
        extensionlessFile.createNewFile()
        extensionlessFile.writeText("test content")
        extensionlessFile.deleteOnExit()

        val slot = io.mockk.slot<Map<String, String>>()
        coEvery { planetApi.uploadResource(capture(slot), any(), any()) } returns ApiResponse.success(kotlinx.serialization.json.JsonObject(emptyMap()))

        repository.uploadAttachment(
            file = extensionlessFile,
            destinationFormat = "%s/%s/%s",
            id = "doc-1",
            rev = "rev-1",
            name = "extensionless_test_file"
        )

        assertEquals("application/octet-stream", slot.captured["Content-Type"])
    }
}
