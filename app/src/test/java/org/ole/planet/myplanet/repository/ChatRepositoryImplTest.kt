package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmChatHistory.Companion.addConversationToChatHistory

class ChatRepositoryImplTest {

    private lateinit var chatRepository: ChatRepositoryImpl
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val mockRealm: Realm = mockk(relaxed = true)

    @Before
    fun setup() {
        chatRepository = spyk(ChatRepositoryImpl(databaseService), recordPrivateCalls = true)
        mockkObject(RealmChatHistory.Companion)
        every { RealmChatHistory.insert(any(), any()) } just Runs
        every { RealmChatHistory.addConversationToChatHistory(any(), any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getChatHistoryForUser_returnsEmptyListForNullOrEmptyUserName() = runTest {
        val resultNull = chatRepository.getChatHistoryForUser(null)
        assertTrue(resultNull.isEmpty())

        val resultEmpty = chatRepository.getChatHistoryForUser("")
        assertTrue(resultEmpty.isEmpty())
    }

    @Test
    fun getChatHistoryForUser_queriesWithCorrectUserAndDescendingSort() = runTest {
        val userName = "testUser"
        val mockHistoryList = listOf(RealmChatHistory().apply { user = userName })
        val builderSlot = slot<RealmQuery<RealmChatHistory>.() -> Unit>()

        coEvery { chatRepository["queryList"](RealmChatHistory::class.java, capture(builderSlot)) } returns mockHistoryList

        val result = chatRepository.getChatHistoryForUser(userName)

        assertEquals(mockHistoryList, result)
        coVerify(exactly = 1) {
            chatRepository["queryList"](RealmChatHistory::class.java, any<RealmQuery<RealmChatHistory>.() -> Unit>())
        }

        // Verify the query builder matches expected parameters
        val mockQuery: RealmQuery<RealmChatHistory> = mockk(relaxed = true)
        every { mockQuery.equalTo("user", userName) } returns mockQuery
        every { mockQuery.sort("id", Sort.DESCENDING) } returns mockQuery

        builderSlot.captured.invoke(mockQuery)

        verify(exactly = 1) { mockQuery.equalTo("user", userName) }
        verify(exactly = 1) { mockQuery.sort("id", Sort.DESCENDING) }
    }

    @Test
    fun getLatestRev_findsHighestRevByNumericPrefix() = runTest {
        val id = "123"
        val mockQuery: RealmQuery<RealmChatHistory> = mockk(relaxed = true)
        val mockResults: RealmResults<RealmChatHistory> = mockk(relaxed = true)

        val item1 = RealmChatHistory().apply { _rev = "1-abc" }
        val item2 = RealmChatHistory().apply { _rev = "10-def" }
        val item3 = RealmChatHistory().apply { _rev = "2-ghi" }

        val list = mutableListOf(item1, item2, item3)

        coEvery { databaseService.withRealmAsync<String?>(any()) } answers {
            every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
            every { mockQuery.equalTo("_id", id) } returns mockQuery
            every { mockQuery.findAll() } returns mockResults
            every { mockResults.iterator() } returns list.iterator()

            val op = arg<(Realm) -> String?>(0)
            op.invoke(mockRealm)
        }

        val result = chatRepository.getLatestRev(id)
        assertEquals("10-def", result)
    }

    @Test
    fun saveNewChat_executesTransaction() = runTest {
        val chatObj = JsonObject()
        val transactionSlot = slot<(Realm) -> Unit>()

        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        chatRepository.saveNewChat(chatObj)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any()) }
        verify(exactly = 1) { RealmChatHistory.insert(mockRealm, chatObj) }
    }

    @Test
    fun continueConversation_executesTransaction() = runTest {
        val id = "123"
        val query = "hello"
        val response = "hi"
        val rev = "1-rev"

        val transactionSlot = slot<(Realm) -> Unit>()

        coEvery { databaseService.executeTransactionAsync(capture(transactionSlot)) } answers {
            transactionSlot.captured.invoke(mockRealm)
        }

        chatRepository.continueConversation(id, query, response, rev)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any()) }
        verify(exactly = 1) { RealmChatHistory.addConversationToChatHistory(mockRealm, id, query, response, rev) }
    }
}
