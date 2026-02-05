package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.data.api.ApiInterface
import retrofit2.Response

class ConfigurationsRepositoryTest {

    private lateinit var repository: ConfigurationsRepositoryImpl
    private val context: Context = mock()
    private val apiInterface: ApiInterface = mock()
    private val preferences: SharedPreferences = mock()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)

    @Before
    fun setUp() {
        repository = ConfigurationsRepositoryImpl(context, apiInterface, testScope, preferences)
    }

    @Test
    fun `checkServerAvailability(url) returns true when db count is 8 or more`() = runTest {
        val url = "http://test.com"
        val dbList = "db1,db2,db3,db4,db5,db6,db7,db8"
        val responseBody = dbList.toResponseBody("text/plain".toMediaTypeOrNull())
        whenever(apiInterface.isPlanetAvailable(url)).thenReturn(Response.success(responseBody))

        val result = repository.checkServerAvailability(url)

        assertTrue(result)
    }

    @Test
    fun `checkServerAvailability(url) returns false when db count is less than 8`() = runTest {
        val url = "http://test.com"
        val dbList = "db1,db2,db3"
        val responseBody = dbList.toResponseBody("text/plain".toMediaTypeOrNull())
        whenever(apiInterface.isPlanetAvailable(url)).thenReturn(Response.success(responseBody))

        val result = repository.checkServerAvailability(url)

        assertFalse(result)
    }

    @Test
    fun `checkServerAvailability(url) returns true when response code is 401`() = runTest {
        val url = "http://test.com"
        val errorBody = "Unauthorized".toResponseBody("text/plain".toMediaTypeOrNull())
        whenever(apiInterface.isPlanetAvailable(url)).thenReturn(Response.error(401, errorBody))

        val result = repository.checkServerAvailability(url)

        assertTrue(result)
    }

    @Test
    fun `checkServerAvailability(url) returns false when response code is 404`() = runTest {
        val url = "http://test.com"
        val errorBody = "Not Found".toResponseBody("text/plain".toMediaTypeOrNull())
        whenever(apiInterface.isPlanetAvailable(url)).thenReturn(Response.error(404, errorBody))

        val result = repository.checkServerAvailability(url)

        assertFalse(result)
    }

    @Test
    fun `checkServerAvailability(url) returns false on exception`() = runTest {
        val url = "http://test.com"
        whenever(apiInterface.isPlanetAvailable(url)).thenThrow(RuntimeException("Network error"))

        val result = repository.checkServerAvailability(url)

        assertFalse(result)
    }
}
