package org.ole.planet.myplanet.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.AchievementDao
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.RetryDao
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.NewsLog
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.RetryOperation
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.SubmitPhotos
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.RetryRepository
import org.ole.planet.myplanet.repository.RetryRepositoryImpl
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepositoryImpl
import org.ole.planet.myplanet.repository.TeamsRepositoryImpl
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UploadRepositoryImpl
import org.ole.planet.myplanet.repository.UserRepositoryImpl
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.repository.VoicesRepositoryImpl
import org.ole.planet.myplanet.services.retry.RetryQueue
import org.ole.planet.myplanet.services.upload.AchievementUploader
import org.ole.planet.myplanet.services.upload.PhotoUploader
import org.ole.planet.myplanet.services.upload.TeamsUploader
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.services.upload.VoicesUploader
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY_URL
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.KJson
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.assertJsonEquals
import org.ole.planet.myplanet.utils.relaxedInstance
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Goldens for what the upload services put on the wire and into the retry queue, pinned at the
 * HTTP boundary ([ApiInterface], kotlinx-typed on both sides of the migration) and at the
 * [RetryRepository.recordFailure] string, so they hold whatever JSON types the services use in
 * between. Every request is answered with a retryable failure, so one call yields both the body
 * that was sent and the payload text the retry queue persists in Room.
 *
 * The retry text is written once and re-sent later, possibly by a newer app version: the
 * round-trip test feeds the recorded (Gson-written) text through [RetryRepositoryImpl] and
 * checks the re-sent body is the same document.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UploadWireGoldenTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val api = mockk<ApiInterface>()
    private val retryRepository = mockk<RetryRepository>(relaxed = true)
    private val sent = mutableListOf<String>()
    private val stored = mutableListOf<String>()

    /** JSON text Room holds (lists, filters) with number literals Gson and kotlinx could print differently. */
    private val numbersText = """{"n":1e2,"f":1.50,"i":7,"big":$BIG_LONG,"neg":-0.0}"""

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "https://planet.example/db"
        every { UrlUtils.header } returns "Basic golden"
        val failure = { Response.error<JsonObject>(503, "{}".toResponseBody(null)) }
        coEvery { api.postDoc(any(), any(), any(), any()) } answers { sent.add(arg<JsonObject>(3).toString()); failure() }
        coEvery { api.putDoc(any(), any(), any(), any()) } answers { sent.add(arg<JsonObject>(3).toString()); failure() }
        coEvery { api.postDocArray(any(), any(), any(), any()) } answers {
            sent.add(arg<JsonObject>(3).toString())
            Response.error<JsonArray>(503, "{}".toResponseBody(null))
        }
        coEvery { retryRepository.recordFailure(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            stored.add(arg(2))
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun uploadRepository(scheduler: TestCoroutineScheduler) =
        UploadRepositoryImpl(api, TestDispatcherProvider(StandardTestDispatcher(scheduler)))

    /** Pins the sent body (structurally) and the stored retry text (byte for byte) of the only request made. */
    private fun assertWire(name: String) {
        assertEquals("one request for $name", 1, sent.size)
        assertMatchesGolden("services/upload/$name-sent", sent.single())
        assertEquals("one retry for $name", 1, stored.size)
        assertMatchesRawGolden("services/upload/$name-retry", stored.single())
        sent.clear()
        stored.clear()
    }

    private fun TestScope.coordinatorFixture(
        voices: VoicesRepository = mockk(relaxed = true),
        activities: ActivitiesRepository = mockk(relaxed = true),
        submissions: SubmissionsRepository = mockk(relaxed = true),
    ): Pair<UploadCoordinator, UploadConfigs> {
        val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
        every { sharedPrefManager.getCustomDeviceName() } returns GoldenFixtures.CUSTOM_DEVICE_NAME
        every { sharedPrefManager.getPlanetCode() } returns "planet"
        every { sharedPrefManager.getParentCode() } returns "parent"
        val configs = relaxedInstance<UploadConfigs>(context, voices, submissions, activities, sharedPrefManager)
        val coordinator = UploadCoordinator(
            uploadRepository(testScheduler), RetryQueue(retryRepository), TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        )
        return coordinator to configs
    }

    @Test
    fun `news log activity`() = runTest {
        val voices = mockk<VoicesRepository>(relaxed = true)
        coEvery { voices.getPendingNewsLogUploads() } returns listOf(NewsLog().apply {
            id = "nl-1"; type = TRICKY; time = BIG_LONG; userId = "org.couchdb.user:learner"; androidId = "aid"
        })
        val (coordinator, configs) = coordinatorFixture(voices = voices)
        coordinator.uploadRoom(configs.NewsActivities)
        assertWire("newsLog")
    }

    @Test
    fun `search activity with a stored filter`() = runTest {
        val activities = mockk<ActivitiesRepository>(relaxed = true)
        coEvery { activities.getPendingSearchActivityUploads() } returns listOf(
            SearchActivity(id = "sa-1", text = TRICKY, type = "courses", time = BIG_LONG, user = "learner", filter = numbersText, createdOn = "planet", parentCode = "parent")
        )
        val (coordinator, configs) = coordinatorFixture(activities = activities)
        coordinator.uploadRoom(configs.SearchActivity)
        assertWire("searchActivity")
    }

    @Test
    fun `course and resource activities`() = runTest {
        val activities = mockk<ActivitiesRepository>(relaxed = true)
        coEvery { activities.getPendingCourseActivityUploads() } returns listOf(CourseActivity().apply {
            id = "ca-1"; createdOn = "planet"; time = BIG_LONG; title = TRICKY; courseId = "c1"; parentCode = "parent"; type = "visit"; user = "learner"
        })
        coEvery { activities.getPendingResourceActivityUploads() } returns listOf(ResourceActivity().apply {
            id = "ra-1"; createdOn = "planet"; time = BIG_LONG; title = TRICKY; resourceId = "r1"; parentCode = "parent"; type = "visit"; user = "learner"
        })
        val (coordinator, configs) = coordinatorFixture(activities = activities)
        coordinator.uploadRoom(configs.CourseActivities)
        assertWire("courseActivity")
        coordinator.uploadRoom(configs.ResourceActivities)
        assertWire("resourceActivity")
    }

    @Test
    fun `exam result put through the coordinator`() = runTest {
        val submissions = mockk<SubmissionsRepository>(relaxed = true)
        coEvery { submissions.getPendingExamResults() } returns listOf(Submission(id = "s-1", _id = "sub-1", userId = "org.couchdb.user:learner"))
        // The repository embeds a stored parent parsed with Gson's grammar (number literals kept).
        coEvery { submissions.getExamUploadPayload(any(), any()) } returns KJson.asJsonObject(
            KJson.parseOrNull("""{"_id":"sub-1","parent":$numbersText,"title":"$TRICKY_URL","answers":[]}""")
        )
        val (coordinator, configs) = coordinatorFixture(submissions = submissions)
        coordinator.upload(configs.ExamResults)
        assertWire("examResult")
    }

    @Test
    fun `news bulk docs`() = runTest {
        val newsDao = mockk<NewsDao>(relaxed = true)
        // Every column is text Gson parsed, so number literals reach the document as written.
        coEvery { newsDao.getAll() } returns listOf(News().apply {
            id = "news-1"; _id = "n-1"; _rev = "1-n"; message = TRICKY; time = BIG_LONG; createdOn = "planet"; docType = "message"
            viewableBy = "teams"; viewableId = "team-1"; viewIn = "[$numbersText]"; user = numbersText
            images = """[{"resourceId":"r1","markdown":"![]($TRICKY_URL)","n":1.50}]"""; labels = listOf("<help>")
            conversations = "[$numbersText]"; newsId = "c-1"; newsCreatedDate = BIG_LONG
        })
        val voices = relaxedInstance<VoicesRepositoryImpl>(newsDao)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val uploader = relaxedInstance<VoicesUploader>(voices, uploadRepository(testScheduler), RetryQueue(retryRepository), TestDispatcherProvider(dispatcher))
        uploader.uploadNews()
        advanceUntilIdle()
        assertWire("newsBulkDocs")
    }

    @Test
    fun `team bulk docs`() = runTest {
        val teamDao = mockk<TeamDao>(relaxed = true)
        coEvery { teamDao.getUpdatedTeams() } returns listOf(
            MyTeam(_id = "team-1", _rev = "1-t", name = TRICKY, userId = "learner", description = TRICKY_URL, limit = 12, createdDate = BIG_LONG, teamType = "local", teamPlanetCode = "planet", parentCode = "parent", docType = "team", type = "team", isPublic = true, updated = true),
            MyTeam(_id = "team-2", _rev = "2-t", isDeletePending = true),
        )
        val teams = relaxedInstance<TeamsRepositoryImpl>(teamDao)
        val lazyTeams = Lazy<TeamsSyncRepository> { teams }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val uploader = TeamsUploader(context, lazyTeams, uploadRepository(testScheduler), RetryQueue(retryRepository), TestDispatcherProvider(dispatcher))
        uploader.uploadTeams()
        advanceUntilIdle()
        assertEquals(1, sent.size)
        assertMatchesGolden("services/upload/teamsBulkDocs-sent", sent.single())
        assertEquals(2, stored.size)
        assertMatchesRawGolden("services/upload/teamsBulkDocs-retry-team", stored[0])
        assertMatchesRawGolden("services/upload/teamsBulkDocs-retry-deleted", stored[1])
    }

    @Test
    fun `submission photo`() = runTest {
        val submitPhotosDao = mockk<SubmitPhotosDao>(relaxed = true)
        coEvery { submitPhotosDao.getUnuploaded() } returns listOf(SubmitPhotos().apply {
            id = "ph-1"; submissionId = "sub-1"; courseId = "c1"; examId = "e1"; memberId = "learner"; date = TRICKY; uniqueId = "u1"; photoLocation = "/x/p.jpg"
        })
        val submissions = relaxedInstance<SubmissionsRepositoryImpl>(submitPhotosDao)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val uploader = PhotoUploader(submissions, TestDispatcherProvider(dispatcher), this, uploadRepository(testScheduler))
        uploader.uploadSubmitPhotos(null)
        advanceUntilIdle()
        assertEquals(1, sent.size)
        assertMatchesGolden("services/upload/submitPhoto-sent", sent.single())
    }

    @Test
    fun `achievement put`() = runTest {
        val achievementDao = mockk<AchievementDao>(relaxed = true)
        coEvery { achievementDao.getPendingUploads() } returns listOf(Achievement().apply {
            _id = "ach-1"; _rev = "1-a"; goals = TRICKY; purpose = TRICKY_URL; achievementsHeader = "h"; sendToNation = "true"
            username = "learner"; parentCode = "parent"; createdOn = "planet"
            achievements = listOf(numbersText, JsonPrimitive("plain $TRICKY").toString())
            references = listOf(buildJsonObject { put("name", TRICKY); put("phone", "1") }.toString())
            links = listOf(buildJsonObject { put("url", TRICKY_URL) }.toString())
            otherInfo = listOf("""{"k":1.0}""")
            resumeFileName = ""
        })
        val users = relaxedInstance<UserRepositoryImpl>(achievementDao)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val uploader = AchievementUploader(context, users, uploadRepository(testScheduler), TestDispatcherProvider(dispatcher))
        uploader.uploadAchievement()
        advanceUntilIdle()
        assertEquals(1, sent.size)
        assertMatchesGolden("services/upload/achievement-sent", sent.single())
    }

    @Test
    fun `stored retry payloads are re-sent as the same document`() = runTest {
        val names = listOf("newsLog", "searchActivity", "courseActivity", "resourceActivity", "examResult", "teamsBulkDocs-retry-team")
        names.forEach { name ->
            val file = if (name.startsWith("teams")) name else "$name-retry"
            val text = java.io.File("src/test/resources/json-golden/services/upload/$file.txt").readText()
            val retryDao = mockk<RetryDao>(relaxed = true)
            val repository = RetryRepositoryImpl(retryDao, api, TestTimeProvider(1L))
            sent.clear()
            repository.executeOperation(RetryOperation().apply {
                id = "op"; serializedPayload = text; endpoint = "x"; httpMethod = "PUT"; dbId = "doc"
            })
            assertEquals(1, sent.size)
            assertJsonEquals(text, sent.single(), "re-sent $name")
            assertEquals(Json.parseToJsonElement(text), Json.parseToJsonElement(sent.single()))
        }
        // A payload shape no uploader writes today still re-sends: the reader is plain kotlinx.
        val custom = buildJsonObject { put("a", 1) }.toString()
        sent.clear()
        RetryRepositoryImpl(mockk(relaxed = true), api, TestTimeProvider(1L)).executeOperation(RetryOperation().apply {
            id = "op"; serializedPayload = custom; endpoint = "x"; httpMethod = "POST"
        })
        assertEquals(custom, sent.single())
    }
}
