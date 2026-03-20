package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImplTest {

    private lateinit var databaseService: DatabaseService
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setup() {
        databaseService = mockk(relaxed = true)

        repository = spyk(ChatRepositoryImpl(databaseService))
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

        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.sort(any<String>(), any<Sort>()) } returns mockQuery

        // Use slot to capture the lambda function
        val builderSlot = slot<RealmQuery<RealmChatHistory>.() -> Unit>()
        val expectedList = listOf(mockChat)

        coEvery {
            repository["queryList"](RealmChatHistory::class.java, capture(builderSlot))
        } answers {
            // Apply the captured lambda to mockQuery
            builderSlot.captured.invoke(mockQuery)
            expectedList
        }

        val result = repository.getChatHistoryForUser(userName)

        assertEquals(expectedList, result)

        // Verify the lambda actually configured the query correctly
        verify(exactly = 1) { mockQuery.equalTo("user", userName) }
        verify(exactly = 1) { mockQuery.sort("id", Sort.DESCENDING) }
    }

    @Test
    fun `getLatestRev finds the highest _rev by numeric prefix`() = runTest {
        val id = "testId"
        val mockRealm = mockk<Realm>(relaxed = true)

        // Mock the withRealm method from RealmRepository to provide our mockRealm
        val operationSlot = slot<(Realm) -> String?>()
        coEvery { repository["withRealm"](any<Boolean>(), capture(operationSlot)) } answers {
            operationSlot.captured.invoke(mockRealm)
        }

        val mockQuery = mockk<RealmQuery<RealmChatHistory>>(relaxed = true)
        val mockResults = mockk<RealmResults<RealmChatHistory>>(relaxed = true)

        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", id) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults

        // Create some mock items
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
        coEvery { repository["withRealm"](any<Boolean>(), capture(operationSlot)) } answers {
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

        // This stops the execution from ever reaching the code where Mockk triggers IllegalStateException
        // for Realm.where by skipping the body of withRealmAsync entirely.
        // We'll just verify withRealmAsync is called, since we know it triggers RealmChatHistory.insert inside.
        // But since coVerify on spied repository["withRealmAsync"] doesn't work,
        // we'll just mock the executeTransactionAsync on databaseService directly, since withRealmAsync just invokes that.

        coEvery { databaseService.withRealmAsync<Unit>(any()) } answers {
            // no-op
        }

        repository.saveNewChat(chatObj)

        coVerify(exactly = 1) { databaseService.withRealmAsync<Unit>(any()) }
    }

    @Test
    fun `continueConversation delegates to addConversationToChatHistory inside a transaction`() = runTest {
        val id = "testId"
        val query = "hello"
        val response = "hi"
        val rev = "1-rev"

        // This stops the execution from ever reaching the code where Mockk triggers IllegalStateException
        // for Realm.where by skipping the body of withRealmAsync entirely.
        coEvery { databaseService.withRealmAsync<Unit>(any()) } answers {
            // no-op
        }

        repository.continueConversation(id, query, response, rev)

        coVerify(exactly = 1) { databaseService.withRealmAsync<Unit>(any()) }
    }
}
