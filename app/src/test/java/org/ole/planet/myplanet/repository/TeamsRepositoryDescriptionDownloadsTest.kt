package org.ole.planet.myplanet.repository

import android.app.Application
import android.content.SharedPreferences
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.CourseDao
import org.ole.planet.myplanet.data.room.dao.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.ServerUrlMapper
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.DownloadUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Team sync used to start the download service once per team document, even when the team's
 * description linked nothing — over a thousand foreground-service starts on a first sync. Links
 * are now gathered across the batch and handed to the service once, or not at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [31])
class TeamsRepositoryDescriptionDownloadsTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TeamsRepositoryImpl

    private fun teamRow(id: String, description: String?): JsonObject = JsonObject().apply {
        add("doc", JsonObject().apply {
            addProperty("_id", id)
            addProperty("docType", "team")
            addProperty("type", "team")
            addProperty("name", id)
            description?.let { addProperty("description", it) }
        })
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        MainApplication.testContext = RuntimeEnvironment.getApplication()
        mockkObject(UrlUtils)
        every { UrlUtils.getUrl() } returns "https://planet.example/db"
        mockkObject(DownloadUtils)
        every { DownloadUtils.openDownloadService(any(), any(), any()) } returns Unit

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
            mockk<TeamTaskDao>(relaxed = true),
            mockk<MyLibraryDao>(relaxed = true),
            db.teamDao(),
            mockk<CourseDao>(relaxed = true),
            mockk<CourseStepDao>(relaxed = true),
            db,
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
        MainApplication.testContext = null
        db.close()
    }

    @Test
    fun `bulkInsertFromSync without description links never starts the download service`() = runBlocking {
        val batch = JsonArray().apply {
            add(teamRow("team_a", "Plain text"))
            add(teamRow("team_b", null))
            add(teamRow("team_c", ""))
        }

        repository.bulkInsertFromSync(batch)

        verify(exactly = 0) { DownloadUtils.openDownloadService(any(), any(), any()) }
    }

    @Test
    fun `bulkInsertFromSync starts the download service once with every linked file`() = runBlocking {
        val batch = JsonArray().apply {
            add(teamRow("team_a", "![logo](resources/a.png)"))
            add(teamRow("team_b", "No images here"))
            add(teamRow("team_c", "![one](resources/c.png) and ![again](resources/a.png)"))
        }

        repository.bulkInsertFromSync(batch)

        verify(exactly = 1) {
            DownloadUtils.openDownloadService(
                any(),
                arrayListOf("https://planet.example/db/resources/a.png", "https://planet.example/db/resources/c.png"),
                true
            )
        }
    }

    @Test
    fun `batchInsertMyTeams starts the download service once per batch`() = runBlocking {
        val docs = listOf(
            teamRow("team_a", "![a](resources/a.png)").getAsJsonObject("doc"),
            teamRow("team_b", "![b](resources/b.png)").getAsJsonObject("doc"),
        )

        repository.batchInsertMyTeams(docs)

        verify(exactly = 1) { DownloadUtils.openDownloadService(any(), any(), true) }
    }
}
