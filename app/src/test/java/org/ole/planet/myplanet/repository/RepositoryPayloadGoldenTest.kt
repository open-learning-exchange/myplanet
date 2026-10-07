package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject as KJsonObject
import okhttp3.RequestBody
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.data.room.dao.FeedbackDao
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.data.room.dao.NewsDao
import org.ole.planet.myplanet.data.room.dao.RatingDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.CourseStep
import org.ole.planet.myplanet.model.ExamQuestion
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MeetupCreationParams
import org.ole.planet.myplanet.model.MyCourse
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.News
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.TagEntity
import org.ole.planet.myplanet.model.TeamLog
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY_URL
import org.ole.planet.myplanet.utils.GsonUtils
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.callPrivate
import org.ole.planet.myplanet.utils.callPrivateSuspend
import org.ole.planet.myplanet.utils.relaxedInstance
import org.ole.planet.myplanet.utils.toGson
import org.ole.planet.myplanet.utils.withGsonBridgedNumbers

/**
 * Goldens for documents the repositories assemble themselves (outside the model serializers),
 * and for the JSON text they write into Room columns. One test per domain; goldens live under
 * `json-golden/repository/<domain>/`.
 */
class RepositoryPayloadGoldenTest {

    private val time = TestTimeProvider(1_717_171_717_171L)
    private val deviceNameProvider = mockk<DeviceNameProvider>()
    private val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)

    /** The app-wide Gson (serializeNulls) and the plain one (drops nulls), as NetworkModule provided them. */
    private val injectedGson: Gson = GsonUtils.appGson()
    private val plainGson = Gson()

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        every { deviceNameProvider.getDeviceName() } returns GoldenFixtures.DEVICE_NAME
        every { deviceNameProvider.getCustomDeviceName() } returns GoldenFixtures.CUSTOM_DEVICE_NAME
        every { sharedPrefManager.getPlanetCode() } returns "planet"
        every { sharedPrefManager.getParentCode() } returns "parent"
        every { sharedPrefManager.getCommunityName() } returns "community"
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "https://planet.example/db"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun golden(name: String, actual: Any?) = assertMatchesGolden("repository/$name", actual)
    private fun raw(name: String, actual: String) = assertMatchesRawGolden("repository/$name", actual)

    /** Server documents reach the repositories as kotlinx trees bridged with toGson(), so build fixtures the same way. */
    private fun serverDoc(text: String): JsonObject = (Json.parseToJsonElement(text) as KJsonObject).toGson()

    /** The same fixture for the repositories already on kotlinx: the tree the sync manager hands them, numbers as Gson held them. */
    private fun kServerDoc(text: String): KJsonObject = (Json.parseToJsonElement(text) as KJsonObject).withGsonBridgedNumbers()

    private fun user(planetCode: String? = "planet") = UserEntity(
        id = "org.couchdb.user:learner",
        _id = "org.couchdb.user:learner",
        _rev = "1-u",
        name = "learner",
        rolesList = listOf("learner"),
        firstName = TRICKY,
        planetCode = planetCode,
        parentCode = "parent",
    )

    @Test
    fun `resources serializeForUpload`() {
        val repository = relaxedInstance<ResourcesRepositoryImpl>(time, deviceNameProvider)
        val library = MyLibrary().apply {
            title = TRICKY
            createdDate = BIG_LONG
            resourceLocalAddress = "file:///sdcard/ole/my%20file<1>.pdf"
            author = null
            medium = "Text"
            description = "desc & <b>"
            year = "2020"
            language = "Español"
            subject = listOf("Math", "<Art>")
            level = null
            resourceType = "Activities"
            openWith = "PDF.js"
            resourceFor = emptyList()
            isPrivate = true
            privateFor = "team-1"
        }
        golden("resources/serializeForUpload-full", repository.serializeForUpload(library, user()))
        golden("resources/serializeForUpload-sparse", repository.serializeForUpload(MyLibrary(), null))
    }

    @Test
    fun `personals serialize`() {
        val repository = relaxedInstance<PersonalsRepositoryImpl>(time, deviceNameProvider)
        val personal = Personal().apply {
            title = TRICKY
            date = BIG_LONG
            path = "/storage/ole/personal/<my> file.mp4"
            userName = "learner"
            description = "desc"
            userId = "org.couchdb.user:learner"
        }
        golden("personals/serialize-full", repository.callPrivate("serialize", personal))
        golden("personals/serialize-sparse", repository.callPrivate("serialize", Personal()))
    }

    @Test
    fun `teams activity log and task columns`() = runTest {
        val userSessionManager = mockk<UserSessionManager>()
        coEvery { userSessionManager.getUserModel() } returns user(planetCode = null)
        val teamTaskDao = mockk<TeamTaskDao>(relaxed = true)
        val savedTask = slot<TeamTask>()
        coEvery { teamTaskDao.upsert(capture(savedTask)) } returns Unit
        val repository = relaxedInstance<TeamsRepositoryImpl>(userSessionManager, teamTaskDao)

        val log = TeamLog().apply {
            _id = "log-1"
            _rev = "1-l"
            user = "learner"
            type = "teamVisit"
            createdOn = "planet"
            parentCode = "parent"
            teamType = "local"
            time = BIG_LONG
            teamId = "team<1>"
        }
        golden("teams/teamActivity-full", repository.serializeTeamActivities(log))
        golden("teams/teamActivity-sparse", repository.serializeTeamActivities(TeamLog()))

        repository.createTask("title", "desc", 1L, "team <1>", null)
        raw("teams/createTask-columns", "link=${savedTask.captured.link}\nsync=${savedTask.captured.sync}")
    }

    @Test
    fun `voices serializeNews and synced news columns`() = runTest {
        val newsDao = mockk<NewsDao>(relaxed = true)
        coEvery { newsDao.getByUnderscoreIds(any()) } returns emptyList()
        coEvery { newsDao.getByUnderscoreId(any()) } returns null
        val saved = slot<List<News>>()
        coEvery { newsDao.upsertAll(capture(saved)) } returns Unit
        val storedLinks = slot<String>()
        every { sharedPrefManager.getConcatenatedLinks() } returns """["https://old.example/a?b=1&c=<2>"]"""
        every { sharedPrefManager.setConcatenatedLinks(capture(storedLinks)) } returns Unit
        val repository = relaxedInstance<VoicesRepositoryImpl>(sharedPrefManager, newsDao)

        val news = News().apply {
            _id = "news-1"
            _rev = "1-n"
            chat = true
            message = TRICKY
            time = BIG_LONG
            createdOn = "planet"
            docType = "message"
            viewableId = "team-1"
            viewableBy = "teams"
            viewIn = """[{"_id":"team-1","section":"teams","name":"Team <1>"}]"""
            avatar = ""
            messageType = "sync"
            messagePlanetCode = "planet"
            replyTo = ""
            parentCode = "parent"
            images = """[{"resourceId":"r1","markdown":"![<img>](x=1)"}]"""
            labels = listOf("help", "<offer>")
            user = """{"_id":"u1","name":"<n>","age":1.0,"none":null}"""
            newsId = "chat-1"
            newsRev = "1-c"
            newsUser = "learner"
            aiProvider = "openai"
            newsTitle = "title = <x>"
            conversations = """[{"query":"q = 1","response":"<r>"}]"""
            newsCreatedDate = 1L
            newsUpdatedDate = 2L
            sharedBy = "sharer"
        }
        golden("voices/serializeNews-full", repository.callPrivate("serializeNews", news))
        golden("voices/serializeNews-sparse", repository.callPrivate("serializeNews", News()))

        repository.insertNewsList(
            listOf(
                kServerDoc(
                    """
                    {"_id":"n1","_rev":"2-n","message":"see resources/abc and <b>bold</b> & 'x'","time":1717171717171,"updatedDate":1.0,
                     "user":{"_id":"u1","name":"<learner>","age":30,"ratio":0.1,"none":null,"big":9007199254740993},
                     "images":[{"resourceId":"r","markdown":"![x](y)"}],"labels":["a","<b>"],
                     "viewIn":[{"_id":"team_1","section":"teams","name":"T & 'eam'","sharedDate":1717171717171},{"section":"community","_id":"c"}],
                     "chat":"true","news":{"_id":"c1","title":"t","conversations":[{"query":"=","response":"<r>"}],"createdDate":"5"}}
                    """.trimIndent()
                ),
                kServerDoc("""{"_id":"n2"}"""),
            )
        )
        raw(
            "voices/synced-columns",
            saved.captured.joinToString("\n") {
                "user=${it.user}\nimages=${it.images}\nviewIn=${it.viewIn}\nconversations=${it.conversations}\n" +
                    "labels=${it.labels} chat=${it.chat} updatedDate=${it.updatedDate} newsCreatedDate=${it.newsCreatedDate}"
            } + "\nconcatenatedLinks=${storedLinks.captured}"
        )
    }

    @Test
    fun `voices viewIn edits and uploaded images columns`() = runTest {
        val newsDao = mockk<NewsDao>(relaxed = true)
        val upserted = mutableListOf<News>()
        coEvery { newsDao.upsert(capture(upserted)) } returns Unit
        val uploaded = slot<List<News>>()
        coEvery { newsDao.upsertAll(capture(uploaded)) } returns Unit
        // viewIn is text Gson parsed: its number literals are kept as written.
        val viewIn = """[{"_id":"team_1","section":"teams","name":"T & 'eam' <1>","n":1.50,"e":1e2},""" +
            """{"section":"community","_id":"planet@parent"},{"_id":"x","section":"teams","sharedDate":1717171717171}]"""
        coEvery { newsDao.getById("shared") } returns News().apply { id = "shared"; this.viewIn = """[{"_id":"team_1","section":"teams","n":1.50,"e":1e2}]""" }
        coEvery { newsDao.getById("unshared") } returns News().apply { id = "unshared"; this.viewIn = viewIn }
        coEvery { newsDao.getByIds(listOf("up")) } returns listOf(News().apply { id = "up" })
        val repository = relaxedInstance<VoicesRepositoryImpl>(sharedPrefManager, newsDao)

        repository.shareNewsToCommunity("shared", "learner", "", "", "Team <1> & 'x'")
        repository.deletePost("unshared", "")
        val images = (Json.parseToJsonElement(
            """[{"resourceId":"r=1","filename":"a&b <c>.jpg","markdown":"![](resources/r=1/a&b <c>.jpg)"}]"""
        ) as kotlinx.serialization.json.JsonArray)
        repository.markNewsUploaded(listOf(NewsUpdateData("up", "up-id", "1-up", images)))

        raw(
            "voices/viewIn-and-images-columns",
            upserted.joinToString("\n") { "viewIn=${it.viewIn?.replace(Regex("\"sharedDate\":\\d{13}(?=})"), "\"sharedDate\":<now>")} sharedBy=${it.sharedBy}" } +
                "\nimages=${uploaded.captured.single().images}"
        )
    }

    @Test
    fun `courses search activity filter column`() = runTest {
        val searchActivityDao = mockk<SearchActivityDao>(relaxed = true)
        val saved = mutableListOf<SearchActivity>()
        coEvery { searchActivityDao.insert(capture(saved)) } returns Unit
        val repository = relaxedInstance<CoursesRepositoryImpl>(sharedPrefManager, searchActivityDao)
        val tags = listOf(TagEntity().apply { _id = "tag <1> & 'x' = y" }, TagEntity().apply { _id = TRICKY })
        repository.saveSearchActivity(TRICKY, "learner", "planet", "parent", tags, "grade <1>", "")
        repository.saveSearchActivity("", "learner", "planet", "parent", emptyList(), "", "")
        raw("courses/searchActivity-filter", saved.joinToString("\n") { "filter=${it.filter}" })
    }

    @Test
    fun `courses sync payload step ids and question choices`() {
        val repository = relaxedInstance<CoursesRepositoryImpl>(sharedPrefManager)
        val doc = kServerDoc(
            """
            {"_id":"course-1","_rev":"3-c","courseTitle":"Course <1>","description":"plain text, no links","memberLimit":"12","createdDate":1.7E12,
             "steps":[
               {"stepTitle":"Step 'one'","description":"first & <only>","n":1.0,"e":1e3,"big":9007199254740993,"u":"ünï\u2028","resources":[{"_id":"r1","title":"<r>"}],
                "exam":{"_id":"exam-1","name":"Exam","questions":[{"id":"q1","type":"select","body":"B","choices":[{"id":"a","text":"<A> & 'B'","n":1.0}],"correctChoice":"a"},{"title":"no id","choices":[]},{"title":"no choices"}]}},
               {"stepTitle":"two","survey":{"questions":[{"type":"selectMultiple","choices":[{"id":"x","text":"X"}],"correctChoice":["x"]}]}}
             ]}
            """.trimIndent()
        )
        val payload = repository.callPrivate("buildCoursePayload", doc, "shelf-user", emptyMap<String, MyCourse>())!!
        @Suppress("UNCHECKED_CAST")
        fun <T> field(name: String): T = payload.javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            it.get(payload) as T
        }
        val course = field<MyCourse>("course")
        val steps = field<List<CourseStep>>("steps")
        val exams = field<List<StepExam>>("exams")
        val questions = field<List<ExamQuestion>>("questions")
        raw(
            "courses/sync-payload",
            buildString {
                appendLine("course=${course.courseId} rev=${course.courseRev} memberLimit=${course.memberLimit} createdDate=${course.createdDate} userId=${course.userId}")
                steps.forEach { appendLine("step id=${it.id} title=${it.stepTitle} resources=${it.noOfResources}") }
                exams.forEach { appendLine("exam id=${it.id} type=${it.type} stepId=${it.stepId} questions=${it.noOfQuestions}") }
                questions.forEach { appendLine("question id=${it.id} examId=${it.examId} choices=${it.choices} correct=${it.correctChoiceList}") }
            }
        )
    }

    @Test
    fun `user guest document`() = runTest {
        val repository = relaxedInstance<UserRepositoryImpl>(sharedPrefManager)
        golden("user/guest-user", repository.callPrivateSuspend("buildGuestUserJson", "guest <name> & 'x'"))
    }

    @Test
    fun `feedback message columns`() = runTest {
        val feedbackDao = mockk<FeedbackDao>(relaxed = true)
        val updated = slot<Feedback>()
        val upserted = slot<Feedback>()
        coEvery { feedbackDao.update(capture(updated)) } returns Unit
        coEvery { feedbackDao.upsert(capture(upserted)) } returns Unit
        coEvery { feedbackDao.findById("fb-1") } returns Feedback().apply {
            id = "fb-1"
            messages = """[{"message":"first","time":"1","user":"u"}]"""
        }
        coEvery { feedbackDao.findById("fb-sync") } returns null
        val repository = relaxedInstance<FeedbackRepositoryImpl>(feedbackDao, time)

        raw("feedback/createFeedback-messages", repository.createFeedback(null, "true", "Question", TRICKY, "item", "state <x>").messages!!)
        repository.addReply("fb-1", "reply <b> & 'c' =", null)
        raw("feedback/addReply-messages", updated.captured.messages!!)
        repository.insertFromJson(kServerDoc("""{"_id":"fb-sync","openTime":"5","messages":[{"message":"<m>","time":1717171717171,"user":null,"n":1.0}]}"""))
        raw("feedback/synced-messages", "messages=${upserted.captured.messages} openTime=${upserted.captured.openTime}")
    }

    @Test
    fun `ratings user columns`() = runTest {
        val ratingDao = mockk<RatingDao>(relaxed = true)
        val upserted = slot<List<Rating>>()
        coEvery { ratingDao.upsertAll(capture(upserted)) } returns Unit
        val repository = relaxedInstance<RatingsRepositoryImpl>(ratingDao)

        val rating = Rating()
        repository.callPrivate("setRatingData", rating, user(planetCode = null), "resource", "item-1", TRICKY, 3.7f, "nice <3>")
        raw("ratings/local-user-column", "user=${rating.user} rate=${rating.rate}")

        repository.insertRatingsFromSync(
            listOf(kServerDoc("""{"_id":"r1","rate":4.0,"time":"17","user":{"_id":"u1","name":"<u>","none":null,"_attachments":{"a":1}}}"""))
        )
        raw("ratings/synced-user-column", upserted.captured.joinToString("\n") { "user=${it.user} rate=${it.rate} time=${it.time}" })
    }

    @Test
    fun `events created meetup columns`() = runTest {
        val meetupDao = mockk<MeetupDao>(relaxed = true)
        val saved = slot<Meetup>()
        coEvery { meetupDao.upsert(capture(saved)) } returns Unit
        val repository = relaxedInstance<EventsRepositoryImpl>(time, meetupDao)
        repository.createMeetup(
            MeetupCreationParams(TRICKY, TRICKY_URL, "d", "loc", "09:00", "10:00", null, null, "learner", BIG_LONG, 1L, "team <1>")
        )
        raw("events/createMeetup-columns", "sync=${saved.captured.sync}\nlink=${saved.captured.link}")
    }

    @Test
    fun `chat history synced columns`() = runTest {
        val chatDao = mockk<ChatDao>(relaxed = true)
        val saved = slot<List<ChatHistory>>()
        coEvery { chatDao.upsertAll(capture(saved)) } returns Unit
        val repository = relaxedInstance<ChatRepositoryImpl>(chatDao)
        repository.insertChatHistoryFromSync(
            listOf(
                kServerDoc(
                    """{"doc":{"_id":"chat-1","_rev":"2-c","title":"<t> & 'x'","createdDate":1.7E12,"updatedDate":"5","user":"learner","aiProvider":"openai",
                       "conversations":[{"query":"q & <1>","response":1e2},{"query":true,"extra":1},{"response":null},{"query":1.50}]}}"""
                ),
                kServerDoc("""{"doc":{"_id":"_design/x"}}"""),
            )
        )
        raw(
            "chat/synced-columns",
            saved.captured.joinToString("\n") {
                "id=${it.id} rev=${it._rev} title=${it.title} created=${it.createdDate} updated=${it.updatedDate} user=${it.user} ai=${it.aiProvider} " +
                    "conversations=${it.conversations?.joinToString(";") { c -> "${c.query}|${c.response}" }}"
            }
        )
    }

    @Test
    fun `chat request bodies`() = runTest {
        val chatApiService = mockk<ChatApiService>()
        val bodies = mutableListOf<RequestBody>()
        coEvery { chatApiService.sendChatRequest(capture(bodies)) } throws IllegalStateException("offline")
        val repository = relaxedInstance<ChatRepositoryImpl>(chatApiService)
        val provider = AiProvider("openai", "gpt <4> & 'o'")
        repository.sendNewChatRequest(TRICKY, null, provider)
        repository.sendContinueChatRequest("again = <x>", "learner", provider, "chat-1", "2-c")
        raw("chat/request-bodies", bodies.joinToString("\n") { body -> Buffer().also { body.writeTo(it) }.readUtf8() })
    }
}
