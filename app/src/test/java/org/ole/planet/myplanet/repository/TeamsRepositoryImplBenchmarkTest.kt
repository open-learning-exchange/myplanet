package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.realm.Realm
import io.realm.RealmQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.JsonUtils
import kotlin.system.measureTimeMillis

class TeamsRepositoryImplBenchmarkTest {

    private lateinit var teamsRepositoryImpl: TeamsRepositoryImpl
    private lateinit var activitiesRepository: ActivitiesRepository
    private lateinit var databaseService: DatabaseService
    private lateinit var realmDispatcher: CoroutineDispatcher
    private lateinit var userSessionManager: UserSessionManager
    private lateinit var uploadManager: UploadManager
    private lateinit var gson: Gson
    private lateinit var preferences: SharedPreferences
    private lateinit var sharedPrefManager: SharedPrefManager
    private lateinit var serverUrlMapper: ServerUrlMapper
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var userRepository: UserRepository
    private lateinit var realm: Realm

    @Before
    fun setup() {
        activitiesRepository = mockk(relaxed = true)
        databaseService = mockk(relaxed = true)
        realmDispatcher = mockk(relaxed = true)
        userSessionManager = mockk(relaxed = true)
        uploadManager = mockk(relaxed = true)
        gson = Gson()
        preferences = mockk(relaxed = true)
        sharedPrefManager = mockk(relaxed = true)
        serverUrlMapper = mockk(relaxed = true)
        dispatcherProvider = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        realm = mockk(relaxed = true)

        mockkStatic(JsonUtils::class)
        every { JsonUtils.getString(any<String>(), any<JsonObject>()) } returns "mockString"

        teamsRepositoryImpl = TeamsRepositoryImpl(
            activitiesRepository,
            databaseService,
            realmDispatcher,
            userSessionManager,
            uploadManager,
            gson,
            preferences,
            sharedPrefManager,
            serverUrlMapper,
            dispatcherProvider,
            userRepository
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun benchmarkBatchInsertMyTeams() = runTest {
        val documents = List(2000) { JsonObject().apply { addProperty("_id", "team_$it") } }

        every { databaseService.withRealm(any<(Realm) -> Unit>()) } answers {
            val block = firstArg<(Realm) -> Unit>()
            block.invoke(realm)
        }

        every { realm.executeTransaction(any<Realm.Transaction>()) } answers {
            val block = firstArg<Realm.Transaction>()
            block.execute(realm)
        }

        val mockQuery = mockk<RealmQuery<RealmMyTeam>>(relaxed = true)
        every { realm.where(RealmMyTeam::class.java) } returns mockQuery
        every { mockQuery.equalTo(any<String>(), any<String>()) } returns mockQuery
        every { mockQuery.findFirst() } answers {
            Thread.sleep(1) // Simulate database read latency per query
            null
        }

        val time = measureTimeMillis {
            teamsRepositoryImpl.batchInsertMyTeams(documents)
        }
        println("Batch insert time: $time ms")
    }
}
