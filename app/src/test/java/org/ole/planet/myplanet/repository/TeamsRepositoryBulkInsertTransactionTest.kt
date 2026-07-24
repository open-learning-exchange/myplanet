package org.ole.planet.myplanet.repository

import android.app.Application
import android.content.SharedPreferences
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TestTimeProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Real in-memory Room coverage for [TeamsRepositoryImpl.bulkInsertFromSync], which wraps a whole
 * heavy-table sync page in a single `appDatabase.withTransaction { }` so the ~1000 per-doc upserts
 * commit (and fsync) once instead of individually. The rest of the suite mocks the DAOs, so this is
 * the only test that verifies the batch actually lands atomically.
 *
 * SDK is pinned below S so `processDescription` short-circuits (it otherwise calls the download
 * service via MainApplication.context, which isn't wired up in a plain unit test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class TeamsRepositoryBulkInsertTransactionTest {

    private lateinit var db: AppDatabase
    private lateinit var teamDao: TeamDao
    private lateinit var repository: TeamsRepositoryImpl

    private fun teamRow(id: String, name: String): JsonObject = JsonObject().apply {
        add("doc", JsonObject().apply {
            addProperty("_id", id)
            addProperty("docType", "team")
            addProperty("type", "team")
            addProperty("name", name)
        })
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        // Spy the real DAO so writes hit the in-memory DB inside the real transaction, while still
        // allowing a single call to be forced to throw for the rollback test.
        teamDao = spyk(db.teamDao())

        repository = TeamsRepositoryImpl(
            mockk<ActivitiesRepository>(relaxed = true),
            mockk<UserSessionManager>(relaxed = true),
            mockk<UploadManager>(relaxed = true),
            Gson(),
            mockk<SharedPreferences>(relaxed = true),
            mockk<SharedPrefManager>(relaxed = true),
            mockk<ServerUrlMapper>(relaxed = true),
            mockk<DispatcherProvider>(relaxed = true),
            mockk<UserRepository>(relaxed = true),
            mockk<dagger.Lazy<ResourcesRepository>>(relaxed = true),
            TestTimeProvider(),
            mockk<TeamLogDao>(relaxed = true),
            mockk<TeamTaskDao>(relaxed = true),
            mockk<MyLibraryDao>(relaxed = true),
            teamDao,
            mockk<UserDao>(relaxed = true),
            mockk<CourseDao>(relaxed = true),
            mockk<CourseStepDao>(relaxed = true),
            db,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `bulkInsertFromSync persists the whole batch and skips design docs`() = runBlocking {
        val batch = JsonArray().apply {
            add(teamRow("team_a", "Team A"))
            add(teamRow("team_b", "Team B"))
            add(JsonObject().apply {
                add("doc", JsonObject().apply { addProperty("_id", "_design/teams") })
            })
        }

        repository.bulkInsertFromSync(batch)

        assertEquals("Team A", db.teamDao().getById("team_a")?.name)
        assertEquals("Team B", db.teamDao().getById("team_b")?.name)
        // _design docs are filtered out before the transaction.
        assertNull(db.teamDao().getById("_design/teams"))
    }

    @Test
    fun `bulkInsertFromSync rolls back the whole batch when one doc fails`() = runBlocking {
        // Force the second doc's upsert to fail mid-transaction.
        coEvery { teamDao.upsert(match { it._id == "team_poison" }) } throws RuntimeException("boom")

        val batch = JsonArray().apply {
            add(teamRow("team_ok", "Team OK"))
            add(teamRow("team_poison", "Team Poison"))
            add(teamRow("team_never", "Team Never"))
        }

        try {
            repository.bulkInsertFromSync(batch)
            fail("expected the failing upsert to abort the transaction")
        } catch (_: RuntimeException) {
            // expected
        }

        // Atomic rollback: the earlier successful upsert must not survive, and later docs
        // must never have been written.
        assertNull(db.teamDao().getById("team_ok"))
        assertNull(db.teamDao().getById("team_poison"))
        assertNull(db.teamDao().getById("team_never"))
    }

    @Test
    fun `bulkInsertFromSync updates an existing team row in place`() = runBlocking {
        repository.bulkInsertFromSync(JsonArray().apply { add(teamRow("team_a", "Original")) })
        assertEquals("Original", db.teamDao().getById("team_a")?.name)

        repository.bulkInsertFromSync(JsonArray().apply { add(teamRow("team_a", "Renamed")) })

        val updated = db.teamDao().getById("team_a")
        assertNotNull(updated)
        assertEquals("Renamed", updated?.name)
        assertEquals(1, db.teamDao().getByDocType("team").size)
    }
}
