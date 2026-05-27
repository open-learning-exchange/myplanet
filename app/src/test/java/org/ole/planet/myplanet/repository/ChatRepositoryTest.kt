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

        // 1. Valid document (NEW record - should not call delete)
        val newDoc = JsonObject()
        newDoc.addProperty("_id", "123")
        newDoc.addProperty("_rev", "1-rev")
        val newObjWrapper = JsonObject()
        newObjWrapper.add("doc", newDoc)
        jsonArray.add(newObjWrapper)

        // 2. Valid document (EXISTING record - should call delete)
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
        val mockExistingRecord: RealmChatHistory = mockk(relaxed = true)

        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery

        // Mocking finding the records
        every { mockQuery.equalTo("_id", "123") } returns mockQuery
        every { mockQuery.equalTo("_id", "456") } returns mockQuery

        // For "123" return null (new record)
        // For "456" return existing record (triggers deleteFromRealm)
        every { mockQuery.findFirst() } returnsMany listOf(null, mockExistingRecord)

        // Mock creation
        every { mockRealm.createObject(RealmChatHistory::class.java, "123") } returns RealmChatHistory().apply { _id = "123" }
        every { mockRealm.createObject(RealmChatHistory::class.java, "456") } returns RealmChatHistory().apply { _id = "456" }

        chatRepository.insertChatHistoryBatch(mockRealm, jsonArray)

        // Verification for NEW document ("123")
        verify(exactly = 1) { mockQuery.equalTo("_id", "123") }
        verify(exactly = 1) { mockRealm.createObject(RealmChatHistory::class.java, "123") }
        // The existing record mock should NOT be deleted in the context of "123"

        // Verification for EXISTING document ("456")
        verify(exactly = 1) { mockQuery.equalTo("_id", "456") }
        verify(exactly = 1) { mockRealm.createObject(RealmChatHistory::class.java, "456") }
        verify(exactly = 1) { mockExistingRecord.deleteFromRealm() }

        // Verification for DESIGN document (should be ignored entirely)
        verify(exactly = 0) { mockQuery.equalTo("_id", "_design/foo") }
        verify(exactly = 0) { mockRealm.createObject(RealmChatHistory::class.java, "_design/foo") }
    }
}
