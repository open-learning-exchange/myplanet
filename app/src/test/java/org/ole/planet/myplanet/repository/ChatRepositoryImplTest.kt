package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort
import kotlinx.coroutines.test.runTest
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import retrofit2.Response

class ChatRepositoryImplTest {
    private lateinit var chatRepository: ChatRepositoryImpl
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val mockRealm: Realm = mockk(relaxed = true)
    private val chatApiService: ChatApiService = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    @Before
    fun setup() {
        every { sharedPrefManager.rawPreferences } returns mockk(relaxed = true)
        chatRepository = spyk(ChatRepositoryImpl(databaseService, kotlinx.coroutines.test.UnconfinedTestDispatcher(), chatApiService, serverUrlMapper, sharedPrefManager), recordPrivateCalls = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun fetchAiProviders_returnsMap() = runTest {
        val serverUrl = "http://example.com"
        val mockMapping = ServerUrlMapper.UrlMapping(primaryUrl = serverUrl)
        val mockResponse = mapOf("provider1" to true, "provider2" to false)
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)

        every { sharedPrefManager.rawPreferences } returns mockPrefs
        every { serverUrlMapper.processUrl(serverUrl) } returns mockMapping
        coEvery { serverUrlMapper.updateServerIfNecessary(any(), any(), any()) } answers { }
        coEvery { chatApiService.fetchAiProviders() } returns mockResponse

        val result = chatRepository.fetchAiProviders(serverUrl) { true }

        assertEquals(mockResponse, result)
        verify(exactly = 1) { serverUrlMapper.processUrl(serverUrl) }
        coVerify(exactly = 1) { serverUrlMapper.updateServerIfNecessary(mockMapping, mockPrefs, any()) }
        coVerify(exactly = 1) { chatApiService.fetchAiProviders() }
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

        coEvery { chatRepository.saveNewChat(any()) } answers { callOriginal() }

        chatRepository.saveNewChat(chatObj)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun continueConversation_executesTransaction() = runTest {
        val id = "123"
        val query = "hello"
        val response = "hi"
        val rev = "1-rev"

        coEvery { chatRepository.continueConversation(any(), any(), any(), any()) } answers { callOriginal() }

        chatRepository.continueConversation(id, query, response, rev)

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun insertChatHistoryList_executesTransaction() = runTest {
        val chatObj1 = JsonObject()
        val chatObj2 = JsonObject()

        coEvery { chatRepository.insertChatHistoryList(any()) } answers { callOriginal() }

        chatRepository.insertChatHistoryList(listOf(chatObj1, chatObj2))

        coVerify(exactly = 1) { databaseService.executeTransactionAsync(any()) }
    }

    @Test
    fun sendNewChatRequest_callsChatApiService() = runTest {
        val query = "test query"
        val user = "testUser"
        val aiProvider = AiProvider("OpenAI", "GPT-4")
        val mockResponse = Response.success(ChatResponse())

        coEvery { chatApiService.sendChatRequest(any()) } returns mockResponse

        val result = chatRepository.sendNewChatRequest(query, user, aiProvider)

        assertEquals(mockResponse, result)
        coVerify(exactly = 1) { chatApiService.sendChatRequest(any<RequestBody>()) }
    }

    @Test
    fun sendContinueChatRequest_callsChatApiService() = runTest {
        val message = "test message"
        val user = "testUser"
        val aiProvider = AiProvider("OpenAI", "GPT-4")
        val id = "chat-123"
        val rev = "1-rev"
        val mockResponse = Response.success(ChatResponse())

        coEvery { chatApiService.sendChatRequest(any()) } returns mockResponse

        val result = chatRepository.sendContinueChatRequest(message, user, aiProvider, id, rev)

        assertEquals(mockResponse, result)
        coVerify(exactly = 1) { chatApiService.sendChatRequest(any<RequestBody>()) }
    }
}
