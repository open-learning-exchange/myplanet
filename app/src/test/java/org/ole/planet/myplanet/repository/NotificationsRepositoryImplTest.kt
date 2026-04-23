package org.ole.planet.myplanet.repository

import dagger.Lazy
import io.mockk.*
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUser

class NotificationsRepositoryImplTest {
    private val databaseService: DatabaseService = mockk()
    private val userRepository: UserRepository = mockk()
    private val userRepositoryLazy: Lazy<UserRepository> = mockk()
    private val realm: Realm = mockk(relaxed = true)
    private lateinit var repository: NotificationsRepositoryImpl

    @Before
    fun setUp() {
        every { userRepositoryLazy.get() } returns userRepository
        repository = NotificationsRepositoryImpl(databaseService, mockk(relaxed = true), userRepositoryLazy)

        coEvery { databaseService.withRealmAsync<Any>(any()) } answers {
            val block = firstArg<(Realm) -> Any>()
            block(realm)
        }
    }

    @Test
    fun testGetJoinRequestDetailsBatch() = runBlocking {
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
        val queryTeam = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        every { realm.where(RealmMyTeam::class.java) } returns queryJR andThen queryTeam

        val resultsJR = mockk<RealmResults<RealmMyTeam>>()
        val resultsTeam = mockk<RealmResults<RealmMyTeam>>()
        every { queryJR.findAll() } returns resultsJR
        every { queryTeam.findAll() } returns resultsTeam

        val jrList = listOf(jr1)
        every { resultsJR.iterator() } answers { jrList.iterator() as MutableIterator<RealmMyTeam> }
        every { resultsJR.size } returns 1

        val teamList = listOf(team1)
        every { resultsTeam.iterator() } answers { teamList.iterator() as MutableIterator<RealmMyTeam> }
        every { resultsTeam.size } returns 1

        coEvery { userRepository.getUsersByIds(any()) } returns listOf(user1)

        val result = repository.getJoinRequestDetailsBatch(relatedIds)

        assertEquals("Result map should have 1 entry", 1, result.size)
        assertEquals("User 1", result["req1"]?.first)
        assertEquals("Team 1", result["req1"]?.second)
    }
}
