package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.MyTeam
import org.ole.planet.myplanet.model.TeamLog
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.TestTimeProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsRepositoryImplTest {

    private lateinit var teamsRepository: TeamsRepositoryImpl
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val uploadManager: UploadManager = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val preferences: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk()
    private val apiInterfaceMock = mockk<ApiInterface>(relaxed = true)
    private val teamLogDao: TeamLogDao = mockk(relaxed = true)
    private val teamTaskDao: TeamTaskDao = mockk(relaxed = true)
    private val myLibraryDao: MyLibraryDao = mockk(relaxed = true)
    private val teamDao: TeamDao = mockk(relaxed = true)
    private val courseDao: CourseDao = mockk(relaxed = true)
    private val courseStepDao: CourseStepDao = mockk(relaxed = true)
    private val appDatabase: AppDatabase = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        val serverUrlMapping = mockk<ServerUrlMapper.UrlMapping>(relaxed = true)
        every { serverUrlMapping.primaryUrl } returns "http://primary.com"
        every { serverUrlMapping.alternativeUrl } returns null
        coEvery { serverUrlMapper.processUrl(any()) } returns serverUrlMapping
        every { sharedPrefManager.getServerUrl() } returns "http://test.com"

        teamsRepository = TeamsRepositoryImpl(
            mockk<android.content.Context>(relaxed = true),
            activitiesRepository,
            userSessionManager,
            uploadManager,
            gson,
            preferences,
            sharedPrefManager,
            serverUrlMapper,
            dispatcherProvider,
            userRepository,
            dagger.Lazy { mockk<ResourcesRepository>(relaxed = true) },
            TestTimeProvider(),
            teamLogDao,
            teamTaskDao,
            myLibraryDao,
            teamDao,
            courseDao,
            courseStepDao,
            appDatabase,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `test refreshJoinedMembersForLogin uses getJoinedMembers and saves users`() = runTest(testDispatcher) {
        val teamId = "test_team_id"
        val mockUser = UserEntity().apply {
            name = "Test User"
            userImage = "http://example.com/image.png"
        }
        val mockTeamMembers = listOf(mockUser)

        val spyRepository = io.mockk.spyk(teamsRepository)
        coEvery { spyRepository.getJoinedMembers(teamId) } returns mockTeamMembers

        val existingSavedUsers = emptyList<User>()
        every { sharedPrefManager.getSavedUsers() } returns existingSavedUsers
        every { sharedPrefManager.setSavedUsers(any()) } returns Unit

        val result = spyRepository.refreshJoinedMembersForLogin(teamId)

        org.junit.Assert.assertEquals(mockTeamMembers, result)

        io.mockk.verify {
            sharedPrefManager.getSavedUsers()
            sharedPrefManager.setSavedUsers(match { list ->
                list.size == 1 && list[0].name == "Test User" && list[0].image == "http://example.com/image.png" && list[0].source == "team"
            })
        }
    }

    @Test
    fun `test recordTeamActivity delegates to syncTeamActivities`() = runTest(testDispatcher) {
        io.mockk.mockkObject(org.ole.planet.myplanet.MainApplication.Companion)
        coEvery { org.ole.planet.myplanet.MainApplication.Companion.isServerReachable(any()) } returns true

        coEvery { uploadManager.uploadResource(any()) } returns Unit
        coEvery { uploadManager.uploadTeams() } returns Unit
        coEvery { uploadManager.uploadTeamActivities() } returns Unit

        teamsRepository.recordTeamActivity()

        advanceUntilIdle()

        coVerify { uploadManager.uploadResource(null) }
        coVerify { uploadManager.uploadTeams() }
        coVerify { uploadManager.uploadTeamActivities() }

        io.mockk.unmockkObject(org.ole.planet.myplanet.MainApplication.Companion)
    }

    @Test
    fun `test syncTeamActivities uses dispatcherProvider io`() = runTest(testDispatcher) {
        io.mockk.mockkObject(org.ole.planet.myplanet.MainApplication.Companion)
        coEvery { org.ole.planet.myplanet.MainApplication.Companion.isServerReachable(any()) } returns true

        coEvery { uploadManager.uploadResource(any()) } returns Unit
        coEvery { uploadManager.uploadTeams() } returns Unit
        coEvery { uploadManager.uploadTeamActivities() } returns Unit

        teamsRepository.syncTeamActivities()

        advanceUntilIdle()

        coVerify { uploadManager.uploadResource(null) }
        coVerify { uploadManager.uploadTeams() }
        coVerify { uploadManager.uploadTeamActivities() }

        io.mockk.unmockkObject(org.ole.planet.myplanet.MainApplication.Companion)
    }

    @Test
    fun `test getTasksFlow delegates to teamTaskDao`() = runTest(testDispatcher) {
        val userId = "test_user_id"
        val mockTasks = listOf(TeamTask())
        val mockFlow = flowOf(mockTasks)
        every { teamTaskDao.getOpenTasksForUser(userId) } returns mockFlow

        val resultFlow = teamsRepository.getTasksFlow(userId)

        val resultList = resultFlow.first()
        assertEquals(mockTasks, resultList)
    }

    @Test
    fun `test getMyTeamDetailsFlow returns mapped flow of team details`() = runTest(testDispatcher) {
        // Arrange
        val userId = "user_id_1"
        val team1Id = "team_id_1"
        val team2Id = "team_id_2"

        // Membership for team 1
        val membership1 = MyTeam().apply {
            _id = "mem1"
            this.userId = userId
            this.teamId = team1Id
            this.docType = "membership"
        }

        // Membership for team 2
        val membership2 = MyTeam().apply {
            _id = "mem2"
            this.userId = userId
            this.teamId = team2Id
            this.docType = "membership"
        }

        // Root teams
        val team1 = MyTeam().apply {
            _id = team1Id
            this.teamId = "" // Empty string or null makes it a root team
            this.status = "active"
            this.name = "Team 1"
        }

        val team2 = MyTeam().apply {
            _id = team2Id
            this.teamId = null // Empty string or null makes it a root team
            this.status = "archived" // Should be filtered out
            this.name = "Team 2"
        }

        // Extra team user is not a member of
        val team3 = MyTeam().apply {
            _id = "team_id_3"
            this.teamId = null
            this.status = "active"
        }

        val allEntities = listOf(membership1, membership2, team1, team2, team3)
        every { teamDao.observeAll() } returns kotlinx.coroutines.flow.flowOf(allEntities)

        // Mock getRecentVisitCounts behavior via teamLogDao
        coEvery { teamLogDao.getRecentTeamVisits(any(), any()) } returns listOf()

        // Mock getTeamMemberStatuses behavior via teamDao.getByTeamIdAndDocType
        coEvery { teamDao.getByTeamIdAndDocType(team1Id, "membership") } returns listOf(membership1)

        // Act
        val resultFlow = teamsRepository.getMyTeamDetailsFlow(userId)
        val results = mutableListOf<List<org.ole.planet.myplanet.model.TeamDetails>>()
        val collectJob = launch {
            resultFlow.collect { results.add(it) }
        }
        advanceUntilIdle()

        // Assert
        assert(results.isNotEmpty())
        val teamDetailsList = results.first()

        // Only team 1 should be included
        // - it's a root team
        // - user has a membership for it
        // - its status is not "archived"
        assert(teamDetailsList.size == 1)
        assert(teamDetailsList[0]._id == team1Id)
        assert(teamDetailsList[0].name == "Team 1")

        collectJob.cancel()
    }

    @Test
    fun `serializeTeamActivities returns correctly serialized JsonObject`() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        io.mockk.mockkObject(org.ole.planet.myplanet.MainApplication.Companion)
        every { org.ole.planet.myplanet.MainApplication.Companion.context } returns mockContext

        io.mockk.mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "test-android-id"
        every { NetworkUtils.getDeviceName() } returns "test-device-name"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "test-custom-device-name"

        io.mockk.mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val text = arg<CharSequence?>(0)
            text == null || text.length == 0
        }

        val teamLog = TeamLog()
        teamLog.id = "test_id"
        teamLog.user = "user1"
        teamLog.type = "type1"
        teamLog.createdOn = "2023-10-27"
        teamLog.parentCode = "parent1"
        teamLog.teamType = "teamType1"
        teamLog.time = 1234567890L
        teamLog.teamId = "team1"
        teamLog._rev = "rev1"
        teamLog._id = "id1"

        val jsonObject = teamsRepository.serializeTeamActivities(teamLog)

        assertEquals("user1", jsonObject.get("user").asString)
        assertEquals("type1", jsonObject.get("type").asString)
        assertEquals("2023-10-27", jsonObject.get("createdOn").asString)
        assertEquals("parent1", jsonObject.get("parentCode").asString)
        assertEquals("teamType1", jsonObject.get("teamType").asString)
        assertEquals(1234567890L, jsonObject.get("time").asLong)
        assertEquals("team1", jsonObject.get("teamId").asString)
        assertEquals("test-android-id", jsonObject.get("androidId").asString)
        assertEquals("test-device-name", jsonObject.get("deviceName").asString)
        assertEquals("test-custom-device-name", jsonObject.get("customDeviceName").asString)
        assertEquals("rev1", jsonObject.get("_rev").asString)
        assertEquals("id1", jsonObject.get("_id").asString)

        io.mockk.unmockkStatic(android.text.TextUtils::class)
        io.mockk.unmockkObject(NetworkUtils)
        io.mockk.unmockkObject(org.ole.planet.myplanet.MainApplication.Companion)
    }

    @Test
    fun `serializeTeamActivities omits _rev and _id when _rev is empty`() {
        val mockContext = mockk<android.content.Context>(relaxed = true)
        io.mockk.mockkObject(org.ole.planet.myplanet.MainApplication.Companion)
        every { org.ole.planet.myplanet.MainApplication.Companion.context } returns mockContext

        io.mockk.mockkObject(NetworkUtils)
        every { NetworkUtils.getUniqueIdentifier() } returns "test-android-id"
        every { NetworkUtils.getDeviceName() } returns "test-device-name"
        every { NetworkUtils.getCustomDeviceName(any()) } returns "test-custom-device-name"

        io.mockk.mockkStatic(android.text.TextUtils::class)
        every { android.text.TextUtils.isEmpty(any()) } answers {
            val text = arg<CharSequence?>(0)
            text == null || text.length == 0
        }

        val teamLog = TeamLog()
        teamLog.id = "test_id"
        teamLog._rev = ""
        teamLog._id = "id1"

        val jsonObject = teamsRepository.serializeTeamActivities(teamLog)

        assertFalse(jsonObject.has("_rev"))
        assertFalse(jsonObject.has("_id"))

        io.mockk.unmockkStatic(android.text.TextUtils::class)
        io.mockk.unmockkObject(NetworkUtils)
        io.mockk.unmockkObject(org.ole.planet.myplanet.MainApplication.Companion)
    }

    @Test
    fun `createLocalResourceLink upserts resourceLink MyTeam row`() = runTest(testDispatcher) {
        val teamId = "team_1"
        val resourceId = "res_1"
        val title = "Test Resource"
        val planetCode = "planet_code"

        coEvery { teamDao.upsert(any()) } returns Unit

        teamsRepository.createLocalResourceLink(teamId, resourceId, title, planetCode)

        val slot = io.mockk.slot<MyTeam>()
        coVerify { teamDao.upsert(capture(slot)) }

        val captured = slot.captured
        assertEquals(teamId, captured.teamId)
        assertEquals(resourceId, captured.resourceId)
        assertEquals(title, captured.title)
        assertEquals("resourceLink", captured.docType)
        assertEquals("local", captured.teamType)
        assertEquals(planetCode, captured.sourcePlanet)
        assertEquals(planetCode, captured.teamPlanetCode)
        assertEquals(true, captured.updated)
    }

    @Test
    fun `getMyTeamsFlow filters out non-root, archived, delete-pending, and non-team types`() = runTest(testDispatcher) {
        val validTeam = MyTeam().apply {
            _id = "team1"
            name = "Team 1"
            type = "team"
            status = "active"
            teamId = null
            isDeletePending = false
        }
        val nullTypeTeam = MyTeam().apply {
            _id = "team2"
            name = "Team 2"
            type = null
            status = "active"
            teamId = null
            isDeletePending = false
        }
        val enterpriseTeam = MyTeam().apply {
            _id = "ent1"
            name = "Enterprise 1"
            type = "enterprise"
            status = "active"
            teamId = null
            isDeletePending = false
        }
        val nonRootTeam = MyTeam().apply {
            _id = "sub1"
            name = "Sub Team"
            type = "team"
            status = "active"
            teamId = "team1"
            isDeletePending = false
        }
        val archivedTeam = MyTeam().apply {
            _id = "arch1"
            name = "Archived Team"
            type = "team"
            status = "archived"
            teamId = null
            isDeletePending = false
        }
        val deletePendingTeam = MyTeam().apply {
            _id = "del1"
            name = "Delete Pending Team"
            type = "team"
            status = "active"
            teamId = null
            isDeletePending = true
        }
        val membershipValid1 = MyTeam().apply {
            _id = "mem1"
            userId = "user1"
            teamId = "team1"
            docType = "membership"
            isDeletePending = false
        }
        val membershipValid2 = MyTeam().apply {
            _id = "mem2"
            userId = "user1"
            teamId = "team2"
            docType = "membership"
            isDeletePending = false
        }
        val membershipEnt = MyTeam().apply {
            _id = "mem3"
            userId = "user1"
            teamId = "ent1"
            docType = "membership"
            isDeletePending = false
        }
        val membershipSub = MyTeam().apply {
            _id = "mem4"
            userId = "user1"
            teamId = "sub1"
            docType = "membership"
            isDeletePending = false
        }
        val membershipArch = MyTeam().apply {
            _id = "mem5"
            userId = "user1"
            teamId = "arch1"
            docType = "membership"
            isDeletePending = false
        }
        val membershipDel = MyTeam().apply {
            _id = "mem6"
            userId = "user1"
            teamId = "del1"
            docType = "membership"
            isDeletePending = false
        }
        val deletePendingMembership = MyTeam().apply {
            _id = "mem7"
            userId = "user1"
            teamId = "team_del_mem"
            docType = "membership"
            isDeletePending = true
        }
        val teamForDeletedMem = MyTeam().apply {
            _id = "team_del_mem"
            name = "Deleted Membership Team"
            type = "team"
            status = "active"
            teamId = null
            isDeletePending = false
        }

        val allEntities = listOf(
            validTeam, nullTypeTeam, enterpriseTeam, nonRootTeam,
            archivedTeam, deletePendingTeam, teamForDeletedMem,
            membershipValid1, membershipValid2, membershipEnt,
            membershipSub, membershipArch, membershipDel, deletePendingMembership
        )
        every { teamDao.observeAll() } returns flowOf(allEntities)

        val result = teamsRepository.getMyTeamsFlow("user1").first()

        assertEquals(2, result.size)
        val resultIds = result.map { it._id }
        assertEquals(listOf("team1", "team2"), resultIds)
    }

    @Test
    fun `getMyTeamDetailsFlow filters correctly for team and enterprise types`() = runTest(testDispatcher) {
        val validTeam = MyTeam().apply {
            _id = "team1"
            name = "Team 1"
            type = "team"
            status = "active"
            teamId = null
            isDeletePending = false
        }
        val enterpriseTeam = MyTeam().apply {
            _id = "ent1"
            name = "Enterprise 1"
            type = "enterprise"
            status = "active"
            teamId = null
            isDeletePending = false
        }
        val membershipTeam = MyTeam().apply {
            _id = "mem1"
            userId = "user1"
            teamId = "team1"
            docType = "membership"
            isDeletePending = false
        }
        val membershipEnt = MyTeam().apply {
            _id = "mem2"
            userId = "user1"
            teamId = "ent1"
            docType = "membership"
            isDeletePending = false
        }

        val allEntities = listOf(validTeam, enterpriseTeam, membershipTeam, membershipEnt)
        every { teamDao.observeAll() } returns flowOf(allEntities)
        coEvery { teamDao.getByUserId("user1") } returns listOf(membershipTeam, membershipEnt)

        val teamResults = teamsRepository.getMyTeamDetailsFlow("user1", "team").first()
        assertEquals(1, teamResults.size)
        assertEquals("team1", teamResults[0]._id)

        val entResults = teamsRepository.getMyTeamDetailsFlow("user1", "enterprise").first()
        assertEquals(1, entResults.size)
        assertEquals("ent1", entResults[0]._id)
    }

    @Test
    fun `getMyTeamDetailsFlow returns empty list when user has no joined teams`() = runTest(testDispatcher) {
        val validTeam = MyTeam().apply {
            _id = "team1"
            name = "Team 1"
            type = "team"
            status = "active"
            teamId = null
            isDeletePending = false
        }
        every { teamDao.observeAll() } returns flowOf(listOf(validTeam))

        val result = teamsRepository.getMyTeamDetailsFlow("user1", "team").first()
        assertEquals(0, result.size)
    }

    @Test
    fun `getJoinedMembers uses getUsersByIds and preserves order including _id match`() = runTest(testDispatcher) {
        val teamId = "team1"
        val member1 = MyTeam().apply { userId = "user1"; isDeletePending = false }
        val member2 = MyTeam().apply { userId = "user2"; isDeletePending = false }
        val member3 = MyTeam().apply { userId = "user3"; isDeletePending = false }
        val deletePendingMember = MyTeam().apply { userId = "user4"; isDeletePending = true }

        coEvery { teamDao.getByTeamIdAndDocType(teamId, "membership") } returns listOf(member1, member2, deletePendingMember, member3)

        val user1 = UserEntity().apply { id = "org.couchdb.user:alice"; _id = "user1"; name = "Alice" }
        val user2 = UserEntity().apply { id = "user2"; name = "Bob" }
        val user3 = UserEntity().apply { id = "user3"; name = "Charlie" }

        coEvery { userRepository.getUsersByIds(listOf("user1", "user2", "user3")) } returns listOf(user3, user1, user2)

        val result = teamsRepository.getJoinedMembers(teamId)

        assertEquals(listOf(user1, user2, user3), result)
        coVerify(exactly = 1) { userRepository.getUsersByIds(listOf("user1", "user2", "user3")) }
        coVerify(exactly = 0) { userRepository.getUserById(any()) }
    }

    @Test
    fun `getRequestedMembers uses getUsersByIds and preserves order including _id match`() = runTest(testDispatcher) {
        val teamId = "team1"
        val req1 = MyTeam().apply { userId = "user1" }
        val req2 = MyTeam().apply { userId = "user2" }

        coEvery { teamDao.getByTeamIdAndDocType(teamId, "request") } returns listOf(req1, req2)

        val user1 = UserEntity().apply { id = "org.couchdb.user:alice"; _id = "user1"; name = "Alice" }
        val user2 = UserEntity().apply { id = "user2"; name = "Bob" }

        coEvery { userRepository.getUsersByIds(listOf("user1", "user2")) } returns listOf(user2, user1)

        val result = teamsRepository.getRequestedMembers(teamId)

        assertEquals(listOf(user1, user2), result)
        coVerify(exactly = 1) { userRepository.getUsersByIds(listOf("user1", "user2")) }
        coVerify(exactly = 0) { userRepository.getUserById(any()) }
    }
}
