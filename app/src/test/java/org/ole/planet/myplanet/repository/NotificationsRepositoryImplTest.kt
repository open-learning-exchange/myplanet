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
        realm = mockk(relaxed = true)

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val block = firstArg<(Realm) -> Any>()
            block(realm)
        }
    }

    @Test
    fun `getJoinRequestDetailsBatch should fetch users in batch`() = runTest {
        val relatedIds = listOf("req1")

        val jr1 = mockk<RealmMyTeam>(relaxed = true)
        every { jr1._id } returns "req1"
        every { jr1.userId } returns "user1"
        every { jr1.teamId } returns "team1"

        val team1 = mockk<RealmMyTeam>(relaxed = true)
        every { team1._id } returns "team1"
        every { team1.name } returns "Team 1"

        val user1 = mockk<RealmUser>(relaxed = true)
        every { user1.id } returns "user1"
        every { user1.name } returns "User 1"

        val queryJR = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        val resultsJR = mockk<RealmResults<RealmMyTeam>>()
        val queryTeam = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        val resultsTeam = mockk<RealmResults<RealmMyTeam>>()

        every { realm.where(RealmMyTeam::class.java) } returns queryJR andThen queryTeam
        every { queryJR.findAll() } returns resultsJR
        every { queryTeam.findAll() } returns resultsTeam

        val jrList = listOf(jr1)
        every { resultsJR.iterator() } answers { jrList.iterator() as MutableIterator<RealmMyTeam> }
        every { resultsJR.size } returns jrList.size

        val teamList = listOf(team1)
        every { resultsTeam.iterator() } answers { teamList.iterator() as MutableIterator<RealmMyTeam> }
        every { resultsTeam.size } returns teamList.size

        coEvery { userRepository.getUsersByIds(any()) } returns listOf(user1)

        val result = repository.getJoinRequestDetailsBatch(relatedIds)

        assertEquals(1, result.size)
        assertEquals("User 1", result["req1"]?.first)
        assertEquals("Team 1", result["req1"]?.second)

        coVerify(exactly = 1) { userRepository.getUsersByIds(listOf("user1")) }
    }
}
