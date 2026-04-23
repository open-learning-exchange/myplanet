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

    private fun <T : io.realm.RealmModel> createMutableIterator(list: List<T>): MutableIterator<T> {
        val iterator = list.iterator()
        return object : MutableIterator<T> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): T = iterator.next()
            override fun remove() = throw UnsupportedOperationException()
        }
    }

    @Test
    fun testGetJoinRequestDetailsBatch() = runBlocking {
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

        val queryJR = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        val queryTeam = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)

        every { realm.where(RealmMyTeam::class.java) } returns queryJR andThen queryTeam

        val resultsJR = mockk<RealmResults<RealmMyTeam>>(relaxed = true)
        val resultsTeam = mockk<RealmResults<RealmMyTeam>>(relaxed = true)

        every { queryJR.findAll() } returns resultsJR
        every { queryTeam.findAll() } returns resultsTeam

        // Use answers to provide a fresh iterator every time it's requested
        every { resultsJR.iterator() } answers { createMutableIterator(listOf(jr1, jr2)) }
        every { resultsTeam.iterator() } answers { createMutableIterator(listOf(team1)) }

        coEvery { userRepository.getUsersByIds(any()) } returns listOf(user1, user2)

        val result = repository.getJoinRequestDetailsBatch(relatedIds)

        assertEquals("Should have 2 results", 2, result.size)
        assertEquals("User 1", result["req1"]?.first)
        assertEquals("Team 1", result["req1"]?.second)
        assertEquals("User 2", result["req2"]?.first)
        assertEquals("Team 1", result["req2"]?.second)

        coVerify(exactly = 1) { userRepository.getUsersByIds(match { it.containsAll(listOf("user1", "user2")) }) }
    }
}
