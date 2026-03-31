package org.ole.planet.myplanet.ui.surveys

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.callback.OnSyncListener
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.SyncManager

@OptIn(ExperimentalCoroutinesApi::class)
class SurveysViewModelTest {

    private lateinit var surveysRepository: SurveysRepository
    private lateinit var syncManager: SyncManager
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var serverUrlMapper: ServerUrlMapper
    private lateinit var viewModel: SurveysViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        surveysRepository = mockk()
        syncManager = mockk(relaxed = true)
        userSessionManager = mockk()
        sharedPrefManager = mockk(relaxed = true)
        serverUrlMapper = mockk(relaxed = true)

        viewModel = SurveysViewModel(
            surveysRepository,
            syncManager,
            userSessionManager,
            sharedPrefManager,
            serverUrlMapper
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSurvey(
        id: String,
        name: String,
        createdDate: Long,
        adoptionDate: Long,
        sourceSurveyId: String? = null
    ): RealmStepExam {
        val survey = RealmStepExam()
        survey.id = id
        survey.name = name
        survey.createdDate = createdDate
        survey.adoptionDate = adoptionDate
        survey.sourceSurveyId = sourceSurveyId
        return survey
    }

    private fun stubLoadSurveys(surveys: List<RealmStepExam>) {
        coEvery { surveysRepository.getIndividualSurveys() } returns surveys
        coEvery { userSessionManager.getUserModel() } returns mockk(relaxed = true)
        coEvery { surveysRepository.getSurveyInfos(any(), any(), any(), any()) } returns emptyMap()
        coEvery { surveysRepository.getSurveyFormState(any(), any()) } returns emptyMap()
    }

    @Test
    fun `test sorting defaults to DATE_DESC and switches sort options`() = runTest {
        val survey1 = createSurvey("1", "Zebra", 1000L, 0L)
        val survey2 = createSurvey("2", "Apple", 2000L, 0L)
        val survey3 = createSurvey("3", "Banana", 1500L, 0L)

        stubLoadSurveys(listOf(survey1, survey2, survey3))

        viewModel.loadSurveys(false, null, false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Default should be DATE_DESC
        var currentSurveys = viewModel.surveys.value
        assertEquals("2", currentSurveys[0].id)
        assertEquals("3", currentSurveys[1].id)
        assertEquals("1", currentSurveys[2].id)

        // Switch to DATE_ASC
        viewModel.sort(SurveysViewModel.SortOption.DATE_ASC)
        currentSurveys = viewModel.surveys.value
        assertEquals("1", currentSurveys[0].id)
        assertEquals("3", currentSurveys[1].id)
        assertEquals("2", currentSurveys[2].id)

        // Switch to TITLE_ASC
        viewModel.sort(SurveysViewModel.SortOption.TITLE_ASC)
        currentSurveys = viewModel.surveys.value
        assertEquals("2", currentSurveys[0].id) // Apple
        assertEquals("3", currentSurveys[1].id) // Banana
        assertEquals("1", currentSurveys[2].id) // Zebra

        // Switch to TITLE_DESC
        viewModel.sort(SurveysViewModel.SortOption.TITLE_DESC)
        currentSurveys = viewModel.surveys.value
        assertEquals("1", currentSurveys[0].id) // Zebra
        assertEquals("3", currentSurveys[1].id) // Banana
        assertEquals("2", currentSurveys[2].id) // Apple
    }

    @Test
    fun `test toggleTitleSort correctly toggles between TITLE_ASC and TITLE_DESC`() = runTest {
        val survey1 = createSurvey("1", "Zebra", 1000L, 0L)
        val survey2 = createSurvey("2", "Apple", 2000L, 0L)

        stubLoadSurveys(listOf(survey1, survey2))

        viewModel.loadSurveys(false, null, false)
        testDispatcher.scheduler.advanceUntilIdle()

        // Toggle from default (DATE_DESC) -> TITLE_ASC
        viewModel.toggleTitleSort()
        var currentSurveys = viewModel.surveys.value
        assertEquals("2", currentSurveys[0].id) // Apple

        // Toggle again -> TITLE_DESC
        viewModel.toggleTitleSort()
        currentSurveys = viewModel.surveys.value
        assertEquals("1", currentSurveys[0].id) // Zebra
    }

    @Test
    fun `test date ordering logic prioritizes adoptionDate if sourceSurveyId is not null`() = runTest {
        val survey1 = createSurvey("1", "A", 1000L, 0L, null)
        val survey2 = createSurvey("2", "B", 500L, 3000L, "src2")
        val survey3 = createSurvey("3", "C", 2000L, 0L, "src3")

        stubLoadSurveys(listOf(survey1, survey2, survey3))

        viewModel.loadSurveys(false, null, false)
        testDispatcher.scheduler.advanceUntilIdle()

        val currentSurveys = viewModel.surveys.value
        assertEquals("2", currentSurveys[0].id)
        assertEquals("3", currentSurveys[1].id)
        assertEquals("1", currentSurveys[2].id)
    }

    @Test
    fun `test normalized search behavior with diacritics and multi-tokens`() = runTest {
        val survey1 = createSurvey("1", "El niño is here", 1000L, 0L)
        val survey2 = createSurvey("2", "The dog barks", 2000L, 0L)
        val survey3 = createSurvey("3", "Café au lait", 1500L, 0L)

        stubLoadSurveys(listOf(survey1, survey2, survey3))

        viewModel.loadSurveys(false, null, false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.search("niño")
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("1", viewModel.surveys.value[0].id)

        viewModel.search("nino")
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("1", viewModel.surveys.value[0].id)

        viewModel.search("CAFE")
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("3", viewModel.surveys.value[0].id)

        viewModel.search("lait cafe")
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("3", viewModel.surveys.value[0].id)

        viewModel.search("The dog")
        assertEquals(1, viewModel.surveys.value.size)
        assertEquals("2", viewModel.surveys.value[0].id)
    }

    @Test
    fun `test startExamSync when fastSync is false`() {
        every { sharedPrefManager.getFastSync() } returns false
        every { sharedPrefManager.isExamsSynced() } returns false

        viewModel.startExamSync()

        verify(exactly = 0) { syncManager.start(any(), any(), any()) }
    }

    @Test
    fun `test startExamSync when isExamsSynced is true`() {
        every { sharedPrefManager.getFastSync() } returns true
        every { sharedPrefManager.isExamsSynced() } returns true

        viewModel.startExamSync()

        verify(exactly = 0) { syncManager.start(any(), any(), any()) }
    }

    @Test
    fun `test startExamSync triggers sync and handles error state mapping`() = runTest {
        every { sharedPrefManager.getFastSync() } returns true
        every { sharedPrefManager.isExamsSynced() } returns false
        every { sharedPrefManager.getServerUrl() } returns "http://test.com"
        every { serverUrlMapper.processUrl(any()) } returns mockk()

        stubLoadSurveys(emptyList())

        // Mock serverUrlMapper.updateServerIfNecessary
        coEvery { serverUrlMapper.updateServerIfNecessary(any(), any()) } answers {
            // execute callback directly if we want
        }

        viewModel.startExamSync()
        testDispatcher.scheduler.advanceUntilIdle()

        val listenerSlot = slot<OnSyncListener>()
        verify { syncManager.start(capture(listenerSlot), any(), any()) }

        val listener = listenerSlot.captured

        // Test onSyncStarted
        listener.onSyncStarted()
        assertEquals(true, viewModel.isLoading.value)

        // Test onSyncFailed
        listener.onSyncFailed("Network Error")
        assertEquals(false, viewModel.isLoading.value)
        assertEquals("Sync failed: Network Error", viewModel.errorMessage.value)

        // Test onSyncComplete
        listener.onSyncComplete()
        verify { sharedPrefManager.setExamsSynced(true) }
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.isLoading.value)
    }
}
