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

        val jr1 = RealmMyTeam().apply {
            _id = "req1"
            userId = "user1"
            teamId = "team1"
        }
        val jr2 = RealmMyTeam().apply {
            _id = "req2"
            userId = "user2"
            teamId = "team1"
        }
        val team1 = RealmMyTeam().apply {
            _id = "team1"
            name = "Team 1"
        }
        val user1 = RealmUser().apply {
            id = "user1"
            name = "User 1"
        }
        val user2 = RealmUser().apply {
            id = "user2"
            name = "User 2"
        }

        val query = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        every { realm.where(RealmMyTeam::class.java) } returns query

        val resultsJR = mockk<RealmResults<RealmMyTeam>>()
        val resultsTeam = mockk<RealmResults<RealmMyTeam>>()
        every { query.findAll() } returns resultsJR andThen resultsTeam

        val jrList = listOf(jr1, jr2)
        every { resultsJR.iterator() } returns (jrList.iterator() as MutableIterator<RealmMyTeam>)
        every { resultsJR.size } returns jrList.size

        val teamList = listOf(team1)
        every { resultsTeam.iterator() } returns (teamList.iterator() as MutableIterator<RealmMyTeam>)
        every { resultsTeam.size } returns teamList.size

        coEvery { userRepository.getUsersByIds(any()) } returns listOf(user1, user2)

        val result = repository.getJoinRequestDetailsBatch(relatedIds)

        assertEquals(2, result.size)
        assertEquals("User 1", result["req1"]?.first)
        assertEquals("Team 1", result["req1"]?.second)
        assertEquals("User 2", result["req2"]?.first)
        assertEquals("Team 1", result["req2"]?.second)

        coVerify(exactly = 1) { userRepository.getUsersByIds(any()) }
    }
}
