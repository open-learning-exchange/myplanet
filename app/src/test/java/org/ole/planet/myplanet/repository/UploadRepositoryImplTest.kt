package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.legacy.AnswerDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomExamEntity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.utils.UrlUtils

@OptIn(ExperimentalCoroutinesApi::class)
class UploadRepositoryImplTest {

    private lateinit var apiInterface: ApiInterface
    private lateinit var examDao: ExamDao
    private lateinit var submissionDao: SubmissionDao
    private lateinit var answerDao: AnswerDao
    private lateinit var repository: UploadRepositoryImpl

    @Before
    fun setUp() {
        apiInterface = mockk(relaxed = true)
        examDao = mockk(relaxed = true)
        submissionDao = mockk(relaxed = true)
        answerDao = mockk(relaxed = true)
        repository = UploadRepositoryImpl(apiInterface, examDao, submissionDao, answerDao)

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
    fun `queryPending returns adopted surveys from exam dao`() = runTest {
        coEvery { examDao.getPendingAdoptedSurveys() } returns listOf(
            RoomExamEntity(id = "exam-1", sourceSurveyId = "source-1", type = "surveys")
        )

        val result: List<RealmStepExam> = repository.queryPending(
            UploadQueryContract(UploadQueryType.AdoptedSurveys)
        )

        assertEquals(listOf("exam-1"), result.map { it.id })
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
}
