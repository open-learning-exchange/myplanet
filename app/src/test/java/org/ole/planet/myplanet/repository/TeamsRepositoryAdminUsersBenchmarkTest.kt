package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import kotlin.system.measureTimeMillis

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsRepositoryAdminUsersBenchmarkTest {
    private lateinit var teamsRepository: TeamsRepositoryImpl
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val uploadManager: UploadManager = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val preferences: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk()
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val realm: Realm = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(Realm) -> Unit>()
            transaction(realm)
        }

        coEvery { databaseService.withRealmAsync<Any?>(any()) } answers {
            val block = firstArg<(Realm) -> Any?>()
            block(realm)
        }

        teamsRepository = TeamsRepositoryImpl(
            activitiesRepository,
            databaseService,
            testDispatcher,
            userSessionManager,
            uploadManager,
            gson,
            preferences,
            sharedPrefManager,
            serverUrlMapper,
            dispatcherProvider,
            userRepository
        )
    }

    @Test
    fun benchmarkGetJoinedMembersWithVisitInfo() = runTest {
        val teamId = "test_team_id"

        // Mock team members
        val teamMembersQuery: RealmQuery<RealmMyTeam> = mockk(relaxed = true)
        val teamMembersResults: RealmResults<RealmMyTeam> = mockk(relaxed = true)

        every { realm.where(RealmMyTeam::class.java) } returns teamMembersQuery
        every { teamMembersQuery.equalTo("teamId", teamId) } returns teamMembersQuery
        every { teamMembersQuery.equalTo("isLeader", true) } returns teamMembersQuery
        every { teamMembersQuery.findAll() } returns teamMembersResults
        every { teamMembersResults.iterator() } returns mutableListOf<RealmMyTeam>().iterator()

        // Mock shared pref leaders JSON
        val leadersJsonArray = JsonArray()
        val numAdmins = 100
        for (i in 1..numAdmins) {
            leadersJsonArray.add(JsonObject().apply {
                addProperty("name", "admin$i")
            })
        }
        every { sharedPrefManager.getCommunityLeaders() } returns leadersJsonArray.toString()

        val parsedAdmins = (1..numAdmins).map { i ->
            RealmUser().apply { name = "admin$i" }
        }
        coEvery { userRepository.parseLeadersJson(any()) } returns parsedAdmins

        val allTeamUserIds = (1..numAdmins).map { "org.couchdb.user:admin$it" }.toSet()
        val teamUserIdsQuery: RealmQuery<RealmMyTeam> = mockk(relaxed = true)
        val teamUserIdsResults: RealmResults<RealmMyTeam> = mockk(relaxed = true)

        every { realm.where(RealmMyTeam::class.java) } returns teamUserIdsQuery
        every { teamUserIdsQuery.equalTo("teamId", teamId) } returns teamUserIdsQuery
        every { teamUserIdsQuery.findAll() } returns teamUserIdsResults

        val teamUsers = allTeamUserIds.map { RealmMyTeam().apply { userId = it } }
        every { teamUserIdsResults.iterator() } returns teamUsers.toMutableList().iterator()

        val userQuery: RealmQuery<RealmUser> = mockk(relaxed = true)
        every { realm.where(RealmUser::class.java) } returns userQuery
        every { userQuery.equalTo("name", any<String>()) } returns userQuery
        every { userQuery.findFirst() } returns RealmUser().apply { name = "dummy" }
        every { realm.copyFromRealm(any<RealmUser>()) } answers { firstArg() }

        val time = measureTimeMillis {
            teamsRepository.getJoinedMembersWithVisitInfo(teamId)
        }

        println("getJoinedMembersWithVisitInfo completed in $time ms")
    }
}
