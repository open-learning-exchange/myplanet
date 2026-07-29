package org.ole.planet.myplanet.repository

import android.app.Application
import androidx.room.Room
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.services.SharedPrefManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Real in-memory Room coverage for the MyLibrary / shelf sync insert paths
 * ([ResourcesRepositoryImpl.batchInsertResources] and [ResourcesRepositoryImpl.batchInsertMyLibrary]).
 * The rest of the suite mocks the DAO, so this is the only test exercising the CouchDB doc ->
 * MyLibrary mapping, the _design filtering, and — most importantly — the shelf userId merge that
 * lets one resource belong to multiple shelves.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
class ResourcesRepositoryLibrarySyncTest {

    private lateinit var db: AppDatabase
    private lateinit var myLibraryDao: MyLibraryDao
    private lateinit var repository: ResourcesRepositoryImpl

    private fun resourceDoc(id: String, title: String): JsonObject = JsonObject().apply {
        addProperty("_id", id)
        addProperty("_rev", "1-$id")
        addProperty("title", title)
        addProperty("description", "desc of $title")
        addProperty("mediaType", "video")
        addProperty("author", "Author $title")
        add("subject", JsonArray().apply { add("science") })
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        myLibraryDao = db.myLibraryDao()

        repository = ResourcesRepositoryImpl(
            RuntimeEnvironment.getApplication(),
            mockk<ActivitiesRepository>(relaxed = true),
            mockk<SharedPrefManager>(relaxed = true),
            mockk<RatingsRepository>(relaxed = true),
            mockk<TagsRepository>(relaxed = true),
            mockk<SearchActivityDao>(relaxed = true),
            mockk<ResourceActivityDao>(relaxed = true),
            mockk<RemovedLogDao>(relaxed = true),
            mockk<dagger.Lazy<TeamsSyncRepository>>(relaxed = true),
            myLibraryDao,
            mockk<UserDao>(relaxed = true),
            mockk<TeamDao>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `batchInsertResources maps couchdb docs and skips design docs`() = runBlocking {
        val savedIds = repository.batchInsertResources(
            listOf(
                resourceDoc("res1", "Algebra"),
                resourceDoc("res2", "Biology"),
                JsonObject().apply { addProperty("_id", "_design/resources") },
            )
        )

        assertEquals(setOf("res1", "res2"), savedIds.toSet())
        val res1 = myLibraryDao.getById("res1")
        assertNotNull(res1)
        assertEquals("Algebra", res1?.title)
        assertEquals("desc of Algebra", res1?.description)
        assertEquals("video", res1?.mediaType)
        assertEquals(listOf("science"), res1?.subject)
        assertNull(myLibraryDao.getById("_design/resources"))
    }

    @Test
    fun `batchInsertMyLibrary merges shelf ids so one resource can span shelves`() = runBlocking {
        val count1 = repository.batchInsertMyLibrary("shelfUserA", listOf(resourceDoc("res1", "Algebra")))
        assertEquals(1, count1)
        assertEquals(listOf("shelfUserA"), myLibraryDao.getById("res1")?.userId)

        // The same resource synced under a second shelf must merge, not overwrite.
        val count2 = repository.batchInsertMyLibrary("shelfUserB", listOf(resourceDoc("res1", "Algebra")))
        assertEquals(1, count2)

        val merged = myLibraryDao.getById("res1")?.userId
        assertNotNull(merged)
        assertTrue(merged!!.containsAll(listOf("shelfUserA", "shelfUserB")))
        assertEquals(2, merged.size)
        // Still a single row for the resource.
        assertEquals(1, myLibraryDao.getAll().size)
    }
}
