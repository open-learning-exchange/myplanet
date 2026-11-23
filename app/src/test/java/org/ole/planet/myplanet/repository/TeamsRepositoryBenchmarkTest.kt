package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.TeamLog
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsRepositoryBenchmarkTest {
    private lateinit var teamsRepository: TeamsRepositoryImpl
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)
    private val uploadManager: UploadManager = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val preferences: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk()
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val resourcesRepositoryLazy: dagger.Lazy<ResourcesRepository> = mockk()
    private val teamLogDao: TeamLogDao = mockk(relaxed = true)
    private val teamTaskDao: TeamTaskDao = mockk(relaxed = true)
    private val myLibraryDao: MyLibraryDao = mockk(relaxed = true)
    private val teamDao: TeamDao = mockk(relaxed = true)
    private val courseDao: CourseDao = mockk(relaxed = true)
    private val courseStepDao: CourseStepDao = mockk(relaxed = true)
    private val appDatabase: AppDatabase = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        teamsRepository = TeamsRepositoryImpl(
            mockk(relaxed = true),
            userSessionManager,
            uploadManager,
            gson,
            preferences,
            sharedPrefManager,
            serverUrlMapper,
            dispatcherProvider,
            userRepository,
            resourcesRepositoryLazy,
            teamLogDao,
            teamTaskDao,
            myLibraryDao,
            teamDao,
            courseDao,
            courseStepDao,
            appDatabase,
        )
        io.mockk.mockkStatic("androidx.room.RoomDatabaseKt")
        val blockSlot = io.mockk.slot<suspend () -> Unit>()
        coEvery { appDatabase.withTransaction(capture(blockSlot)) } coAnswers { blockSlot.captured.invoke() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun benchmarkInsertTeamLogs() = runTest {
        val logs = (1..100).map { i ->
            JsonObject().apply {
                addProperty("_id", "id_$i")
                addProperty("_rev", "rev_$i")
            }
        }

        teamsRepository.insertTeamLogs(logs)

        coVerify(exactly = 1) { teamLogDao.upsertAll(match { it.size == 100 && it.first().id == "id_1" }) }
    }

    @Test
    fun testInsertTeamLogsWithDuplicatesInBatch() = runTest {
        val logs = listOf(
            JsonObject().apply { addProperty("_id", "dup_id"); addProperty("_rev", "rev1") },
            JsonObject().apply { addProperty("_id", "dup_id"); addProperty("_rev", "rev2") }
        )

        teamsRepository.insertTeamLogs(logs)

        coVerify(exactly = 1) { teamLogDao.upsertAll(match { it.size == 2 && it.all { log -> log.id == "dup_id" } }) }
    }

    @Test
    fun `insertTeamLogs updates a visit logged on this device instead of adding a second row`() = runTest {
        val local = TeamLog().apply { id = "local-uuid"; _id = "remote1"; _rev = "1-a"; type = "teamVisit" }
        coEvery { teamLogDao.getByRemoteIds(listOf("remote1")) } returns listOf(local)

        teamsRepository.insertTeamLogs(listOf(JsonObject().apply { addProperty("_id", "remote1"); addProperty("_rev", "1-a") }))

        coVerify { teamLogDao.deleteByIds(listOf("remote1")) }
        coVerify { teamLogDao.upsertAll(match { it.single().id == "local-uuid" && it.single()._id == "remote1" }) }
    }
}
