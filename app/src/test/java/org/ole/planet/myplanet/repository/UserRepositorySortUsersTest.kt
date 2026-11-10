package org.ole.planet.myplanet.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.data.room.dao.UserDao
import org.ole.planet.myplanet.model.UserEntity

class UserRepositorySortUsersTest {

    private fun repositoryWith(users: List<UserEntity>): UserRepositoryImpl {
        val userDao = mockk<UserDao>(relaxed = true)
        coEvery { userDao.getAll() } returns users
        return UserRepositoryImpl(
            settings = mockk(relaxed = true),
            sharedPrefManager = mockk(relaxed = true),
            apiInterface = mockk(relaxed = true),
            resourcesRepositoryLazy = mockk(relaxed = true),
            coursesRepositoryLazy = mockk(relaxed = true),
            uploadToShelfService = mockk(relaxed = true),
            context = mockk(relaxed = true),
            configurationsRepository = mockk(relaxed = true),
            appScope = mockk(relaxed = true),
            dispatcherProvider = mockk(relaxed = true),
            activitiesRepositoryLazy = mockk(relaxed = true),
            eventsRepositoryLazy = mockk(relaxed = true),
            achievementDao = mockk(relaxed = true),
            userDao = userDao,
            realtimeSyncManager = mockk(relaxed = true),
            deviceNameProvider = mockk(relaxed = true)
        )
    }

    private fun user(id: String, name: String? = null, firstName: String? = null) =
        UserEntity().apply {
            this.id = id
            this.name = name
            this.firstName = firstName
        }

    @Test
    fun `getUsersSortedBy sorts name case-insensitively ascending`() = runTest {
        val repo = repositoryWith(
            listOf(
                user("3", name = "banana"),
                user("1", name = "Apple"),
                user("2", name = "cherry")
            )
        )

        val sorted = repo.getUsersSortedBy("name", descending = false)

        assertEquals(listOf("1", "3", "2"), sorted.map { it.id })
    }

    @Test
    fun `getUsersSortedBy sorts name case-insensitively descending`() = runTest {
        val repo = repositoryWith(
            listOf(
                user("3", name = "banana"),
                user("1", name = "Apple"),
                user("2", name = "cherry")
            )
        )

        val sorted = repo.getUsersSortedBy("name", descending = true)

        assertEquals(listOf("2", "3", "1"), sorted.map { it.id })
    }

    @Test
    fun `getUsersSortedBy is stable for equal keys ascending`() = runTest {
        val repo = repositoryWith(
            listOf(
                user("a", name = "Same"),
                user("b", name = "same"),
                user("c", name = "SAME")
            )
        )

        val sorted = repo.getUsersSortedBy("name", descending = false)

        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }

    @Test
    fun `getUsersSortedBy is stable for equal keys descending`() = runTest {
        val repo = repositoryWith(
            listOf(
                user("a", name = "Same"),
                user("b", name = "same"),
                user("c", name = "SAME")
            )
        )

        val sorted = repo.getUsersSortedBy("name", descending = true)

        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }

    @Test
    fun `getUsersSortedBy treats null string keys as empty and sorts them first ascending`() = runTest {
        val repo = repositoryWith(
            listOf(
                user("1", firstName = "Zed"),
                user("2", firstName = null),
                user("3", firstName = "abe")
            )
        )

        val sorted = repo.getUsersSortedBy("firstName", descending = false)

        assertEquals(listOf("2", "3", "1"), sorted.map { it.id })
    }

    @Test
    fun `getUsersSortedBy unknown field returns the original list unchanged`() = runTest {
        val repo = repositoryWith(
            listOf(
                user("1", name = "b"),
                user("2", name = "a")
            )
        )

        val sorted = repo.getUsersSortedBy("notAField", descending = false)

        assertEquals(listOf("1", "2"), sorted.map { it.id })
    }

    @Test
    fun `getUsersSortedBy joinDate stays a direct field sort`() = runTest {
        val repo = repositoryWith(
            listOf(
                UserEntity().apply { id = "1"; joinDate = 30 },
                UserEntity().apply { id = "2"; joinDate = 10 },
                UserEntity().apply { id = "3"; joinDate = 20 }
            )
        )

        val sorted = repo.getUsersSortedBy("joinDate", descending = false)

        assertEquals(listOf("2", "3", "1"), sorted.map { it.id })
    }

    @Test
    fun `searchUsers sorts the matched users by the requested string field`() = runTest {
        val userDao = mockk<UserDao>(relaxed = true)
        coEvery { userDao.search(any()) } returns listOf(
            user("3", name = "Charlie"),
            user("1", name = "alpha"),
            user("2", name = "Bravo")
        )
        val repo = UserRepositoryImpl(
            settings = mockk(relaxed = true),
            sharedPrefManager = mockk(relaxed = true),
            apiInterface = mockk(relaxed = true),
            resourcesRepositoryLazy = mockk(relaxed = true),
            coursesRepositoryLazy = mockk(relaxed = true),
            uploadToShelfService = mockk(relaxed = true),
            context = mockk(relaxed = true),
            configurationsRepository = mockk(relaxed = true),
            appScope = mockk(relaxed = true),
            dispatcherProvider = mockk(relaxed = true),
            activitiesRepositoryLazy = mockk(relaxed = true),
            eventsRepositoryLazy = mockk(relaxed = true),
            achievementDao = mockk(relaxed = true),
            userDao = userDao,
            realtimeSyncManager = mockk(relaxed = true),
            deviceNameProvider = mockk(relaxed = true)
        )

        val sorted = repo.searchUsers("a", "name", descending = true)

        assertEquals(listOf("3", "2", "1"), sorted.map { it.id })
    }
}
