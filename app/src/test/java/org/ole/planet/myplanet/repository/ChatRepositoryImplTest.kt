package org.ole.planet.myplanet.repository

import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import io.mockk.just
import io.mockk.Runs
import io.mockk.unmockkAll
import io.mockk.mockkObject
import io.realm.Realm
import kotlinx.coroutines.test.runTest
import org.junit.After
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
