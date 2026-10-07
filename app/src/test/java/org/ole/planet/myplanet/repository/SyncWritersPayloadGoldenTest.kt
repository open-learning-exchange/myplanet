package org.ole.planet.myplanet.repository

import android.app.Application
import android.content.SharedPreferences
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.ExamDao
import org.ole.planet.myplanet.data.room.dao.NotificationDao
import org.ole.planet.myplanet.data.room.dao.OfflineActivityDao
import org.ole.planet.myplanet.data.room.dao.QuestionDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.di.NetworkModule
import org.ole.planet.myplanet.model.AppNotification
import org.ole.planet.myplanet.model.Answer
import org.ole.planet.myplanet.model.Community
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.FinanceReportParams
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.utils.AppVersionProvider
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.VersionUtils
import org.ole.planet.myplanet.utils.relaxedInstance
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/**
 * Goldens for the repositories behind the sync writers and the remaining inline payload builders
 * (resources, sync, tags, community, notifications, progress, activities, enterprises,
 * configurations), recorded from the Gson code. Sync writers are driven through
 * [TransactionSyncManager.syncDb] or their API calls, and documents handed between
 * repositories are rendered with [JsonGolden.jsonText], so the tests do not depend on which
 * JSON library the signatures use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SyncWritersPayloadGoldenTest {

    private val api = mockk<ApiInterface>()
    private val time = TestTimeProvider(1_717_171_717_171L)
    private val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
    private val deviceNameProvider = mockk<DeviceNameProvider>(relaxed = true)
    private val calls = mutableListOf<JsonObject>()

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        every { deviceNameProvider.getDeviceName() } returns GoldenFixtures.DEVICE_NAME
        every { deviceNameProvider.getCustomDeviceName() } returns GoldenFixtures.CUSTOM_DEVICE_NAME
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "https://planet.example/db"
        every { UrlUtils.header } returns "Basic golden"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    private fun quoted(text: String) = Json.encodeToString(String.serializer(), text)

    private fun record(method: String, url: String?, body: Any?) {
        calls.add(buildJsonObject {
            put("method", method)
            put("url", url)
            put("body", if (body == null) JsonNull else Json.parseToJsonElement(JsonGolden.jsonText(body)))
        })
    }

    /** A document rendered the same whichever JSON library holds it. */
    private fun canonical(value: Any?): String = Json.parseToJsonElement(JsonGolden.jsonText(value)).toString()

    private fun docs(list: List<Any?>): String = list.joinToString(",", "[", "]") { canonical(it) }

    private fun page(vararg docs: String) = parse(
        """{"total_rows":${docs.size},"rows":[${docs.joinToString(",") { """{"id":"x","doc":$it}""" }}]}"""
    )

    private fun syncDb(table: String, page: JsonObject, vararg writers: Any): Int {
        coEvery { api.postDoc(any(), any(), match { it.contains("/$table/_all_docs") }, any()) } returns Response.success(page)
        val manager = relaxedInstance<TransactionSyncManager>(api, TestDispatcherProvider(UnconfinedTestDispatcher()), *writers)
        return kotlinx.coroutines.runBlocking { manager.syncDb(table) }
    }

    @Test
    fun `tags written from synced documents`() {
        val tagDao = mockk<TagDao>(relaxed = true)
        val saved = mutableListOf<TagEntity>()
        coEvery { tagDao.upsertAll(any()) } answers { saved.addAll(firstArg<List<TagEntity>>()) }
        syncDb(
            "tags",
            page(
                """{"_id":"t1","_rev":"1-t","name":${quoted(TRICKY)},"db":"resources","docType":"tag","tagId":"x","linkId":"y","attachedTo":["p1",1.50,null,{"a":1},true,1e2]}""",
                """{"_id":"t2","attachedTo":"single","name":5}""",
                """{"_id":"t3","attachedTo":5}""",
                """{"_id":"t4"}""",
                """{"_id":"_design/tags"}""",
            ),
            TagsRepositoryImpl(tagDao),
        )
        assertMatchesRawGolden(
            "repository/tags/synced-columns",
            saved.joinToString("\n") { "id=${it.id} _id=${it._id} rev=${it._rev} name=${it.name} db=${it.db} docType=${it.docType} tagId=${it.tagId} linkId=${it.linkId} attachedTo=${it.attachedTo} attached=${it.isAttached}" }
        )
    }

    @Test
    fun `notifications written from synced documents`() {
        val notificationDao = mockk<NotificationDao>(relaxed = true)
        val saved = mutableListOf<AppNotification>()
        coEvery { notificationDao.upsertAllPreservingPendingRead(any()) } answers { saved.addAll(firstArg<List<AppNotification>>()) }
        val repository = relaxedInstance<NotificationsRepositoryImpl>(time, notificationDao)
        val counts = listOf(
            syncDb(
                "notifications",
                page(
                    """{"_id":"n1","_rev":"1-n","type":"team","message":${quoted(TRICKY)},"link":"/teams/view/t1","user":"org.couchdb.user:a","priority":2,"status":"unread","time":1717171717171,"item":"team-1","linkParams":{"activeTab":"applicantTab"}}""",
                    """{"_id":"n2","type":"replyMessage","message":"m","priority":"3","status":"read","time":"1717","replyTo":"news-1"}""",
                    """{"_id":"n3","type":"newTask","message":"m","link":"/myTeams/view/task-1/x","priority":2.5,"time":1.7E12,"user":5}""",
                    """{"_id":"n4","type":"team","message":"m","linkParams":{"activeTab":1.50},"item":7}""",
                    """{"type":"orphan"}""",
                    """{"_id":"_design/n"}""",
                ),
                repository,
            ),
            syncDb("notifications", page("""{"_id":"n5","message":null}"""), repository),
        )
        assertMatchesRawGolden(
            "repository/notifications/synced-columns",
            "counts=$counts\n" + saved.joinToString("\n") {
                "id=${it.id} user=${it.userId} message=${it.message} read=${it.isRead} created=${it.createdAt.time} type=${it.type} sub=${it.subType} " +
                    "related=${it.relatedId} link=${it.link} priority=${it.priority} server=${it.isFromServer} rev=${it.rev}"
            }
        )
    }

    private fun progressRepository(
        coursesRepository: CoursesRepository = mockk(relaxed = true),
        submissionsRepository: SubmissionsRepository = mockk(relaxed = true),
        courseProgressDao: CourseProgressDao = mockk(relaxed = true),
        courseStepDao: CourseStepDao = mockk(relaxed = true),
        examDao: ExamDao = mockk(relaxed = true),
        questionDao: QuestionDao = mockk(relaxed = true),
    ) = ProgressRepositoryImpl(
        TestDispatcherProvider(UnconfinedTestDispatcher()),
        Lazy { coursesRepository },
        Lazy { mockk<ActivitiesRepository>(relaxed = true) },
        Lazy { submissionsRepository },
        courseProgressDao,
        courseStepDao,
        examDao,
        questionDao,
    )

    @Test
    fun `course progress written from synced documents`() {
        val courseProgressDao = mockk<CourseProgressDao>(relaxed = true)
        val saved = mutableListOf<CourseProgress>()
        coEvery { courseProgressDao.getByIds(any()) } returns listOf(CourseProgress().apply { id = "p1"; passed = true })
        coEvery { courseProgressDao.getByCourseUsersAndSteps(any()) } returns listOf(
            CourseProgress().apply { id = "local-2"; courseId = "c1"; userId = "u1"; stepNum = 2; passed = true }
        )
        coEvery { courseProgressDao.upsertAll(any()) } answers { saved.addAll(firstArg<List<CourseProgress>>()) }
        syncDb(
            "courses_progress",
            page(
                """{"_id":"p1","_rev":"2-p","passed":false,"stepNum":1,"userId":"u1","parentCode":"parent","courseId":"c1","createdOn":"planet","createdDate":1717171717171,"updatedDate":1.7E12}""",
                """{"_id":"p2","passed":"TRUE","stepNum":"2","userId":"u1","courseId":"c1","createdDate":"17","updatedDate":2.5}""",
                """{"_id":"p3","stepNum":3.9,"passed":1,"userId":5}""",
            ),
            progressRepository(courseProgressDao = courseProgressDao),
        )
        assertMatchesRawGolden(
            "repository/progress/synced-columns",
            saved.joinToString("\n") {
                "id=${it.id} _id=${it._id} rev=${it._rev} passed=${it.passed} step=${it.stepNum} user=${it.userId} parent=${it.parentCode} course=${it.courseId} " +
                    "createdOn=${it.createdOn} created=${it.createdDate} updated=${it.updatedDate}"
            }
        )
    }

    @Test
    fun `course progress data with step mistakes`() = runTest {
        val coursesRepository = mockk<CoursesRepository>(relaxed = true)
        coEvery { coursesRepository.getMyCourses("u1") } returns listOf(
            MyCourse(id = "c1", courseId = "c1", courseTitle = TRICKY),
            MyCourse(id = "c2", courseId = "c2", courseTitle = "No steps"),
            MyCourse(id = "c3", courseId = null, courseTitle = null),
        )
        val courseStepDao = mockk<CourseStepDao>(relaxed = true)
        coEvery { courseStepDao.getByCourseIds(any()) } returns listOf(CourseStep(id = "s1", courseId = "c1"), CourseStep(id = "s2", courseId = "c1"))
        val courseProgressDao = mockk<CourseProgressDao>(relaxed = true)
        coEvery { courseProgressDao.getByUserAndCourseIds(any(), any()) } returns listOf(CourseProgress().apply { courseId = "c1"; stepNum = 1 })
        val examDao = mockk<ExamDao>(relaxed = true)
        coEvery { examDao.getByCourseIds(any()) } returns listOf(StepExam(id = "e1", courseId = "c1"), StepExam(id = "e2", courseId = "c1"))
        val submissionsRepository = mockk<SubmissionsRepository>(relaxed = true)
        coEvery { submissionsRepository.getExamSubmissionsByUser("u1") } returns listOf(
            Submission(id = "sub1", parentId = "e1@c1"),
            Submission(id = "sub2", parentId = "e2@c1"),
        )
        coEvery { submissionsRepository.getAnswersBySubmissionIds(any()) } returns listOf(
            Answer(id = "a1", questionId = "q1", mistakes = 2, submissionId = "sub1"),
            Answer(id = "a2", questionId = "q2", mistakes = 1, submissionId = "sub2"),
            Answer(id = "a3", questionId = "q1", mistakes = 3, submissionId = "sub2"),
        )
        val questionDao = mockk<QuestionDao>(relaxed = true)
        coEvery { questionDao.getByIds(any()) } returns listOf(ExamQuestion(id = "q1", examId = "e1"), ExamQuestion(id = "q2", examId = "e2"))
        val repository = progressRepository(coursesRepository, submissionsRepository, courseProgressDao, courseStepDao, examDao, questionDao)

        val data = repository.fetchCourseData("u1")
        val rows = repository.getCourseProgressRows("u1")
        val found = listOf("c1", "c2", "missing").map { id ->
            runCatching { canonical(repository.findProgressForCourse(data, id)) }.getOrElse { "threw ${it::class.simpleName}" }
        }
        assertMatchesGolden("repository/progress/course-data", JsonGolden.jsonText(data))
        assertMatchesRawGolden("repository/progress/course-rows-and-lookups", (rows.map { it.toString() } + found).joinToString("\n"))
    }

    @Test
    fun `community written from registration requests`() = runTest {
        val communityDao = mockk<CommunityDao>(relaxed = true)
        val saved = slot<List<Community>>()
        coEvery { communityDao.replaceAll(capture(saved)) } returns Unit
        coEvery { api.getJsonObject(any(), any()) } returns Response.success(
            page(
                """{"_id":"c1","name":"learning","localDomain":"<l>","parentDomain":"p","registrationRequest":"accepted"}""",
                """{"_id":"c2","name":5,"localDomain":null}""",
            )
        )
        CommunityRepositoryImpl(api, communityDao).syncCommunityDocs()
        assertMatchesRawGolden(
            "repository/community/synced-columns",
            saved.captured.joinToString("\n") { "id=${it.id} weight=${it.weight} name=${it.name} local=${it.localDomain} parent=${it.parentDomain} request=${it.registrationRequest}" }
        )
    }

    @Test
    fun `resources search activity filter column`() = runTest {
        val searchActivityDao = mockk<SearchActivityDao>(relaxed = true)
        val saved = mutableListOf<SearchActivity>()
        coEvery { searchActivityDao.insert(capture(saved)) } returns Unit
        val repository = relaxedInstance<ResourcesRepositoryImpl>(searchActivityDao)
        val tags = listOf(TagEntity().apply { _id = "tag <1> & 'x' = y" }, TagEntity().apply { _id = null }, TagEntity().apply { _id = TRICKY })
        repository.saveSearchActivity("learner", TRICKY, "planet", "parent", tags, linkedSetOf("Math", "<Art>"), linkedSetOf("Español"), linkedSetOf(), linkedSetOf("Text & 'v'"))
        repository.saveSearchActivity("learner", "", "planet", "parent", emptyList(), emptySet(), emptySet(), emptySet(), emptySet())
        assertMatchesRawGolden("repository/resources/searchActivity-filter", saved.joinToString("\n") { "filter=${it.filter}" })
    }

    @Test
    fun `shelf and resource sync documents`() = runTest {
        val resourcesWriter = mockk<ResourcesSyncWriter>(relaxed = true)
        val coursesRepository = mockk<CoursesRepository>(relaxed = true)
        val eventsWriter = mockk<EventsSyncWriter>(relaxed = true)
        val teamsSyncRepository = mockk<TeamsSyncRepository>(relaxed = true)
        val handed = mutableListOf<String>()
        coEvery { resourcesWriter.batchInsertMyLibrary(any(), any()) } answers { handed.add("resources ${firstArg<String?>()} ${docs(secondArg())}"); 1 }
        coEvery { coursesRepository.batchInsertMyCourses(any(), any()) } answers { handed.add("courses ${firstArg<String?>()} ${docs(secondArg())}"); 1 }
        coEvery { eventsWriter.batchInsertMeetups(any()) } answers { handed.add("meetups ${docs(firstArg())}"); 1 }
        coEvery { teamsSyncRepository.batchInsertMyTeams(any()) } answers { handed.add("teams ${docs(firstArg())}"); 1 }
        coEvery { api.getJsonObject(any(), any()) } answers {
            val url = secondArg<String>()
            when {
                url.endsWith("/shelf/shelf-1") -> Response.success(
                    parse("""{"_id":"shelf-1","resourceIds":["r1",null,2,1.50,"r <2>"],"courseIds":["c1"],"meetupIds":[],"myTeamIds":["t1"]}""")
                )
                url.contains("limit=0") -> Response.success(parse("""{"total_rows":1.5e1,"offset":0,"rows":[]}"""))
                else -> Response.success(
                    parse(
                        """{"total_rows":3,"rows":[{"id":"_design/r","doc":{"_id":"_design/r"}},{"id":"r1","doc":{"_id":"r1","title":${quoted(TRICKY)},"n":1e2,"f":1.50,"big":$BIG_LONG}},
                           {"id":"r2","value":{}},{"id":"r3","doc":{"_id":""}},{"id":"r4","doc":{"_id":"r4","none":null}}]}"""
                    )
                )
            }
        }
        coEvery { api.postDoc(any(), any(), any(), any()) } answers {
            val url = thirdArg<String>()
            record("POST", url, arg<JsonObject>(3))
            Response.success(
                parse(
                    """{"rows":[{"key":"a","doc":{"_id":"a","n":1e2,"f":1.50,"big":$BIG_LONG,"t":${quoted(TRICKY)},"none":null}},{"key":"b","error":"not_found"},{"key":"c","doc":{"_id":"c"}}]}"""
                )
            )
        }
        val repository = relaxedInstance<SyncRepositoryImpl>(
            api, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)), resourcesWriter, coursesRepository, eventsWriter, teamsSyncRepository, time,
        )
        val processed = repository.processShelfParallel("shelf-1")
        val total = repository.fetchResourceTotalRows()
        val rows = repository.fetchResourceRows(10, 0)
        val syncable = rows?.let { repository.filterSyncableResourceDocs(it) }
        assertMatchesGolden("repository/sync/shelf-keys", JsonArray(calls.sortedBy { (it["url"] as JsonPrimitive).content }))
        assertMatchesRawGolden(
            "repository/sync/shelf-and-resource-docs",
            (handed.sorted() + "processed=$processed total=$total" + "rows=${canonical(rows)}" + "syncable=${syncable?.let { docs(it) }}").joinToString("\n")
        )
    }

    @Test
    fun `login activities uploaded and synced`() = runTest {
        val offlineActivityDao = mockk<OfflineActivityDao>(relaxed = true)
        val saved = mutableListOf<OfflineActivity>()
        coEvery { offlineActivityDao.upsertAll(any()) } answers { saved.addAll(firstArg<List<OfflineActivity>>()) }
        val pending = listOf(
            OfflineActivity().apply {
                id = "a1"; userName = TRICKY; userId = "org.couchdb.user:a"; type = "login"; loginTime = BIG_LONG; logoutTime = null
                createdOn = "planet"; parentCode = "parent"
            },
            OfflineActivity().apply { id = "a2"; userId = "org.couchdb.user:b"; _id = "srv-2"; _rev = "1-b"; loginTime = 1L; logoutTime = 2L },
            OfflineActivity().apply { id = "a3"; userId = "guest_x" },
        )
        coEvery { offlineActivityDao.getPendingLoginUploads() } returns pending
        coEvery { offlineActivityDao.getByIds(any()) } answers { pending.filter { it.id in firstArg<List<String>>() } }
        coEvery { api.postDoc(any(), any(), any(), any()) } answers {
            record("POST", thirdArg(), arg<JsonObject>(3))
            Response.success(parse("""{"ok":true,"id":"srv","rev":"2-x","_id":"srv-id","_rev":1.50}"""))
        }
        val repository = relaxedInstance<ActivitiesRepositoryImpl>(api, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)), time, offlineActivityDao, deviceNameProvider)
        repository.uploadActivities()
        val uploaded = saved.joinToString("\n") { "uploaded id=${it.id} _id=${it._id} rev=${it._rev}" }
        saved.clear()

        coEvery { offlineActivityDao.getByRemoteIds(any()) } returns listOf(OfflineActivity().apply { id = "local-1"; _id = "s1" })
        coEvery { offlineActivityDao.getByLoginTimesAndUserNames(any(), any()) } returns listOf(
            OfflineActivity().apply { id = "local-2"; loginTime = 1717171717171L; userName = "bob" }
        )
        syncDb(
            "login_activities",
            page(
                """{"_id":"s1","_rev":"3-s","user":"amy","type":"login","loginTime":1.7E12,"logoutTime":"5","parentCode":"p","createdOn":"c","androidId":"x"}""",
                """{"_id":"s2","user":"bob","loginTime":1717171717171,"logoutTime":null,"androidId":7}""",
                """{"_id":"_design/a"}""",
            ),
            repository,
        )
        assertMatchesGolden("repository/activities/login-upload-calls", JsonArray(calls))
        assertMatchesRawGolden(
            "repository/activities/login-columns",
            uploaded + "\n" + saved.joinToString("\n") {
                "synced id=${it.id} _id=${it._id} rev=${it._rev} user=${it.userName} type=${it.type} login=${it.loginTime} logout=${it.logoutTime} " +
                    "parent=${it.parentCode} createdOn=${it.createdOn} androidId=${it.androidId}"
            }
        )
    }

    @Test
    fun `myPlanet activities uploaded`() = runTest {
        mockkObject(MyPlanet.Companion)
        every { MyPlanet.getNormalMyPlanetActivities(any(), any(), any()) } returns parse("""{"type":"sync","n":1.50}""")
        every { MyPlanet.getTabletUsages(any(), any(), any()) } returns JsonArray(listOf(parse("""{"time":1717171717171,"f":1e2,"t":${quoted(TRICKY)}}""")))
        every { MyPlanet.getMyPlanetActivities(any(), any(), any(), any()) } returns parse("""{"_id":"fresh","usages":[{"a":1.50}]}""")
        coEvery { api.postDoc(any(), any(), any(), any()) } answers {
            record("POST", thirdArg(), arg<JsonObject>(3))
            Response.success(parse("""{"ok":true}"""))
        }
        val bodies = ArrayDeque(
            listOf<JsonObject?>(
                parse("""{"_id":"existing","_rev":"4-m","usages":[{"time":1,"f":1.50,"none":null}],"n":1e2}"""),
                null,
                parse("""{"_id":"no-usages"}"""),
            )
        )
        coEvery { api.getJsonObject(any(), any()) } answers { Response.success<JsonObject>(bodies.removeFirst()) }
        val repository = relaxedInstance<ActivitiesRepositoryImpl>(api, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)), time, deviceNameProvider)
        val user = UserEntity(id = "u1", parentCode = "parent", planetCode = "planet")
        val outcomes = (1..3).map { runCatching { repository.uploadMyPlanetActivities(user) }.exceptionOrNull()?.let { it::class.simpleName } ?: "ok" }
        calls.add(buildJsonObject { put("outcomes", JsonArray(outcomes.map { JsonPrimitive(it) })) })
        assertMatchesGolden("repository/activities/myplanet-calls", JsonArray(calls))
    }

    @Test
    fun `enterprise report columns`() = runTest {
        val teamDao = mockk<TeamDao>(relaxed = true)
        val saved = slot<MyTeam>()
        coEvery { teamDao.upsert(capture(saved)) } returns Unit
        val repository = relaxedInstance<EnterprisesRepositoryImpl>(teamDao, time)
        repository.addReport(FinanceReportParams(TRICKY, 1, -2, Int.MAX_VALUE, 0, 5, BIG_LONG, 1L, "team <1>", null, "planet"))
        val team = saved.captured
        assertMatchesRawGolden(
            "repository/enterprises/report-columns",
            "id=${team._id.length} desc=${team.description} begin=${team.beginningBalance} sales=${team.sales} income=${team.otherIncome} wages=${team.wages} " +
                "expenses=${team.otherExpenses} start=${team.startDate} end=${team.endDate} created=${team.createdDate} updated=${team.updatedDate} " +
                "teamId=${team.teamId} teamType=${team.teamType} planet=${team.teamPlanetCode} docType=${team.docType} flagged=${team.updated}"
        )
    }

    private fun configurationsRepository(scope: TestScope, prefs: SharedPreferences, editor: SharedPreferences.Editor): ConfigurationsRepositoryImpl {
        every { sharedPrefManager.rawPreferences } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        val serverUrlMapper = mockk<ServerUrlMapper>(relaxed = true)
        every { serverUrlMapper.processUrl(any()) } answers { ServerUrlMapper.UrlMapping(firstArg()) }
        return relaxedInstance<ConfigurationsRepositoryImpl>(
            api, scope, sharedPrefManager, serverUrlMapper, TestDispatcherProvider(UnconfinedTestDispatcher(scope.testScheduler)), time,
            NetworkModule.providePlainGson(), mockk<AppVersionProvider> { every { versionName } returns "0.70.64" },
        )
    }

    @Test
    fun `configuration fetched and community leaders stored`() = runTest {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val stored = mutableListOf<String>()
        val repository = configurationsRepository(TestScope(UnconfinedTestDispatcher(testScheduler)), prefs, editor)
        every { editor.putString(any(), any()) } answers { stored.add("pref ${firstArg<String>()}=${secondArg<String?>()}"); editor }
        every { sharedPrefManager.setParentCode(any()) } answers { stored.add("parentCode=${firstArg<String>()}") }
        every { sharedPrefManager.setCommunityLeaders(any()) } answers { stored.add("leaders=${firstArg<String>()}") }
        coEvery { api.getConfiguration(any()) } answers {
            val url = firstArg<String>()
            when {
                url.endsWith("/versions") -> Response.success(parse("""{"minapk":"0.1.0","latestapk":1.50}"""))
                else -> Response.success(
                    parse(
                        """{"rows":[{"id":"config-1","doc":{"_id":"config-1","code":"planet <code>","parentCode":"parent & 'p'","preferredLang":"Klingon <x>",
                           "models":{"openai":"gpt <4> & 'o' = x","n":1.50,"e":1e2,"b":true},"planetType":"community"}}]}"""
                    )
                )
            }
        }
        val result = repository.getMinApk("https://satellite:pin@planet.example", "pin")
        stored.add("result=$result")

        coEvery { api.postDoc(any(), any(), any(), any()) } answers {
            record("POST", thirdArg(), arg<JsonObject>(3))
            Response.success(
                parse("""{"docs":[{"_id":"org.couchdb.user:admin","name":${quoted(TRICKY)},"isUserAdmin":true,"n":1e2,"f":1.50,"none":null,"url":"a/b?c=<d>&e='f'"}],"bookmark":"x"}""")
            )
        }
        repository.syncCommunityLeaders()
        assertMatchesGolden("repository/configurations/leaders-find", JsonArray(calls))
        assertMatchesRawGolden("repository/configurations/stored-values", stored.joinToString("\n"))
    }

    @Test
    fun `version detail cached and read back`() = runTest {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val outcomes = mutableListOf<String>()
        val repository = configurationsRepository(TestScope(UnconfinedTestDispatcher(testScheduler)), prefs, editor)
        every { UrlUtils.baseUrl(any()) } returns "https://planet.example"
        every { UrlUtils.getUpdateUrl(any()) } returns "https://planet.example/versions"
        every { UrlUtils.getApkVersionUrl(any()) } returns "https://planet.example/apkversion"
        mockkObject(NetworkUtils)
        every { NetworkUtils.getCurrentNetworkId(any()) } returns 1
        mockkObject(VersionUtils)
        every { VersionUtils.getVersionCode(any()) } returns 100
        mockkObject(Constants)
        every { Constants.showBetaFeature(any(), any()) } returns false
        var versionDetail: String? = null
        every { sharedPrefManager.setVersionDetail(any()) } answers { versionDetail = firstArg(); outcomes.add("versionDetail=${firstArg<String>()}") }
        every { sharedPrefManager.getVersionDetail() } answers { versionDetail }
        coEvery { api.checkVersion(any()) } returns Response.success(
            MyPlanet().apply { planetVersion = "0.20 <x> & 'y'"; minapkcode = 90; latestapkcode = 200; apkpath = "/a?b=1&c=<2>"; appname = null; localapkpath = TRICKY }
        )
        val apkVersions = ArrayDeque(listOf("\"0.2.10\"", "0.2.10", "", "{\"v\":1}", "null"))
        coEvery { api.getApkVersion(any()) } answers { Response.success(apkVersions.removeFirst().toResponseBody(null)) }
        val callback = object : ConfigurationsRepository.CheckVersionCallback {
            override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
                outcomes.add("update planet=${info?.planetVersion} min=${info?.minapkcode} latest=${info?.latestapkcode} apk=${info?.apkpath} app=${info?.appname} local=${info?.localapkpath} cancelable=$cancelable")
            }
            override fun onError(msg: String, blockSync: Boolean) {
                outcomes.add("error block=$blockSync")
            }
        }
        every { prefs.getLong("last_version_check_timestamp", any()) } returns 0L
        every { prefs.getInt("cachedApkVersion", any()) } answers { 0 }
        repeat(5) { repository.checkVersion(callback) }
        every { prefs.getLong("last_version_check_timestamp", any()) } returns time.now()
        every { prefs.getInt("cachedApkVersion", any()) } returns 210
        repository.checkVersion(callback)
        assertMatchesRawGolden("repository/configurations/version-check", outcomes.joinToString("\n"))
    }
}
