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
import org.ole.planet.myplanet.data.api.ApiInterface
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

    private lateinit var apiInterface: ApiInterface
    private lateinit var repository: UploadRepositoryImpl

    @Before
    fun setUp() {
        apiInterface = mockk(relaxed = true)
        repository = UploadRepositoryImpl(apiInterface, dispatcherProvider)

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
    fun `postUpload calls postDoc on ApiInterface`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val kotlinxData = kotlinx.serialization.json.JsonObject(emptyMap())
        val expectedResponse = retrofit2.Response.success(kotlinxData)
        coEvery { apiInterface.postDoc(any(), eq("application/json"), eq(url), eq(kotlinxData)) } returns expectedResponse

        val result = repository.postUpload(url, data)

        assertEquals(true, result.isSuccessful)
        coVerify(exactly = 1) { apiInterface.postDoc(any(), eq("application/json"), eq(url), eq(kotlinxData)) }
    }

    @Test
    fun `postUploadArray calls postDocArray on ApiInterface`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val kotlinxData = kotlinx.serialization.json.JsonObject(emptyMap())
        val expectedResponse = retrofit2.Response.success(kotlinx.serialization.json.JsonArray(emptyList()))
        coEvery { apiInterface.postDocArray(any(), eq("application/json"), eq(url), eq(kotlinxData)) } returns expectedResponse

        val result = repository.postUploadArray(url, data)

        assertEquals(true, result.isSuccessful)
        coVerify(exactly = 1) { apiInterface.postDocArray(any(), eq("application/json"), eq(url), eq(kotlinxData)) }
    }

    @Test
    fun `putUpload calls putDoc on ApiInterface`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val kotlinxData = kotlinx.serialization.json.JsonObject(emptyMap())
        val expectedResponse = retrofit2.Response.success(kotlinxData)
        coEvery { apiInterface.putDoc(any(), eq("application/json"), eq(url), eq(kotlinxData)) } returns expectedResponse

        val result = repository.putUpload(url, data)

        assertEquals(true, result.isSuccessful)
        coVerify(exactly = 1) { apiInterface.putDoc(any(), eq("application/json"), eq(url), eq(kotlinxData)) }
    }

    @Test
    fun `fetchExistingDoc calls getJsonObject on ApiInterface`() = runTest {
        val url = "testUrl"
        val expectedResponse = retrofit2.Response.success(kotlinx.serialization.json.JsonObject(emptyMap()))
        coEvery { apiInterface.getJsonObject(any(), eq(url)) } returns expectedResponse

        val result = repository.fetchExistingDoc(url)

        assertEquals(true, result.isSuccessful)
        coVerify(exactly = 1) { apiInterface.getJsonObject(any(), eq(url)) }
    }

    @Test
    fun `uploadAttachment calls uploadResource on ApiInterface`() = runTest {
        val file = java.io.File.createTempFile("test", "txt")
        file.writeText("test content")
        file.deleteOnExit()

        val expectedResponse = retrofit2.Response.success(kotlinx.serialization.json.JsonObject(emptyMap()))
        coEvery { apiInterface.uploadResource(any(), any(), any()) } returns expectedResponse

        val result = repository.uploadAttachment(
            file = file,
            destinationFormat = "%s/%s/%s",
            id = "doc-1",
            rev = "rev-1",
            name = "file.txt"
        )

        assertEquals(true, result.isSuccessful)
        coVerify(exactly = 1) { apiInterface.uploadResource(any(), any(), any()) }
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
            coEvery { apiInterface.uploadResource(capture(slot), any(), any()) } returns retrofit2.Response.success(kotlinx.serialization.json.JsonObject(emptyMap()))

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
        coEvery { apiInterface.uploadResource(capture(slot), any(), any()) } returns retrofit2.Response.success(kotlinx.serialization.json.JsonObject(emptyMap()))

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
