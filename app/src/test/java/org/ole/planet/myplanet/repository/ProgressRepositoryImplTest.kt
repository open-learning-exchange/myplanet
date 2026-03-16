package org.ole.planet.myplanet.repository

import com.google.gson.JsonArray
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.utils.DispatcherProvider

@ExperimentalCoroutinesApi
class ProgressRepositoryImplTest {

    private lateinit var repository: ProgressRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val databaseService: DatabaseService = mockk(relaxed = true)

    @Before
    fun setup() {
        every { dispatcherProvider.io } returns testDispatcher

        repository = spyk(ProgressRepositoryImpl(databaseService, dispatcherProvider))

        // Mock the protected queryList method to return empty list and avoid Realm access
        coEvery { repository["queryList"](RealmMyCourse::class.java, any<Function1<*, *>>()) } returns emptyList<RealmMyCourse>()
    }

    @Test
    fun fetchCourseData_uses_dispatcherProvider_io() = runTest(testDispatcher) {
        val result = repository.fetchCourseData("user123")
        assertEquals(JsonArray(), result)
        verify { dispatcherProvider.io }
    }
}
