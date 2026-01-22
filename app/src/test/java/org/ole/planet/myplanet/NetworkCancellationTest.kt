package org.ole.planet.myplanet

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.api.ApiInterface
import retrofit2.Response

@ExperimentalCoroutinesApi
class NetworkCancellationTest {

    @Test
    fun `test network call cancellation`() = runTest {
        val apiInterface = mockk<ApiInterface>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)

        coEvery { apiInterface.healthAccess(any()) } coAnswers {
            delay(1000)
            Response.success(ResponseBody.create(null, "success"))
        }

        val job = scope.launch {
            try {
                apiInterface.healthAccess("http://test.url")
            } catch (e: CancellationException) {
                throw e
            }
        }

        advanceTimeBy(500)
        job.cancel()

        assertTrue(job.isCancelled)
    }
}
