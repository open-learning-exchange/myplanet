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
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.SubmissionDao
import org.ole.planet.myplanet.model.StepExam
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
    private lateinit var examDao: ExamDao
    private lateinit var submissionDao: SubmissionDao
    private lateinit var repository: UploadRepositoryImpl

    @Before
    fun setUp() {
        apiInterface = mockk(relaxed = true)
        examDao = mockk(relaxed = true)
        submissionDao = mockk(relaxed = true)
        repository = UploadRepositoryImpl(apiInterface, examDao, submissionDao, dispatcherProvider)

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
    fun `markUploaded delegates submission updates to dao`() = runTest {
        coEvery { submissionDao.markUploaded("sub-1", "remote-1", "rev-1") } returns 1

        val failed = repository.markUploaded(
            UploadUpdateContract(UploadUpdateType.Submissions),
            listOf(UploadedItemResult("sub-1", "remote-1", "rev-1", com.google.gson.JsonObject()))
        )

        assertEquals(emptyList<UploadedItemResult>(), failed)
        coVerify { submissionDao.markUploaded("sub-1", "remote-1", "rev-1") }
    }

    @Test
    fun `markUploaded with Submissions returns failure if dao returns 0`() = runTest {
        coEvery { submissionDao.markUploaded("sub-1", "remote-1", "rev-1") } returns 0

        val failed = repository.markUploaded(
            UploadUpdateContract(UploadUpdateType.Submissions),
            listOf(UploadedItemResult("sub-1", "remote-1", "rev-1", com.google.gson.JsonObject()))
        )

        assertEquals(1, failed.size)
        assertEquals("sub-1", failed.first().localId)
        coVerify { submissionDao.markUploaded("sub-1", "remote-1", "rev-1") }
    }

    @Test
    fun `markUploaded updates exams and returns missing exams as failures`() = runTest {
        val existingExam = StepExam(id = "exam-1")
        coEvery { examDao.getByIds(listOf("exam-1", "exam-missing")) } returns listOf(existingExam)

        val result1 = UploadedItemResult("exam-1", "remote-1", "rev-1", com.google.gson.JsonObject())
        val result2 = UploadedItemResult("exam-missing", "remote-2", "rev-2", com.google.gson.JsonObject())

        val failed = repository.markUploaded(
            UploadUpdateContract(UploadUpdateType.Exams),
            listOf(result1, result2)
        )

        // Only "exam-missing" should fail
        assertEquals(1, failed.size)
        assertEquals("exam-missing", failed.first().localId)

        // examDao.upsertAll should be called with existingExam whose _rev has been updated to "rev-1"
        assertEquals("rev-1", existingExam._rev)
        coVerify { examDao.upsertAll(listOf(existingExam)) }
    }

    @Test
    fun `postUpload calls postDoc on ApiInterface`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val expectedResponse = mockk<retrofit2.Response<com.google.gson.JsonObject>>()
        coEvery { apiInterface.postDoc(any(), eq("application/json"), eq(url), eq(data)) } returns expectedResponse

        val result = repository.postUpload(url, data)

        assertEquals(expectedResponse, result)
        coVerify(exactly = 1) { apiInterface.postDoc(any(), eq("application/json"), eq(url), eq(data)) }
    }

    @Test
    fun `postUploadArray calls postDocArray on ApiInterface`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val expectedResponse = mockk<retrofit2.Response<com.google.gson.JsonArray>>()
        coEvery { apiInterface.postDocArray(any(), eq("application/json"), eq(url), eq(data)) } returns expectedResponse

        val result = repository.postUploadArray(url, data)

        assertEquals(expectedResponse, result)
        coVerify(exactly = 1) { apiInterface.postDocArray(any(), eq("application/json"), eq(url), eq(data)) }
    }

    @Test
    fun `putUpload calls putDoc on ApiInterface`() = runTest {
        val url = "testUrl"
        val data = com.google.gson.JsonObject()
        val expectedResponse = mockk<retrofit2.Response<com.google.gson.JsonObject>>()
        coEvery { apiInterface.putDoc(any(), eq("application/json"), eq(url), eq(data)) } returns expectedResponse

        val result = repository.putUpload(url, data)

        assertEquals(expectedResponse, result)
        coVerify(exactly = 1) { apiInterface.putDoc(any(), eq("application/json"), eq(url), eq(data)) }
    }

    @Test
    fun `fetchExistingDoc calls getJsonObject on ApiInterface`() = runTest {
        val url = "testUrl"
        val expectedResponse = mockk<retrofit2.Response<com.google.gson.JsonObject>>()
        coEvery { apiInterface.getJsonObject(any(), eq(url)) } returns expectedResponse

        val result = repository.fetchExistingDoc(url)

        assertEquals(expectedResponse, result)
        coVerify(exactly = 1) { apiInterface.getJsonObject(any(), eq(url)) }
    }

    @Test
    fun `uploadAttachment calls uploadResource on ApiInterface`() = runTest {
        val file = java.io.File.createTempFile("test", "txt")
        file.writeText("test content")
        file.deleteOnExit()

        val expectedResponse = mockk<retrofit2.Response<com.google.gson.JsonObject>>()
        coEvery { apiInterface.uploadResource(any(), any(), any()) } returns expectedResponse

        val result = repository.uploadAttachment(
            file = file,
            destinationFormat = "%s/%s/%s",
            id = "doc-1",
            rev = "rev-1",
            name = "file.txt"
        )

        assertEquals(expectedResponse, result)
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
            coEvery { apiInterface.uploadResource(capture(slot), any(), any()) } returns mockk()

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
        coEvery { apiInterface.uploadResource(capture(slot), any(), any()) } returns mockk()

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
