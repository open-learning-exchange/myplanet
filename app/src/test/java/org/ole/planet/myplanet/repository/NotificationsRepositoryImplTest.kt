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

        val jr1 = mockk<RealmMyTeam>(relaxed = true) {
            every { _id } returns "req1"
            every { userId } returns userId1
            every { teamId } returns teamId1
        }
        val jr2 = mockk<RealmMyTeam>(relaxed = true) {
            every { _id } returns "req2"
            every { userId } returns userId2
            every { teamId } returns teamId1
        }

        val team1 = mockk<RealmMyTeam>(relaxed = true) {
            every { _id } returns teamId1
            every { name } returns "Team 1"
        }

        val user1 = mockk<RealmUser>(relaxed = true) {
            every { id } returns userId1
            every { name } returns "User 1"
        }
        val user2 = mockk<RealmUser>(relaxed = true) {
            every { id } returns userId2
            every { name } returns "User 2"
        }

        val queryJR = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        val resultsJR = mockk<RealmResults<RealmMyTeam>>(relaxed = true)
        val queryTeam = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        val resultsTeam = mockk<RealmResults<RealmMyTeam>>(relaxed = true)

        every { realm.where(RealmMyTeam::class.java) } returns queryJR andThen queryTeam

        every { queryJR.equalTo("docType", "request") } returns queryJR
        every { queryJR.beginGroup() } returns queryJR
        every { queryJR.or() } returns queryJR
        every { queryJR.equalTo("_id", any<String>()) } returns queryJR
        every { queryJR.endGroup() } returns queryJR
        every { queryJR.findAll() } returns resultsJR

        every { resultsJR.iterator() } returns (mutableListOf(jr1, jr2).iterator() as MutableIterator<RealmMyTeam>)

        every { queryTeam.beginGroup() } returns queryTeam
        every { queryTeam.or() } returns queryTeam
        every { queryTeam.equalTo("_id", any<String>()) } returns queryTeam
        every { queryTeam.endGroup() } returns queryTeam
        every { queryTeam.findAll() } returns resultsTeam

        every { resultsTeam.iterator() } returns (mutableListOf(team1).iterator() as MutableIterator<RealmMyTeam>)

        coEvery { userRepository.getUsersByIds(any()) } returns listOf(user1, user2)

        val result = repository.getJoinRequestDetailsBatch(relatedIds)

        assertEquals(2, result.size)
        assertEquals("User 1", result["req1"]?.first)
        assertEquals("Team 1", result["req1"]?.second)
        assertEquals("User 2", result["req2"]?.first)
        assertEquals("Team 1", result["req2"]?.second)

        coVerify(exactly = 1) { userRepository.getUsersByIds(match { it.containsAll(listOf(userId1, userId2)) }) }
        coVerify(exactly = 0) { userRepository.getUserById(any()) }
    }
}
