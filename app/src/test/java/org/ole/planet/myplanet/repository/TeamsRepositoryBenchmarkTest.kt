package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider

@OptIn(ExperimentalCoroutinesApi::class)
class TeamsRepositoryBenchmarkTest {
    private lateinit var teamsRepository: TeamsRepositoryImpl
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val userSessionManager: UserSessionManager = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val uploadManager: UploadManager = mockk(relaxed = true)
    private val gson: Gson = mockk(relaxed = true)
    private val preferences: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val serverUrlMapper: ServerUrlMapper = mockk(relaxed = true)
    private val dispatcherProvider: DispatcherProvider = mockk()
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val resourcesRepositoryLazy: dagger.Lazy<ResourcesRepository> = mockk()
    private val realm: Realm = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { dispatcherProvider.main } returns testDispatcher
        every { dispatcherProvider.io } returns testDispatcher
        every { dispatcherProvider.default } returns testDispatcher
        every { dispatcherProvider.unconfined } returns testDispatcher

        coEvery { databaseService.executeTransactionAsync(any()) } answers {
            val transaction = firstArg<(Realm) -> Unit>()
            transaction(realm)
        }

        teamsRepository = TeamsRepositoryImpl(
            activitiesRepository,
            databaseService,
            testDispatcher,
            userSessionManager,
            uploadManager,
            gson,
            preferences,
            sharedPrefManager,
            serverUrlMapper,
            dispatcherProvider,
            userRepository,
            resourcesRepositoryLazy
        )
    }

    @Test
    fun benchmarkInsertTeamLogs() = runTest {
        val logs = (1..100).map { i ->
            JsonObject().apply {
                addProperty("_id", "id_$i")
                addProperty("_rev", "rev_$i")
            }
        }

        val query: RealmQuery<RealmTeamLog> = mockk(relaxed = true)
        val results: RealmResults<RealmTeamLog> = mockk(relaxed = true)

        every { realm.where(RealmTeamLog::class.java) } returns query
        every { query.`in`(any<String>(), any<Array<String>>()) } returns query
        every { query.findAll() } returns results
        every { results.iterator() } returns mutableListOf<RealmTeamLog>().iterator()

        every { realm.createObject(RealmTeamLog::class.java, any()) } returns RealmTeamLog()

        teamsRepository.insertTeamLogs(logs)

        verify(exactly = 1) { realm.where(RealmTeamLog::class.java) }
    }

    @Test
    fun testInsertTeamLogsWithDuplicatesInBatch() = runTest {
        val logs = listOf(
            JsonObject().apply { addProperty("_id", "dup_id"); addProperty("_rev", "rev1") },
            JsonObject().apply { addProperty("_id", "dup_id"); addProperty("_rev", "rev2") }
        )

        val query: RealmQuery<RealmTeamLog> = mockk(relaxed = true)
        val results: RealmResults<RealmTeamLog> = mockk(relaxed = true)

        every { realm.where(RealmTeamLog::class.java) } returns query
        every { query.`in`(any<String>(), any<Array<String>>()) } returns query
        every { query.findAll() } returns results
        every { results.iterator() } returns mutableListOf<RealmTeamLog>().iterator()

        // We now use bulk inserts with unmanaged objects, so createObject is not called.
        // Instead, we just verify it runs without throwing.
        every { realm.insert(any<List<RealmTeamLog>>()) } returns Unit

        teamsRepository.insertTeamLogs(logs)

        // Verify that createObject is never called anymore
        verify(exactly = 0) { realm.createObject(RealmTeamLog::class.java, "dup_id") }
        // Verify bulk insert is called
        verify(exactly = 1) { realm.insert(any<List<RealmTeamLog>>()) }
    }
}
