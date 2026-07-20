package org.ole.planet.myplanet.services.sync

import android.content.SharedPreferences
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.SyncTimeLogger
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.RobolectricTestRunner
import retrofit2.Response

/**
 * Covers the checkpoint/cancellation behaviour added to [TransactionSyncManager.syncDb] for
 * background heavy-table sync (see the resume/interrupt handling in `HeavyTableSyncWorker`).
 * Robolectric supplies working `android.util.Log`/`SystemClock` shadows that syncDb relies on.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionSyncManagerCheckpointTest {

    private lateinit var transactionSyncManager: TransactionSyncManager
    private val apiInterface: ApiInterface = mockk()
    private val sharedPrefManager: SharedPrefManager = mockk()
    private val ratingsRepository: RatingsRepository = mockk()
    private val prefs: SharedPreferences = mockk()
    private val editor: SharedPreferences.Editor = mockk()
    private val putValues = mutableListOf<Int>()

    // Plain Dispatchers.Unconfined + runBlocking (no TestDispatcher/runTest): syncDb only needs
    // its withContext(io) to run inline, and this avoids runTest's uncaught-exception detector
    // flagging the CancellationException/RuntimeException these tests deliberately drive.
    private val dispatcherProvider: DispatcherProvider = mockk()

    private fun rowsResponse(count: Int): Response<JsonObject> {
        val body = JsonObject().apply {
            add("rows", JsonArray().apply {
                repeat(count) { i ->
                    add(JsonObject().apply {
                        add("doc", JsonObject().apply { addProperty("_id", "rating_$i") })
                    })
                }
            })
        }
        val response = mockk<Response<JsonObject>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns body
        every { response.code() } returns 200
        return response
    }

    @Before
    fun setup() {
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "http://mockurl"
        every { UrlUtils.header } returns "Basic mockHeader"

        mockkObject(SyncTimeLogger)
        every { SyncTimeLogger.logApiCall(any(), any(), any(), any()) } returns Unit
        every { SyncTimeLogger.logRealmOperation(any(), any(), any(), any()) } returns Unit
        every { SyncTimeLogger.logDetail(any(), any()) } returns Unit

        every { dispatcherProvider.io } returns Dispatchers.Unconfined
        every { dispatcherProvider.main } returns Dispatchers.Unconfined

        every { sharedPrefManager.rawPreferences } returns prefs
        every { prefs.getInt(any(), any()) } returns 0
        every { prefs.edit() } returns editor
        every { editor.putInt(any(), capture(putValues)) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.commit() } returns true

        transactionSyncManager = TransactionSyncManager(
            apiInterface,
            mockk(relaxed = true),
            mockk<VoicesRepository>(relaxed = true),
            mockk<ChatRepository>(relaxed = true),
            mockk<FeedbackRepository>(relaxed = true),
            sharedPrefManager,
            mockk<UserRepository>(relaxed = true),
            mockk<UserSyncRepository>(relaxed = true),
            mockk<ActivitiesRepository>(relaxed = true),
            mockk<Lazy<TeamsRepository>>(relaxed = true),
            mockk<Lazy<TeamsSyncRepository>>(relaxed = true),
            mockk<NotificationsRepository>(relaxed = true),
            mockk<TagsRepository>(relaxed = true),
            ratingsRepository,
            mockk<SubmissionsRepository>(relaxed = true),
            mockk<CoursesRepository>(relaxed = true),
            mockk<CommunityRepository>(relaxed = true),
            mockk<HealthRepository>(relaxed = true),
            mockk<ProgressRepository>(relaxed = true),
            mockk<SurveysRepository>(relaxed = true),
            // syncDb confines its work to dispatcherProvider.io, not this scope; a throwaway
            // scope is enough and keeps each test isolated (no shared leaked-exception state).
            CoroutineScope(Dispatchers.Unconfined),
            dispatcherProvider
        )
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
        unmockkObject(SyncTimeLogger)
    }

    @Test
    fun `checkpoint persists the committed batch boundary`() = runBlocking {
        coEvery { apiInterface.findDocs(any(), any(), any(), any()) } returnsMany
            listOf(rowsResponse(20), rowsResponse(0))
        coEvery { ratingsRepository.insertRatingsFromSync(any()) } returns Unit

        val total = transactionSyncManager.syncDb("ratings", useCheckpoint = true)

        assertEquals(20, total)
        // The post-commit write persists skip=20, proving progress is checkpointed
        // only after the batch actually lands.
        assertTrue("expected a checkpoint at the committed boundary 20", putValues.contains(20))
    }

    // runBlocking (not runTest) so the RuntimeException that syncDb catches internally isn't
    // re-flagged by runTest's uncaught-exception detection as it unwinds the withContext child.
    @Test
    fun `checkpoint does not advance past a batch that failed to commit`() = runBlocking {
        coEvery { apiInterface.findDocs(any(), any(), any(), any()) } returns rowsResponse(20)
        coEvery { ratingsRepository.insertRatingsFromSync(any()) } throws RuntimeException("insert boom")

        val total = transactionSyncManager.syncDb("ratings", useCheckpoint = true)

        assertEquals(0, total)
        // Only the pre-batch skip=0 should have been written; the fetched-but-uncommitted
        // page must not move the checkpoint to 20.
        assertTrue(putValues.contains(0))
        assertFalse("checkpoint must not advance for an uncommitted batch", putValues.contains(20))
    }

    // runBlocking (not runTest) so throwing/rethrowing a CancellationException across the
    // withContext boundary isn't misread by runTest's uncaught-exception detection.
    @Test
    fun `cancellation propagates instead of being swallowed`() = runBlocking {
        coEvery { apiInterface.findDocs(any(), any(), any(), any()) } throws
            CancellationException("worker stopped")

        try {
            transactionSyncManager.syncDb("ratings", useCheckpoint = true)
            fail("expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // expected: the generic catch(Exception) must not swallow cancellation
        }
    }
}
