package org.ole.planet.myplanet.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.realm.Realm
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ChatApiService
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

}
