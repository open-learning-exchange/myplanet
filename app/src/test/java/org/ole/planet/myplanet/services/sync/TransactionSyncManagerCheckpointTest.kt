package org.ole.planet.myplanet.services.sync

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.Lazy
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.data.room.dao.SyncCursorDao
import org.ole.planet.myplanet.model.SyncCursor
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
 * Covers the incremental `_changes`-cursor behaviour in [TransactionSyncManager.syncDb] for the
 * heavy tables (see [TransactionSyncManager.incrementalTables] / `HeavyTableSyncWorker`).
 * Robolectric supplies working `android.util.Log`/`SystemClock` shadows that syncDb relies on.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionSyncManagerCheckpointTest {

    private lateinit var transactionSyncManager: TransactionSyncManager
    private val apiInterface: ApiInterface = mockk()
    private val sharedPrefManager: SharedPrefManager = mockk()
    private val ratingsRepository: RatingsRepository = mockk()
    private val tagsRepository: TagsRepository = mockk()
    private val teamsSyncRepository: TeamsSyncRepository = mockk()
    private val teamsSyncRepositoryLazy: Lazy<TeamsSyncRepository> = mockk()
    private val notificationsRepository: NotificationsRepository = mockk()
    private val coursesRepository: CoursesRepository = mockk()
    private val syncCursorDao: SyncCursorDao = mockk()
    private val upsertedCursors = mutableListOf<SyncCursor>()

    // Plain Dispatchers.Unconfined + runBlocking (no TestDispatcher/runTest): syncDb only needs
    // its withContext(io) to run inline, and this avoids runTest's uncaught-exception detector
    // flagging the CancellationException/RuntimeException these tests deliberately drive.
    private val dispatcherProvider: DispatcherProvider = mockk()

    private fun changesResponse(count: Int, lastSeq: String, idPrefix: String = "rating"): Response<JsonObject> {
        val body = JsonObject().apply {
            add("results", JsonArray().apply {
                repeat(count) { i ->
                    add(JsonObject().apply {
                        addProperty("seq", "${i + 1}")
                        addProperty("id", "${idPrefix}_$i")
                        add("doc", JsonObject().apply { addProperty("_id", "${idPrefix}_$i") })
                    })
                }
            })
            addProperty("last_seq", lastSeq)
        }
        val response = mockk<Response<JsonObject>>()
        every { response.isSuccessful } returns true
        every { response.body() } returns body
        every { response.code() } returns 200
        return response
    }

    private fun deletedChangesResponse(deletedIds: List<String>, lastSeq: String): Response<JsonObject> {
        val body = JsonObject().apply {
            add("results", JsonArray().apply {
                deletedIds.forEach { id ->
                    add(JsonObject().apply {
                        addProperty("seq", lastSeq)
                        addProperty("id", id)
                        addProperty("deleted", true)
                    })
                }
            })
            addProperty("last_seq", lastSeq)
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

        coEvery { syncCursorDao.getSince(any()) } returns null
        coEvery { syncCursorDao.upsert(capture(upsertedCursors)) } returns Unit
        every { teamsSyncRepositoryLazy.get() } returns teamsSyncRepository

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
            teamsSyncRepositoryLazy,
            notificationsRepository,
            tagsRepository,
            ratingsRepository,
            mockk<SubmissionsRepository>(relaxed = true),
            coursesRepository,
            mockk<CommunityRepository>(relaxed = true),
            mockk<HealthRepository>(relaxed = true),
            mockk<ProgressRepository>(relaxed = true),
            mockk<SurveysRepository>(relaxed = true),
            // syncDb confines its work to dispatcherProvider.io, not this scope; a throwaway
            // scope is enough and keeps each test isolated (no shared leaked-exception state).
            CoroutineScope(Dispatchers.Unconfined),
            dispatcherProvider,
            mockk<org.ole.planet.myplanet.services.UserSessionManager>(relaxed = true),
            syncCursorDao
        )
    }

    @After
    fun tearDown() {
        unmockkObject(UrlUtils)
        unmockkObject(SyncTimeLogger)
    }

    @Test
    fun `cursor persists the committed page's last_seq`() = runBlocking {
        coEvery { apiInterface.getJsonObject(any(), any()) } returnsMany
            listOf(changesResponse(20, lastSeq = "20"), changesResponse(0, lastSeq = "20"))
        coEvery { ratingsRepository.insertRatingsFromSync(any()) } returns Unit

        val total = transactionSyncManager.syncDb("ratings", useCheckpoint = true)

        assertEquals(20, total)
        // The cursor write only happens after the batch is committed, proving the resume point
        // tracks the last successfully-processed page, not the fetch.
        assertEquals(listOf(SyncCursor("ratings", "20")), upsertedCursors)
    }

    // runBlocking (not runTest) so the RuntimeException that syncDb catches internally isn't
    // re-flagged by runTest's uncaught-exception detection as it unwinds the withContext child.
    @Test
    fun `cursor does not advance past a batch that failed to commit`() = runBlocking {
        coEvery { apiInterface.getJsonObject(any(), any()) } returns changesResponse(20, lastSeq = "20")
        coEvery { ratingsRepository.insertRatingsFromSync(any()) } throws RuntimeException("insert boom")

        val total = transactionSyncManager.syncDb("ratings", useCheckpoint = true)

        assertEquals(0, total)
        // The insert throws before the cursor write for that page is reached, so nothing should
        // have been persisted -- the next sync must retry this same page, not skip past it.
        coVerify(exactly = 0) { syncCursorDao.upsert(any()) }
    }

    // runBlocking (not runTest) so throwing/rethrowing a CancellationException across the
    // withContext boundary isn't misread by runTest's uncaught-exception detection.
    @Test
    fun `cancellation propagates instead of being swallowed`() = runBlocking {
        coEvery { apiInterface.getJsonObject(any(), any()) } throws
            CancellationException("worker stopped")

        try {
            transactionSyncManager.syncDb("ratings", useCheckpoint = true)
            fail("expected CancellationException to propagate")
        } catch (_: CancellationException) {
            // expected: the generic catch(Exception) must not swallow cancellation
        }
    }

    // The following cover deleteBatch's per-table dispatch added when incremental sync was
    // extended to the rest of parallelTables -- one representative case per wiring style rather
    // than all 16, per table: a freshly-added DAO passthrough (tags), an existing general-purpose
    // repository method reused as-is (teams), an existing Set<String>-based method (notifications),
    // and the deliberately-narrow top-level-only case (courses).

    @Test
    fun `a deleted tags doc is propagated via the new deleteByIds passthrough`() = runBlocking {
        coEvery { apiInterface.getJsonObject(any(), any()) } returnsMany
            listOf(deletedChangesResponse(listOf("tag1"), lastSeq = "5"), changesResponse(0, lastSeq = "5"))
        coEvery { tagsRepository.deleteByIds(listOf("tag1")) } returns Unit

        val total = transactionSyncManager.syncDb("tags", useCheckpoint = false)

        assertEquals(0, total)
        coVerify { tagsRepository.deleteByIds(listOf("tag1")) }
    }

    @Test
    fun `a deleted teams doc reuses the existing deleteLocalTeamRecords method`() = runBlocking {
        coEvery { apiInterface.getJsonObject(any(), any()) } returnsMany
            listOf(deletedChangesResponse(listOf("team1"), lastSeq = "5"), changesResponse(0, lastSeq = "5"))
        coEvery { teamsSyncRepository.deleteLocalTeamRecords(listOf("team1")) } returns Unit

        transactionSyncManager.syncDb("teams", useCheckpoint = false)

        coVerify { teamsSyncRepository.deleteLocalTeamRecords(listOf("team1")) }
    }

    @Test
    fun `a deleted notifications doc reuses the existing Set-based deleteNotifications method`() = runBlocking {
        coEvery { apiInterface.getJsonObject(any(), any()) } returnsMany
            listOf(deletedChangesResponse(listOf("n1"), lastSeq = "5"), changesResponse(0, lastSeq = "5"))
        coEvery { notificationsRepository.deleteNotifications(setOf("n1")) } returns setOf("n1")

        transactionSyncManager.syncDb("notifications", useCheckpoint = false)

        coVerify { notificationsRepository.deleteNotifications(setOf("n1")) }
    }

    @Test
    fun `a deleted courses doc only removes the top-level MyCourse row`() = runBlocking {
        coEvery { apiInterface.getJsonObject(any(), any()) } returnsMany
            listOf(deletedChangesResponse(listOf("course1"), lastSeq = "5"), changesResponse(0, lastSeq = "5"))
        coEvery { coursesRepository.deleteByIds(listOf("course1")) } returns Unit

        transactionSyncManager.syncDb("courses", useCheckpoint = false)

        coVerify { coursesRepository.deleteByIds(listOf("course1")) }
    }
}
