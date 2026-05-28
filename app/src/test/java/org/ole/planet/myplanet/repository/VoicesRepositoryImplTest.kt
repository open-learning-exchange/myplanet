package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@ExperimentalCoroutinesApi
class VoicesRepositoryImplTest {

    private lateinit var repository: VoicesRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { dispatcherProvider.default } returns testDispatcher
        repository = spyk(VoicesRepositoryImpl(
            databaseService,
            UnconfinedTestDispatcher(),
            dispatcherProvider,
            gson,
            sharedPrefManager
        ), recordPrivateCalls = true)
    }

    @Test
    fun getCommunityNews_uses_dispatcherProvider_default() = testScope.runTest {
        coEvery { repository["queryListFlow"](RealmNews::class.java, any<Function1<*, *>>()) } returns kotlinx.coroutines.flow.flowOf(emptyList<RealmNews>())

        val flow = repository.getCommunityNews("testUser")
        val result = flow.toList()

        assertNotNull(result)
        io.mockk.verify { dispatcherProvider.default }
    }

    @Test
    fun getDiscussionsByTeamIdFlow_uses_dispatcherProvider_default() = testScope.runTest {
        coEvery { repository["queryListFlow"](RealmNews::class.java, any<Function1<*, *>>()) } returns kotlinx.coroutines.flow.flowOf(emptyList<RealmNews>())

        val flow = repository.getDiscussionsByTeamIdFlow("testTeam")
        val result = flow.toList()

        assertNotNull(result)
        io.mockk.verify { dispatcherProvider.default }
    }
}
