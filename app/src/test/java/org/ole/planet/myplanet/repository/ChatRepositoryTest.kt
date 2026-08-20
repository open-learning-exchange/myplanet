package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.ole.planet.myplanet.data.api.ChatApiService
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryTest {
    private lateinit var chatRepository: ChatRepositoryImpl
    private val chatDao: ChatDao = mockk(relaxed = true)
    private val chatApiService: ChatApiService = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)

    @Before
    fun setup() {
        every { sharedPrefManager.rawPreferences } returns mockk(relaxed = true)
        every { dispatcherProvider.default } returns kotlinx.coroutines.Dispatchers.Unconfined
        every { dispatcherProvider.io } returns kotlinx.coroutines.Dispatchers.Unconfined
        every { dispatcherProvider.main } returns kotlinx.coroutines.Dispatchers.Unconfined
        chatRepository = ChatRepositoryImpl(
            chatDao,
            chatApiService,
            serverUrlMapper,
            sharedPrefManager,
            dispatcherProvider,
            Gson()
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}
