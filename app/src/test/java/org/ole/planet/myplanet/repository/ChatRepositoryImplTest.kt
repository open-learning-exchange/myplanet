package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        databaseService = mockk(relaxed = true)
        repository = ChatRepositoryImpl(databaseService)
    }

    @After
    fun teardown() {
    }

    @Test
    fun `getChatHistoryForUser returns empty list for null or empty userName`() = runTest {
        val nullResult = repository.getChatHistoryForUser(null)
        val emptyResult = repository.getChatHistoryForUser("")

        assertEquals(0, nullResult.size)
        assertEquals(0, emptyResult.size)
    }

    @Test
    fun `getChatHistoryForUser queries with correct user and DESCENDING sort`() = runTest {
        val userName = "testUser"
        val mockRealm = mockk<Realm>(relaxed = true)
        val mockQuery = mockk<RealmQuery<RealmChatHistory>>(relaxed = true)
        val mockChat = mockk<RealmChatHistory>(relaxed = true)
        val expectedList = listOf(mockChat)

        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.sort(any<String>(), any<Sort>()) } returns mockQuery

        val operationSlot = slot<(Realm) -> List<RealmChatHistory>>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val mockResults = mockk<RealmResults<RealmChatHistory>>(relaxed = true)
        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockRealm.copyFromRealm(mockResults) } returns expectedList

        val result = repository.getChatHistoryForUser(userName)

        assertEquals(expectedList, result)

        verify(exactly = 1) { mockQuery.equalTo("user", userName) }
        verify(exactly = 1) { mockQuery.sort("id", Sort.DESCENDING) }
    }

    @Test
    fun `getLatestRev finds the highest _rev by numeric prefix`() = runTest {
        val id = "testId"
        val mockRealm = mockk<Realm>(relaxed = true)

        val operationSlot = slot<(Realm) -> String?>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val mockQuery = mockk<RealmQuery<RealmChatHistory>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmChatHistory>>(relaxed = true)

        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", id) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        val item1 = mockk<RealmChatHistory>()
        every { item1._rev } returns "1-abc"

        val item2 = mockk<RealmChatHistory>()
        every { item2._rev } returns "10-xyz"

        val item3 = mockk<RealmChatHistory>()
        every { item3._rev } returns "2-def"

        val item4 = mockk<RealmChatHistory>()
        every { item4._rev } returns "invalid-rev"

        val item5 = mockk<RealmChatHistory>()
        every { item5._rev } returns null

        every { mockResults.iterator() } returns mutableListOf(item1, item2, item3, item4, item5).iterator()

        val result = repository.getLatestRev(id)

        assertEquals("10-xyz", result)
    }

    @Test
    fun `getLatestRev returns null if no results`() = runTest {
        val id = "testId"
        val mockRealm = mockk<Realm>(relaxed = true)

        val operationSlot = slot<(Realm) -> String?>()
        coEvery { databaseService.withRealmAsync(capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val mockQuery = mockk<RealmQuery<RealmChatHistory>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmChatHistory>>(relaxed = true)

        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", id) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        every { mockResults.iterator() } returns mutableListOf<RealmChatHistory>().iterator()

        val result = repository.getLatestRev(id)

        assertNull(result)
    }

    @Test
    fun `saveNewChat delegates to RealmChatHistory insert inside a transaction`() = runTest {
        val chatObj = JsonObject()
        val mockRealm = mockk<Realm>(relaxed = true)
        every { mockRealm.isClosed } returns false

        // CRITICAL FIX: The reason mockk evaluating Realm.where throws IllegalStateException is because
        // when the lambda executes, the companion method code calls Realm.where.
        // We tried mockkStatic but it still leaked.
        // We will fully mock the query first BEFORE executing the captured block.

        val mockQuery = mockk<RealmQuery<RealmChatHistory>>(relaxed = true)
        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmChatHistory::class.java, any<String>()) } returns mockk(relaxed = true)

        val operationSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.withRealmAsync<Unit>(capture(operationSlot)) } answers {
            // we capture but DO NOT evaluate the lambda yet
        }

        repository.saveNewChat(chatObj)

        coVerify(exactly = 1) { databaseService.withRealmAsync<Unit>(any()) }

        val transactionSlot = slot<Realm.Transaction>()
        every { mockRealm.executeTransaction(capture(transactionSlot)) } answers {
            // DO NOTHING - Do not execute transaction block during mockk matching
        }

        // Execute outer lambda manually
        operationSlot.captured.invoke(mockRealm)

        verify(exactly = 1) { mockRealm.executeTransaction(any()) }

        io.mockk.mockkObject(RealmChatHistory.Companion)
        every { RealmChatHistory.insert(any(), any()) } answers { }

        transactionSlot.captured.execute(mockRealm)

        verify(exactly = 1) { RealmChatHistory.insert(mockRealm, chatObj) }

        io.mockk.unmockkObject(RealmChatHistory.Companion)
    }

    @Test
    fun `continueConversation delegates to addConversationToChatHistory inside a transaction`() = runTest {
        val id = "testId"
        val query = "hello"
        val response = "hi"
        val rev = "1-rev"
        val mockRealm = mockk<Realm>(relaxed = true)
        every { mockRealm.isClosed } returns false

        val mockQuery = mockk<RealmQuery<RealmChatHistory>>(relaxed = true)
        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmChatHistory::class.java, any<String>()) } returns mockk(relaxed = true)

        val operationSlot = slot<(Realm) -> Unit>()
        coEvery { databaseService.withRealmAsync<Unit>(capture(operationSlot)) } answers {
            // DO NOTHING
        }

        repository.continueConversation(id, query, response, rev)

        coVerify(exactly = 1) { databaseService.withRealmAsync<Unit>(any()) }

        val transactionSlot = slot<Realm.Transaction>()
        every { mockRealm.executeTransaction(capture(transactionSlot)) } answers {
            // DO NOTHING
        }

        // Execute outer lambda manually
        operationSlot.captured.invoke(mockRealm)

        verify(exactly = 1) { mockRealm.executeTransaction(any()) }

        io.mockk.mockkObject(RealmChatHistory.Companion)
        every { RealmChatHistory.Companion.addConversationToChatHistory(any(), any(), any(), any(), any()) } answers { }

        transactionSlot.captured.execute(mockRealm)

        verify(exactly = 1) { RealmChatHistory.Companion.addConversationToChatHistory(mockRealm, id, query, response, rev) }

        io.mockk.unmockkObject(RealmChatHistory.Companion)
    }

    @Test
    fun `saveNewChat propagates exceptions from withRealmAsync`() = runTest {
        val chatObj = JsonObject()
        val expectedException = RuntimeException("Realm failed")

        coEvery { databaseService.withRealmAsync<Unit>(any()) } throws expectedException

        try {
            repository.saveNewChat(chatObj)
            assert(false) { "Expected exception was not thrown" }
        } catch (e: Exception) {
            assertEquals(expectedException, e)
        }
    }

    @Test
    fun `continueConversation propagates exceptions from withRealmAsync`() = runTest {
        val expectedException = RuntimeException("Realm failed")

        coEvery { databaseService.withRealmAsync<Unit>(any()) } throws expectedException

        try {
            repository.continueConversation("id", "query", "response", "rev")
            assert(false) { "Expected exception was not thrown" }
        } catch (e: Exception) {
            assertEquals(expectedException, e)
        }
    }
}
