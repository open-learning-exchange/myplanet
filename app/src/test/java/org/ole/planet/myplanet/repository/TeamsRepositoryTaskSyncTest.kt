package org.ole.planet.myplanet.repository

import android.app.Application
import android.content.SharedPreferences
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Real in-memory Room coverage for [TeamsRepositoryImpl.bulkInsertTasksFromSync]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class TeamsRepositoryTaskSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TeamTaskDao
    private lateinit var repository: TeamsRepositoryImpl

    private fun pulledTask(remoteId: String, title: String, deadline: Long = 0L): JsonObject = JsonObject().apply {
        add("doc", JsonObject().apply {
            addProperty("_id", remoteId)
            addProperty("_rev", "2-abc")
            addProperty("title", title)
            addProperty("deadline", deadline)
            add("link", JsonObject().apply { addProperty("teams", "team1") })
            add("sync", JsonObject())
        })
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        taskDao = db.teamTaskDao()
        repository = TeamsRepositoryImpl(
            mockk<android.content.Context>(relaxed = true),
            mockk<UserSessionManager>(relaxed = true),
            mockk<UploadManager>(relaxed = true),
            Gson(),
            mockk<SharedPreferences>(relaxed = true),
            mockk<SharedPrefManager>(relaxed = true),
            mockk<ServerUrlMapper>(relaxed = true),
            mockk<DispatcherProvider>(relaxed = true),
            mockk<UserRepository>(relaxed = true),
            mockk<dagger.Lazy<ResourcesRepository>>(relaxed = true),
            mockk<TeamLogDao>(relaxed = true),
            taskDao,
            mockk<MyLibraryDao>(relaxed = true),
            db.teamDao(),
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
    fun `pulling an uploaded local task updates it instead of adding a second row`() = runBlocking {
        // Created in the app, then uploaded: local UUID id, server id in _id
        taskDao.upsert(TeamTask().apply { id = "local-uuid"; teamId = "team1"; title = "Old title" })
        taskDao.markUploaded("local-uuid", "server-1", "1-abc")

        repository.bulkInsertTasksFromSync(JsonArray().apply { add(pulledTask("server-1", "New title")) })

        val tasks = taskDao.getTasksByTeamId("team1").first()
        assertEquals(1, tasks.size)
        assertEquals("local-uuid", tasks[0].id)
        assertEquals("New title", tasks[0].title)
        assertEquals("2-abc", tasks[0]._rev)
    }

    @Test
    fun `pulling removes a duplicate already stored under the server id`() = runBlocking {
        taskDao.upsert(TeamTask().apply { id = "local-uuid"; _id = "server-1"; teamId = "team1"; title = "Task" })
        taskDao.upsert(TeamTask().apply { id = "server-1"; _id = "server-1"; teamId = "team1"; title = "Task" })

        repository.bulkInsertTasksFromSync(JsonArray().apply { add(pulledTask("server-1", "Task")) })

        assertEquals(listOf("local-uuid"), taskDao.getTasksByTeamId("team1").first().map { it.id })
    }

    @Test
    fun `tasks created on the server are stored under their server id as before`() = runBlocking {
        repository.bulkInsertTasksFromSync(JsonArray().apply { add(pulledTask("server-2", "From Planet")) })

        assertEquals(listOf("server-2"), taskDao.getTasksByTeamId("team1").first().map { it.id })
    }

    @Test
    fun `pulling keeps the notified flag so a due task isn't announced again`() = runBlocking {
        taskDao.upsert(TeamTask().apply { id = "server-3"; _id = "server-3"; teamId = "team1"; title = "Due"; deadline = 1000L; isNotified = true })

        repository.bulkInsertTasksFromSync(JsonArray().apply { add(pulledTask("server-3", "Due", deadline = 1000L)) })

        assertEquals(true, taskDao.getById("server-3")?.isNotified)
    }

    @Test
    fun `a moved deadline resets the notified flag`() = runBlocking {
        taskDao.upsert(TeamTask().apply { id = "server-4"; _id = "server-4"; teamId = "team1"; title = "Due"; deadline = 1000L; isNotified = true })

        repository.bulkInsertTasksFromSync(JsonArray().apply { add(pulledTask("server-4", "Due", deadline = 2000L)) })

        assertEquals(false, taskDao.getById("server-4")?.isNotified)
    }

    @Test
    fun `pulling doesn't overwrite a task with unsynced local edits`() = runBlocking {
        taskDao.upsert(TeamTask().apply { id = "server-5"; _id = "server-5"; teamId = "team1"; title = "Edited offline"; isUpdated = true })

        repository.bulkInsertTasksFromSync(JsonArray().apply { add(pulledTask("server-5", "Server title")) })

        val task = taskDao.getById("server-5")
        assertEquals("Edited offline", task?.title)
        assertEquals(true, task?.isUpdated)
    }
}
