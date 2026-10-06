package org.ole.planet.myplanet.model

import android.content.Context
import io.mockk.mockk
import io.mockk.unmockkAll
import java.io.File
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY_URL
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden

/**
 * Golden snapshots of every model-level serializer that builds a document uploaded to Planet.
 * Each has a "full" case (every field set, with tricky strings and edge numbers) and a "sparse"
 * case (nulls, empties and zeros, which is where serializeNulls/omission behaviour shows).
 */
class ModelPayloadGoldenTest {

    private var originalContext: Context? = null
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        originalContext = try {
            MainApplication.context
        } catch (_: Exception) {
            null
        }
        MainApplication.testContext = mockk(relaxed = true)
        tempDir = createTempDir()
    }

    @After
    fun tearDown() {
        MainApplication.testContext = originalContext
        tempDir.deleteRecursively()
        unmockkAll()
    }

    private fun createTempDir(): File = File.createTempFile("golden", "").apply {
        delete()
        mkdirs()
    }

    private fun golden(name: String, actual: Any?) = assertMatchesGolden("model/$name", actual)

    private fun fullUser(): UserEntity = UserEntity(
        id = "org.couchdb.user:learner",
        _id = "org.couchdb.user:learner",
        _rev = "3-abc",
        name = "learner",
        rolesList = listOf("learner", "leader"),
        userAdmin = false,
        joinDate = BIG_LONG,
        firstName = TRICKY,
        lastName = "Ñandú",
        middleName = "",
        email = "a&b@example.org",
        planetCode = "planet<1>",
        parentCode = "parent&1",
        phoneNumber = "+1 (555) 0100",
        password_scheme = "pbkdf2",
        iterations = "10000",
        derived_key = "deadbeef",
        level = "Beginner",
        language = "Español",
        gender = "female",
        salt = "s@lt=",
        dob = "1990-01-01T00:00:00.000Z",
        age = "35",
        birthPlace = "Kathmandu",
        isArchived = true,
    )

    @Test
    fun `ApkLog serialize`() {
        val full = ApkLog().apply {
            id = "log-1"
            userId = "org.couchdb.user:learner"
            type = ApkLog.ERROR_TYPE_CRASH
            error = "java.lang.IllegalStateException: <boom> & 'bang'\n\tat Foo.kt:1"
            page = TRICKY
            parentCode = "parent"
            version = "0.70.64"
            createdOn = "planet"
            time = "1717171717171"
        }
        golden("ApkLog-full", ApkLog.serialize(full, "custom <name>"))
        golden("ApkLog-sparse", ApkLog.serialize(ApkLog(), ""))
    }

    @Test
    fun `NewsLog serialize`() {
        val full = NewsLog().apply {
            userId = "user"
            type = "visit"
            time = BIG_LONG
        }
        golden("NewsLog-full", NewsLog.serialize(full, TRICKY))
        golden("NewsLog-sparse", NewsLog.serialize(NewsLog(), ""))
    }

    @Test
    fun `Rating serializeRating`() {
        val full = Rating().apply {
            _id = "rating-1"
            _rev = "1-a"
            user = """{"_id":"u1","name":"<n>","age":1.0,"nested":{"a":null},"roles":["x"]}"""
            item = "item"
            type = "resource"
            title = TRICKY
            time = BIG_LONG
            comment = "great & 'useful' <3"
            rate = 4
            createdOn = "planet"
            parentCode = "parent"
            planetCode = "planet"
        }
        golden("Rating-full", Rating.serializeRating(full, "custom"))
        golden("Rating-sparse", Rating.serializeRating(Rating(), ""))
    }

    @Test
    fun `CourseProgress serializeProgress`() {
        val full = CourseProgress().apply {
            userId = "user"
            parentCode = "parent"
            courseId = "course"
            passed = true
            stepNum = 7
            createdOn = "planet"
            createdDate = BIG_LONG
            updatedDate = Long.MAX_VALUE
        }
        golden("CourseProgress-full", CourseProgress.serializeProgress(full))
        golden("CourseProgress-sparse", CourseProgress.serializeProgress(CourseProgress()))
    }

    @Test
    fun `Feedback serializeFeedback`() {
        val full = Feedback().apply {
            _id = "fb-1"
            _rev = "2-b"
            title = TRICKY
            source = "planet"
            status = "Open"
            priority = "Yes"
            owner = "learner"
            openTime = BIG_LONG
            type = "Question"
            url = TRICKY_URL
            parentCode = "parent"
            state = "state"
            item = "item"
            messages = """[{"message":"<hi> & 'bye'","time":"1717","user":"learner"},{"message":"x","time":1.5,"user":null}]"""
        }
        golden("Feedback-full", Feedback.serializeFeedback(full))
        golden("Feedback-sparse", Feedback.serializeFeedback(Feedback()))
        golden("Feedback-malformed-messages", Feedback.serializeFeedback(Feedback().apply { messages = "not json [" }))
    }

    @Test
    fun `SubmitPhotos serialize`() {
        val full = SubmitPhotos().apply {
            id = "photo-1"
            submissionId = "sub"
            courseId = "course"
            examId = "exam"
            memberId = "member"
            date = "2024-01-01"
            uniqueId = "mac:aa:bb"
            photoLocation = "/sdcard/<photo>&.jpg"
        }
        golden("SubmitPhotos-full", SubmitPhotos.serialize(full))
        golden("SubmitPhotos-sparse", SubmitPhotos.serialize(SubmitPhotos()))
    }

    @Test
    fun `Meetup serialize`() {
        val full = Meetup().apply {
            meetupId = "meetup-1"
            meetupIdRev = "1-m"
            title = TRICKY
            description = "desc <b>"
            startDate = BIG_LONG
            endDate = 1_717_171_717_171L
            startTime = "09:00"
            endTime = "10:00"
            recurring = "weekly"
            meetupLocation = "Room & 'A'"
            meetupLink = TRICKY_URL
            creator = "learner"
            teamId = "team"
            category = "cat"
            createdDate = 1L
            recurringNumber = 3
            sourcePlanet = "planet"
            sync = """{"type":"local","planetCode":"planet"}"""
            link = """{"teams":"team","n":1.0}"""
        }
        golden("Meetup-full", Meetup.serialize(full))
        golden("Meetup-sparse", Meetup.serialize(Meetup()))
        golden("Meetup-myMeetupIds", Meetup.getMyMeetUpIds(listOf(full, Meetup())))
    }

    @Test
    fun `TeamTask serialize`() {
        val full = TeamTask().apply {
            _id = "task-1"
            _rev = "1-t"
            title = TRICKY
            description = "do <it>"
            deadline = BIG_LONG
            completed = true
            completedTime = 5L
            sync = """{"type":"local","planetCode":"planet"}"""
            link = """{"teams":"team"}"""
        }
        golden("TeamTask-full", TeamTask.serialize(full, fullUser()))
        golden("TeamTask-sparse", TeamTask.serialize(TeamTask(), null))
    }

    @Test
    fun `HealthExamination serialize`() {
        val full = HealthExamination().apply {
            userId = "health-user"
            _rev = "1-h"
            data = "encrypted<blob>=="
            setTemperature(36.6f)
            pulse = 72
            bp = "120/80"
            height = 0.1f
            setWeight(1.5f)
            vision = "20/20"
            hearing = "ok & fine"
            date = BIG_LONG
            isSelfExamination = true
            planetCode = "planet"
            isHasInfo = true
            profileId = "profile"
            gender = "male"
            age = 42
            conditions = """{"Asthma":true,"Diabetes":false,"Other <x>":true}"""
        }
        golden("HealthExamination-full", HealthExamination.serialize(full))
        golden("HealthExamination-sparse", HealthExamination.serialize(HealthExamination()))
    }

    private fun fullTeam(docType: String?, teamType: String? = "local") = MyTeam(
        _id = "team-1",
        _rev = "1-x",
        courses = listOf("course-1", "missing-course"),
        teamId = "parent-team",
        name = TRICKY,
        userId = "user",
        description = "desc <b>&</b>",
        sourcePlanet = "planet",
        limit = 12,
        createdDate = BIG_LONG,
        resourceId = "resource",
        status = "active",
        teamType = teamType,
        teamPlanetCode = "planet",
        userPlanetCode = "planet",
        parentCode = "parent",
        docType = docType,
        title = "title 'x'",
        route = "/teams",
        services = "services",
        createdBy = "creator",
        rules = "rules = none",
        isLeader = true,
        type = "team",
        amount = -5,
        date = 1L,
        isPublic = true,
        beginningBalance = 100,
        sales = 2,
        otherIncome = 3,
        wages = 4,
        otherExpenses = 5,
        startDate = 6L,
        endDate = 7L,
        updatedDate = 8L,
    )

    @Test
    fun `MyTeam serialize`() {
        golden("MyTeam-team-full", MyTeam.serialize(fullTeam("team")))
        golden("MyTeam-team-sparse", MyTeam.serialize(MyTeam()))
        golden("MyTeam-report", MyTeam.serialize(fullTeam("report")))
        golden("MyTeam-request", MyTeam.serialize(fullTeam("request")))
        golden("MyTeam-transaction-credit", MyTeam.serialize(fullTeam("transaction", teamType = "credit")))
        golden("MyTeam-resourceLink", MyTeam.serialize(fullTeam("resourceLink")))
        golden("MyTeam-deletePending", MyTeam.serialize(fullTeam("team").apply { isDeletePending = true }))
    }

    @Test
    fun `MyTeam serialize with courses`() {
        val course = fullCourse()
        val resources = mapOf("course-1" to mapOf<String?, List<MyLibrary>>("step-1" to listOf(fullLibrary())))
        golden("MyTeam-with-courses", MyTeam.serialize(fullTeam("team"), listOf(course), resources))
    }

    @Test
    fun `UserEntity serialize existing user`() {
        val image = File(tempDir, "avatar.jpg").apply { writeBytes(byteArrayOf(0, 1, 2, -1, -2, 60, 62)) }
        golden("UserEntity-existing", fullUser().apply { userImage = image.absolutePath }.serialize())
        golden("UserEntity-sparse", UserEntity().serialize())
    }

    @Test
    fun `UserEntity serialize new user`() {
        val newUser = fullUser().apply {
            _id = ""
            password = "p@ss<word>&'"
            iterations = "not-a-number"
            userAdmin = null
            userImage = "https://remote/image.png"
        }
        golden("UserEntity-new", newUser.serialize())
    }

    private fun fullLibrary(): MyLibrary = MyLibrary().apply {
        _id = "res-1"
        _rev = "1-r"
        needsOptimization = true
        resourceFor = listOf("learner", "<leader>")
        publisher = "OLE & co"
        linkToLicense = TRICKY_URL
        addedBy = "admin"
        uploadDate = "2024-01-01"
        openWith = "PDF.js"
        subject = listOf("Math", "Science")
        kind = "Resource"
        medium = "Text"
        language = "Français"
        author = TRICKY
        sum = 9
        level = emptyList()
        languages = listOf("fr")
        tag = null
        timesRated = 3
        year = "2020"
        title = "Title <1>"
        averageRating = "4.5"
        filename = "file name.pdf"
        mediaType = "pdf"
        description = "desc"
        resourceLocalAddress = "local/file name.pdf"
    }

    @Test
    fun `MyLibrary serializeResource`() {
        golden("MyLibrary-full", fullLibrary().serializeResource())
        golden("MyLibrary-sparse", MyLibrary().serializeResource())
    }

    private fun fullCourse(): MyCourse = MyCourse(
        id = "course-1",
        courseId = "course-1",
        courseRev = "2-c",
        languageOfInstruction = "English",
        courseTitle = TRICKY,
        description = "desc <b>",
        method = "online",
        gradeLevel = "1",
        subjectLevel = "beginner",
        createdDate = BIG_LONG,
        memberLimit = 0,
        coverFileName = "cover <1>.png",
    ).apply {
        courseSteps = mutableListOf(
            CourseStep(id = "step-1", stepTitle = "Step 'one'", description = "first & only"),
            CourseStep(id = "step-2"),
        )
    }

    @Test
    fun `MyCourse serialize`() {
        val resources = mapOf<String?, List<MyLibrary>>("step-1" to listOf(fullLibrary(), MyLibrary()))
        golden("MyCourse-full", MyCourse.serialize(fullCourse(), resources))
        golden("MyCourse-sparse", MyCourse.serialize(MyCourse(), emptyMap()))
    }

    @Test
    fun `Achievement serialize`() {
        val full = Achievement().apply {
            _id = "ach-1"
            _rev = "1-a"
            goals = TRICKY
            purpose = "purpose <x>"
            achievementsHeader = "header"
            sendToNation = "true"
            dateSortOrder = "asc"
            createdOn = "planet"
            username = "learner"
            parentCode = "parent"
            achievements = listOf("""{"title":"<won>","date":"2024","n":1.0}""", "\"plain string\"")
            references = listOf("""{"name":"ref","phone":"123"}""")
            links = listOf("""{"title":"site","url":"https://x.org/?a=1&b=2"}""")
            otherInfo = emptyList()
            resumeFileName = "cv.pdf"
        }
        golden("Achievement-full", Achievement.serialize(full))
        golden("Achievement-sparse", Achievement.serialize(Achievement()))
        golden("Achievement-createReference", Achievement.createReference(TRICKY, "friend", "", "a@b.c"))
        golden("Achievement-createReference-null-name", Achievement.createReference(null, "", "", ""))
    }

    private fun questions(): List<ExamQuestion> = listOf(
        ExamQuestion(
            id = "q1",
            header = "Header <1>",
            body = TRICKY,
            type = "selectMultiple",
            examId = "exam-1",
            correctChoiceList = listOf("a", "b & c"),
            marks = "1",
            choices = """[{"id":"a","text":"<A>"},{"id":"b","text":"B","n":1.0}]""",
            hasOtherOption = true,
        ),
        ExamQuestion(id = "q2", choices = null),
        ExamQuestion(id = "q3", choices = "   "),
    )

    @Test
    fun `ExamQuestion serializeQuestions`() {
        golden("ExamQuestion-questions", ExamQuestion.serializeQuestions(questions()))
    }

    @Test
    fun `StepExam serializeExam`() {
        val full = StepExam(
            id = "exam-1",
            _rev = "3-e",
            createdDate = BIG_LONG,
            updatedDate = 2L,
            adoptionDate = 0L,
            createdBy = "admin",
            totalMarks = 10,
            name = TRICKY,
            description = "desc",
            type = "surveys",
            passingPercentage = "50",
            sourcePlanet = "planet",
            teamId = "team",
            sourceSurveyId = "survey-src",
        )
        golden("StepExam-full", StepExam.serializeExam(full, questions()))
        golden("StepExam-sparse", StepExam.serializeExam(StepExam(), emptyList()))
    }

    @Test
    fun `Answer serializeAnswer`() {
        val answers = listOf(
            Answer(id = "a1", value = TRICKY, mistakes = 2, isPassed = true, questionId = "q1"),
            Answer(id = "a2", value = "", valueChoices = listOf("""{"id":"a","text":"<A>"}""", """{"id":"b","n":1.0}"""), questionId = ""),
            Answer(id = "a3"),
        )
        golden("Answer-answers", Answer.serializeAnswer(answers))
    }

    @Test
    fun `Answer serializeAnswer throws on a stored null choice`() {
        // Gson 2.14 rejected a top-level JSON null for JsonObject (JsonSyntaxException); the kotlinx parse keeps
        // that rejection (SerializationException). No caller catches either, so only the throw itself is pinned.
        assertThrows(RuntimeException::class.java) {
            Answer.serializeAnswer(listOf(Answer(id = "a", valueChoices = listOf("null"))))
        }
    }

    @Test
    fun `UserSurveyProfile toJson`() {
        val full = UserSurveyProfile(
            fname = TRICKY,
            lname = "Last",
            mName = "Mid",
            email = "a&b@c.d",
            phone = "+1 <555>",
            dob = "",
            yob = "",
            level = "Beginner",
            gender = "female",
            language = "Nepali",
        )
        golden("UserSurveyProfile-full", full.toJson())
        golden("UserSurveyProfile-empty", UserSurveyProfile("", "", "", "", "", "", "", "", "", "").toJson())
    }
}
