package org.ole.planet.myplanet.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.services.SharedPrefManager
import java.lang.reflect.Method

@OptIn(ExperimentalCoroutinesApi::class)
class ResourcesRepositoryImplTest {

    private val context: Context = mockk(relaxed = true)
    private val databaseService: DatabaseService = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()
    private val activitiesRepository: ActivitiesRepository = mockk(relaxed = true)
    private val settings: SharedPreferences = mockk(relaxed = true)
    private val sharedPrefManager: SharedPrefManager = mockk(relaxed = true)
    private val ratingsRepository: RatingsRepository = mockk(relaxed = true)
    private val tagsRepository: TagsRepository = mockk(relaxed = true)
    private val teamsRepositoryLazy: Lazy<TeamsRepository> = mockk(relaxed = true)

    private val repository = ResourcesRepositoryImpl(
        context,
        databaseService,
        testDispatcher,
        activitiesRepository,
        settings,
        sharedPrefManager,
        ratingsRepository,
        tagsRepository,
        teamsRepositoryLazy
    )

    @Test
    fun testNormalizeText() {
        // Happy paths
        assertEquals("hello world", ResourcesRepositoryImpl.normalizeText("HELLO World"))

        // Diacritics testing
        assertEquals("cafe", ResourcesRepositoryImpl.normalizeText("Café"))
        assertEquals("nino", ResourcesRepositoryImpl.normalizeText("Niño"))
        assertEquals("a e i o u", ResourcesRepositoryImpl.normalizeText("á é í ó ú"))
        assertEquals("c", ResourcesRepositoryImpl.normalizeText("ç"))
        assertEquals("aeiou", ResourcesRepositoryImpl.normalizeText("äëïöü"))
    }

    @Test
    fun testFilterLibrariesNeedingUpdate() {
        val method: Method = ResourcesRepositoryImpl::class.java.getDeclaredMethod(
            "filterLibrariesNeedingUpdate",
            Collection::class.java
        )
        method.isAccessible = true

        val lib1 = mockk<RealmMyLibrary>()
        every { lib1.needToUpdate() } returns true

        val lib2 = mockk<RealmMyLibrary>()
        every { lib2.needToUpdate() } returns false

        val lib3 = mockk<RealmMyLibrary>()
        every { lib3.needToUpdate() } returns true

        val lib4 = mockk<RealmMyLibrary>()
        every { lib4.needToUpdate() } returns false

        val input = listOf(lib1, lib2, lib3, lib4)

        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(repository, input) as List<RealmMyLibrary>

        assertEquals(2, result.size)
        assertTrue(result.contains(lib1))
        assertTrue(result.contains(lib3))
    }
}
