package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
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
        chatRepository = spyk(
            ChatRepositoryImpl(
                databaseService,
                UnconfinedTestDispatcher(),
                chatApiService,
                serverUrlMapper,
                sharedPrefManager
            ), recordPrivateCalls = true
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun insertChatHistoryBatch_callsBulkInsertFromSync() {
        val jsonArray = JsonArray()

        every { chatRepository.bulkInsertFromSync(mockRealm, jsonArray) } answers { }

        chatRepository.insertChatHistoryBatch(mockRealm, jsonArray)

        verify(exactly = 1) { chatRepository.bulkInsertFromSync(mockRealm, jsonArray) }
    }

    @Test
    fun bulkInsertFromSync_filtersDesignDocsAndCallsInsertChatHistory() {
        val jsonArray = JsonArray()

        // Valid document
        val validDoc = JsonObject()
        validDoc.addProperty("_id", "123")
        validDoc.addProperty("_rev", "1-rev")
        val validObjWrapper = JsonObject()
        validObjWrapper.add("doc", validDoc)
        jsonArray.add(validObjWrapper)

        // Design document
        val designDoc = JsonObject()
        designDoc.addProperty("_id", "_design/foo")
        val designObjWrapper = JsonObject()
        designObjWrapper.add("doc", designDoc)
        jsonArray.add(designObjWrapper)

        val mockQuery: RealmQuery<RealmChatHistory> = mockk(relaxed = true)
        every { mockRealm.where(RealmChatHistory::class.java) } returns mockQuery
        every { mockQuery.equalTo("_id", "123") } returns mockQuery
        every { mockQuery.findFirst() } returns null
        every { mockRealm.createObject(RealmChatHistory::class.java, "123") } returns RealmChatHistory().apply { _id = "123" }

        chatRepository.bulkInsertFromSync(mockRealm, jsonArray)

        // Verification for valid document
        verify(exactly = 1) { mockRealm.where(RealmChatHistory::class.java) }
        verify(exactly = 1) { mockQuery.equalTo("_id", "123") }
        verify(exactly = 1) { mockRealm.createObject(RealmChatHistory::class.java, "123") }

        // Verification for design document (should not happen)
        verify(exactly = 0) { mockQuery.equalTo("_id", "_design/foo") }
        verify(exactly = 0) { mockRealm.createObject(RealmChatHistory::class.java, "_design/foo") }
    }
}
