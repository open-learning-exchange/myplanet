package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper

class ChatRepositoryTest {
    private lateinit var chatRepository: ChatRepositoryImpl
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val mockRealm: Realm = mockk(relaxed = true)
    private val chatApiService: ChatApiService = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    @Before
    fun setup() {
        every { sharedPrefManager.rawPreferences } returns mockk(relaxed = true)
        chatRepository = ChatRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            chatApiService,
            serverUrlMapper,
            sharedPrefManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun insertChatHistoryBatch_filtersDesignDocsAndHandlesExistingRecords() {
        val jsonArray = JsonArray()

        // 1. Valid document (NEW record)
        val newDoc = JsonObject()
        newDoc.addProperty("_id", "123")
        newDoc.addProperty("_rev", "1-rev")
        val newObjWrapper = JsonObject()
        newObjWrapper.add("doc", newDoc)
        jsonArray.add(newObjWrapper)

        // 2. Valid document (EXISTING record)
        val existingDoc = JsonObject()
        existingDoc.addProperty("_id", "456")
        existingDoc.addProperty("_rev", "2-rev")
        val existingObjWrapper = JsonObject()
        existingObjWrapper.add("doc", existingDoc)
        jsonArray.add(existingObjWrapper)

        // 3. Design document (should be ignored)
        val designDoc = JsonObject()
        designDoc.addProperty("_id", "_design/foo")
        val designObjWrapper = JsonObject()
        designObjWrapper.add("doc", designDoc)
        jsonArray.add(designObjWrapper)

        val mockQuery: RealmQuery<RealmChatHistory> = mockk(relaxed = true)
        val mockResults: io.realm.RealmResults<RealmChatHistory> = mockk(relaxed = true)

        val mockConversationList: io.realm.RealmList<org.ole.planet.myplanet.model.RealmConversation> = mockk(relaxed = true)
        val existingChat = RealmChatHistory().apply {
            _id = "456"
            conversations = mockConversationList
        }

        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
        every { mockQuery.`in`("_id", any<Array<String>>()) } returns mockQuery
        every { mockQuery.findAll() } returns mockResults
        every { mockResults.iterator() } answers { mutableListOf(existingChat).iterator() }

        chatRepository.insertChatHistoryBatch(mockRealm, jsonArray)

        // Verification for bulk query
        verify(exactly = 1) { mockQuery.`in`("_id", match<Array<String>> { it.toSet() == setOf("123", "456") }) }

        // Verification that existing chat's conversations are cleared to prevent orphans
        verify(exactly = 1) { mockConversationList.deleteAllFromRealm() }

        // Verification for bulk insert
        verify(exactly = 1) {
            mockRealm.insertOrUpdate(match<List<RealmChatHistory>> { list ->
                list.size == 2 && list.any { it._id == "123" } && list.any { it._id == "456" }
            })
        }

        // Verification for DESIGN document (should be ignored entirely)
        verify(exactly = 0) { mockQuery.equalTo("_id", "_design/foo") }
    }
}
