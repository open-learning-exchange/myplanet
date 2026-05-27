package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.coVerify
import io.realm.Realm
import javax.inject.Provider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.services.SharedPrefManager

@ExperimentalCoroutinesApi
class SubmissionsRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var teamsRepositoryProvider: Provider<TeamsRepository>
    private lateinit var surveysRepositoryProvider: Provider<SurveysRepository>
    private lateinit var context: Context
    private lateinit var sharedPrefManager: SharedPrefManager
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: SubmissionsRepositoryImpl

    @Before
    fun setUp() {
        databaseService = mockk(relaxed = true)
        teamsRepositoryProvider = mockk(relaxed = true)
        surveysRepositoryProvider = mockk(relaxed = true)
        context = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)

        every { databaseService.ioDispatcher } returns testDispatcher

        val transactionSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            val realm = mockk<Realm>(relaxed = true)
            transactionSlot.captured.invoke(realm)
        }

        repository = SubmissionsRepositoryImpl(
            databaseService,
            testDispatcher,
            teamsRepositoryProvider,
            surveysRepositoryProvider,
            context,
            sharedPrefManager
        )
    }

    @Test
    fun `getPendingSurveysFlow queries correctly`() = runTest {
        assertNotNull(repository.getPendingSurveysFlow("user_123"))
    }

    @Test
    fun `getSubmissionsFlow queries correctly`() = runTest {
        assertNotNull(repository.getSubmissionsFlow("user_123"))
    }

    @Test
    fun `getPendingSurveys returns empty list when userId is null`() = runTest {
        val result = repository.getPendingSurveys(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getUniquePendingSurveys returns empty list when userId is null`() = runTest {
        val result = repository.getUniquePendingSurveys(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSurveyTitlesFromSubmissions returns empty list when examIds is empty`() = runTest {
        val result = repository.getSurveyTitlesFromSubmissions(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSubmissionsByIds returns empty list when ids is empty`() = runTest {
        val result = repository.getSubmissionsByIds(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSubmissionsByUserId returns correctly`() = runTest {
        assertNotNull(repository.getSubmissionsByUserId("test"))
    }

    @Test
    fun `saveSubmission performs transaction`() = runTest {
        val sub = mockk<RealmSubmission>(relaxed = true)
        repository.saveSubmission(sub)

        coVerify { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun `bulkInsertFromSync processes correctly`() {
        val realm = mockk<Realm>(relaxed = true)
        val jsonArray = JsonArray()
        repository.bulkInsertFromSync(realm, jsonArray)
        verify(exactly = 0) { realm.createObject(RealmSubmission::class.java, any<String>()) }
    }

    @Test
    fun `insertSubmission skips if _attachments present`() {
        val realm = mockk<Realm>(relaxed = true)
        val submission = JsonObject().apply { addProperty("_attachments", "test") }
        repository.insertSubmission(realm, submission)
        verify(exactly = 0) { realm.beginTransaction() }
    }
}
