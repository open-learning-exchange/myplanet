package org.ole.planet.myplanet.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonArray
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmConfiguration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.utils.DispatcherProvider

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ProgressRepositoryImplTest {

    private lateinit var repository: ProgressRepositoryImpl
    private val dispatcherProvider: DispatcherProvider = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var databaseService: DatabaseService

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Realm.init(context)
        val config = RealmConfiguration.Builder().inMemory().name("test-realm").build()
        Realm.setDefaultConfiguration(config)

        databaseService = DatabaseService(context)
        every { dispatcherProvider.io } returns testDispatcher

        repository = ProgressRepositoryImpl(databaseService, dispatcherProvider)
    }

    @Test
    fun fetchCourseData_uses_dispatcherProvider_io() = runTest(testDispatcher) {
        val result = repository.fetchCourseData("user123")
        assertEquals(JsonArray(), result)
        verify { dispatcherProvider.io }
    }
}
