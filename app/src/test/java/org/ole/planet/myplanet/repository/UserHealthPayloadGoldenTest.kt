package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.HealthExaminationDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.LearnerRegistrationInfo
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.AndroidDecrypter
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.GoldenFixtures
import org.ole.planet.myplanet.utils.GoldenFixtures.BIG_LONG
import org.ole.planet.myplanet.utils.GoldenFixtures.TRICKY
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesGolden
import org.ole.planet.myplanet.utils.JsonGolden.assertMatchesRawGolden
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.TestDispatcherProvider
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.relaxedInstance
import org.ole.planet.myplanet.utils.withGsonBridgedNumbers
import retrofit2.Response

/**
 * Goldens for what the user and health repositories send to CouchDB (membership, `_users`,
 * `_security`, key/iv, shelf and health documents, pinned at [ApiInterface]), the user columns
 * they write from a user document, and the health profile plaintext before encryption (pinned by
 * stubbing [AndroidDecrypter], since the ciphertext is not stable).
 */
class UserHealthPayloadGoldenTest {

    private val api = mockk<ApiInterface>()
    private val userDao = mockk<UserDao>(relaxed = true)
    private val users = linkedMapOf<String, UserEntity>()
    private val upserted = mutableListOf<UserEntity>()
    private val sharedPrefManager = mockk<SharedPrefManager>(relaxed = true)
    private val settings = mockk<SharedPreferences>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val configurationsRepository = mockk<ConfigurationsRepository>(relaxed = true)
    private val resourcesRepository = mockk<ResourcesRepository>(relaxed = true)
    private val coursesRepository = mockk<CoursesRepository>(relaxed = true)
    private val eventsRepository = mockk<EventsRepository>(relaxed = true)
    private val removedLogDao = mockk<RemovedLogDao>(relaxed = true)
    private val deviceNameProvider = mockk<DeviceNameProvider>(relaxed = true)
    private val calls = mutableListOf<JsonObject>()
    private var originalContext: Context? = null

    @Before
    fun setUp() {
        GoldenFixtures.mockDeviceIdentity()
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "https://admin:secret@planet.example/db"
        every { UrlUtils.header } returns "Basic golden"
        mockkObject(SecurePrefs)
        every { SecurePrefs.getPassword(any(), any()) } returns "p@ss"
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers { firstArg<CharSequence?>().isNullOrEmpty() }
        every { deviceNameProvider.getCustomDeviceName() } returns GoldenFixtures.CUSTOM_DEVICE_NAME
        mockkObject(AndroidDecrypter.Companion)
        every { AndroidDecrypter.generateKey() } returns "generated-key"
        every { AndroidDecrypter.generateIv() } returns "generated-iv"
        every { sharedPrefManager.getPlanetCode() } returns "planet"
        every { sharedPrefManager.getParentCode() } returns "parent"
        every { sharedPrefManager.getUserId() } returns "org.couchdb.user:learner"
        originalContext = try {
            MainApplication.context
        } catch (_: Exception) {
            null
        }
        MainApplication.testContext = context
        coEvery { userDao.getById(any()) } answers { users[firstArg()] }
        coEvery { userDao.getByName(any()) } answers { users.values.firstOrNull { it.name == firstArg<String>() } }
        coEvery { userDao.getGuestUserByName(any()) } answers {
            users.values.firstOrNull { it.name == firstArg<String>() && it._id?.startsWith("guest_") == true }
        }
        coEvery { userDao.getUsersByAnyIds(any()) } answers {
            val ids = firstArg<List<String>>()
            users.values.filter { it.id in ids || it._id in ids }
        }
        coEvery { userDao.getGuestUsersByNames(any()) } answers {
            val names = firstArg<List<String>>()
            users.values.filter { it.name in names && it._id?.startsWith("guest_") == true }
        }
        coEvery { userDao.upsert(any()) } answers {
            val user = firstArg<UserEntity>()
            users[user.id] = user
            upserted.add(user)
        }
        coEvery { userDao.upsertAll(any()) } answers {
            firstArg<List<UserEntity>>().forEach { user ->
                users[user.id] = user
                upserted.add(user)
            }
        }
    }

    @After
    fun tearDown() {
        MainApplication.testContext = originalContext
        unmockkAll()
    }

    private fun lazyOf(value: Any): Lazy<Any> = Lazy { value }

    @Suppress("UNCHECKED_CAST")
    private fun userRepository(scope: TestScope): UserRepositoryImpl {
        val dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(scope.testScheduler))
        return UserRepositoryImpl(
            settings,
            sharedPrefManager,
            api,
            lazyOf(resourcesRepository) as Lazy<ResourcesRepository>,
            lazyOf(coursesRepository) as Lazy<CoursesRepository>,
            mockk(relaxed = true),
            context,
            configurationsRepository,
            scope,
            dispatchers,
            mockk(relaxed = true),
            lazyOf(eventsRepository) as Lazy<EventsRepository>,
            mockk(relaxed = true),
            removedLogDao,
            mockk(relaxed = true),
            userDao,
            mockk(relaxed = true),
            deviceNameProvider,
        )
    }

    private fun parse(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    /** A document as the user repository receives it: the API's kotlinx tree, numbers as the Gson bridge held them. */
    private fun userDoc(text: String): JsonObject = parse(text).withGsonBridgedNumbers()

    private fun record(method: String, header: String?, url: String?, body: JsonObject?) {
        calls.add(buildJsonObject {
            put("method", method)
            put("header", header)
            put("url", url)
            put("body", body?.masked() ?: kotlinx.serialization.json.JsonNull)
        })
    }

    /** The clock-driven members (`joinDate` of a new member, `createdOn` of a key/iv doc) as `<now>`. */
    private fun JsonObject.masked(): JsonObject = JsonObject(mapValues { (key, value) ->
        if ((key == "joinDate" || key == "createdOn") && value is JsonPrimitive && !value.isString && value.content.length == 13) {
            JsonPrimitive("<now>")
        } else {
            value
        }
    })

    private fun stubWrites(putResponse: (String?) -> JsonObject, postResponse: (String?) -> JsonObject) {
        coEvery { api.putDoc(any(), any(), any(), any()) } answers {
            record("PUT", firstArg(), thirdArg(), arg(3))
            Response.success(putResponse(thirdArg()))
        }
        coEvery { api.postDoc(any(), any(), any(), any()) } answers {
            record("POST", firstArg(), thirdArg(), arg(3))
            Response.success(postResponse(thirdArg()))
        }
    }

    private fun columns(user: UserEntity, maskJoinDate: Boolean = false): String = with(user) {
        "id=$id _id=$_id _rev=$_rev name=$name roles=$rolesList admin=$userAdmin joinDate=${if (maskJoinDate) "<now>" else joinDate} " +
            "first=$firstName last=$lastName middle=$middleName email=$email phone=$phoneNumber level=$level language=$language " +
            "gender=$gender dob=$dob birthPlace=$birthPlace age=$age planet=$planetCode parent=$parentCode password=$password " +
            "scheme=$password_scheme iterations=$iterations derived=$derived_key salt=$salt archived=$isArchived image=$userImage key=$key iv=$iv"
    }

    private fun String.maskUuid() = replace(Regex("id=[0-9a-f-]{36} "), "id=<uuid> ")

    private val member = LearnerRegistrationInfo(
        username = "newbie",
        password = "p@ss=<word>&'",
        rePassword = "p@ss=<word>&'",
        fName = TRICKY,
        lName = "Ñandú",
        mName = "",
        email = "a&b@example.org",
        language = "Español",
        level = "<1>",
        phoneNumber = "+1 (555)",
        birthDate = "2000-01-01",
        gender = "female",
    )

    /** The `_users` document the server hands back, with every lexical form [UserRepositoryImpl] reads. */
    private val serverUser = """
        {"_id":"org.couchdb.user:newbie","_rev":"1-u","name":"newbie","roles":["learner",1.50,true,null,{"a":1},"<r>"],
         "isUserAdmin":"TRUE","joinDate":1.7E12,"firstName":${Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(TRICKY))},
         "lastName":5,"middleName":null,"email":"a&b@x","phoneNumber":"","level":"1","language":"Español","gender":"f",
         "birthDate":"2000-01-01","birthPlace":"<p>","age":30,"planetCode":"planet","parentCode":"parent","password":"pw",
         "password_scheme":"pbkdf2","iterations":10,"derived_key":"dk","salt":"s","isArchived":"false",
         "_attachments":{"img <1>.png":{"content_type":"image/png"},"b.png":{}}}
    """.trimIndent()

    @Test
    fun `membership created online`() = runTest {
        coEvery { configurationsRepository.checkServerAvailability() } returns true
        coEvery { api.getJsonObject(any(), any()) } answers {
            val url = secondArg<String>()
            when {
                url.endsWith("/_users/org.couchdb.user:newbie") && calls.isEmpty() -> Response.success(parse("""{"error":"not_found"}"""))
                url.endsWith("/_users/org.couchdb.user:newbie") -> Response.success(parse(serverUser))
                url.endsWith("/_security") -> Response.success(parse("""{"admins":{"names":[],"roles":["_admin"]},"members":{"names":["newbie"],"roles":["learner",1.50,null]}}"""))
                else -> Response.success(parse("{}"))
            }
        }
        stubWrites(
            putResponse = { url -> if (url!!.contains("/_users/")) parse("""{"ok":true,"id":"org.couchdb.user:newbie","rev":"1-u"}""") else parse("""{"ok":true}""") },
            postResponse = { parse("""{"ok":true,"id":"keys","rev":"1-k"}""") },
        )
        val repository = userRepository(TestScope(UnconfinedTestDispatcher(testScheduler)))
        repository.createMember(member)
        assertMatchesGolden("repository/user/membership-online-calls", JsonArray(calls))
        assertMatchesRawGolden("repository/user/membership-online-columns", upserted.joinToString("\n") { columns(it) })
    }

    @Test
    fun `membership created offline`() = runTest {
        coEvery { configurationsRepository.checkServerAvailability() } returns false
        val repository = userRepository(TestScope(UnconfinedTestDispatcher(testScheduler)))
        repository.createMember(member)
        assertMatchesRawGolden("repository/user/membership-offline-columns", upserted.joinToString("\n") { columns(it, maskJoinDate = true).maskUuid() })
    }

    @Test
    fun `user columns from a synced user document`() = runTest {
        users["guest_newbie"] = UserEntity(id = "guest_newbie", _id = "guest_newbie", name = "newbie", firstName = "Guest", email = "guest@x")
        val repository = userRepository(TestScope(UnconfinedTestDispatcher(testScheduler)))
        repository.saveUser(userDoc(serverUser), "k", "v")
        repository.saveUser(userDoc("""{"name":"bare","roles":"learner","joinDate":"17","isUserAdmin":1,"_attachments":{}}"""))
        repository.insertUsersFromSync(
            listOf(
                userDoc("""{"doc":$serverUser}"""),
                userDoc("""{"doc":{"_id":"_design/users"}}"""),
                userDoc("""{"doc":{"_id":"org.couchdb.user:other","name":"other","joinDate":1717171717171,"isArchived":true,"_attachments":[1]}}"""),
            )
        )
        assertMatchesRawGolden("repository/user/synced-columns", upserted.joinToString("\n") { columns(it).maskUuid() })
    }

    @Test
    fun `users uploaded and updated`() = runTest {
        coEvery { api.getJsonObject(any(), any()) } answers {
            Response.success(parse("""{"_id":"org.couchdb.user:learner","_rev":"7-l","password_scheme":"pbkdf2","derived_key":1.50,"salt":"s","iterations":10}"""))
        }
        stubWrites(
            putResponse = { parse("""{"ok":true,"id":"org.couchdb.user:learner","rev":"8-l"}""") },
            postResponse = { parse("""{"ok":true}""") },
        )
        val repository = userRepository(TestScope(UnconfinedTestDispatcher(testScheduler)))
        val fresh = UserEntity(
            id = "local-1", _id = "", name = "learner", rolesList = listOf("learner", "<x>"), password = "p@ss<word>&'",
            firstName = TRICKY, joinDate = BIG_LONG, planetCode = "planet", iterations = "not-a-number", userAdmin = null,
        )
        users[fresh.id] = fresh
        repository.uploadNewUser(fresh) { _, _ -> }
        val existing = UserEntity(
            id = "org.couchdb.user:learner", _id = "org.couchdb.user:learner", _rev = null, name = "learner",
            rolesList = listOf("learner"), firstName = null, lastName = "<l>", joinDate = 1L, planetCode = "planet", isUpdated = true,
        )
        users[existing.id] = existing
        repository.updateExistingUser("Basic golden", existing)
        assertMatchesGolden("repository/user/upload-and-update-calls", JsonArray(calls))
    }

    @Test
    fun `shelf documents`() = runTest {
        coEvery { resourcesRepository.getMyLibIds(any()) } returns JsonArray(listOf(JsonPrimitive("r1"), JsonPrimitive("r2")))
        coEvery { coursesRepository.getMyCourseIds(any()) } returns JsonArray(listOf(JsonPrimitive("c1")))
        coEvery { eventsRepository.getMeetupsForUser(any()) } returns listOf(Meetup().apply { meetupId = "m1" })
        coEvery { removedLogDao.getRemovedDocIds("resources", any()) } returns listOf("removed-r")
        coEvery { removedLogDao.getRemovedDocIds("courses", any()) } returns listOf("c2")
        coEvery { api.getJsonObject(any(), any()) } returns Response.success(
            parse("""{"_id":"org.couchdb.user:learner","_rev":"5-s","resourceIds":["r2","r3","removed-r"],"courseIds":["c1","c2","c3"],"meetupIds":["m9","m1"]}""")
        )
        stubWrites(
            putResponse = { parse("""{"ok":true}""") },
            postResponse = {
                parse(
                    """{"rows":[{"id":"a","doc":{"_id":"a","resourceIds":["r"]}},{"id":"b","doc":{"_id":"b","courseIds":[],"meetupIds":{}}},
                       {"id":"c","doc":{"_id":"c","teamIds":["t"]}},{"id":"d","error":"not_found"}]}"""
                )
            },
        )
        val repository = userRepository(TestScope(UnconfinedTestDispatcher(testScheduler)))
        repository.uploadShelfData(UserEntity(id = "org.couchdb.user:learner", _id = "org.couchdb.user:learner"))
        val withData = repository.checkShelfBatchForDataOptimized(listOf("a", "b <2>", "c", "d"))
        calls.add(buildJsonObject { put("shelvesWithData", JsonArray(withData.map { JsonPrimitive(it) })) })
        assertMatchesGolden("repository/user/shelf-calls", JsonArray(calls))
    }

    @Test
    fun `user security data read from tablet users`() = runTest {
        users["learner"] = UserEntity(id = "learner", name = "learner")
        coEvery { api.getJsonObject(any(), any()) } returns Response.success(
            parse("""{"_id":"org.couchdb.user:learner","_rev":"3-t","derived_key":"dk","salt":1.50,"password_scheme":true,"iterations":10}""")
        )
        val repository = userRepository(TestScope(UnconfinedTestDispatcher(testScheduler)))
        repository.fetchUserSecurityData("learner")
        assertMatchesRawGolden("repository/user/security-data-columns", upserted.joinToString("\n") { columns(it) })
    }

    private val healthDao = mockk<HealthExaminationDao>(relaxed = true)
    private val plaintexts = mutableListOf<String>()

    private fun healthRepository(scope: TestScope, userRepository: UserRepository): HealthRepositoryImpl {
        every { AndroidDecrypter.encrypt(any(), any(), any()) } answers {
            plaintexts.add(firstArg())
            "cipher-${plaintexts.size}"
        }
        @Suppress("UNCHECKED_CAST")
        return relaxedInstance<HealthRepositoryImpl>(
            api,
            TestDispatcherProvider(UnconfinedTestDispatcher(scope.testScheduler)),
            healthDao,
            lazyOf(userRepository) as Lazy<UserRepository>,
        )
    }

    private val storedProfile = """
        {"profile":{"emergencyContactName":"<n> & 'x'","emergencyContactType":1.50,"emergencyContact":true,"specialNeeds":"needs","notes":"old","extra":1},
         "userKey":"k & <1>","lastExamination":"1717","unknown":[1,{"a":null}]}
    """.trimIndent()

    @Test
    fun `health profile plaintext`() = runTest {
        val userRepository = mockk<UserRepository>(relaxed = true)
        val user = UserEntity(id = "u1", _id = "org.couchdb.user:u1", key = "key", iv = "iv")
        coEvery { userRepository.getUserById("u1") } returns user
        coEvery { userRepository.getUserById("u2") } returns UserEntity(id = "u2", _id = "org.couchdb.user:u2")
        coEvery { healthDao.getByIdOrUserId("u1") } returns HealthExamination().apply { _id = "h1"; data = "stored" }
        coEvery { healthDao.getByIdOrUserId("u2") } returns null
        every { AndroidDecrypter.decrypt("stored", any(), any()) } returns storedProfile
        val repository = healthRepository(TestScope(UnconfinedTestDispatcher(testScheduler)), userRepository)
        repository.updateUserHealthProfile(
            "u1",
            mapOf("firstName" to " A ", "emergencyContactName" to TRICKY, "emergencyContact" to "", "notes" to " <note> = 'x' ", "dob" to "01-02-2000"),
        )
        repository.updateUserHealthProfile("u2", mapOf("emergencyContactType" to "phone", "specialNeeds" to "none"))
        assertMatchesRawGolden("repository/health/profile-plaintext", plaintexts.joinToString("\n"))
    }

    @Test
    fun `health profile decoded`() = runTest {
        val userRepository = mockk<UserRepository>(relaxed = true)
        val repository = healthRepository(TestScope(UnconfinedTestDispatcher(testScheduler)), userRepository)
        val texts = listOf(
            storedProfile,
            """{"lastExamination":1e3,"userKey":5,"profile":{}}""",
            """{'profile':{notes:unquoted},"lastExamination":"17"}""",
            """{"lastExamination":1.5}""",
            """{"profile":[]}""",
            """{"profile":{"notes":{"a":1}}}""",
            """[]""",
            """""",
            """{"userKey":false,"lastExamination":-2}""",
        )
        val decoded = texts.mapIndexed { index, text ->
            every { AndroidDecrypter.decrypt("data-$index", any(), any()) } returns text
            val health = repository.getDecryptedHealth(HealthExamination().apply { data = "data-$index" }, UserEntity())
            if (health == null) {
                "$index: null"
            } else {
                val profile = health.profile
                "$index: userKey=${health.userKey} lastExamination=${health.lastExamination} profile=" +
                    (profile?.let { "${it.emergencyContactName}|${it.emergencyContactType}|${it.emergencyContact}|${it.specialNeeds}|${it.notes}" } ?: "null")
            }
        }
        assertMatchesRawGolden("repository/health/profile-decoded", decoded.joinToString("\n"))
    }

    @Test
    fun `health conditions, uploads and record creators`() = runTest {
        val userRepository = mockk<UserRepository>(relaxed = true)
        val requestedIds = slot<List<String>>()
        coEvery { userRepository.getUsersByIds(capture(requestedIds)) } returns emptyList()
        val repository = healthRepository(TestScope(UnconfinedTestDispatcher(testScheduler)), userRepository)
        val conditions = repository.getExaminationConditions(
            HealthExamination().apply { this.conditions = """{"a":true,"b":"TRUE","c":1,"d":null,"e":{"x":1},"f":[true],"g":"yes","h":false}""" }
        )

        coEvery { api.postDoc(any(), any(), any(), any()) } answers {
            val body = arg<JsonObject>(3)
            record("POST", firstArg(), thirdArg(), body)
            when ((body["_id"] as? JsonPrimitive)?.content) {
                "u1" -> Response.success(parse("""{"ok":true,"id":"h1","rev":"2-h"}"""))
                "u2" -> Response.success(parse("""{"ok":true,"id":"h2","rev":5}"""))
                "u3" -> Response.success(parse("""{"ok":true,"id":"h3"}"""))
                else -> Response.success(parse("""{"ok":true}"""))
            }
        }
        val uploaded = repository.uploadHealthData(
            listOf(
                HealthExamination().apply {
                    _id = "e1"; userId = "u1"; _rev = "1-e"; data = "cipher"; setTemperature(36.6f); pulse = 70; bp = "120/80"; height = 170.5f
                    setWeight(60f); vision = "<20/20>"; hearing = "ok & fine"; date = BIG_LONG; isSelfExamination = true; planetCode = "planet"
                    isHasInfo = true; profileId = "p1"; gender = "f"; age = 30; this.conditions = """{"Cold":true,"n":1.50,"e":1e2}"""
                },
                HealthExamination().apply { _id = "e2"; userId = "u2" },
                HealthExamination().apply { _id = "e3"; userId = "u3" },
                HealthExamination().apply { _id = "e4"; userId = "u4" },
            )
        )

        val currentUser = UserEntity(id = "me", key = "key", iv = "iv")
        coEvery { healthDao.getByIdOrUserId("patient") } returns HealthExamination().apply { _id = "patient"; data = "profile" }
        every { AndroidDecrypter.decrypt("profile", any(), any()) } returns """{"userKey":"pk","profile":{}}"""
        coEvery { healthDao.getByProfileId("pk") } returns listOf("""{"createdBy":"<a>"}""", """{"createdBy":1.50}""", """{"createdBy":true}""", """{"x":1}""", """{"createdBy":"<a>"}""")
            .mapIndexed { index, text ->
                every { AndroidDecrypter.decrypt("exam-$index", any(), any()) } returns text
                HealthExamination().apply { _id = "x$index"; data = "exam-$index" }
            }
        repository.getPatientHealthRecords("patient", currentUser)

        assertMatchesGolden("repository/health/upload-calls", JsonArray(calls))
        assertMatchesRawGolden(
            "repository/health/conditions-uploads-creators",
            "conditions=${conditions.toSortedMap()}\nuploaded=${uploaded.toSortedMap()}\ncreators=${requestedIds.captured}"
        )
        assertTrue(plaintexts.isEmpty())
    }
}
