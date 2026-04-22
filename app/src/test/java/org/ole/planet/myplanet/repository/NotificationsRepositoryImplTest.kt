package org.ole.planet.myplanet.repository

import dagger.Lazy
import io.mockk.*
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsRepositoryImplTest {
    private lateinit var databaseService: DatabaseService
    private lateinit var realmDispatcher: CoroutineDispatcher
    private lateinit var userRepository: UserRepository
    private lateinit var userRepositoryLazy: Lazy<UserRepository>
    private lateinit var repository: NotificationsRepositoryImpl
    private lateinit var realm: Realm

    @Before
    fun setUp() {
        databaseService = mockk()
        realmDispatcher = UnconfinedTestDispatcher()
        userRepository = mockk()
        userRepositoryLazy = mockk()
        every { userRepositoryLazy.get() } returns userRepository

        repository = NotificationsRepositoryImpl(databaseService, realmDispatcher, userRepositoryLazy)
        realm = mockk()

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val block = firstArg<(Realm) -> Any>()
            block(realm)
        }
    }

    @Test
    fun `getJoinRequestDetailsBatch should fetch users in batch`() = runTest {
        val relatedIds = listOf("req1", "req2")
        val userId1 = "user1"
        val userId2 = "user2"
        val teamId1 = "team1"

        val jr1 = RealmMyTeam().apply {
            _id = "req1"
            userId = userId1
            teamId = teamId1
        }
        val jr2 = RealmMyTeam().apply {
            _id = "req2"
            userId = userId2
            teamId = teamId1
        }

        val team1 = RealmMyTeam().apply {
            _id = teamId1
            name = "Team 1"
        }

        val user1 = RealmUser().apply {
            id = userId1
            name = "User 1"
        }
        val user2 = RealmUser().apply {
            id = userId2
            name = "User 2"
        }

        val queryJR = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        val resultsJR = mockk<RealmResults<RealmMyTeam>>()
        val queryTeam = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        val resultsTeam = mockk<RealmResults<RealmMyTeam>>()

        every { realm.where(RealmMyTeam::class.java) } returns queryJR andThen queryTeam

        every { queryJR.equalTo("docType", "request") } returns queryJR
        every { queryJR.findAll() } returns resultsJR

        val jrList = mutableListOf(jr1, jr2)
        every { resultsJR.iterator() } returns (jrList.iterator() as MutableIterator<RealmMyTeam>)
        every { resultsJR.size } returns jrList.size

        every { queryTeam.findAll() } returns resultsTeam
        val teamList = mutableListOf(team1)
        every { resultsTeam.iterator() } returns (teamList.iterator() as MutableIterator<RealmMyTeam>)
        every { resultsTeam.size } returns teamList.size

        coEvery { userRepository.getUsersByIds(any()) } returns listOf(user1, user2)

        val result = repository.getJoinRequestDetailsBatch(relatedIds)

        assertEquals("Expected 2 results", 2, result.size)
        assertEquals("User 1", result["req1"]?.first)
        assertEquals("Team 1", result["req1"]?.second)
        assertEquals("User 2", result["req2"]?.first)
        assertEquals("Team 1", result["req2"]?.second)

        coVerify(exactly = 1) { userRepository.getUsersByIds(any()) }
    }
}
