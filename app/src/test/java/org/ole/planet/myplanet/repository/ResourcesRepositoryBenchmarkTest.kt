package org.ole.planet.myplanet.repository

import android.content.Context
import com.google.gson.JsonObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.TeamDao
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.services.SharedPrefManager

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesRepositoryBenchmarkTest {
    private lateinit var resourcesRepository: ResourcesRepositoryImpl
    private val context: Context = mockk(relaxed = true)
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val ratingsRepository: RatingsRepository = mockk(relaxed = true)
    private val tagsRepository: TagsRepository = mockk(relaxed = true)
    private val searchActivityDao: SearchActivityDao = mockk(relaxed = true)
    private val resourceActivityDao: ResourceActivityDao = mockk(relaxed = true)
    private val removedLogDao: RemovedLogDao = mockk(relaxed = true)
    private val teamsSyncRepositoryLazy: dagger.Lazy<TeamsSyncRepository> = mockk(relaxed = true)
    private val myLibraryDao: MyLibraryDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val teamDao: TeamDao = mockk(relaxed = true)

    @Before
    fun setup() {
        resourcesRepository = ResourcesRepositoryImpl(
            context,
            activitiesRepository,
            sharedPrefManager,
            ratingsRepository,
            tagsRepository,
            searchActivityDao,
            resourceActivityDao,
            removedLogDao,
            teamsSyncRepositoryLazy,
            myLibraryDao,
            userDao,
            teamDao
        )
    }

    @Test
    fun benchmarkBatchInsertMyLibrary() = runTest {
        val count = 100
        val docs = (1..count).map { i ->
            JsonObject().apply {
                addProperty("_id", "id_$i")
                addProperty("_rev", "rev_$i")
            }
        }

        coEvery { myLibraryDao.getByIds(any()) } returns emptyList()
        coEvery { myLibraryDao.upsert(any()) } returns Unit

        resourcesRepository.batchInsertMyLibrary(shelfId = "shelf1", documents = docs)

        coVerify(exactly = 0) { myLibraryDao.getById(any()) }
        coVerify(exactly = 1) { myLibraryDao.getByIds(match { it.size == 100 }) }
    }
}
