package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.model.AiProvider
import org.ole.planet.myplanet.model.ChatResponse
import org.ole.planet.myplanet.model.CouchDBResponse
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImplTest {
    private lateinit var chatRepository: ChatRepositoryImpl
    private val chatDao: ChatDao = mockk(relaxed = true)
    private val chatApiService: ChatApiService = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    @Before
    fun setup() {
        every { sharedPrefManager.rawPreferences } returns mockk(relaxed = true)
        chatRepository = ChatRepositoryImpl(chatDao, chatApiService, serverUrlMapper, sharedPrefManager)
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

        chatRepository.reachabilityCheck = { true }
        val result = chatRepository.fetchAiProviders(serverUrl)

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
    fun getChatHistoryForUser_delegatesToDao() = runTest {
        val userName = "testUser"
        val mockHistoryList = listOf(RealmChatHistory().apply { user = userName })
        coEvery { chatDao.getByUser(userName) } returns mockHistoryList

        val result = chatRepository.getChatHistoryForUser(userName)

        assertEquals(mockHistoryList, result)
        coVerify(exactly = 1) { chatDao.getByUser(userName) }
    }

    @Test
    fun getLatestRev_findsHighestRevByNumericPrefix() = runTest {
        val id = "123"
        val item1 = RealmChatHistory().apply { _rev = "1-abc" }
        val item2 = RealmChatHistory().apply { _rev = "10-def" }
        val item3 = RealmChatHistory().apply { _rev = "2-ghi" }
        coEvery { chatDao.getByDocId(id) } returns listOf(item1, item2, item3)

        val result = chatRepository.getLatestRev(id)

        assertEquals("10-def", result)
    }

    @Test
    fun insertChatHistoryList_upsertsAllViaDao() = runTest {
        val chatObj1 = JsonObject().apply {
            addProperty("_id", "1")
            addProperty("_rev", "1-rev")
            add("conversations", JsonArray())
        }
        val chatObj2 = JsonObject().apply {
            addProperty("_id", "2")
            addProperty("_rev", "2-rev")
            add("conversations", JsonArray())
        }
        val slot = slot<List<RealmChatHistory>>()
        coEvery { chatDao.upsertAll(capture(slot)) } returns Unit

        chatRepository.insertChatHistoryList(listOf(chatObj1, chatObj2))

        coVerify(exactly = 1) { chatDao.upsertAll(any()) }
        assertEquals(2, slot.captured.size)
    }

    @Test
    fun insertChatHistoryFromSync_unwrapsDocAndUpsertsBatch() = runTest {
        val chatDoc = JsonObject().apply {
            addProperty("_id", "chat123")
            addProperty("_rev", "1-rev")
            addProperty("title", "Test Chat")
            add("conversations", JsonArray())
        }
        val wrapper = JsonObject().apply { add("doc", chatDoc) }
        val slot = slot<List<RealmChatHistory>>()
        coEvery { chatDao.upsertAll(capture(slot)) } returns Unit

        chatRepository.insertChatHistoryFromSync(listOf(wrapper))

        coVerify(exactly = 1) { chatDao.upsertAll(any()) }
        val inserted = slot.captured.first()
        assertEquals("chat123", inserted._id)
        assertEquals("Test Chat", inserted.title)
    }

    @Test
    fun sendNewChatRequest_callsChatApiService() = runTest {
        val query = "test query"
        val user = "testUser"
        val aiProvider = AiProvider("OpenAI", "GPT-4")
        val couchDb = CouchDBResponse(ok = true, id = "test-id", rev = "test-rev")
        val mockResponse = retrofit2.Response.success(ChatResponse(status = "Success", chat = "test chat", couchDBResponse = couchDb))

        coEvery { chatApiService.sendChatRequest(any()) } returns mockResponse

        val result = chatRepository.sendNewChatRequest(query, user, aiProvider)

        assertEquals(ChatResult.Success("test chat", "test-id", "test-rev"), result)
        coVerify(exactly = 1) { chatApiService.sendChatRequest(any<okhttp3.RequestBody>()) }
    }

    @Test
    fun sendContinueChatRequest_callsChatApiService() = runTest {
        val message = "test message"
        val user = "testUser"
        val aiProvider = AiProvider("OpenAI", "GPT-4")
        val id = "chat-123"
        val rev = "1-rev"
        val couchDb = CouchDBResponse(ok = true, id = id, rev = "2-rev")
        val mockResponse = retrofit2.Response.success(ChatResponse(status = "Success", chat = "test chat", couchDBResponse = couchDb))

        coEvery { chatApiService.sendChatRequest(any()) } returns mockResponse

        val result = chatRepository.sendContinueChatRequest(message, user, aiProvider, id, rev)

        assertEquals(ChatResult.Success("test chat", id, "2-rev"), result)
        coVerify(exactly = 1) { chatApiService.sendChatRequest(any<okhttp3.RequestBody>()) }
    }
}
