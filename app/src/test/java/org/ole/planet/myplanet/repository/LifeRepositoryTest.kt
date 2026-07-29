package org.ole.planet.myplanet.repository

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.services.SharedPrefManager

@OptIn(ExperimentalCoroutinesApi::class)
class LifeRepositoryTest {

    private lateinit var myLifeDao: MyLifeDao
    private lateinit var repository: LifeRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Logger.getLogger("io.mockk").level = Level.OFF
        myLifeDao = mockk(relaxed = true)
        val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
        every { sharedPrefManager.rawPreferences } returns mockk(relaxed = true)
        repository = LifeRepositoryImpl(
            myLifeDao,
            sharedPrefManager,
            Gson(),
            CoroutineScope(testDispatcher)
        )
    }

    @Test
    fun updateVisibility_delegatesToDao() = runTest {
        val myLifeId = "missing123"

        repository.updateVisibility(true, myLifeId)

        coVerify(exactly = 1) { myLifeDao.updateVisibility(myLifeId, true) }
    }

    @Test
    fun updateMyLifeListOrder_itemNotInList_weightNotUpdated() = runTest {
        // The reorder list only contains item with _id "3".
        val listItem = MyLife().apply { _id = "3"; userId = "u" }

        // The managed rows retrieved from the DB have ids "1" and "2" (not in the list).
        val managedItem1 = MyLife().apply { _id = "1"; weight = 99 }
        val managedItem2 = MyLife().apply { _id = "2"; weight = 99 }
        coEvery { myLifeDao.getByIds(any()) } returns listOf(managedItem1, managedItem2)

        repository.updateMyLifeListOrder(listOf(listItem))

        // Weights unchanged because neither managed id appears in the reorder list.
        assertEquals(99, managedItem1.weight)
        assertEquals(99, managedItem2.weight)
        coVerify(exactly = 0) { myLifeDao.update(any()) }
    }

    @Test
    fun getMyLifeByUserId_returnsEmptyList() = runTest {
        val userId = "user123"
        coEvery { myLifeDao.getByUserId(userId) } returns emptyList()

        val result = repository.getMyLifeByUserId(userId, ensureLatest = true)

        assertEquals(0, result.size)
    }
}
