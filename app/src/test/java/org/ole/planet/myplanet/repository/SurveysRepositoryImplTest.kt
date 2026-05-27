package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class SurveysRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: SurveysRepositoryImpl
    private lateinit var mockRealm: Realm
    private lateinit var context: Context
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sharedPreferencesEditor: SharedPreferences.Editor

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        mockRealm = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)
        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val operation = firstArg<(Realm) -> Any>()
            operation(mockRealm)
        }
        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val operation = firstArg<(Realm) -> Unit>()
            operation(mockRealm)
        }

        context = mockk(relaxed = true)
        userSessionManager = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        dispatcherProvider = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        sharedPreferencesEditor = mockk(relaxed = true)

        every { context.getSharedPreferences("survey_reminders", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putLong(any(), any()) } returns sharedPreferencesEditor
        every { sharedPreferencesEditor.putString(any(), any()) } returns sharedPreferencesEditor

        repository = SurveysRepositoryImpl(
            context,
            databaseService,
            UnconfinedTestDispatcher(),
            userSessionManager,
            sharedPrefManager,
            dispatcherProvider
        )
    }

    private inline fun <reified T : io.realm.RealmModel> mockQueryResults(vararg results: List<T>): RealmQuery<T> {
        val mockQuery = mockk<RealmQuery<T>>(relaxed = true)
        val mockResults = mockk<RealmResults<T>>(relaxed = true)

        every { mockRealm.where(T::class.java) } returns mockQuery

        // Setup fluent return
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<Boolean>()) } returns mockQuery
        every { mockQuery.isNull(any<String>()) } returns mockQuery
        every { mockQuery.or() } returns mockQuery
        every { mockQuery.beginGroup() } returns mockQuery
        every { mockQuery.endGroup() } returns mockQuery
        every { mockQuery.`in`(any<String>(), any<Array<String>>()) } returns mockQuery

        every { mockQuery.findAll() } returns mockResults

        // Map sequential calls to copyFromRealm to different results
        if (results.size == 1) {
             every { mockRealm.copyFromRealm(mockResults) } returns results[0]
        } else if (results.isNotEmpty()) {
             every { mockRealm.copyFromRealm(mockResults) } returnsMany results.toList()
        }

        return mockQuery
    }

    @Test
    fun `getExamQuestions filters by examId`() = runTest {
        val mockResult = listOf(RealmExamQuestion().apply { examId = "exam1" })
        val mockQuery = mockQueryResults(mockResult)

        val result = repository.getExamQuestions("exam1")

        assertEquals(mockResult, result)
        verify {
            mockQuery.equalTo("examId", "exam1")
        }
    }

    @Test
    fun `getSurveys returns all surveys`() = runTest {
        val mockResult = listOf(RealmStepExam().apply { type = "surveys" })
        val mockQuery = mockQueryResults(mockResult)

        val result = repository.getSurveys()

        assertEquals(mockResult, result)
        verify {
            mockQuery.equalTo("type", "surveys")
        }
    }

    @Test
    fun `getLastSurveyDialogShown returns stored value`() = runTest {
        every { sharedPreferences.getLong("last_survey_dialog_shown", 0L) } returns 12345L

        val result = repository.getLastSurveyDialogShown()

        assertEquals(12345L, result)
        verify { sharedPreferences.getLong("last_survey_dialog_shown", 0L) }
    }

    @Test
    fun `setLastSurveyDialogShown stores value`() = runTest {
        repository.setLastSurveyDialogShown(12345L)

        verify { sharedPreferences.edit() }
        verify { sharedPreferencesEditor.putLong("last_survey_dialog_shown", 12345L) }
        verify { sharedPreferencesEditor.apply() }
    }

    @Test
    fun `isReminderScheduled returns true if scheduled`() = runTest {
        every { sharedPreferences.contains("reminder_time_survey1") } returns true

        val result = repository.isReminderScheduled("survey1")

        assertTrue(result)
        verify { sharedPreferences.contains("reminder_time_survey1") }
    }

    @Test
    fun `isReminderScheduled returns false if not scheduled`() = runTest {
        every { sharedPreferences.contains("reminder_time_survey1") } returns false

        val result = repository.isReminderScheduled("survey1")

        assertFalse(result)
        verify { sharedPreferences.contains("reminder_time_survey1") }
    }
}
